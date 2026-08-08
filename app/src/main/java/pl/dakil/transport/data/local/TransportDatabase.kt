package pl.dakil.transport.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's offline store: everywhere it has looked, and every journey the user pinned.
 *
 * Deliberately separate from the DataStore repositories in `data/prefs/`, which hold choices
 * the user made. This holds data the app *fetched* — it can grow to tens of thousands of rows,
 * needs indexed spatial and text lookups, and is safe to throw away and refetch.
 */
@Database(
    entities = [CachedPlaceEntity::class, StopTileEntity::class, SavedItineraryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TransportDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun stopTileDao(): StopTileDao
    abstract fun savedItineraryDao(): SavedItineraryDao

    companion object {
        const val NAME = "transport_cache"

        /**
         * Adds `cached_place.importance` and `saved_itinerary.lastRefreshedAt`.
         *
         * Written out rather than left to a destructive fallback because `saved_itinerary` is the
         * one table here that cannot be refetched — dropping it would throw away journeys the
         * user pinned precisely so they would still be there. Existing places get importance 0,
         * which simply means "the API never told us", exactly as for a place it omits it for.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_place ADD COLUMN importance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE saved_itinerary ADD COLUMN lastRefreshedAt INTEGER")
            }
        }
    }
}
