package pl.dakil.transport.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import pl.dakil.transport.domain.model.TransitLocation

/**
 * Lets other screens (e.g. the Map's "Begin here"/"Finish here" actions) prefill the
 * Search screen's fields. [SearchViewModel] consumes and clears these on start.
 * [pendingMapLocation] flows the other way: a pick made in the map's search field,
 * consumed by [pl.dakil.transport.ui.map.MapViewModel] to select and center it.
 */
@Singleton
class SearchStateHolder @Inject constructor() {
    val pendingFrom = MutableStateFlow<TransitLocation?>(null)
    val pendingTo = MutableStateFlow<TransitLocation?>(null)
    val pendingMapLocation = MutableStateFlow<TransitLocation?>(null)

    fun setBeginHere(location: TransitLocation) {
        pendingFrom.value = location
    }

    fun setFinishHere(location: TransitLocation) {
        pendingTo.value = location
    }

    fun setMapLocation(location: TransitLocation) {
        pendingMapLocation.value = location
    }
}
