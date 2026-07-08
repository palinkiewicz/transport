package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StopTimeDto(
    val place: PlaceDto,
    val mode: String,
    val realTime: Boolean,
    val headsign: String? = null,
    val agencyName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val tripShortName: String? = null,
    val displayName: String? = null,
    val cancelled: Boolean? = null,
    val tripCancelled: Boolean? = null,
)

@Serializable
data class StopTimesResponseDto(
    val stopTimes: List<StopTimeDto>,
    val place: PlaceDto,
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)
