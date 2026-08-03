package pl.dakil.transport.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.dakil.transport.domain.model.AppSettings

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

private val SETTINGS_KEY = stringPreferencesKey("settings")

/** Persists the app-wide settings edited on the Settings screen. */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    /** Stored as one JSON blob so [AppSettings] can grow fields without a prefs migration. */
    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        prefs[SETTINGS_KEY]
            ?.let { stored -> runCatching { json.decodeFromString<AppSettings>(stored) }.getOrNull() }
            ?: AppSettings.DEFAULT
    }

    suspend fun save(settings: AppSettings) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }
}
