package pl.dakil.transport.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The strip of option buttons above the Search action: one button per group of search options,
 * each opening its own sheet. It replaces the tall inline controls the forms used to carry, so
 * the whole form fits on one screen.
 */
@Composable
fun SearchOptionsBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

/**
 * One button of a [SearchOptionsBar]. [badge] shows the group's chosen value (only the transfer
 * limit has one worth a glance); null draws no badge at all. [title] doubles as the button's
 * content description, since the icon alone doesn't say which sheet it opens.
 */
@Composable
fun RowScope.SearchOptionsButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(badge = { if (badge != null) Badge { Text(badge) } }) {
            Icon(icon, contentDescription = title)
        }
    }
}
