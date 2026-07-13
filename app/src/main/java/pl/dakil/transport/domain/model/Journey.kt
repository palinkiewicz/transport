package pl.dakil.transport.domain.model

import java.time.Duration
import java.time.OffsetDateTime

data class Journey(
    val id: String?,
    val duration: Int,
    val startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    val transfers: Int,
    val legs: List<JourneyLeg>,
) {
    private val transitLegs: List<JourneyLeg> get() = legs.filter { it.isTransit }

    /** Name of the first transit stop boarded, i.e. skipping any initial walk leg. */
    val firstStopName: String get() = transitLegs.firstOrNull()?.fromName ?: legs.first().fromName

    /** Name of the last transit stop alighted at, i.e. skipping any final walk leg. */
    val lastStopName: String get() = transitLegs.lastOrNull()?.toName ?: legs.last().toName

    /** Walking distance from the search origin to [firstStopName], if the journey starts with a walk. */
    val walkToFirstStopMeters: Double? get() = legs.first().takeIf { !it.isTransit }?.distanceMeters

    /** Walking distance from [lastStopName] to the search destination, if the journey ends with a walk. */
    val walkFromLastStopMeters: Double? get() = legs.last().takeIf { !it.isTransit }?.distanceMeters

    val departureTime: OffsetDateTime get() = transitLegs.firstOrNull()?.startTime ?: legs.first().startTime
    val departureScheduledTime: OffsetDateTime
        get() = transitLegs.firstOrNull()?.scheduledStartTime ?: legs.first().scheduledStartTime

    val arrivalTime: OffsetDateTime get() = transitLegs.lastOrNull()?.endTime ?: legs.last().endTime
    val arrivalScheduledTime: OffsetDateTime
        get() = transitLegs.lastOrNull()?.scheduledEndTime ?: legs.last().scheduledEndTime

    /** Elapsed time between boarding the first vehicle and alighting the last one. */
    val transitDurationSeconds: Long
        get() = Duration.between(departureTime, arrivalTime).seconds.coerceAtLeast(0)
}

/** A stop passed through (not boarded/alighted at) on a transit leg. */
data class IntermediateStop(
    val name: String,
    val arrivalTime: OffsetDateTime? = null,
    val scheduledArrivalTime: OffsetDateTime? = null,
    val track: String? = null,
)

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
    val distanceMeters: Double? = null,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val displayName: String? = null,
    val agencyName: String? = null,
    val routeColor: String? = null,
    val cancelled: Boolean = false,
    /** True/false only when the feed says so; null = the feed doesn't provide the attribute. */
    val wheelchairAccessible: Boolean? = null,
    val bikesAllowed: Boolean? = null,
    val intermediateStops: List<IntermediateStop> = emptyList(),
    /** Decoded leg geometry for drawing the leg on a map; empty when the API omits it. */
    val path: List<GeoPoint> = emptyList(),
) {
    val isTransit: Boolean get() = mode != TransportMode.WALK && mode != TransportMode.BIKE && mode != TransportMode.CAR

    /** Line badge text: short route name, falling back to the display name. */
    val lineLabel: String get() = routeShortName ?: displayName ?: mode.name

    val hasDelay: Boolean get() = startTime != scheduledStartTime || endTime != scheduledEndTime
}
