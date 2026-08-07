package pl.dakil.transport.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.SettingsRepository

/**
 * Keeps the place cache within the size the user allowed.
 *
 * A separate object rather than a method on [PlaceCacheRepository] because working out what must
 * *not* be evicted means reading the starred places and the saved journeys — and
 * [SavedItineraryRepository] already depends on the cache, so having the cache depend back on it
 * would be a dependency cycle.
 */
@Singleton
class CacheMaintenance @Inject constructor(
    private val placeCacheRepository: PlaceCacheRepository,
    private val savedItineraryRepository: SavedItineraryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Trims the oldest places once the cache outgrows its limit.
     *
     * Called after a refresh has written to the cache — the only moment it can have grown. Never
     * touches a starred place or anything a saved journey needs to draw offline: the limit is
     * about reclaiming space, not about quietly dropping what the user asked to keep.
     */
    suspend fun trim() {
        val limit = settingsRepository.settings.first().offlineCache.maxCachedPlaces
        val protectedKeys = (
            favoritesRepository.favorites.first().locations.map { it.favoriteKey } +
                savedItineraryRepository.protectedPlaceKeys()
            ).distinct()
        placeCacheRepository.evictIfOversized(limit, protectedKeys)
    }
}
