package pl.dakil.transport.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.parseRouteColor
import pl.dakil.transport.ui.itinerary.ItineraryStop
import pl.dakil.transport.ui.itinerary.JourneyColors
import pl.dakil.transport.ui.itinerary.journeyStops
import pl.dakil.transport.ui.itinerary.rememberJourneyColors

/** A resolved colour as the `#RRGGBB` string a style expression reads out of a feature. */
internal fun Color.toHexString(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/** One coloured polyline of a route drawn on the map, e.g. one leg of a journey. */
data class RouteLine(
    val points: List<GeoPoint>,
    val color: Color,
    /** Dotted rendering for non-vehicle stretches (walk/bike legs). */
    val dashed: Boolean = false,
)

/**
 * A named stop of a drawn route. Passing these instead of letting the map derive bare dots from the
 * line ends is what lets it label them and hand taps back by [id].
 */
data class RoutePoint(
    /** Stable within one route; what the map's click callback and selection are keyed by. */
    val id: String,
    val point: GeoPoint,
    val name: String,
    /** Drives the marker's glyph, and its fill wherever [color] leaves the choice open. */
    val mode: TransportMode,
    /**
     * Fill for the pin, already resolved by whoever is drawing the route — the colour its leg's
     * badge carries, so a stop and the line it belongs to agree.
     */
    val color: Color? = null,
    /** Ends of the whole journey are drawn larger than the transfer points between legs. */
    val terminus: Boolean = false,
)

/**
 * Everything the map needs to draw one itinerary, plus the resolution its pane reads its badges
 * from. One derivation for both: the route on the map, the chips in the sheet, the dots beside the
 * places to change at and the exported file all take their colours from [colors], which is what
 * stops the same journey coming out in two colour schemes.
 */
internal data class JourneyOverlay(
    val lines: List<RouteLine>,
    val points: List<RoutePoint>,
    val stops: List<ItineraryStop>,
    val colors: JourneyColors,
)

/** Whether [journey] carries geometry worth drawing at all. */
fun Journey.hasDrawableGeometry(): Boolean = legs.any { it.path.size >= 2 }

@Composable
internal fun rememberJourneyOverlay(pinned: PendingMapJourney): JourneyOverlay {
    val journey = pinned.journey
    val colors = rememberJourneyColors(journey)
    val walkColor = MaterialTheme.colorScheme.outline
    val stops = remember(journey, pinned.fromName, pinned.toName) {
        journeyStops(journey, pinned.fromName, pinned.toName)
    }
    val lines = remember(journey, colors, walkColor) {
        journey.legs.mapIndexedNotNull { index, leg ->
            leg.path.takeIf { it.size >= 2 }?.let { path ->
                RouteLine(
                    points = path,
                    color = when {
                        !leg.isTransit -> walkColor
                        else -> colors.legColors.getOrNull(index)
                            ?: parseRouteColor(leg.routeColor, leg.mode.color)
                    },
                    dashed = !leg.isTransit,
                )
            }
        }
    }
    val points = remember(stops, colors) {
        stops.map { stop ->
            RoutePoint(
                id = stop.id,
                point = stop.point,
                name = stop.name,
                mode = stop.mode,
                // A boarding pin belongs to its leg as much as the line does.
                color = colors.lineColors.at(stop.colorIndex, stop.mode.color),
                terminus = stop.terminus,
            )
        }
    }
    return remember(lines, points, stops, colors) { JourneyOverlay(lines, points, stops, colors) }
}
