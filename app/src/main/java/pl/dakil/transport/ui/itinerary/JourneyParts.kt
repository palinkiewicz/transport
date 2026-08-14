package pl.dakil.transport.ui.itinerary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import java.time.OffsetDateTime
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.LineColorRequest
import pl.dakil.transport.ui.components.LineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.rememberLineColors

/**
 * The pieces one journey is made of, shared by [ItineraryScreen]'s list and the map that draws the
 * same journey ([JourneyMapPane] and `rememberJourneyOverlay`). Both views resolve them from this
 * one place so a stop, a badge and a line on the map cannot disagree about the journey they show.
 */

/** A stop the itinerary names in both views, so a tap in one can point at it in the other. */
internal data class ItineraryStop(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val time: OffsetDateTime,
    val scheduledTime: OffsetDateTime,
    val mode: TransportMode,
    /** Which transit leg this stop belongs to, i.e. its slot in the journey's palette order. */
    val colorIndex: Int,
    val terminus: Boolean,
    /** True for a boarding stop; false where the journey gets off. */
    val boarding: Boolean,
)

/**
 * One line of the map pane's condensed itinerary: a place the traveller has to act at, with
 * the time they get there and the time they leave again. A same-stop transfer collapses into
 * a single line carrying both; a transfer that walks between two stops stays two lines,
 * because it really is two places.
 */
internal data class ItineraryWaypoint(
    /** Map point ids this line stands for — more than one when two stops were merged. */
    val ids: List<String>,
    val name: String,
    val arrival: OffsetDateTime?,
    val scheduledArrival: OffsetDateTime?,
    val departure: OffsetDateTime?,
    val scheduledDeparture: OffsetDateTime?,
    val mode: TransportMode,
    val colorIndex: Int,
)

/** Shared between the list rows and the map features so a tap in either resolves in the other. */
internal fun stopId(legIndex: Int, from: Boolean): String = "leg-$legIndex-${if (from) "from" else "to"}"

/**
 * [stops] — which alternate board, alight, board, alight — folded into the pane's lines.
 * Consecutive alight/board pairs at the same named stop become one line showing both times.
 */
internal fun waypoints(stops: List<ItineraryStop>): List<ItineraryWaypoint> {
    fun ItineraryStop.asWaypoint() = ItineraryWaypoint(
        ids = listOf(id),
        name = name,
        arrival = time.takeIf { !boarding },
        scheduledArrival = scheduledTime.takeIf { !boarding },
        departure = time.takeIf { boarding },
        scheduledDeparture = scheduledTime.takeIf { boarding },
        mode = mode,
        colorIndex = colorIndex,
    )

    val result = mutableListOf<ItineraryWaypoint>()
    var index = 0
    while (index < stops.size) {
        val stop = stops[index]
        val next = stops.getOrNull(index + 1)
        if (!stop.boarding && next != null && next.boarding && next.name == stop.name) {
            result += ItineraryWaypoint(
                ids = listOf(stop.id, next.id),
                name = stop.name,
                arrival = stop.time,
                scheduledArrival = stop.scheduledTime,
                departure = next.time,
                scheduledDeparture = next.scheduledTime,
                // The line you leave on is the one that matters at a transfer.
                mode = next.mode,
                colorIndex = next.colorIndex,
            )
            index += 2
        } else {
            result += stop.asWaypoint()
            index++
        }
    }
    return result
}

/**
 * The stops where the journey boards and alights, in order. Intermediate stops are left out on
 * purpose: they are pass-throughs, and labelling every one of them buries the map in text.
 * Coordinates come from the leg geometry — [JourneyLeg] carries no endpoint coordinates of its
 * own, and its path ends are exactly those points.
 */
internal fun journeyStops(journey: Journey, fromName: String, toName: String): List<ItineraryStop> {
    val transitIndices = journey.legs.indices.filter { journey.legs[it].isTransit }
    return transitIndices.flatMapIndexed { order, index ->
        val leg = journey.legs[index]
        if (leg.path.size < 2) return@flatMapIndexed emptyList()
        listOf(
            ItineraryStop(
                id = stopId(index, from = true),
                name = if (index == 0) fromName else leg.fromName,
                point = leg.path.first(),
                time = leg.startTime,
                scheduledTime = leg.scheduledStartTime,
                mode = leg.mode,
                colorIndex = order,
                terminus = order == 0,
                boarding = true,
            ),
            ItineraryStop(
                id = stopId(index, from = false),
                name = if (index == journey.legs.lastIndex) toName else leg.toName,
                point = leg.path.last(),
                time = leg.endTime,
                scheduledTime = leg.scheduledEndTime,
                mode = leg.mode,
                colorIndex = order,
                terminus = order == transitIndices.lastIndex,
                boarding = false,
            ),
        )
    }
}

/** How one journey's badges came out, resolved once — see [rememberJourneyColors]. */
internal data class JourneyColors(
    /** The palette as drawn, indexed by the journey's *transit* legs in order. */
    val lineColors: LineColors,
    /** Leg index → its slot in [lineColors]; absent for the legs with no badge. */
    val transitOrder: Map<Int, Int>,
    /**
     * The colour each leg ended up with, indexed like [Journey.legs] and null where a leg has no
     * badge to take one from. Resolving once and passing it on is what stops the overlay and the
     * exported file re-deriving colours of their own and disagreeing with what is on screen.
     */
    val legColors: List<Color?>,
)

/**
 * One journey is one sequence, so its transit legs are each other's neighbours: they take one flat
 * palette group, and the list, the map's overlay, the map's pane and the exported file all read
 * from this single resolution.
 */
@Composable
internal fun rememberJourneyColors(journey: Journey?): JourneyColors {
    val lineColors = rememberLineColors(
        journey?.legs.orEmpty().filter { it.isTransit }.map { leg ->
            LineColorRequest(leg.routeColor, leg.mode.color)
        },
    )
    // Walk legs take no palette slot, so a leg's row index is not its slot in the palette order.
    val transitOrder = remember(journey) {
        journey?.legs.orEmpty().withIndex()
            .filter { (_, leg) -> leg.isTransit }
            .mapIndexed { order, (index, _) -> index to order }
            .toMap()
    }
    return remember(journey, lineColors, transitOrder) {
        JourneyColors(
            lineColors = lineColors,
            transitOrder = transitOrder,
            legColors = journey?.legs.orEmpty().mapIndexed { index, leg ->
                transitOrder[index]?.let { order -> lineColors.at(order, leg.mode.color) }
            },
        )
    }
}

/**
 * A leg's line badge, tappable to see that run on the map; legs the feed gives no trip id for stay
 * inert rather than looking tappable and doing nothing.
 */
@Composable
internal fun LegModeChip(
    leg: JourneyLeg,
    containerColor: Color,
    onOpenTrip: (PendingMapTrip) -> Unit,
) {
    ModeChip(
        mode = leg.mode,
        label = leg.lineLabel,
        containerColor = containerColor,
        clickLabel = stringResource(R.string.itinerary_open_trip),
        onClick = leg.tripId?.let { tripId ->
            {
                onOpenTrip(
                    PendingMapTrip(
                        tripId = tripId,
                        label = leg.lineLabel,
                        mode = leg.mode,
                        routeColor = leg.routeColor,
                    ),
                )
            }
        },
    )
}
