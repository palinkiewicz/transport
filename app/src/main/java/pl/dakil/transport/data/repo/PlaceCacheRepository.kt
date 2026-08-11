package pl.dakil.transport.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.dakil.transport.data.local.Bbox
import pl.dakil.transport.data.local.CachedPlaceEntity
import pl.dakil.transport.data.local.PlaceDao
import pl.dakil.transport.data.local.StopTileDao
import pl.dakil.transport.data.local.StopTileEntity
import pl.dakil.transport.data.local.TileGrid
import pl.dakil.transport.data.local.TileKey
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.data.local.toCachedPlace
import pl.dakil.transport.data.local.toTransitLocation
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation

/** Where the stops of one area came from, so the UI can say when it is drawing stale data. */
data class CachedArea(
    val stops: List<TransitLocation>,
    /** Tiles in the requested box that have never been fetched. */
    val missingTiles: List<TileKey>,
    /** Tiles whose last fetch is older than the TTL — drawn, but worth refreshing. */
    val expiredTiles: List<TileKey>,
) {
    /** Areas the app should ask the API about: never fetched, or past their TTL. */
    val staleTiles: List<TileKey> get() = missingTiles + expiredTiles

    /**
     * [stops] with everything from an expired tile removed — what the map draws when the user
     * has asked not to be shown data that may be out of date.
     */
    fun stopsExcludingExpired(): List<TransitLocation> {
        if (expiredTiles.isEmpty()) return stops
        val expired = expiredTiles.toHashSet()
        return stops.filterNot { TileKey(TileGrid.tileX(it.lon), TileGrid.tileY(it.lat)) in expired }
    }
}

/** How many places the cache holds, for the settings screen's stats line. */
data class CacheStats(val places: Int, val stops: Int, val areas: Int)

/**
 * The app's memory of the places it has seen.
 *
 * Every read is answered from disk, immediately and unconditionally — including from tiles whose
 * TTL has passed. Freshness is reported alongside the data rather than gating it, so the map can
 * draw first and let the network catch up, and so a phone with no connection keeps working
 * instead of showing an empty world.
 */
