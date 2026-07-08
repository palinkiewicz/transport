package pl.dakil.transport.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object MapRoute

@Serializable
object SearchRoute

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
    val maxTransfers: Int?,
    val timeIso: String?,
)

@Serializable
data class ItineraryRoute(val index: Int)

@Serializable
data class DeparturesRoute(
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val stopId: String?,
)
