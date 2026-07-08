package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val name: String,
    val stopId: String? = null,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val level: Double? = null,
    val tz: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val scheduledTrack: String? = null,
    val track: String? = null,
    val stopCode: String? = null,
    val description: String? = null,
    val cancelled: Boolean? = null,
    val modes: List<String>? = null,
)
