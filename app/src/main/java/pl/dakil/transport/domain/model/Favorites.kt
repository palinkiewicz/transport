package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/** A saved start→end pair; date/time and search options are deliberately not part of it. */
@Serializable
data class FavoriteConnection(
    val from: TransitLocation,
    val to: TransitLocation,
) {
    val key: String get() = "${from.favoriteKey}→${to.favoriteKey}"
}

/**
 * A saved line (route run). Identified by label+headsign+mode rather than trip id — trip ids
 * are service-day-specific, so [tripId] is only the last seen run, kept to open its timetable.
 */
@Serializable
data class FavoriteLine(
    val label: String,
    val headsign: String?,
    val mode: TransportMode,
    val routeColor: String? = null,
    val tripId: String,
) {
    val key: String get() = "$label|$headsign|$mode"
}

/** Everything the user has starred, persisted as one blob (see FavoritesRepository). */
@Serializable
data class Favorites(
    val locations: List<TransitLocation> = emptyList(),
    val connections: List<FavoriteConnection> = emptyList(),
    val lines: List<FavoriteLine> = emptyList(),
) {
    fun containsLocation(location: TransitLocation): Boolean =
        locations.any { it.favoriteKey == location.favoriteKey }

    fun containsConnection(connection: FavoriteConnection): Boolean =
        connections.any { it.key == connection.key }

    fun containsLine(line: FavoriteLine): Boolean =
        lines.any { it.key == line.key }

    fun toggleLocation(location: TransitLocation): Favorites =
        if (containsLocation(location)) {
            copy(locations = locations.filterNot { it.favoriteKey == location.favoriteKey })
        } else {
            copy(locations = locations + location)
        }

    fun toggleConnection(connection: FavoriteConnection): Favorites =
        if (containsConnection(connection)) {
            copy(connections = connections.filterNot { it.key == connection.key })
        } else {
            copy(connections = connections + connection)
        }

    fun toggleLine(line: FavoriteLine): Favorites =
        if (containsLine(line)) {
            copy(lines = lines.filterNot { it.key == line.key })
        } else {
            copy(lines = lines + line)
        }

    companion object {
        val EMPTY = Favorites()
    }
}
