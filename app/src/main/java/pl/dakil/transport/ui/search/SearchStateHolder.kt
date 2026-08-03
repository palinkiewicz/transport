package pl.dakil.transport.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.ViaPoint

/**
 * The search forms' locations, held app-wide rather than per-screen.
 *
 * The Connections and Departures tabs are separate destinations that the bottom bar tears down
 * and rebuilds as the user switches between them, so a ViewModel-owned field would lose the
 * user's pick on every tab switch. Keeping the picks here also lets other screens fill them in
 * — the Map's "Begin here"/"Finish here", the Favourites list, and the full-screen location
 * picker all write straight into these flows instead of passing nav arguments around.
 *
 * [pendingMapLocation] is the one exception and still works as a one-shot signal: it flows the
 * other way, from a `MAP`-target pick to [pl.dakil.transport.ui.map.MapViewModel], which
 * consumes it to select the location and drive the camera, then clears it.
 */
@Singleton
class SearchStateHolder @Inject constructor() {
    /** Connections form: start. */
    val from = MutableStateFlow<TransitLocation?>(null)

    /** Connections form: destination. */
    val to = MutableStateFlow<TransitLocation?>(null)

    /** Connections form: intermediate stops, in travel order. At most [ViaPoint.MAX_VIA_POINTS]. */
    val vias = MutableStateFlow<List<ViaPoint>>(emptyList())

    /** Departures form: the stop whose board to show. */
    val departureStop = MutableStateFlow<TransitLocation?>(null)

    /** A map-target pick, consumed by the Map screen. */
    val pendingMapLocation = MutableStateFlow<TransitLocation?>(null)

    fun setBeginHere(location: TransitLocation) {
        from.value = location
    }

    fun setFinishHere(location: TransitLocation) {
        to.value = location
    }

    fun setDepartureStop(location: TransitLocation) {
        departureStop.value = location
    }

    fun setMapLocation(location: TransitLocation) {
        pendingMapLocation.value = location
    }

    /** Reverses the whole route, intermediate stops included — they are held in travel order. */
    fun swapFromTo() {
        val previousFrom = from.value
        from.value = to.value
        to.value = previousFrom
        vias.value = vias.value.reversed()
    }

    /**
     * Fills the intermediate stop at [index], appending a new one when [index] is past the end
     * (which is how the "Add stop" button adds one). Silently ignores anything beyond the API's
     * cap, and anything the API cannot route through — `via` takes stop ids only.
     */
    fun setVia(index: Int, location: TransitLocation) {
        if (location.stopId == null) return
        vias.value = vias.value.toMutableList().apply {
            if (index in indices) {
                this[index] = this[index].copy(location = location)
            } else if (size < ViaPoint.MAX_VIA_POINTS) {
                add(ViaPoint(location))
            }
        }
    }

    fun setViaMinimumStay(index: Int, minutes: Int) {
        vias.value = vias.value.mapIndexed { i, via ->
            if (i == index) via.copy(minimumStayMinutes = minutes) else via
        }
    }

    fun removeVia(index: Int) {
        vias.value = vias.value.filterIndexed { i, _ -> i != index }
    }
}
