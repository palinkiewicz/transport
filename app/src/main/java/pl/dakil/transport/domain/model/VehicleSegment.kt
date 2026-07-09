package pl.dakil.transport.domain.model

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * One between-stops segment of a trip currently operating on the map (MOTIS `TripSegment`).
 * A vehicle's position at a point in time is interpolated along [path] between [departure]
 * and [arrival]; segments of the same [tripKey] together describe one vehicle.
 */
data class VehicleSegment(
    /** Groups segments belonging to one vehicle; falls back to the label when the id is absent. */
    val tripKey: String,
    /** Line label to show next to the marker, e.g. "S1" or "Bus 175". */
    val label: String,
    /** Destination of the trip, from the segment's arrival place. */
    val headsign: String?,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
    /** Whether real-time data backs this segment (vs. schedule only). */
    val realTime: Boolean,
    val departure: OffsetDateTime,
    val arrival: OffsetDateTime,
    val path: List<GeoPoint>,
) {

    /**
     * Position along [path] at [time], linearly interpolated by elapsed-time fraction weighted
     * by distance. Clamped to the endpoints outside the segment's time window (a vehicle
     * dwelling at a stop sits at the segment boundary); null only when there is no geometry.
     */
    fun positionAt(time: OffsetDateTime): GeoPoint? {
        if (path.isEmpty()) return null
        if (time <= departure || path.size == 1) return path.first()
        if (time >= arrival) return path.last()

        val totalMillis = Duration.between(departure, arrival).toMillis()
        if (totalMillis <= 0) return path.first()
        val fraction = Duration.between(departure, time).toMillis().toDouble() / totalMillis

        val legLengths = path.zipWithNext { a, b -> approxDistance(a, b) }
        val totalLength = legLengths.sum()
        if (totalLength <= 0.0) return path.first()

        var remaining = fraction * totalLength
        for ((i, legLength) in legLengths.withIndex()) {
            if (remaining <= legLength) {
                val t = if (legLength > 0.0) remaining / legLength else 0.0
                val a = path[i]
                val b = path[i + 1]
                return GeoPoint(
                    lat = a.lat + (b.lat - a.lat) * t,
                    lon = a.lon + (b.lon - a.lon) * t,
                )
            }
            remaining -= legLength
        }
        return path.last()
    }
}

/** Equirectangular approximation — only relative weights matter for interpolation. */
private fun approxDistance(a: GeoPoint, b: GeoPoint): Double {
    val x = (b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
    val y = b.lat - a.lat
    return sqrt(x * x + y * y)
}
