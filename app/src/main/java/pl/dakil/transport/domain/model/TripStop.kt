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

/** Flattens the trip itinerary (joined interlined legs) into a single ordered stop list. */
fun Journey.toTripStops(): List<TripStop> = buildList {
    val transitLegs = legs.filter { it.isTransit }
    transitLegs.forEachIndexed { index, leg ->
        if (index == 0) {
            add(TripStop(leg.fromPlace, leg.startTime, leg.scheduledStartTime, leg.fromTrack))
        }
        leg.intermediateStops.forEach { stop ->
            val arrival = stop.arrivalTime ?: return@forEach
            add(TripStop(stop.place, arrival, stop.scheduledArrivalTime ?: arrival, stop.track))
        }
        add(TripStop(leg.toPlace, leg.endTime, leg.scheduledEndTime, leg.toTrack))
    }
}

/**
 * Index of the last stop the vehicle has already called at, or -1 before the run has started.
 * Times are the real-time ones where a feed supplies them, so a delayed run advances late.
 */
fun List<TripStop>.lastPassedIndex(now: OffsetDateTime): Int =
    indexOfLast { !it.time.isAfter(now) }

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
