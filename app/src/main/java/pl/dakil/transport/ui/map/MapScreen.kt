package pl.dakil.transport.ui.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
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
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.domain.model.TripStop
import androidx.compose.ui.text.style.TextOverflow
import pl.dakil.transport.ui.components.AttributeChip
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.parseRouteColor
import pl.dakil.transport.ui.components.rememberTickingNow
import pl.dakil.transport.ui.components.tripTimetable
import pl.dakil.transport.ui.itinerary.JourneyMapPane
import pl.dakil.transport.ui.components.shortMessage
import pl.dakil.transport.ui.navigation.DepartureBoardRoute
import pl.dakil.transport.ui.theme.SettledMotionScheme
import pl.dakil.transport.domain.model.nextStopIndex

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

/** How far the stop panel's other line chips fade back while one line is focused. */
private const val DIMMED_CHIP_ALPHA = 0.4f

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

/**
 * How tall `BottomSheetDefaults.DragHandle` measures — its 4 dp bar plus the 22 dp of padding M3
 * puts above and below it. Not a style choice of ours: the handle is the scaffold's own, drawn in
 * its own slot above the panel, and the sheet's peek height has to be the panel *plus* it.
 */
private val DRAG_HANDLE_HEIGHT = 48.dp

/** Bottom padding of a sheet panel whose last row is not a scrolling list. */
private val PANEL_BOTTOM_PADDING = 12.dp

/**
 * Room the sheet leaves under a panel's identity row when it rests at its header. Exactly the gap
 * every panel puts between that row and whatever follows it, so the sheet stops on the gap rather
 * than a few pixels into the next row — a sliver of chip tops peeking over the edge reads as the
 * sheet having failed to close on something.
 */
private val HEADER_BOTTOM_PADDING = 4.dp

/**
 * How much taller than its own header a panel must be before the header is worth resting at.
 * Roughly a row: below that, collapsing would stop the sheet where it already stood and a swipe
 * down would read as having failed to dismiss it.
 */
private val MIN_HEADER_COLLAPSE_GAIN = 48.dp

/** Gap between the vehicle panel's chips and its timetable. */
private val TIMETABLE_TOP_PADDING = 4.dp

/** How long the camera takes to reach a freshly selected vehicle. */
private val FOLLOW_CENTER_DURATION = 500.milliseconds

/** Longest the viewport may go unreported while the camera never settles. */
private const val VIEWPORT_HEARTBEAT_MILLIS = 30_000L

/**
 * How often the moving camera's box is handed to the cached-stop read. Roughly every few frames:
 * often enough that stops keep up with a pan, rare enough that a fling is a handful of disk
 * reads rather than one per frame.
 */
private const val LIVE_VIEWPORT_SAMPLE_MILLIS = 100L

/** How long the download-area outcome stays on the panel before it clears itself. */
private const val AREA_DOWNLOAD_MESSAGE_MILLIS = 4_000L

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

/**
 * Reserves [topInset] above the content and reports itself that much taller — the sheet handle's
 * room to stay out of the status bar. See the inset itself in [MapScreen] for why it is measured
 * here rather than read as a padding value in composition.
 */
private fun Modifier.sheetTopInset(topInset: () -> Dp) = layout { measurable, constraints ->
    val inset = topInset().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -inset))
    layout(placeable.width, placeable.height + inset) { placeable.place(0, inset) }
}

