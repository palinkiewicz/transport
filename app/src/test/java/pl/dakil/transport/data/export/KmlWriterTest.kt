package pl.dakil.transport.data.export

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.domain.model.ExportSettings

/**
 * Covers what a KML reader would reject or silently mis-draw: coordinate order, colour encoding,
 * schema element order, and the archive actually being a zip with a `doc.kml` in it.
 */
class KmlWriterTest {

    private val labels = ExportFixtures.colouredLabels
    private val now = ExportFixtures.now
    private val journey = ExportFixtures.journey

    private fun export(settings: ExportSettings = ExportSettings()): String =
        StringWriter().also { writeKml(it, journey, settings, labels, now) }.toString()

    private fun String.occurrencesOf(needle: String) = split(needle).size - 1

    @Test
    fun `waypoints and paths land in their own folders`() {
        val kml = export()
        assertEquals(2, kml.occurrencesOf("<Folder>"))
        assertTrue(kml.contains("<name>Stops</name>"))
        assertTrue(kml.contains("<name>Route</name>"))
        // Five boundaries and three drawn legs, the same as GPX gets.
        assertEquals(5, kml.occurrencesOf("<Point>"))
        assertEquals(3, kml.occurrencesOf("<LineString>"))
    }

    @Test
    fun `coordinates are lon,lat — the reverse of every other format here`() {
        assertTrue(export().contains("<coordinates>21.003410,52.228670</coordinates>"))
    }

    @Test
    fun `colours are converted to KML's alpha-first, reversed-channel form`() {
        val kml = export()
        // E1251B (GTFS rrggbb) becomes ff1b25e1 (aabbggrr).
        assertEquals(3, kml.occurrencesOf("<color>ff1b25e1</color>"))
        assertEquals(3, kml.occurrencesOf("<styleUrl>#leg-"))
    }

    @Test
    fun `an uncoloured journey gets no styles to point at`() {
        val plain = StringWriter()
            .also { writeKml(it, journey, ExportSettings(), ExportFixtures.labels, now) }
            .toString()
        assertFalse(plain.contains("<Style "))
        assertFalse(plain.contains("<styleUrl>"))
    }

    @Test
    fun `placemark children follow the KML feature sequence`() {
        val placemark = export().substringAfter("<Placemark>").substringBefore("</Placemark>")
        val order = listOf("<name>", "<description>", "<TimeStamp>", "<ExtendedData>", "<Point>")
            .mapNotNull { tag -> placemark.indexOf(tag).takeIf { it >= 0 } }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `the settings that shape a GPX file shape this one too`() {
        assertFalse(export().contains("Ochota"))
        assertTrue(export(ExportSettings(includeIntermediateStops = true)).contains("Ochota"))
        assertEquals(0, export(ExportSettings(includeTracks = false)).occurrencesOf("<LineString>"))
        assertFalse(export(ExportSettings(includeTimes = false)).contains("<TimeStamp>"))
        assertFalse(export(ExportSettings(includeDescriptions = false)).contains("<description>"))
        assertTrue(export().contains("<when>2026-08-06T06:25:00Z</when>"))
    }

    @Test
    fun `feed text is escaped rather than breaking the document`() {
        val hostile = journey.copy(
            legs = listOf(journey.legs[1].copy(routeShortName = "A&B", headsign = "<script>")),
        )
        val kml = StringWriter()
            .also { writeKml(it, hostile, ExportSettings(), labels, now) }
            .toString()
        assertTrue(kml.contains("A&amp;B · towards &lt;script&gt;"))
        assertFalse(kml.contains("<script>"))
    }

    @Test
    fun `the KMZ is a zip holding exactly one doc_kml`() {
        val bytes = ByteArrayOutputStream()
            .also { writeKmz(it, journey, ExportSettings(), labels, now) }
            .toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("doc.kml"), entries.keys)
        val kml = entries.getValue("doc.kml")
        assertTrue(kml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
        assertTrue(kml.trimEnd().endsWith("</kml>"))
    }
}
