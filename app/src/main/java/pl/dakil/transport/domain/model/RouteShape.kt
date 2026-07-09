package pl.dakil.transport.domain.model

/** A bare WGS84 coordinate, used for map geometry that isn't a stop/place. */
data class GeoPoint(val lat: Double, val lon: Double)

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
