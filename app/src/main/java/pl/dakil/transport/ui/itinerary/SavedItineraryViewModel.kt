package pl.dakil.transport.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SearchOptionsRepository
import pl.dakil.transport.data.repo.PlanRepository
import pl.dakil.transport.data.repo.SavedItineraryRepository
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SavedItinerary

/** What the saved-itinerary screen is showing, and where it came from. */
sealed interface SavedItineraryUiState {
    data object Loading : SavedItineraryUiState

    /** The saved journey no longer exists — unstarred from another screen, or never stored. */
    data object Missing : SavedItineraryUiState

    data class Shown(
        val saved: SavedItinerary,
        /** The journey to draw: the refreshed plan where one was found, else the snapshot. */
        val journey: Journey,
        /** True while the stored copy is what is on screen. */
        val fromSnapshot: Boolean,
    ) : SavedItineraryUiState
}

/**
 * Opens a pinned journey.
 *
 * A saved itinerary is deliberately never trusted to be current: the stored copy goes on screen
 * first — instantly, and with no connection at all — and a re-plan of the same run is attempted
 * behind it. The refreshed times only replace the snapshot when the returned journey is
 * recognisably the *same* run, matched on its legs' trip ids; anything else (a failed request, a
 * service that no longer operates, a different set of legs) leaves the stored copy standing.
 * Silently swapping in a different journey would be worse than showing a slightly old one.
 */
@HiltViewModel
class SavedItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savedItineraryRepository: SavedItineraryRepository,
    private val planRepository: PlanRepository,
    private val searchOptionsRepository: SearchOptionsRepository,
) : ViewModel() {

    private val id: String = savedStateHandle["id"]!!

    private val _uiState = MutableStateFlow<SavedItineraryUiState>(SavedItineraryUiState.Loading)
    val uiState: StateFlow<SavedItineraryUiState> = _uiState

    init {
        viewModelScope.launch {
            val saved = savedItineraryRepository.find(id)
            if (saved == null) {
                _uiState.value = SavedItineraryUiState.Missing
                return@launch
            }
            _uiState.value = SavedItineraryUiState.Shown(saved, saved.journey, fromSnapshot = true)
            refresh(saved)
        }
    }

    /** Re-runs the refresh; the stored copy stays on screen throughout either way. */
    fun retry() {
        val shown = _uiState.value as? SavedItineraryUiState.Shown ?: return
        viewModelScope.launch { refresh(shown.saved) }
    }

    private suspend fun refresh(saved: SavedItinerary) {
        val options = searchOptionsRepository.options.first()
        val refreshed = planRepository.plan(
            from = saved.from,
            to = saved.to,
            // The run's own scheduled departure, not "now": this is the journey the user pinned,
            // not whatever leaves next.
            time = saved.journey.departureScheduledTime,
            options = options,
            pageCursor = null,
            vias = emptyList(),
        ).getOrNull()
            ?.journeys
            ?.firstOrNull { it.isSameRunAs(saved.journey) }
            ?: return

        savedItineraryRepository.updateSnapshot(saved, refreshed)
        _uiState.value = SavedItineraryUiState.Shown(saved, refreshed, fromSnapshot = false)
    }
}

/**
 * Whether two journeys are the same set of vehicle runs.
 *
 * Trip ids alone: times move with delays, and the walking legs at either end can be re-routed
 * between plans without the journey being a different journey.
 */
private fun Journey.isSameRunAs(other: Journey): Boolean {
    val runs = legs.mapNotNull { it.tripId }
    val otherRuns = other.legs.mapNotNull { it.tripId }
    return runs.isNotEmpty() && runs == otherRuns
}
