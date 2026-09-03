package com.dentalstudio.notes.ui.adapt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.RuleType
import com.dentalstudio.notes.learning.StyleProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdaptUiState(
    val rules: List<LearnedRule> = emptyList(),
    val profile: StyleProfile = StyleProfile(),
    val edits: Int = 0,
) {
    val active: List<LearnedRule> get() = rules.filter { it.isActive }
    val emerging: List<LearnedRule> get() = rules.filter { !it.isActive && it.enabled }
    val byType: Map<RuleType, List<LearnedRule>> get() = active.groupBy { it.type }
    val autoApplied: Int get() = rules.count { it.isAutoApplied }
    val trimPercent: Int get() = (profile.trimRatio * 100).toInt()
}

class AdaptViewModel(private val repository: NoteRepository) : ViewModel() {

    val state: StateFlow<AdaptUiState> = combine(
        repository.allRules,
        repository.globalProfile,
        repository.editCount,
    ) { rules, profile, edits ->
        AdaptUiState(rules = rules, profile = profile, edits = edits)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdaptUiState())

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setRuleEnabled(id, enabled) }
    }

    fun forget(id: Long) {
        viewModelScope.launch { repository.deleteRule(id) }
    }

    fun forgetEverything() {
        viewModelScope.launch { repository.resetLearning() }
    }
}
