package pl.dakil.transport.domain.model

/**
 * A trip the user asked to see on the map, handed from a timetable screen to the map through
 * [pl.dakil.transport.ui.search.SearchStateHolder].
 *
 * [between] is the stop pair the vehicle sits between right now (see [currentLegAt]): the map has
 * no way to look a trip's position up by id, so it fetches the trip's segments from a box around
 * that pair. Carrying it here means the map can start fetching immediately, without first waiting
 * on the trip's own details.
 *
 * [isRunning] is what decides whether there is a vehicle to follow at all. A run that has finished
 * — or has yet to set off — still has a route and stops worth showing, it just has nowhere to draw
 * a marker, so the map skips the vehicle fetches entirely rather than polling for one that cannot
 * be there.
 */
data class PendingMapTrip(
    val tripId: String,
    /** Line label to show while the trip's own details are still loading. */
    val label: String,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
    val between: Pair<GeoPoint, GeoPoint>,
    val isRunning: Boolean,
)
