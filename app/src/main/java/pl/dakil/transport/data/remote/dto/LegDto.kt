package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LegDto(
    val mode: String,
    val from: PlaceDto,
    val to: PlaceDto,
    val duration: Int,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val realTime: Boolean,
    val scheduled: Boolean? = null,
    val distance: Double? = null,
    val interlineWithPreviousLeg: Boolean? = null,
    val headsign: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val tripShortName: String? = null,
    val displayName: String? = null,
    val agencyName: String? = null,
    val cancelled: Boolean? = null,
    val intermediateStops: List<PlaceDto>? = null,
)
