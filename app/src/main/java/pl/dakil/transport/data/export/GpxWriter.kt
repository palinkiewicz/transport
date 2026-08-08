package pl.dakil.transport.data.export

import java.io.Writer
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import pl.dakil.transport.domain.model.GpxExportSettings
import pl.dakil.transport.domain.model.GpxFileName
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation

/**
 * What the exported file needs from the layer above: its app-authored text, and the colours the
 * itinerary was actually drawn in. Both are passed in rather than resolved here because this is
 * not the Compose layer — it can reach neither resources nor the line-colour setting, the same
 * reasoning as [TransitLocation.currentPosition].
 */
data class GpxLabels(
    /** `<metadata><name>`, e.g. "Warszawa Centralna → Lotnisko Chopina". */
    val documentName: String,
    /**
     * What the user searched for at each end. The plan API labels the journey's outer endpoints
     * `START`/`END` rather than naming them, so — exactly as the itinerary list does — those two
     * names are overridden by what was actually searched for.
     */
    val originName: String,
    val destinationName: String,
    /** The `creator` attribute, e.g. "Transport 1.0". */
    val creator: String,
    /** `<trk><name>` for a leg that isn't a ride — the mode's own name ("Walk", "Cycle", …). */
    val accessLegName: (JourneyLeg) -> String,
    /** `<wpt><name>` prefix where the journey boards. */
    val board: String,
    /** …where it changes vehicle. */
    val transfer: String,
    /** …and where it gets off for good. */
    val alight: String,
    /** Joins the parts of a `<desc>`: line, headsign, platform, operator. */
    val descSeparator: String,
    /** Renders a platform/track as text, e.g. "platform 3". */
    val track: (String) -> String,
    /** Renders a headsign as text, e.g. "towards Lotnisko Chopina". */
    val towards: (String) -> String,
    /**
     * The colour the leg is drawn in on screen, as GTFS-shaped `RRGGBB`, or null to leave the
     * track uncoloured. Legs are matched by identity — the writer only ever sees the instances it
     * was handed — so this reads a resolution made once above rather than repeating it.
     */
    val legColor: (JourneyLeg) -> String? = { null },
)

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
    settings: GpxExportSettings,
    labels: GpxLabels,
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
    out.tag(2, "time", now.toGpxTime())
    out.write("  </metadata>\n")

    val legs = journey.exportedLegs(settings)

    waypointsOf(journey, legs, settings, labels).forEach { waypoint ->
        out.write(
            "  <wpt lat=\"${waypoint.place.lat.toGpxCoordinate()}\" " +
                "lon=\"${waypoint.place.lon.toGpxCoordinate()}\">\n",
        )
        if (settings.includeTimes) waypoint.time?.let { out.tag(2, "time", it.toGpxTime()) }
        out.tag(2, "name", waypoint.name)
        if (settings.includeDescriptions) waypoint.description?.let { out.tag(2, "desc", it) }
        out.tag(2, "type", waypoint.type)
        out.write("  </wpt>\n")
    }

    if (settings.includeTracks) {
        legs.filter { it.path.size >= 2 }.forEach { leg ->
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
                    "      <trkpt lat=\"${point.lat.toGpxCoordinate()}\" " +
                        "lon=\"${point.lon.toGpxCoordinate()}\"/>\n",
                )
            }
            out.write("    </trkseg>\n")
            out.write("  </trk>\n")
        }
    }

    out.write("</gpx>\n")
    out.flush()
}

/**
 * The suggested filename, extension included. Slugged to lowercase ASCII so it survives every
 * filesystem and every share target that echoes it back.
 */
fun gpxFileName(
    journey: Journey,
    mode: GpxFileName,
    fromName: String,
    toName: String,
): String = when (mode) {
    GpxFileName.ROUTE_AND_DATE -> {
        val date = journey.departureTime.format(DATE_PATTERN)
        val route = listOf(fromName, toName).map { it.slug() }.filter { it.isNotEmpty() }
        if (route.isEmpty()) "itinerary-$date.gpx" else "${route.joinToString("-")}-$date.gpx"
    }
    GpxFileName.DATE_TIME -> "itinerary-${journey.departureTime.format(DATE_TIME_PATTERN)}.gpx"
    GpxFileName.PLAIN -> "itinerary.gpx"
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

private val DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
private val DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US)

/** Cap per name, so two long stop names can't build a filename the filesystem refuses. */
private const val SLUG_MAX_LENGTH = 32

private fun String.slug(): String = lowercase(Locale.US)
    .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
    .joinToString("")
    .replace(Regex("-+"), "-")
    .trim('-')
    .take(SLUG_MAX_LENGTH)
    .trim('-')

/**
 * ISO-8601 in UTC, the only form GPX 1.1 accepts. A machine format, never localized — the same
 * call as `MapViewModel.formatCoordinates` staying on [Locale.US].
 */
private fun OffsetDateTime.toGpxTime(): String = withOffsetSameInstant(ZoneOffset.UTC)
    // Whole seconds: sub-second precision on a timetable would only be noise.
    .truncatedTo(ChronoUnit.SECONDS)
    .format(DateTimeFormatter.ISO_INSTANT)

