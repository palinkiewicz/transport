package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.transport.data.repo.MapStyleRepository
import pl.dakil.transport.data.repo.StopsRepository
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.search.SearchStateHolder

data class Viewport(val south: Double, val west: Double, val north: Double, val east: Double)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val stopsRepository: StopsRepository,
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

    /** Called once the map camera has settled (already debounced by the caller). */
    fun onViewportSettled(south: Double, west: Double, north: Double, east: Double) {
        viewport.value = Viewport(south, west, north, east)
    }

    fun beginHere(location: TransitLocation) = searchStateHolder.setBeginHere(location)

    fun finishHere(location: TransitLocation) = searchStateHolder.setFinishHere(location)
}
