package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dakil.transport.data.repo.MapStyleRepository

/**
 * Exposes the patched bundled map style to screens embedding a map without the full
 * [MapViewModel] (e.g. [RouteMap] inside the itinerary screen).
 */
@HiltViewModel
class MapStyleViewModel @Inject constructor(
    mapStyleRepository: MapStyleRepository,
) : ViewModel() {

    /** Patched bundled style JSON; null only for the brief moment the asset is being read. */
    private val _styleJson = MutableStateFlow<String?>(null)
    val styleJson: StateFlow<String?> = _styleJson

    init {
        viewModelScope.launch {
            _styleJson.value = mapStyleRepository.transitFreeGmapsStyle()
        }
    }
}
