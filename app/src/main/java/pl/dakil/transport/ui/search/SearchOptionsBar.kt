package pl.dakil.transport.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * limit has one worth a glance); without one, [modified] draws a plain dot, so a group changed
 * from its defaults — trains switched off, a slower pace — is visible without opening its sheet.
 * [title] doubles as the button's content description, since the icon alone doesn't say which
 * sheet it opens.
 */
@Composable
fun RowScope.SearchOptionsButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    modified: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                // Both kinds sit further out than BadgedBox's default, which crowds the icon.
                when {
                    badge != null -> Badge(
                        modifier = Modifier.offset(x = BADGE_OFFSET, y = -BADGE_OFFSET),
                    ) { Text(badge) }
                    // Larger than the default 6dp dot, which is easy to miss on a busy form, and
                    // nudged out further so the extra size grows away from the icon.
                    modified -> Badge(
                        modifier = Modifier
                            .offset(x = BADGE_OFFSET + 2.dp, y = -BADGE_OFFSET - 2.dp)
                            .size(MODIFIED_DOT_SIZE),
                    )
                }
            },
        ) {
            Icon(icon, contentDescription = title)
        }
    }
}

private val MODIFIED_DOT_SIZE = 8.dp
private val BADGE_OFFSET = 4.dp
