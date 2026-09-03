package com.dentalstudio.notes.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentalstudio.notes.data.db.NoteEntity
import com.dentalstudio.notes.data.repo.LearningOutcome
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.domain.NoteTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteTab { NOTE, TRANSCRIPT, INSIGHTS }

data class NoteUiState(
    val note: NoteEntity? = null,
    val tab: NoteTab = NoteTab.NOTE,
    val editing: Boolean = false,
    val draftText: String = "",
    val saving: Boolean = false,
    val outcome: LearningOutcome? = null,
    val message: String? = null,
) {
    val template: NoteTemplate get() = NoteTemplate.byId(note?.templateId)
    val displayText: String get() = note?.finalNote?.ifBlank { note.draftNote }.orEmpty()
    val hasUnsavedChanges: Boolean get() = editing && draftText.trim() != displayText.trim()
}

class NoteViewModel(
    private val repository: NoteRepository,
    private val noteId: Long,
) : ViewModel() {

    private val local = MutableStateFlow(NoteUiState())

    val state: StateFlow<NoteUiState> = combine(local, repository.note(noteId)) { local, note ->
        local.copy(note = note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteUiState())

    fun selectTab(tab: NoteTab) {
        local.value = local.value.copy(tab = tab)
    }

    fun startEditing(current: String) {
        local.value = local.value.copy(editing = true, draftText = current, outcome = null)
    }

    fun updateDraft(text: String) {
        local.value = local.value.copy(draftText = text)
    }

    fun cancelEditing() {
        local.value = local.value.copy(editing = false, draftText = "")
    }

    /**
     * Saves the edit and reports what it taught the app, so the clinician can
     * see the connection between correcting a note and better drafts later.
     */
    fun saveEdit() {
        val text = local.value.draftText
        if (text.isBlank()) {
            local.value = local.value.copy(message = "A note cannot be saved empty.")
            return
        }
        local.value = local.value.copy(saving = true)
        viewModelScope.launch {
            val outcome = repository.saveEdit(noteId, text)
            local.value = local.value.copy(
                saving = false,
                editing = false,
                draftText = "",
                outcome = outcome,
                message = when {
                    outcome == null -> "Saved."
                    outcome.learnedSomething -> null
                    else -> "Saved. Nothing new to learn from this one."
                },
            )
        }
    }

    fun dismissMessage() {
        local.value = local.value.copy(message = null, outcome = null)
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            onDone()
        }
    }
}
