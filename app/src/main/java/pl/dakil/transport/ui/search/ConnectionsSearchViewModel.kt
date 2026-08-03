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

data class ConnectionsUiState(
    val from: TransitLocation? = null,
    val to: TransitLocation? = null,
    val options: SearchOptions = SearchOptions.DEFAULT,
    val dateTime: OffsetDateTime = OffsetDateTime.now(),
) {
    /**
     * Start and destination are the same place — planning that trip makes the API answer with
     * an HTTP 500 or a nonsensical one-minute walk, so the search is blocked instead.
     */
    val hasSameEndpoints: Boolean
        get() = from != null && to != null && from.isSamePlaceAs(to)

    val canSearch: Boolean
        get() = from != null && to != null && !hasSameEndpoints
}

/**
 * The Connections tab's form. The picked places live in [SearchStateHolder] so they survive
 * switching bottom-bar tabs and can be filled in from the Map and Favourites screens.
 */
@HiltViewModel
class ConnectionsSearchViewModel @Inject constructor(
    private val searchStateHolder: SearchStateHolder,
    private val searchOptionsRepository: SearchOptionsRepository,
) : ViewModel() {

    private val dateTime = MutableStateFlow(OffsetDateTime.now())

    /** Mirrors persisted options so the panel reflects edits immediately; see [updateOptions]. */
    private val options = MutableStateFlow(SearchOptions.DEFAULT)

    val uiState: StateFlow<ConnectionsUiState> = combine(
        searchStateHolder.from,
        searchStateHolder.to,
        options,
        dateTime,
    ) { from, to, options, dateTime ->
        ConnectionsUiState(from = from, to = to, options = options, dateTime = dateTime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionsUiState())

    init {
        viewModelScope.launch {
            searchOptionsRepository.options.collect { options.value = it }
        }
    }

    fun swapFromTo() = searchStateHolder.swapFromTo()

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
