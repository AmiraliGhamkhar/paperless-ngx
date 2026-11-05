package studio.vela.art.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.velaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vela_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.velaSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> decode(preferences[SETTINGS_KEY]) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SETTINGS_KEY])
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    suspend fun current(): AppSettings = decode(dataStore.data.first()[SETTINGS_KEY])

    private fun decode(raw: String?): AppSettings {
        if (raw == null) return AppSettings()
        return try {
            json.decodeFromString(AppSettings.serializer(), raw)
        } catch (e: kotlinx.serialization.SerializationException) {
            // Corrupt settings must never block startup: fall back to defaults.
            AppSettings()
        }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("settings_json")
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
