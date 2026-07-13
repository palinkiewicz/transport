package pl.dakil.transport.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object MapRoute

@Serializable
object SearchRoute

@Serializable
object FavouritesRoute

/** Full-screen location search filling the Search screen's start (`isFrom`) or destination field. */
@Serializable
data class LocationPickerRoute(val isFrom: Boolean)

/** Groups [ResultsRoute] and [ItineraryRoute] so they can share a [pl.dakil.transport.ui.results.ResultsViewModel]. */
@Serializable
object ResultsGraph

@Serializable
data class ResultsRoute(
    val fromName: String,
    val fromLat: Double,
    val fromLon: Double,
    val fromStopId: String?,
    val toName: String,
    val toLat: Double,
    val toLon: Double,
    val toStopId: String?,
    val timeIso: String?,
)

@Serializable
data class ItineraryRoute(val index: Int)

/** Full run of a single vehicle trip — all stops with times. */
@Serializable
data class TripRoute(
    val tripId: String,
    val lineLabel: String,
    val headsign: String?,
    val modeName: String,
    val routeColor: String?,
)

@Serializable
data class DeparturesRoute(
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val stopId: String?,
    val timeIso: String?,
)
