package pl.dakil.transport.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.material3.DisappearingCompassButton
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import androidx.compose.ui.text.style.TextOverflow
import pl.dakil.transport.ui.components.AttributeChip
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.parseRouteColor
import pl.dakil.transport.ui.components.shortMessage
import pl.dakil.transport.ui.navigation.DepartureBoardRoute
import pl.dakil.transport.domain.model.lastPassedIndex
import pl.dakil.transport.ui.trip.rememberTickingNow
import pl.dakil.transport.ui.trip.tripTimetable

// The mode palette, glyph keys and stroke live in MapMarkers.kt — shared with the itinerary's
// route map so a stop looks like the same object wherever the app draws one.

private fun TransitLocation.markerColorHex(): String = markerColorHex(primaryMode ?: TransportMode.OTHER)

/** Line color for drawing this route on the map, preferring the feed's GTFS route color. */
private fun RouteShape.lineColorHex(): String = routeMarkerColorHex(routeColor, mode)

private fun TransitLocation.markerIconKey(): String = markerIconKey(primaryMode ?: TransportMode.OTHER)

/** Marker fill for a vehicle: the feed's GTFS route color when valid, else the mode color. */
private fun VehicleMarker.markerColorHex(): String = routeMarkerColorHex(routeColor, mode)

/**
 * Stroke distinguishing vehicles whose times a real-time feed corrected from ones running on
 * the plain timetable. Neither is a tracked position — see [pl.dakil.transport.domain.model.VehicleMotionSettings].
 */
private const val VEHICLE_STROKE_LIVE = "#2E7D32"
private const val VEHICLE_STROKE_TIMETABLE = "#9E9E9E"

/**
 * Zooms at which the stop pins gain their mode icon and their name label, when the pins
 * themselves are shown from the default zoom. A "Stops from zoom" setting below these carries
 * them down with it, so lowering it never yields bare dots with no way to tell stops apart.
 */
private const val STOP_ICONS_MIN_ZOOM = 15f
private const val STOP_LABELS_MIN_ZOOM = 14f
private const val STOPS_DEFAULT_MIN_ZOOM = 13f
private val STOP_TAP_TARGET_RADIUS = 24.dp

/**
 * Zoom at which a stop detail layer ([defaultZoom] by default) turns on, given the zoom stops
 * appear at. Lowering "Stops from zoom" drags the detail down by the same amount, keeping the
 * gap it normally has above the pins; raising it past a detail simply pushes the detail up.
 */
private fun detailZoom(stopsMinZoom: Float, defaultZoom: Float): Float =
    (stopsMinZoom + (defaultZoom - STOPS_DEFAULT_MIN_ZOOM)).coerceAtLeast(stopsMinZoom)

/**
 * Neutral marker color for a location that isn't a transit stop (a picked point or a plain
 * place) — matches [markerColorHex] for [TransportMode.OTHER], which colors its halo.
 */
private val PICKED_POINT_COLOR = Color(0xFF78909C)

/** Share of the map the selected vehicle's panel may take before its timetable scrolls. */
private const val VEHICLE_PANEL_MAX_MAP_FRACTION = 0.4f

/** How long the camera takes to reach a freshly selected vehicle. */
private val FOLLOW_CENTER_DURATION = 500.milliseconds

/** Longest the viewport may go unreported while the camera never settles. */
private const val VIEWPORT_HEARTBEAT_MILLIS = 30_000L

/**
 * Camera target that puts [vehicle] in the middle of the map a panel of [panelHeight] leaves
 * visible: the camera aims half a panel *below* the vehicle, which lifts the vehicle by the
 * same amount. Done in screen space rather than with `CameraPosition.padding`, which MapLibre
 * applies in one step at the end of an animation instead of animating into it.
 */
/** Bounds containing every point, or null when there are none to contain. */
private fun List<GeoPoint>.boundingBox(): BoundingBox? {
    if (isEmpty()) return null
    return BoundingBox(
        west = minOf { it.lon },
        south = minOf { it.lat },
        east = maxOf { it.lon },
        north = maxOf { it.lat },
    )
}

