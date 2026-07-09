package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.repo.MapStyleRepository
import pl.dakil.transport.data.repo.RoutesRepository
import pl.dakil.transport.data.repo.StopsRepository
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.search.SearchStateHolder

data class Viewport(val south: Double, val west: Double, val north: Double, val east: Double)

/** State of the "Show routes" overlay for the currently selected stop. */
sealed interface StopRoutesUiState {
    data object Hidden : StopRoutesUiState
    data object Loading : StopRoutesUiState
    data class Shown(val routes: List<RouteShape>) : StopRoutesUiState
    data class Error(val message: String) : StopRoutesUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val stopsRepository: StopsRepository,
    private val routesRepository: RoutesRepository,
    private val mapStyleRepository: MapStyleRepository,
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    private val viewport = MutableStateFlow<Viewport?>(null)

    /**
     * Patched bundled style JSON (base transit stop icons removed, sources repointed);
     * null only for the brief moment the asset is being read.
     */
    private val _styleJson = MutableStateFlow<String?>(null)
    val styleJson: StateFlow<String?> = _styleJson

    init {
        viewModelScope.launch {
            _styleJson.value = mapStyleRepository.transitFreeGmapsStyle()
        }
    }

    val stops: StateFlow<List<TransitLocation>> = viewport
        .filterNotNull()
        .distinctUntilChanged()
        .mapLatest { vp ->
            stopsRepository.stopsInViewport(vp.south, vp.west, vp.north, vp.east).getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStop = MutableStateFlow<TransitLocation?>(null)
    val selectedStop: StateFlow<TransitLocation?> = _selectedStop

    private val _stopRoutes = MutableStateFlow<StopRoutesUiState>(StopRoutesUiState.Hidden)
    val stopRoutes: StateFlow<StopRoutesUiState> = _stopRoutes

    private var routesJob: Job? = null

    /** Called once the map camera has settled (already debounced by the caller). */
    fun onViewportSettled(south: Double, west: Double, north: Double, east: Double) {
        viewport.value = Viewport(south, west, north, east)
    }

    fun selectStop(stop: TransitLocation) {
        if (_selectedStop.value != stop) hideRoutes()
        _selectedStop.value = stop
    }

    fun clearSelection() {
        _selectedStop.value = null
        hideRoutes()
    }

    fun showRoutes() {
        val stop = _selectedStop.value ?: return
        routesJob?.cancel()
        _stopRoutes.value = StopRoutesUiState.Loading
        routesJob = viewModelScope.launch {
            routesRepository.routesThroughStop(stop).fold(
                onSuccess = { routes ->
                    _stopRoutes.value = if (routes.isEmpty()) {
                        StopRoutesUiState.Error("No route shapes available for this stop")
                    } else {
                        StopRoutesUiState.Shown(routes)
                    }
                },
                onFailure = { error ->
                    _stopRoutes.value = StopRoutesUiState.Error(error.message ?: "Something went wrong")
                },
            )
        }
    }

    fun hideRoutes() {
        routesJob?.cancel()
        _stopRoutes.value = StopRoutesUiState.Hidden
    }

    fun beginHere(location: TransitLocation) = searchStateHolder.setBeginHere(location)

    fun finishHere(location: TransitLocation) = searchStateHolder.setFinishHere(location)
}
