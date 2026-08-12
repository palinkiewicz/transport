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
import pl.dakil.transport.data.prefs.RecentPlacesRepository
import pl.dakil.transport.data.prefs.SessionStateRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.data.repo.PlaceCacheRepository
import pl.dakil.transport.data.repo.PlaceSearchEngine
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.PickerTarget

/** One row of the location picker list. */
data class PickerItem(
    val location: TransitLocation,
    /** Straight-line distance from the reference point; null when there is none to measure from. */
    val distanceMeters: Double?,
    val isFavorite: Boolean,
    /** Whether this is a place the user picked before; drawn with a history icon. */
    val isRecent: Boolean = false,
)

/**
 * Full-screen start/destination picker (opened from the Search screen's fields). With an
 * empty query it offers the current location, the favourite places and the recently used ones;
 * typing searches the geocoder. The chosen location is handed back through [SearchStateHolder].
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
    private val recentPlacesRepository: RecentPlacesRepository,
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

    private val pinRecentPlaces: Flow<Boolean> =
        settingsRepository.settings.map { it.pinRecentPlaces }

    /**
     * The places picked lately, newest first — the same history whatever field this picker is
     * filling, and trimmed here rather than only at write time so shortening the setting takes
     * effect at once instead of on the next pick.
     */
    private val recentPlaces: Flow<List<TransitLocation>> =
        combine(
            recentPlacesRepository.recentPlaces,
            settingsRepository.settings.map { it.recentPlacesLimit }.distinctUntilChanged(),
        ) { places, limit ->
            places.take(limit)
                // A remembered address can't stand in for a stop id, same as a starred one.
                .filter { !stopsOnly || it.stopId != null }
        }

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
            combine(
                favoritesRepository.favorites,
                sortByDistance,
                keepFirstCachedResult,
                recentPlaces,
                pinRecentPlaces,
                ::Presentation,
            ),
        ) { ranking, presentation ->
            val (query, cached, remote, bias, strength) = ranking
            val (favorites, sortByDistance, keepFirstCached, recents, pinRecents) = presentation
            if (query.isNotBlank() && cached.isNotEmpty()) {
                pinnedCachedRow = query to cached.first().favoriteKey
            }
            val items = if (query.isBlank()) {
                favorites.locations
                    // A starred address or map point can't stand in for a stop id.
                    .filter { !stopsOnly || it.stopId != null }
                    .map { location ->
                        PickerItem(
                            location = location,
                            distanceMeters = distanceOf(location),
                            isFavorite = true,
                            // Not marked recent even when it also is: this is the Saved section,
                            // and its rows keep their mode icons rather than turning into clocks
                            // as the places behind them get used.
                            isRecent = false,
                        )
                    }
            } else {
                // Recents are local and answer on the keystroke, like the cached rows — so a
                // pinned one is on screen from the first frame and never arrives late enough to
                // move the row under the user's finger. Offered as a source of their own as well
                // as pinned, or a place neither the cache nor the geocoder returned this time
                // could not be pinned at all.
                val recentMatches = if (pinRecents) {
                    recents.filter { PlaceSearchEngine.score(query, it) != null }
                } else {
                    emptyList()
                }
                val recentKeys = recentMatches.map { it.favoriteKey }.toSet()
                PlaceSearchEngine.mergeStations(
                    query = query,
                    remote = remote,
                    cached = cached + recentMatches,
                    bias = bias,
                    biasStrength = strength,
                    // Recents lead: a place the user has actually been to is a stronger claim
                    // about what they mean than the steadiness of the top cached row.
                    pinnedKeys = recentMatches.map { it.favoriteKey } +
                        listOfNotNull(
                            pinnedCachedRow
                                ?.takeIf { keepFirstCached && it.first == query }
                                ?.second,
                        ),
                )
                    .filter { !stopsOnly || it.place.stopId != null }
                    .map { station ->
                        PickerItem(
                            location = station.place,
                            distanceMeters = distanceOf(station.place),
                            isFavorite = favorites.containsLocation(station.place),
                            // By member, not by the row drawn: a recently used pole is shown as
                            // the geocoded station that absorbed it, which carries another key.
                            isRecent = station.members.any { it.favoriteKey in recentKeys },
                        )
                    }
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

    /**
     * The recently used places, for the blank-query list only — [items] carries them the rest of
     * the time, pinned in among the results.
     *
     * Starred places are left out: they are already listed above under Saved, and one place drawn
     * twice on one screen is exactly what the station grouping elsewhere exists to prevent.
     */
    val recentItems: StateFlow<List<PickerItem>> =
        combine(recentPlaces, favoritesRepository.favorites) { recents, favorites ->
            recents
                .filterNot { favorites.containsLocation(it) }
                .map { location ->
                    PickerItem(
                        location = location,
                        distanceMeters = distanceOf(location),
                        isFavorite = false,
                        isRecent = true,
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun distanceOf(location: TransitLocation): Double? =
        referencePosition?.let { GeoPoint(location.lat, location.lon).distanceMetersTo(it) }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun toggleFavorite(location: TransitLocation) {
        viewModelScope.launch { favoritesRepository.toggleLocation(location) }
    }

    /** Hands the pick back to its consumer; the screen pops itself right after. */
    fun select(location: TransitLocation) {
        rememberPick(location)
        when (target) {
            PickerTarget.FROM -> searchStateHolder.setBeginHere(location)
            PickerTarget.TO -> searchStateHolder.setFinishHere(location)
            PickerTarget.VIA -> searchStateHolder.setVia(viaIndex, location)
            PickerTarget.STOP -> searchStateHolder.setDepartureStop(location)
            PickerTarget.MAP -> searchStateHolder.setMapLocation(location)
        }
    }

    /**
     * Files the pick under the recent places.
     *
     * This is the one place a pick is recorded, and it is deliberately every target: the history
     * is shared, so a stop chosen as a destination here is offered back when a start is being
     * picked there. The current-position row is the exception — it is a snapshot of where the
     * phone was just now, and offering it back tomorrow would be a stale coordinate wearing the
     * words "Your location".
     *
     * The write is fire-and-forget inside the repository rather than launched here, because this
     * runs on the tap that pops the screen and takes this ViewModel's scope with it.
     */
    private fun rememberPick(location: TransitLocation) {
        if (location.favoriteKey == currentLocation?.favoriteKey) return
        recentPlacesRepository.record(location)
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

    /** Everything [items] needs that is not part of the ranking; see [Ranking] for why. */
    private data class Presentation(
        val favorites: Favorites,
        val sortByDistance: Boolean,
        val keepFirstCachedResult: Boolean,
        val recents: List<TransitLocation>,
        val pinRecentPlaces: Boolean,
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
