package pl.dakil.transport.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.FavoritesRepository
import pl.dakil.transport.data.prefs.SearchOptionsRepository
import pl.dakil.transport.data.repo.PlanRepository
import pl.dakil.transport.data.repo.PlanResult
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.ResultsRoute

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Content(val result: PlanResult) : ResultsUiState
    data class Error(val message: String) : ResultsUiState
}

const val REFRESH_INTERVAL_SECONDS = 30

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val favoritesRepository: FavoritesRepository,
    private val searchOptionsRepository: SearchOptionsRepository,
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
        timeIso = savedStateHandle["timeIso"],
    )

    /**
     * Options are read once from prefs and frozen for this results session, so the 30s
     * refresh loop and paging always query with the parameters the search was started with.
     */
    private lateinit var options: SearchOptions

    private val from = TransitLocation(route.fromName, route.fromLat, route.fromLon, route.fromStopId)
    private val to = TransitLocation(route.toName, route.toLat, route.toLon, route.toStopId)
    private val time: OffsetDateTime? = route.timeIso?.let { OffsetDateTime.parse(it) }

    val fromName: String get() = route.fromName
    val toName: String get() = route.toName

    /** This search's start+end pair as a favourite (date/time deliberately not included). */
    private val favoriteConnection = FavoriteConnection(from, to)

    val isFavorite: StateFlow<Boolean> = favoritesRepository.favorites
        .map { it.containsConnection(favoriteConnection) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        viewModelScope.launch { favoritesRepository.toggleConnection(favoriteConnection) }
    }

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState

    private val _secondsUntilRefresh = MutableStateFlow(REFRESH_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int> = _secondsUntilRefresh

    /** Current page of results; null is the initial page for the requested time. */
    private var pageCursor: String? = null
    private var refreshJob: Job? = null

    init {
        startRefreshLoop(showLoading = false)
    }

    private fun startRefreshLoop(showLoading: Boolean) {
        refreshJob?.cancel()
        if (showLoading) _uiState.value = ResultsUiState.Loading
        refreshJob = viewModelScope.launch {
            if (!::options.isInitialized) options = searchOptionsRepository.options.first()
            while (true) {
                refresh()
                for (seconds in REFRESH_INTERVAL_SECONDS downTo 1) {
                    _secondsUntilRefresh.value = seconds
                    delay(1_000)
                }
            }
        }
    }

    /** Loads the previous (earlier connections) page, if the API offered one. */
    fun showPrevious() {
        val cursor = (_uiState.value as? ResultsUiState.Content)?.result?.previousPageCursor ?: return
        pageCursor = cursor
        startRefreshLoop(showLoading = true)
    }

    /** Loads the next (later connections) page, if the API offered one. */
    fun showNext() {
        val cursor = (_uiState.value as? ResultsUiState.Content)?.result?.nextPageCursor ?: return
        pageCursor = cursor
        startRefreshLoop(showLoading = true)
    }

    private suspend fun refresh() {
        planRepository.plan(from, to, time, options, pageCursor).fold(
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
