package pl.dakil.transport.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
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
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.parseRouteColor
import androidx.compose.ui.res.stringResource
import pl.dakil.transport.R

/** One colored polyline drawn on a [RouteMap], e.g. one journey leg. */
data class RouteMapLine(
    val points: List<GeoPoint>,
    val color: Color,
    /** Dotted rendering for non-vehicle stretches (walk/bike legs). */
    val dashed: Boolean = false,
)

/**
 * A named stop drawn on a [RouteMap]. Passing these instead of letting the map derive bare
 * dots from the line ends is what lets it label them and hand taps back by [id].
 */
data class RouteMapPoint(
    /** Stable within one route; what [RouteMap]'s click callback and selection are keyed by. */
    val id: String,
    val point: GeoPoint,
    val name: String,
    /** Drives the marker's glyph, and its fill wherever [color] leaves the choice open. */
    val mode: TransportMode,
    /**
     * Fill for the pin, already resolved by whoever is drawing the route — normally the colour
     * its leg's badge carries, so a stop and the line it belongs to agree. Null falls back to the
     * mode's own marker colour.
     */
    val color: Color? = null,
    /** Ends of the whole journey are drawn larger than the transfer points between legs. */
    val terminus: Boolean = false,
)

/**
 * This journey's legs as [RouteMapLine]s, colored exactly like the itinerary screen's leg rail,
 * with a muted dotted line for walk/bike/car legs.
 *
 * [legColors] is one entry per leg of [journey], carrying the colour that leg's badge was drawn
 * in (null for the legs that have no badge). Passing it is what keeps the two views in step:
 * re-deriving the colour here from the feed's hex quietly disagrees with the rail whenever
 * [pl.dakil.transport.domain.model.LineColorMode] substituted something, so the same journey
 * came out in two different colour schemes. Left empty the feed's own colours are used, which is
 * right for any caller drawing a route outside that setting's reach.
 */
@Composable
fun rememberJourneyRouteLines(
    journey: Journey,
    legColors: List<Color?> = emptyList(),
): List<RouteMapLine> {
    val walkColor = MaterialTheme.colorScheme.outline
    return remember(journey, walkColor, legColors) {
        journey.legs.mapIndexedNotNull { index, leg ->
            leg.path.takeIf { it.size >= 2 }?.let { path ->
                RouteMapLine(
                    points = path,
                    color = when {
                        !leg.isTransit -> walkColor
                        else -> legColors.getOrNull(index)
                            ?: parseRouteColor(leg.routeColor, leg.mode.color)
                    },
                    dashed = !leg.isTransit,
                )
            }
        }
    }
}

