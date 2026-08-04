package pl.dakil.transport.ui.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.repo.TimetableRepository
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.results.REFRESH_INTERVAL_SECONDS

/** One row of a trip's timetable. */
data class TripStop(
    val name: String,
    val time: OffsetDateTime,
    val scheduledTime: OffsetDateTime,
    val track: String?,
)

sealed interface TripUiState {
    data object Loading : TripUiState
    data class Content(
        val stops: List<TripStop>,
        /** True only when every transit leg of the run reports the amenity; null = feed silent. */
        val wheelchairAccessible: Boolean? = null,
        val bikesAllowed: Boolean? = null,
    ) : TripUiState
    data class Error(val error: AppError) : TripUiState
}

/** Collapses per-leg amenity flags into one value for the whole run (null when no leg reports). */
private fun List<Boolean>.allOrNull(): Boolean? = takeIf { it.isNotEmpty() }?.all { it }

/** Flattens the trip itinerary (joined interlined legs) into a single ordered stop list. */
fun Journey.toTripStops(): List<TripStop> = buildList {
    val transitLegs = legs.filter { it.isTransit }
    transitLegs.forEachIndexed { index, leg ->
        if (index == 0) {
            add(TripStop(leg.fromName, leg.startTime, leg.scheduledStartTime, leg.fromTrack))
        }
        leg.intermediateStops.forEach { stop ->
            val arrival = stop.arrivalTime ?: return@forEach
            add(TripStop(stop.name, arrival, stop.scheduledArrivalTime ?: arrival, stop.track))
        }
        add(TripStop(leg.toName, leg.endTime, leg.scheduledEndTime, leg.toTrack))
    }
}

@HiltViewModel
class TripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timetableRepository: TimetableRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle["tripId"]!!
    val lineLabel: String = savedStateHandle["lineLabel"]!!
    val headsign: String? = savedStateHandle["headsign"]
    val mode: TransportMode = TransportMode.fromApiValue(savedStateHandle["modeName"])
    val routeColor: String? = savedStateHandle["routeColor"]

    private val favoriteLine = FavoriteLine(
        label = lineLabel,
        headsign = headsign,
        mode = mode,
        routeColor = routeColor,
        tripId = tripId,
    )

    val isFavorite: StateFlow<Boolean> = favoritesRepository.favorites
        .map { it.containsLine(favoriteLine) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        viewModelScope.launch { favoritesRepository.toggleLine(favoriteLine) }
    }

    private val _uiState = MutableStateFlow<TripUiState>(TripUiState.Loading)
    val uiState: StateFlow<TripUiState> = _uiState

    /** Countdown to the next automatic reload; null once auto-refresh is off. */
    private val _secondsUntilRefresh = MutableStateFlow<Int?>(REFRESH_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int?> = _secondsUntilRefresh

    private var refreshJob: Job? = null

    init {
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        // Retrying from the error screen swaps it for the spinner, so the tap visibly does something.
        if (_uiState.value is TripUiState.Error) _uiState.value = TripUiState.Loading
        refreshJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            while (true) {
                refresh()
                if (!settings.autoRefreshEnabled) {
                    // One load, then wait for the screen's refresh button instead of polling.
                    _secondsUntilRefresh.value = null
                    break
                }
                for (seconds in settings.resultsRefreshSeconds downTo 1) {
                    _secondsUntilRefresh.value = seconds
                    delay(1_000)
                }
            }
        }
    }

    /** Reloads the timetable now — the only way back to fresh data with auto-refresh off. */
    fun refreshNow() = startRefreshLoop()

    private suspend fun refresh() {
        timetableRepository.trip(tripId).fold(
            onSuccess = { journey ->
                val transitLegs = journey.legs.filter { it.isTransit }
                _uiState.value = TripUiState.Content(
                    stops = journey.toTripStops(),
                    wheelchairAccessible = transitLegs.mapNotNull { it.wheelchairAccessible }.allOrNull(),
                    bikesAllowed = transitLegs.mapNotNull { it.bikesAllowed }.allOrNull(),
                )
            },
            onFailure = { error ->
                // runCatching also catches cancellation; a cancelled refresh is not a failure.
                if (error !is CancellationException && _uiState.value !is TripUiState.Content) {
                    _uiState.value = TripUiState.Error(error.toAppError())
                }
            },
        )
    }
}
