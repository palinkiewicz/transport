package pl.dakil.transport.domain.model

/**
 * Details of one vehicle run fetched from the trip endpoint: its on-map geometry plus the
 * amenity/accessibility attributes the feed provides (nulls = the feed doesn't say).
 */
data class TripDetails(
    val headsign: String?,
    val agencyName: String?,
    val routeLongName: String?,
    val wheelchairAccessible: Boolean?,
    val bikesAllowed: Boolean?,
    /** Route geometry for drawing the trip's path on the map; null when the feed has none. */
    val shape: RouteShape?,
)
