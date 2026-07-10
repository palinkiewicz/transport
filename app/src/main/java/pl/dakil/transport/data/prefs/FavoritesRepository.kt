package pl.dakil.transport.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.TransitLocation

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

private val FAVORITES_KEY = stringPreferencesKey("favorites")

/** Persists the user's starred locations/connections/lines across app launches. */
@Singleton
class FavoritesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    /** Stored as one JSON blob so [Favorites] can grow fields without a prefs migration. */
    val favorites: Flow<Favorites> = context.favoritesDataStore.data.map { prefs ->
        prefs[FAVORITES_KEY].decodeOrEmpty()
    }

    suspend fun toggleLocation(location: TransitLocation) = update { it.toggleLocation(location) }

    suspend fun toggleConnection(connection: FavoriteConnection) = update { it.toggleConnection(connection) }

    suspend fun toggleLine(line: FavoriteLine) = update { it.toggleLine(line) }

    /** Read-modify-write inside one DataStore edit, so concurrent toggles can't lose updates. */
    private suspend fun update(transform: (Favorites) -> Favorites) {
        context.favoritesDataStore.edit { prefs ->
            prefs[FAVORITES_KEY] =
                json.encodeToString(Favorites.serializer(), transform(prefs[FAVORITES_KEY].decodeOrEmpty()))
        }
    }

    private fun String?.decodeOrEmpty(): Favorites =
        this?.let { stored -> runCatching { json.decodeFromString<Favorites>(stored) }.getOrNull() }
            ?: Favorites.EMPTY
}
