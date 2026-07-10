package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.MapFiltersRepository
import pl.dakil.transport.data.repo.MapStyleRepository
import pl.dakil.transport.data.repo.RoutesRepository
import pl.dakil.transport.data.repo.StopsRepository
import pl.dakil.transport.data.repo.VehiclesRepository
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.MapFilters
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.domain.model.TripDetails
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
)

/** State of the selected vehicle's trip details (info panel attributes + route overlay). */
sealed interface VehicleDetailsUiState {
    data object Hidden : VehicleDetailsUiState
    data object Loading : VehicleDetailsUiState
    data class Shown(val details: TripDetails) : VehicleDetailsUiState
    data class Error(val message: String) : VehicleDetailsUiState
}

/** State of the routes overlay + line chips loaded for the currently selected stop. */
sealed interface StopRoutesUiState {
    data object Hidden : StopRoutesUiState
    data object Loading : StopRoutesUiState
    data class Shown(val routes: List<RouteShape>) : StopRoutesUiState
    data class Error(val message: String) : StopRoutesUiState
}

/** Stops aren't fetched/shown below this zoom (matches the stop layers' minZoom). */
const val STOPS_MIN_ZOOM = 13.0

/** Vehicles are fetched from lower zooms — the API itself culls local services when zoomed out. */
const val VEHICLES_MIN_ZOOM = 9.0

private const val VEHICLES_REFRESH_MS = 30_000L
private const val VEHICLES_INTERPOLATE_MS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val stopsRepository: StopsRepository,
    private val routesRepository: RoutesRepository,
    private val vehiclesRepository: VehiclesRepository,
    private val mapStyleRepository: MapStyleRepository,
    private val filtersRepository: MapFiltersRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

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

    init {
        viewModelScope.launch {
            _styleJson.value = mapStyleRepository.transitFreeGmapsStyle()
        }
        viewModelScope.launch {
            _filters.value = filtersRepository.filters.first()
        }
    }

    fun updateFilters(transform: (MapFilters) -> MapFilters) {
        val updated = _filters.updateAndGet(transform)
        viewModelScope.launch { filtersRepository.save(updated) }
    }

    fun resetFilters() = updateFilters { MapFilters.DEFAULT }

    private val allStops: StateFlow<List<TransitLocation>> = viewport
        .filterNotNull()
        .distinctUntilChanged()
        .mapLatest { vp ->
            if (vp.zoom < STOPS_MIN_ZOOM) {
                emptyList()
            } else {
                stopsRepository.stopsInViewport(vp.south, vp.west, vp.north, vp.east).getOrDefault(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stops: StateFlow<List<TransitLocation>> =
        combine(allStops, filters) { stops, filters -> stops.filter(filters::matchesStop) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Fetching is gated only on "any vehicle category on" (not the specific categories/data
    // source), so tweaking filters refines the already-fetched segments instantly instead of
    // hitting the shared Transitous API again.
    private val vehicleSegments: StateFlow<List<VehicleSegment>> =
        combine(
            viewport,
            filters.mapLatest { it.vehicleCategories.isNotEmpty() }.distinctUntilChanged(),
        ) { vp, enabled ->
            vp?.takeIf { enabled && it.zoom >= VEHICLES_MIN_ZOOM }
        }
            .distinctUntilChanged()
            .flatMapLatest { vp ->
                if (vp == null) {
                    flowOf(emptyList())
                } else {
                    flow {
                        while (true) {
                            emit(
                                vehiclesRepository
                                    .vehiclesInViewport(vp.south, vp.west, vp.north, vp.east, vp.zoom)
                                    .getOrDefault(emptyList()),
                            )
                            delay(VEHICLES_REFRESH_MS)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vehicles: StateFlow<List<VehicleMarker>> =
        combine(vehicleSegments, filters) { segments, filters -> segments.filter(filters::matchesVehicle) }
            .flatMapLatest { segments ->
                if (segments.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // Re-interpolate positions every second between the 30s refetches.
                    flow {
                        while (true) {
                            emit(markersAt(segments, OffsetDateTime.now()))
                            delay(VEHICLES_INTERPOLATE_MS)
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
        combine(selectedVehicleSegments, vehicleSegments) { selected, fetched ->
            val tripKey = selected?.firstOrNull()?.tripKey
            if (tripKey == null) null else fetched.filter { it.tripKey == tripKey }.ifEmpty { selected }
        }
            .flatMapLatest { segments ->
                if (segments == null) {
                    flowOf<VehicleMarker?>(null)
                } else {
                    flow {
                        while (true) {
                            val now = OffsetDateTime.now()
                            emit(
                                markersAt(segments, now).firstOrNull()
                                    // Past the segments' time coverage: hold the last position.
                                    ?: segments.maxByOrNull { it.arrival }?.markerAt(now),
                            )
                            delay(VEHICLES_INTERPOLATE_MS)
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

    /** Called once the map camera has settled (already debounced by the caller). */
    fun onViewportSettled(south: Double, west: Double, north: Double, east: Double, zoom: Double) {
        viewport.value = Viewport(south, west, north, east, zoom)
    }

    fun selectStop(stop: TransitLocation) {
        clearVehicleSelection()
        if (_selectedStop.value == stop) return
        _selectedStop.value = stop
        loadRoutes(stop)
    }

    fun clearSelection() {
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
                    _vehicleDetails.value = VehicleDetailsUiState.Error(error.message ?: "Something went wrong")
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
                        StopRoutesUiState.Error("No route shapes available for this stop")
                    } else {
                        StopRoutesUiState.Shown(routes)
                    }
                },
                onFailure = { error ->
                    _stopRoutes.value = StopRoutesUiState.Error(error.message ?: "Something went wrong")
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

/**
 * One marker per vehicle at [time]: the segment whose time window contains [time] wins;
 * a vehicle between segments (dwelling at a stop) sits at its next segment's start.
 */
private fun markersAt(segments: List<VehicleSegment>, time: OffsetDateTime): List<VehicleMarker> =
    segments.groupBy { it.tripKey }.mapNotNull { (_, tripSegments) ->
        val current = tripSegments.firstOrNull { time >= it.departure && time <= it.arrival }
            ?: tripSegments.filter { it.departure > time }.minByOrNull { it.departure }
            ?: return@mapNotNull null
        current.markerAt(time)
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
