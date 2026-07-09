package pl.dakil.transport.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dakil.transport.data.repo.TimetableRepository
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.domain.model.StopDepartures
import pl.dakil.transport.domain.model.TransitLocation

sealed interface DeparturesUiState {
    data object Loading : DeparturesUiState
    data class Content(val departures: StopDepartures) : DeparturesUiState
    data class Error(val message: String) : DeparturesUiState
}

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

    private val _secondsUntilRefresh = MutableStateFlow(REFRESH_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int> = _secondsUntilRefresh

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                for (seconds in REFRESH_INTERVAL_SECONDS downTo 1) {
                    _secondsUntilRefresh.value = seconds
                    delay(1_000)
                }
            }
        }
    }

    private suspend fun refresh() {
        timetableRepository.departures(stop, time = time).fold(
            onSuccess = { result -> _uiState.value = DeparturesUiState.Content(result) },
            onFailure = { error ->
                if (_uiState.value !is DeparturesUiState.Content) {
                    _uiState.value = DeparturesUiState.Error(error.message ?: "Something went wrong")
                }
            },
        )
    }
}
