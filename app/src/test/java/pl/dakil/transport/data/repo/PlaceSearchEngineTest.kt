package pl.dakil.transport.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * Covers what would go wrong invisibly: folding that silently drops Polish stops from every
 * result, and an ordering the user reads as random.
 */
class PlaceSearchEngineTest {

    private fun stop(
        name: String,
        lat: Double = 52.23,
        lon: Double = 21.01,
        id: String = "stop:$name",
        importance: Double = 0.0,
    ) = TransitLocation(name = name, lat = lat, lon = lon, stopId = id, importance = importance)

    /** A platform of a station: same name and importance, a short walk away. */
    private fun pole(
        name: String,
        id: String,
        metresEast: Double,
        importance: Double = 0.01,
    ) = stop(
        name = name,
        lat = 52.23,
        // ~1 degree of longitude is 68 km at this latitude.
        lon = 21.01 + metresEast / 68_000.0,
        id = id,
        importance = importance,
    )

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
    fun `platforms of one station collapse into a single result`() {
        val poles = listOf(
            pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0),
            pole("Centrum", "pl-Warszawa_centrum15", metresEast = 60.0),
            pole("Centrum", "pl-Warszawa_centruma13", metresEast = 200.0),
        )
        assertEquals(listOf("Centrum"), names(PlaceSearchEngine.rank("centrum", poles)))
    }

    @Test
    fun `same-named stations far apart stay separate`() {
        val warsaw = stop("Rynek", lat = 52.23, lon = 21.01, id = "pl-Warszawa_rynek")
        val krakow = stop("Rynek", lat = 50.06, lon = 19.94, id = "pl-Krakow_rynek")
        assertEquals(2, PlaceSearchEngine.rank("rynek", listOf(warsaw, krakow)).size)
    }

    @Test
    fun `nearby same-named stops with different importance are different stations`() {
        // The API scores a station as a whole, so poles of one station always agree. Two that
        // disagree are two stations that happen to share a name and sit close together.
        val busy = pole("Rynek", "a", metresEast = 0.0, importance = 0.01)
        val quiet = pole("Rynek", "b", metresEast = 100.0, importance = 0.0001)
        assertEquals(2, PlaceSearchEngine.rank("rynek", listOf(busy, quiet)).size)
    }

    @Test
    fun `a station is represented by the member that knows its area`() {
        val bare = pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0)
        val geocoded = pole("Centrum", "pl-Warszawa_centruma13", metresEast = 200.0)
            .copy(city = "Warszawa", country = "PL")

        val result = PlaceSearchEngine.rank("centrum", listOf(bare, geocoded)).single()
        assertEquals("pl-Warszawa_centruma13", result.stopId)
        assertEquals("Warszawa, PL", result.areaLabel)
    }

    @Test
    fun `a station carries the modes of every platform`() {
        val tram = pole("Centrum", "a", metresEast = 0.0).copy(modes = listOf(TransportMode.TRAM))
        val bus = pole("Centrum", "b", metresEast = 60.0).copy(modes = listOf(TransportMode.BUS))
        val metro = pole("Centrum", "c", metresEast = 120.0).copy(modes = listOf(TransportMode.SUBWAY))

        val result = PlaceSearchEngine.rank("centrum", listOf(tram, bus, metro)).single()
        assertEquals(
            setOf(TransportMode.TRAM, TransportMode.BUS, TransportMode.SUBWAY),
            result.modes.toSet(),
        )
    }

    @Test
    fun `an address is never folded into a stop of the same name`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(address("Centrum"), stop("Centrum")),
        )
        assertEquals(2, results.size)
        // …and the stop still leads, because it is the one you can actually travel from.
        assertEquals("stop:Centrum", results.first().stopId)
    }

    @Test
    fun `a place held by both sources is returned once`() {
        // What the picker hands over: the geocoder's answer concatenated with the cache's, where
        // the cache is holding the very same rows because it stored them last time. Every result
        // keys a list row, so a repeat here is a crash rather than a cosmetic problem.
        val remote = listOf(
            address("Marszałkowska 1").copy(city = "Warszawa"),
            stop("Centrum", id = "pl-Warszawa_centruma13").copy(city = "Warszawa"),
        )
        val cached = listOf(address("Marszałkowska 1"), stop("Centrum", id = "pl-Warszawa_centruma13"))

        val results = PlaceSearchEngine.groupIntoStations(remote + cached).map { it.place }
        assertEquals(results.size, results.distinctBy { it.favoriteKey }.size)
        assertEquals(2, results.size)
        // The geocoder's copy is the one kept, because it knows the area.
        assertTrue(results.all { it.city == "Warszawa" })
    }

    @Test
    fun `ranking never returns two results with the same key`() {
        val duplicated = listOf(
            address("Centrum"), address("Centrum"),
            stop("Centrum", id = "a"), stop("Centrum", id = "a"),
        )
        val results = PlaceSearchEngine.rank("centrum", duplicated)
        assertEquals(results.size, results.distinctBy { it.favoriteKey }.size)
    }

    @Test
    fun `a more important station outranks a lesser one of the same name`() {
        val major = stop("Dworzec", id = "major", lat = 52.30, lon = 21.20, importance = 0.05)
        val minor = stop("Dworzec", id = "minor", lat = 52.40, lon = 21.30, importance = 0.00001)
        assertEquals("major", PlaceSearchEngine.rank("dworzec", listOf(minor, major)).first().stopId)
    }

    @Test
    fun `equally scored places keep a stable order between keystrokes`() {
        val places = listOf(stop("Centrum B"), stop("Centrum A"))
        val first = names(PlaceSearchEngine.rank("centrum", places))
        val second = names(PlaceSearchEngine.rank("centrum", places.reversed()))
        assertEquals(first, second)
    }
}
