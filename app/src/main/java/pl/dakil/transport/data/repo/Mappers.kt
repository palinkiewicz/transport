package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import pl.dakil.transport.data.remote.dto.ItineraryDto
import pl.dakil.transport.data.remote.dto.LegDto
import pl.dakil.transport.data.remote.dto.MatchDto
import pl.dakil.transport.data.remote.dto.PlaceDto
import pl.dakil.transport.data.remote.dto.StopTimeDto
import pl.dakil.transport.data.remote.dto.TripSegmentDto
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.domain.model.IntermediateStop
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.domain.model.VehicleSegment

fun MatchDto.toTransitLocation(): TransitLocation =
    TransitLocation(
        name = name,
        lat = lat,
        lon = lon,
        stopId = if (type == "STOP") id else null,
        city = areas?.firstOrNull { it.default == true }?.name,
    )

fun PlaceDto.toTransitLocation(): TransitLocation =
    TransitLocation(
        name = name,
        lat = lat,
        lon = lon,
        stopId = stopId,
        modes = modes?.map { TransportMode.fromApiValue(it) } ?: emptyList(),
    )

/**
 * MOTIS silently misparses timestamps with more than millisecond precision — e.g. requesting
 * `2026-07-09T08:38:49.43796+02:00` returns itineraries for July **5th** — so second-truncate
 * every timestamp sent to the API. ([OffsetDateTime.now] carries nanoseconds, and the search
 * screen's pickers only replace hour/minute/date, keeping the fractional seconds.)
 */
fun OffsetDateTime.toApiTimestamp(): String =
    truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/**
 * The API returns times in UTC (`Z`), but the UI formats [OffsetDateTime]s with the offset
 * embedded in the value itself (no zone conversion at format time) — so times must be
 * converted to the device's local zone here, once, right after parsing.
 */
private fun String.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()

fun LegDto.toDomain(): JourneyLeg =
    JourneyLeg(
        mode = TransportMode.fromApiValue(mode),
        fromName = from.name,
        toName = to.name,
        fromTrack = from.track ?: from.scheduledTrack,
        toTrack = to.track ?: to.scheduledTrack,
        startTime = startTime.toOffsetDateTime(),
        endTime = endTime.toOffsetDateTime(),
        scheduledStartTime = scheduledStartTime.toOffsetDateTime(),
        scheduledEndTime = scheduledEndTime.toOffsetDateTime(),
        realTime = realTime,
        duration = duration,
        distanceMeters = distance,
        headsign = headsign,
        routeShortName = routeShortName,
        routeLongName = routeLongName,
        displayName = displayName,
        agencyName = agencyName,
        routeColor = routeColor,
        cancelled = cancelled ?: false,
        intermediateStops = intermediateStops?.map { stop ->
            IntermediateStop(
                name = stop.name,
                arrivalTime = (stop.arrival ?: stop.departure)?.toOffsetDateTime(),
                scheduledArrivalTime = (stop.scheduledArrival ?: stop.scheduledDeparture)?.toOffsetDateTime(),
                track = stop.track ?: stop.scheduledTrack,
            )
        } ?: emptyList(),
    )

fun ItineraryDto.toDomain(): Journey =
    Journey(
        id = id,
        duration = duration,
        startTime = startTime.toOffsetDateTime(),
        endTime = endTime.toOffsetDateTime(),
        transfers = transfers,
        legs = legs.map { it.toDomain() },
    )

/**
 * @param polylinePrecision precision the segment's polyline was requested with — `/map/trips`
 * doesn't echo it back per polyline the way `legGeometry` does.
 */
fun TripSegmentDto.toDomain(polylinePrecision: Int): VehicleSegment {
    val label = trips.firstOrNull()?.let { it.displayName ?: it.routeShortName } ?: mode
    return VehicleSegment(
        tripKey = trips.firstOrNull()?.tripId ?: "$label/${to.name}",
        tripId = trips.firstOrNull()?.tripId,
        label = label,
        headsign = to.name,
        mode = TransportMode.fromApiValue(mode),
        routeColor = routeColor?.takeIf { it.isNotBlank() },
        realTime = realTime,
        departure = departure.toOffsetDateTime(),
        arrival = arrival.toOffsetDateTime(),
        path = decodePolyline(polyline, polylinePrecision),
    )
}

fun StopTimeDto.toDomain(): Departure {
    val time = place.departure ?: place.arrival ?: error("StopTime place is missing both arrival and departure")
    val scheduledTime = place.scheduledDeparture ?: place.scheduledArrival ?: time
    return Departure(
        mode = TransportMode.fromApiValue(mode),
        stopName = place.name,
        headsign = headsign,
        routeShortName = routeShortName,
        displayName = displayName,
        routeColor = routeColor,
        time = time.toOffsetDateTime(),
        scheduledTime = scheduledTime.toOffsetDateTime(),
        realTime = realTime,
        cancelled = cancelled ?: false,
        tripCancelled = tripCancelled ?: false,
        poleStopId = place.stopId,
        directionId = directionId,
        track = place.track ?: place.scheduledTrack,
        tripId = tripId,
    )
}
