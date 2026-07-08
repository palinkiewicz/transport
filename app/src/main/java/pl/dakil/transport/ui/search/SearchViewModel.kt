package pl.dakil.transport.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import pl.dakil.transport.data.location.LocationService
import pl.dakil.transport.data.repo.GeocodeRepository
import pl.dakil.transport.domain.model.TransitLocation

enum class SearchMode { CONNECTIONS, DEPARTURES }

data class SearchUiState(
    val mode: SearchMode = SearchMode.CONNECTIONS,
    val fromQuery: String = "",
    val fromSuggestions: List<TransitLocation> = emptyList(),
    val fromSelected: TransitLocation? = null,
    val toQuery: String = "",
    val toSuggestions: List<TransitLocation> = emptyList(),
    val toSelected: TransitLocation? = null,
    val maxTransfers: Int? = null,
    val dateTime: OffsetDateTime = OffsetDateTime.now(),
) {
    val canSearch: Boolean
        get() = fromSelected != null && (mode == SearchMode.DEPARTURES || toSelected != null)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val geocodeRepository: GeocodeRepository,
    private val locationService: LocationService,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val fromQueryFlow = MutableStateFlow("")
    private val toQueryFlow = MutableStateFlow("")

    init {
        observeSuggestions(fromQueryFlow) { suggestions ->
            _uiState.update { it.copy(fromSuggestions = suggestions) }
        }
        observeSuggestions(toQueryFlow) { suggestions ->
            _uiState.update { it.copy(toSuggestions = suggestions) }
        }
        searchStateHolder.pendingFrom.value?.let { selectFrom(it) }
        searchStateHolder.pendingTo.value?.let { selectTo(it) }
        searchStateHolder.pendingFrom.value = null
        searchStateHolder.pendingTo.value = null
    }

    private fun observeSuggestions(queryFlow: MutableStateFlow<String>, onResult: (List<TransitLocation>) -> Unit) {
        queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    val bias = locationService.lastKnownLocation()
                    flowOf(
                        geocodeRepository.suggest(query, bias?.lat, bias?.lon)
                            .getOrDefault(emptyList()),
                    )
                }
            }
            .onEach(onResult)
            .launchIn(viewModelScope)
    }

    fun setMode(mode: SearchMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun onFromQueryChange(query: String) {
        _uiState.update { it.copy(fromQuery = query, fromSelected = null) }
        fromQueryFlow.value = query
    }

    fun onToQueryChange(query: String) {
        _uiState.update { it.copy(toQuery = query, toSelected = null) }
        toQueryFlow.value = query
    }

    fun selectFrom(location: TransitLocation) {
        _uiState.update { it.copy(fromSelected = location, fromQuery = location.name, fromSuggestions = emptyList()) }
    }

    fun selectTo(location: TransitLocation) {
        _uiState.update { it.copy(toSelected = location, toQuery = location.name, toSuggestions = emptyList()) }
    }

    fun swapFromTo() {
        _uiState.update {
            it.copy(
                fromQuery = it.toQuery,
                fromSelected = it.toSelected,
                toQuery = it.fromQuery,
                toSelected = it.fromSelected,
            )
        }
    }

    fun setMaxTransfers(maxTransfers: Int?) {
        _uiState.update { it.copy(maxTransfers = maxTransfers) }
    }

    fun setDateTime(dateTime: OffsetDateTime) {
        _uiState.update { it.copy(dateTime = dateTime) }
    }
}
