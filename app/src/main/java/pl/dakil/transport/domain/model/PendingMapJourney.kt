package pl.dakil.transport.domain.model

/**
 * A planned journey the user asked to see on the map, handed over from an itinerary through
 * [pl.dakil.transport.ui.search.SearchStateHolder] the way [PendingMapTrip] is.
 *
 * The whole [journey] travels, not an id to re-plan from: it is the itinerary the user is looking
 * at, times and geometry included, and a second plan call could answer with a different one.
 *
 * [endpoints] are the places it was planned between, which is what a saved itinerary is keyed by —
 * null where there is nothing to save (a journey opened from the Saved tab is already pinned), and
 * the pane then leaves its star out. [selectedStopId] is a stop of the journey to open focused on,
 * set when the map was opened by tapping that stop's row rather than the map action.
 */
data class PendingMapJourney(
    val journey: Journey,
    val fromName: String,
    val toName: String,
    val endpoints: Pair<TransitLocation, TransitLocation>?,
    val selectedStopId: String? = null,
)
