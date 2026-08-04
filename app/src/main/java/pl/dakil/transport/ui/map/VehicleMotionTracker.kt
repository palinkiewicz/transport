package pl.dakil.transport.ui.map

import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.VehicleMotionSettings

/**
 * Keeps the schedule-derived vehicle positions from going backwards.
 *
 * Every fetch replaces a trip's segments with freshly delay-corrected ones, so the position a
 * marker "should" be at can move discontinuously — and, when a feed revises a delay upward,
 * *backwards*. Recomputing each frame from scratch would render those revisions as a marker
 * jumping back down its route, so this tracker keeps per-trip state between frames: a trip's
 * progress may never decrease, and a revision stalls the marker where it is until the schedule
 * catches up. Progress is ordered by (current segment's departure time, fraction along that
 * segment), which stays comparable across fetches even though segment lists don't.
 *
 * Not thread-safe: it is stepped from a single collector coroutine.
 */
class VehicleMotionTracker {

    private data class TripMotion(
        /** Ordering key of the last accepted progress: segment departure epoch millis. */
        val segmentDepartureMillis: Long,
        /** Fraction along that segment, 0..1. */
        val fraction: Double,
        /** Position drawn for this trip, after monotonic clamping. */
        val position: GeoPoint,
    )

    private val motions = mutableMapOf<String, TripMotion>()

    /**
     * Advances one frame. [targets] are the raw positions the timetable implies right now, each
     * paired with the progress key it was computed from; the returned markers carry the
     * corrected positions. Trips absent from [targets] are forgotten, so a vehicle that leaves
     * the map and comes back later starts fresh rather than being held back by stale progress.
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
                position = target.marker.position,
            )
            return target.marker.position
        }

        // Monotonic progress: keep the previous position when the new one sits earlier on the route.
        val movedForward = target.segmentDepartureMillis > previous.segmentDepartureMillis ||
            (target.segmentDepartureMillis == previous.segmentDepartureMillis && target.fraction >= previous.fraction)
        if (!movedForward && settings.monotonicProgress) return previous.position

        motions[target.marker.id] = TripMotion(
            segmentDepartureMillis = target.segmentDepartureMillis,
            fraction = target.fraction,
            position = target.marker.position,
        )
        return target.marker.position
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
