package pl.dakil.transport.data.export

import java.io.Writer
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey

/**
 * Writes [journey] to [out] as GeoJSON (RFC 7946): one `FeatureCollection` holding a `Point` per
 * waypoint and a `LineString` per drawn leg, in that order, so a reader that draws features in
 * document order puts the stops on top of the paths.
 *
 * Unlike the GPX and KML writers this builds a tree and encodes it in one go rather than streaming
 * text: JSON has no schema to keep element order valid, and letting the serializer do the escaping
 * removes the one thing hand-written JSON gets wrong on hostile feed text.
 *
 * Styling rides in the properties under the *simplestyle* keys (`stroke`, `stroke-width`), which
 * are what GitHub, geojson.io and Mapbox read; anything else ignores unknown properties, so the
 * file stays plain RFC 7946.
 */
fun writeGeoJson(
    out: Writer,
    journey: Journey,
    settings: ExportSettings,
    labels: ExportLabels,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    val legs = journey.exportedLegs(settings)

    val points = waypointsOf(journey, legs, settings, labels).map { waypoint ->
        feature(
            geometry = buildJsonObject {
                put("type", "Point")
                put("coordinates", position(waypoint.place.lat, waypoint.place.lon))
            },
            properties = buildJsonObject {
                put("name", waypoint.name)
                if (settings.includeDescriptions) {
                    waypoint.description?.let { put("description", it) }
                }
                if (settings.includeTimes) waypoint.time?.let { put("time", it.toExportTime()) }
                put("type", waypoint.type)
            },
        )
    }

    val lines = legs.pathLegs(settings).map { leg ->
        feature(
            geometry = buildJsonObject {
                put("type", "LineString")
                putJsonArray("coordinates") {
                    leg.path.forEach { point -> add(position(point.lat, point.lon)) }
                }
            },
            properties = buildJsonObject {
                put("name", leg.trackName(labels))
                if (settings.includeDescriptions) {
                    leg.description(labels)?.let { put("description", it) }
                }
                put("mode", leg.mode.name.lowercase(Locale.US))
                labels.legColor(leg)?.let { color ->
                    put("stroke", "#$color")
                    put("stroke-width", STROKE_WIDTH)
                }
            },
        )
    }

    val collection = buildJsonObject {
        put("type", "FeatureCollection")
        // Foreign members: RFC 7946 allows them and readers that don't know them skip them, which
        // is the only place the document's own name and generator can go.
        put("name", labels.documentName)
        putJsonObject("properties") {
            put("creator", labels.creator)
            put("time", now.toExportTime())
        }
        putJsonArray("features") {
            points.forEach { add(it) }
            lines.forEach { add(it) }
        }
    }

    out.write(JSON.encodeToString(JsonObject.serializer(), collection))
    out.write("\n")
    out.flush()
}

/** Readable when opened in an editor; the files are small enough that the bytes don't matter. */
private val JSON = Json { prettyPrint = true }

private const val STROKE_WIDTH = 4

private fun feature(geometry: JsonObject, properties: JsonObject): JsonObject = buildJsonObject {
    put("type", "Feature")
    put("geometry", geometry)
    put("properties", properties)
}

/**
 * A GeoJSON position: `[lon, lat]`, longitude first. Rounded rather than formatted because JSON
 * wants a number, not a string — six decimals is roughly 10 cm, finer than any transit feed's
 * geometry, and the rounding is what keeps `52.22866999999999` out of the file.
 */
private fun position(lat: Double, lon: Double): JsonArray = buildJsonArray {
    add(JsonPrimitive(lon.roundToCoordinate()))
    add(JsonPrimitive(lat.roundToCoordinate()))
}

private fun Double.roundToCoordinate(): Double = (this * 1_000_000).roundToLong() / 1_000_000.0
