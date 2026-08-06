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
