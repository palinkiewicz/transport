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

    /**
     * One of the Connections form's intermediate stops, addressed by
     * [LocationPickerRoute.viaIndex]. Restricted to transit stops: the plan API rejects
     * coordinates for `via`.
     */
    VIA,

    /** The Departures form's stop field. */
    STOP,

    /** The Map screen's selection (also drives the camera). */
    MAP,
}

/**
 * Full-screen location search. [target] is [PickerTarget]'s name — kept as a String because
 * type-safe nav args land in the ViewModel's SavedStateHandle as primitives. [viaIndex] is the
 * intermediate-stop slot being filled, and is ignored for every target but [PickerTarget.VIA].
 */
@Serializable
data class LocationPickerRoute(val target: String, val viaIndex: Int = 0) {
    constructor(target: PickerTarget, viaIndex: Int = 0) : this(target.name, viaIndex)
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
    /**
     * Intermediate stops as a JSON array of [pl.dakil.transport.domain.model.ViaPoint], or null
     * for none. Encoded as one string because type-safe nav args only carry primitives, and so
     * the shape can grow without touching this route again — same reasoning as the
     * one-JSON-blob prefs models.
     */
    val viasJson: String? = null,
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
