package pl.dakil.transport.data.export

import java.io.Writer
import java.time.OffsetDateTime
import java.util.Locale
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey

/**
 * Writes [journey] to [out] as GPX 1.1.
 *
 * Waypoints carry the places the traveller acts at (and, optionally, the stops passed through);
 * each leg's decoded geometry becomes one `<trk>`. Note there are deliberately **no `<time>`
 * stamps on track points**: the API serves stop-to-stop schedule, not positions, so a per-point
 * time would be invented — the same "this is not GPS" line the map holds.
 *
 * Child element order follows the GPX 1.1 schema (`wpt`: time, name, desc, type; `trk`: name,
 * desc, type, trkseg), because validating readers reject anything else. The XML is written by
 * hand rather than through `android.util.Xml`: that keeps this file plain Kotlin, so it runs — and
 * is tested — on the JVM without an emulator.
 */
fun writeGpx(
    out: Writer,
    journey: Journey,
    settings: ExportSettings,
    labels: ExportLabels,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.write(
        "<gpx version=\"1.1\" creator=\"${labels.creator.escapeXml()}\" " +
            "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
            "xmlns:$STYLE_PREFIX=\"$STYLE_NAMESPACE\">\n",
    )

    out.write("  <metadata>\n")
    out.tag(2, "name", labels.documentName)
    out.tag(2, "time", now.toExportTime())
    out.write("  </metadata>\n")

    val legs = journey.exportedLegs(settings)

    waypointsOf(journey, legs, settings, labels).forEach { waypoint ->
        out.write(
            "  <wpt lat=\"${waypoint.place.lat.toExportCoordinate()}\" " +
                "lon=\"${waypoint.place.lon.toExportCoordinate()}\">\n",
        )
        if (settings.includeTimes) waypoint.time?.let { out.tag(2, "time", it.toExportTime()) }
        out.tag(2, "name", waypoint.name)
        if (settings.includeDescriptions) waypoint.description?.let { out.tag(2, "desc", it) }
        out.tag(2, "type", waypoint.type)
        out.write("  </wpt>\n")
    }

    legs.pathLegs(settings).forEach { leg ->
        out.write("  <trk>\n")
        out.tag(2, "name", leg.trackName(labels))
        if (settings.includeDescriptions) {
            leg.description(labels)?.let { out.tag(2, "desc", it) }
        }
        out.tag(2, "type", leg.mode.name.lowercase(Locale.US))
        labels.legColor(leg)?.let { color ->
            out.write("    <extensions>\n")
            out.write("      <$STYLE_PREFIX:line>\n")
            out.tag(3, "$STYLE_PREFIX:color", color)
            out.write("      </$STYLE_PREFIX:line>\n")
            out.write("    </extensions>\n")
        }
        out.write("    <trkseg>\n")
        leg.path.forEach { point ->
            out.write(
                "      <trkpt lat=\"${point.lat.toExportCoordinate()}\" " +
                    "lon=\"${point.lon.toExportCoordinate()}\"/>\n",
            )
        }
        out.write("    </trkseg>\n")
        out.write("  </trk>\n")
    }

    out.write("</gpx>\n")
    out.flush()
}

/**
 * GPX 1.1 has no colour of its own, so per-track colours ride in `<extensions>` under the GPX
 * Style extension — the one form the common readers (OsmAnd, Locus, GPX Studio) agree on. Garmin's
 * `gpxx:DisplayColor` was the alternative and is not usable here: it only accepts a fixed set of
 * colour *names*, which cannot carry an operator's own hex. Readers that know neither skip the
 * element, so the file stays valid GPX 1.1 either way.
 */
private const val STYLE_PREFIX = "gpx_style"
private const val STYLE_NAMESPACE = "http://www.topografix.com/GPX/gpx_style/0/2"

internal fun Writer.tag(indentLevel: Int, name: String, value: String) {
    write("  ".repeat(indentLevel + 1))
    write("<$name>${value.escapeXml()}</$name>\n")
}

/**
 * Escapes the five XML entities and drops the control characters XML 1.0 forbids outright — stop
 * names and headsigns come from third-party feeds, so neither is guaranteed clean.
 */
internal fun String.escapeXml(): String = buildString(length) {
    this@escapeXml.forEach { char ->
        when {
            char == '&' -> append("&amp;")
            char == '<' -> append("&lt;")
            char == '>' -> append("&gt;")
            char == '"' -> append("&quot;")
            char == '\'' -> append("&apos;")
            char == '\t' || char == '\n' || char == '\r' -> append(char)
            char.code < 0x20 -> Unit
            else -> append(char)
        }
    }
}
