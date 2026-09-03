package com.dentalstudio.notes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentalstudio.notes.data.prefs.AppSettings
import com.dentalstudio.notes.data.prefs.SettingsStore
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.domain.ToothNotation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val repository: NoteRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setApiKey(value: String) = viewModelScope.launch { store.setApiKey(value) }
    fun setModel(value: String) = viewModelScope.launch { store.setModel(value) }
    fun setToothNotation(value: ToothNotation) = viewModelScope.launch { store.setToothNotation(value) }
    fun setClinicianName(value: String) = viewModelScope.launch { store.setClinicianName(value) }
    fun setPracticeName(value: String) = viewModelScope.launch { store.setPracticeName(value) }
    fun setOfflineDictation(value: Boolean) = viewModelScope.launch { store.setPreferOfflineDictation(value) }
    fun setAutoApply(value: Boolean) = viewModelScope.launch { store.setAutoApplyLearning(value) }
    fun resetLearning() = viewModelScope.launch { repository.resetLearning() }
    fun wipeEverything() = viewModelScope.launch { repository.wipeEverything() }
}
