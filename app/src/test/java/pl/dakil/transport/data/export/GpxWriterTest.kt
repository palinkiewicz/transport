package pl.dakil.transport.data.export

import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.GpxExportSettings
import pl.dakil.transport.domain.model.GpxFileName
import pl.dakil.transport.domain.model.IntermediateStop
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * Covers the parts of the export a reader would reject the file over: element order, UTC times,
 * locale-independent coordinates, and which points each setting puts in.
 */
class GpxWriterTest {

    private val labels = GpxLabels(
        documentName = "Home → Hotel",
        originName = "Home",
        destinationName = "Hotel",
        creator = "Transport test",
        accessLegName = { "Walk" },
        board = "Board",
        transfer = "Change at",
        alight = "Get off at",
        descSeparator = " · ",
        track = { "Pl. $it" },
        towards = { "towards $it" },
    )

    private val now = OffsetDateTime.of(2026, 8, 6, 8, 0, 0, 0, ZoneOffset.ofHours(2))

    private fun place(name: String, lat: Double, lon: Double, stopId: String? = null) =
        TransitLocation(name = name, lat = lat, lon = lon, stopId = stopId)

    private fun at(hour: Int, minute: Int) =
        OffsetDateTime.of(2026, 8, 6, hour, minute, 0, 0, ZoneOffset.ofHours(2))

    /**
     * START --walk--> Centralna --rail (1 intermediate)--> Airport --bus--> Terminal A --walk--> END.
     *
     * Covers every boundary kind at once: an origin the API refuses to name, a walk that feeds a
     * ride, a genuine vehicle-to-vehicle change, and a ride that ends in a walk.
     */
    private val journey: Journey = run {
        val home = place("START", 52.1, 21.0)
        val centralna = place("Warszawa Centralna", 52.228_67, 21.003_41, stopId = "pl:1")
        val airport = place("Lotnisko Chopina", 52.170_12, 20.973_45, stopId = "pl:2")
        val terminal = place("Terminal A", 52.171, 20.974, stopId = "pl:4")
        val end = place("END", 52.172, 20.975)
        Journey(
            id = "j1",
            duration = 3_600,
            startTime = at(8, 10),
            endTime = at(9, 10),
            transfers = 0,
            legs = listOf(
                JourneyLeg(
                    mode = TransportMode.WALK,
                    fromPlace = home,
                    toPlace = centralna,
                    startTime = at(8, 10),
                    endTime = at(8, 20),
                    scheduledStartTime = at(8, 10),
                    scheduledEndTime = at(8, 20),
                    realTime = false,
                    duration = 600,
                    path = listOf(GeoPoint(52.1, 21.0), GeoPoint(52.228_67, 21.003_41)),
                ),
                JourneyLeg(
                    mode = TransportMode.RAIL,
                    tripId = "trip-1",
                    fromPlace = centralna,
                    toPlace = airport,
                    fromTrack = "3",
                    startTime = at(8, 25),
                    endTime = at(8, 50),
                    // Two minutes late, so the real/scheduled switch has something to show.
                    scheduledStartTime = at(8, 23),
                    scheduledEndTime = at(8, 48),
                    realTime = true,
                    duration = 1_500,
                    headsign = "Lotnisko Chopina",
                    routeShortName = "S2",
                    agencyName = "Koleje Mazowieckie",
                    intermediateStops = listOf(
                        IntermediateStop(
                            place = place("Ochota", 52.21, 20.99, stopId = "pl:3"),
                            arrivalTime = at(8, 32),
                            scheduledArrivalTime = at(8, 30),
                        ),
                    ),
                    path = listOf(
                        GeoPoint(52.228_67, 21.003_41),
                        GeoPoint(52.21, 20.99),
                        GeoPoint(52.170_12, 20.973_45),
                    ),
                ),
                JourneyLeg(
                    mode = TransportMode.BUS,
                    tripId = "trip-2",
                    fromPlace = airport,
                    toPlace = terminal,
                    startTime = at(8, 55),
                    endTime = at(9, 5),
                    scheduledStartTime = at(8, 55),
                    scheduledEndTime = at(9, 5),
                    realTime = false,
                    duration = 600,
                    headsign = "Terminal A",
                    routeShortName = "188",
                    path = listOf(GeoPoint(52.170_12, 20.973_45), GeoPoint(52.171, 20.974)),
                ),
                JourneyLeg(
                    mode = TransportMode.WALK,
                    fromPlace = terminal,
                    toPlace = end,
                    startTime = at(9, 5),
                    endTime = at(9, 10),
                    scheduledStartTime = at(9, 5),
                    scheduledEndTime = at(9, 10),
                    realTime = false,
                    duration = 300,
                    // No geometry: this leg must contribute a waypoint but no track.
                    path = emptyList(),
                ),
            ),
        )
    }

    private fun export(settings: GpxExportSettings = GpxExportSettings()): String =
        StringWriter().also { writeGpx(it, journey, settings, labels, now) }.toString()

    private fun String.occurrencesOf(needle: String) = split(needle).size - 1

    @Test
    fun `each boundary is labelled by what actually happens there`() {
        val gpx = export()
        // Origin, walk→rail, rail→bus, bus→walk, destination.
        assertEquals(5, gpx.occurrencesOf("<wpt "))
        assertEquals(listOf("board", "board", "transfer", "alight", "alight"), gpx.typesOf())
        assertTrue(gpx.contains("<name>Board Warszawa Centralna</name>"))
        // Only a change between two vehicles is a transfer; getting off to walk is not.
        assertTrue(gpx.contains("<name>Change at Lotnisko Chopina</name>"))
        assertTrue(gpx.contains("<name>Get off at Terminal A</name>"))
    }

