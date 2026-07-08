package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import pl.dakil.transport.data.remote.dto.ItineraryDto
import pl.dakil.transport.data.remote.dto.LegDto
import pl.dakil.transport.data.remote.dto.MatchDto
import pl.dakil.transport.data.remote.dto.PlaceDto
import pl.dakil.transport.data.remote.dto.StopTimeDto
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

fun MatchDto.toTransitLocation(): TransitLocation =
    TransitLocation(
        name = name,
        lat = lat,
        lon = lon,
        stopId = if (type == "STOP") id else null,
    )

fun PlaceDto.toTransitLocation(): TransitLocation =
    TransitLocation(name = name, lat = lat, lon = lon, stopId = stopId)

private fun String.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.parse(this)

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
        headsign = headsign,
        routeShortName = routeShortName,
        routeLongName = routeLongName,
        displayName = displayName,
        agencyName = agencyName,
        routeColor = routeColor,
        cancelled = cancelled ?: false,
        intermediateStopNames = intermediateStops?.map { it.name } ?: emptyList(),
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
    )
}
