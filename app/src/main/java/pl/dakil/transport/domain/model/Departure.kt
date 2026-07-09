package pl.dakil.transport.domain.model

import java.time.OffsetDateTime

data class Departure(
    val mode: TransportMode,
    val stopName: String,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val routeColor: String? = null,
    val time: OffsetDateTime,
    val scheduledTime: OffsetDateTime,
    val realTime: Boolean,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
    val poleStopId: String? = null,
    val directionId: String? = null,
    val track: String? = null,
    val tripId: String? = null,
) {
    val lineLabel: String get() = routeShortName ?: displayName ?: mode.name
    val hasDelay: Boolean get() = time != scheduledTime
}

data class StopDepartures(
    val stopName: String,
    val stopId: String?,
    val departures: List<Departure>,
    val nextPageCursor: String? = null,
    val previousPageCursor: String? = null,
)
