package pl.dakil.transport.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The history is a list of *places*, newest first, and never longer than the user asked for. */
class RecentPlacesTest {

    private fun stop(id: String) = TransitLocation(name = id, lat = 52.0, lon = 21.0, stopId = id)

    @Test
    fun `the newest pick leads`() {
        val places = RecentPlaces.EMPTY.record(stop("a"), LIMIT).record(stop("b"), LIMIT).places
        assertEquals(listOf("b", "a"), places.map { it.stopId })
    }

    @Test
    fun `re-picking a place promotes it instead of repeating it`() {
        val places = RecentPlaces.EMPTY
            .record(stop("a"), LIMIT)
            .record(stop("b"), LIMIT)
            .record(stop("a"), LIMIT)
            .places
        assertEquals(listOf("a", "b"), places.map { it.stopId })
    }

    @Test
    fun `the oldest pick falls off the end`() {
        val full = (1..3).fold(RecentPlaces.EMPTY) { recents, index -> recents.record(stop("$index"), 2) }
        assertEquals(listOf("3", "2"), full.places.map { it.stopId })
    }

    @Test
    fun `a limit of zero forgets what was already stored`() {
        // How turning the setting off actually clears the history: the next pick writes nothing
        // and drops the rest with it.
        val stored = RecentPlaces.EMPTY.record(stop("a"), LIMIT).record(stop("b"), LIMIT)
        assertTrue(stored.record(stop("c"), 0).places.isEmpty())
    }

    private companion object {
        const val LIMIT = 10
    }
}
