package pl.dakil.transport.ui.navigation

import android.net.Uri
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.map.formatCoordinates

/**
 * A `geo:` point handed to the app by another one (a map app's "open in…", a link).
 *
 * RFC 5870 puts the coordinates in the path, but map apps routinely send `geo:0,0?q=…` with the
 * real point — and a label — in the query, so both forms are read here. A `q=` holding free
 * text rather than coordinates is *not* guessed at: geocoding someone else's search string
 * could silently route the user to the wrong town.
 */
fun parseGeoUri(uri: Uri?): TransitLocation? {
    if (uri == null || !uri.scheme.equals("geo", ignoreCase = true)) return null

    // schemeSpecificPart keeps the query, which is where the useful point usually is.
    val ssp = uri.schemeSpecificPart ?: return null
    val query = ssp.substringAfter('?', "")
    val label = query.parameter("q")?.let(::labelIn)

    val point = query.parameter("q")?.let(::coordinatesIn)
        ?: coordinatesIn(ssp.substringBefore('?'))
        ?: return null

    val (lat, lon) = point
    return TransitLocation(
        name = label ?: formatCoordinates(lat, lon),
        lat = lat,
        lon = lon,
    )
}

/** Value of [name] in an already-extracted query string, URL-decoded. */
private fun String.parameter(name: String): String? = split('&')
    .firstOrNull { it.startsWith("$name=", ignoreCase = true) }
    ?.substringAfter('=')
    ?.let { runCatching { Uri.decode(it) }.getOrNull() }

/** `52.4,16.9` or `52.4,16.9(Some place)`, also tolerating a trailing `;u=35` uncertainty. */
private fun coordinatesIn(value: String): Pair<Double, Double>? {
    val coordinates = value.substringBefore('(').substringBefore(';').trim().split(',')
    if (coordinates.size < 2) return null
    val lat = coordinates[0].trim().toDoubleOrNull() ?: return null
    val lon = coordinates[1].trim().toDoubleOrNull() ?: return null
    // geo:0,0 is the conventional placeholder for "the point is in the query", not a place.
    if (lat == 0.0 && lon == 0.0) return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}

/** The `(Name)` a sender may append to its coordinates. */
private fun labelIn(value: String): String? = value
    .substringAfter('(', "")
    .substringBeforeLast(')')
    .trim()
    .takeIf { it.isNotEmpty() }
