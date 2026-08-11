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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.R
import pl.dakil.transport.data.location.LocationService
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.SessionStateRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.data.repo.PlaceCacheRepository
import pl.dakil.transport.data.repo.PlaceSearchEngine
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.AppSettings
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
    sessionStateRepository: SessionStateRepository,
    private val settingsRepository: SettingsRepository,
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
     * distances shown and the optional distance sort; what the geocoder is biased toward is
     * [biasPosition], which can stand in for this when there is nothing here to measure from.
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
     * Where the geocoder is asked to pull its results toward. Normally [referencePosition]; with
     * neither a route neighbour nor a fix it falls back to wherever the map was last left, so a
     * search still has *somewhere* to be local to instead of ranging over the whole planet —
     * which is the case a fresh install or a denied location permission lands in.
     *
     * Deliberately separate from [referencePosition]: a forgotten map viewport is a fine hint for
     * *which* places to ask the server for, but a distance measured from it would read as a claim
     * about where the user actually is.
     */
    private val biasPosition: StateFlow<GeoPoint?> = flow {
        if (referencePosition != null) return@flow
        // With the setting off the stored camera is stale by the user's own choice — the same
        // gate MapViewModel writes it behind.
        if (!settingsRepository.settings.first().rememberMapCamera) return@flow
        val camera = sessionStateRepository.state.first().mapCamera ?: return@flow
        // Zoomed out past a city, the camera covers half a continent and biases toward nothing
        // in particular — MapCamera.DEFAULT is all of Europe.
        if (camera.zoom >= MIN_BIAS_CAMERA_ZOOM) emit(GeoPoint(camera.lat, camera.lon))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, referencePosition)

    /** How hard [biasPosition] pulls; see [AppSettings.searchBiasStrength]. */
    private val biasStrength: Flow<Int> =
        settingsRepository.settings.map { it.searchBiasStrength }

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
        combine(
            _query.debounce(300).distinctUntilChanged(),
            retryTicks,
            biasPosition,
            biasStrength,
        ) { query, _, bias, strength -> Triple(query, bias, strength) }
            .mapLatest { (query, bias, strength) ->
                if (query.isBlank()) {
                    _searchError.value = null
                    emptyList()
                } else {
                    geocodeRepository.suggest(
                        text = query,
                        biasLat = bias?.lat,
                        biasLon = bias?.lon,
                        stopsOnly = stopsOnly,
                        biasStrength = strength.toDouble(),
                    ).fold(
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

    private val keepFirstCachedResult: Flow<Boolean> =
        settingsRepository.settings.map { it.keepFirstCachedResult }

    private val offlineSearchEnabled: Flow<Boolean> =
        settingsRepository.settings.map { it.offlineCache.offlineSearchEnabled }

    /**
     * Places already on disk that match the query, ranked locally.
     *
     * Runs on the keystroke itself — no debounce — because it costs one disk query and no network
     * at all. This is what puts a plausible list under the user's finger while the geocoder is
     * still being waited on, and the whole list when there is no connection to wait on.
     * [suggestions] then merges in on top of it.
     */
    private val cachedMatches: Flow<List<TransitLocation>> =
        combine(_query, offlineSearchEnabled, biasPosition, biasStrength) { query, enabled, bias, strength ->
            CachedQuery(query, enabled, bias, strength)
        }
            .mapLatest { (query, enabled, bias, strength) ->
                val token = PlaceSearchEngine.candidateToken(query)
                if (!enabled || token == null) {
                    emptyList()
                } else {
                    val candidates = placeCacheRepository.search(
                        foldedToken = token,
                        stopsOnly = stopsOnly,
                        bias = bias,
                        limit = PlaceSearchEngine.CANDIDATE_LIMIT,
                    )
                    PlaceSearchEngine.rank(
                        query = query,
                        places = candidates,
                        // The same pull, at the same strength, the geocoder is asked for — so the
                        // two halves of the list agree on what "near" is worth.
                        bias = bias,
                        biasStrength = strength,
                        limit = MAX_CACHED_SUGGESTIONS,
                    )
                }
            }
            .onStart { emit(emptyList()) }

    /**
     * The best cached row, as the query it was found for and its key.
     *
     * Kept so the row that appeared on the keystroke can be held at the top when the geocoder's
     * answer lands; carrying the query with it is what makes it expire on the next keystroke
     * rather than pinning a stale place to a search it does not answer. A plain field because
     * only [items]' own `combine` touches it, and that runs sequentially in one coroutine.
     */
    private var pinnedCachedRow: Pair<String, String>? = null

    /** Suggestions while typing; the favourite places when the query is blank. */
    val items: StateFlow<List<PickerItem>> =
        combine(
            combine(_query, cachedMatches, suggestions, biasPosition, biasStrength, ::Ranking),
            favoritesRepository.favorites,
            sortByDistance,
            keepFirstCachedResult,
        ) { ranking, favorites, sortByDistance, keepFirstCached ->
            val (query, cached, remote, bias, strength) = ranking
            if (query.isNotBlank() && cached.isNotEmpty()) {
                pinnedCachedRow = query to cached.first().favoriteKey
            }
            val locations = if (query.isBlank()) {
                favorites.locations
            } else {
                PlaceSearchEngine.merge(
                    query = query,
                    remote = remote,
                    cached = cached,
                    bias = bias,
                    biasStrength = strength,
                    pinnedKey = pinnedCachedRow
                        ?.takeIf { keepFirstCached && it.first == query }
                        ?.second,
                )
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
            // Favourites are already in the order the user built them, and the ranking above
            // already prefers what is near; this is the opt-in that throws the match away and
            // sorts on nothing but distance.
            if (sortByDistance && query.isNotBlank() && referencePosition != null) {
                items.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            } else {
                items
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

    /** The inputs [cachedMatches] re-runs on; only a name for what `combine` produces. */
    private data class CachedQuery(
        val query: String,
        val offlineSearchEnabled: Boolean,
        val bias: GeoPoint?,
        val biasStrength: Int,
    )

    /**
     * Everything the ranking needs, as one value.
     *
     * `combine` has typed overloads for at most five flows and [items] needs more than that, so
     * the ranking inputs are combined into this first and the presentation inputs around it.
     */
    private data class Ranking(
        val query: String,
        val cached: List<TransitLocation>,
        val remote: List<TransitLocation>,
        val bias: GeoPoint?,
        val biasStrength: Int,
    )

    private companion object {
        /**
         * Below this the map's last camera covers a country or more, which biases toward nothing
         * in particular — a stored viewport only stands in for "near here" once it frames a city.
         */
        const val MIN_BIAS_CAMERA_ZOOM = 9.0

        /**
         * Cached matches offered alongside the geocoder's own. Kept near the geocoder's
         * `numResults` so a healthy connection reads as one list rather than a short authoritative
         * one followed by a long local tail.
         */
        const val MAX_CACHED_SUGGESTIONS = 12
    }
}
