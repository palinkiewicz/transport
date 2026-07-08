package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ItineraryDto(
    val duration: Int,
    val startTime: String,
    val endTime: String,
    val transfers: Int,
    val id: String? = null,
    val legs: List<LegDto>,
)

@Serializable
data class PlanResponseDto(
    val from: PlaceDto,
    val to: PlaceDto,
    val direct: List<ItineraryDto> = emptyList(),
    val itineraries: List<ItineraryDto> = emptyList(),
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)
