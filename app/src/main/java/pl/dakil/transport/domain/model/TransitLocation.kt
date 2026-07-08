package pl.dakil.transport.domain.model

/** A location the user picked as a start/end/via point: either a transit stop or a bare coordinate. */
data class TransitLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
) {
    /** Value to send as `fromPlace`/`toPlace`/`stopId` query parameters. */
    val queryValue: String
        get() = stopId ?: "$lat,$lon"

    companion object {
        fun currentPosition(lat: Double, lon: Double): TransitLocation =
            TransitLocation(name = "Your location", lat = lat, lon = lon, stopId = null)
    }
}