@Singleton
class PlaceCacheRepository @Inject constructor(
    private val placeDao: PlaceDao,
    private val stopTileDao: StopTileDao,
    private val stopsRepository: StopsRepository,
) {

    /**
     * Serializes area refreshes. Two overlapping rectangles refreshing at once would each
     * delete the other's freshly inserted stops inside the overlap; the lock is cheap because
     * refreshes are already settle-gated and rare.
     */
    private val refreshLock = Mutex()

    /**
     * Cached stops inside [box], with which of its tiles are stale at [ttlMillis].
     *
     * [now] is passed in rather than read here so a caller can evaluate a whole viewport against
     * one instant.
     */
    suspend fun stopsIn(box: Bbox, ttlMillis: Long, now: Long): CachedArea {
        val stops = box.splitAtAntimeridian()
            .flatMap { part ->
                placeDao.stopsInBox(part.south, part.west, part.north, part.east, MAX_STOPS_PER_READ)
            }
            .map { it.toTransitLocation() }

        val tiles = TileGrid.tilesFor(box)
        val fetchedAt = tiles.map { it.id }
            // SQLite caps the parameters one statement may bind; a zoomed-out viewport can
            // easily name more tiles than that.
            .chunked(SQL_PARAMETER_CHUNK)
            .flatMap { stopTileDao.tilesIn(it) }
            .associate { it.tileKey to it.fetchedAt }
        val missing = tiles.filter { it.id !in fetchedAt }
        val expired = tiles.filter { tile ->
            fetchedAt[tile.id]?.let { now - it > ttlMillis } == true
        }
        return CachedArea(stops = stops, missingTiles = missing, expiredTiles = expired)
    }

    /**
     * Fetches the stops of [tiles] and writes them into the cache, one request per merged
     * rectangle.
     *
     * Rectangles come from [TileGrid.mergeIntoRects] so a viewport with a few holes costs a few
     * small requests rather than one that re-downloads everything already known. Past
     * [maxRequests] the whole set collapses into its bounding box instead: a screen of misses is
     * one request, not forty, which matters on an API the whole community shares.
     *
     * @return the number of rectangles that failed, so the caller can flag the area as offline.
     */
    suspend fun refreshTiles(tiles: List<TileKey>, now: Long, maxRequests: Int = MAX_REFRESH_REQUESTS): Int {
        if (tiles.isEmpty()) return 0
        val rects = TileGrid.mergeIntoRects(tiles)
        val batches = if (rects.size <= maxRequests) rects else listOf(tiles)

        var failures = 0
        for (batch in batches) {
            // Whole tiles only, so everything in the box is covered by the response and can be
            // stamped as fetched — a rectangle drawn any other way would mark tiles it only
            // partly covered.
            val box = TileGrid.boxOf(batch) ?: continue
            stopsRepository.stopsInViewport(box.south, box.west, box.north, box.east).fold(
                onSuccess = { stops -> refreshArea(box, stops, now, batch) },
                // A failed rectangle stamps nothing, so it is simply retried next time.
                onFailure = { failures++ },
            )
        }
        return failures
    }

    /**
     * Replaces the map-sourced stops of [box] with [stops] and marks [tiles] fetched.
     *
     * The replace is what lets a withdrawn stop disappear. Area information is carried over from
     * the rows being replaced: `/v6/map/stops` returns no city/state/country, so a stop the
     * geocoder had already described would otherwise lose its subtitle the first time the map
     * refreshed over it.
     */
    private suspend fun refreshArea(
        box: Bbox,
        stops: List<TransitLocation>,
        now: Long,
        tiles: List<TileKey>,
    ) = refreshLock.withLock {
        val existing = stops.map { it.favoriteKey }
            .chunked(SQL_PARAMETER_CHUNK)
            .flatMap { placeDao.findByKeys(it) }
            .associateBy { it.key }
        val rows = stops.map { stop ->
            val previous = existing[stop.favoriteKey]
            stop.toCachedPlace(fromMap = true, now = now).copy(
                city = stop.city ?: previous?.city,
                state = stop.state ?: previous?.state,
                country = stop.country ?: previous?.country,
            )
        }
        placeDao.replaceMapPlacesInBox(box.south, box.west, box.north, box.east, rows)
        stopTileDao.upsert(tiles.map { StopTileEntity(tileKey = it.id, fetchedAt = now) })
    }

    /**
     * Records places the geocoder returned. Written with `fromMap = false` so an area refresh
     * over the same ground leaves them alone — a searched address is not something
     * `/v6/map/stops` would ever return, and deleting it would lose it for good.
     */
    suspend fun rememberGeocoded(places: List<TransitLocation>, now: Long) {
        if (places.isEmpty()) return
        placeDao.upsert(places.map { it.toCachedPlace(fromMap = false, now = now) })
        places.forEach { spreadAreaToStation(it) }
    }

    /**
     * Copies a geocoded stop's city/state/country onto the map-fetched platforms of the same
     * station.
     *
     * `/v6/map/stops` returns no area information at all, so a stop the map found is a bare name
     * on a coordinate — several identical "Centrum" rows with nothing to tell them apart. The
     * geocoder does return it, but only for the station as a whole. Spreading it here is what
     * makes an area the user has searched once stay properly labelled afterwards, including with
     * no connection.
     *
     * Scoped by name *and* proximity, so this can only ever label platforms of the very station
     * the geocoder described.
     */
    private suspend fun spreadAreaToStation(station: TransitLocation) {
        if (station.stopId == null || station.areaLabel == null) return
        val box = Bbox(
            south = station.lat - STATION_SPREAD_DEGREES,
            west = station.lon - STATION_SPREAD_DEGREES,
            north = station.lat + STATION_SPREAD_DEGREES,
            east = station.lon + STATION_SPREAD_DEGREES,
        )
        val neighbours = placeDao
            .stopsInBox(box.south, box.west, box.north, box.east, MAX_STOPS_PER_READ)
            .filter { it.key != station.favoriteKey && it.searchName == foldForSearch(station.name) }
            // Only fill gaps: a row the geocoder itself described already knows better.
            .filter { it.city == null && it.state == null && it.country == null }
        if (neighbours.isEmpty()) return
        placeDao.upsert(
            neighbours.map {
                it.copy(city = station.city, state = station.state, country = station.country)
            },
        )
    }

    /**
     * Candidate places for [foldedToken]; ranking is the caller's job.
     *
     * Two reads rather than one, because either alone loses answers the ranker needs. Ordering by
     * importance alone buries a small local stop under famous same-named ones worldwide; ordering
     * by proximity alone can only ever offer what the phone happens to have cached nearby. So the
     * significant matches and the [bias]-local ones are fetched separately and unioned, each
     * capped at [limit].
     *
     * [foldedToken] is one word, not the whole query: the picker searches on the query's longest
     * (most selective) word and lets `PlaceSearchEngine` judge the rest, so a two-word query like
     * "Park Gó" can match "N-Park Gorzów" — which a `LIKE` on the whole string never could.
     */
    suspend fun search(
        foldedToken: String,
        stopsOnly: Boolean,
        bias: GeoPoint?,
        limit: Int,
    ): List<TransitLocation> {
        if (foldedToken.isBlank()) return emptyList()
        val pattern = "%${foldedToken.escapeLike()}%"
        val byImportance = placeDao.searchByName(pattern = pattern, stopsOnly = stopsOnly, limit = limit)
        val nearby = if (bias == null) {
            emptyList()
        } else {
            Bbox(
                south = (bias.lat - SEARCH_NEAR_DEGREES).coerceAtLeast(TileGrid.MIN_LATITUDE),
                west = wrapLongitude(bias.lon - SEARCH_NEAR_DEGREES),
                north = (bias.lat + SEARCH_NEAR_DEGREES).coerceAtMost(TileGrid.MAX_LATITUDE),
                east = wrapLongitude(bias.lon + SEARCH_NEAR_DEGREES),
            ).splitAtAntimeridian().flatMap { part ->
                placeDao.searchByNameNear(
                    pattern = pattern,
                    stopsOnly = stopsOnly,
                    south = part.south,
                    west = part.west,
                    north = part.north,
                    east = part.east,
                    limit = limit,
                )
            }
        }
        return (byImportance + nearby)
            .distinctBy { it.key }
            .map { it.toTransitLocation() }
    }

    /** Trims the cache back to [maxPlaces], oldest first, never touching [protectedKeys]. */
    suspend fun evictIfOversized(maxPlaces: Int, protectedKeys: List<String>) {
        val excess = placeDao.countPlaces() - maxPlaces
        if (excess > 0) placeDao.deleteOldest(excess, protectedKeys)
    }

    suspend fun stats(): CacheStats = CacheStats(
        places = placeDao.countPlaces(),
        stops = placeDao.countStops(),
        areas = stopTileDao.countTiles(),
    )

    /** Empties the cache apart from [protectedKeys] — what the settings screen's button does. */
    suspend fun clear(protectedKeys: List<String>) {
        placeDao.deleteAllExcept(protectedKeys)
        // Every area has to be considered unknown again: the rows backing it are gone, and a
        // tile left stamped would stop the map ever refetching what it just deleted.
        stopTileDao.deleteAll()
    }

    companion object {
        /** Above this many merged rectangles, one bounding-box request is the kinder option. */
        const val MAX_REFRESH_REQUESTS = 4

        /**
         * Ceiling on the stops one viewport read returns. Well past what a screen can usefully
         * draw, and there only so a zoomed-out box over a dense country cannot pull the entire
         * cache into memory.
         */
        const val MAX_STOPS_PER_READ = 6_000

        /**
         * Tiles a viewport may name before the app declines to fetch it at all. A box that large
         * is not something to ask a shared community API for in one go; its cached stops are
         * still drawn, they just stop being refreshed until the user zooms in.
         */
        const val MAX_VIEWPORT_TILES = 256

        /** Comfortably under SQLite's bound-parameter limit. */
        private const val SQL_PARAMETER_CHUNK = 500

        /**
         * Half-width of the box searched for platforms of a geocoded station, in degrees —
         * roughly 500 m of latitude. Comfortably wider than `PlaceSearchEngine`'s own station
         * radius, since this only proposes candidates that the name check then confirms.
         */
        private const val STATION_SPREAD_DEGREES = 0.0045

        /**
         * Half-width of the "nearby" candidate box, in degrees — roughly 55 km of latitude. Sized
         * to cover the bands where the geocoder's proximity bonus actually varies: everything
         * inside 100 km is scored differently from everything outside it, so a box about that
         * size is what the ranker needs to see to reproduce the ordering.
         */
        private const val SEARCH_NEAR_DEGREES = 0.5
    }
}

/** Longitude folded back into -180..180, so a box near the antimeridian stays well-formed. */
private fun wrapLongitude(lon: Double): Double {
    var wrapped = (lon + 180.0) % 360.0
    if (wrapped < 0) wrapped += 360.0
    return wrapped - 180.0
}

/** Escapes the LIKE wildcards so a query containing `%` or `_` matches them literally. */
private fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
