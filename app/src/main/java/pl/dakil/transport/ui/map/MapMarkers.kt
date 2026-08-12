package pl.dakil.transport.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.ImageValue
import pl.dakil.transport.domain.model.TransportMode

/**
 * How a transit stop is drawn wherever the app puts one on a map — the main map and the
 * itinerary's route map alike. Shared so the two never drift into looking like different
 * kinds of thing.
 */

/**
 * "#RRGGBB" hex string for the mode's marker color, as consumed by
 * [org.maplibre.compose.expressions.dsl.convertToColor]. Deliberately brighter than
 * [TransportMode.color]: the muted list-screen palette reads washed-out against the light,
 * low-chroma Google-Maps-like basemap.
 */
internal fun markerColorHex(mode: TransportMode): String = when (mode) {
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

/** [markerColorHex] as a Compose [Color], for UI elements echoing the map marker's look. */
internal fun markerColor(mode: TransportMode): Color =
    Color(markerColorHex(mode).removePrefix("#").toLong(16) or 0xFF000000)

internal val GTFS_COLOR_REGEX = Regex("^[0-9a-fA-F]{6}$")

/** The feed's GTFS route color when it is usable, else the mode's marker color. */
internal fun routeMarkerColorHex(routeColor: String?, mode: TransportMode): String =
    routeColor?.takeIf { GTFS_COLOR_REGEX.matches(it) }?.let { "#$it" } ?: markerColorHex(mode)

/**
 * Key of the marker glyph shown inside stop/vehicle circles; matched against
 * [rememberMarkerIconImage]'s cases. Several modes share one glyph (e.g. all rail variants),
 * so this is coarser than [TransportMode].
 */
internal fun markerIconKey(mode: TransportMode): String = when (mode) {
    TransportMode.TRAM, TransportMode.FUNICULAR, TransportMode.AERIAL_LIFT -> "tram"
    TransportMode.SUBWAY -> "subway"
    TransportMode.FERRY -> "ferry"
    TransportMode.AIRPLANE -> "airplane"
    TransportMode.RAIL, TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE,
    TransportMode.NIGHT_RAIL, TransportMode.REGIONAL_RAIL, TransportMode.SUBURBAN,
    -> "rail"
    else -> "bus"
}

/** Stroke ringing every stop pin, on both maps. Mid-gray reads on either basemap colourway. */
internal val MARKER_STROKE_COLOR = Color(0xFF9E9E9E)

/**
 * Colour of the stop and vehicle name labels drawn beside a marker.
 *
 * Fixed values rather than theme-derived ones: what a label has to stay legible against is the
 * basemap under it, not the app's surfaces — the two are separate choices (see
 * [pl.dakil.transport.domain.model.MapTheme]), so a dark app over a light map would otherwise
 * write pale grey onto near-white. The pair matches the basemap's own place labels.
 */
internal fun mapLabelColor(darkMap: Boolean): Color =
    if (darkMap) Color(0xFFE3E3E3) else Color(0xFF424242)

/**
 * The white mode glyph drawn inside a marker, switched on the feature's `icon` property.
 * Must be called inside a `MaplibreMap` content lambda — it composes vector painters.
 */
@Composable
internal fun rememberMarkerIconImage(): Expression<ImageValue> {
    val glyphSize = DpSize(13.dp, 13.dp)
    val glyphTint = ColorFilter.tint(Color.White)
    return switch(
        input = feature["icon"].asString(),
        case("tram", image(rememberVectorPainter(Icons.Default.Tram), glyphSize, colorFilter = glyphTint)),
        case("subway", image(rememberVectorPainter(Icons.Default.Subway), glyphSize, colorFilter = glyphTint)),
        case("ferry", image(rememberVectorPainter(Icons.Default.DirectionsBoat), glyphSize, colorFilter = glyphTint)),
        case("airplane", image(rememberVectorPainter(Icons.Default.Flight), glyphSize, colorFilter = glyphTint)),
        case("rail", image(rememberVectorPainter(Icons.Default.Train), glyphSize, colorFilter = glyphTint)),
        fallback = image(rememberVectorPainter(Icons.Default.DirectionsBus), glyphSize, colorFilter = glyphTint),
    )
}