private fun Color.toHexString(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/** Tap slop around a stop dot — the dots are far smaller than a fingertip. */
private val POINT_TAP_TARGET_RADIUS = 24.dp

/** Zoom a selected stop is brought to, unless the camera is already closer. */
private const val SELECTED_POINT_ZOOM = 15.0

/**
 * A non-interactive-content map that draws [lines] over the app's patched base style and
 * auto-fits the camera to them. Reusable by any screen that needs to show a route (itinerary,
 * favourites previews, …); the main Map screen keeps its own richer implementation.
 *
 * [bottomOverlay] is docked to the bottom edge above the attribution row, so panels passed
 * here lift the attribution instead of covering it (same pattern as the Map screen).
 *
 * [points] are the named stops to draw; when empty the map falls back to bare dots derived
 * from the line ends. [selectedPointId] enlarges one of them and centres the camera on it,
 * and [onPointClick] reports taps (null id = tapped empty map).
 */
@Composable
fun RouteMap(
    lines: List<RouteMapLine>,
    modifier: Modifier = Modifier,
    points: List<RouteMapPoint> = emptyList(),
    selectedPointId: String? = null,
    showPointLabels: Boolean = true,
    onPointClick: (String?) -> Unit = {},
    styleViewModel: MapStyleViewModel = hiltViewModel(),
    bottomOverlay: @Composable ColumnScope.() -> Unit = {},
) {
    // Only a composable can read the device's dark-mode setting, which MapTheme.SYSTEM needs.
    val systemInDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(systemInDarkTheme) { styleViewModel.setSystemInDarkTheme(systemInDarkTheme) }
    val styleJson by styleViewModel.styleJson.collectAsStateWithLifecycle()
    // Delegated, not captured: the map content lambda is composed once, so reading this at the
    // use site inside it is what keeps the labels following the colourway.
    val darkMap by styleViewModel.darkMap.collectAsStateWithLifecycle()

    val allPoints = remember(lines) { lines.flatMap { it.points } }
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = allPoints.firstOrNull()?.let { Position(latitude = it.lat, longitude = it.lon) }
                ?: Position(latitude = 50.0, longitude = 10.0),
            zoom = 10.0,
        ),
    )
    val styleState = rememberStyleState()

    LaunchedEffect(allPoints) {
        if (allPoints.isEmpty()) return@LaunchedEffect
        cameraState.animateTo(
            boundingBox = BoundingBox(
                west = allPoints.minOf { it.lon },
                south = allPoints.minOf { it.lat },
                east = allPoints.maxOf { it.lon },
                north = allPoints.maxOf { it.lat },
            ),
            padding = PaddingValues(48.dp),
        )
    }

    // Selecting a stop — here or by tapping its row in the list view — brings it into view.
    LaunchedEffect(selectedPointId) {
        val selected = points.firstOrNull { it.id == selectedPointId } ?: return@LaunchedEffect
        cameraState.animateTo(
            CameraPosition(
                target = Position(latitude = selected.point.lat, longitude = selected.point.lon),
                zoom = maxOf(cameraState.position.zoom, SELECTED_POINT_ZOOM),
            ),
        )
    }

    // The map content lambda below is composed once and never swapped, so it must read the
    // lines through a State object rather than capture the parameter value directly.
    val currentLines by rememberUpdatedState(lines)
    val currentPoints by rememberUpdatedState(points)
    val currentSelectedPointId by rememberUpdatedState(selectedPointId)
    val currentShowLabels by rememberUpdatedState(showPointLabels)
    // Same reason the callbacks object is remembered without keying on this: read, never captured.
    val currentOnPointClick by rememberUpdatedState(onPointClick)

    Box(modifier = modifier) {
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
                        left = clickOffset.x - POINT_TAP_TARGET_RADIUS,
                        top = clickOffset.y - POINT_TAP_TARGET_RADIUS,
                        right = clickOffset.x + POINT_TAP_TARGET_RADIUS,
                        bottom = clickOffset.y + POINT_TAP_TARGET_RADIUS,
                    )
                    val hitId = projection
                        .queryRenderedFeatures(rect = hitRect, layerIds = setOf("route-map-points"))
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
                    if (hitId == null && currentSelectedPointId == null) {
                        ClickResult.Pass
                    } else {
                        currentOnPointClick(hitId)
                        ClickResult.Consume
                    }
                }
            },
        ) {
            fun lineFeatures(lines: List<RouteMapLine>) = FeatureCollection(
                lines.map { line ->
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
            // dasharray isn't data-driven in MapLibre, so dotted lines need their own layer.
            val solidSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    remember(currentLines) { lineFeatures(currentLines.filter { !it.dashed }) },
                ),
            )
            val dashedSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    remember(currentLines) { lineFeatures(currentLines.filter { it.dashed }) },
                ),
            )
            // Named stops when the caller supplied them; otherwise bare dots derived from the
            // endpoints of the whole route plus each junction between lines.
            val pointFeatures = remember(currentLines, currentPoints, currentSelectedPointId) {
                val stops = currentPoints.ifEmpty {
                    buildList {
                        currentLines.firstOrNull()?.let {
                            add(RouteMapPoint("start", it.points.first(), "", TransportMode.OTHER, terminus = true))
                        }
                        currentLines.dropLast(1).forEachIndexed { index, line ->
                            add(RouteMapPoint("via-$index", line.points.last(), "", TransportMode.OTHER))
                        }
                        currentLines.lastOrNull()?.let {
                            add(RouteMapPoint("end", it.points.last(), "", TransportMode.OTHER, terminus = true))
                        }
                    }
                }
                FeatureCollection(
                    stops.map { stop ->
                        Feature<Point, JsonObject?>(
                            id = JsonPrimitive(stop.id),
                            geometry = Point(
                                Position(latitude = stop.point.lat, longitude = stop.point.lon),
                            ),
                            properties = JsonObject(
                                mapOf(
                                    "kind" to JsonPrimitive(if (stop.terminus) "terminus" else "via"),
                                    "name" to JsonPrimitive(
                                        stop.name.takeIf { currentShowLabels }.orEmpty(),
                                    ),
                                    "color" to JsonPrimitive(
                                        stop.color?.toHexString() ?: markerColorHex(stop.mode),
                                    ),
                                    "icon" to JsonPrimitive(markerIconKey(stop.mode)),
                                    "selected" to JsonPrimitive(stop.id == currentSelectedPointId),
                                ),
                            ),
                        )
                    },
                )
            }
            val pointsSource = rememberGeoJsonSource(data = GeoJsonData.Features(pointFeatures))
            val markerIconImage = rememberMarkerIconImage()

            val lineWidth = interpolate(
                linear(),
                zoom(),
                11 to const(3.dp),
                16 to const(6.dp),
            )
            // Same anchor as the Map screen's layers: above streets/shields, below place labels.
            Anchor.Above("road_shield") {
                LineLayer(
                    id = "route-map-lines-dashed",
                    source = dashedSource,
                    color = feature["color"].convertToColor(),
                    width = lineWidth,
                    opacity = const(0.8f),
                    // Near-zero dash + round cap renders Google-Maps-like walking dots.
                    dasharray = const(listOf(0.1, 1.8)),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = "route-map-lines",
                    source = solidSource,
                    color = feature["color"].convertToColor(),
                    width = lineWidth,
                    opacity = const(0.8f),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                CircleLayer(
                    id = "route-map-points",
                    source = pointsSource,
                    // Same pin the main map draws — mode/route colored with a thin gray ring —
                    // just sized for a route overview rather than a zoom-dependent map.
                    radius = switch(
                        input = feature["kind"].asString(),
                        case("terminus", const(11.dp)),
                        fallback = const(9.dp),
                    ),
                    color = feature["color"].convertToColor(),
                    strokeColor = const(MARKER_STROKE_COLOR),
                    // The selected stop grows a heavier ring rather than changing color, so it
                    // reads as the same marker being pointed at.
                    strokeWidth = switch(
                        input = feature["selected"].asString(),
                        case("true", const(3.dp)),
                        fallback = const(1.dp),
                    ),
                )
                SymbolLayer(
                    id = "route-map-point-icons",
                    source = pointsSource,
                    iconImage = markerIconImage,
                    iconAllowOverlap = const(true),
                )
                SymbolLayer(
                    id = "route-map-point-labels",
                    source = pointsSource,
                    textField = format(span(feature["name"].asString())),
                    // Must be a fontstack the style's glyph server actually serves (the library
                    // default 404s there); Roboto also matches the basemap's typography.
                    textFont = const(listOf("Roboto Regular")),
                    textSize = const(0.75f.em),
                    textOffset = offset(0f.em, 1.4f.em),
                    textAnchor = const(SymbolAnchor.Top),
                    // Keyed to the basemap, not the app theme — see mapLabelColor.
                    textColor = const(mapLabelColor(darkMap == true)),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MapAttributionButton(
                    cameraState = cameraState,
                    styleState = styleState,
                    darkMap = darkMap == true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
            bottomOverlay()
        }
    }
}
