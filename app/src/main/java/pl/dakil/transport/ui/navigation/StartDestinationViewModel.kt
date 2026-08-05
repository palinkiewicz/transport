package pl.dakil.transport.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.domain.model.DefaultTab
import pl.dakil.transport.ui.search.SearchStateHolder

/**
 * Resolves which tab the app opens on, once per process.
 *
 * Deliberately not a live view of the setting: the start destination is also the back stack's
 * anchor, so swapping it while the user is navigating would rebuild the graph under them. A
 * change takes effect the next time the app starts.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    /** Null until the stored setting has been read; the nav host waits rather than guess. */
    private val _startTab = MutableStateFlow<DefaultTab?>(null)
    val startTab: StateFlow<DefaultTab?> = _startTab

    /** Raised when another app hands over a destination — see [SearchStateHolder]. */
    val pendingRouteRequest: StateFlow<Boolean> = searchStateHolder.pendingRouteRequest

    fun consumeRouteRequest() {
        searchStateHolder.pendingRouteRequest.value = false
    }

    init {
        viewModelScope.launch {
            _startTab.value = settingsRepository.settings.first().defaultTab
        }
    }
}

/** The bottom-bar route a [DefaultTab] opens. */
fun DefaultTab.route(): Any = when (this) {
    DefaultTab.MAP -> MapRoute
    DefaultTab.CONNECTIONS -> ConnectionsRoute
    DefaultTab.DEPARTURES -> DeparturesRoute
    DefaultTab.FAVOURITES -> FavouritesRoute
}
