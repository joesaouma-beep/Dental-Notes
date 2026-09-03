package com.dentalstudio.notes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentalstudio.notes.data.db.NoteEntity
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.StyleProfile
import com.dentalstudio.notes.util.Formatting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val notes: List<NoteEntity> = emptyList(),
    val notesThisWeek: Int = 0,
    val activeRules: Int = 0,
    val profile: StyleProfile = StyleProfile(),
    val edits: Int = 0,
) {
    val trimPercent: Int get() = (profile.trimRatio * 100).toInt()
    val isLearning: Boolean get() = activeRules > 0 || profile.samples > 0
}

class HomeViewModel(private val repository: NoteRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.allNotes,
        repository.notesSince(Formatting.startOfWeek()),
        repository.allRules,
        repository.globalProfile,
        repository.editCount,
    ) { notes, weekly, rules: List<LearnedRule>, profile, edits ->
        HomeUiState(
            notes = notes,
            notesThisWeek = weekly,
            activeRules = rules.count { it.isActive },
            profile = profile,
            edits = edits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
    }
}
