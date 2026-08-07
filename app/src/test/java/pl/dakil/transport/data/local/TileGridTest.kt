package pl.dakil.transport.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two ways this can be wrong without anything visibly breaking: a box whose tiles
 * don't actually cover it (the map silently refetches the same area forever, or worse, stamps
 * ground it never asked about), and rectangle merging that loses or duplicates tiles.
 */
class TileGridTest {

    // Warsaw, a little under one tile wide at zoom 13.
    private val warsaw = Bbox(south = 52.20, west = 20.95, north = 52.26, east = 21.05)

    @Test
    fun `tiles cover every corner of the box`() {
        val tiles = TileGrid.tilesFor(warsaw).toSet()
        for (lat in listOf(warsaw.south, warsaw.north)) {
            for (lon in listOf(warsaw.west, warsaw.east)) {
                assertTrue(
                    "corner $lat,$lon not covered",
                    TileKey(TileGrid.tileX(lon), TileGrid.tileY(lat)) in tiles,
                )
            }
        }
    }

    @Test
    fun `every tile box contains the point it was derived from`() {
        val lat = 52.2297
        val lon = 21.0122
        val box = TileGrid.boxOf(TileKey(TileGrid.tileX(lon), TileGrid.tileY(lat)))
        assertTrue(box.contains(lat, lon))
    }

    @Test
    fun `tile boxes tile the plane without gaps`() {
        val tile = TileKey(TileGrid.tileX(21.0), TileGrid.tileY(52.2))
        val box = TileGrid.boxOf(tile)
        // The east edge of one tile is the west edge of the next, and likewise north/south.
        assertEquals(box.east, TileGrid.boxOf(TileKey(tile.x + 1, tile.y)).west, 1e-9)
        assertEquals(box.south, TileGrid.boxOf(TileKey(tile.x, tile.y + 1)).north, 1e-9)
    }

    @Test
    fun `bounding box of a tile set covers each of its tiles`() {
        val tiles = TileGrid.tilesFor(warsaw)
        val box = requireNotNull(TileGrid.boxOf(tiles))
        for (tile in tiles) {
            val tileBox = TileGrid.boxOf(tile)
            assertTrue(tileBox.south >= box.south - 1e-9 && tileBox.north <= box.north + 1e-9)
            assertTrue(tileBox.west >= box.west - 1e-9 && tileBox.east <= box.east + 1e-9)
        }
    }

    @Test
    fun `a box crossing the antimeridian splits into two queryable halves`() {
        val wrapping = Bbox(south = -18.0, west = 179.0, north = -17.0, east = -179.0)
        val parts = wrapping.splitAtAntimeridian()
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.west <= it.east })
        assertEquals(180.0, parts[0].east, 0.0)
        assertEquals(-180.0, parts[1].west, 0.0)
        // And it must produce tiles from both sides of the line, not an empty or single-side set.
        val tiles = TileGrid.tilesFor(wrapping)
        assertTrue(tiles.any { it.x == 0 })
        assertTrue(tiles.any { it.x == (1 shl TileGrid.TILE_ZOOM) - 1 })
    }

    @Test
    fun `latitudes beyond the mercator limit clamp instead of overflowing`() {
        assertEquals(0, TileGrid.tileY(89.9))
        assertEquals((1 shl TileGrid.TILE_ZOOM) - 1, TileGrid.tileY(-89.9))
    }

    @Test
    fun `merging preserves every tile exactly once`() {
        val tiles = listOf(
            TileKey(1, 1), TileKey(2, 1), TileKey(3, 1),
            TileKey(1, 2), TileKey(2, 2), TileKey(3, 2),
            TileKey(7, 5),
        )
        val rects = TileGrid.mergeIntoRects(tiles)
        val flattened = rects.flatten()
        assertEquals(tiles.size, flattened.size)
        assertEquals(tiles.toSet(), flattened.toSet())
    }

    @Test
    fun `a solid block merges into a single rectangle`() {
        val block = buildList {
            for (y in 4..6) for (x in 10..13) add(TileKey(x, y))
        }
        assertEquals(1, TileGrid.mergeIntoRects(block).size)
    }

    @Test
    fun `a hole splits the row it sits in but does not fragment the rest`() {
        // Two full rows with the middle tile of the lower one already cached.
        val tiles = buildList {
            for (x in 0..2) add(TileKey(x, 0))
            add(TileKey(0, 1))
            add(TileKey(2, 1))
        }
        val rects = TileGrid.mergeIntoRects(tiles)
        assertEquals(tiles.toSet(), rects.flatten().toSet())
        // Row 0 cannot merge with row 1 (different spans), and row 1 is two separate runs.
        assertEquals(3, rects.size)
    }

    @Test
    fun `an empty set merges to nothing`() {
        assertEquals(emptyList<List<TileKey>>(), TileGrid.mergeIntoRects(emptyList()))
        assertEquals(null, TileGrid.boxOf(emptyList()))
    }

    @Test
    fun `inflating grows the box but stays inside the world`() {
        val inflated = warsaw.inflated(0.5)
        assertTrue(inflated.south < warsaw.south && inflated.north > warsaw.north)
        assertTrue(inflated.west < warsaw.west && inflated.east > warsaw.east)

        val nearPole = Bbox(south = 84.0, west = 170.0, north = 85.0, east = 179.0).inflated(2.0)
        assertTrue(nearPole.north <= TileGrid.MAX_LATITUDE)
        assertTrue(nearPole.east <= 180.0)
    }
}
