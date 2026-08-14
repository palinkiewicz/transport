package pl.dakil.transport.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SessionStateRepository
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.SessionState
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
 * [pendingMapLocation] and [pendingMapTrip] are the exceptions and work as one-shot signals: they
 * flow the other way, towards [pl.dakil.transport.ui.map.MapViewModel] — a `MAP`-target pick to be
 * selected and centred on, and a trip opened from a timetable to be followed — which consumes and
 * clears them. They are also the fields never persisted: a signal that outlived the process would
 * fire at the wrong moment.
 */
@OptIn(FlowPreview::class)
@Singleton
class SearchStateHolder @Inject constructor(
    private val sessionStateRepository: SessionStateRepository,
    private val settingsRepository: SettingsRepository,
) {
    // Its own scope: this is a process-lifetime singleton with no lifecycle to borrow.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Held back until the stored picks are read, so the empty initial state is never saved over them. */
    private val restored = CompletableDeferred<Unit>()

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

    /** A trip to follow on the map, raised wherever a line is tapped to be seen on one. */
    val pendingMapTrip = MutableStateFlow<PendingMapTrip?>(null)

    /**
     * Raised when another app hands over a point, and consumed by the nav host to open the Map
     * tab so the point in [pendingMapLocation] gets shown. Like it, this is a one-shot signal
     * and is never persisted — a request that outlived the process would fire at the wrong moment.
     */
    val pendingMapRequest = MutableStateFlow(false)

    init {
        scope.launch {
            if (settingsRepository.settings.first().rememberLastSearch) {
                val stored = sessionStateRepository.state.first()
                // Only fill what the user has not already picked in the meantime — the forms
                // are usable before this read lands.
                if (from.value == null) from.value = stored.from
                if (to.value == null) to.value = stored.to
                if (vias.value.isEmpty()) vias.value = stored.vias
                if (departureStop.value == null) departureStop.value = stored.departureStop
            }
            restored.complete(Unit)
        }
        scope.launch {
            restored.await()
            combine(from, to, vias, departureStop) { from, to, vias, departureStop ->
                SessionState(from = from, to = to, vias = vias, departureStop = departureStop)
            }
                // The forms are edited a field at a time; one write per settled state is plenty.
                .debounce(SAVE_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { state ->
                    if (settingsRepository.settings.first().rememberLastSearch) {
                        sessionStateRepository.saveSearch(state)
                    }
                }
        }
    }

    fun setBeginHere(location: TransitLocation) {
        from.value = location
    }

    fun setFinishHere(location: TransitLocation) {
        to.value = location
    }

    /**
     * A point handed over by another app. It is shown on the map and selected rather than
     * dropped into a search field: the panel it opens offers routing to (or from) it alongside
     * its surroundings, so nothing is assumed about what the user wants to do with it.
     */
    fun setExternalLocation(location: TransitLocation) {
        pendingMapLocation.value = location
        pendingMapRequest.value = true
    }

    fun clearBeginHere() {
        from.value = null
    }

    fun clearFinishHere() {
        to.value = null
    }

    fun setDepartureStop(location: TransitLocation) {
        departureStop.value = location
    }

    fun clearDepartureStop() {
        departureStop.value = null
    }

    fun setMapLocation(location: TransitLocation) {
        pendingMapLocation.value = location
    }

    /**
     * A trip the user asked to see on the map. The caller pushes the map itself — everywhere this
     * is raised from is already inside the nav host, unlike a point shared by another app.
     */
    fun setMapTrip(trip: PendingMapTrip) {
        pendingMapTrip.value = trip
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

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
