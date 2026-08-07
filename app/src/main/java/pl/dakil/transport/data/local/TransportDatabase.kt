package pl.dakil.transport.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's offline store: everywhere it has looked, and every journey the user pinned.
 *
 * Deliberately separate from the DataStore repositories in `data/prefs/`, which hold choices
 * the user made. This holds data the app *fetched* — it can grow to tens of thousands of rows,
 * needs indexed spatial and text lookups, and is safe to throw away and refetch.
 */
@Database(
    entities = [CachedPlaceEntity::class, StopTileEntity::class, SavedItineraryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TransportDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun stopTileDao(): StopTileDao
    abstract fun savedItineraryDao(): SavedItineraryDao

    companion object {
        const val NAME = "transport_cache"
    }
}
