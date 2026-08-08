package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/** A location the user picked as a start/end/via point: either a transit stop or a bare coordinate. */
@Serializable
data class TransitLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val modes: List<TransportMode> = emptyList(),
    /**
     * The API's significance score for this place; 0 when it did not provide one.
     *
     * Two uses: ranking a major interchange above a request stop of the same name, and — because
     * every pole of one station is given the identical value — corroborating that a group of
     * same-named stops really is one station. See `PlaceSearchEngine`.
     */
    val importance: Double = 0.0,
) {
    /** Value to send as `fromPlace`/`toPlace`/`stopId` query parameters. */
    val queryValue: String
        get() = stopId ?: "$lat,$lon"

    /** "City, State, Country" subtitle for list rows; null when no area info is known. */
    val areaLabel: String?
        get() = listOfNotNull(city, state, country).ifEmpty { null }?.joinToString(", ")

    /** Stable identity for favorites/dedup: the stop id, or the bare coordinate. */
    val favoriteKey: String
        get() = stopId ?: "$lat,$lon"

    /**
     * Whether [other] is the same physical place: the same stop, or — when either side is a
     * bare coordinate — the same position. Names are ignored: the same stop reached through
     * the map and through search can be labelled differently.
     */
    fun isSamePlaceAs(other: TransitLocation): Boolean =
        if (stopId != null && other.stopId != null) {
            stopId == other.stopId
        } else {
            lat == other.lat && lon == other.lon
        }

    /** Mode used to color/icon this stop on the map when it serves more than one mode. */
    val primaryMode: TransportMode?
        get() = modes.minByOrNull { MODE_PRIORITY.indexOf(it).let { i -> if (i == -1) Int.MAX_VALUE else i } }

    companion object {
        /**
         * The user's own position as a pickable place. [name] is passed in rather than baked
         * in because it is user-visible text, and this model has no access to resources.
         */
        fun currentPosition(lat: Double, lon: Double, name: String): TransitLocation =
            TransitLocation(name = name, lat = lat, lon = lon, stopId = null)

        // Rarer/faster modes take priority so e.g. a rail+bus interchange reads as a rail stop.
        private val MODE_PRIORITY = listOf(
            TransportMode.AIRPLANE,
            TransportMode.HIGHSPEED_RAIL,
            TransportMode.NIGHT_RAIL,
            TransportMode.LONG_DISTANCE,
            TransportMode.RAIL,
            TransportMode.REGIONAL_RAIL,
            TransportMode.SUBURBAN,
            TransportMode.SUBWAY,
            TransportMode.FERRY,
            TransportMode.AERIAL_LIFT,
            TransportMode.FUNICULAR,
            TransportMode.TRAM,
            TransportMode.COACH,
            TransportMode.BUS,
            TransportMode.OTHER,
        )
    }
}
