package pl.dakil.transport.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SearchOptionsRepository
import pl.dakil.transport.data.repo.PlanRepository
import pl.dakil.transport.data.repo.SavedItineraryRepository
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SavedItinerary

/** How current the journey on screen is, and why. */
enum class SavedItineraryFreshness {
    /** Being checked against the API right now; the stored copy is on screen meanwhile. */
    REFRESHING,

    /** Just checked — the times shown are the API's current ones. */
    CURRENT,

    /** The API could not be reached. The stored copy stands, with when it was last checked. */
    UNREACHABLE,

    /** The API answered, but no longer offers this run — it was cancelled, or its day has gone. */
    NO_LONGER_RUNNING,

    /**
     * The departure is in the past, so there is nothing to check. Not a failure: a journey that
     * has already happened is a record, and its stored times are the right ones to show.
     */
    DEPARTED,
}

/** What the saved-itinerary screen is showing, and where it came from. */
sealed interface SavedItineraryUiState {
    data object Loading : SavedItineraryUiState

    /** The saved journey no longer exists — unstarred from another screen, or never stored. */
    data object Missing : SavedItineraryUiState

    data class Shown(
        val saved: SavedItinerary,
        /** The journey to draw: the refreshed plan where one was found, else the stored copy. */
        val journey: Journey,
        val freshness: SavedItineraryFreshness,
    ) : SavedItineraryUiState
}

/**
 * Opens a pinned journey.
 *
 * The stored copy goes on screen first — instantly, and with no connection at all — and a
 * re-plan of the same run is attempted behind it. The refreshed times only replace the stored
 * ones when the returned journey is recognisably the *same* run, matched on its legs' trip ids;
 * silently swapping in a different journey would be worse than showing a slightly old one.
 *
 * The distinction the screen cares about is not "is this the stored copy" — it almost always is
 * for the first moment — but *why*: a run that was just checked needs no caveat, one that could
 * not be reached needs to say when it was last checked, and one whose departure has passed
 * should not claim to be either.
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

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = savedItineraryRepository.find(id)
            if (saved == null) {
                _uiState.value = SavedItineraryUiState.Missing
                return@launch
            }
            show(saved, saved.journey, SavedItineraryFreshness.REFRESHING)
            startRefresh(saved)
        }
    }

    /** Re-runs the check. The stored copy stays on screen throughout, whatever the outcome. */
    fun retry() {
        val shown = _uiState.value as? SavedItineraryUiState.Shown ?: return
        startRefresh(shown.saved)
    }

    private fun startRefresh(saved: SavedItinerary) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refresh(saved) }
    }

    private suspend fun refresh(saved: SavedItinerary) {
        if (!saved.isRefreshable(OffsetDateTime.now())) {
            show(saved, saved.journey, SavedItineraryFreshness.DEPARTED)
            return
        }
        show(saved, saved.journey, SavedItineraryFreshness.REFRESHING)

        val options = searchOptionsRepository.options.first()
        planRepository.plan(
            from = saved.from,
            to = saved.to,
            // The run's own scheduled departure, not "now": this is the journey the user pinned,
            // not whatever leaves next.
            time = saved.journey.departureScheduledTime,
            options = options,
            pageCursor = null,
            vias = emptyList(),
        ).fold(
            onSuccess = { plan ->
                val match = plan.journeys.firstOrNull { it.isSameRunAs(saved.journey) }
                savedItineraryRepository.recordRefresh(saved, match, System.currentTimeMillis())
                show(
                    savedItineraryRepository.find(saved.id) ?: saved,
                    match ?: saved.journey,
                    if (match != null) {
                        SavedItineraryFreshness.CURRENT
                    } else {
                        SavedItineraryFreshness.NO_LONGER_RUNNING
                    },
                )
            },
            onFailure = { error ->
                // runCatching swallows cancellation into a failure; the screen going away is
                // not the API being unreachable.
                if (error !is CancellationException) {
                    show(saved, saved.journey, SavedItineraryFreshness.UNREACHABLE)
                }
            },
        )
    }

    private fun show(saved: SavedItinerary, journey: Journey, freshness: SavedItineraryFreshness) {
        _uiState.value = SavedItineraryUiState.Shown(saved, journey, freshness)
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
