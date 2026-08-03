package pl.dakil.transport.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SearchOptionsRepository
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.TransitLocation

data class DeparturesUiState(
    val stop: TransitLocation? = null,
    val options: SearchOptions = SearchOptions.DEFAULT,
    val dateTime: OffsetDateTime = OffsetDateTime.now(),
) {
    val canSearch: Boolean get() = stop != null
}

/**
 * The Departures tab's form. The picked stop lives in [SearchStateHolder] so it survives
 * switching bottom-bar tabs.
 */
@HiltViewModel
class DeparturesSearchViewModel @Inject constructor(
    searchStateHolder: SearchStateHolder,
    private val searchOptionsRepository: SearchOptionsRepository,
) : ViewModel() {

    private val dateTime = MutableStateFlow(OffsetDateTime.now())

    private val options = MutableStateFlow(SearchOptions.DEFAULT)

    val uiState: StateFlow<DeparturesUiState> = combine(
        searchStateHolder.departureStop,
        options,
        dateTime,
    ) { stop, options, dateTime ->
        DeparturesUiState(stop = stop, options = options, dateTime = dateTime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeparturesUiState())

    init {
        viewModelScope.launch {
            searchOptionsRepository.options.collect { options.value = it }
        }
    }

    /** Applies [transform] to the current options and persists the result. */
    fun updateOptions(transform: (SearchOptions) -> SearchOptions) {
        val updated = transform(options.value)
        options.value = updated
        viewModelScope.launch { searchOptionsRepository.save(updated) }
    }

    fun setDateTime(dateTime: OffsetDateTime) {
        this.dateTime.value = dateTime
    }
}
