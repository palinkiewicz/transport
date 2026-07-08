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
import pl.dakil.transport.data.repo.PlanRepository
import pl.dakil.transport.data.repo.PlanResult
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.ResultsRoute

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Content(val result: PlanResult) : ResultsUiState
    data class Error(val message: String) : ResultsUiState
}

private const val REFRESH_INTERVAL_MS = 30_000L

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
) : ViewModel() {

    private val route = ResultsRoute(
        fromName = savedStateHandle["fromName"]!!,
        fromLat = savedStateHandle["fromLat"]!!,
        fromLon = savedStateHandle["fromLon"]!!,
        fromStopId = savedStateHandle["fromStopId"],
        toName = savedStateHandle["toName"]!!,
        toLat = savedStateHandle["toLat"]!!,
        toLon = savedStateHandle["toLon"]!!,
        toStopId = savedStateHandle["toStopId"],
        maxTransfers = savedStateHandle["maxTransfers"],
        timeIso = savedStateHandle["timeIso"],
    )

    private val from = TransitLocation(route.fromName, route.fromLat, route.fromLon, route.fromStopId)
    private val to = TransitLocation(route.toName, route.toLat, route.toLon, route.toStopId)
    private val time: OffsetDateTime? = route.timeIso?.let { OffsetDateTime.parse(it) }

    val fromName: String get() = route.fromName
    val toName: String get() = route.toName

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        planRepository.plan(from, to, time, route.maxTransfers).fold(
            onSuccess = { result -> _uiState.value = ResultsUiState.Content(result) },
            onFailure = { error ->
                if (_uiState.value !is ResultsUiState.Content) {
                    _uiState.value = ResultsUiState.Error(error.message ?: "Something went wrong")
                }
            },
        )
    }

    fun journeyAt(index: Int): Journey? =
        (_uiState.value as? ResultsUiState.Content)?.result?.journeys?.getOrNull(index)
}
