package pl.dakil.transport.data.export

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import pl.dakil.transport.domain.model.ExportFileName
import pl.dakil.transport.domain.model.ExportFormat
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation

/**
 * What an exported file needs from the layer above: its app-authored text, and the colours the
 * itinerary was actually drawn in. Both are passed in rather than resolved here because this is
 * not the Compose layer — it can reach neither resources nor the line-colour setting, the same
 * reasoning as [TransitLocation.currentPosition].
 *
 * One set of labels serves every [ExportFormat]: the three files differ in syntax, not in what
 * they say.
 */
data class ExportLabels(
    /** The document's own name, e.g. "Warszawa Centralna → Lotnisko Chopina". */
    val documentName: String,
    /**
     * What the user searched for at each end. The plan API labels the journey's outer endpoints
     * `START`/`END` rather than naming them, so — exactly as the itinerary list does — those two
     * names are overridden by what was actually searched for.
     */
    val originName: String,
    val destinationName: String,
    /** The generating application, e.g. "Transport 1.0". */
    val creator: String,
    /** The name of a leg that isn't a ride — the mode's own name ("Walk", "Cycle", …). */
    val accessLegName: (JourneyLeg) -> String,
    /** Waypoint name prefix where the journey boards. */
    val board: String,
    /** …where it changes vehicle. */
    val transfer: String,
    /** …and where it gets off for good. */
    val alight: String,
    /** Joins the parts of a description: line, headsign, platform, operator. */
    val descSeparator: String,
    /** Renders a platform/track as text, e.g. "platform 3". */
    val track: (String) -> String,
    /** Renders a headsign as text, e.g. "towards Lotnisko Chopina". */
    val towards: (String) -> String,
    /** Groups the waypoints in formats that have folders (KML). */
    val stopsFolder: String = "Stops",
    /** …and the paths. */
    val routeFolder: String = "Route",
    /**
     * The colour the leg is drawn in on screen, as GTFS-shaped `RRGGBB`, or null to leave the
     * path uncoloured. Legs are matched by identity — the writer only ever sees the instances it
     * was handed — so this reads a resolution made once above rather than repeating it.
     */
    val legColor: (JourneyLeg) -> String? = { null },
)

/**
 * The suggested filename, extension included. Slugged to lowercase ASCII so it survives every
 * filesystem and every share target that echoes it back.
 */
fun exportFileName(
    journey: Journey,
    mode: ExportFileName,
    format: ExportFormat,
    fromName: String,
    toName: String,
): String {
    val extension = format.extension
    return when (mode) {
        ExportFileName.ROUTE_AND_DATE -> {
            val date = journey.departureTime.format(DATE_PATTERN)
            val route = listOf(fromName, toName).map { it.slug() }.filter { it.isNotEmpty() }
            if (route.isEmpty()) {
                "itinerary-$date.$extension"
            } else {
                "${route.joinToString("-")}-$date.$extension"
            }
        }
        ExportFileName.DATE_TIME ->
            "itinerary-${journey.departureTime.format(DATE_TIME_PATTERN)}.$extension"
        ExportFileName.PLAIN -> "itinerary.$extension"
    }
}

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
 * ISO-8601 in UTC — the only form GPX and KML accept, and the one RFC 3339 form GeoJSON consumers
 * expect. A machine format, never localized: the same call as `MapViewModel.formatCoordinates`
 * staying on [Locale.US].
 */
internal fun OffsetDateTime.toExportTime(): String = withOffsetSameInstant(ZoneOffset.UTC)
    // Whole seconds: sub-second precision on a timetable would only be noise.
    .truncatedTo(ChronoUnit.SECONDS)
    .format(DateTimeFormatter.ISO_INSTANT)

/** Six decimals is roughly 10 cm — finer than any transit feed's geometry. */
internal fun Double.toExportCoordinate(): String = String.format(Locale.US, "%.6f", this)

/** The legs that end up in the file: all of them, or only the rides. */
internal fun Journey.exportedLegs(settings: ExportSettings): List<JourneyLeg> =
    if (settings.includeAccessLegs) legs else legs.filter { it.isTransit }

/** The legs that contribute a drawn path — geometry, and the setting that allows it. */
internal fun List<JourneyLeg>.pathLegs(settings: ExportSettings): List<JourneyLeg> =
    if (settings.includeTracks) filter { it.path.size >= 2 } else emptyList()

/** One point of interest to write, already resolved down to text. */
internal data class ExportWaypoint(
    val place: TransitLocation,
    val name: String,
    val time: OffsetDateTime?,
    val description: String?,
    /** A machine-facing category — `board`, `transfer`, `alight` or `stop` — not display text. */
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
internal fun waypointsOf(
    journey: Journey,
    legs: List<JourneyLeg>,
    settings: ExportSettings,
    labels: ExportLabels,
): List<ExportWaypoint> {
    if (legs.isEmpty()) return emptyList()
    val result = mutableListOf<ExportWaypoint>()

    /** A place, with the leg arriving at it and the leg leaving it — either may be absent. */
    fun boundary(arriving: JourneyLeg?, leaving: JourneyLeg?): ExportWaypoint {
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
        return ExportWaypoint(
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
                result += ExportWaypoint(
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

/** A path's name: the line and where it is heading, or what the traveller does on an access leg. */
internal fun JourneyLeg.trackName(labels: ExportLabels): String = if (isTransit) {
    headsign?.let { "$lineLabel ${labels.towards(it)}" } ?: lineLabel
} else {
    labels.accessLegName(this)
}

/** Line, headsign, platform and operator, whichever of them the feed provides. */
internal fun JourneyLeg.description(labels: ExportLabels, track: String? = null): String? {
    if (!isTransit) return null
    val parts = listOfNotNull(
        routeLongName ?: routeShortName ?: displayName,
        headsign?.let { labels.towards(it) },
        track?.let { labels.track(it) },
        agencyName,
    )
    return parts.ifEmpty { null }?.joinToString(labels.descSeparator)
}
