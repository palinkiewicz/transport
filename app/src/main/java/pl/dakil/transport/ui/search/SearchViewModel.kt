package pl.dakil.transport.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.transport.domain.model.TransitLocation

enum class SearchMode { CONNECTIONS, DEPARTURES }

data class SearchUiState(
    val mode: SearchMode = SearchMode.CONNECTIONS,
    val fromSelected: TransitLocation? = null,
    val toSelected: TransitLocation? = null,
    val maxTransfers: Int? = null,
    val dateTime: OffsetDateTime = OffsetDateTime.now(),
) {
    val canSearch: Boolean
        get() = fromSelected != null && (mode == SearchMode.DEPARTURES || toSelected != null)
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    init {
        // Collected (not consumed once): prefills arrive both from the Map screen's
        // "Begin/Finish here" and from the location picker popping back to this screen,
        // and this ViewModel outlives both while the Search tab sits in the back stack.
        viewModelScope.launch {
            searchStateHolder.pendingFrom.collect { location ->
                if (location != null) {
                    selectFrom(location)
                    searchStateHolder.pendingFrom.value = null
                }
            }
        }
        viewModelScope.launch {
            searchStateHolder.pendingTo.collect { location ->
                if (location != null) {
                    selectTo(location)
                    searchStateHolder.pendingTo.value = null
                }
            }
        }
    }

    fun setMode(mode: SearchMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun selectFrom(location: TransitLocation) {
        _uiState.update { it.copy(fromSelected = location) }
    }

    fun selectTo(location: TransitLocation) {
        _uiState.update { it.copy(toSelected = location) }
    }

    fun swapFromTo() {
        _uiState.update { it.copy(fromSelected = it.toSelected, toSelected = it.fromSelected) }
    }

    fun setMaxTransfers(maxTransfers: Int?) {
        _uiState.update { it.copy(maxTransfers = maxTransfers) }
    }

    fun setDateTime(dateTime: OffsetDateTime) {
        _uiState.update { it.copy(dateTime = dateTime) }
    }
}
