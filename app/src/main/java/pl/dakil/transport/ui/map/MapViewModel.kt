package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.MapFiltersRepository
import pl.dakil.transport.data.prefs.SessionStateRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.data.local.Bbox
import pl.dakil.transport.data.local.TileGrid
import pl.dakil.transport.data.repo.CacheMaintenance
import pl.dakil.transport.data.repo.PlaceCacheRepository
import pl.dakil.transport.data.repo.RoutesRepository
import pl.dakil.transport.data.repo.VehiclesRepository
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.MapCamera
import pl.dakil.transport.domain.model.MapFilters
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.domain.model.TripDetails
import pl.dakil.transport.domain.model.VehicleMotionSettings
import pl.dakil.transport.domain.model.VehicleSegment
import pl.dakil.transport.ui.search.SearchStateHolder

data class Viewport(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val zoom: Double,
) {
    fun toBbox(): Bbox = Bbox(south = south, west = west, north = north, east = east)
}

/** What a cached-stop read was asked for, so an unchanged one can be skipped. */
private data class CacheReadRequest(
    val viewport: Viewport,
    val minZoom: Float,
    val ttlMillis: Long,
    val showExpired: Boolean,
    val generation: Int,
)

/**
 * Stops read from the cache, with the box they were read for.
 *
 * [box] is wider than the viewport that triggered the read, so the map keeps drawing stops into
 * the space a pan uncovers instead of waiting for a query per frame.
 */
private data class LoadedStops(
    val box: Bbox?,
    val stops: List<TransitLocation>,
    val request: CacheReadRequest?,
) {
    fun covers(viewport: Bbox): Boolean {
        val box = box ?: return false
        return viewport.south >= box.south && viewport.north <= box.north &&
            viewport.west >= box.west && viewport.east <= box.east
    }

    /** Whether [other] would be read the same way — same freshness rules, same cache contents. */
    fun matches(other: CacheReadRequest): Boolean {
        val request = request ?: return false
        return request.minZoom == other.minZoom &&
            request.ttlMillis == other.ttlMillis &&
            request.showExpired == other.showExpired &&
            request.generation == other.generation
    }

    companion object {
        val EMPTY = LoadedStops(box = null, stops = emptyList(), request = null)
    }
}

/** One vehicle's marker on the map: its interpolated position at a moment in time. */
data class VehicleMarker(
    val id: String,
    /** Trip id for the details fetch and the trip timetable screen; null when the API omits it. */
    val tripId: String?,
    val label: String,
    /** The vehicle's next stop — see [VehicleSegment.nextStopName]; not the trip's destination. */
    val nextStopName: String?,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
    val realTime: Boolean,
    val position: GeoPoint,
) {
    /**
     * This vehicle's line as a favourite; null when there is no trip id to open it with.
     *
     * [destination] must be the trip's headsign, not [nextStopName]: [FavoriteLine.key] is
     * built from it, so keying on the next stop would give the same line a different key every
     * time the vehicle passes a stop.
     */
    fun favoriteLine(destination: String?): FavoriteLine? = tripId?.let {
        FavoriteLine(label = label, headsign = destination, mode = mode, routeColor = routeColor, tripId = it)
    }
}

/** State of the selected vehicle's trip details (info panel attributes + route overlay). */
sealed interface VehicleDetailsUiState {
    data object Hidden : VehicleDetailsUiState
    data object Loading : VehicleDetailsUiState
    data class Shown(val details: TripDetails) : VehicleDetailsUiState
    data class Error(val error: AppError) : VehicleDetailsUiState
}

/** Progress of an explicit "download this area" request from the filter panel. */
sealed interface AreaDownloadState {
    data object Idle : AreaDownloadState
    data object Running : AreaDownloadState
    data class Done(val areas: Int) : AreaDownloadState
    data object Failed : AreaDownloadState

    /** The viewport covers more ground than the app is willing to fetch in one go. */
    data object TooLarge : AreaDownloadState
}

/** State of the routes overlay + line chips loaded for the currently selected stop. */
sealed interface StopRoutesUiState {
    data object Hidden : StopRoutesUiState
    data object Loading : StopRoutesUiState
    data class Shown(val routes: List<RouteShape>) : StopRoutesUiState

