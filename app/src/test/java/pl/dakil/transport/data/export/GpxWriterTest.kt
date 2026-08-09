package pl.dakil.transport.data.export

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.domain.model.ExportSettings

/**
 * Covers the parts of the export a reader would reject the file over: element order, UTC times,
 * locale-independent coordinates, and which points each setting puts in.
 */
class GpxWriterTest {

    private val labels = ExportFixtures.labels
    private val now = ExportFixtures.now
    private val journey = ExportFixtures.journey

    private fun export(settings: ExportSettings = ExportSettings()): String =
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
        val withStops = export(ExportSettings(includeIntermediateStops = true))
        assertEquals(6, withStops.occurrencesOf("<wpt "))
        assertTrue(withStops.contains("<name>Ochota</name>"))
        assertTrue(withStops.contains("<type>stop</type>"))
    }

    @Test
    fun `dropping access legs leaves only the rides, keeping their real stop names`() {
        val gpx = export(ExportSettings(includeAccessLegs = false))
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
        val gpx = export(ExportSettings(includeTracks = false))
        assertEquals(0, gpx.occurrencesOf("<trk>"))
        assertEquals(5, gpx.occurrencesOf("<wpt "))
    }

    @Test
    fun `times are UTC and follow the real-time setting`() {
        // 08:25+02:00 is 06:25Z.
        assertTrue(export().contains("<time>2026-08-06T06:25:00Z</time>"))
        val scheduled = export(ExportSettings(useRealTimes = false))
        assertTrue(scheduled.contains("<time>2026-08-06T06:23:00Z</time>"))
        assertFalse(scheduled.contains("<time>2026-08-06T06:25:00Z</time>"))
    }

    @Test
    fun `times can be dropped without touching the waypoints`() {
        val gpx = export(ExportSettings(includeTimes = false))
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
        assertFalse(export(ExportSettings(includeDescriptions = false)).contains("<desc>"))
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
            .also { writeGpx(it, hostile, ExportSettings(), labels, now) }
            .toString()
        assertTrue(gpx.contains("A&amp;B · towards &lt;script&gt;"))
        assertFalse(gpx.contains("<script>"))
    }

    @Test
    fun `leg colours ride in the gpx_style extension`() {
        val gpx = StringWriter()
            .also { writeGpx(it, journey, ExportSettings(), ExportFixtures.colouredLabels, now) }
            .toString()
        // One per drawn track, and the GTFS hex written through untouched.
        assertEquals(3, gpx.occurrencesOf("<gpx_style:color>E1251B</gpx_style:color>"))
    }

    private fun String.typesOf(): List<String> = Regex("<type>([a-z]+)</type>")
        .findAll(substringBefore("<trk>"))
        .map { it.groupValues[1] }
        .toList()
}
