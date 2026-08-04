package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.data.repo.MapStyleRepository
import pl.dakil.transport.data.repo.RoutesRepository
import pl.dakil.transport.data.repo.StopsRepository
import pl.dakil.transport.data.repo.VehiclesRepository
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.MapFilters
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
)

/** One vehicle's marker on the map: its interpolated position at a moment in time. */
data class VehicleMarker(
    val id: String,
    /** Trip id for the details fetch and the trip timetable screen; null when the API omits it. */
    val tripId: String?,
    val label: String,
    val headsign: String?,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
    val realTime: Boolean,
    val position: GeoPoint,
) {
    /** This vehicle's line as a favourite; null when there is no trip id to open it with. */
    val favoriteLine: FavoriteLine?
        get() = tripId?.let {
            FavoriteLine(label = label, headsign = headsign, mode = mode, routeColor = routeColor, tripId = it)
        }
}

/** State of the selected vehicle's trip details (info panel attributes + route overlay). */
sealed interface VehicleDetailsUiState {
    data object Hidden : VehicleDetailsUiState
    data object Loading : VehicleDetailsUiState
    data class Shown(val details: TripDetails) : VehicleDetailsUiState
    data class Error(val error: AppError) : VehicleDetailsUiState
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val stopsRepository: StopsRepository,
    private val geocodeRepository: GeocodeRepository,
    private val routesRepository: RoutesRepository,
    private val vehiclesRepository: VehiclesRepository,
    private val mapStyleRepository: MapStyleRepository,
    private val filtersRepository: MapFiltersRepository,
    private val favoritesRepository: FavoritesRepository,
    private val searchStateHolder: SearchStateHolder,
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

    private val viewport = MutableStateFlow<Viewport?>(null)

    /**
     * Patched bundled style JSON (base transit stop icons removed, sources repointed);
     * null only for the brief moment the asset is being read.
     */
    private val _styleJson = MutableStateFlow<String?>(null)
    val styleJson: StateFlow<String?> = _styleJson

    // Kept locally (seeded from disk once) rather than read through the repository flow, so
    // rapid toggling in the filter menu never races the DataStore write round-trip.
    private val _filters = MutableStateFlow(MapFilters.DEFAULT)
    val filters: StateFlow<MapFilters> = _filters

    // A location picked in the map's search field, pending the screen's camera move
    // (the selection itself is applied here, via the regular selectStop path).
    private val _searchCameraTarget = MutableStateFlow<TransitLocation?>(null)
    val searchCameraTarget: StateFlow<TransitLocation?> = _searchCameraTarget

    init {
        viewModelScope.launch {
            _styleJson.value = mapStyleRepository.transitFreeGmapsStyle()
        }
        viewModelScope.launch {
            _filters.value = filtersRepository.filters.first()
        }
        viewModelScope.launch {
            searchStateHolder.pendingMapLocation.collect { location ->
                if (location != null) {
                    searchStateHolder.pendingMapLocation.value = null
                    selectStop(location)
                    _searchCameraTarget.value = location
                }
            }
        }
    }

    /** Called by the screen once it has animated the camera to [searchCameraTarget]. */
    fun consumeSearchCameraTarget() {
        _searchCameraTarget.value = null
    }

    fun updateFilters(transform: (MapFilters) -> MapFilters) {
        val updated = _filters.updateAndGet(transform)
        viewModelScope.launch { filtersRepository.save(updated) }
    }

    fun resetFilters() = updateFilters { MapFilters.DEFAULT }

