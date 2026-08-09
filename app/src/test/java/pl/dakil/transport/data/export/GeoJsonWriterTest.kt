package pl.dakil.transport.data.export

import java.io.StringWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.domain.model.ExportSettings

/**
 * Covers what an RFC 7946 reader needs: parseable JSON, `[lon, lat]` positions as numbers, and the
 * same points and paths the other formats carry.
 */
class GeoJsonWriterTest {

    private val labels = ExportFixtures.colouredLabels
    private val now = ExportFixtures.now
    private val journey = ExportFixtures.journey

    private fun export(settings: ExportSettings = ExportSettings()): JsonObject {
        val text = StringWriter()
            .also { writeGeoJson(it, journey, settings, labels, now) }
            .toString()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.features(): List<JsonObject> =
        getValue("features").jsonArray.map { it.jsonObject }

    private fun JsonObject.geometryType(): String =
        getValue("geometry").jsonObject.getValue("type").jsonPrimitive.content

    private fun JsonObject.property(name: String): String? =
        getValue("properties").jsonObject[name]?.jsonPrimitive?.contentOrNull

    @Test
    fun `the document is one FeatureCollection of points then lines`() {
        val collection = export()
        assertEquals("FeatureCollection", collection.getValue("type").jsonPrimitive.content)
        assertEquals("Home → Hotel", collection.getValue("name").jsonPrimitive.content)
        val types = collection.features().map { it.geometryType() }
        // Five boundaries and three drawn legs, points first so they draw on top.
        assertEquals(List(5) { "Point" } + List(3) { "LineString" }, types)
    }

    @Test
    fun `positions are numbers in lon,lat order, rounded to six decimals`() {
        val boarding = export().features()[1]
        val position = boarding.getValue("geometry").jsonObject.getValue("coordinates") as JsonArray
        assertEquals(21.003_41, position[0].jsonPrimitive.double, 0.0)
        assertEquals(52.228_67, position[1].jsonPrimitive.double, 0.0)
    }

    @Test
    fun `waypoints carry the same names, types and times the other formats write`() {
        val points = export().features().filter { it.geometryType() == "Point" }
        assertEquals(
            listOf("board", "board", "transfer", "alight", "alight"),
            points.map { it.property("type") },
        )
        assertEquals("Board Home", points.first().property("name"))
        assertEquals("Get off at Hotel", points.last().property("name"))
        assertEquals("2026-08-06T06:25:00Z", points[1].property("time"))
    }

    @Test
    fun `lines carry their mode and the simplestyle colour keys`() {
        val lines = export().features().filter { it.geometryType() == "LineString" }
        assertEquals(listOf("walk", "rail", "bus"), lines.map { it.property("mode") })
        assertEquals("S2 towards Lotnisko Chopina", lines[1].property("name"))
        assertTrue(lines.all { it.property("stroke") == "#E1251B" })
    }

    @Test
    fun `an uncoloured journey simply omits the style keys`() {
        val text = StringWriter()
            .also { writeGeoJson(it, journey, ExportSettings(), ExportFixtures.labels, now) }
            .toString()
        assertFalse(text.contains("stroke"))
    }

    @Test
    fun `the settings that shape a GPX file shape this one too`() {
        fun points(settings: ExportSettings) =
            export(settings).features().count { it.geometryType() == "Point" }

        assertEquals(6, points(ExportSettings(includeIntermediateStops = true)))
        assertEquals(3, points(ExportSettings(includeAccessLegs = false)))
        assertEquals(5, export(ExportSettings(includeTracks = false)).features().size)
        assertNull(export(ExportSettings(includeTimes = false)).features()[1].property("time"))
        assertNull(
            export(ExportSettings(includeDescriptions = false)).features()[1]
                .property("description"),
        )
    }

    @Test
    fun `hostile feed text stays inside its string rather than breaking the document`() {
        val hostile = journey.copy(
            legs = listOf(journey.legs[1].copy(routeShortName = "\"A\\B\"", headsign = "{}")),
        )
        val text = StringWriter()
            .also { writeGeoJson(it, hostile, ExportSettings(), labels, now) }
            .toString()
        // Parsing at all is the assertion: unescaped quotes or braces would have ended the string.
        val line = Json.parseToJsonElement(text).jsonObject.features()
            .first { it.geometryType() == "LineString" }
        assertEquals("\"A\\B\" towards {}", line.property("name"))
    }
}
