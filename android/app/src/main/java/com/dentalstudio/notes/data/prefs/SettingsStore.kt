package com.dentalstudio.notes.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dentalstudio.notes.domain.ToothNotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dental_notes_settings")

/** User preferences. The API key stays in the app's private storage and is never logged. */
data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val toothNotation: ToothNotation = ToothNotation.FDI,
    val clinicianName: String = "",
    val practiceName: String = "",
    val preferOfflineDictation: Boolean = false,
    val autoApplyLearning: Boolean = true,
    val onboarded: Boolean = false,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5"
        val MODELS = listOf(
            "claude-sonnet-5" to "Sonnet 5 — balanced, recommended",
            "claude-opus-5" to "Opus 5 — most thorough",
            "claude-haiku-4-5-20251001" to "Haiku 4.5 — fastest",
        )
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val NOTATION = stringPreferencesKey("tooth_notation")
        val CLINICIAN = stringPreferencesKey("clinician_name")
        val PRACTICE = stringPreferencesKey("practice_name")
        val OFFLINE_DICTATION = booleanPreferencesKey("offline_dictation")
        val AUTO_APPLY = booleanPreferencesKey("auto_apply_learning")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[Keys.API_KEY].orEmpty(),
            model = p[Keys.MODEL] ?: AppSettings.DEFAULT_MODEL,
            toothNotation = runCatching { ToothNotation.valueOf(p[Keys.NOTATION] ?: "") }
                .getOrDefault(ToothNotation.FDI),
            clinicianName = p[Keys.CLINICIAN].orEmpty(),
            practiceName = p[Keys.PRACTICE].orEmpty(),
            preferOfflineDictation = p[Keys.OFFLINE_DICTATION] ?: false,
            autoApplyLearning = p[Keys.AUTO_APPLY] ?: true,
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setApiKey(value: String) = edit { it[Keys.API_KEY] = value.trim() }
    suspend fun setModel(value: String) = edit { it[Keys.MODEL] = value }
    suspend fun setToothNotation(value: ToothNotation) = edit { it[Keys.NOTATION] = value.name }
    suspend fun setClinicianName(value: String) = edit { it[Keys.CLINICIAN] = value }
    suspend fun setPracticeName(value: String) = edit { it[Keys.PRACTICE] = value }
    suspend fun setPreferOfflineDictation(value: Boolean) = edit { it[Keys.OFFLINE_DICTATION] = value }
    suspend fun setAutoApplyLearning(value: Boolean) = edit { it[Keys.AUTO_APPLY] = value }
    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
