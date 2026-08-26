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
 * An end of the whole journey, drawn as a plain dot. Where a traveller starts and finishes is
 * usually not a stop — it is an address, a long-pressed point, their own position — so nothing
 * else on the map marks it and the route just faded out into the basemap at both ends. Dropped
 * when it *is* a stop, since the pin already drawn there says it better.
 */
data class RouteEndpoint(
    val point: GeoPoint,
    /** The colour of the leg it hangs off, so the dot reads as that line's end. */
    val color: Color,
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
    val endpoints: List<RouteEndpoint>,
    val stops: List<ItineraryStop>,
    val colors: JourneyColors,
    /**
     * Trip id → the colour its leg is drawn in, for the one vehicle marker each running leg puts on
     * the map. A marker takes the operator's colour everywhere else, but here it stands *on* a line
     * this palette already coloured, and a bus in a different colour from the route under it reads
     * as a different bus.
     */
    val vehicleColors: Map<String, Color>,
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
    val endpoints = remember(lines, points) { journeyEndpoints(lines, points) }
    val vehicleColors = remember(journey, colors) {
        journey.legs.withIndex().mapNotNull { (index, leg) ->
            val tripId = leg.tripId?.takeIf { leg.isTransit } ?: return@mapNotNull null
            // Resolved exactly as the leg's own line is above — `lines` cannot be read back for it,
            // since a leg with no geometry draws none and the two lists stop lining up.
            val color = colors.legColors.getOrNull(index)
                ?: parseRouteColor(leg.routeColor, leg.mode.color)
            tripId to color
        }.toMap()
    }
    return remember(lines, points, endpoints, stops, colors, vehicleColors) {
        JourneyOverlay(lines, points, endpoints, stops, colors, vehicleColors)
    }
}

/**
 * The two ends of the drawn route, minus any that a stop pin is already standing on. [lines] is
 * what the map actually draws, so its ends are the ends the user sees — reading them off the
 * journey's legs instead would place a dot on geometry that was never rendered.
 */
private fun journeyEndpoints(
    lines: List<RouteLine>,
    points: List<RoutePoint>,
): List<RouteEndpoint> {
    val first = lines.firstOrNull() ?: return emptyList()
    val last = lines.last()
    return listOf(
        RouteEndpoint(first.points.first(), first.color),
        RouteEndpoint(last.points.last(), last.color),
    ).filter { endpoint ->
        points.none { it.point.distanceMetersTo(endpoint.point) < ENDPOINT_STOP_RADIUS_METERS }
    }
}

/**
 * How close to a stop pin an end has to be before it counts as that stop. Generous: a walk leg
 * routed to the pavement outside a station ends metres off the platform the pin sits on, and two
 * markers on top of each other read as a smudge rather than as extra information.
 */
private const val ENDPOINT_STOP_RADIUS_METERS = 25.0
