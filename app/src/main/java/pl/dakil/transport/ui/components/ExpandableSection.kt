package pl.dakil.transport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A surface card with a clickable header that expands/collapses its [content], in the app's
 * extraLarge-surface idiom. [badge] (e.g. "Modified") appears next to the title, and
 * [headerActions] (e.g. a Reset button) sit before the chevron.
 */
@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badge: String? = null,
    headerActions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Badge { Text(badge) }
                    }
                    Spacer(Modifier.weight(1f))
                    headerActions()
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    content = content,
                )
            }
        }
    }
}