    private val allStops: StateFlow<List<TransitLocation>> =
        combine(viewport.filterNotNull(), settings.map { it.stopsMinZoom }.distinctUntilChanged()) { vp, minZoom ->
            vp to minZoom
        }
            .distinctUntilChanged()
            .mapLatest { (vp, minZoom) ->
                if (vp.zoom < minZoom) {
                    emptyList()
                } else {
                    stopsRepository.stopsInViewport(vp.south, vp.west, vp.north, vp.east).getOrDefault(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stops: StateFlow<List<TransitLocation>> =
        combine(allStops, filters) { stops, filters -> stops.filter(filters::matchesStop) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val vehicles: StateFlow<List<VehicleMarker>> =
        combine(vehicleSegments, filters, motionSettings) { segments, filters, motion ->
            segments.filter(filters::matchesVehicle) to motion
        }
            .flatMapLatest { (segments, motion) ->
                if (segments.isEmpty()) {
                    motionTracker.reset()
                    flowOf(emptyList())
                } else {
                    flow {
                        while (true) {
                            emit(motionTracker.frame(frameTargets(segments, OffsetDateTime.now()), motion))
                            delay(motion.frameIntervalMillis.toLong())
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStop = MutableStateFlow<TransitLocation?>(null)
    val selectedStop: StateFlow<TransitLocation?> = _selectedStop

    // The selected vehicle's trip segments, snapshotted at selection time so the selection
    // survives the viewport-gated fetch dropping the trip (zooming out, panning away) — the
    // same persistence the selected stop gets by being held as plain state above.
    private val selectedVehicleSegments = MutableStateFlow<List<VehicleSegment>?>(null)

    /**
     * Marker of the selected vehicle, tracking its live position independently of the
     * viewport fetches (refreshed from them whenever they still cover the trip). Fetches only
     * cover a ~minute time window, so once they stop renewing the trip (panned/zoomed away)
     * the marker freezes at the snapshot's last known position rather than disappearing —
     * only deselection closes the panel, matching the selected stop's behavior.
     */
    val selectedVehicle: StateFlow<VehicleMarker?> =
        combine(selectedVehicleSegments, vehicleSegments, motionSettings) { selected, fetched, motion ->
            val tripKey = selected?.firstOrNull()?.tripKey
            val segments = if (tripKey == null) null else {
                fetched.filter { it.tripKey == tripKey }.ifEmpty { selected }
            }
            segments to motion
        }
            .flatMapLatest { (segments, motion) ->
                if (segments == null) {
                    flowOf<VehicleMarker?>(null)
                } else {
                    flow {
                        while (true) {
                            val now = OffsetDateTime.now()
                            emit(
                                frameTargets(segments, now).firstOrNull()?.marker
                                    // Past the segments' time coverage: hold the last position.
                                    ?: segments.maxByOrNull { it.arrival }?.markerAt(now),
                            )
                            delay(motion.frameIntervalMillis.toLong())
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _vehicleDetails = MutableStateFlow<VehicleDetailsUiState>(VehicleDetailsUiState.Hidden)
    val vehicleDetails: StateFlow<VehicleDetailsUiState> = _vehicleDetails

    private var vehicleDetailsJob: Job? = null

    private val _stopRoutes = MutableStateFlow<StopRoutesUiState>(StopRoutesUiState.Hidden)
    val stopRoutes: StateFlow<StopRoutesUiState> = _stopRoutes

    private var routesJob: Job? = null

    private var pointNameJob: Job? = null

    /** Called once the map camera has settled (already debounced by the caller). */
    fun onViewportSettled(south: Double, west: Double, north: Double, east: Double, zoom: Double) {
        viewport.value = Viewport(south, west, north, east, zoom)
    }

    fun selectStop(stop: TransitLocation) {
        clearVehicleSelection()
        pointNameJob?.cancel()
        if (_selectedStop.value == stop) return
        _selectedStop.value = stop
        // Lines through a bare point are only "whatever passes nearby" — misleading enough
        // that the panel doesn't offer them (nor a timetable) for anything but a real stop.
        if (stop.stopId != null) loadRoutes(stop) else hideRoutes()
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

    fun clearSelection() {
        pointNameJob?.cancel()
        _selectedStop.value = null
        hideRoutes()
        clearVehicleSelection()
    }

    /** Selects a vehicle and starts loading its details + route overlay (shown while selected). */
    fun selectVehicle(vehicle: VehicleMarker) {
        _selectedStop.value = null
        hideRoutes()
        if (selectedVehicleSegments.value?.firstOrNull()?.tripKey == vehicle.id) return
        selectedVehicleSegments.value = vehicleSegments.value.filter { it.tripKey == vehicle.id }
        vehicleDetailsJob?.cancel()
        val tripId = vehicle.tripId
        if (tripId == null) {
            // No trip id — the panel can still show marker-level info, just no details/route.
            _vehicleDetails.value = VehicleDetailsUiState.Hidden
            return
        }
        _vehicleDetails.value = VehicleDetailsUiState.Loading
        vehicleDetailsJob = viewModelScope.launch {
            routesRepository.tripDetails(tripId, vehicle.label).fold(
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
        selectedVehicleSegments.value = null
        _vehicleDetails.value = VehicleDetailsUiState.Hidden
    }

    private fun loadRoutes(stop: TransitLocation) {
        routesJob?.cancel()
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
        _stopRoutes.value = StopRoutesUiState.Hidden
    }

    fun beginHere(location: TransitLocation) = searchStateHolder.setBeginHere(location)

    fun finishHere(location: TransitLocation) = searchStateHolder.setFinishHere(location)
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

/** This segment's vehicle as a marker, positioned at [time] (clamped to the segment's path). */
private fun VehicleSegment.markerAt(time: OffsetDateTime): VehicleMarker? {
    val position = positionAt(time) ?: return null
    return VehicleMarker(
        id = tripKey,
        tripId = tripId,
        label = label,
        headsign = headsign,
        mode = mode,
        routeColor = routeColor,
        realTime = realTime,
        position = position,
    )
}
