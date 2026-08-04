package pl.dakil.transport.ui.results

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SearchOptionsRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.remote.toAppError
import pl.dakil.transport.data.repo.TimetableRepository
import pl.dakil.transport.domain.model.AppError
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.StopDepartures
import pl.dakil.transport.domain.model.TransitLocation

sealed interface DeparturesUiState {
    data object Loading : DeparturesUiState
    data class Content(val departures: StopDepartures) : DeparturesUiState
    data class Error(val error: AppError) : DeparturesUiState
}

/** True only while actual departures are on screen — an empty board is not content to keep. */
private val DeparturesUiState.hasDepartures: Boolean
    get() = this is DeparturesUiState.Content && departures.departures.isNotEmpty()

data class DepartureGroup(
    val poleStopId: String?,
    val header: String,
    val departures: List<Departure>,
)

/** Groups same-named-stop departures by direction pole, with [clickedPoleStopId]'s group first. */
fun List<Departure>.groupedByPole(clickedPoleStopId: String?): List<DepartureGroup> {
    val byPole = groupBy { it.poleStopId }
    return byPole.keys
        .sortedBy { key -> if (key == clickedPoleStopId) 0 else 1 }
        .map { key ->
            val group = byPole.getValue(key)
            val headsigns = group.mapNotNull { it.headsign }.distinct().take(3)
            val track = group.firstNotNullOfOrNull { it.track }
            val headerParts = buildList {
                if (headsigns.isNotEmpty()) add("towards " + headsigns.joinToString(" / "))
                if (track != null) add("Platform $track")
            }
            DepartureGroup(
                poleStopId = key,
                header = headerParts.joinToString(" · ").ifEmpty { "Departures" },
                departures = group,
            )
        }
}

@HiltViewModel
class DeparturesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timetableRepository: TimetableRepository,
    private val searchOptionsRepository: SearchOptionsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val stop = TransitLocation(
        name = savedStateHandle["stopName"]!!,
        lat = savedStateHandle["lat"]!!,
        lon = savedStateHandle["lon"]!!,
        stopId = savedStateHandle["stopId"],
    )

    private val time: OffsetDateTime? =
        savedStateHandle.get<String>("timeIso")?.let { OffsetDateTime.parse(it) }

    val stopName: String get() = stop.name
    val clickedPoleStopId: String? get() = stop.stopId

    private val _uiState = MutableStateFlow<DeparturesUiState>(DeparturesUiState.Loading)
    val uiState: StateFlow<DeparturesUiState> = _uiState

    /** Countdown to the next automatic reload; null once auto-refresh is off. */
    private val _secondsUntilRefresh = MutableStateFlow<Int?>(REFRESH_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int?> = _secondsUntilRefresh

    /** Frozen for the session like [ResultsViewModel]'s options — see the note there. */
    private lateinit var options: SearchOptions

    private var refreshJob: Job? = null

    init {
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        // Retrying from the error or no-departures screen swaps it for the spinner, so the tap
        // visibly does something; with departures on screen the reload stays silent as before.
        if (!_uiState.value.hasDepartures) _uiState.value = DeparturesUiState.Loading
        refreshJob = viewModelScope.launch {
            if (!::options.isInitialized) options = searchOptionsRepository.options.first()
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

    /** Reloads the board now — the only way back to fresh data with auto-refresh off. */
    fun refreshNow() = startRefreshLoop()

    private suspend fun refresh() {
        timetableRepository.departures(stop, time = time, options = options).fold(
            onSuccess = { result -> _uiState.value = DeparturesUiState.Content(result) },
            onFailure = { error ->
                // runCatching also catches cancellation; a cancelled refresh is not a failure.
                if (error !is CancellationException && !_uiState.value.hasDepartures) {
                    _uiState.value = DeparturesUiState.Error(error.toAppError())
                }
            },
        )
    }
}