    @Test
    fun `the journey's own endpoints take the searched names, not the API placeholders`() {
        val gpx = export()
        assertTrue(gpx.contains("<name>Board Home</name>"))
        assertTrue(gpx.contains("<name>Get off at Hotel</name>"))
        assertFalse(gpx.contains("START"))
        assertFalse(gpx.contains("END"))
    }

    @Test
    fun `intermediate stops are opt-in`() {
        assertFalse(export().contains("Ochota"))
        val withStops = export(GpxExportSettings(includeIntermediateStops = true))
        assertEquals(6, withStops.occurrencesOf("<wpt "))
        assertTrue(withStops.contains("<name>Ochota</name>"))
        assertTrue(withStops.contains("<type>stop</type>"))
    }

    @Test
    fun `dropping access legs leaves only the rides, keeping their real stop names`() {
        val gpx = export(GpxExportSettings(includeAccessLegs = false))
        assertEquals(3, gpx.occurrencesOf("<wpt "))
        assertEquals(2, gpx.occurrencesOf("<trk>"))
        // The rides no longer start at the journey's origin, so no name may be overridden.
        assertFalse(gpx.contains("Board Home"))
        assertFalse(gpx.contains("Get off at Hotel"))
        assertTrue(gpx.contains("<name>Board Warszawa Centralna</name>"))
        assertTrue(gpx.contains("<name>Get off at Terminal A</name>"))
    }

    @Test
    fun `tracks come from leg geometry and skip legs without it`() {
        val gpx = export()
        // The final walk has no path, so three of the four legs produce a track.
        assertEquals(3, gpx.occurrencesOf("<trk>"))
        assertEquals(3, gpx.occurrencesOf("<trkseg>"))
        assertEquals(7, gpx.occurrencesOf("<trkpt "))
        assertTrue(gpx.contains("<name>S2 towards Lotnisko Chopina</name>"))
        assertTrue(gpx.contains("<name>Walk</name>"))
        assertTrue(gpx.contains("<type>rail</type>"))
    }

    @Test
    fun `tracks can be turned off entirely`() {
        val gpx = export(GpxExportSettings(includeTracks = false))
        assertEquals(0, gpx.occurrencesOf("<trk>"))
        assertEquals(5, gpx.occurrencesOf("<wpt "))
    }

    @Test
    fun `times are UTC and follow the real-time setting`() {
        // 08:25+02:00 is 06:25Z.
        assertTrue(export().contains("<time>2026-08-06T06:25:00Z</time>"))
        val scheduled = export(GpxExportSettings(useRealTimes = false))
        assertTrue(scheduled.contains("<time>2026-08-06T06:23:00Z</time>"))
        assertFalse(scheduled.contains("<time>2026-08-06T06:25:00Z</time>"))
    }

    @Test
    fun `times can be dropped without touching the waypoints`() {
        val gpx = export(GpxExportSettings(includeTimes = false))
        assertEquals(5, gpx.occurrencesOf("<wpt "))
        // Only the one in <metadata> survives.
        assertEquals(1, gpx.occurrencesOf("<time>"))
    }

    @Test
    fun `coordinates use a dot regardless of the default locale`() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pl-PL"))
        try {
            assertTrue(export().contains("lat=\"52.228670\" lon=\"21.003410\""))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `descriptions carry line, direction, platform and operator, and are optional`() {
        assertTrue(
            export().contains("<desc>S2 · towards Lotnisko Chopina · Pl. 3 · Koleje Mazowieckie</desc>"),
        )
        assertFalse(export(GpxExportSettings(includeDescriptions = false)).contains("<desc>"))
    }

    @Test
    fun `waypoint children follow the GPX 1_1 schema order`() {
        val wpt = export().substringAfter("<wpt ").substringBefore("</wpt>")
        val order = listOf("<time>", "<name>", "<desc>", "<type>")
            .mapNotNull { tag -> wpt.indexOf(tag).takeIf { it >= 0 } }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `feed text is escaped rather than breaking the document`() {
        val hostile = journey.copy(
            legs = listOf(
                journey.legs[1].copy(
                    routeShortName = "A&B",
                    headsign = "<script>",
                    agencyName = null,
                    fromTrack = null,
                ),
            ),
        )
        val gpx = StringWriter()
            .also { writeGpx(it, hostile, GpxExportSettings(), labels, now) }
            .toString()
        assertTrue(gpx.contains("A&amp;B · towards &lt;script&gt;"))
        assertFalse(gpx.contains("<script>"))
    }

    @Test
    fun `file names are slugged and follow the chosen mode`() {
        assertEquals(
            "home-airport-2026-08-06.gpx",
            gpxFileName(journey, GpxFileName.ROUTE_AND_DATE, "Home", "Airport"),
        )
        assertEquals(
            "warszawa-centralna-lotnisko-chopina-2026-08-06.gpx",
            gpxFileName(journey, GpxFileName.ROUTE_AND_DATE, "Warszawa Centralna", "Lotnisko Chopina!"),
        )
        assertEquals(
            "itinerary-20260806-0825.gpx",
            gpxFileName(journey, GpxFileName.DATE_TIME, "Home", "Airport"),
        )
        assertEquals("itinerary.gpx", gpxFileName(journey, GpxFileName.PLAIN, "Home", "Airport"))
    }

    private fun String.typesOf(): List<String> = Regex("<type>([a-z]+)</type>")
        .findAll(substringBefore("<trk>"))
        .map { it.groupValues[1] }
        .toList()
}
