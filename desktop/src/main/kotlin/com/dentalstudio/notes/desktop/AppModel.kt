package com.dentalstudio.notes.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dentalstudio.notes.desktop.speech.DesktopDictation
import com.dentalstudio.notes.desktop.store.DesktopRepository
import com.dentalstudio.notes.desktop.store.LearningOutcome
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.generate.GenerationException
import com.dentalstudio.notes.speech.DictationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { NOTES, RECORD, ADAPT, SETTINGS }

/**
 * All mutable screen state for the desktop app.
 *
 * Compose state rather than flows: there is one window and one user, and this
 * keeps the screens easy to read.
 */
class AppModel(
    val repository: DesktopRepository,
    val dictation: DesktopDictation,
    private val scope: CoroutineScope,
) {
    var screen by mutableStateOf(Screen.NOTES)
        private set
    var selectedNoteId by mutableStateOf<Long?>(null)
        private set

    // New note
    var patientLabel by mutableStateOf("")
    var template by mutableStateOf(NoteTemplate.DEFAULT)
    var elapsedSeconds by mutableStateOf(0)
        private set
    var generating by mutableStateOf(false)
        private set
    var recordError by mutableStateOf<String?>(null)

    // Note editing
    var editing by mutableStateOf(false)
        private set
    var draftText by mutableStateOf("")
    var outcome by mutableStateOf<LearningOutcome?>(null)
        private set
    var banner by mutableStateOf<String?>(null)
        private set

    private var timerJob: Job? = null

    init {
        // Keep the dictation engine pointed at whatever model the settings name.
        scope.launch {
            repository.settings.collect { dictation.configure(it.voskModelPath) }
        }
    }

    fun go(target: Screen) {
        if (target != Screen.RECORD && screen == Screen.RECORD) discardRecording()
        screen = target
        if (target != Screen.NOTES) selectedNoteId = null
        clearBanner()
    }

    fun openNote(id: Long) {
        selectedNoteId = id
        screen = Screen.NOTES
        editing = false
        clearBanner()
    }

    fun closeNote() {
        selectedNoteId = null
        editing = false
    }

    fun newNote() {
        discardRecording()
        screen = Screen.RECORD
        selectedNoteId = null
    }

    // ── Dictation ─────────────────────────────────────────────────────────────

    fun toggleDictation() {
        if (dictation.state.value.status == DictationStatus.LISTENING) {
            dictation.pause()
            stopTimer()
        } else {
            dictation.start()
            if (dictation.state.value.status == DictationStatus.LISTENING) startTimer()
        }
    }

    fun setTranscript(text: String) = dictation.overwriteTranscript(text)

    fun discardRecording() {
        dictation.reset()
        stopTimer()
        elapsedSeconds = 0
        patientLabel = ""
        recordError = null
        generating = false
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ── Generation ────────────────────────────────────────────────────────────

    fun generate() {
        val transcript = dictation.state.value.fullText
        if (transcript.isBlank()) {
            recordError = "Nothing has been dictated or typed yet."
            return
        }
        dictation.pause()
        stopTimer()
        generating = true
        recordError = null

        scope.launch {
            try {
                val generated = repository.generate(transcript, template, patientLabel)
                val id = repository.createNote(patientLabel, template.id, transcript, generated, elapsedSeconds)
                generating = false
                discardRecording()
                openNote(id)
            } catch (e: GenerationException) {
                generating = false
                recordError = e.message
            } catch (e: Exception) {
                generating = false
                recordError = e.message ?: "The note could not be generated."
            }
        }
    }

    // ── Editing and learning ──────────────────────────────────────────────────

    fun startEditing(current: String) {
        draftText = current
        editing = true
        clearBanner()
    }

    fun cancelEditing() {
        editing = false
        draftText = ""
    }

    fun saveEdit() {
        val id = selectedNoteId ?: return
        val text = draftText
        if (text.isBlank()) {
            banner = "A note cannot be saved empty."
            return
        }
        scope.launch {
            val result = repository.saveEdit(id, text)
            editing = false
            draftText = ""
            outcome = result
            banner = when {
                result == null -> "Saved."
                result.learnedSomething -> null
                else -> "Saved. Nothing new to learn from this one."
            }
        }
    }

    fun clearBanner() {
        banner = null
        outcome = null
    }

    fun deleteNote(id: Long) {
        scope.launch {
            repository.deleteNote(id)
            if (selectedNoteId == id) closeNote()
        }
    }

    // ── Learned preferences ───────────────────────────────────────────────────

    fun setRuleEnabled(id: Long, enabled: Boolean) = scope.launch { repository.setRuleEnabled(id, enabled) }
    fun forgetRule(id: Long) = scope.launch { repository.deleteRule(id) }
    fun forgetEverything() = scope.launch { repository.resetLearning() }
    fun wipeEverything() = scope.launch { repository.wipeEverything() }

    fun updateSettings(block: (com.dentalstudio.notes.desktop.store.DesktopSettings) -> com.dentalstudio.notes.desktop.store.DesktopSettings) {
        scope.launch { repository.updateSettings(block) }
    }
}
