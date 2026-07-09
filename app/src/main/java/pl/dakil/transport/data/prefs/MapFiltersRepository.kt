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
import pl.dakil.transport.domain.model.MapFilters

private val Context.mapFiltersDataStore by preferencesDataStore(name = "map_filters")

private val FILTERS_KEY = stringPreferencesKey("filters")

/** Persists the map's layer filters across app launches. */
@Singleton
class MapFiltersRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    /** Stored as one JSON blob so [MapFilters] can grow fields without a prefs migration. */
    val filters: Flow<MapFilters> = context.mapFiltersDataStore.data.map { prefs ->
        prefs[FILTERS_KEY]
            ?.let { stored -> runCatching { json.decodeFromString<MapFilters>(stored) }.getOrNull() }
            ?: MapFilters.DEFAULT
    }

    suspend fun save(filters: MapFilters) {
        context.mapFiltersDataStore.edit { prefs ->
            prefs[FILTERS_KEY] = json.encodeToString(MapFilters.serializer(), filters)
        }
    }
}
