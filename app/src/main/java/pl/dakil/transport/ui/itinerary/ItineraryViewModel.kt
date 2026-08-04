package pl.dakil.transport.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.transport.data.prefs.SettingsRepository

/**
 * The itinerary screen is otherwise stateless — it is handed its [pl.dakil.transport.domain.model.Journey]
 * by the results graph's ViewModel. This exists only to feed it the map-display settings.
 */
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Whether the itinerary map labels the stops where you board and alight. */
    val showStopNames: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showItineraryStopNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
