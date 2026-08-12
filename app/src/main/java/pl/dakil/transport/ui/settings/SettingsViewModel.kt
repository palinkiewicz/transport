package pl.dakil.transport.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.RecentPlacesRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.repo.CacheStats
import pl.dakil.transport.data.repo.PlaceCacheRepository
import pl.dakil.transport.data.repo.SavedItineraryRepository
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.OfflineCacheSettings
import pl.dakil.transport.domain.model.VehicleMotionSettings

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val placeCacheRepository: PlaceCacheRepository,
    private val savedItineraryRepository: SavedItineraryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentPlacesRepository: RecentPlacesRepository,
) : ViewModel() {

    // Held locally (seeded from disk once) rather than read straight from the repository flow,
    // so dragging a slider never races the DataStore write round-trip — same reasoning as the
    // map filter menu.
    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings

    /** How much the cache is holding; null until the first count comes back. */
    private val _cacheStats = MutableStateFlow<CacheStats?>(null)
    val cacheStats: StateFlow<CacheStats?> = _cacheStats

    init {
        viewModelScope.launch {
            _settings.value = settingsRepository.settings.first()
        }
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        viewModelScope.launch { _cacheStats.value = placeCacheRepository.stats() }
    }

    fun updateOfflineCache(transform: (OfflineCacheSettings) -> OfflineCacheSettings) =
        update { it.copy(offlineCache = transform(it.offlineCache)) }

    fun resetOfflineCache() = update { it.copy(offlineCache = OfflineCacheSettings.DEFAULT) }

    /**
     * Empties the cache, keeping everything the user starred — the starred places themselves and
     * every place a saved journey needs to render offline. Clearing is a way to reclaim space,
     * not a way to lose the trips someone pinned for a journey tomorrow.
     */
    fun clearCache() {
        viewModelScope.launch {
            placeCacheRepository.clear(protectedKeys())
            _cacheStats.value = placeCacheRepository.stats()
        }
    }

    private suspend fun protectedKeys(): List<String> =
        (
            favoritesRepository.favorites.first().locations.map { it.favoriteKey } +
                savedItineraryRepository.protectedPlaceKeys()
            ).distinct()

    fun update(transform: (AppSettings) -> AppSettings) {
        val previous = _settings.value
        val updated = transform(previous)
        _settings.value = updated
        viewModelScope.launch { settingsRepository.save(updated) }
        // Lowering the cap on remembered places has to forget the ones past it, here rather than
        // whenever the picker next happens to run: "Off" is a request to be left with nothing.
        if (updated.recentPlacesLimit < previous.recentPlacesLimit) {
            recentPlacesRepository.trimToLimit(updated.recentPlacesLimit)
        }
    }

    fun updateMotion(transform: (VehicleMotionSettings) -> VehicleMotionSettings) =
        update { it.copy(vehicleMotion = transform(it.vehicleMotion)) }

    fun resetAll() = update { AppSettings.DEFAULT }

    fun updateExport(transform: (ExportSettings) -> ExportSettings) =
        update { it.copy(export = transform(it.export)) }

    fun resetMotion() = update { it.copy(vehicleMotion = VehicleMotionSettings.DEFAULT) }

    fun resetItineraryExport() = update { it.copy(export = ExportSettings.DEFAULT) }

    fun resetLineColors() = update {
        it.copy(
            lineColorMode = AppSettings.DEFAULT.lineColorMode,
            customLineColors = AppSettings.DEFAULT.customLineColors,
        )
    }
}
