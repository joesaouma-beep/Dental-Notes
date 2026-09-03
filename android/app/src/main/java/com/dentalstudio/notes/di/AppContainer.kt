package com.dentalstudio.notes.di

import android.content.Context
import com.dentalstudio.notes.data.db.AppDatabase
import com.dentalstudio.notes.generate.AnthropicNoteGenerator
import com.dentalstudio.notes.data.prefs.SettingsStore
import com.dentalstudio.notes.data.repo.NoteRepository
import com.dentalstudio.notes.speech.DictationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import com.dentalstudio.notes.data.prefs.AppSettings

/** Hand-rolled dependency graph. The app is small enough not to need a framework. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore: SettingsStore = SettingsStore(appContext)

    private val settingsState = settingsStore.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val database: AppDatabase = AppDatabase.get(appContext)

    private val generator = AnthropicNoteGenerator(
        apiKeyProvider = { settingsState.value.apiKey },
        modelProvider = { settingsState.value.model },
    )

    val repository: NoteRepository = NoteRepository(
        db = database,
        settingsStore = settingsStore,
        remoteGenerator = generator,
    )

    val dictation: DictationController = DictationController(appContext)
}
