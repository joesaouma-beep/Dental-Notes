package com.dentalstudio.notes.ui.nav

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dentalstudio.notes.di.AppContainer
import com.dentalstudio.notes.ui.adapt.AdaptViewModel
import com.dentalstudio.notes.ui.home.HomeViewModel
import com.dentalstudio.notes.ui.note.NoteViewModel
import com.dentalstudio.notes.ui.record.RecordViewModel
import com.dentalstudio.notes.ui.settings.SettingsViewModel

fun homeFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(container.repository) }
}

fun recordFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { RecordViewModel(container.repository, container.dictation, container.settingsStore) }
}

fun noteFactory(container: AppContainer, noteId: Long): ViewModelProvider.Factory = viewModelFactory {
    initializer { NoteViewModel(container.repository, noteId) }
}

fun adaptFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { AdaptViewModel(container.repository) }
}

fun settingsFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { SettingsViewModel(container.settingsStore, container.repository) }
}
