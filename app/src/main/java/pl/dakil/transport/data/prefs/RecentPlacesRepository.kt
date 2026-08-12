package pl.dakil.transport.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.dakil.transport.domain.model.RecentPlaces
import pl.dakil.transport.domain.model.TransitLocation

private val Context.recentPlacesDataStore by preferencesDataStore(name = "recent_places")

private val RECENT_PLACES_KEY = stringPreferencesKey("recent_places")

/**
 * Remembers the places the user picked lately, so the location picker can offer them back.
 *
 * Kept in prefs rather than in Room: this is the user's own history of choices, not something
 * fetched from the API — the same reason the starred places live next door in
 * [FavoritesRepository].
 */
@Singleton
class RecentPlacesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) {
    /** Its own scope: [record] outlives the picker that calls it; see there. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Stored as one JSON blob so [RecentPlaces] can grow fields without a prefs migration. */
    val recentPlaces: Flow<List<TransitLocation>> = context.recentPlacesDataStore.data.map { prefs ->
        prefs[RECENT_PLACES_KEY].decodeOrEmpty().places
    }

    /**
     * Drops everything past [limit] — which at zero means the whole history.
     *
     * Called when the cap is *lowered*, because shortening it is a request to forget rather than
     * merely to show fewer: the setting's own copy promises that turning it off clears what was
     * stored, and a history nobody can see is still a history sitting on disk. [limit] is passed
     * in rather than read back, since the write that changed it may not have landed yet.
     */
    fun trimToLimit(limit: Int) {
        scope.launch {
            context.recentPlacesDataStore.edit { prefs ->
                val stored = prefs[RECENT_PLACES_KEY].decodeOrEmpty()
                if (stored.places.size <= limit) return@edit
                prefs[RECENT_PLACES_KEY] = json.encodeToString(
                    RecentPlaces.serializer(),
                    stored.copy(places = stored.places.take(limit.coerceAtLeast(0))),
                )
            }
        }
    }

    /**
     * Files [location] as the most recent pick.
     *
     * Fire-and-forget rather than `suspend`, and on this singleton's own scope, because the
     * picker records a pick as the very same tap pops the screen: a write launched in its
     * ViewModel's scope would be cancelled about as often as it landed. Reads the cap itself for
     * the same reason — it is part of what recording means, not something the caller should have
     * had to look up first.
     */
    fun record(location: TransitLocation) {
        scope.launch {
            val limit = settingsRepository.settings.first().recentPlacesLimit
            context.recentPlacesDataStore.edit { prefs ->
                val stored = prefs[RECENT_PLACES_KEY].decodeOrEmpty()
                prefs[RECENT_PLACES_KEY] =
                    json.encodeToString(RecentPlaces.serializer(), stored.record(location, limit))
            }
        }
    }

    private fun String?.decodeOrEmpty(): RecentPlaces =
        this?.let { stored -> runCatching { json.decodeFromString<RecentPlaces>(stored) }.getOrNull() }
            ?: RecentPlaces.EMPTY
}
