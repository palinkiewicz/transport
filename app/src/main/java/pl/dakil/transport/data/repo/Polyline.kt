package pl.dakil.transport.data.repo

import kotlin.math.pow
import pl.dakil.transport.domain.model.GeoPoint

/**
 * Decodes a Google-encoded polyline into coordinates. [precision] is the number of decimal
 * places the coordinates were scaled by when encoding (MOTIS reports it per polyline).
 * Malformed trailing input just ends the decode rather than throwing.
 */
fun decodePolyline(encoded: String, precision: Int): List<GeoPoint> {
    val factor = 10.0.pow(precision)
    val points = ArrayList<GeoPoint>(encoded.length / 4)
    var index = 0
    var lat = 0L
    var lon = 0L

    fun nextDelta(): Long? {
        var result = 0L
        var shift = 0
        while (index < encoded.length) {
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f).toLong() shl shift)
            if (chunk < 0x20) {
                return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            }
            shift += 5
        }
        return null // ran out of characters mid-value
    }

    while (index < encoded.length) {
        lat += nextDelta() ?: break
        lon += nextDelta() ?: break
        points += GeoPoint(lat = lat / factor, lon = lon / factor)
    }
    return points
}