/** Lays the sheet's body out at [total] less whatever [topInset] the handle above it is taking. */
private fun Modifier.sheetBodyHeight(total: Dp, topInset: () -> Dp) = layout { measurable, constraints ->
    val height = (total - topInset()).roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@OptIn(
    kotlinx.coroutines.FlowPreview::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun MapScreen(
    onOpenTimetable: (DepartureBoardRoute) -> Unit,
    onNavigateToConnections: () -> Unit,
    onOpenLocationSearch: () -> Unit,
    /** Shows one line of a drawn itinerary on a map of its own, the way every other screen does. */
    onOpenTrip: (PendingMapTrip) -> Unit,
    /**
     * Leaves a map opened to draw an itinerary — closing its pane is done with it, and this map was
     * pushed for nothing else, so it goes back to the list it came from.
     */
    onCloseJourney: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    styleViewModel: MapStyleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val areaDownload by viewModel.areaDownload.collectAsStateWithLifecycle()
    val stopsOffline by viewModel.stopsOffline.collectAsStateWithLifecycle()

    // Only while this map is the resumed one: "show on map" pushes another map on top of the
    // stack, and the trip it hands over belongs to that one, not to the maps left underneath.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.consumePendingSignals() }
    }

    // The outcome is a passing acknowledgement, not state to sit in the panel forever.
    LaunchedEffect(areaDownload) {
        if (areaDownload is AreaDownloadState.Done ||
            areaDownload == AreaDownloadState.Failed ||
            areaDownload == AreaDownloadState.TooLarge
        ) {
            delay(AREA_DOWNLOAD_MESSAGE_MILLIS)
            viewModel.consumeAreaDownload()
        }
    }
    // Only a composable can answer what the device's dark-mode setting is, and MapTheme.SYSTEM
    // needs it; everything downstream of the answer is derived in the view model.
    val systemInDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(systemInDarkTheme) { styleViewModel.setSystemInDarkTheme(systemInDarkTheme) }
    val styleJson by styleViewModel.styleJson.collectAsStateWithLifecycle()
    // Delegated for the same reason as the layer state below: the map content lambda is composed
    // once, so a captured value would freeze the labels at whichever colourway loaded first.
    val darkMap by styleViewModel.darkMap.collectAsStateWithLifecycle()
    val selectedStop by viewModel.selectedStop.collectAsStateWithLifecycle()
    val stopRoutes by viewModel.stopRoutes.collectAsStateWithLifecycle()
    // Delegated for the same reason as the rest: the routes layer reads this from inside the
    // map content lambda, where only a State read stays live.
    val focusedRoute by viewModel.focusedRoute.collectAsStateWithLifecycle()
    val selectedVehicle by viewModel.selectedVehicle.collectAsStateWithLifecycle()
    val vehicleDetails by viewModel.vehicleDetails.collectAsStateWithLifecycle()
    // A trip opened from a timetable. It outlives its marker: a run that is not on the road has
    // no vehicle to draw, and only its route and stops are shown.
    val pinnedTrip by viewModel.pinnedTrip.collectAsStateWithLifecycle()
    // An itinerary this map was pushed to draw. While it is set the map is that journey and
    // nothing else — see the ViewModel, which stands the viewport's own stops and vehicles down.
    val pinnedJourney by viewModel.pinnedJourney.collectAsStateWithLifecycle()
    val showItineraryStopNames by viewModel.showItineraryStopNames.collectAsStateWithLifecycle()
    // Null until the run's own timetable has said whether it is on the road at all.
    val pinnedTripLive by viewModel.pinnedTripLive.collectAsStateWithLifecycle()
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

    /** Measured height of the map itself, which the vehicle panel is sized against. */
    var mapHeight by remember { mutableStateOf(0.dp) }

    /**
     * Ceiling on the vehicle sheet, so the map keeps the bigger share of the screen and a long
     * run's timetable scrolls inside the sheet instead of growing it. Also what the camera aims
     * around: a ceiling rather than the sheet's measured height, since the panel grows as the
     * trip's details land and following a live height would shunt the map mid-follow.
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

    // The two info panels are one *standard* (non-modal) M3 bottom sheet: no scrim, so the map
    // behind it stays pannable and tappable, and the scaffold only spans the area the nav host
    // gave this screen, so the bottom bar keeps its place under it. The partial→expanded snapping,
    // the fling physics and the nested scroll into the timetable are all the framework's.
    //
    // A pinned trip heads the panel itself while its run is off the road: there is no marker to
    // read the line from, and the trip is still what the panel is about.
    val panelLine = selectedVehicle?.panelLine() ?: pinnedTrip?.panelLine()
    // The sheet gives way to the stop panel rather than stacking with it: tapping a stop of the
    // followed run keeps the vehicle selected, so the two can be open at once.
    val journeyOverlay = pinnedJourney?.let { rememberJourneyOverlay(it) }
    /** The journey stop the map is pointing at, tapped here or picked in the pane. */
    var journeyStopId by remember(pinnedJourney) { mutableStateOf(pinnedJourney?.selectedStopId) }
    val sheetContentKind = when {
        // First: a map showing an itinerary shows nothing else, so nothing can take the sheet.
        pinnedJourney != null -> SheetContentKind.JOURNEY
        selectedStop != null -> SheetContentKind.STOP
        panelLine != null -> SheetContentKind.VEHICLE
        else -> null
    }
    // Both keep showing their last subject while the sheet animates out after deselection.
    var displayedStop by remember { mutableStateOf<TransitLocation?>(null) }
    selectedStop?.let { displayedStop = it }
    var displayedLine by remember { mutableStateOf<VehiclePanelLine?>(null) }
    panelLine?.let { displayedLine = it }
    // What the sheet is *drawing*, which lags the selection by one animation: swapping the body
    // at the moment of deselection would show the next panel's content sliding out.
    var displayedKind by remember { mutableStateOf<SheetContentKind?>(null) }
    sheetContentKind?.let { displayedKind = it }

    /**
     * How tall the stop panel measures. Its open height is the *content's*, so the sheet wraps what
     * it holds instead of resting at a guessed constant: a stop with four rows of line chips and
     * one with none are different heights, and neither should show a gap or a cut-off row. It is a
     * measurement fed back as a layout input, which is safe because that panel wraps its own
     * content and so cannot grow to fill whatever it is given.
     */
    var stopPanelHeight by remember { mutableStateOf(0.dp) }

    /**
     * How tall the vehicle panel *would* measure with nothing capping its timetable. The panel
     * itself fills the sheet, so this is the one number that says where the sheet should rest:
     * at the whole panel while it fits, at [vehiclePanelHeight] once the run is longer than that.
     */
    var vehicleNaturalHeight by remember { mutableStateOf(0.dp) }

    /** How tall the itinerary pane measures. Like the stop panel, it wraps what it has to say. */
    var journeyPanelHeight by remember { mutableStateOf(0.dp) }

    /**
     * How tall each panel's identity row measures — the strip that says *what* the sheet is about,
     * and the only thing left on screen once the sheet is taken down to its header rest. Measured
     * rather than assumed for the same reason the open heights are: a stop name that wraps and one
     * that does not are different heights, and the sheet has to stop just under the row either way.
     */
    var stopHeaderHeight by remember { mutableStateOf(0.dp) }
    var vehicleHeaderHeight by remember { mutableStateOf(0.dp) }
    var journeyHeaderHeight by remember { mutableStateOf(0.dp) }

    // Only a run whose timetable outgrows the collapsed panel has anywhere to expand *to*; letting
    // a three-stop run be dragged to full screen would just uncover blank surface.
    val sheetExpandable = displayedKind == SheetContentKind.VEHICLE &&
        vehicleNaturalHeight > vehiclePanelHeight
    val panelHeight = when (displayedKind) {
        SheetContentKind.STOP -> stopPanelHeight
        SheetContentKind.VEHICLE -> minOf(vehicleNaturalHeight, vehiclePanelHeight)
        SheetContentKind.JOURNEY -> journeyPanelHeight
        null -> 0.dp
    }
    /** Where the sheet rests once it is taken down to its header: the row plus room to breathe. */
    val headerHeight = when (displayedKind) {
        SheetContentKind.STOP -> stopHeaderHeight
        SheetContentKind.VEHICLE -> vehicleHeaderHeight
        SheetContentKind.JOURNEY -> journeyHeaderHeight
        null -> 0.dp
    }.let { if (it > 0.dp) it + HEADER_BOTTOM_PADDING else 0.dp }
    // A panel barely taller than its own header has nothing to uncover, so it gets no header rest
    // at all: the sheet would stop a few pixels below where it already stood, and a swipe down
    // would read as having failed to dismiss it.
    val sheetCollapsible = headerHeight > 0.dp &&
        headerHeight + MIN_HEADER_COLLAPSE_GAIN <= panelHeight

    /**
     * Whether the sheet is resting at its header instead of at the whole panel — the third level
     * the framework does not have. A standard sheet anchors at its peek height and at its content's
     * and there is no third value to add, so the *peek itself* moves: this says which of the two
     * heights it currently is. Everything else — the drag, the thresholds, the fling, the settle —
     * is still the framework's; only where the sheet comes to rest is ours.
     */
    var sheetCollapsed by remember { mutableStateOf(false) }

    /**
     * Whether the peek height jumps to its new value instead of animating there.
     *
     * A sheet *resting* on the peek is carried by it, so animating the peek is what animates the
     * sheet — the normal case, and the whole point. A sheet that is already moving is not: every
     * change to the anchors restarts the animation carrying it, so a peek sliding under a dragged
     * or a closing sheet for a third of a second leaves it crawling after an anchor that keeps
     * moving away. There the peek is put where it is going in one step and the sheet's own
     * animation covers the distance — nothing is lost, because a moving sheet's anchor is not
     * what the eye is following.
     */
    var snapSheetHeight by remember { mutableStateOf(false) }

    /** Where the sheet rests: the panel — or just its header — plus the handle above it. */
    val restingHeight = when {
        sheetContentKind == null -> 0.dp
        sheetCollapsed && sheetCollapsible -> DRAG_HANDLE_HEIGHT + headerHeight
        else -> DRAG_HANDLE_HEIGHT + panelHeight
    }
    val animatedSheetHeight by animateDpAsState(targetValue = restingHeight, label = "sheetHeight")
    val sheetHeight = if (snapSheetHeight) restingHeight else animatedSheetHeight
    val restingHeightNow by rememberUpdatedState(restingHeight)
    // The jump was for the one change that asked for it; whatever the panel does afterwards — a
    // timetable landing, a chip row wrapping — animates again.
    LaunchedEffect(sheetCollapsed, snapSheetHeight) {
        if (!snapSheetHeight) return@LaunchedEffect
        snapshotFlow { animatedSheetHeight == restingHeightNow }.first { it }
        snapSheetHeight = false
    }

    /** Takes the sheet between its header rest and its open one — see [snapSheetHeight]. */
    fun setSheetCollapsed(collapsed: Boolean, snap: Boolean) {
        snapSheetHeight = snap
        sheetCollapsed = collapsed
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Read through State: the sheet state keeps the first lambda it is given for the sheet's life.
    val expandableNow by rememberUpdatedState(sheetExpandable)
    val collapsedNow by rememberUpdatedState(sheetCollapsed)
    val collapsibleNow by rememberUpdatedState(sheetCollapsible)
    val hasPanelNow by rememberUpdatedState(sheetContentKind != null)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
        // Which of the framework's three values the sheet is allowed to settle in from where it
        // currently stands. Deliberately free of side effects: M3 calls this while *describing* the
        // sheet to accessibility as well as when settling it, so anything moved in here would move
        // again every time the sheet is read out.
        confirmValueChange = { target ->
            when (target) {
                // A swipe down off the open panel has the header rest to stop at first, so it is
                // not a dismissal yet; a swipe down off the header is.
                SheetValue.Hidden -> collapsedNow || !collapsibleNow || !hasPanelNow
                // Full screen is for a run whose timetable outgrows the open panel, and only from
                // that panel: from the header the drag has the panel to stop at first.
                SheetValue.Expanded -> !collapsedNow && expandableNow
                SheetValue.PartiallyExpanded -> true
            }
        },
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    // The other half of the third level: which of the two heights the peek is, moved by where the
    // drag is *heading* rather than by where it ended. Reading the settled value instead would move
    // the anchor only after the finger had lifted, which is the sheet jumping somewhere else once
    // the gesture is over. So crossing M3's threshold towards Hidden takes the rest down to the
    // header and crossing it towards Expanded brings it back up to the panel, while the vetoes
    // above stop that same drag carrying on into the state it was nominally aimed at.
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.targetValue }.collect { target ->
            when {
                target == SheetValue.Hidden && !sheetCollapsed && collapsibleNow && hasPanelNow ->
                    setSheetCollapsed(collapsed = true, snap = true)
                target == SheetValue.Expanded && sheetCollapsed ->
                    setSheetCollapsed(collapsed = false, snap = true)
            }
        }
    }
    /** What the open sheet is about, so that a fresh subject opens at its whole panel again. */
    val sheetSubject = when (sheetContentKind) {
        SheetContentKind.STOP -> selectedStop?.favoriteKey
        SheetContentKind.VEHICLE -> panelLine?.tripId ?: panelLine?.label
        SheetContentKind.JOURNEY -> "journey"
        null -> null
    }
    LaunchedEffect(sheetContentKind, sheetSubject) {
        if (sheetContentKind == null) {
            sheetState.hide()
        } else {
            // Whatever the panel before it was collapsed to was said about that one.
            setSheetCollapsed(collapsed = false, snap = false)
            if (sheetState.currentValue != SheetValue.PartiallyExpanded) sheetState.partialExpand()
        }
    }
    // Swiping the sheet away is a deselection like any other — the halo and route overlay go with
    // it. Only the settled state counts, or a drag past the threshold would deselect mid-gesture.
    // An itinerary's pane is the screen rather than a layer on it, so dismissing that leaves.
    val closeSheet by rememberUpdatedState {
        if (pinnedJourney != null) onCloseJourney() else viewModel.clearSelection()
    }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }
            .collect { if (it == SheetValue.Hidden) closeSheet() }
    }

    /**
     * How far the sheet is between resting at its peek height (0) and standing fully open (1),
     * taken from its live offset rather than from the state it will settle in — so what reads it
     * follows the finger through the drag instead of snapping when the gesture ends.
     */
    val containerPx = with(density) { mapHeight.toPx() }
    val peekPx = with(density) { sheetHeight.toPx() }
    val sheetExpansion = remember(containerPx, peekPx) {
        derivedStateOf {
            if (containerPx <= peekPx) return@derivedStateOf 0f
            // Throws until the sheet's first measurement has placed it; the read still registers,
            // so this recomputes as soon as there is an offset to read.
            val offset = runCatching { sheetState.requireOffset() }.getOrNull()
                ?: return@derivedStateOf 0f
            ((containerPx - offset - peekPx) / (containerPx - peekPx)).coerceIn(0f, 1f)
        }
    }

    /**
     * How much of the status bar the sheet has slid under — nothing while its edge is below the bar,
     * the whole bar once it stands at the top of the screen. An expandable sheet is laid out at the
     * screen's full height, so at its open anchor the handle would sit *under* the status bar, where
     * the system bar takes the taps meant for it; this is the room the handle takes to keep clear of
     * it, growing as the sheet passes underneath the way the platform's own sheets do. The body
     * gives up exactly what the handle takes ([sheetTopInset] against [sheetBodyHeight]), so the
     * sheet's own height — and with it the anchor this is measured from — never moves.
     *
     * Read in the layout phase by both, and so a lambda: a composition-phase read would recompose
     * the whole sheet every frame of a drag, and the two would disagree by however far the sheet had
     * moved in between.
     */
    val sheetTopInset: () -> Dp = {
        val offset = runCatching { sheetState.requireOffset() }.getOrNull()
        if (offset == null) {
            0.dp
        } else {
            (statusBarHeight - with(density) { offset.toDp() }).coerceIn(0.dp, statusBarHeight)
        }
    }

    // The status bar sits on the map — except with the sheet standing open over the whole screen,
    // where it sits on the sheet's own surface and the app's theme is what its icons have to read
    // against. Settled, not live: flipping the icons mid-drag would strobe them.
    MapStatusBarIcons(
        darkBackground = if (sheetState.currentValue == SheetValue.Expanded) {
            MaterialTheme.colorScheme.surface.luminance() < 0.5f
        } else {
            darkMap
        },
    )

    // Back peels the map's own layers off one at a time — filter panel, then whichever info
    // panel is open — instead of leaving the app from under a full-screen selection.
    //
    // A run handed over by "show on map" ([pinnedTrip]) is the exception, and deliberately so:
    // that map was *pushed* to show this run (see AppNavHost), so it is not a layer the user
    // opened on top of the map, it is what the screen is for. Peeling it off would strand them on
    // a blank map and cost a back press on the way out of every lap of stop → timetable → trip →
    // map, so back leaves the screen instead and lands on the timetable it came from. Everything
    // the user did open here still peels first — including a *different* vehicle tapped on this
    // map, which clears the handed-over run and so is no longer one.
    BackHandler(
        enabled = filtersExpanded || focusedRoute != null ||
            (selectedVehicle != null && pinnedTrip == null) ||
            selectedStop != null || routeDraftVisible,
    ) {
        when {
            filtersExpanded -> filtersExpanded = false
            // A focused line is a layer on top of the stop panel, so it peels off first.
            focusedRoute != null -> viewModel.clearRouteFocus()
            // Stop first, then the vehicle: with both open the stop panel is on top.
            selectedStop != null -> viewModel.clearStopSelection()
            selectedVehicle != null && pinnedTrip == null -> viewModel.clearVehicleSelection()
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
        ?.takeIf { pinnedTripLive == false }
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

    // An itinerary is framed whole, in the part of the map its pane leaves visible: the route is
    // what this map was pushed for, and its shape is the first thing to read. Keyed on the journey
    // and on the pane having been measured, so it happens once per opening and never fights the
    // user's own panning afterwards.
    LaunchedEffect(pinnedJourney, journeyPanelHeight > 0.dp) {
        val bounds = journeyOverlay?.lines?.flatMap { it.points }?.boundingBox()
            ?: return@LaunchedEffect
        if (journeyPanelHeight <= 0.dp) return@LaunchedEffect
        pendingLocateMe = false
        cameraState.animateTo(
            boundingBox = bounds,
            // Clear of the pane below and the search bar above, so the whole route stays visible.
            padding = PaddingValues(
                start = 32.dp,
                top = 96.dp,
                end = 32.dp,
                bottom = DRAG_HANDLE_HEIGHT + journeyPanelHeight + 32.dp,
            ),
            duration = FOLLOW_CENTER_DURATION,
        )
    }

    // A waypoint picked in the pane (or tapped on the route) is lifted into view exactly like a
    // selected stop — same centring, same duration — rather than being left wherever it happened
    // to sit. Skipped for the stop the map was opened on: framing the whole route comes first.
    LaunchedEffect(journeyStopId) {
        val point = journeyStopId
            ?.takeIf { it != pinnedJourney?.selectedStopId }
            ?.let { id -> journeyOverlay?.points?.firstOrNull { it.id == id } }
            ?: return@LaunchedEffect
        val panelHeight = snapshotFlow { journeyPanelHeight }.first { it > 0.dp }
        val projection = cameraState.awaitProjection()
        pendingLocateMe = false
        cameraState.animateTo(
            cameraState.position.copy(
                target = projection.targetCentering(point.point, DRAG_HANDLE_HEIGHT + panelHeight),
            ),
            duration = FOLLOW_CENTER_DURATION,
        )
    }

    // The map click callback below is captured once by MaplibreMap and never refreshed, so it
    // must read current data through State objects rather than capture the values directly.
    // Same reason as the rest of this block: the map content lambda captures values once, so the
    // itinerary's geometry has to be read through State to reach the layers at all.
    val currentJourneyOverlay by rememberUpdatedState(journeyOverlay)
    val currentJourneyStopId by rememberUpdatedState(journeyStopId)
    val currentShowStopNames by rememberUpdatedState(showItineraryStopNames)
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

    // The cached half of the same story: where the camera is *now*, reported without waiting for
    // it to settle, so stops already on disk are drawn while the finger is still moving rather
    // than popping in 400 ms after it lifts. This never triggers a request — the settled
    // viewport above remains the only thing that does.
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.position }
            .sample(LIVE_VIEWPORT_SAMPLE_MILLIS)
            .collect {
                val bbox = cameraState.projection?.queryVisibleBoundingBox() ?: return@collect
                viewModel.onViewportChanged(
                    bbox.south, bbox.west, bbox.north, bbox.east,
                    zoom = cameraState.position.zoom,
                )
            }
    }

    // Panning or pinching the map takes the sheet down to its header. What is under the finger is
    // the map, and a panel standing over half of it is in the way of the very thing being looked
    // at; the header keeps saying what is selected, and one tap on the handle brings the panel
    // back. Only the user's own gestures count — the camera moves the app makes (flying to a
    // picked place, lifting a selected stop, following a run) are the sheet's own doing and must
    // not pull it down a moment after it opened.
    LaunchedEffect(cameraState, sheetState) {
        snapshotFlow { cameraState.position }
            .drop(1)
            .filter { cameraState.moveReason == CameraMoveReason.GESTURE }
            .collect {
                if (!hasPanelNow || !collapsibleNow || sheetCollapsed) return@collect
                // From full screen the sheet has the whole travel to cover, so the peek is put
                // where it is going in one step; resting on the peek it is carried by it.
                val settled = sheetState.currentValue == SheetValue.PartiallyExpanded
                setSheetCollapsed(collapsed = true, snap = !settled)
                if (!settled) sheetState.partialExpand()
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

    // A selected stop is lifted into the map its panel leaves visible the same way, minus the
    // following: a stop does not move, so this is the one animation and the camera is the user's
    // again afterwards. Keyed on the stop, so it happens once per selection.
    LaunchedEffect(selectedStop?.favoriteKey) {
        val stop = selectedStop ?: return@LaunchedEffect
        // A vehicle being followed is already driving the camera, and it wins: the stop was tapped
        // on the run it is showing, not instead of it.
        if (followingVehicle) return@LaunchedEffect
        // A stop picked in the search field arrives with a fly-to of its own (cameraTarget above),
        // which zooms as well as pans; the lift is the last word on where the camera lands, so it
        // waits for that to be done rather than animating against it — and reads the projection
        // afterwards, at the zoom the shift will actually be made at.
        snapshotFlow { cameraTarget }.first { it == null }
        // Nothing to aim at until the panel has been measured — centring on the whole map first
        // would land the stop short and snap it up a frame later.
        val panelHeight = snapshotFlow { stopPanelHeight }.first { it > 0.dp }
        val projection = cameraState.awaitProjection()
        pendingLocateMe = false
        cameraState.animateTo(
            cameraState.position.copy(
                target = projection.targetCentering(
                    GeoPoint(lat = stop.lat, lon = stop.lon),
                    DRAG_HANDLE_HEIGHT + panelHeight,
                ),
            ),
            duration = FOLLOW_CENTER_DURATION,
        )
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


    // Captured before the override below, which is in force for everything under the scaffold: the
    // sheet's content reaches back for it (see `sheetContent`), so only the sheet itself is settled.
    val appMotionScheme = MaterialTheme.motionScheme

    // The sheet's drag settle, its open animation and the fling that carries over from the timetable
    // all animate on the theme's default spatial spec, and the expressive scheme's spring overshoots
    // every one of them: the panel swings past its anchor and back, and M3 stretches the sheet's own
    // surface to cover the gap that opens under it. [SettledMotionScheme] is that one spec, and
    // nothing else, made duration-based so no fling speed can carry it past where it is going.
    MaterialExpressiveTheme(motionScheme = SettledMotionScheme) {
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetHeight,
        // The scaffold is a frame around the map, not a surface of its own; anything it painted
        // would sit between the map and the sheet.
        containerColor = Color.Transparent,
        // M3's own handle, in M3's own slot: the scaffold wraps whatever it is given here in the
        // drag semantics, the tooltip and the tap that expands or collapses the sheet, so a
        // hand-drawn one would have to reimplement all of it to end up where this starts.
        sheetDragHandle = {
            // Above it, the room it needs to stay clear of the status bar the sheet slides under.
            Box(modifier = Modifier.sheetTopInset(sheetTopInset)) {
                // M3's own tap expands a sheet that has an expanded state to reach and hides one
                // that has not. Neither is right at every level here, so the tap is taken over
                // wherever the framework's would do the wrong thing or nothing at all: it lifts a
                // sheet resting at its header back to the whole panel, and closes a panel that has
                // no full screen to be expanded to — what the header's ✕ used to be, without a
                // second control saying the same thing. The inner click takes the tap first.
                val liftFromHeader = sheetCollapsed && sheetCollapsible
                val handleTap: (() -> Unit)? = when {
                    liftFromHeader -> { { setSheetCollapsed(collapsed = false, snap = false) } }
                    sheetExpandable -> null
                    else -> {
                        {
                            // The panel the handle belongs to, so closing a stop opened over a
                            // followed run leaves the run itself selected.
                            when (displayedKind) {
                                SheetContentKind.STOP -> viewModel.clearStopSelection()
                                SheetContentKind.VEHICLE -> viewModel.clearVehicleSelection()
                                SheetContentKind.JOURNEY -> onCloseJourney()
                                null -> {}
                            }
                        }
                    }
                }
                if (handleTap == null) {
                    BottomSheetDefaults.DragHandle()
                } else {
                    Box(
                        modifier = Modifier.clickable(
                            onClickLabel = stringResource(
                                if (liftFromHeader) R.string.action_expand else R.string.action_close,
                            ),
                            role = Role.Button,
                            onClick = handleTap,
                        ),
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                }
            }
        },
        sheetContent = {
            // Only the *sheet* settles without a spring; what it holds is ordinary app UI and keeps
            // the theme's own motion, so nothing drawn in here loses the expressive feel.
            MaterialExpressiveTheme(motionScheme = appMotionScheme) {
            when (displayedKind) {
                SheetContentKind.STOP -> displayedStop?.let { stop ->
                    StopInfoPanel(
                        stop = stop,
                        routesState = stopRoutes,
                        focusedRoute = focusedRoute,
                        onToggleRouteFocus = { viewModel.toggleRouteFocus(it) },
                        isFavorite = favorites.containsLocation(stop),
                        onToggleFavorite = { viewModel.toggleFavoriteStop(stop) },
                        onHeightChange = { stopPanelHeight = it },
                        onHeaderHeightChange = { stopHeaderHeight = it },
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
                SheetContentKind.VEHICLE -> displayedLine?.let { line ->
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
                        expandable = sheetExpandable,
                        sheetExpansion = { sheetExpansion.value },
                        collapsed = { sheetState.currentValue != SheetValue.Expanded },
                        onNaturalHeightChange = { vehicleNaturalHeight = it },
                        onHeaderHeightChange = { vehicleHeaderHeight = it },
                        // An expandable panel is laid out at the sheet's full travel and its
                        // timetable scrolls inside it, so dragging the sheet up uncovers a list
                        // that is already there — following the finger, resizing nothing.
                        modifier = if (sheetExpandable) {
                            Modifier.sheetBodyHeight(
                                total = mapHeight - DRAG_HANDLE_HEIGHT,
                                topInset = sheetTopInset,
                            )
                        } else {
                            Modifier
                        },
                    )
                }
                // No lingering copy of this one while the sheet animates out, unlike the two
                // above: closing it leaves the screen, so there is no map left to animate over.
                SheetContentKind.JOURNEY -> if (pinnedJourney != null && journeyOverlay != null) {
                    JourneyMapPane(
                        pinned = requireNotNull(pinnedJourney),
                        colors = journeyOverlay.colors,
                        stops = journeyOverlay.stops,
                        selectedStopId = journeyStopId,
                        onWaypointClick = { id -> journeyStopId = id },
                        onOpenTrip = onOpenTrip,
                        onHeightChange = { height -> journeyPanelHeight = height },
                        onHeaderHeightChange = { height -> journeyHeaderHeight = height },
                    )
                }
                null -> {}
            }
            }
        },
    ) { _ ->
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
                    // A map drawing an itinerary has only its own stops to hit: everything else
                    // is stood down, and a tap that opened a stop panel over the pane would be
                    // showing something this map was not opened for.
                    if (currentJourneyOverlay != null) {
                        val hit = nearestFeatureId("journey-points")
                        return@MaplibreMap if (hit == null && currentJourneyStopId == null) {
                            ClickResult.Pass
                        } else {
                            journeyStopId = hit
                            ClickResult.Consume
                        }
                    }
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
                // Not on an itinerary's map: a picked point there would open a panel over the one
                // thing that map exists to show.
                if (currentJourneyOverlay != null) {
                    ClickResult.Pass
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.selectPoint(position.latitude, position.longitude)
                    ClickResult.Consume
                }
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
            val stopRouteShapes = (stopRoutes as? StopRoutesUiState.Shown)?.routes ?: emptyList()
            // Focusing one line hides the rest of the network through the stop. The followed
            // vehicle's own shape isn't part of that network, so it stays either way.
            val shownStopRoutes = focusedRoute
                ?.let { key -> stopRouteShapes.filter { it.focusKey == key } }
                ?: stopRouteShapes
            val routeShapes = shownStopRoutes + listOfNotNull(vehicleRouteShape)
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
                    // Keyed to the basemap, not the app theme: the two are separate settings,
                    // and a label only has to stay legible against what is drawn under it.
                    textColor = const(mapLabelColor(darkMap == true)),
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
                    textColor = const(mapLabelColor(darkMap == true)),
                )
                // The itinerary, when this map was pushed to draw one. Its layers stay declared
                // either way and simply carry no features otherwise — a layer set that came and
                // went would have to be added to the style at exactly the right moment.
                val journeyLines = currentJourneyOverlay?.lines.orEmpty()
                fun journeyLineFeatures(dashed: Boolean) = FeatureCollection(
                    journeyLines.filter { it.dashed == dashed }.map { line ->
                        Feature<LineString, JsonObject?>(
                            geometry = LineString(
                                line.points.map { Position(latitude = it.lat, longitude = it.lon) },
                            ),
                            properties = JsonObject(
                                mapOf("color" to JsonPrimitive(line.color.toHexString())),
                            ),
                        )
                    },
                )
                // dasharray isn't data-driven in MapLibre, so walking legs need their own layer.
                val journeyDashedSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        remember(journeyLines) { journeyLineFeatures(dashed = true) },
                    ),
                )
                val journeySolidSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        remember(journeyLines) { journeyLineFeatures(dashed = false) },
                    ),
                )
                val journeyPoints = currentJourneyOverlay?.points.orEmpty()
                val journeyPointFeatures = remember(journeyPoints, currentJourneyStopId, currentShowStopNames) {
                    FeatureCollection(
                        journeyPoints.map { point ->
                            Feature<Point, JsonObject?>(
                                id = JsonPrimitive(point.id),
                                geometry = Point(
                                    Position(latitude = point.point.lat, longitude = point.point.lon),
                                ),
                                properties = JsonObject(
                                    mapOf(
                                        "kind" to JsonPrimitive(if (point.terminus) "terminus" else "via"),
                                        "name" to JsonPrimitive(
                                            point.name.takeIf { currentShowStopNames }.orEmpty(),
                                        ),
                                        "color" to JsonPrimitive(
                                            point.color?.toHexString() ?: markerColorHex(point.mode),
                                        ),
                                        "icon" to JsonPrimitive(markerIconKey(point.mode)),
                                        "selected" to JsonPrimitive(point.id == currentJourneyStopId),
                                    ),
                                ),
                            )
                        },
                    )
                }
                val journeyPointsSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(journeyPointFeatures),
                )
                val journeyLineWidth = interpolate(
                    linear(),
                    zoom(),
                    11 to const(3.dp),
                    16 to const(6.dp),
                )
                LineLayer(
                    id = "journey-lines-dashed",
                    source = journeyDashedSource,
                    color = feature["color"].convertToColor(),
                    width = journeyLineWidth,
                    opacity = const(0.8f),
                    // Near-zero dash + round cap renders Google-Maps-like walking dots.
                    dasharray = const(listOf(0.1, 1.8)),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = "journey-lines",
                    source = journeySolidSource,
                    color = feature["color"].convertToColor(),
                    width = journeyLineWidth,
                    opacity = const(0.8f),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                CircleLayer(
                    id = "journey-points",
                    source = journeyPointsSource,
                    // Sized for a route overview rather than a zoom-dependent map: these are the
                    // few places the journey acts at, and they are what it is read for.
                    radius = switch(
                        input = feature["kind"].asString(),
                        case("terminus", const(11.dp)),
                        fallback = const(9.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    strokeColor = const(MARKER_STROKE_COLOR),
                    // The selected stop grows a heavier ring rather than changing colour, so it
                    // reads as the same marker being pointed at.
                    strokeWidth = switch(
                        input = feature["selected"].asString(),
                        case("true", const(3.dp)),
                        fallback = const(1.dp),
                    ),
                )
                SymbolLayer(
                    id = "journey-point-icons",
                    source = journeyPointsSource,
                    iconImage = markerIconImage,
                    iconAllowOverlap = const(true),
                )
                SymbolLayer(
                    id = "journey-point-labels",
                    source = journeyPointsSource,
                    textField = format(span(feature["name"].asString())),
                    textFont = const(listOf("Roboto Regular")),
                    textSize = const(0.75f.em),
                    textOffset = offset(0f.em, 1.4f.em),
                    textAnchor = const(SymbolAnchor.Top),
                    textColor = const(mapLabelColor(darkMap == true)),
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

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth(),
        ) {
            // Nothing that starts a search of its own over an itinerary: this map is that
            // journey, and a picked place would fly the camera off it and open a panel the
            // journey's own pane is holding.
            if (pinnedJourney == null) {
                MapSearchBar(
                    onClick = onOpenLocationSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
            AnimatedVisibility(visible = routeDraftVisible && pinnedJourney == null) {
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
            // Filter button and compass share one row: same top edge, one at each end. The
            // compass rides the column, so it stays clear of the route draft bar without
            // anyone having to measure it.
            Box(modifier = Modifier.fillMaxWidth()) {
                // No filters over an itinerary: the map is drawing one journey, and none of what
                // they filter — the viewport's stops, its vehicles — is on it to be filtered.
                if (pinnedJourney == null) {
                    MapFiltersMenu(
                        filters = filters,
                        expanded = filtersExpanded,
                        onExpandedChange = { filtersExpanded = it },
                        onUpdate = viewModel::updateFilters,
                        onReset = viewModel::resetFilters,
                        areaDownload = areaDownload,
                        onDownloadArea = viewModel::downloadVisibleArea,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 72.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 16.dp),
                ) {
                    // Positioning lives on the wrapper, not on this modifier:
                    // DisappearingCompassButton hands its own `modifier` to both the
                    // AnimatedVisibility and the button inside it, so any padding passed here
                    // is applied twice.
                    DisappearingCompassButton(
                        cameraState = cameraState,
                        // Matches the filter button's chrome: a 48dp circle over
                        // surface + 3dp tonal elevation, holding a 24dp glyph.
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        contentPadding = PaddingValues(12.dp),
                    )
                }
            }
            // Only while the panel is closed: with it open the download row right below says
            // the same thing, and the chip would be repeating itself.
            AnimatedVisibility(visible = stopsOffline && !filtersExpanded && pinnedJourney == null) {
                OfflineChip(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 72.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Ornaments sit on top of the sheet, so opening it lifts them instead of the
                // sheet covering them. The *animated* height, not the one the sheet is anchored
                // at: where that jumps to keep a moving sheet off a moving anchor, nothing is
                // dragging these, so they glide the whole way.
                .padding(bottom = animatedSheetHeight),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Basemap credits and the Transitous sources link in one control. The end
                // padding clears the locate-me FAB below (16dp inset + its 56dp) and then some,
                // so the expanded credits wrap beside the button instead of under it.
                MapAttributionButton(
                    cameraState = cameraState,
                    styleState = styleState,
                    darkMap = darkMap == true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp, end = 84.dp),
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
        }
    }
    }
    }
}

/** Which of the panels the map's bottom sheet is showing. */
private enum class SheetContentKind { STOP, VEHICLE, JOURNEY }

/**
 * M3-search-bar-styled field overlaying the top of the map. Not a real input: tapping it
 * opens the full-screen [pl.dakil.transport.ui.search.LocationPickerScreen], whose pick
 * flows back to the map as a selection + camera move.
 */
/**
 * Contrasts the status bar's icons against the basemap for as long as the map is open: dark
 * icons over the light map, light icons over the dark one.
 *
 * This screen is the one place the map itself is what the status bar sits against — every other
 * destination puts a top app bar there — so the clock and signal icons have to follow whatever is
 * behind them, the basemap or a sheet standing open over it, rather than the app theme that
 * `enableEdgeToEdge` set at startup. The previous appearance is put back on leaving, so nothing else
 * is affected. Null means the colourway is not resolved yet: nothing to contrast against, so nothing
 * is touched.
 */
@Composable
private fun MapStatusBarIcons(darkBackground: Boolean?) {
    val view = LocalView.current
    if (view.isInEditMode || darkBackground == null) return
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(view, window, darkBackground) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = !darkBackground
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/**
 * Says the stops on screen came from the phone rather than the network.
 *
 * Informational, not an error: the whole point of the cache is that losing signal is not a
 * failure, and the map is still perfectly usable. It only appears once a refresh has actually
 * been attempted and failed — never merely because nothing needed refreshing.
 */
@Composable
private fun OfflineChip(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.map_offline_cached),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
    /** Null for a run that is off the road: neither "live" nor "scheduled" is true of it. */
    val realTime: Boolean?,
) {

    /** See [VehicleMarker.favoriteLine] — [destination] must be the trip's headsign. */
    fun favoriteLine(destination: String?): FavoriteLine? = tripId?.let {
        FavoriteLine(label = label, headsign = destination, mode = mode, routeColor = routeColor, tripId = it)
    }
}

private fun VehicleMarker.panelLine() = VehiclePanelLine(
    tripId = tripId,
    label = label,
    mode = mode,
    routeColor = routeColor,
    realTime = realTime,
)

private fun PendingMapTrip.panelLine() = VehiclePanelLine(
    tripId = tripId,
    label = label,
    mode = mode,
    routeColor = routeColor,
    realTime = null,
)

/**
 * Body of the map's bottom sheet for a tapped vehicle, mirroring [StopInfoPanel]. Shows the
 * trip's attributes and its timetable as they load ([detailsState]).
 *
 * No surface of its own: it is sheet content, so the container and the handle belong to the sheet.
 * A run whose timetable outgrows the sheet's peek height makes the sheet [expandable]: the panel is
 * then laid out at the sheet's whole travel and the timetable scrolls inside it, so dragging the
 * sheet up simply uncovers more of a list that is already there. What that costs is the run's
 * position — with only the top strip of the list on screen the highlighted row has to be *scrolled*
 * up into it, and towards the end of a run there are not enough stops left below it to scroll that
 * far, hence the blank tail below.
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
    /** Whether the sheet can be dragged open — false when the whole panel already shows. */
    expandable: Boolean,
    /** How far the sheet is open: 0 at its peek height, 1 fully open. Read per frame of a drag. */
    sheetExpansion: () -> Float,
    /** Whether the sheet has settled back at its peek height. Deferred: this is a settled read. */
    collapsed: () -> Boolean,
    /** Height this measures uncapped — what the collapsed sheet peeks at, before its ceiling. */
    onNaturalHeightChange: (Dp) -> Unit,
    /** Height of the line's own row — where the sheet rests once it is taken down to its header. */
    onHeaderHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val timetable = (detailsState as? VehicleDetailsUiState.Shown)?.details?.timetable.orEmpty()
    val now = rememberTickingNow()
    // The stop being approached rather than the last one called at: where the run is *going* is
    // what this panel is read for, and it stays an answer once the run has left its last stop.
    val highlightedIndex = timetable.nextStopIndex(now)
    val listState = rememberLazyListState()
    /** Everything above the timetable, measured: the peek height is this plus what list fits. */
    var headerHeight by remember { mutableStateOf(0.dp) }
    // Every row is one height, so one of them answers for the whole list — which is what lets the
    // sheet peek at the panel's natural height without laying the timetable out a second time.
    // Taken from the first measurement and then left alone, because a stop name long enough to wrap
    // makes rows differ by a line: tracking it would move the tail below with the scroll, and the
    // tail is what the scroll is aimed by.
    // The viewport is latched with it, and for a second reason: over the last stretch of the drag
    // the list gives up the room the handle takes to clear the status bar, and a tail that followed
    // that would re-aim the list in the middle of the gesture.
    var geometry by remember(line.tripId) { mutableStateOf(ListGeometry()) }
    LaunchedEffect(line.tripId, listState) {
        geometry = snapshotFlow {
            ListGeometry(
                rowHeightPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0,
                viewportPx = listState.layoutInfo.viewportSize.height,
            )
        }.first { it.rowHeightPx > 0 && it.viewportPx > 0 }
    }
    val rowHeightPx = geometry.rowHeightPx
    val listHeightPx = rowHeightPx * timetable.size

    val naturalHeight = headerHeight + with(density) {
        if (timetable.isEmpty()) PANEL_BOTTOM_PADDING else TIMETABLE_TOP_PADDING + listHeightPx.toDp()
    }
    LaunchedEffect(naturalHeight) { onNaturalHeightChange(naturalHeight) }

    /**
     * Blank tail the *collapsed* sheet needs under the last stop, so that scrolling the highlighted
     * row to the very top of the list is always possible: a list can only be scrolled by as much as
     * it overflows its viewport, and this viewport is the sheet's whole travel — mostly off screen
     * while it is collapsed, which leaves the rows *below* the highlighted one paying for the
     * scroll. Towards the end of a run there are not enough of them, and without the tail the row
     * stops short of the top and, near the terminus, short of the strip that is on screen at all.
     *
     * Aiming at the top rather than at the strip's lower edge is what makes this independent of how
     * tall the strip happens to be — and it is the more useful place besides, since the stops after
     * the one being approached are the ones still to come.
     *
     * None of it applies to an open sheet, where the whole list is on screen: the tail is scaled
     * away as the sheet is dragged (see [VehicleTimetable]), so it never holds a screenful of
     * nothing under a run that is nearly over. A run long enough to fill the open sheet has no tail
     * at either end of the drag.
     */
    val collapsedTail = with(density) {
        (highlightedIndex.coerceAtLeast(0) * rowHeightPx + geometry.viewportPx - listHeightPx)
            .coerceAtLeast(0)
            .toDp()
    }

    Column(
        modifier = modifier
            // The timetable runs to the panel's edge: its rows carry their own padding, and
            // a gap below the last visible one reads as the list having stopped scrolling.
            .padding(
                start = 16.dp,
                end = 8.dp,
                bottom = if (timetable.isEmpty()) PANEL_BOTTOM_PADDING else 0.dp,
            ),
    ) {
        Column(
            modifier = Modifier.onSizeChanged { size ->
                headerHeight = with(density) { size.height.toDp() }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { size ->
                    onHeaderHeightChange(with(density) { size.height.toDp() })
                },
            ) {
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
        }

        if (timetable.isNotEmpty()) {
            VehicleTimetable(
                stops = timetable,
                railColor = parseRouteColor(line.routeColor, markerColor(line.mode)),
                highlightedIndex = highlightedIndex,
                listState = listState,
                collapsedTail = collapsedTail,
                // Nothing to aim at before the first layout has said how tall a row is: the very
                // first scroll should land on the highlight, not animate to it from a guess.
                measured = rowHeightPx > 0,
                sheetExpansion = sheetExpansion,
                collapsed = collapsed,
                // Filling only once the sheet can be dragged open: a run that fits keeps the
                // panel its own height, where filling would leave blank under the last stop.
                modifier = Modifier.weight(1f, fill = expandable),
            )
        }
    }
}

/**
 * What [VehicleInfoPanel] measures its timetable's geometry as, latched together at the first
 * layout: both numbers describe the same pass, and one of them arriving a frame before the other
 * would size the sheet against a list it does not match.
 */
private data class ListGeometry(val rowHeightPx: Int = 0, val viewportPx: Int = 0)

/**
 * The timetable inside [VehicleInfoPanel], split out because it is the one thing here that reads
 * the sheet's [sheetExpansion] — a value that changes every frame of a drag, and recomposing this
 * much is cheaper than recomposing the panel around it.
 *
 * The list opens on the stop the vehicle is heading for, and follows the run as it advances until
 * the user scrolls the list themselves, from when on it is theirs — until the sheet comes back down,
 * which re-aims the strip and hands it back.
 */
@Composable
private fun VehicleTimetable(
    stops: List<TripStop>,
    railColor: Color,
    highlightedIndex: Int,
    listState: LazyListState,
    /** Blank tail the collapsed sheet needs under the last stop — see [VehicleInfoPanel]. */
    collapsedTail: Dp,
    /** Whether the list has been laid out once, so the tail and the row height are real numbers. */
    measured: Boolean,
    /** How far the sheet is open: 0 at its peek height, 1 fully open. */
    sheetExpansion: () -> Float,
    /** Whether the sheet has settled back at its peek height. */
    collapsed: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val tripKey = stops.first().place.favoriteKey
    // Whether the highlight may still steer the list. Dragging the timetable is the user taking it
    // over — scrolling it out from under them afterwards is the app fighting them.
    var userScrolled by remember(tripKey) { mutableStateOf(false) }
    LaunchedEffect(listState, tripKey) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) userScrolled = true
        }
    }
    // Coming back down re-aims the strip at the stop the run is at, and hands the list back to the
    // highlight: the few rows a collapsed sheet shows are only worth the space if they are the part
    // of the run it is at, and whatever the user scrolled to was them reading the *open* sheet.
    // Only settled collapses count — the drag's own target flips as it crosses the threshold, and
    // scrolling the list then would move it under the finger.
    var recentres by remember(tripKey) { mutableIntStateOf(0) }
    LaunchedEffect(tripKey) {
        snapshotFlow { collapsed() }
            .drop(1)
            .filter { it }
            .collect {
                userScrolled = false
                recentres++
            }
    }
    // First landing on the highlight is a jump — there is nothing on screen yet to animate from.
    // Every one after it is the run having advanced a stop, or the sheet having come back down.
    var landed by remember(tripKey) { mutableStateOf(false) }
    LaunchedEffect(tripKey, highlightedIndex, collapsedTail, measured, userScrolled, recentres) {
        if (!measured || userScrolled) return@LaunchedEffect
        val index = highlightedIndex.coerceAtLeast(0)
        if (landed) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        landed = true
    }
    LazyColumn(
        state = listState,
        // Scaled off as the sheet opens rather than dropped when it settles: the tail is what holds
        // the list at a scroll offset the open sheet has no room for, so removing it in one step
        // yanks the whole timetable down the moment the drag ends. Taken out along the way instead,
        // the list unwinds under the finger as the sheet rises.
        contentPadding = PaddingValues(bottom = collapsedTail * (1f - sheetExpansion())),
        modifier = modifier.padding(top = TIMETABLE_TOP_PADDING, end = 8.dp),
    ) {
        tripTimetable(stops = stops, railColor = railColor, highlightedIndex = highlightedIndex)
    }
}

/**
 * Body of the map's bottom sheet for a tapped stop. The sheet is a *standard* one, so the map
 * above it stays fully interactive while it is open — see [MapScreen] for the container.
 *
 * Unlike the vehicle's, this panel is as tall as what it has to say and the sheet rests at exactly
 * that height ([onHeightChange]) — there is no long list here to drag open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopInfoPanel(
    stop: TransitLocation,
    routesState: StopRoutesUiState,
    /** [RouteShape.focusKey] of the line drawn alone, or null while the whole network is shown. */
    focusedRoute: String?,
    onToggleRouteFocus: (RouteShape) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenTimetable: () -> Unit,
    onBeginHere: () -> Unit,
    onFinishHere: () -> Unit,
    /** What this measures — the height the sheet opens to, and what the camera aims around. */
    onHeightChange: (Dp) -> Unit,
    /** Height of the stop's own row — where the sheet rests once it is taken down to its header. */
    onHeaderHeightChange: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .onSizeChanged { size -> onHeightChange(with(density) { size.height.toDp() }) }
            .padding(start = 16.dp, end = 8.dp, bottom = PANEL_BOTTOM_PADDING),
    ) {
            // A plain place or a point picked on the map is not served by any mode, so it gets
            // a map pin rather than a (meaningless, always-bus) vehicle icon, and none of the
            // stop-specific content: no lines, no timetable.
            val isPoint = stop.stopId == null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { size ->
                    onHeaderHeightChange(with(density) { size.height.toDp() })
                },
            ) {
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
                        val isFocused = focusedRoute == route.focusKey
                        // The map keeps the feed's colours in every mode: markers and overlays have
                        // no draw order to hand a palette out along.
                        ModeChip(
                            mode = route.mode,
                            label = route.lineLabel,
                            containerColor = parseRouteColor(route.routeColor, route.mode.color),
                            // Fading the rest is the panel's half of "only this line is drawn":
                            // the chips still say what is being hidden.
                            modifier = Modifier.alpha(
                                if (focusedRoute == null || isFocused) 1f else DIMMED_CHIP_ALPHA,
                            ),
                            onClick = { onToggleRouteFocus(route) },
                            clickLabel = stringResource(
                                if (isFocused) R.string.map_line_show_all else R.string.map_line_focus,
                            ),
                            // The chip toggles the overlay in place; it doesn't lead anywhere.
                            showChevron = false,
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

@Composable
private fun PanelActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}
