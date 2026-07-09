package pl.dakil.transport.domain.model

/** A location the user picked as a start/end/via point: either a transit stop or a bare coordinate. */
data class TransitLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val city: String? = null,
    val modes: List<TransportMode> = emptyList(),
) {
    /** Value to send as `fromPlace`/`toPlace`/`stopId` query parameters. */
    val queryValue: String
        get() = stopId ?: "$lat,$lon"

    /** Mode used to color/icon this stop on the map when it serves more than one mode. */
    val primaryMode: TransportMode?
        get() = modes.minByOrNull { MODE_PRIORITY.indexOf(it).let { i -> if (i == -1) Int.MAX_VALUE else i } }

    companion object {
        fun currentPosition(lat: Double, lon: Double): TransitLocation =
            TransitLocation(name = "Your location", lat = lat, lon = lon, stopId = null)

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
