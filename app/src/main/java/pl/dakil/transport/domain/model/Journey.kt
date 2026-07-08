package pl.dakil.transport.domain.model

import java.time.OffsetDateTime

data class Journey(
    val id: String?,
    val duration: Int,
    val startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    val transfers: Int,
    val legs: List<JourneyLeg>,
) {
    val fromName: String get() = legs.first().fromName
    val toName: String get() = legs.last().toName
}

data class JourneyLeg(
    val mode: TransportMode,
    val fromName: String,
    val toName: String,
    val fromTrack: String? = null,
    val toTrack: String? = null,
    val startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    val scheduledStartTime: OffsetDateTime,
    val scheduledEndTime: OffsetDateTime,
    val realTime: Boolean,
    val duration: Int,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val displayName: String? = null,
    val agencyName: String? = null,
    val routeColor: String? = null,
    val cancelled: Boolean = false,
    val intermediateStopNames: List<String> = emptyList(),
) {
    val isTransit: Boolean get() = mode != TransportMode.WALK && mode != TransportMode.BIKE && mode != TransportMode.CAR

    /** Line badge text: short route name, falling back to the display name. */
    val lineLabel: String get() = routeShortName ?: displayName ?: mode.name

    val hasDelay: Boolean get() = startTime != scheduledStartTime || endTime != scheduledEndTime
}