private fun CameraProjection.targetCentering(vehicle: GeoPoint, panelHeight: Dp): Position {
    val onScreen = screenLocationFromPosition(Position(latitude = vehicle.lat, longitude = vehicle.lon))
    return positionFromScreenLocation(DpOffset(onScreen.x, onScreen.y + panelHeight / 2))
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(
    onOpenTimetable: (DepartureBoardRoute) -> Unit,
    onNavigateToConnections: () -> Unit,
    onOpenLocationSearch: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val styleJson by viewModel.styleJson.collectAsStateWithLifecycle()
    val selectedStop by viewModel.selectedStop.collectAsStateWithLifecycle()
    val stopRoutes by viewModel.stopRoutes.collectAsStateWithLifecycle()
    val selectedVehicle by viewModel.selectedVehicle.collectAsStateWithLifecycle()
    val vehicleDetails by viewModel.vehicleDetails.collectAsStateWithLifecycle()
    // A trip opened from a timetable. It outlives its marker: a run that is not on the road has
    // no vehicle to draw, and only its route and stops are shown.
    val pinnedTrip by viewModel.pinnedTrip.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    // Delegated on purpose: the map content lambda is composed once and never swapped, so the
    // layers below must read this through its State (a captured value would freeze at startup).
    val stopsFocusedOnTrip by viewModel.stopsFocusedOnTrip.collectAsStateWithLifecycle()
    // Following a run means its stops are the point of the view, so they ignore the
    // stops-from-zoom floor the way the selection halo already does.
    val currentStopsMinZoom by viewModel.stopsMinZoom.collectAsStateWithLifecycle()
    // Derived state, not a plain value: the layers below read this from inside the map content
    // lambda, which captures values once — only a State read stays live.
    val effectiveStopsMinZoom by remember {
        derivedStateOf { if (stopsFocusedOnTrip) 0f else currentStopsMinZoom }
    }
    val routeFrom by viewModel.routeFrom.collectAsStateWithLifecycle()
    val routeTo by viewModel.routeTo.collectAsStateWithLifecycle()
    val routeDraftVisible by viewModel.routeDraftVisible.collectAsStateWithLifecycle()
    val stayOnMapWhenPickingRoute by viewModel.stayOnMapWhenPickingRoute.collectAsStateWithLifecycle()

    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val density = LocalDensity.current

    /** Measured height of the route draft bar, so the compass can sit clear of it. */
    var routeDraftHeight by remember { mutableStateOf(0.dp) }

    /** Measured height of the map itself, which the vehicle panel is sized against. */
    var mapHeight by remember { mutableStateOf(0.dp) }

    /**
     * The part of the map the vehicle panel hides. Its ceiling, not its measured height: the
     * panel grows as the trip's details land, and following a live height would shunt the map
     * mid-follow every time it did.
     */
    val vehiclePanelHeight = mapHeight * VEHICLE_PANEL_MAX_MAP_FRACTION

    /**
     * Filling one end of the route from the map. Staying put is the point of the draft bar —
     * the user is already looking at the other end — so the search form only opens once the
     * route is complete (or when the setting turns the whole behaviour off).
     */
    fun pickRouteEndpoint(isStart: Boolean, stop: TransitLocation) {
        viewModel.clearSelection()
        if (isStart) viewModel.beginHere(stop) else viewModel.finishHere(stop)
        val otherEnd = if (isStart) routeTo else routeFrom
        if (!stayOnMapWhenPickingRoute || otherEnd != null) {
            viewModel.hideRouteDraft()
            onNavigateToConnections()
        }
    }

    // Back peels the map's own layers off one at a time — filter panel, then whichever info
    // panel is open — instead of leaving the app from under a full-screen selection.
    BackHandler(
        enabled = filtersExpanded || selectedVehicle != null || pinnedTrip != null ||
            selectedStop != null || routeDraftVisible,
    ) {
        when {
            filtersExpanded -> filtersExpanded = false
            // Stop first, then the vehicle: with both open the stop panel is on top.
            selectedStop != null -> viewModel.clearStopSelection()
            selectedVehicle != null || pinnedTrip != null -> viewModel.clearVehicleSelection()
            // Abandoning the draft leaves the picks in the search form; only the bar goes away.
            else -> viewModel.hideRouteDraft()
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Only ever set by the locate-me button: on entry the camera restores the stored position
    // instead, so auto-centering on the user would throw that restored position away.
    var pendingLocateMe by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    // The camera state is built once and never rebuilt, so its start position has to be known
    // before it exists — hence waiting on the stored one rather than moving the camera after.
    val initialCamera = viewModel.initialCamera.collectAsStateWithLifecycle().value ?: return
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = initialCamera.lat, longitude = initialCamera.lon),
            zoom = initialCamera.zoom,
        ),
    )
    val styleState = rememberStyleState()

    // Where the ViewModel wants the camera: a location picked in the map's search field (already
    // selected there, info panel + halo included), or a trip opened from a timetable, whose
    // marker the follow effect below then takes over from this zoom.
    val cameraTarget by viewModel.cameraTarget.collectAsStateWithLifecycle()
    LaunchedEffect(cameraTarget) {
        cameraTarget?.let { target ->
            // Stop an in-flight locate-me animation from overriding the searched location.
            pendingLocateMe = false
            cameraState.animateTo(
                CameraPosition(
                    target = Position(latitude = target.lat, longitude = target.lon),
                    zoom = 16.0,
                ),
            )
            viewModel.consumeCameraTarget()
        }
    }

    // A trip opened from a timetable whose run is off the road: with no marker to fly to and
    // follow, the camera frames the whole line once its geometry lands, which is the only thing
    // there is to look at. Keyed on the trip, so it happens once per opening and never fights the
    // user's own panning afterwards.
    val pinnedTripShape = pinnedTrip
        ?.takeIf { !it.isRunning }
        ?.let { (vehicleDetails as? VehicleDetailsUiState.Shown)?.details?.shape }
    LaunchedEffect(pinnedTrip?.tripId, pinnedTripShape != null) {
        val bounds = pinnedTripShape?.segments?.flatten()?.boundingBox() ?: return@LaunchedEffect
        pendingLocateMe = false
        cameraState.animateTo(
            boundingBox = bounds,
            // Clear of the panel below and the search bar above, so the whole run stays visible.
            padding = PaddingValues(start = 32.dp, top = 96.dp, end = 32.dp, bottom = vehiclePanelHeight + 32.dp),
            duration = FOLLOW_CENTER_DURATION,
        )
    }

    // The map click callback below is captured once by MaplibreMap and never refreshed, so it
    // must read current data through State objects rather than capture the values directly.
    val stopsById by rememberUpdatedState(remember(stops) { stops.associateBy { it.favoriteKey } })
    val vehiclesById by rememberUpdatedState(remember(vehicles) { vehicles.associateBy { it.id } })
    val currentSelectedStop by rememberUpdatedState(selectedStop)
    val currentSelectedVehicle by rememberUpdatedState(selectedVehicle)

    LaunchedEffect(cameraState) {
        merge(
            snapshotFlow { cameraState.isCameraMoving }
                .debounce(400)
                .filter { moving -> !moving },
            // Following a vehicle keeps the camera in near-constant motion, so it would
            // otherwise never settle and the viewport would go stale — taking the followed
            // trip's own segment fetches down with it. A standing viewport re-reports the same
            // box, which the ViewModel discards, so this costs nothing when nothing moves.
            flow {
                while (true) {
                    delay(VIEWPORT_HEARTBEAT_MILLIS)
                    emit(false)
                }
            },
        ).collect {
            // The ViewModel gates stop/vehicle fetches on zoom itself.
            val bbox = cameraState.projection?.queryVisibleBoundingBox()
            if (bbox != null) {
                viewModel.onViewportSettled(
                    bbox.south, bbox.west, bbox.north, bbox.east,
                    zoom = cameraState.position.zoom,
                )
            }
        }
    }

    // Selecting a vehicle hands the camera over to it until the user takes it back. The
    // followed vehicle is centred in the map the panel leaves visible, not in the map as a
    // whole — that is what CameraPosition.padding shifts.
    var followingVehicle by remember { mutableStateOf(false) }
    val followedVehicleId = selectedVehicle?.id
    LaunchedEffect(followedVehicleId) { followingVehicle = followedVehicleId != null }

    // One pan or pinch of the user's own ends the follow at once, mid-animation included.
    LaunchedEffect(followingVehicle) {
        if (!followingVehicle) return@LaunchedEffect
        snapshotFlow { cameraState.position to cameraState.moveReason }
            .drop(1)
            .first { (_, reason) -> reason == CameraMoveReason.GESTURE }
        followingVehicle = false
    }

    LaunchedEffect(followingVehicle) {
        if (!followingVehicle) return@LaunchedEffect
        val projection = cameraState.awaitProjection()
        // The first move is an animation — the vehicle is wherever it was tapped. After that
        // its marker is redrawn many times a second, so the camera is set outright instead of
        // queueing animations that would each land after the marker had moved on again.
        var centered = false
        snapshotFlow { selectedVehicle?.position to vehiclePanelHeight }
            .collect { (position, panelHeight) ->
                // Until the panel has been measured there is nothing to aim at: centring on the
                // whole map first would land the vehicle short and snap it up a frame later.
                if (position == null || panelHeight <= 0.dp) return@collect
                val camera = cameraState.position.copy(
                    target = projection.targetCentering(position, panelHeight),
                )
                if (centered) {
                    cameraState.position = camera
                } else {
                    centered = true
                    cameraState.animateTo(camera, duration = FOLLOW_CENTER_DURATION)
                }
            }
    }

    val locationState = if (hasLocationPermission) {
        val locationProvider = rememberDefaultLocationProvider()
        rememberUserLocationState(locationProvider)
    } else {
        null
    }

    LaunchedEffect(pendingLocateMe, hasLocationPermission, locationState) {
        if (pendingLocateMe && hasLocationPermission && locationState != null) {
            // Wait for the first fix; on app entry the provider usually has none yet.
            val position = snapshotFlow { locationState.location?.position?.value }
                .filterNotNull()
                .first()
            cameraState.animateTo(CameraPosition(target = position, zoom = 15.0))
            pendingLocateMe = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> mapHeight = with(density) { size.height.toDp() } },
    ) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = styleJson?.let { BaseStyle.Json(it) }
                ?: return@Box, // patched style still loading from assets; it arrives within moments
            cameraState = cameraState,
            styleState = styleState,
            options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
            onMapClick = { _, clickOffset ->
                val projection = cameraState.projection
                if (projection == null) {
                    ClickResult.Pass
                } else {
                    val hitRect = DpRect(
                        left = clickOffset.x - STOP_TAP_TARGET_RADIUS,
                        top = clickOffset.y - STOP_TAP_TARGET_RADIUS,
                        right = clickOffset.x + STOP_TAP_TARGET_RADIUS,
                        bottom = clickOffset.y + STOP_TAP_TARGET_RADIUS,
                    )
                    fun nearestFeatureId(layerId: String): String? = projection
                        .queryRenderedFeatures(rect = hitRect, layerIds = setOf(layerId))
                        .minByOrNull { candidate ->
                            val position = (candidate.geometry as? Point)?.coordinates
                            val candidateOffset = position?.let { projection.screenLocationFromPosition(it) }
                            if (candidateOffset != null) {
                                val dx = (candidateOffset.x - clickOffset.x).value
                                val dy = (candidateOffset.y - clickOffset.y).value
                                dx * dx + dy * dy
                            } else {
                                Float.MAX_VALUE
                            }
                        }?.id?.content
                    // Vehicles render above stops, so they win the tap too.
                    val vehicle = nearestFeatureId("transport-vehicles")?.let { vehiclesById[it] }
                    val stop = if (vehicle == null) nearestFeatureId("transport-stops")?.let { stopsById[it] } else null
                    when {
                        vehicle != null -> {
                            viewModel.selectVehicle(vehicle)
                            ClickResult.Consume
                        }
                        stop != null -> {
                            viewModel.selectStop(stop)
                            ClickResult.Consume
                        }
                        // Tapping empty map dismisses whichever info panel is open.
                        currentSelectedStop != null || currentSelectedVehicle != null -> {
                            viewModel.clearSelection()
                            ClickResult.Consume
                        }
                        else -> ClickResult.Pass
                    }
                }
            },
            // Long press picks a bare coordinate anywhere on the map — including places with no
            // stop nearby — so it deliberately ignores whatever feature sits under the finger.
            onMapLongClick = { position, _ ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.selectPoint(position.latitude, position.longitude)
                ClickResult.Consume
            },
        ) {
            // This content lambda is composed once by the library and never swapped for an
            // updated instance, so values captured from the outer composition stay frozen at
            // their first-composition state. Anything dynamic must be derived in here, from
            // snapshot state reads (like `stops`, `selectedStop`, `stopRoutes`).
            val stopFeatures = remember(stops) {
                FeatureCollection(
                    stops.map { stop ->
                        Feature<Point, JsonObject?>(
                            id = JsonPrimitive(stop.favoriteKey),
                            geometry = Point(Position(latitude = stop.lat, longitude = stop.lon)),
                            properties = JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(stop.name),
                                    "color" to JsonPrimitive(stop.markerColorHex()),
                                    "icon" to JsonPrimitive(stop.markerIconKey()),
                                ),
                            ),
                        )
                    },
                )
            }
            val stopsSource = rememberGeoJsonSource(data = GeoJsonData.Features(stopFeatures))
            // The selected vehicle's route joins the selected stop's routes overlay for as
            // long as the vehicle stays selected — or, for a trip opened from a timetable with
            // no vehicle on the road, for as long as the trip itself is open.
            val vehicleRouteShape = if (selectedVehicle != null || pinnedTrip != null) {
                (vehicleDetails as? VehicleDetailsUiState.Shown)?.details?.shape
            } else {
                null
            }
            val routeShapes = ((stopRoutes as? StopRoutesUiState.Shown)?.routes ?: emptyList()) +
                listOfNotNull(vehicleRouteShape)
            val routeFeatures = remember(routeShapes) {
                FeatureCollection(
                    routeShapes.flatMap { route ->
                        route.segments.map { segment ->
                            Feature<LineString, JsonObject?>(
                                geometry = LineString(
                                    segment.map { Position(latitude = it.lat, longitude = it.lon) },
                                ),
                                properties = JsonObject(
                                    mapOf("color" to JsonPrimitive(route.lineColorHex())),
                                ),
                            )
                        }
                    },
                )
            }
            val routesSource = rememberGeoJsonSource(data = GeoJsonData.Features(routeFeatures))
            val selectedFeatures = remember(selectedStop, selectedVehicle) {
                FeatureCollection(
                    listOfNotNull(
                        selectedStop?.let { stop ->
                            Feature<Point, JsonObject?>(
                                geometry = Point(Position(latitude = stop.lat, longitude = stop.lon)),
                                properties = JsonObject(
                                    mapOf("color" to JsonPrimitive(stop.markerColorHex())),
                                ),
                            )
                        },
                        // The vehicle halo follows the marker's interpolated position.
                        selectedVehicle?.let { vehicle ->
                            Feature<Point, JsonObject?>(
                                geometry = Point(
                                    Position(latitude = vehicle.position.lat, longitude = vehicle.position.lon),
                                ),
                                properties = JsonObject(
                                    mapOf("color" to JsonPrimitive(vehicle.markerColorHex())),
                                ),
                            )
                        },
                    ),
                )
            }
            val selectedSource = rememberGeoJsonSource(data = GeoJsonData.Features(selectedFeatures))
            // A picked point has no stop marker of its own to sit on, so it gets a pin glyph
            // inside its halo — otherwise a long press would leave just a faint gray circle.
            val pointFeatures = remember(selectedStop) {
                FeatureCollection(
                    listOfNotNull(
                        selectedStop?.takeIf { it.stopId == null }?.let { point ->
                            Feature<Point, JsonObject?>(
                                geometry = Point(Position(latitude = point.lat, longitude = point.lon)),
                                properties = null,
                            )
                        },
                    ),
                )
            }
            val pointSource = rememberGeoJsonSource(data = GeoJsonData.Features(pointFeatures))
            val vehicleFeatures = remember(vehicles) {
                FeatureCollection(
                    vehicles.map { vehicle ->
                        Feature<Point, JsonObject?>(
                            id = JsonPrimitive(vehicle.id),
                            geometry = Point(
                                Position(latitude = vehicle.position.lat, longitude = vehicle.position.lon),
                            ),
                            properties = JsonObject(
                                mapOf(
                                    "label" to JsonPrimitive(vehicle.label),
                                    "color" to JsonPrimitive(vehicle.markerColorHex()),
                                    // Stroke computed here rather than via a style expression:
                                    // keeps the layer definitions free of boolean-case DSL.
                                    "stroke" to JsonPrimitive(
                                        if (vehicle.realTime) VEHICLE_STROKE_LIVE else VEHICLE_STROKE_TIMETABLE,
                                    ),
                                    "icon" to JsonPrimitive(markerIconKey(vehicle.mode)),
                                ),
                            ),
                        )
                    },
                )
            }
            val vehiclesSource = rememberGeoJsonSource(data = GeoJsonData.Features(vehicleFeatures))
            // Non-SDF glyphs tinted white up front: crisper than SDF rendering at this size.
            val markerIconImage = rememberMarkerIconImage()
            // Above street names and road shields so stops never hide behind them, but still
            // below place labels (village/town/city names), matching Google Maps. NB: the
            // anchor layer must exist in the style or MapLibre throws when adding these layers.
            // Declaration order stacks bottom-to-top: routes < selection halo < stop pins.
            Anchor.Above("road_shield") {
                LineLayer(
                    id = "transport-stop-routes",
                    source = routesSource,
                    color = feature["color"].convertToColor(),
                    width = interpolate(
                        linear(),
                        zoom(),
                        11 to const(2.5.dp),
                        16 to const(5.dp),
                    ),
                    opacity = const(0.8f),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                // Selection halo: kept visible at every zoom so the picked stop stays findable
                // even when the stop pins themselves are hidden (below their minZoom).
                CircleLayer(
                    id = "transport-stop-selected",
                    source = selectedSource,
                    radius = interpolate(
                        linear(),
                        zoom(),
                        13 to const(10.dp),
                        15 to const(12.dp),
                        16 to const(20.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    opacity = const(0.3f),
                    strokeColor = feature["color"].convertToColor(),
                    strokeWidth = const(1.5.dp),
                )
                SymbolLayer(
                    id = "transport-picked-point",
                    source = pointSource,
                    iconImage = image(
                        rememberVectorPainter(Icons.Default.Place),
                        DpSize(24.dp, 24.dp),
                        colorFilter = ColorFilter.tint(PICKED_POINT_COLOR),
                    ),
                    // Pin tip sits on the picked coordinate, like a dropped map pin.
                    iconAnchor = const(SymbolAnchor.Bottom),
                    iconAllowOverlap = const(true),
                )
                CircleLayer(
                    id = "transport-stops",
                    source = stopsSource,
                    minZoom = effectiveStopsMinZoom,
                    // Small dots while zoomed out, growing into icon-bearing pins by z16.
                    radius = interpolate(
                        linear(),
                        zoom(),
                        13 to const(4.dp),
                        15 to const(6.dp),
                        16 to const(12.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    strokeColor = const(MARKER_STROKE_COLOR),
                    strokeWidth = const(1.dp),
                )
                SymbolLayer(
                    id = "transport-stop-icons",
                    source = stopsSource,
                    minZoom = detailZoom(effectiveStopsMinZoom, STOP_ICONS_MIN_ZOOM),
                    iconImage = markerIconImage,
                    // Scale the glyph together with the circle it sits on.
                    iconSize = interpolate(
                        linear(),
                        zoom(),
                        15 to const(0.6f),
                        16 to const(1f),
                    ),
                    iconAllowOverlap = const(true),
                )
                SymbolLayer(
                    id = "transport-stop-labels",
                    source = stopsSource,
                    minZoom = detailZoom(effectiveStopsMinZoom, STOP_LABELS_MIN_ZOOM),
                    textField = format(span(feature["name"].asString())),
                    // Must be a fontstack the style's glyph server actually serves (the library
                    // default 404s there); Roboto also matches the basemap's typography.
                    textFont = const(listOf("Roboto Regular")),
                    textSize = const(0.75f.em),
                    textOffset = offset(0f.em, 1.4f.em),
                    textAnchor = const(SymbolAnchor.Top),
                    // Fixed dark gray: the base map is light regardless of app theme, and
                    // theme-derived grays wash out against it in dark mode.
                    textColor = const(Color(0xFF424242)),
                )
                // Vehicles stack above stops: they move, so they should never hide under pins.
                CircleLayer(
                    id = "transport-vehicles",
                    source = vehiclesSource,
                    radius = interpolate(
                        linear(),
                        zoom(),
                        9 to const(5.dp),
                        13 to const(8.dp),
                        16 to const(12.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    strokeColor = feature["stroke"].convertToColor(),
                    strokeWidth = const(1.5.dp),
                )
                SymbolLayer(
                    id = "transport-vehicle-icons",
                    source = vehiclesSource,
                    minZoom = 11f,
                    iconImage = markerIconImage,
                    iconSize = interpolate(
                        linear(),
                        zoom(),
                        11 to const(0.6f),
                        16 to const(1f),
                    ),
                    iconAllowOverlap = const(true),
                )
                SymbolLayer(
                    id = "transport-vehicle-labels",
                    source = vehiclesSource,
                    minZoom = 12f,
                    textField = format(span(feature["label"].asString())),
                    textFont = const(listOf("Roboto Regular")),
                    textSize = const(0.75f.em),
                    textOffset = offset(0f.em, 1.4f.em),
                    textAnchor = const(SymbolAnchor.Top),
                    textColor = const(Color(0xFF424242)),
                )
            }
            if (locationState != null) {
                LocationPuck(
                    idPrefix = "user",
                    location = locationState.location,
                    cameraState = cameraState,
                )
            }
        }

        DisappearingCompassButton(
            cameraState = cameraState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                // Below the search bar (8dp gap + its 56dp height + 16dp spacing), and below
                // the route draft bar too — measured rather than assumed, so the compass rides
                // the bar's expand/collapse animation instead of jumping once it settles.
                .padding(top = 80.dp + routeDraftHeight, end = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth(),
        ) {
            MapSearchBar(
                onClick = onOpenLocationSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            AnimatedVisibility(
                visible = routeDraftVisible,
                modifier = Modifier.onSizeChanged { size ->
                    routeDraftHeight = with(density) { size.height.toDp() }
                },
            ) {
                MapRouteDraftBar(
                    from = routeFrom,
                    to = routeTo,
                    onClearFrom = viewModel::clearRouteFrom,
                    onClearTo = viewModel::clearRouteTo,
                    onSearch = {
                        viewModel.hideRouteDraft()
                        onNavigateToConnections()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
            MapFiltersMenu(
                filters = filters,
                expanded = filtersExpanded,
                onExpandedChange = { filtersExpanded = it },
                onUpdate = viewModel::updateFilters,
                onReset = viewModel::resetFilters,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 72.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            // Map ornaments live above the stop panel so opening it lifts them instead of
            // covering them.
            Box(modifier = Modifier.fillMaxWidth()) {
                ExpandingAttributionButton(
                    cameraState = cameraState,
                    styleState = styleState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    contentAlignment = Alignment.BottomStart,
                )

                TransitousAttributionLabel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 72.dp, bottom = 20.dp),
                )

                FloatingActionButton(
                    onClick = {
                        if (!hasLocationPermission) {
                            pendingLocateMe = true
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        } else {
                            pendingLocateMe = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_locate_me))
                }
            }

            // Keeps showing the last stop while the panel animates out after deselection.
            var displayedStop by remember { mutableStateOf<TransitLocation?>(null) }
            selectedStop?.let { displayedStop = it }
            AnimatedVisibility(
                visible = selectedStop != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                displayedStop?.let { stop ->
                    StopInfoPanel(
                        stop = stop,
                        routesState = stopRoutes,
                        isFavorite = favorites.containsLocation(stop),
                        onToggleFavorite = { viewModel.toggleFavoriteStop(stop) },
                        onClose = { viewModel.clearStopSelection() },
                        onOpenTimetable = {
                            viewModel.clearSelection()
                            onOpenTimetable(
                                DepartureBoardRoute(
                                    stopName = stop.name,
                                    lat = stop.lat,
                                    lon = stop.lon,
                                    stopId = stop.stopId,
                                    timeIso = null,
                                ),
                            )
                        },
                        onBeginHere = { pickRouteEndpoint(isStart = true, stop = stop) },
                        onFinishHere = { pickRouteEndpoint(isStart = false, stop = stop) },
                    )
                }
            }

            // Same animate-out pattern for the vehicle panel. It gives way to the stop panel
            // rather than stacking with it: tapping a stop of the followed run keeps the
            // vehicle selected, so the two can now be open at once.
            // A pinned trip heads the panel itself while its run is off the road: there is no
            // marker to read the line from, and the trip is still what the panel is about.
            val panelLine = selectedVehicle?.panelLine() ?: pinnedTrip?.panelLine()
            var displayedLine by remember { mutableStateOf<VehiclePanelLine?>(null) }
            panelLine?.let { displayedLine = it }
            AnimatedVisibility(
                visible = panelLine != null && selectedStop == null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                displayedLine?.let { line ->
                    // Where the vehicle is actually going. `/map/trips` only knows the next
                    // stop, so the destination comes from the trip details fetched on
                    // selection; until they land there is nothing truthful to show.
                    val destination = (vehicleDetails as? VehicleDetailsUiState.Shown)?.details?.headsign
                    val favoriteLine = line.favoriteLine(destination)
                    VehicleInfoPanel(
                        line = line,
                        destination = destination,
                        detailsState = vehicleDetails,
                        // Starring is held back until the destination is known: the favourite's
                        // key is built from it, and a wrong key saves a duplicate line.
                        isFavorite = favoriteLine
                            ?.takeIf { vehicleDetails !is VehicleDetailsUiState.Loading }
                            ?.let(favorites::containsLine),
                        onToggleFavorite = { favoriteLine?.let(viewModel::toggleFavoriteLine) },
                        onClose = { viewModel.clearVehicleSelection() },
                        maxHeight = vehiclePanelHeight,
                    )
                }
            }
        }
    }
}

/**
 * M3-search-bar-styled field overlaying the top of the map. Not a real input: tapping it
 * opens the full-screen [pl.dakil.transport.ui.search.LocationPickerScreen], whose pick
 * flows back to the map as a selection + camera move.
 */
@Composable
private fun MapSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
            Text(
                text = stringResource(R.string.map_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the vehicle panel heads itself with. Comes from the marker of a vehicle being followed, or
 * from the trip itself when one was opened from a timetable and its run is not on the road — the
 * panel is the same either way, the run just has no position to describe.
 */
private data class VehiclePanelLine(
    val tripId: String?,
    val label: String,
    val mode: TransportMode,
    val routeColor: String?,
    /** The vehicle's next stop; null when nothing is running (or before the first fetch lands). */
    val nextStopName: String?,
    /** Null for a run that is off the road: neither "live" nor "scheduled" is true of it. */
    val realTime: Boolean?,
) {

    /** See [VehicleMarker.favoriteLine] — [destination] must be the headsign, not [nextStopName]. */
    fun favoriteLine(destination: String?): FavoriteLine? = tripId?.let {
        FavoriteLine(label = label, headsign = destination, mode = mode, routeColor = routeColor, tripId = it)
    }
}

private fun VehicleMarker.panelLine() = VehiclePanelLine(
    tripId = tripId,
    label = label,
    mode = mode,
    routeColor = routeColor,
    nextStopName = nextStopName,
    realTime = realTime,
)

private fun PendingMapTrip.panelLine() = VehiclePanelLine(
    tripId = tripId,
    label = label,
    mode = mode,
    routeColor = routeColor,
    nextStopName = null,
    realTime = null,
)

/**
 * Inline info panel for a tapped vehicle, mirroring [StopInfoPanel]. Shows the trip's
 * attributes and its timetable as they load ([detailsState]).
 *
 * The panel is capped at half the screen so the map above it stays usable; the header stays
 * put and only the timetable scrolls, which is also why it is not draggable/expandable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleInfoPanel(
    line: VehiclePanelLine,
    /** The trip's destination, once its details have loaded; null until then. */
    destination: String?,
    detailsState: VehicleDetailsUiState,
    /** Null hides the star entirely (no trip id to identify the line by). */
    isFavorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    /** Ceiling on the whole panel, so the map keeps the bigger share of the screen. */
    maxHeight: Dp,
) {
    val timetable = (detailsState as? VehicleDetailsUiState.Shown)?.details?.timetable.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                // The timetable runs to the panel's edge: its rows carry their own padding, and
                // a gap below the last visible one reads as the list having stopped scrolling.
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = if (timetable.isEmpty()) 12.dp else 0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Same colored-circle look as the vehicle's marker on the map.
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .background(
                            parseRouteColor(line.routeColor, markerColor(line.mode)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = line.mode.icon,
                        contentDescription = stringResource(line.mode.labelRes),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination?.let { stringResource(R.string.format_route_arrow, line.label, it) }
                            ?: line.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = (detailsState as? VehicleDetailsUiState.Shown)?.details
                    Text(
                        text = listOfNotNull(
                            stringResource(line.mode.labelRes),
                            line.nextStopName?.let { stringResource(R.string.map_vehicle_next_stop, it) },
                            details?.agencyName ?: details?.routeLongName,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isFavorite != null) {
                    FavoriteButton(isFavorite = isFavorite, onToggle = onToggleFavorite)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
            ) {
                when (line.realTime) {
                    true -> AttributeChip(
                        Icons.Default.Sensors,
                        stringResource(R.string.attribute_live),
                        Color(0xFF2E7D32),
                    )
                    false -> AttributeChip(Icons.Default.Schedule, stringResource(R.string.attribute_scheduled))
                    // Nothing is running, so neither chip is true of it.
                    null -> {}
                }
                when (val state = detailsState) {
                    is VehicleDetailsUiState.Shown -> VehicleAmenityChips(
                        wheelchairAccessible = state.details.wheelchairAccessible,
                        bikesAllowed = state.details.bikesAllowed,
                    )
                    is VehicleDetailsUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    is VehicleDetailsUiState.Error -> Text(
                        text = state.error.shortMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> {}
                }
            }

            if (timetable.isNotEmpty()) {
                val now = rememberTickingNow()
                val currentIndex = timetable.lastPassedIndex(now)
                val listState = rememberLazyListState()
                // Open on where the vehicle is now rather than at the run's first stop.
                LaunchedEffect(timetable.first().place.favoriteKey) {
                    if (currentIndex > 0) listState.scrollToItem(currentIndex)
                }
                LazyColumn(
                    state = listState,
                    // fill = false so a short run keeps the panel short instead of stretching it.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = 4.dp, end = 8.dp),
                ) {
                    tripTimetable(
                        stops = timetable,
                        railColor = parseRouteColor(line.routeColor, markerColor(line.mode)),
                        currentIndex = currentIndex,
                    )
                }
            }
        }
    }
}

/**
 * Inline (non-modal) info panel for a tapped stop, docked to the bottom of the map so the
 * map above it stays fully interactive while it is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopInfoPanel(
    stop: TransitLocation,
    routesState: StopRoutesUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onOpenTimetable: () -> Unit,
    onBeginHere: () -> Unit,
    onFinishHere: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            // A plain place or a point picked on the map is not served by any mode, so it gets
            // a map pin rather than a (meaningless, always-bus) vehicle icon, and none of the
            // stop-specific content: no lines, no timetable.
            val isPoint = stop.stopId == null
            Row(verticalAlignment = Alignment.CenterVertically) {
                val mode = stop.primaryMode ?: TransportMode.OTHER
                // Same colored-circle look as the stop's marker on the map.
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .background(if (isPoint) PICKED_POINT_COLOR else markerColor(mode), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPoint) Icons.Default.Place else mode.icon,
                        contentDescription = stringResource(if (isPoint) R.string.label_place else mode.stopLabelRes),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (isPoint) {
                        stop.areaLabel ?: formatCoordinates(stop.lat, stop.lon)
                    } else {
                        // The place, not the mode: "Bus stop", not "Bus" — otherwise a selected
                        // stop reads exactly like a selected vehicle of the same mode.
                        stop.primaryMode?.let { stringResource(it.stopLabelRes) }
                    }
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FavoriteButton(isFavorite = isFavorite, onToggle = onToggleFavorite)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            // Line chips + route overlay load automatically on selection (real stops only);
            // the spinner covers the brief fetch window.
            when (routesState) {
                is StopRoutesUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
                is StopRoutesUiState.Shown -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                ) {
                    routesState.routes.forEach { route ->
                        // The map keeps the feed's colours in every mode: markers and overlays have
                        // no draw order to hand a palette out along.
                        ModeChip(
                            mode = route.mode,
                            label = route.lineLabel,
                            containerColor = parseRouteColor(route.routeColor, route.mode.color),
                        )
                    }
                }
                is StopRoutesUiState.Error -> Text(
                    text = stringResource(
                        R.string.map_stop_lines_error,
                        routesState.error.shortMessage.lowercase(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                )
                is StopRoutesUiState.Empty -> Text(
                    text = stringResource(R.string.map_stop_lines_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                )
                else -> {}
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            ) {
                if (!isPoint) {
                    PanelActionButton(
                        stringResource(R.string.map_action_timetable),
                        Icons.Default.Schedule,
                        onOpenTimetable,
                    )
                }
                PanelActionButton(stringResource(R.string.map_action_begin_here), Icons.Default.NearMe, onBeginHere)
                PanelActionButton(stringResource(R.string.map_action_finish_here), Icons.Default.Flag, onFinishHere)
            }
        }
    }
}

@Composable
private fun PanelActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}
