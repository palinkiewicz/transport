package pl.dakil.transport.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A bare WGS84 coordinate, used for map geometry that isn't a stop/place. */
data class GeoPoint(val lat: Double, val lon: Double) {

    /** Great-circle (haversine) distance to [other] in meters. */
    fun distanceMetersTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

/**
 * The on-map geometry of one transit line (one representative trip's path), e.g. for
 * drawing every route serving a stop. [segments] holds one polyline per transit leg —
 * usually a single segment, more when the sampled trip has interlined legs.
 */
data class RouteShape(
    val lineLabel: String,
    val headsign: String?,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
    val segments: List<List<GeoPoint>>,
)
