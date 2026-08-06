package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R

/**
 * Borderless location field for the search screen's route card (the card supplies the
 * container). Not editable in place — tapping it opens the full-screen location picker.
 *
 * [onClear] adds a clear button that appears only once the field holds a place; pass null for
 * fields that cannot be emptied. The button sits outside the field's own click target so
 * clearing never opens the picker on the way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocationField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = value ?: label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
        AnimatedVisibility(
            visible = onClear != null && value != null,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            IconButton(
                onClick = { onClear?.invoke() },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear_field, label),
                )
            }
        }
    }
}
