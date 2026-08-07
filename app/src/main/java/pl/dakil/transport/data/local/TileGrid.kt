package pl.dakil.transport.data.local

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** A rectangle of the world in degrees. [west] may be greater than [east] only after splitting. */
data class Bbox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val widthDegrees: Double get() = east - west
    val heightDegrees: Double get() = north - south

    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east

    /** This box grown by [factor] of its own size on each side, clamped to the valid world. */
    fun inflated(factor: Double): Bbox {
        val padLat = heightDegrees * factor
        val padLon = widthDegrees * factor
        return Bbox(
            south = (south - padLat).coerceAtLeast(TileGrid.MIN_LATITUDE),
            west = (west - padLon).coerceAtLeast(-180.0),
            north = (north + padLat).coerceAtMost(TileGrid.MAX_LATITUDE),
            east = (east + padLon).coerceAtMost(180.0),
        )
    }

    /**
     * The box split at the antimeridian when it wraps, so each part can be handed to a plain
     * `lon BETWEEN west AND east` query. A non-wrapping box is returned as-is.
     */
    fun splitAtAntimeridian(): List<Bbox> =
        if (west <= east) {
            listOf(this)
        } else {
            listOf(copy(west = west, east = 180.0), copy(west = -180.0, east = east))
        }
}

/** One tile of [TileGrid], addressed in XYZ scheme coordinates at [TileGrid.TILE_ZOOM]. */
data class TileKey(val x: Int, val y: Int) {
    /** Stable string form, used as the `stop_tile` primary key. */
    val id: String get() = "$x/$y"
}

/**
 * Web-Mercator XYZ tiling used to record which parts of the world have had their stops fetched.
 *
 * Stops are cached per *tile* rather than per requested rectangle because a rectangle is a
 * record of one particular pan: two overlapping rectangles say nothing useful about whether the
 * area between them is known. A fixed grid gives an exact, set-shaped answer to "is this
 * viewport already covered", which is what lets a pan back over familiar ground make no request
 * at all.
 *
 * The zoom is fixed at [TILE_ZOOM] rather than following the camera: a tile's meaning is "the
 * stops here are known", and that must not change when the user zooms. 13 is chosen to match the
 * default `AppSettings.stopsMinZoom`, so a screen at the zoom where stops first appear is a
 * handful of tiles rather than hundreds.
 */
object TileGrid {

    const val TILE_ZOOM = 13

    /** Web Mercator is undefined at the poles; this is the standard cut-off it is defined to. */
    const val MAX_LATITUDE = 85.0511287798066
    const val MIN_LATITUDE = -MAX_LATITUDE

    /** Tiles per axis at [TILE_ZOOM]. */
    private const val TILE_COUNT = 1 shl TILE_ZOOM

    fun tileX(lon: Double): Int =
        floor((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * TILE_COUNT)
            .toInt()
            .coerceIn(0, TILE_COUNT - 1)

    fun tileY(lat: Double): Int {
        val clamped = lat.coerceIn(MIN_LATITUDE, MAX_LATITUDE)
        val radians = Math.toRadians(clamped)
        val mercator = ln(tan(radians) + 1.0 / kotlin.math.cos(radians))
        return floor((1.0 - mercator / PI) / 2.0 * TILE_COUNT)
            .toInt()
            .coerceIn(0, TILE_COUNT - 1)
    }

    fun westOf(x: Int): Double = x.toDouble() / TILE_COUNT * 360.0 - 180.0

    fun northOf(y: Int): Double {
        val n = PI - 2.0 * PI * y / TILE_COUNT
        return Math.toDegrees(atan(sinh(n)))
    }

    /** Every tile the [box] touches. A box wrapping the antimeridian is split first. */
    fun tilesFor(box: Bbox): List<TileKey> =
        box.splitAtAntimeridian().flatMap { part ->
            val minX = tileX(part.west)
            val maxX = tileX(part.east)
            // y grows southwards, so the north edge gives the smaller index.
            val minY = tileY(part.north)
            val maxY = tileY(part.south)
            buildList {
                for (y in minY..maxY) {
                    for (x in minX..maxX) add(TileKey(x, y))
                }
            }
        }.distinct()

    /** The exact bounding box of [tile] — the area a fetch covering it must ask for. */
    fun boxOf(tile: TileKey): Bbox = Bbox(
        south = northOf(tile.y + 1),
        west = westOf(tile.x),
        north = northOf(tile.y),
        east = westOf(tile.x + 1),
    )

    /** The smallest box containing every tile in [tiles]; null when [tiles] is empty. */
    fun boxOf(tiles: Collection<TileKey>): Bbox? {
        if (tiles.isEmpty()) return null
        val minX = tiles.minOf { it.x }
        val maxX = tiles.maxOf { it.x }
        val minY = tiles.minOf { it.y }
        val maxY = tiles.maxOf { it.y }
        return Bbox(
            south = northOf(maxY + 1),
            west = westOf(minX),
            north = northOf(minY),
            east = westOf(maxX + 1),
        )
    }

    /**
     * [tiles] packed into as few rectangles as possible, so a viewport with a few holes in its
     * cache costs a few small requests instead of one that re-downloads everything already
     * known. Greedy: maximal horizontal runs per row, then runs stacked vertically where the
     * row below repeats the same span exactly.
     *
     * Every returned rectangle is exactly a union of whole tiles, which is what makes it safe to
     * stamp all of its tiles as fetched once the request succeeds.
     */
    fun mergeIntoRects(tiles: Collection<TileKey>): List<List<TileKey>> {
        if (tiles.isEmpty()) return emptyList()
        val byRow = tiles.groupBy { it.y }.mapValues { (_, row) -> row.map { it.x }.sorted() }

        // Each row becomes its maximal runs of consecutive x, e.g. [3,4,5,9] -> [3..5, 9..9].
        val runsByRow: Map<Int, List<IntRange>> = byRow.mapValues { (_, xs) ->
            buildList {
                var start = xs.first()
                var previous = start
                for (x in xs.drop(1)) {
                    if (x != previous + 1) {
                        add(start..previous)
                        start = x
                    }
                    previous = x
                }
                add(start..previous)
            }
        }

        val consumed = mutableSetOf<Pair<Int, IntRange>>()
        val rects = mutableListOf<List<TileKey>>()
        for (y in runsByRow.keys.sorted()) {
            for (run in runsByRow.getValue(y)) {
                if (!consumed.add(y to run)) continue
                // Grow downwards for as long as the next row offers the identical span.
                var bottom = y
                while (runsByRow[bottom + 1]?.contains(run) == true && consumed.add((bottom + 1) to run)) {
                    bottom++
                }
                rects.add(
                    buildList {
                        for (rowY in y..bottom) {
                            for (x in run) add(TileKey(x, rowY))
                        }
                    },
                )
            }
        }
        return rects
    }
}
