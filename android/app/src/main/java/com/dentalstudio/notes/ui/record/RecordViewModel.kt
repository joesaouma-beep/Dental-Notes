package com.dentalstudio.notes.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentalstudio.notes.data.generate.GenerationException
import com.dentalstudio.notes.data.prefs.AppSettings
import com.dentalstudio.notes.data.prefs.SettingsStore
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.speech.DictationController
import com.dentalstudio.notes.speech.DictationState
import com.dentalstudio.notes.speech.DictationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the generation is up to, for the progress screen. */
enum class GenerationStage { IDLE, STRUCTURING, APPLYING_STYLE, DONE }

data class RecordUiState(
    val patientLabel: String = "",
    val template: NoteTemplate = NoteTemplate.DEFAULT,
    val dictation: DictationState = DictationState(),
    val elapsedSeconds: Int = 0,
    val manualEntry: Boolean = false,
    val manualText: String = "",
    val stage: GenerationStage = GenerationStage.IDLE,
    val error: String? = null,
    val settings: AppSettings = AppSettings(),
    val savedNoteId: Long? = null,
    val learnedPreferences: Int = 0,
) {
    val isRecording: Boolean get() = dictation.status == DictationStatus.LISTENING
    val isPaused: Boolean get() = dictation.status == DictationStatus.PAUSED
    val isGenerating: Boolean get() = stage == GenerationStage.STRUCTURING || stage == GenerationStage.APPLYING_STYLE
    val transcript: String get() = if (manualEntry) manualText else dictation.fullText
    val canGenerate: Boolean get() = transcript.trim().length >= 12 && !isGenerating
}

class RecordViewModel(
    private val repository: NoteRepository,
    private val dictation: DictationController,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val local = MutableStateFlow(RecordUiState())
    private var timerJob: Job? = null

    val state: StateFlow<RecordUiState> = combine(
        local,
        dictation.state,
        settingsStore.settings,
    ) { local, dict, settings ->
        local.copy(dictation = dict, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordUiState())

    val speechAvailable: Boolean get() = dictation.isAvailable

    init {
        viewModelScope.launch {
            repository.allRules.collect { rules ->
                local.value = local.value.copy(learnedPreferences = rules.count { it.isActive })
            }
        }
    }

    fun setPatientLabel(value: String) {
        local.value = local.value.copy(patientLabel = value)
    }

    fun setTemplate(template: NoteTemplate) {
        local.value = local.value.copy(template = template)
    }

    fun setManualText(value: String) {
        local.value = local.value.copy(manualText = value)
    }

    fun toggleManualEntry() {
        val next = !local.value.manualEntry
        if (next) dictation.pause()
        local.value = local.value.copy(
            manualEntry = next,
            manualText = if (next && local.value.manualText.isBlank()) dictation.state.value.fullText else local.value.manualText,
        )
    }

    fun startOrPause(preferOffline: Boolean) {
        when (dictation.state.value.status) {
            DictationStatus.LISTENING -> {
                dictation.pause()
                stopTimer()
            }
            else -> {
                dictation.start(preferOffline)
                startTimer()
            }
        }
    }

    fun editTranscript(text: String) = dictation.overwriteTranscript(text)

    fun discard() {
        dictation.reset()
        stopTimer()
        local.value = RecordUiState(settings = local.value.settings, learnedPreferences = local.value.learnedPreferences)
    }

    fun dismissError() {
        local.value = local.value.copy(error = null)
    }

    /** Generates the note, saves it, and hands back the id for navigation. */
    fun generate() {
        val current = local.value
        val transcript = if (current.manualEntry) current.manualText else dictation.state.value.fullText
        if (transcript.isBlank()) {
            local.value = current.copy(error = "Nothing has been dictated yet.")
            return
        }

        dictation.pause()
        stopTimer()
        local.value = current.copy(stage = GenerationStage.STRUCTURING, error = null)

        viewModelScope.launch {
            try {
                val generated = repository.generate(
                    transcript = transcript,
                    template = current.template,
                    patientLabel = current.patientLabel,
                )
                local.value = local.value.copy(stage = GenerationStage.APPLYING_STYLE)
                val id = repository.createNote(
                    patientLabel = current.patientLabel,
                    templateId = current.template.id,
                    transcript = transcript,
                    generated = generated,
                    durationSec = local.value.elapsedSeconds,
                )
                local.value = local.value.copy(stage = GenerationStage.DONE, savedNoteId = id)
            } catch (e: GenerationException) {
                local.value = local.value.copy(stage = GenerationStage.IDLE, error = e.message)
            } catch (e: Exception) {
                local.value = local.value.copy(
                    stage = GenerationStage.IDLE,
                    error = e.message ?: "The note could not be generated.",
                )
            }
        }
    }

    fun consumeSavedNote() {
        dictation.reset()
        local.value = RecordUiState(settings = local.value.settings, learnedPreferences = local.value.learnedPreferences)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                local.value = local.value.copy(elapsedSeconds = local.value.elapsedSeconds + 1)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopTimer()
        dictation.stop()
        super.onCleared()
    }
}
