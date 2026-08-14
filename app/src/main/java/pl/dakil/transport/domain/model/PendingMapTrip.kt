package pl.dakil.transport.domain.model

/**
 * A trip the user asked to see on the map, handed over through
 * [pl.dakil.transport.ui.search.SearchStateHolder] by whichever screen the line was tapped on — a
 * departure board, a saved line, an itinerary leg.
 *
 * Identity only. Where the run has got to, and whether it is on the road at all, are worked out by
 * the map from the trip's own timetable ([pl.dakil.transport.ui.map.MapViewModel.selectPinnedTrip]):
 * that timetable has to be fetched for the panel regardless, and asking the tapped row to know it
 * would mean a network round trip before the map could even open.
 */
data class PendingMapTrip(
    val tripId: String,
    /** Line label to show while the trip's own details are still loading. */
    val label: String,
    val mode: TransportMode,
    /** GTFS `RRGGBB` route color (no leading `#`), when the feed provides one. */
    val routeColor: String?,
)
