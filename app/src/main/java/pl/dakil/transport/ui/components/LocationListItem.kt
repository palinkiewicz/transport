package pl.dakil.transport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * Shared list row for a [TransitLocation] (location picker, favourites): mode icon for stops /
 * place mark otherwise, name with optional distance from the user, area subtitle, and an
 * optional trailing control (typically a [FavoriteButton]).
 *
 * [isRecent] swaps the leading icon for a history one. It replaces the mode icon rather than
 * sitting beside it: what makes the row worth a glance is that this is somewhere the user has
 * already been, and the mode is still readable from the name and the area below it.
 */
@Composable
fun LocationListItem(
    location: TransitLocation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceMeters: Double? = null,
    isRecent: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = location.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                distanceMeters?.let {
                    Text(
                        text = formatDistance(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = location.areaLabel?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            val mode = if (location.stopId != null) location.primaryMode ?: TransportMode.OTHER else null
            Icon(
                imageVector = when {
                    isRecent -> Icons.Default.History
                    else -> mode?.icon ?: Icons.Default.Place
                },
                contentDescription = stringResource(
                    when {
                        isRecent -> R.string.label_recent
                        else -> mode?.labelRes ?: R.string.label_place
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
