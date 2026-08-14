package pl.dakil.transport.ui.itinerary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.OffsetDateTime
import pl.dakil.transport.R
import pl.dakil.transport.ui.components.rememberDateFormatter
import pl.dakil.transport.ui.components.rememberTimeFormatter
import pl.dakil.transport.domain.model.PendingMapTrip

/**
 * A pinned journey, opened from the Saved tab.
 *
 * Reuses [ItineraryScreen] wholesale — the only differences are where the journey comes from and
 * the note about how current it is. No star: getting here means it is already saved, and the
 * Saved tab is where it is removed from.
 */
@Composable
fun SavedItineraryScreen(
    onBack: () -> Unit,
    onOpenTrip: (PendingMapTrip) -> Unit,
    viewModel: SavedItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        // The stored copy is one indexed read from disk, so this is a frame or two at most.
        SavedItineraryUiState.Loading,
        SavedItineraryUiState.Missing,
        -> ItineraryScreen(
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
            savedNote = savedNote(current),
            // Only worth offering where trying again could change something.
            onRetryRefresh = viewModel::retry
                .takeIf { current.freshness == SavedItineraryFreshness.UNREACHABLE },
        )
    }
}

/**
 * The caveat above the itinerary, or null when there is nothing to say.
 *
 * Nothing is said in the ordinary case — the run was just checked and the times are current.
 * A note appears only when the times might not be what the user would get by searching now, and
 * then it says *why*, and when the run was last successfully checked.
 */
@Composable
private fun savedNote(state: SavedItineraryUiState.Shown): String? {
    val checkedLabel = lastCheckedLabel(state.saved.lastRefreshedAt)
    return when (state.freshness) {
        SavedItineraryFreshness.CURRENT, SavedItineraryFreshness.REFRESHING -> null
        SavedItineraryFreshness.UNREACHABLE -> if (checkedLabel != null) {
            stringResource(R.string.saved_itinerary_offline_note, checkedLabel)
        } else {
            stringResource(R.string.saved_itinerary_offline_never_note)
        }
        SavedItineraryFreshness.NO_LONGER_RUNNING ->
            stringResource(R.string.saved_itinerary_not_running_note)
        SavedItineraryFreshness.DEPARTED -> stringResource(
            R.string.saved_itinerary_departed_note,
            rememberDateFormatter().format(state.saved.savedAt),
        )
    }
}

/** "today at 13:16" for a check made today, otherwise the date and time. */
@Composable
private fun lastCheckedLabel(at: OffsetDateTime?): String? {
    val timeFormatter = rememberTimeFormatter()
    val dateFormatter = rememberDateFormatter()
    if (at == null) return null
    return if (at.toLocalDate() == OffsetDateTime.now().toLocalDate()) {
        stringResource(R.string.saved_itinerary_checked_today, timeFormatter.format(at))
    } else {
        stringResource(
            R.string.saved_itinerary_checked_on,
            dateFormatter.format(at),
            timeFormatter.format(at),
        )
    }
}
