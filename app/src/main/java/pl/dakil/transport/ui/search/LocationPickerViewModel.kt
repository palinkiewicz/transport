package pl.dakil.transport.ui.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.R
import pl.dakil.transport.data.location.LocationService
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.data.repo.PlaceCacheRepository
import pl.dakil.transport.data.repo.PlaceSearchEngine
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.PickerTarget

/** One row of the location picker list. */
data class PickerItem(
    val location: TransitLocation,
    /** Straight-line distance from the reference point; null when there is none to measure from. */
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
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val geocodeRepository: GeocodeRepository,
    private val placeCacheRepository: PlaceCacheRepository,
    locationService: LocationService,
    private val favoritesRepository: FavoritesRepository,
    private val searchStateHolder: SearchStateHolder,
    settingsRepository: SettingsRepository,
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
     * What "near" means for this pick: the route point next to the one being filled — the leg
     * is measured from there, not from wherever the phone happens to be. The route reads
     * `from → vias… → to`, so a pick is measured against its neighbour on the way in, or, when
     * that end of the route is still empty, its neighbour on the way out. Everything else (and
     * a route with nothing else filled in) falls back to the current position. Drives the
     * geocoder's bias, the distances shown, and the optional distance sort.
     */
    private val referencePosition: GeoPoint? = routeNeighbour()?.let { GeoPoint(it.lat, it.lon) } ?: userPosition

    /**
     * The already-picked location adjacent to the slot being filled, if any. Read once at
     * construction: the form cannot change while the picker is on top of it.
     */
    private fun routeNeighbour(): TransitLocation? {
        val from = searchStateHolder.from.value
        val to = searchStateHolder.to.value
        val vias = searchStateHolder.vias.value.map { it.location }
        // The whole route in travel order, with the slot being filled left out of its own
        // neighbourhood (an unfilled "Add stop" slot sits past the end of the list).
        val before: List<TransitLocation?>
        val after: List<TransitLocation?>
        when (target) {
            PickerTarget.FROM -> {
                before = emptyList()
                after = vias + to
            }
            PickerTarget.TO -> {
                before = (listOf(from) + vias).reversed()
                after = emptyList()
            }
            PickerTarget.VIA -> {
                before = (listOf(from) + vias.take(viaIndex)).reversed()
                after = vias.drop(viaIndex + 1) + to
            }
            else -> return null
        }
        return before.firstOrNull { it != null } ?: after.firstOrNull { it != null }
    }

    /**
     * "Your location" entry for the empty-query list; null without permission or a fix, and
     * withheld when only stops are offerable — a raw fix is a coordinate, never a stop.
     */
    val currentLocation: TransitLocation? =
        userPosition?.takeIf { !stopsOnly }?.let {
            TransitLocation.currentPosition(it.lat, it.lon, context.getString(R.string.location_your_location))
        }

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
                    geocodeRepository.suggest(query, referencePosition?.lat, referencePosition?.lon, stopsOnly).fold(
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

    private val sortByDistance: Flow<Boolean> =
        settingsRepository.settings.map { it.sortSuggestionsByDistance }

    private val offlineSearchEnabled: Flow<Boolean> =
        settingsRepository.settings.map { it.offlineCache.offlineSearchEnabled }

    /**
     * Places already on disk that match the query, ranked locally.
     *
     * Runs on the keystroke itself — no debounce — because it costs one indexed query and no
     * network at all. This is what puts a plausible list under the user's finger while the
     * geocoder is still being waited on, and the whole list when there is no connection to wait
     * on. [suggestions] then merges in on top of it.
     */
    private val cachedMatches: Flow<List<TransitLocation>> =
        combine(_query, sortByDistance, offlineSearchEnabled) { query, sortByDistance, enabled ->
            Triple(query, sortByDistance, enabled)
        }
            .mapLatest { (query, sortByDistance, enabled) ->
                if (!enabled || query.isBlank()) {
                    emptyList()
                } else {
                    val candidates = placeCacheRepository.search(
                        foldedQuery = foldForSearch(query),
                        stopsOnly = stopsOnly,
                        limit = PlaceSearchEngine.CANDIDATE_LIMIT,
                    )
                    PlaceSearchEngine.rank(
                        query = query,
                        places = candidates,
                        reference = referencePosition,
                        distanceWeight = if (sortByDistance) {
                            PlaceSearchEngine.DISTANCE_WEIGHT_WHEN_SORTING
                        } else {
                            0.0
                        },
                        limit = MAX_CACHED_SUGGESTIONS,
                    )
                }
            }
            .onStart { emit(emptyList()) }

    /** Suggestions while typing; the favourite places when the query is blank. */
    val items: StateFlow<List<PickerItem>> =
        combine(
            _query,
            cachedMatches,
            suggestions,
            favoritesRepository.favorites,
            sortByDistance,
        ) { query, cached, suggestions, favorites, sortByDistance ->
            val locations = if (query.isBlank()) {
                favorites.locations
            } else {
                mergeSuggestions(suggestions, cached)
            }
                // A starred address or map point can't stand in for a stop id.
                .filter { !stopsOnly || it.stopId != null }
            val items = locations.map { location ->
                PickerItem(
                    location = location,
                    distanceMeters = referencePosition?.let {
                        GeoPoint(location.lat, location.lon).distanceMetersTo(it)
                    },
                    isFavorite = favorites.containsLocation(location),
                )
            }
            // Favourites are already in the order the user built them; only the geocoder's
            // ranking is worth overriding, and only when there is a point to measure from.
            if (sortByDistance && query.isNotBlank() && referencePosition != null) {
                items.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            } else {
                items
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The geocoder's answers and the cache's, as one list with each station appearing once.
     *
     * The two sources describe the same places differently: the geocoder returns the grouped
     * station, carrying its city and district, while the cache holds the individual platforms the
     * map fetched, which carry neither. Concatenating them shows the same stop several times over
     * — once from the geocoder and once per cached pole — so they are clustered together instead
     * and each station is offered as the member that knows the most about it.
     *
     * Ordering keeps the geocoder in charge where it answered: it ranks against the whole world,
     * not just what this phone has seen. Stations only the cache knows follow, in the local
     * ranking's order — which is what is left when there is no connection at all.
     */
    private fun mergeSuggestions(
        remote: List<TransitLocation>,
        cached: List<TransitLocation>,
    ): List<TransitLocation> {
        if (remote.isEmpty()) return cached
        val remoteRank = remote.withIndex().associate { (index, place) -> place.favoriteKey to index }
        // Remote first, so where both sources hold the very same place the geocoder's copy — the
        // one carrying the city and district — is the one kept.
        return PlaceSearchEngine.groupIntoStations(remote + cached)
            .sortedBy { station ->
                station.members.minOfOrNull { remoteRank[it.favoriteKey] ?: Int.MAX_VALUE }
                    ?: Int.MAX_VALUE
            }
            .map { it.place }
    }

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

    private companion object {
        /**
         * Cached matches offered alongside the geocoder's own. Kept near the geocoder's
         * `numResults` so a healthy connection reads as one list rather than a short authoritative
         * one followed by a long local tail.
         */
        const val MAX_CACHED_SUGGESTIONS = 12
    }
}
