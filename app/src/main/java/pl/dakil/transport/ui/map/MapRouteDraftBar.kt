package pl.dakil.transport.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.TransitLocation

/**
 * The route being assembled from the map, shown once "Begin here" or "Finish here" has filled
 * one end of it.
 *
 * Its point is that picking a start no longer throws the user off the map: the missing end is
 * named as a hint, so the next stop they tap can complete the route, and only then does
 * searching become an option.
 */
@Composable
fun MapRouteDraftBar(
    from: TransitLocation?,
    to: TransitLocation?,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            RouteEndpointRow(
                icon = Icons.Filled.Circle,
                location = from,
                hintRes = R.string.map_route_draft_from_hint,
                clearLabelRes = R.string.map_route_draft_clear_from,
                onClear = onClearFrom,
            )
            RouteEndpointRow(
                icon = Icons.Outlined.Circle,
                location = to,
                hintRes = R.string.map_route_draft_to_hint,
                clearLabelRes = R.string.map_route_draft_clear_to,
                onClear = onClearTo,
            )
            if (from != null && to != null) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
                ) {
                    Button(onClick = onSearch) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.map_route_draft_search),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One end of the draft: its name once picked, otherwise the hint naming what is still missing. */
@Composable
private fun RouteEndpointRow(
    icon: ImageVector,
    location: TransitLocation?,
    hintRes: Int,
    clearLabelRes: Int,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = location?.name ?: stringResource(hintRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (location != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        // Only a filled end can be cleared; the empty one keeps the row's height so the two
        // stay aligned as the user fills them in either order.
        if (location != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(clearLabelRes),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Row(modifier = Modifier.size(48.dp)) {}
        }
    }
}
