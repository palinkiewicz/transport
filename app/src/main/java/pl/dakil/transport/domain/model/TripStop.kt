package pl.dakil.transport.domain.model

import java.time.OffsetDateTime

/** One row of a trip's timetable. */
data class TripStop(
    /** The stop itself — its id and coordinates are what open its departure board. */
    val place: TransitLocation,
    val time: OffsetDateTime,
    val scheduledTime: OffsetDateTime,
    val track: String?,
) {
    val name: String get() = place.name
}

/**
 * The one leg's own timetable: the stop boarded, everything called at on the way, the stop
 * alighted at. Only the ridden portion of the run — the plan endpoint says nothing about where the
 * vehicle was before or goes after — which is exactly the window in which the traveller has a
 * vehicle of their own to look for.
 */
fun JourneyLeg.toTripStops(): List<TripStop> = buildList {
    add(TripStop(fromPlace, startTime, scheduledStartTime, fromTrack))
    intermediateStops.forEach { stop ->
        val arrival = stop.arrivalTime ?: return@forEach
        add(TripStop(stop.place, arrival, stop.scheduledArrivalTime ?: arrival, stop.track))
    }
    add(TripStop(toPlace, endTime, scheduledEndTime, toTrack))
}

/**
 * Flattens the trip itinerary (joined interlined legs) into a single ordered stop list. The legs
 * are one run's, so each one's first stop is the previous one's last and is dropped at the join.
 */
fun Journey.toTripStops(): List<TripStop> =
    legs.filter { it.isTransit }.flatMapIndexed { index, leg ->
        leg.toTripStops().let { if (index == 0) it else it.drop(1) }
    }

/**
 * Index of the last stop the vehicle has already called at, or -1 before the run has started.
 * Times are the real-time ones where a feed supplies them, so a delayed run advances late.
 */
fun List<TripStop>.lastPassedIndex(now: OffsetDateTime): Int =
    indexOfLast { !it.time.isAfter(now) }

/**
 * Index of the stop the run is heading for — the first it has not called at yet, or the terminus
 * once it has called at them all. -1 for an empty list, as for [lastPassedIndex].
 *
 * This is what a live run's timetable highlights: where it is *going* is the answer the reader
 * wants, and it is the only one that stays useful once the vehicle has left the last stop behind.
 */
fun List<TripStop>.nextStopIndex(now: OffsetDateTime): Int =
    indexOfFirst { it.time.isAfter(now) }.takeIf { it >= 0 } ?: lastIndex

/**
 * Whether the run is on the road at [now] — the condition for it having a position to show on the
 * map at all. The lead before the first departure is deliberate: `/map/trips` already returns a
 * vehicle waiting to set off, drawn at its first stop.
 */
fun List<TripStop>.isRunningAt(now: OffsetDateTime): Boolean {
    val first = firstOrNull() ?: return false
    return !now.isBefore(first.time.minusMinutes(LIVE_LEAD_MINUTES)) && !now.isAfter(last().time)
}

/**
 * The two stops the vehicle is between at [now], clamped to the ends of the run. The vehicle is
 * somewhere on the path joining them, which is what makes this pair a bounding box that needs no
 * guess about how late the run is — the times it is derived from are already delay-corrected.
 */
fun List<TripStop>.currentLegAt(now: OffsetDateTime): Pair<GeoPoint, GeoPoint>? {
    if (isEmpty()) return null
    val passed = lastPassedIndex(now).coerceIn(0, lastIndex)
    val next = (passed + 1).coerceAtMost(lastIndex)
    return this[passed].place.toGeoPoint() to this[next].place.toGeoPoint()
}

private fun TransitLocation.toGeoPoint() = GeoPoint(lat = lat, lon = lon)

/** How long before its first departure a run counts as live — see [isRunningAt]. */
private const val LIVE_LEAD_MINUTES = 5L
