package pl.dakil.transport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaceDao {

    /**
     * Cached stops inside one non-wrapping box. Callers must split a box crossing the
     * antimeridian themselves ([Bbox.splitAtAntimeridian]) — a plain BETWEEN matches nothing
     * once west > east.
     */
    @Query(
        """
        SELECT * FROM cached_place
        WHERE stopId IS NOT NULL
          AND lat BETWEEN :south AND :north
          AND lon BETWEEN :west AND :east
        LIMIT :limit
        """,
    )
    suspend fun stopsInBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        limit: Int,
    ): List<CachedPlaceEntity>

    /**
     * The most significant places whose folded name contains [pattern] (already `%…%`-wrapped
     * and folded by the caller).
     *
     * Ranking happens in Kotlin; this only narrows the set. The `ORDER BY` is the point: a bare
     * `LIMIT` truncates in storage order, so on a large cache the row the ranker would have put
     * first can be cut before it ever sees it. Importance is the best single stand-in for "worth
     * offering" that a row carries — the local half of the pair is [searchNameNear].
     */
    @Query(
        """
        SELECT * FROM cached_place
        WHERE searchName LIKE :pattern ESCAPE '\'
          AND (:stopsOnly = 0 OR stopId IS NOT NULL)
        ORDER BY importance DESC
        LIMIT :limit
        """,
    )
    suspend fun searchByName(pattern: String, stopsOnly: Boolean, limit: Int): List<CachedPlaceEntity>

    /**
     * Places matching [pattern] inside one non-wrapping box — the nearby half of the candidate
     * set, so a small local stop the user actually means is never truncated away by a famous
     * one on the other side of the continent. Uses the `(lat, lon)` index.
     *
     * Callers must split a box crossing the antimeridian themselves, as for [stopsInBox].
     */
    @Query(
        """
        SELECT * FROM cached_place
        WHERE searchName LIKE :pattern ESCAPE '\'
          AND (:stopsOnly = 0 OR stopId IS NOT NULL)
          AND lat BETWEEN :south AND :north
          AND lon BETWEEN :west AND :east
        ORDER BY importance DESC
        LIMIT :limit
        """,
    )
    suspend fun searchByNameNear(
        pattern: String,
        stopsOnly: Boolean,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        limit: Int,
    ): List<CachedPlaceEntity>

    @Query("SELECT * FROM cached_place WHERE `key` IN (:keys)")
    suspend fun findByKeys(keys: List<String>): List<CachedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(places: List<CachedPlaceEntity>)

    @Query(
        """
        DELETE FROM cached_place
        WHERE fromMap = 1
          AND lat BETWEEN :south AND :north
          AND lon BETWEEN :west AND :east
        """,
    )
    suspend fun deleteMapPlacesInBox(south: Double, west: Double, north: Double, east: Double)

    /**
     * Replaces the map-sourced stops of one fetched rectangle.
     *
     * The delete is what lets a withdrawn stop disappear; it is scoped to `fromMap` rows so a
     * place only the geocoder knows about survives an area refresh. [places] is expected to
     * already carry any area information merged in from the rows being replaced — see
     * `PlaceCacheRepository.refreshArea`.
     */
    @Transaction
    suspend fun replaceMapPlacesInBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        places: List<CachedPlaceEntity>,
    ) {
        deleteMapPlacesInBox(south, west, north, east)
        upsert(places)
    }

    @Query("SELECT COUNT(*) FROM cached_place")
    suspend fun countPlaces(): Int

    @Query("SELECT COUNT(*) FROM cached_place WHERE stopId IS NOT NULL")
    suspend fun countStops(): Int

    /**
     * Drops the oldest rows once the cache outgrows its limit, never touching [protectedKeys]
     * (the starred places and the stops of saved itineraries — the ones the user explicitly
     * asked to keep offline).
     */
    @Query(
        """
        DELETE FROM cached_place
        WHERE `key` IN (
            SELECT `key` FROM cached_place
            WHERE `key` NOT IN (:protectedKeys)
            ORDER BY fetchedAt ASC
            LIMIT :count
        )
        """,
    )
    suspend fun deleteOldest(count: Int, protectedKeys: List<String>)

    @Query("DELETE FROM cached_place WHERE `key` NOT IN (:protectedKeys)")
    suspend fun deleteAllExcept(protectedKeys: List<String>)
}

@Dao
interface StopTileDao {

    @Query("SELECT * FROM stop_tile WHERE tileKey IN (:keys)")
    suspend fun tilesIn(keys: List<String>): List<StopTileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tiles: List<StopTileEntity>)

    @Query("SELECT COUNT(*) FROM stop_tile")
    suspend fun countTiles(): Int

    @Query("DELETE FROM stop_tile")
    suspend fun deleteAll()
}

@Dao
interface SavedItineraryDao {

    @Query("SELECT * FROM saved_itinerary ORDER BY departureIso DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SavedItineraryEntity>>

    @Query("SELECT * FROM saved_itinerary")
    suspend fun getAll(): List<SavedItineraryEntity>

    @Query("SELECT * FROM saved_itinerary WHERE id = :id")
    suspend fun findById(id: String): SavedItineraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(itinerary: SavedItineraryEntity)

    @Query("DELETE FROM saved_itinerary WHERE id = :id")
    suspend fun delete(id: String)
}
