package pl.dakil.transport.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
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
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.ui.components.parseRouteColor

/** One colored polyline drawn on a [RouteMap], e.g. one journey leg. */
data class RouteMapLine(
    val points: List<GeoPoint>,
    val color: Color,
    /** Dotted rendering for non-vehicle stretches (walk/bike legs). */
    val dashed: Boolean = false,
)

/**
 * This journey's legs as [RouteMapLine]s, colored exactly like the itinerary screen's leg
 * rail: the feed's GTFS route color (mode color fallback) for transit legs, a muted dotted
 * line for walk/bike/car legs.
 */
@Composable
fun rememberJourneyRouteLines(journey: Journey): List<RouteMapLine> {
    val walkColor = MaterialTheme.colorScheme.outline
    return remember(journey, walkColor) {
        journey.legs.mapNotNull { leg ->
            leg.path.takeIf { it.size >= 2 }?.let { path ->
                RouteMapLine(
                    points = path,
                    color = if (leg.isTransit) parseRouteColor(leg.routeColor, leg.mode.color) else walkColor,
                    dashed = !leg.isTransit,
                )
            }
        }
    }
}

private fun Color.toHexString(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/**
 * A non-interactive-content map that draws [lines] over the app's patched base style and
 * auto-fits the camera to them. Reusable by any screen that needs to show a route (itinerary,
 * favourites previews, …); the main Map screen keeps its own richer implementation.
 *
 * [bottomOverlay] is docked to the bottom edge above the attribution row, so panels passed
 * here lift the attribution instead of covering it (same pattern as the Map screen).
 */
@Composable
fun RouteMap(
    lines: List<RouteMapLine>,
    modifier: Modifier = Modifier,
    styleViewModel: MapStyleViewModel = hiltViewModel(),
    bottomOverlay: @Composable ColumnScope.() -> Unit = {},
) {
    val styleJson by styleViewModel.styleJson.collectAsStateWithLifecycle()

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

    // The map content lambda below is composed once and never swapped, so it must read the
    // lines through a State object rather than capture the parameter value directly.
    val currentLines by rememberUpdatedState(lines)

    Box(modifier = modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = styleJson?.let { BaseStyle.Json(it) }
                ?: return@Box, // patched style still loading from assets; it arrives within moments
            cameraState = cameraState,
            styleState = styleState,
            options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
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
            // Dots at the endpoints of the whole route plus each junction between lines
            // (boarding/alighting points).
            val pointFeatures = remember(currentLines) {
                val stops = buildList {
                    currentLines.firstOrNull()?.let { add(it.points.first() to "terminus") }
                    currentLines.dropLast(1).forEach { add(it.points.last() to "via") }
                    currentLines.lastOrNull()?.let { add(it.points.last() to "terminus") }
                }
                FeatureCollection(
                    stops.map { (point, kind) ->
                        Feature<Point, JsonObject?>(
                            geometry = Point(Position(latitude = point.lat, longitude = point.lon)),
                            properties = JsonObject(mapOf("kind" to JsonPrimitive(kind))),
                        )
                    },
                )
            }
            val pointsSource = rememberGeoJsonSource(data = GeoJsonData.Features(pointFeatures))

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
                    radius = switch(
                        input = feature["kind"].asString(),
                        case("terminus", const(5.dp)),
                        fallback = const(3.5.dp),
                    ),
                    color = const(Color.White),
                    strokeColor = const(Color(0xFF424242)),
                    strokeWidth = const(1.5.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
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
                        .padding(end = 8.dp, bottom = 8.dp),
                )
            }
            bottomOverlay()
        }
    }
}

/**
 * Transitous API usage guidelines require a visible link to the data sources wherever its
 * data is shown on a map.
 */
@Composable
fun TransitousAttributionLabel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        text = "Data: transitous.org",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
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
}
