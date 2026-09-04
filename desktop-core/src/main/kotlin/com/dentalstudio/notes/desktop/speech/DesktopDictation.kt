package com.dentalstudio.notes.desktop.speech

import com.dentalstudio.notes.speech.DictationState
import com.dentalstudio.notes.speech.DictationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Dictation on the desktop.
 *
 * Windows has no equivalent of Android's system speech recogniser that an app
 * can drive, so recognition runs locally through Vosk against a model the
 * clinician points the app at. Nothing is uploaded.
 *
 * Without a model the app is still fully usable: the transcript box is an
 * ordinary text field, so Windows' own dictation (Win+H) types straight into
 * it. [unavailableReason] explains which case the clinician is in.
 */
class DesktopDictation {

    private val _state = MutableStateFlow(DictationState())
    val state: StateFlow<DictationState> = _state.asStateFlow()

    private var model: Model? = null
    private var modelPath: String = ""
    private var captureThread: Thread? = null
    @Volatile private var running = false

    private val json = Json { ignoreUnknownKeys = true }

    var unavailableReason: String? = "No speech model configured. Set one in Settings, or use Windows dictation (Win+H) into the transcript box."
        private set

    val isAvailable: Boolean get() = unavailableReason == null

    /** Points the engine at an unpacked Vosk model directory. Safe to call repeatedly. */
    fun configure(path: String) {
        if (path == modelPath && model != null) return
        releaseModel()
        modelPath = path

        if (path.isBlank()) {
            unavailableReason = "No speech model configured. Set one in Settings, or use Windows dictation (Win+H) into the transcript box."
            return
        }
        val chosen = File(path)
        if (!chosen.isDirectory) {
            unavailableReason = "No folder at $path."
            return
        }
        val dir = resolveModelDir(chosen)
        if (dir == null) {
            unavailableReason = "$path does not look like a speech model. Choose the folder created " +
                "when you unpacked the model — the one containing 'am' and 'conf'."
            return
        }
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(dir.absolutePath)
            unavailableReason = null
        } catch (e: Throwable) {
            // A missing native library or an unreadable model must not take the
            // app down — typing and Windows dictation still work.
            model = null
            unavailableReason = "Could not load the speech model: ${e.message ?: e::class.simpleName}"
        }
    }

    /**
     * Accepts either the model folder itself or a wrapper folder containing it,
     * since unpacking an archive often creates one extra level.
     */
    private fun resolveModelDir(chosen: File): File? {
        if (looksLikeModel(chosen)) return chosen
        val nested = chosen.listFiles()?.filter { it.isDirectory && looksLikeModel(it) }.orEmpty()
        return nested.singleOrNull()
    }

    private fun looksLikeModel(dir: File): Boolean =
        File(dir, "conf").isDirectory && (File(dir, "am").isDirectory || File(dir, "graph").isDirectory)

    fun start() {
        val loaded = model
        if (loaded == null) {
            _state.value = _state.value.copy(status = DictationStatus.ERROR, error = unavailableReason)
            return
        }
        if (running) return

        val line = try {
            openMicrophone()
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                status = DictationStatus.ERROR,
                error = "Could not open the microphone: ${e.message}",
            )
            return
        }

        running = true
        _state.value = _state.value.copy(status = DictationStatus.LISTENING, error = null)
        captureThread = thread(name = "dictation", isDaemon = true) { capture(line, loaded) }
    }

    fun pause() {
        running = false
        captureThread = null
        _state.value = _state.value.copy(status = DictationStatus.PAUSED, amplitude = 0f)
    }

    fun stop() {
        pause()
        _state.value = _state.value.copy(status = DictationStatus.IDLE, partialText = "")
    }

    fun reset() {
        running = false
        captureThread = null
        _state.value = DictationState()
    }

    /** Lets the clinician correct the transcript, or type it entirely by hand. */
    fun overwriteTranscript(text: String) {
        _state.value = _state.value.copy(finalText = text.trim(), partialText = "")
    }

    fun release() {
        running = false
        releaseModel()
    }

    private fun releaseModel() {
        runCatching { model?.close() }
        model = null
    }

    private fun openMicrophone(): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, AUDIO_FORMAT)
        require(AudioSystem.isLineSupported(info)) { "16 kHz mono capture is not supported by this device" }
        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(AUDIO_FORMAT)
        line.start()
        return line
    }

    private fun capture(line: TargetDataLine, model: Model) {
        try {
            Recognizer(model, SAMPLE_RATE).use { recognizer ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (running) {
                    val read = line.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    _state.value = _state.value.copy(amplitude = level(buffer, read))

                    if (recognizer.acceptWaveForm(buffer, read)) {
                        textOf(recognizer.result, "text")?.let { append(it) }
                    } else {
                        val partial = textOf(recognizer.partialResult, "partial").orEmpty()
                        _state.value = _state.value.copy(partialText = partial)
                    }
                }
                // Whatever was mid-sentence when the clinician paused still counts.
                textOf(recognizer.finalResult, "text")?.let { append(it) }
            }
        } catch (e: Throwable) {
            _state.value = _state.value.copy(
                status = DictationStatus.ERROR,
                error = "Dictation stopped: ${e.message ?: e::class.simpleName}",
                amplitude = 0f,
            )
        } finally {
            runCatching { line.stop() }
            runCatching { line.close() }
            _state.value = _state.value.copy(amplitude = 0f)
        }
    }

    private fun textOf(result: String, key: String): String? {
        val parsed = runCatching { json.parseToJsonElement(result) as? JsonObject }.getOrNull() ?: return null
        val text = parsed[key]?.jsonPrimitive?.contentOrNull()?.trim().orEmpty()
        return text.ifBlank { null }
    }

    private fun append(text: String) {
        val current = _state.value.finalText
        val joined = if (current.isBlank()) text else "$current $text"
        _state.value = _state.value.copy(finalText = joined, partialText = "")
    }

    /** RMS of the 16-bit little-endian frames, mapped to a 0..1 meter height. */
    private fun level(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            sum += (sample * sample).toDouble()
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count) / Short.MAX_VALUE
        return min(1.0, rms * 4.0).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000f
        const val BUFFER_BYTES = 4096
        val AUDIO_FORMAT = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    runCatching { content }.getOrNull()
