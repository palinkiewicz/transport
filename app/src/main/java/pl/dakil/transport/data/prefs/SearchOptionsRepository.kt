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
import pl.dakil.transport.domain.model.SearchOptions

private val Context.searchOptionsDataStore by preferencesDataStore(name = "search_options")

private val OPTIONS_KEY = stringPreferencesKey("options")

/** Persists the Search screen's max transfers + advanced options across app launches. */
@Singleton
class SearchOptionsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    /** Stored as one JSON blob so [SearchOptions] can grow fields without a prefs migration. */
    val options: Flow<SearchOptions> = context.searchOptionsDataStore.data.map { prefs ->
        prefs[OPTIONS_KEY]
            ?.let { stored -> runCatching { json.decodeFromString<SearchOptions>(stored) }.getOrNull() }
            ?: SearchOptions.DEFAULT
    }

    suspend fun save(options: SearchOptions) {
        context.searchOptionsDataStore.edit { prefs ->
            prefs[OPTIONS_KEY] = json.encodeToString(SearchOptions.serializer(), options)
        }
    }
}
