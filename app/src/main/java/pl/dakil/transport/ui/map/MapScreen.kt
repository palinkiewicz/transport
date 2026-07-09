package pl.dakil.transport.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraPosition
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
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.navigation.DeparturesRoute

/**
 * "#RRGGBB" hex string for the mode's marker color, as consumed by [convertToColor].
 * Deliberately brighter than [TransportMode.color]: the muted list-screen palette reads
 * washed-out against the light, low-chroma Google-Maps-like basemap.
 */
private fun markerColorHex(mode: TransportMode): String = when (mode) {
    TransportMode.TRAM -> "#F44336"
    TransportMode.SUBWAY -> "#4285F4"
    TransportMode.FERRY -> "#00BCD4"
    TransportMode.AIRPLANE -> "#7E57C2"
    TransportMode.BUS -> "#FB8C00"
    TransportMode.COACH -> "#FFA000"
    TransportMode.RAIL, TransportMode.LONG_DISTANCE, TransportMode.REGIONAL_RAIL -> "#4CAF50"
    TransportMode.HIGHSPEED_RAIL -> "#AB47BC"
    TransportMode.NIGHT_RAIL -> "#5C6BC0"
    TransportMode.SUBURBAN -> "#26A69A"
    TransportMode.FUNICULAR, TransportMode.AERIAL_LIFT -> "#8D6E63"
    else -> "#78909C"
}

private fun TransitLocation.markerColorHex(): String = markerColorHex(primaryMode ?: TransportMode.OTHER)

/** [markerColorHex] as a Compose [Color], for UI elements echoing the map marker's look. */
private fun markerColor(mode: TransportMode): Color =
    Color(markerColorHex(mode).removePrefix("#").toLong(16) or 0xFF000000)

private val GTFS_COLOR_REGEX = Regex("^[0-9a-fA-F]{6}$")

/** Line color for drawing this route on the map, preferring the feed's GTFS route color. */
private fun RouteShape.lineColorHex(): String =
    routeColor?.takeIf { GTFS_COLOR_REGEX.matches(it) }?.let { "#$it" } ?: markerColorHex(mode)

/**
 * Key of the marker glyph shown inside stop/vehicle circles; matched against the icon-image
 * switch cases. Several modes share one glyph (e.g. all rail variants), so this is coarser
 * than [TransportMode].
 */
private fun markerIconKey(mode: TransportMode): String = when (mode) {
    TransportMode.TRAM, TransportMode.FUNICULAR, TransportMode.AERIAL_LIFT -> "tram"
    TransportMode.SUBWAY -> "subway"
    TransportMode.FERRY -> "ferry"
    TransportMode.AIRPLANE -> "airplane"
    TransportMode.RAIL, TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE,
    TransportMode.NIGHT_RAIL, TransportMode.REGIONAL_RAIL, TransportMode.SUBURBAN,
    -> "rail"
    else -> "bus"
}

private fun TransitLocation.markerIconKey(): String = markerIconKey(primaryMode ?: TransportMode.OTHER)

/** Marker fill for a vehicle: the feed's GTFS route color when valid, else the mode color. */
private fun VehicleMarker.markerColorHex(): String =
    routeColor?.takeIf { GTFS_COLOR_REGEX.matches(it) }?.let { "#$it" } ?: markerColorHex(mode)

/** Stroke distinguishing live-tracked vehicles from schedule-only ones at a glance. */
private const val VEHICLE_STROKE_LIVE = "#2E7D32"
private const val VEHICLE_STROKE_TIMETABLE = "#9E9E9E"

private const val STOPS_FETCH_MIN_ZOOM = 13f
private const val STOP_ICONS_MIN_ZOOM = 15f
private val STOP_TAP_TARGET_RADIUS = 24.dp

