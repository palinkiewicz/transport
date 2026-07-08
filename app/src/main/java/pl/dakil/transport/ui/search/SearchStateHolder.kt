package pl.dakil.transport.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import pl.dakil.transport.domain.model.TransitLocation

/**
 * Lets other screens (e.g. the Map's "Begin here"/"Finish here" actions) prefill the
 * Search screen's fields. [SearchViewModel] consumes and clears these on start.
 */
@Singleton
class SearchStateHolder @Inject constructor() {
    val pendingFrom = MutableStateFlow<TransitLocation?>(null)
    val pendingTo = MutableStateFlow<TransitLocation?>(null)

    fun setBeginHere(location: TransitLocation) {
        pendingFrom.value = location
    }

    fun setFinishHere(location: TransitLocation) {
        pendingTo.value = location
    }
}
