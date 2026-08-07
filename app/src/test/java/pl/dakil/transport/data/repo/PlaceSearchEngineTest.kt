package pl.dakil.transport.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation

/**
 * Covers what would go wrong invisibly: folding that silently drops Polish stops from every
 * result, and an ordering the user reads as random.
 */
class PlaceSearchEngineTest {

    private fun stop(name: String, lat: Double = 52.23, lon: Double = 21.01) =
        TransitLocation(name = name, lat = lat, lon = lon, stopId = "stop:$name")

    private fun address(name: String, lat: Double = 52.23, lon: Double = 21.01) =
        TransitLocation(name = name, lat = lat, lon = lon, stopId = null)

    private fun names(results: List<TransitLocation>) = results.map { it.name }

    @Test
    fun `folding strips Polish diacritics`() {
        assertEquals("zabkowska", foldForSearch("Ząbkowska"))
        assertEquals("swietokrzyska", foldForSearch("Świętokrzyska"))
        assertEquals("lodz zdunska wola", foldForSearch("Łódź  Zduńska Wola"))
    }

    @Test
    fun `folding leaves an already plain name alone`() {
        assertEquals("centrum", foldForSearch("Centrum"))
        assertEquals("warszawa centralna", foldForSearch(" Warszawa   Centralna "))
    }

    @Test
    fun `an unaccented query matches an accented name`() {
        val results = PlaceSearchEngine.rank("zabkowska", listOf(stop("Ząbkowska")))
        assertEquals(listOf("Ząbkowska"), names(results))
    }

    @Test
    fun `only names containing the query are returned`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Centrum"), stop("Warszawa Centralna"), stop("Metro Centrum Nauki")),
        )
        assertEquals(setOf("Centrum", "Metro Centrum Nauki"), names(results).toSet())
    }

    @Test
    fun `a name starting with the query beats one containing it`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Metro Centrum Nauki Kopernik"), stop("Centrum")),
        )
        assertEquals("Centrum", names(results).first())
    }

    @Test
    fun `a match at a word start beats one mid-word`() {
        val results = PlaceSearchEngine.rank(
            "kowska",
            listOf(stop("Ząbkowska"), stop("Kowska Brama")),
        )
        assertEquals("Kowska Brama", names(results).first())
    }

    @Test
    fun `at the same match class the shorter name wins`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Centrum Nauki Kopernik Przystanek"), stop("Centrum")),
        )
        assertEquals("Centrum", names(results).first())
    }

    @Test
    fun `at the same name a stop beats an address`() {
        val results = PlaceSearchEngine.rank("centrum", listOf(address("Centrum"), stop("Centrum")))
        assertEquals("stop:Centrum", results.first().stopId)
    }

    @Test
    fun `distance only reorders places that matched the same way`() {
        val near = stop("Centrum Bliskie", lat = 52.2300, lon = 21.0100)
        val far = stop("Centrum Dalekie", lat = 52.4000, lon = 21.4000)
        val reference = GeoPoint(52.2300, 21.0100)

        val ranked = PlaceSearchEngine.rank(
            query = "centrum",
            places = listOf(far, near),
            reference = reference,
            distanceWeight = PlaceSearchEngine.DISTANCE_WEIGHT_WHEN_SORTING,
        )
        assertEquals("Centrum Bliskie", names(ranked).first())

        // …but it must never lift a mid-name match above one the user typed the start of, however
        // close by it happens to be.
        val nearButBuried = stop("Metro Centrum Nauki", lat = 52.2300, lon = 21.0100)
        val farButLeading = stop("Centrum", lat = 52.9000, lon = 22.9000)
        val ordered = PlaceSearchEngine.rank(
            query = "centrum",
            places = listOf(nearButBuried, farButLeading),
            reference = reference,
            distanceWeight = PlaceSearchEngine.DISTANCE_WEIGHT_WHEN_SORTING,
        )
        assertEquals("Centrum", names(ordered).first())
    }

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(PlaceSearchEngine.rank("", listOf(stop("Centrum"))).isEmpty())
        assertTrue(PlaceSearchEngine.rank("   ", listOf(stop("Centrum"))).isEmpty())
    }

    @Test
    fun `the limit is applied after ranking, not before`() {
        val places = listOf(
            stop("Zzz Centrum"),
            stop("Centrum"),
            stop("Aaa Centrum"),
        )
        assertEquals(listOf("Centrum"), names(PlaceSearchEngine.rank("centrum", places, limit = 1)))
    }

    @Test
    fun `equally scored places keep a stable order between keystrokes`() {
        val places = listOf(stop("Centrum B"), stop("Centrum A"))
        val first = names(PlaceSearchEngine.rank("centrum", places))
        val second = names(PlaceSearchEngine.rank("centrum", places.reversed()))
        assertEquals(first, second)
    }
}