private fun stopKey(location: TransitLocation): String = location.stopId ?: "${location.lat},${location.lon}"

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(
    onOpenTimetable: (DeparturesRoute) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val styleJson by viewModel.styleJson.collectAsStateWithLifecycle()
    val selectedStop by viewModel.selectedStop.collectAsStateWithLifecycle()
    val stopRoutes by viewModel.stopRoutes.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Starts true so the map centers on the user as soon as a fix is available after app entry.
    var pendingLocateMe by remember { mutableStateOf(true) }

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

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = Position(latitude = 50.0, longitude = 10.0), zoom = 5.0),
    )
    val styleState = rememberStyleState()

    // The map click callback below is captured once by MaplibreMap and never refreshed, so it
    // must read current data through State objects rather than capture the values directly.
    val stopsById by rememberUpdatedState(remember(stops) { stops.associateBy(::stopKey) })
    val currentSelectedStop by rememberUpdatedState(selectedStop)

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving }
            .debounce(400)
            .filter { moving -> !moving }
            .collect {
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

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = styleJson?.let { BaseStyle.Json(it) }
                ?: return@Box, // patched style still loading from assets; it arrives within moments
            cameraState = cameraState,
            styleState = styleState,
            options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
            onMapClick = { _, clickOffset ->
                val projection = cameraState.projection
                val stop = if (projection != null) {
                    val hitRect = DpRect(
                        left = clickOffset.x - STOP_TAP_TARGET_RADIUS,
                        top = clickOffset.y - STOP_TAP_TARGET_RADIUS,
                        right = clickOffset.x + STOP_TAP_TARGET_RADIUS,
                        bottom = clickOffset.y + STOP_TAP_TARGET_RADIUS,
                    )
                    val candidates = projection.queryRenderedFeatures(
                        rect = hitRect,
                        layerIds = setOf("transport-stops"),
                    )
                    val nearestId = candidates.minByOrNull { candidate ->
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
                    nearestId?.let { stopsById[it] }
                } else {
                    null
                }
                when {
                    stop != null -> {
                        viewModel.selectStop(stop)
                        ClickResult.Consume
                    }
                    // Tapping empty map dismisses the open stop panel.
                    currentSelectedStop != null -> {
                        viewModel.clearSelection()
                        ClickResult.Consume
                    }
                    else -> ClickResult.Pass
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
                            id = JsonPrimitive(stopKey(stop)),
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
            val routeShapes = (stopRoutes as? StopRoutesUiState.Shown)?.routes ?: emptyList()
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
            val selectedFeatures = remember(selectedStop) {
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
                    ),
                )
            }
            val selectedSource = rememberGeoJsonSource(data = GeoJsonData.Features(selectedFeatures))
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
            val glyphSize = DpSize(13.dp, 13.dp)
            val glyphTint = ColorFilter.tint(Color.White)
            val markerIconImage = switch(
                input = feature["icon"].asString(),
                case("tram", image(rememberVectorPainter(Icons.Default.Tram), glyphSize, colorFilter = glyphTint)),
                case("subway", image(rememberVectorPainter(Icons.Default.Subway), glyphSize, colorFilter = glyphTint)),
                case("ferry", image(rememberVectorPainter(Icons.Default.DirectionsBoat), glyphSize, colorFilter = glyphTint)),
                case("airplane", image(rememberVectorPainter(Icons.Default.Flight), glyphSize, colorFilter = glyphTint)),
                case("rail", image(rememberVectorPainter(Icons.Default.Train), glyphSize, colorFilter = glyphTint)),
                fallback = image(rememberVectorPainter(Icons.Default.DirectionsBus), glyphSize, colorFilter = glyphTint),
            )
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
                CircleLayer(
                    id = "transport-stops",
                    source = stopsSource,
                    minZoom = STOPS_FETCH_MIN_ZOOM,
                    // Small dots while zoomed out, growing into icon-bearing pins by z16.
                    radius = interpolate(
                        linear(),
                        zoom(),
                        13 to const(4.dp),
                        15 to const(6.dp),
                        16 to const(12.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    strokeColor = const(Color(0xFF9E9E9E)),
                    strokeWidth = const(1.dp),
                )
                SymbolLayer(
                    id = "transport-stop-icons",
                    source = stopsSource,
                    minZoom = STOP_ICONS_MIN_ZOOM,
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
                    minZoom = 14f,
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
                .padding(16.dp),
        )

        MapFiltersMenu(
            filters = filters,
            onUpdate = viewModel::updateFilters,
            onReset = viewModel::resetFilters,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, top = 16.dp, end = 72.dp),
        )

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

                // Transitous API usage guidelines require a visible link to the data sources.
                Text(
                    text = "Data: transitous.org",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 72.dp, bottom = 20.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(4.dp),
                        )
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://transitous.org/sources/")),
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate me")
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
                        onToggleRoutes = {
                            when (stopRoutes) {
                                is StopRoutesUiState.Shown, StopRoutesUiState.Loading -> viewModel.hideRoutes()
                                else -> viewModel.showRoutes()
                            }
                        },
                        onClose = { viewModel.clearSelection() },
                        onOpenTimetable = {
                            viewModel.clearSelection()
                            onOpenTimetable(
                                DeparturesRoute(
                                    stopName = stop.name,
                                    lat = stop.lat,
                                    lon = stop.lon,
                                    stopId = stop.stopId,
                                    timeIso = null,
                                ),
                            )
                        },
                        onBeginHere = {
                            viewModel.clearSelection()
                            viewModel.beginHere(stop)
                            onNavigateToSearch()
                        },
                        onFinishHere = {
                            viewModel.clearSelection()
                            viewModel.finishHere(stop)
                            onNavigateToSearch()
                        },
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
    onToggleRoutes: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                val mode = stop.primaryMode ?: TransportMode.OTHER
                // Same colored-circle look as the stop's marker on the map.
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .background(markerColor(mode), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = mode.label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    stop.primaryMode?.let { mode ->
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            when (routesState) {
                is StopRoutesUiState.Shown -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                ) {
                    routesState.routes.forEach { route ->
                        ModeChip(mode = route.mode, label = route.lineLabel, routeColorHex = route.routeColor)
                    }
                }
                is StopRoutesUiState.Error -> Text(
                    text = routesState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                )
                else -> {}
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            ) {
                FilledTonalButton(onClick = onToggleRoutes) {
                    if (routesState is StopRoutesUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (routesState is StopRoutesUiState.Shown) "Hide routes" else "Show routes")
                }
                PanelActionButton("Timetable", Icons.Default.Schedule, onOpenTimetable)
                PanelActionButton("Begin here", Icons.Default.NearMe, onBeginHere)
                PanelActionButton("Finish here", Icons.Default.Flag, onFinishHere)
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
