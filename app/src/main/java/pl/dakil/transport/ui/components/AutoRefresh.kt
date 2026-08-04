package pl.dakil.transport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * The auto-refreshing screens (results, departure board, trip) share one convention: a null
 * countdown means auto-refresh is switched off in Settings, and the screen offers a manual
 * reload instead of promising one.
 */
fun refreshSubtitle(label: String, secondsUntilRefresh: Int?): String =
    if (secondsUntilRefresh == null) label else "$label · refreshes in $secondsUntilRefresh sec"

/** Manual reload action, shown only while nothing is reloading the screen on its own. */
@Composable
fun RefreshButton(secondsUntilRefresh: Int?, onRefresh: () -> Unit) {
    if (secondsUntilRefresh != null) return
    IconButton(onClick = onRefresh) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
    }
}
