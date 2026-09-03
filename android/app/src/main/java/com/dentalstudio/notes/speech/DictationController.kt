package com.dentalstudio.notes.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Continuous dictation on top of Android's speech recogniser.
 *
 * The platform recogniser stops after every pause. This wrapper restarts it
 * silently and keeps appending, so a clinician can talk through a whole
 * appointment without touching the phone.
 */
class DictationController(private val context: Context) {

    private val _state = MutableStateFlow(DictationState())
    val state: StateFlow<DictationState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var wantsToListen = false
    private var preferOffline = false

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Must be called from the main thread. */
    fun start(preferOffline: Boolean = false) {
        if (!isAvailable) {
            _state.value = _state.value.copy(
                status = DictationStatus.ERROR,
                error = "No speech recognition service on this device. Install or enable Google's speech services.",
            )
            return
        }
        this.preferOffline = preferOffline
        wantsToListen = true
        _state.value = _state.value.copy(status = DictationStatus.LISTENING, error = null)
        listen()
    }

    fun pause() {
        wantsToListen = false
        commitPartial()
        recognizer?.stopListening()
        _state.value = _state.value.copy(status = DictationStatus.PAUSED, amplitude = 0f)
    }

    fun resume() = start(preferOffline)

    fun stop() {
        wantsToListen = false
        commitPartial()
        release()
        _state.value = _state.value.copy(status = DictationStatus.IDLE, amplitude = 0f, partialText = "")
    }

    fun reset() {
        wantsToListen = false
        release()
        _state.value = DictationState()
    }

    /** Lets the clinician correct the transcript before the note is generated. */
    fun overwriteTranscript(text: String) {
        _state.value = _state.value.copy(finalText = text.trim(), partialText = "")
    }

    private fun listen() {
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        r.startListening(intent())
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferOffline) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private fun release() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun commitPartial() {
        val partial = _state.value.partialText
        if (partial.isNotBlank()) append(partial)
        _state.value = _state.value.copy(partialText = "")
    }

    private fun append(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val current = _state.value.finalText
        val joined = if (current.isBlank()) clean else "$current ${clean}"
        _state.value = _state.value.copy(finalText = joined, partialText = "")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(status = DictationStatus.LISTENING, error = null)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // The platform reports roughly -2..10 dB; map it to a 0..1 bar height.
            val normalised = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.value = _state.value.copy(amplitude = normalised)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            when (error) {
                // A silence between sentences is normal chairside. Keep going.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> if (wantsToListen) listen() else Unit

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (wantsToListen) listen() else Unit

                else -> {
                    wantsToListen = false
                    _state.value = _state.value.copy(
                        status = DictationStatus.ERROR,
                        error = message(error),
                        amplitude = 0f,
                    )
                }
            }
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { append(it) }
            if (wantsToListen) listen()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = _state.value.copy(partialText = text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun message(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone problem. Check nothing else is using it."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required to dictate."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a connection, or turn on offline dictation in Settings."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition stopped unexpectedly. Tap to start again."
        SpeechRecognizer.ERROR_SERVER -> "The speech service returned an error. Try again."
        else -> "Dictation stopped. Tap the microphone to continue."
    }
}
