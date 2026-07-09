package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TripInfoDto(
    val tripId: String,
    val routeShortName: String? = null,
    val displayName: String? = null,
)

/** One `/v6/map/trips` segment: a trip's path between two consecutive stops. */
@Serializable
data class TripSegmentDto(
    val trips: List<TripInfoDto> = emptyList(),
    val routeColor: String? = null,
    val mode: String,
    val distance: Double? = null,
    val from: PlaceDto,
    val to: PlaceDto,
    val departure: String,
    val arrival: String,
    val scheduledDeparture: String? = null,
    val scheduledArrival: String? = null,
    val realTime: Boolean = false,
    val polyline: String,
)
