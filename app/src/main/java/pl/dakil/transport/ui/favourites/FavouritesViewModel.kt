package pl.dakil.transport.ui.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Favorites
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.search.SearchStateHolder

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    val favorites: StateFlow<Favorites> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Favorites.EMPTY)

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
