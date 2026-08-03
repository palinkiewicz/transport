package pl.dakil.transport.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object MapRoute

/** Connections (A → B) search form. Bottom-bar tab. */
@Serializable
object ConnectionsRoute

/** Departures/arrivals board search form. Bottom-bar tab. */
@Serializable
object DeparturesRoute

@Serializable
object FavouritesRoute

@Serializable
object SettingsRoute

/** What a [LocationPickerRoute] pick fills. */
enum class PickerTarget {
    /** The Connections form's start field. */
    FROM,

    /** The Connections form's destination field. */
    TO,

    /** The Departures form's stop field. */
    STOP,

    /** The Map screen's selection (also drives the camera). */
    MAP,
}

/**
 * Full-screen location search. [target] is [PickerTarget]'s name — kept as a String because
 * type-safe nav args land in the ViewModel's SavedStateHandle as primitives.
 */
@Serializable
data class LocationPickerRoute(val target: String) {
    constructor(target: PickerTarget) : this(target.name)
}

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

/** The departures/arrivals board itself, for one stop. */
@Serializable
data class DepartureBoardRoute(
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val stopId: String?,
    val timeIso: String?,
)
