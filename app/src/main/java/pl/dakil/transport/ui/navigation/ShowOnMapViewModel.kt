package pl.dakil.transport.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.ui.search.SearchStateHolder

/**
 * Hands a subject over to the Map screen, for the nav host to call as it pushes one.
 *
 * Every screen that offers "see this on the map" — a departure board, the saved lines, an
 * itinerary and its legs — needs the same two things to happen together: the subject goes into
 * [SearchStateHolder]'s one-shot signal, and a map is pushed to pick it up. Doing it here keeps
 * that pairing in the one place that owns navigation, and spares four screens a ViewModel method
 * whose only job is to reach the same singleton.
 */
@HiltViewModel
class ShowOnMapViewModel @Inject constructor(
    private val searchStateHolder: SearchStateHolder,
) : ViewModel() {

    fun showTrip(trip: PendingMapTrip) = searchStateHolder.setMapTrip(trip)

    fun showJourney(journey: PendingMapJourney) = searchStateHolder.setMapJourney(journey)
}
