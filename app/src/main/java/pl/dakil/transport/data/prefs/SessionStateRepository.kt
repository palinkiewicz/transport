package pl.dakil.transport.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.dakil.transport.domain.model.MapCamera
import pl.dakil.transport.domain.model.SessionState

private val Context.sessionStateDataStore by preferencesDataStore(name = "session_state")

private val SESSION_KEY = stringPreferencesKey("session")

/** Persists what the app resumes with: the search forms' picks and the map's camera. */
@Singleton
class SessionStateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    /** Stored as one JSON blob so [SessionState] can grow fields without a prefs migration. */
    val state: Flow<SessionState> = context.sessionStateDataStore.data.map { prefs ->
        prefs[SESSION_KEY]
            ?.let { stored -> runCatching { json.decodeFromString<SessionState>(stored) }.getOrNull() }
            ?: SessionState.EMPTY
    }

    private suspend fun update(transform: (SessionState) -> SessionState) {
        val updated = transform(state.first())
        context.sessionStateDataStore.edit { prefs ->
            prefs[SESSION_KEY] = json.encodeToString(SessionState.serializer(), updated)
        }
    }

    /** The search halves and the camera are written by different screens; neither clobbers the other. */
    suspend fun saveSearch(state: SessionState) = update {
        it.copy(
            from = state.from,
            to = state.to,
            vias = state.vias,
            departureStop = state.departureStop,
        )
    }

    suspend fun saveMapCamera(camera: MapCamera) = update { it.copy(mapCamera = camera) }
}
