package pl.dakil.transport.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.dakil.transport.data.local.PlaceDao
import pl.dakil.transport.data.local.SavedItineraryDao
import pl.dakil.transport.data.local.StopTileDao
import pl.dakil.transport.data.local.TransportDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TransportDatabase =
        Room.databaseBuilder(context, TransportDatabase::class.java, TransportDatabase.NAME)
            .addMigrations(TransportDatabase.MIGRATION_1_2)
            // Last resort only, for a version gap no migration covers. The place and tile tables
            // are a cache of a public API and cost only a few requests to rebuild — but
            // `saved_itinerary` cannot be refetched, so every schema change touching it must
            // come with a real migration above rather than fall through to here.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePlaceDao(database: TransportDatabase): PlaceDao = database.placeDao()

    @Provides
    fun provideStopTileDao(database: TransportDatabase): StopTileDao = database.stopTileDao()

    @Provides
    fun provideSavedItineraryDao(database: TransportDatabase): SavedItineraryDao =
        database.savedItineraryDao()
}