    /** The stop loaded fine, the feed just has no route shapes through it. */
    data object Empty : StopRoutesUiState
    data class Error(val error: AppError) : StopRoutesUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val placeCacheRepository: PlaceCacheRepository,
    private val cacheMaintenance: CacheMaintenance,
    private val geocodeRepository: GeocodeRepository,
    private val routesRepository: RoutesRepository,
    private val vehiclesRepository: VehiclesRepository,
    private val filtersRepository: MapFiltersRepository,
    private val favoritesRepository: FavoritesRepository,
    private val searchStateHolder: SearchStateHolder,
    private val sessionStateRepository: SessionStateRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.DEFAULT)

    private val motionSettings: StateFlow<VehicleMotionSettings> = settings
        .map { it.vehicleMotion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VehicleMotionSettings.DEFAULT)

    /**
     * Zoom below which stops are neither fetched (below) nor drawn: the map layers read this
     * too, so raising it doesn't leave the last fetched stops painted on an empty map, and
     * lowering it actually shows the stops the fetch gate now allows.
     */
    val stopsMinZoom: StateFlow<Float> = settings
        .map { it.stopsMinZoom }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.DEFAULT.stopsMinZoom)

    /** Starred items, for the info panels' star buttons. */
    val favorites: StateFlow<Favorites> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Favorites.EMPTY)

    fun toggleFavoriteStop(stop: TransitLocation) {
        viewModelScope.launch { favoritesRepository.toggleLocation(stop) }
    }

    fun toggleFavoriteLine(line: FavoriteLine) {
        viewModelScope.launch { favoritesRepository.toggleLine(line) }
    }

    /** The camera's box once it has settled. Everything that costs a request keys off this. */
    private val viewport = MutableStateFlow<Viewport?>(null)

    /**
     * The camera's box as it moves, reported without waiting for it to settle. Read only by the
     * cached-stop query, which never touches the network — see [cacheReads].
     */
    private val liveViewport = MutableStateFlow<Viewport?>(null)

    /**
     * Where the camera starts. Null while it is being read: the camera state is built once at
     * first composition and never rebuilt, so the screen waits rather than start at the default
     * and jump.
     */
    private val _initialCamera = MutableStateFlow<MapCamera?>(null)
    val initialCamera: StateFlow<MapCamera?> = _initialCamera

    // Kept locally (seeded from disk once) rather than read through the repository flow, so
    // rapid toggling in the filter menu never races the DataStore write round-trip.
    private val _filters = MutableStateFlow(MapFilters.DEFAULT)
    val filters: StateFlow<MapFilters> = _filters

    // Where the camera is asked to fly next, pending the screen's move: a location picked in the
    // map's search field (whose selection is applied here, via the regular selectStop path), or
    // the last known whereabouts of a trip opened from a timetable.
    private val _cameraTarget = MutableStateFlow<GeoPoint?>(null)
    val cameraTarget: StateFlow<GeoPoint?> = _cameraTarget

    init {
        viewModelScope.launch {
            val remember = settingsRepository.settings.first().rememberMapCamera
            _initialCamera.value = sessionStateRepository.state.first().mapCamera
                .takeIf { remember }
                ?: MapCamera.DEFAULT
        }
        viewModelScope.launch {
            // One write per settled viewport would be one per pan; the debounce keeps a fling
            // across the map down to a single store.
            viewport.filterNotNull()
                .debounce(CAMERA_SAVE_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { viewport ->
                    if (!settingsRepository.settings.first().rememberMapCamera) return@collect
                    sessionStateRepository.saveMapCamera(
                        MapCamera(
                            lat = (viewport.south + viewport.north) / 2,
                            lon = (viewport.west + viewport.east) / 2,
                            zoom = viewport.zoom,
                        ),
                    )
                }
        }
        viewModelScope.launch {
            _filters.value = filtersRepository.filters.first()
        }
    }

    /** Called by the screen once it has animated the camera to [cameraTarget]. */
    fun consumeCameraTarget() {
        _cameraTarget.value = null
    }

    fun updateFilters(transform: (MapFilters) -> MapFilters) {
        val updated = _filters.updateAndGet(transform)
        viewModelScope.launch { filtersRepository.save(updated) }
    }

    fun resetFilters() = updateFilters { MapFilters.DEFAULT }

    // The selected vehicle's trip segments, snapshotted at selection time so the selection
    // survives the viewport-gated fetch dropping the trip (zooming out, panning away) — the
    // same persistence the selected stop gets by being held as plain state below.
    private val selectedVehicleSegments = MutableStateFlow<List<VehicleSegment>?>(null)

    private val _vehicleDetails = MutableStateFlow<VehicleDetailsUiState>(VehicleDetailsUiState.Hidden)
    val vehicleDetails: StateFlow<VehicleDetailsUiState> = _vehicleDetails

    /**
     * The trip opened from a timetable, for as long as it stays selected. Non-null even when the
     * run is not on the road and so has no marker of its own: that is exactly the case where the
     * map's vehicle UI has nothing else to key off, and its route and stops still have to show.
     */
    private val _pinnedTrip = MutableStateFlow<PendingMapTrip?>(null)
    val pinnedTrip: StateFlow<PendingMapTrip?> = _pinnedTrip

    /** Whether this map is the one on screen — see [consumePendingSignals], which maintains it. */
    private val screenVisible = MutableStateFlow(false)

    /** Whether selecting a vehicle narrows the map to that run alone. */
    private val focusSelectedVehicle: StateFlow<Boolean> = settings
        .map { it.focusSelectedVehicle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.DEFAULT.focusSelectedVehicle)

    /**
     * Stops of the run being followed, empty whenever the map is not following one. The trip's
     * stop list only arrives with its details, so this stays empty (and the viewport's stops
     * stay drawn) while they load or if they fail — blanking the map in between would read as
     * a glitch rather than as focus.
     */
    private val focusedTripStops: StateFlow<List<TransitLocation>> =
        combine(
            selectedVehicleSegments,
            vehicleDetails,
            focusSelectedVehicle,
            pinnedTrip,
        ) { selected, details, focus, pinned ->
            // A trip opened from a timetable with no vehicle to follow ignores the setting: its
            // stops are the only thing there is to look at, so hiding them among the viewport's
            // would defeat the whole point of opening it.
            val followingVehicle = selected != null
            if (if (followingVehicle) !focus else pinned == null) {
                emptyList()
            } else {
                // Interlined runs call at the joint stop twice; the map only wants one marker.
                (details as? VehicleDetailsUiState.Shown)?.details?.timetable
                    .orEmpty()
                    .map { it.place }
                    .distinctBy { it.favoriteKey }
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The stops the map is holding, and the padded box they were read for.
     *
     * Read from disk and kept a good margin wider than the screen, so a pan inside that margin
     * needs no query at all and the stops simply extend as the map moves. Written by
     * [cacheReads] below and, indirectly, by every successful refresh.
     */
    private val loadedStops = MutableStateFlow(LoadedStops.EMPTY)

    /** Bumped after a refresh writes to the cache, to make the disk read run again. */
    private val cacheGeneration = MutableStateFlow(0)

    /**
     * Reads cached stops for wherever the camera is *right now* — no settling, no network.
     *
     * This is what makes the map feel native rather than web-shaped: the stops for an area the
     * app has seen before are already on disk, so they can be drawn while the finger is still
     * moving. [liveViewport] deliberately updates during the gesture; the settled [viewport]
     * next door is what decides whether anything is fetched.
     */
    private val cacheReads: Flow<Unit> =
        combine(
            liveViewport.filterNotNull(),
            settings.map { it.stopsMinZoom }.distinctUntilChanged(),
            settings.map { it.offlineCache }.distinctUntilChanged(),
            cacheGeneration,
        ) { vp, minZoom, cache, generation ->
            CacheReadRequest(vp, minZoom, cache.stopCacheTtlMillis, cache.showExpiredCache, generation)
        }
            .mapLatest { request ->
                val box = request.viewport.toBbox()
                // Below the zoom where stops are drawn there is nothing to prepare, and a box
                // that wide would read a whole country's worth of rows to throw them away.
                if (request.viewport.zoom < request.minZoom) {
                    loadedStops.value = LoadedStops.EMPTY
                    return@mapLatest
                }
                val current = loadedStops.value
                // Still inside the margin the last read covered, and nothing about how the cache
                // should be interpreted has changed: the stops on screen are already right.
                if (current.covers(box) && current.matches(request)) return@mapLatest

                val padded = box.inflated(CACHE_READ_PADDING)
                val area = placeCacheRepository.stopsIn(padded, request.ttlMillis, System.currentTimeMillis())
                loadedStops.value = LoadedStops(
                    box = padded,
                    stops = if (request.showExpired) area.stops else area.stopsExcludingExpired(),
                    request = request,
                )
            }

    /** Whether the last refresh of the visible area failed, i.e. the map is running on cache. */
    private val _stopsOffline = MutableStateFlow(false)
    val stopsOffline: StateFlow<Boolean> = _stopsOffline

    private val _areaDownload = MutableStateFlow<AreaDownloadState>(AreaDownloadState.Idle)
    val areaDownload: StateFlow<AreaDownloadState> = _areaDownload

    private var areaDownloadJob: Job? = null

    /**
     * Fetches every tile of the current viewport, ignoring how fresh the cache thinks it is.
     *
     * For the one thing the automatic refresh cannot know: that the user is about to lose signal
     * and wants this area on the phone now. The tile cap still applies — a country-sized viewport
     * is not something to ask a shared API for, and the button says so rather than trying.
     */
    fun downloadVisibleArea() {
        val vp = viewport.value ?: return
        if (areaDownloadJob?.isActive == true) return
        val tiles = TileGrid.tilesFor(vp.toBbox())
        if (tiles.size > PlaceCacheRepository.MAX_VIEWPORT_TILES) {
            _areaDownload.value = AreaDownloadState.TooLarge
            return
        }
        areaDownloadJob = viewModelScope.launch {
            _areaDownload.value = AreaDownloadState.Running
            val now = System.currentTimeMillis()
            // Every tile, not just the stale ones: "download this area" means the whole thing.
            val failures = placeCacheRepository.refreshTiles(tiles, now)
            cacheGeneration.update { it + 1 }
            _areaDownload.value = if (failures > 0) {
                AreaDownloadState.Failed
            } else {
                AreaDownloadState.Done(tiles.size)
            }
        }
    }

    /** Called by the screen once it has shown the outcome. */
    fun consumeAreaDownload() {
        _areaDownload.value = AreaDownloadState.Idle
    }

    /**
     * Refreshes the areas of a settled viewport that the cache has never seen, or has held past
     * its TTL. Only the missing tiles are asked for, merged into as few rectangles as possible,
     * so panning back over familiar ground costs no request at all.
     */
    private val stopFetches: Flow<Unit> =
        combine(
            viewport.filterNotNull(),
            settings.map { it.stopsMinZoom }.distinctUntilChanged(),
            settings.map { it.offlineCache.stopCacheTtlMillis }.distinctUntilChanged(),
        ) { vp, minZoom, ttl -> Triple(vp, minZoom, ttl) }
            .distinctUntilChanged()
            .mapLatest { (vp, minZoom, ttl) ->
                if (vp.zoom < minZoom) return@mapLatest
                val now = System.currentTimeMillis()
                val area = placeCacheRepository.stopsIn(vp.toBbox(), ttl, now)
                val stale = area.staleTiles
                if (stale.isEmpty()) {
                    _stopsOffline.value = false
                    return@mapLatest
                }
                // A viewport this wide is not something to ask a shared API for in one go. Its
                // cached stops stay on screen; refreshing waits for the user to zoom in.
                if (TileGrid.tilesFor(vp.toBbox()).size > PlaceCacheRepository.MAX_VIEWPORT_TILES) {
                    return@mapLatest
                }
                val failures = placeCacheRepository.refreshTiles(stale, now)
                _stopsOffline.value = failures > 0
                // Even a partly failed refresh usually wrote something; re-read either way.
                cacheGeneration.update { it + 1 }
                // A refresh is the only thing that grows the cache, so it is the only place the
                // size limit has to be enforced.
                cacheMaintenance.trim()
            }

    /**
     * The stops of the area on screen, always straight from [loadedStops].
     *
     * Both pipelines are combined in rather than launched eagerly so they run exactly while the
     * map is collecting — the same `WhileSubscribed` discipline the vehicle poller uses. Neither
     * contributes a value; they only ever write into [loadedStops].
     */
    private val allStops: StateFlow<List<TransitLocation>> =
        combine(
            loadedStops,
            cacheReads.onStart { emit(Unit) },
            stopFetches.onStart { emit(Unit) },
        ) { loaded, _, _ -> loaded.stops }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the map draws: the viewport's stops normally, but only the followed trip's own stops
     * while a vehicle is selected (see [focusedTripStops]). The trip's stops deliberately skip
     * [MapFilters.matchesStop] — they belong to the run the user is following, so a filter that
     * hides their mode would empty the very route being watched.
     */
    val stops: StateFlow<List<TransitLocation>> =
        combine(allStops, filters, focusedTripStops) { stops, filters, tripStops ->
            tripStops.ifEmpty { stops.filter(filters::matchesStop) }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while [stops] is showing a followed trip's stops rather than the viewport's. */
    val stopsFocusedOnTrip: StateFlow<Boolean> = focusedTripStops
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Every trip currently being drawn, keyed by trip, with when its segments last arrived from
     * the API. Held across viewport changes and fetches rather than being replaced wholesale:
     * a trip missing from one poll (near the viewport edge, or the time window's boundary)
     * keeps moving on what is already known instead of vanishing and popping back elsewhere.
     */
    private val trackedTrips = MutableStateFlow<Map<String, TrackedTrip>>(emptyMap())

    // Fetching is gated only on "any vehicle category on" (not the specific categories/data
    // source), so tweaking filters refines the already-fetched segments instantly instead of
    // hitting the shared Transitous API again.
    private val vehicleFetches: Flow<Unit> =
        combine(
            viewport,
            filters.map { it.vehicleCategories.isNotEmpty() }.distinctUntilChanged(),
            motionSettings,
        ) { vp, enabled, motion ->
            vp?.takeIf { enabled && it.zoom >= motion.minZoom } to motion
        }
            .distinctUntilChanged()
            .flatMapLatest { (vp, motion) ->
                if (vp == null) {
                    trackedTrips.value = emptyMap()
                    flowOf(Unit)
                } else {
                    flow {
                        while (true) {
                            val fetched = vehiclesRepository.vehiclesInViewport(
                                south = vp.south,
                                west = vp.west,
                                north = vp.north,
                                east = vp.east,
                                zoom = vp.zoom,
                                windowSeconds = motion.fetchWindowSeconds.toLong(),
                            ).getOrDefault(emptyList())
                            mergeFetchedTrips(fetched, motion)
                            emit(Unit)
                            delay(motion.refreshIntervalSeconds * 1_000L)
                        }
                    }
                }
            }

    /**
     * Folds one fetch into [trackedTrips]: trips it returned are refreshed, trips it didn't are
     * kept until they go stale. A zero retention reverts to replacing the whole set.
     */
    private fun mergeFetchedTrips(fetched: List<VehicleSegment>, motion: VehicleMotionSettings) {
        val now = System.currentTimeMillis()
        val incoming = fetched.groupBy { it.tripKey }
        trackedTrips.update { current ->
            val retentionMillis = motion.segmentRetentionSeconds * 1_000L
            val kept = current.filterValues { now - it.lastSeenMillis <= retentionMillis }
            kept + incoming.mapValues { (_, segments) -> TrackedTrip(segments, now) }
        }
    }

    private val vehicleSegments: StateFlow<List<VehicleSegment>> =
        // The fetch loop only signals; the segments themselves are read from the tracked map,
        // so an emission is produced both when a fetch lands and when retention prunes it.
        combine(trackedTrips, vehicleFetches.onStart { emit(Unit) }) { tracked, _ ->
            tracked.values.flatMap { it.segments }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Positions are recomputed every frame from the timetable, then handed to
     * [VehicleMotionTracker], which is what keeps a delay revision from teleporting a marker.
     */
    private val motionTracker = VehicleMotionTracker()

    /**
     * The drawn markers and the selected vehicle's marker come out of **one** frame, computed in
     * a single loop through the same [motionTracker]. The selection used to run its own loop off
     * its own timer and skip the tracker entirely, which put the halo, info panel and camera
     * focus at a different position than the marker they belong to: the two loops sampled
     * different instants, and only the drawn marker got the tracker's monotonic clamping, so
     * every delay revision (i.e. every fetch) pulled them apart. Keep them in one frame.
     */
    private val vehicleFrames: StateFlow<VehicleFrame> =
        combine(
            vehicleSegments,
            filters,
            motionSettings,
            selectedVehicleSegments,
            focusSelectedVehicle,
        ) { segments, filters, motion, selected, focus ->
            val tripKey = selected?.firstOrNull()?.tripKey
            // The selected trip tracks live positions independently of the viewport fetches:
            // freshly fetched segments when they still cover it, the selection snapshot
            // otherwise. Fetches only span a ~minute window, so once they stop renewing the
            // trip (panned/zoomed away) the marker freezes at its last known position rather
            // than disappearing — only deselection closes the panel, like the selected stop.
            val selectedSegments = tripKey?.let { key ->
                segments.filter { it.tripKey == key }.ifEmpty { selected }
            }
            val visible = if (focus && tripKey != null) {
                // Only the followed run, so the marker being followed never blinks out.
                selectedSegments.orEmpty()
            } else {
                segments.filter(filters::matchesVehicle)
            }
            FrameInput(visible, selectedSegments, tripKey, motion)
        }
            .flatMapLatest { input ->
                // The selected trip may be filtered out of the drawn set; it still has to be in
                // the tracker's frame so its halo reads the same corrected position.
                val selectedIsVisible = input.visible.any { it.tripKey == input.tripKey }
                val all = if (input.selectedSegments != null && !selectedIsVisible) {
                    input.visible + input.selectedSegments
                } else {
                    input.visible
                }
                if (all.isEmpty()) {
                    motionTracker.reset()
                    flowOf(VehicleFrame())
                } else {
                    flow {
                        while (true) {
                            val now = OffsetDateTime.now()
                            val markers = motionTracker.frame(frameTargets(all, now), input.motion)
                            emit(
                                VehicleFrame(
                                    visible = if (selectedIsVisible) {
                                        markers
                                    } else {
                                        markers.filterNot { it.id == input.tripKey }
                                    },
                                    selected = input.tripKey?.let { key ->
                                        markers.firstOrNull { it.id == key }
                                        // Past the segments' time coverage: hold the last position.
                                            ?: input.selectedSegments?.maxByOrNull { it.arrival }?.markerAt(now)
                                    },
                                ),
                            )
                            delay(input.motion.frameIntervalMillis.toLong())
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleFrame())

    val vehicles: StateFlow<List<VehicleMarker>> = vehicleFrames
        .map { it.visible }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStop = MutableStateFlow<TransitLocation?>(null)
    val selectedStop: StateFlow<TransitLocation?> = _selectedStop

    /** Marker of the selected vehicle, at exactly the position it is drawn at. */
    val selectedVehicle: StateFlow<VehicleMarker?> = vehicleFrames
        .map { it.selected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var vehicleDetailsJob: Job? = null

    /** Fetch loop of a trip followed from a timetable — see [selectPinnedTrip]. */
    private var pinnedTripJob: Job? = null

    private val _stopRoutes = MutableStateFlow<StopRoutesUiState>(StopRoutesUiState.Hidden)
    val stopRoutes: StateFlow<StopRoutesUiState> = _stopRoutes

    /**
     * [RouteShape.focusKey] of the one line the map is drawing alone, or null for the whole
     * network through the stop. Purely a way of reading a busy interchange — it is dropped
     * whenever the routes themselves change, and never persisted.
     */
    private val _focusedRoute = MutableStateFlow<String?>(null)
    val focusedRoute: StateFlow<String?> = _focusedRoute

    private var routesJob: Job? = null

    private var pointNameJob: Job? = null

    /** Called once the map camera has settled (already debounced by the caller). */
    fun onViewportSettled(south: Double, west: Double, north: Double, east: Double, zoom: Double) {
        val settled = Viewport(south, west, north, east, zoom)
        viewport.value = settled
        // A settled camera is also the newest live position; without this, a camera that moves
        // programmatically (following a vehicle, flying to a pick) would leave the cache read
        // looking at wherever the last gesture ended.
        liveViewport.value = settled
    }

    /**
     * Called while the camera is still moving. Feeds the cached-stop read only — never a fetch —
     * so stops keep appearing during a pan instead of after it.
     */
    fun onViewportChanged(south: Double, west: Double, north: Double, east: Double, zoom: Double) {
        liveViewport.value = Viewport(south, west, north, east, zoom)
    }

    fun selectStop(stop: TransitLocation) {
        // Tapping a stop of the run being followed is part of following it, so the vehicle
        // stays selected and the map keeps its focus; the stop's own lines are left unloaded
        // because drawing every line through it would bury the route being watched.
        val belongsToFocusedTrip = focusedTripStops.value.any { it.favoriteKey == stop.favoriteKey }
        if (!belongsToFocusedTrip) clearVehicleSelection()
        pointNameJob?.cancel()
        if (_selectedStop.value == stop) return
        _selectedStop.value = stop
        // Lines through a bare point are only "whatever passes nearby" — misleading enough
        // that the panel doesn't offer them (nor a timetable) for anything but a real stop.
        if (stop.stopId != null && !belongsToFocusedTrip) loadRoutes(stop) else hideRoutes()
    }

    /**
     * Selects a bare coordinate the user long-pressed on the map. Shown immediately (labelled
     * with its coordinates) so the panel never lags the gesture; the reverse-geocoded name of
     * whatever sits there replaces the label once it arrives, unless the selection changed
     * in the meantime.
     */
    fun selectPoint(lat: Double, lon: Double) {
        val point = TransitLocation(name = formatCoordinates(lat, lon), lat = lat, lon = lon)
        selectStop(point)
        pointNameJob?.cancel()
        pointNameJob = viewModelScope.launch {
            val name = geocodeRepository.nearestPlaceName(lat, lon).getOrNull() ?: return@launch
            _selectedStop.update { current -> if (current == point) current.copy(name = name) else current }
        }
    }

    /**
     * Closes the stop panel only. A vehicle being followed stays selected, so dismissing the
     * stop a user tapped along its route hands them back the vehicle panel rather than
     * dropping the whole trip.
     */
    fun clearStopSelection() {
        pointNameJob?.cancel()
        _selectedStop.value = null
        hideRoutes()
    }

    fun clearSelection() {
        clearStopSelection()
        clearVehicleSelection()
    }

    /** Selects a vehicle and starts loading its details + route overlay (shown while selected). */
    fun selectVehicle(vehicle: VehicleMarker) {
        _selectedStop.value = null
        hideRoutes()
        if (selectedVehicleSegments.value?.firstOrNull()?.tripKey == vehicle.id) return
        // A vehicle tapped on the map is tracked by the viewport fetches like any other; whatever
        // trip was being followed from a timetable is not this one.
        pinnedTripJob?.cancel()
        _pinnedTrip.value = null
        selectedVehicleSegments.value = vehicleSegments.value.filter { it.tripKey == vehicle.id }
        val tripId = vehicle.tripId
        if (tripId == null) {
            // No trip id — the panel can still show marker-level info, just no details/route.
            vehicleDetailsJob?.cancel()
            _vehicleDetails.value = VehicleDetailsUiState.Hidden
            return
        }
        loadVehicleDetails(tripId, vehicle.label)
    }

    /**
     * Follows a trip opened from a timetable screen, which arrives as an id and a rough
     * whereabouts rather than as a marker already on the map.
     *
     * Its segments can't come from the viewport — the map may be looking somewhere else entirely,
     * and the user's filters or zoom may rule vehicle fetches out — so the trip gets its own fetch
     * loop against a small box around it, renewed on the same cadence as the map's own. That keeps
     * the marker moving for as long as the selection lives, which is what
     * [clearVehicleSelection] then has to stop.
     */
    fun selectPinnedTrip(trip: PendingMapTrip) {
        _selectedStop.value = null
        hideRoutes()
        pinnedTripJob?.cancel()
        selectedVehicleSegments.value = null
        _pinnedTrip.value = trip
        loadVehicleDetails(trip.tripId, trip.label)
        if (!trip.isRunning) {
            // Nothing is moving to look for. The route and its stops come from the details being
            // loaded, and the screen frames the whole line once they land.
            return
        }
        // The fetch takes a moment; the camera starts moving now so switching to the map is not a
        // second of nothing happening. The follow effect takes over once the marker exists.
        _cameraTarget.value = trip.between.first
        pinnedTripJob = viewModelScope.launch {
            var between = trip.between
            while (true) {
                // Held while another screen is on top of this map: "show on map" stacks maps, and
                // every one left behind would otherwise keep asking a shared API about a run
                // nobody is watching. Coming back re-fetches straight away.
                screenVisible.first { it }
                val motion = motionSettings.value
                val segments = vehiclesRepository
                    .segmentsForTrip(trip.tripId, between, motion.fetchWindowSeconds.toLong())
                    .getOrDefault(emptyList())
                if (segments.isNotEmpty()) {
                    selectedVehicleSegments.value = segments
                    // Follow the run: the next box is drawn around where it is by then, not
                    // around where it was when the user left the timetable.
                    segments.currentBox()?.let { between = it }
                }
                delay(motion.refreshIntervalSeconds * 1_000L)
            }
        }
    }

    private fun loadVehicleDetails(tripId: String, label: String) {
        vehicleDetailsJob?.cancel()
        _vehicleDetails.value = VehicleDetailsUiState.Loading
        vehicleDetailsJob = viewModelScope.launch {
            routesRepository.tripDetails(tripId, label).fold(
                onSuccess = { _vehicleDetails.value = VehicleDetailsUiState.Shown(it) },
                onFailure = { error ->
                    if (error !is CancellationException) {
                        _vehicleDetails.value = VehicleDetailsUiState.Error(error.toAppError())
                    }
                },
            )
        }
    }

    fun clearVehicleSelection() {
        vehicleDetailsJob?.cancel()
        pinnedTripJob?.cancel()
        _pinnedTrip.value = null
        selectedVehicleSegments.value = null
        _vehicleDetails.value = VehicleDetailsUiState.Hidden
    }

    /** Tapping the focused line again shows the whole network back; tapping another moves the focus. */
    fun toggleRouteFocus(route: RouteShape) {
        _focusedRoute.update { if (it == route.focusKey) null else route.focusKey }
    }

    fun clearRouteFocus() {
        _focusedRoute.value = null
    }

    private fun loadRoutes(stop: TransitLocation) {
        routesJob?.cancel()
        // Reset here and in hideRoutes(), the only two paths that change what is drawn — that
        // covers every caller (stop/vehicle selection, deselection) without each remembering to.
        _focusedRoute.value = null
        _stopRoutes.value = StopRoutesUiState.Loading
        routesJob = viewModelScope.launch {
            routesRepository.routesThroughStop(stop).fold(
                onSuccess = { routes ->
                    _stopRoutes.value = if (routes.isEmpty()) {
                        StopRoutesUiState.Empty
                    } else {
                        StopRoutesUiState.Shown(routes)
                    }
                },
                onFailure = { error ->
                    if (error !is CancellationException) {
                        _stopRoutes.value = StopRoutesUiState.Error(error.toAppError())
                    }
                },
            )
        }
    }

    private fun hideRoutes() {
        routesJob?.cancel()
        _focusedRoute.value = null
        _stopRoutes.value = StopRoutesUiState.Hidden
    }

    /**
     * The route being assembled on the map. These are the search form's own start and
     * destination — filling one here is the same act as typing it there — so the draft bar is
     * gated on [routeDraftVisible] rather than on them being set: the picks are persisted
     * across restarts, and a bar that reappeared on launch would be showing last week's search.
     */
    val routeFrom: StateFlow<TransitLocation?> = searchStateHolder.from

    val routeTo: StateFlow<TransitLocation?> = searchStateHolder.to

    private val _routeDraftVisible = MutableStateFlow(false)
    val routeDraftVisible: StateFlow<Boolean> = _routeDraftVisible

    /** Whether a half-built route keeps the user on the map instead of opening the search form. */
    val stayOnMapWhenPickingRoute: StateFlow<Boolean> = settings
        .map { it.stayOnMapWhenPickingRoute }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppSettings.DEFAULT.stayOnMapWhenPickingRoute,
        )

    fun beginHere(location: TransitLocation) {
        searchStateHolder.setBeginHere(location)
        _routeDraftVisible.value = true
    }

    fun finishHere(location: TransitLocation) {
        searchStateHolder.setFinishHere(location)
        _routeDraftVisible.value = true
    }

    fun clearRouteFrom() {
        searchStateHolder.clearBeginHere()
        hideRouteDraftIfEmpty()
    }

    fun clearRouteTo() {
        searchStateHolder.clearFinishHere()
        hideRouteDraftIfEmpty()
    }

    /** Called when the draft leaves the map — the route is either searched or abandoned. */
    fun hideRouteDraft() {
        _routeDraftVisible.value = false
    }

    private fun hideRouteDraftIfEmpty() {
        if (searchStateHolder.from.value == null && searchStateHolder.to.value == null) {
            _routeDraftVisible.value = false
        }
    }

    /**
     * Takes the one-shot signals [SearchStateHolder] raises for the map — a trip to follow, and a
     * location picked for it by the location picker or handed over by another app — for as long as
     * the screen that called this is the one on screen, which it reports by keeping the call
     * running (see [MapScreen]).
     *
     * Deliberately gated on that rather than collected from `init`: "show on map" pushes a
     * *second* map on top of the stack, so the maps underneath are still alive with their view
     * models, and an init-time collector on the one being left behind would take the signal and
     * clear it — before the map that is about to be shown even exists, which left that map blank.
     * Only one map entry is resumed at a time, so this hands each signal to the map the user is
     * actually looking at. It doubles as the map's "am I visible" flag, which is what pauses the
     * followed run's fetch loop in [selectPinnedTrip].
     *
     * Runs until cancelled.
     */
    suspend fun consumePendingSignals(): Unit = coroutineScope {
        screenVisible.value = true
        try {
            launch {
                searchStateHolder.pendingMapTrip.collect { trip ->
                    if (trip != null) {
                        searchStateHolder.pendingMapTrip.value = null
                        selectPinnedTrip(trip)
                    }
                }
            }
            searchStateHolder.pendingMapLocation.collect { location ->
                if (location != null) {
                    searchStateHolder.pendingMapLocation.value = null
                    selectStop(location)
                    _cameraTarget.value = GeoPoint(lat = location.lat, lon = location.lon)
                }
            }
        } finally {
            screenVisible.value = false
        }
    }

    private companion object {
        const val CAMERA_SAVE_DEBOUNCE_MILLIS = 1_000L

        /**
         * How far past the viewport cached stops are read, as a fraction of its size. Wide
         * enough that an ordinary pan is served from what is already in memory, small enough
         * that the query stays cheap.
         */
        const val CACHE_READ_PADDING = 0.5
    }
}

/** Human-readable coordinates, used to label and describe points picked on the map. */
fun formatCoordinates(lat: Double, lon: Double): String =
    String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon)

/** One trip's known segments, and when the API last confirmed them. */
private data class TrackedTrip(val segments: List<VehicleSegment>, val lastSeenMillis: Long)

/**
 * One raw frame target per vehicle at [time]: the segment whose time window contains [time]
 * wins; a vehicle between segments (dwelling at a stop) sits at its next segment's start.
 * The positions are unsmoothed — [VehicleMotionTracker] turns them into what is drawn.
 */
/** One frame of vehicle motion: what gets drawn, plus the selected vehicle from the same instant. */
private data class VehicleFrame(
    val visible: List<VehicleMarker> = emptyList(),
    val selected: VehicleMarker? = null,
)

/** What a frame loop is started with — everything the loop needs, resolved once per input change. */
private data class FrameInput(
    val visible: List<VehicleSegment>,
    val selectedSegments: List<VehicleSegment>?,
    val tripKey: String?,
    val motion: VehicleMotionSettings,
)

private fun frameTargets(segments: List<VehicleSegment>, time: OffsetDateTime): List<VehicleFrameTarget> =
    segments.groupBy { it.tripKey }.mapNotNull { (_, tripSegments) ->
        val current = tripSegments.firstOrNull { time >= it.departure && time <= it.arrival }
            ?: tripSegments.filter { it.departure > time }.minByOrNull { it.departure }
            ?: return@mapNotNull null
        val marker = current.markerAt(time) ?: return@mapNotNull null
        VehicleFrameTarget(
            marker = marker,
            segmentDepartureMillis = current.departure.toInstant().toEpochMilli(),
            fraction = current.fractionAt(time),
        )
    }

/**
 * The stretch a followed trip is on right now, as the two ends of the segment covering this
 * moment — the box its next fetch is made against. Falls back to the whole known path when the
 * run is between segments (dwelling at a stop) or past the segments' coverage, and null when the
 * fetch carried no geometry at all — the caller then keeps the box it already had.
 */
private fun List<VehicleSegment>.currentBox(): Pair<GeoPoint, GeoPoint>? {
    val now = OffsetDateTime.now()
    val current = firstOrNull { now >= it.departure && now <= it.arrival }
        ?: filter { it.departure > now }.minByOrNull { it.departure }
    val path = current?.path?.takeIf { it.isNotEmpty() } ?: flatMap { it.path }
    return if (path.isEmpty()) null else path.first() to path.last()
}

/** This segment's vehicle as a marker, positioned at [time] (clamped to the segment's path). */
private fun VehicleSegment.markerAt(time: OffsetDateTime): VehicleMarker? {
    val position = positionAt(time) ?: return null
    return VehicleMarker(
        id = tripKey,
        tripId = tripId,
        label = label,
        nextStopName = nextStopName,
        mode = mode,
        routeColor = routeColor,
        realTime = realTime,
        position = position,
    )
}