/** Six decimals is roughly 10 cm — finer than any transit feed's geometry. */
private fun Double.toGpxCoordinate(): String = String.format(Locale.US, "%.6f", this)

/** The legs that end up in the file: all of them, or only the rides. */
private fun Journey.exportedLegs(settings: GpxExportSettings): List<JourneyLeg> =
    if (settings.includeAccessLegs) legs else legs.filter { it.isTransit }

/** One `<wpt>` to write, already resolved down to text. */
private data class GpxWaypoint(
    val place: TransitLocation,
    val name: String,
    val time: OffsetDateTime?,
    val description: String?,
    /** GPX `<type>`: a machine-facing category, not display text. */
    val type: String,
)

/**
 * The places the traveller acts at, in order. A leg's arrival and the next leg's departure at the
 * same place collapse into one waypoint rather than two pins on the same spot; where they differ —
 * a change that walks between two stops — both survive, because they really are two places.
 *
 * What a waypoint *is* comes from the legs either side of it, not from the merge: getting off a
 * train to walk is an alight even though the walk continues from the same place, and only a change
 * between two vehicles is a transfer.
 */
private fun waypointsOf(
    journey: Journey,
    legs: List<JourneyLeg>,
    settings: GpxExportSettings,
    labels: GpxLabels,
): List<GpxWaypoint> {
    if (legs.isEmpty()) return emptyList()
    val result = mutableListOf<GpxWaypoint>()

    /** A place, with the leg arriving at it and the leg leaving it — either may be absent. */
    fun boundary(arriving: JourneyLeg?, leaving: JourneyLeg?): GpxWaypoint {
        val type = when {
            arriving == null -> "board"
            leaving == null -> "alight"
            arriving.isTransit && leaving.isTransit -> "transfer"
            leaving.isTransit -> "board"
            else -> "alight"
        }
        val prefix = when (type) {
            "board" -> labels.board
            "transfer" -> labels.transfer
            else -> labels.alight
        }
        // An alight is about when you get there; anything else is about when you leave again.
        val useArrival = arriving != null && (type == "alight" || leaving == null)
        val name = when {
            // The API labels the journey's own endpoints "START"/"END" rather than naming them.
            arriving == null && leaving === journey.legs.first() -> labels.originName
            leaving == null && arriving === journey.legs.last() -> labels.destinationName
            useArrival -> arriving!!.toName
            else -> leaving!!.fromName
        }
        // Described by whichever neighbouring leg is a ride — a walk has nothing to say here.
        val describing = listOfNotNull(leaving, arriving).firstOrNull { it.isTransit }
        return GpxWaypoint(
            place = if (useArrival) arriving!!.toPlace else leaving!!.fromPlace,
            name = "$prefix $name",
            time = if (useArrival) {
                if (settings.useRealTimes) arriving!!.endTime else arriving!!.scheduledEndTime
            } else {
                if (settings.useRealTimes) leaving!!.startTime else leaving!!.scheduledStartTime
            },
            description = describing?.description(
                labels,
                track = if (describing === arriving) describing.toTrack else describing.fromTrack,
            ),
            type = type,
        )
    }

    result += boundary(arriving = null, leaving = legs.first())
    legs.forEachIndexed { index, leg ->
        if (settings.includeIntermediateStops) {
            leg.intermediateStops.forEach { stop ->
                result += GpxWaypoint(
                    place = stop.place,
                    name = stop.name,
                    time = if (settings.useRealTimes) {
                        stop.arrivalTime ?: stop.scheduledArrivalTime
                    } else {
                        stop.scheduledArrivalTime ?: stop.arrivalTime
                    },
                    description = leg.description(labels, track = stop.track),
                    type = "stop",
                )
            }
        }
        val next = legs.getOrNull(index + 1)
        when {
            next == null -> result += boundary(arriving = leg, leaving = null)
            leg.toPlace.isSamePlaceAs(next.fromPlace) -> result += boundary(leg, next)
            else -> {
                result += boundary(arriving = leg, leaving = null)
                result += boundary(arriving = null, leaving = next)
            }
        }
    }
    return result
}

/** `<trk><name>`: the line and where it is heading, or what the traveller does on an access leg. */
private fun JourneyLeg.trackName(labels: GpxLabels): String = if (isTransit) {
    headsign?.let { "$lineLabel ${labels.towards(it)}" } ?: lineLabel
} else {
    labels.accessLegName(this)
}

/** `<desc>`: line, headsign, platform and operator, whichever of them the feed provides. */
private fun JourneyLeg.description(labels: GpxLabels, track: String? = null): String? {
    if (!isTransit) return null
    val parts = listOfNotNull(
        routeLongName ?: routeShortName ?: displayName,
        headsign?.let { labels.towards(it) },
        track?.let { labels.track(it) },
        agencyName,
    )
    return parts.ifEmpty { null }?.joinToString(labels.descSeparator)
}

private fun Writer.tag(indentLevel: Int, name: String, value: String) {
    write("  ".repeat(indentLevel + 1))
    write("<$name>${value.escapeXml()}</$name>\n")
}

/**
 * Escapes the five XML entities and drops the control characters XML 1.0 forbids outright — stop
 * names and headsigns come from third-party feeds, so neither is guaranteed clean.
 */
private fun String.escapeXml(): String = buildString(length) {
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
