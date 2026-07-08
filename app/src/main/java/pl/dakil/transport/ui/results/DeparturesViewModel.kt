package pl.dakil.transport.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dakil.transport.data.repo.TimetableRepository
import pl.dakil.transport.domain.model.StopDepartures
import pl.dakil.transport.domain.model.TransitLocation

sealed interface DeparturesUiState {
    data object Loading : DeparturesUiState
    data class Content(val departures: StopDepartures) : DeparturesUiState
    data class Error(val message: String) : DeparturesUiState
}

private const val REFRESH_INTERVAL_MS = 30_000L

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

    val stopName: String get() = stop.name

    private val _uiState = MutableStateFlow<DeparturesUiState>(DeparturesUiState.Loading)
    val uiState: StateFlow<DeparturesUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        timetableRepository.departures(stop).fold(
            onSuccess = { result -> _uiState.value = DeparturesUiState.Content(result) },
            onFailure = { error ->
                if (_uiState.value !is DeparturesUiState.Content) {
                    _uiState.value = DeparturesUiState.Error(error.message ?: "Something went wrong")
                }
            },
        )
    }
}
