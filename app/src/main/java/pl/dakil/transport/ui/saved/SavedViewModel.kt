package pl.dakil.transport.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.repo.SavedItineraryRepository
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.search.SearchStateHolder

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val savedItineraryRepository: SavedItineraryRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    val favorites: StateFlow<Favorites> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Favorites.EMPTY)

    /** Journeys pinned in full, newest departure first. */
    val itineraries: StateFlow<List<SavedItinerary>> = savedItineraryRepository.itineraries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeItinerary(id: String) {
        viewModelScope.launch { savedItineraryRepository.delete(id) }
    }

    fun removeLocation(location: TransitLocation) {
        viewModelScope.launch { favoritesRepository.toggleLocation(location) }
    }

    fun removeConnection(connection: FavoriteConnection) {
        viewModelScope.launch { favoritesRepository.toggleConnection(connection) }
    }

    fun removeLine(line: FavoriteLine) {
        viewModelScope.launch { favoritesRepository.toggleLine(line) }
    }

    /** Tapping a favourite place plans a trip to it: prefill the Search screen's destination. */
    fun setSearchDestination(location: TransitLocation) = searchStateHolder.setFinishHere(location)
}
