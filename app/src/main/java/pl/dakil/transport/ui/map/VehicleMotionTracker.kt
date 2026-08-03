package pl.dakil.transport.ui.map

import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.VehicleMotionSettings

/**
 * Smooths the schedule-derived vehicle positions into what actually gets drawn.
 *
 * Every fetch replaces a trip's segments with freshly delay-corrected ones, so the position a
 * marker "should" be at can move discontinuously — and, when a feed revises a delay upward,
 * *backwards*. Recomputing each frame from scratch would render those revisions as teleports.
 * This tracker keeps per-trip state between frames and applies two corrections:
 *
 * 1. **Monotonic progress** — a trip's progress along its route may never decrease. A delay
 *    revision stalls the marker where it is until the schedule catches up, instead of rewinding
 *    it. Progress is ordered by (current segment's departure time, fraction along that segment),
 *    which stays comparable across fetches even though segment lists don't.
 * 2. **Easing** — the drawn position moves a fraction of the way toward the target each frame,
 *    turning a correction into a brief speed-up rather than a jump. Corrections larger than
 *    [VehicleMotionSettings.smoothingSnapThresholdMeters] are applied at once, since easing
 *    across that distance would draw a vehicle gliding over open country.
 *
 * Not thread-safe: it is stepped from a single collector coroutine.
 */
class VehicleMotionTracker {

    private data class TripMotion(
        /** Ordering key of the last accepted progress: segment departure epoch millis. */
        val segmentDepartureMillis: Long,
        /** Fraction along that segment, 0..1. */
        val fraction: Double,
        /** Position the timetable last put this vehicle at, after monotonic clamping. */
        val target: GeoPoint,
        /** Position actually drawn, chasing [target]. */
        val drawn: GeoPoint,
    )

    private val motions = mutableMapOf<String, TripMotion>()

    /**
     * Advances one frame. [targets] are the raw positions the timetable implies right now, each
     * paired with the progress key it was computed from; the returned markers carry the
     * corrected positions. Trips absent from [targets] are forgotten, so a vehicle that leaves
     * the map and comes back later starts fresh rather than easing in from a stale position.
     */
    fun frame(
        targets: List<VehicleFrameTarget>,
        settings: VehicleMotionSettings,
    ): List<VehicleMarker> {
        val seen = HashSet<String>(targets.size)
        val result = targets.map { target ->
            seen += target.marker.id
            val previous = motions[target.marker.id]
            val position = advance(previous, target, settings)
            target.marker.copy(position = position)
        }
        motions.keys.retainAll(seen)
        return result
    }

    /** Drops all state, e.g. when vehicles are switched off entirely. */
    fun reset() = motions.clear()

    private fun advance(
        previous: TripMotion?,
        target: VehicleFrameTarget,
        settings: VehicleMotionSettings,
    ): GeoPoint {
        if (previous == null) {
            motions[target.marker.id] = TripMotion(
                segmentDepartureMillis = target.segmentDepartureMillis,
                fraction = target.fraction,
                target = target.marker.position,
                drawn = target.marker.position,
            )
            return target.marker.position
        }

        // Monotonic progress: keep the previous target when the new one sits earlier on the route.
        val movedForward = target.segmentDepartureMillis > previous.segmentDepartureMillis ||
            (target.segmentDepartureMillis == previous.segmentDepartureMillis && target.fraction >= previous.fraction)
        val accept = movedForward || !settings.monotonicProgress
        val newTarget = if (accept) target.marker.position else previous.target

        val drawn = ease(previous.drawn, newTarget, settings)
        motions[target.marker.id] = TripMotion(
            segmentDepartureMillis = if (accept) target.segmentDepartureMillis else previous.segmentDepartureMillis,
            fraction = if (accept) target.fraction else previous.fraction,
            target = newTarget,
            drawn = drawn,
        )
        return drawn
    }

    private fun ease(from: GeoPoint, to: GeoPoint, settings: VehicleMotionSettings): GeoPoint {
        val factor = settings.smoothingFactor
        if (factor >= 1f) return to
        if (from.distanceMetersTo(to) > settings.smoothingSnapThresholdMeters) return to
        return GeoPoint(
            lat = from.lat + (to.lat - from.lat) * factor,
            lon = from.lon + (to.lon - from.lon) * factor,
        )
    }
}

/**
 * One vehicle's raw, timetable-derived position for a single frame, together with the progress
 * key [VehicleMotionTracker] orders positions by.
 */
data class VehicleFrameTarget(
    val marker: VehicleMarker,
    val segmentDepartureMillis: Long,
    val fraction: Double,
)
