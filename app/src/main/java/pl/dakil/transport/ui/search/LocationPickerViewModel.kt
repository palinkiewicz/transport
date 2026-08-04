package pl.dakil.transport.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.location.LocationService
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.PickerTarget

/** One row of the location picker list. */
data class PickerItem(
    val location: TransitLocation,
    /** Straight-line distance from the user's last known position; null without a fix. */
    val distanceMeters: Double?,
    val isFavorite: Boolean,
)

/**
 * Full-screen start/destination picker (opened from the Search screen's fields). With an
 * empty query it offers the current location and the favourite places; typing searches the
 * geocoder. The chosen location is handed back through [SearchStateHolder].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geocodeRepository: GeocodeRepository,
    locationService: LocationService,
    private val favoritesRepository: FavoritesRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    /** What the pick fills: a Search screen field, or the map's selection. */
    val target: PickerTarget = PickerTarget.valueOf(savedStateHandle["target"]!!)

    /** Which intermediate-stop slot to fill; only meaningful for [PickerTarget.VIA]. */
    private val viaIndex: Int = savedStateHandle["viaIndex"] ?: 0

    /**
     * Intermediate stops must be transit stops — the plan API rejects coordinates for `via` —
     * so the picker offers nothing that could not be used there.
     */
    val stopsOnly: Boolean = target == PickerTarget.VIA

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val userPosition: GeoPoint? =
        locationService.lastKnownLocation()?.let { GeoPoint(it.lat, it.lon) }

    /**
     * "Your location" entry for the empty-query list; null without permission or a fix, and
     * withheld when only stops are offerable — a raw fix is a coordinate, never a stop.
     */
    val currentLocation: TransitLocation? =
        userPosition?.takeIf { !stopsOnly }?.let { TransitLocation.currentPosition(it.lat, it.lon) }

    /** Why the last suggestion lookup came back empty; null while search is healthy. */
    private val _searchError = MutableStateFlow<AppError?>(null)
    val searchError: StateFlow<AppError?> = _searchError

    /** Bumped by [retrySearch] to re-run the current query after a failure. */
    private val retryTicks = MutableStateFlow(0)

    private val suggestions: Flow<List<TransitLocation>> =
        combine(_query.debounce(300).distinctUntilChanged(), retryTicks) { query, _ -> query }
            .mapLatest { query ->
                if (query.isBlank()) {
                    _searchError.value = null
                    emptyList()
                } else {
                    geocodeRepository.suggest(query, userPosition?.lat, userPosition?.lon, stopsOnly).fold(
                        onSuccess = { results ->
                            _searchError.value = null
                            results
                        },
                        onFailure = { error ->
                            // mapLatest cancels the in-flight lookup on every keystroke, and
                            // the repository's runCatching turns that into a failure — ignore it.
                            if (error !is CancellationException) _searchError.value = error.toAppError()
                            emptyList()
                        },
                    )
                }
            }
            .onStart { emit(emptyList()) }

    /** Re-runs the current query after a failed lookup. */
    fun retrySearch() {
        retryTicks.value++
    }

    /** Suggestions while typing; the favourite places when the query is blank. */
    val items: StateFlow<List<PickerItem>> =
        combine(_query, suggestions, favoritesRepository.favorites) { query, suggestions, favorites ->
            val locations = (if (query.isBlank()) favorites.locations else suggestions)
                // A starred address or map point can't stand in for a stop id.
                .filter { !stopsOnly || it.stopId != null }
            locations.map { location ->
                PickerItem(
                    location = location,
                    distanceMeters = userPosition?.let { GeoPoint(location.lat, location.lon).distanceMetersTo(it) },
                    isFavorite = favorites.containsLocation(location),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun toggleFavorite(location: TransitLocation) {
        viewModelScope.launch { favoritesRepository.toggleLocation(location) }
    }

    /** Hands the pick back to its consumer; the screen pops itself right after. */
    fun select(location: TransitLocation) {
        when (target) {
            PickerTarget.FROM -> searchStateHolder.setBeginHere(location)
            PickerTarget.TO -> searchStateHolder.setFinishHere(location)
            PickerTarget.VIA -> searchStateHolder.setVia(viaIndex, location)
            PickerTarget.STOP -> searchStateHolder.setDepartureStop(location)
            PickerTarget.MAP -> searchStateHolder.setMapLocation(location)
        }
    }
}
