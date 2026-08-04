package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/** Where the map was left: its centre and zoom. */
@Serializable
data class MapCamera(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
) {
    companion object {
        /** Most of Europe in one view — the starting point when nothing has been stored yet. */
        val DEFAULT = MapCamera(lat = 50.0, lon = 10.0, zoom = 5.0)
    }
}

/**
 * What the app carries over between launches: the search forms' picks and the map's camera.
 *
 * Deliberately separate from [AppSettings] — this is state the app writes as the user works,
 * not choices the user made on the Settings screen, and the two would otherwise fight over the
 * same blob on every pan of the map.
 */
@Serializable
data class SessionState(
    val from: TransitLocation? = null,
    val to: TransitLocation? = null,
    val vias: List<ViaPoint> = emptyList(),
    val departureStop: TransitLocation? = null,
    val mapCamera: MapCamera? = null,
) {
    companion object {
        val EMPTY = SessionState()
    }
}
