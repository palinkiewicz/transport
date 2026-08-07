package pl.dakil.transport.ui.itinerary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R
import pl.dakil.transport.ui.components.rememberDateFormatter
import pl.dakil.transport.ui.navigation.TripRoute

/**
 * A pinned journey, opened from the Saved tab.
 *
 * Reuses [ItineraryScreen] wholesale — the only differences are where the journey comes from and
 * the note saying so. No star: getting here means it is already saved, and the Saved tab is
 * where it is removed from.
 */
@Composable
fun SavedItineraryScreen(
    onBack: () -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
    viewModel: SavedItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormatter = rememberDateFormatter()

    when (val current = state) {
        // The stored copy is read from disk in a single query, so this is a frame or two at most.
        SavedItineraryUiState.Loading -> ItineraryScreen(
            journey = null,
            fromName = "",
            toName = "",
            onBack = onBack,
            onOpenTrip = onOpenTrip,
        )
        SavedItineraryUiState.Missing -> ItineraryScreen(
            journey = null,
            fromName = "",
            toName = "",
            onBack = onBack,
            onOpenTrip = onOpenTrip,
        )
        is SavedItineraryUiState.Shown -> ItineraryScreen(
            journey = current.journey,
            fromName = current.saved.fromName,
            toName = current.saved.toName,
            onBack = onBack,
            onOpenTrip = onOpenTrip,
            savedNote = if (current.fromSnapshot) {
                stringResource(
                    R.string.saved_itinerary_snapshot_note,
                    dateFormatter.format(current.saved.savedAt),
                )
            } else {
                null
            },
        )
    }
}
