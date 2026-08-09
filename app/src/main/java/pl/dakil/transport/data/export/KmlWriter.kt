package pl.dakil.transport.data.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.OffsetDateTime
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg

/**
 * Writes [journey] to [out] as a KMZ: a zip archive holding a single `doc.kml`.
 *
 * KMZ rather than bare KML because it is what Google Earth and My Maps hand out and expect back,
 * and because a zipped itinerary is a fraction of the size once a long ride's geometry is in it.
 * The archive deliberately holds nothing but `doc.kml` — every icon and colour the file uses is
 * defined inline, so there is no second entry to keep in step.
 */
fun writeKmz(
    out: OutputStream,
    journey: Journey,
    settings: ExportSettings,
    labels: ExportLabels,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    val zip = ZipOutputStream(out)
    // The reader looks for the archive's first .kml file; naming it doc.kml is the convention
    // every KMZ consumer recognises without looking.
    zip.putNextEntry(ZipEntry("doc.kml"))
    val writer = OutputStreamWriter(zip, Charsets.UTF_8)
    writeKml(writer, journey, settings, labels, now)
    writer.flush()
    zip.closeEntry()
    zip.finish()
    zip.flush()
}

/**
 * Writes [journey] to [out] as KML 2.2 — the payload of [writeKmz], separate so it can be read and
 * tested as text.
 *
 * The waypoints and paths are the same ones GPX gets, arranged into two folders because that is
 * how a KML reader offers layers to switch off. Element order follows `AbstractFeatureType`'s
 * sequence (name, description, TimeStamp, styleUrl, ExtendedData, then the geometry); Google Earth
 * tolerates other orders but validating readers do not. Coordinates are `lon,lat` — KML's order,
 * the reverse of GPX's attributes.
 */
fun writeKml(
    out: Writer,
    journey: Journey,
    settings: ExportSettings,
    labels: ExportLabels,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    val legs = journey.exportedLegs(settings)
    val pathLegs = legs.pathLegs(settings)

    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
    out.write("  <Document>\n")
    out.tag(1, "name", labels.documentName)

    // One style per coloured path, so the file opens in the colours the itinerary was seen in.
    pathLegs.forEachIndexed { index, leg ->
        val color = labels.legColor(leg) ?: return@forEachIndexed
        out.write("    <Style id=\"${leg.styleId(index)}\">\n")
        out.write("      <LineStyle>\n")
        out.tag(3, "color", color.toKmlColor())
        out.tag(3, "width", LINE_WIDTH)
        out.write("      </LineStyle>\n")
        out.write("    </Style>\n")
    }

    // KML has no metadata block of its own; what GPX puts in <metadata> goes here.
    out.write("    <ExtendedData>\n")
    out.data(2, "creator", labels.creator)
    out.data(2, "time", now.toExportTime())
    out.write("    </ExtendedData>\n")

    val waypoints = waypointsOf(journey, legs, settings, labels)
    if (waypoints.isNotEmpty()) {
        out.write("    <Folder>\n")
        out.tag(2, "name", labels.stopsFolder)
        waypoints.forEach { waypoint ->
            out.write("      <Placemark>\n")
            out.tag(3, "name", waypoint.name)
            if (settings.includeDescriptions) {
                waypoint.description?.let { out.tag(3, "description", it) }
            }
            if (settings.includeTimes) {
                waypoint.time?.let {
                    out.write("        <TimeStamp>\n")
                    out.tag(4, "when", it.toExportTime())
                    out.write("        </TimeStamp>\n")
                }
            }
            out.write("        <ExtendedData>\n")
            out.data(4, "type", waypoint.type)
            out.write("        </ExtendedData>\n")
            out.write("        <Point>\n")
            out.tag(4, "coordinates", coordinate(waypoint.place.lat, waypoint.place.lon))
            out.write("        </Point>\n")
            out.write("      </Placemark>\n")
        }
        out.write("    </Folder>\n")
    }

    if (pathLegs.isNotEmpty()) {
        out.write("    <Folder>\n")
        out.tag(2, "name", labels.routeFolder)
        pathLegs.forEachIndexed { index, leg ->
            out.write("      <Placemark>\n")
            out.tag(3, "name", leg.trackName(labels))
            if (settings.includeDescriptions) {
                leg.description(labels)?.let { out.tag(3, "description", it) }
            }
            if (labels.legColor(leg) != null) out.tag(3, "styleUrl", "#${leg.styleId(index)}")
            out.write("        <ExtendedData>\n")
            out.data(4, "mode", leg.mode.name.lowercase(Locale.US))
            out.write("        </ExtendedData>\n")
            out.write("        <LineString>\n")
            // Transit geometry follows the ground; without this a line jumps straight between
            // points and cuts through terrain in the 3-D readers.
            out.tag(4, "tessellate", "1")
            out.write("          <coordinates>\n")
            leg.path.forEach { point ->
                out.write("            ${coordinate(point.lat, point.lon)}\n")
            }
            out.write("          </coordinates>\n")
            out.write("        </LineString>\n")
            out.write("      </Placemark>\n")
        }
        out.write("    </Folder>\n")
    }

    out.write("  </Document>\n")
    out.write("</kml>\n")
    out.flush()
}

private const val LINE_WIDTH = "4"

/** Unique per drawn path: the same line ridden twice still gets its own position's colour. */
private fun JourneyLeg.styleId(index: Int): String = "leg-$index"

/** KML wants `lon,lat` — the reverse of every other format here, and a silent bug if mixed up. */
private fun coordinate(lat: Double, lon: Double): String =
    "${lon.toExportCoordinate()},${lat.toExportCoordinate()}"

/**
 * KML colours are `aabbggrr` — alpha first and the channels reversed — where every other format
 * here speaks GTFS's `rrggbb`. Anything that isn't six hex digits is left to the caller's default
 * rather than producing a colour nobody asked for.
 */
private fun String.toKmlColor(): String {
    if (length != 6 || any { it.digitToIntOrNull(16) == null }) return "ff000000"
    return "ff${substring(4, 6)}${substring(2, 4)}${substring(0, 2)}".lowercase(Locale.US)
}

/** One `<Data name="…"><value>…</value></Data>` pair. */
private fun Writer.data(indentLevel: Int, name: String, value: String) {
    write("  ".repeat(indentLevel + 1))
    write("<Data name=\"${name.escapeXml()}\"><value>${value.escapeXml()}</value></Data>\n")
}
