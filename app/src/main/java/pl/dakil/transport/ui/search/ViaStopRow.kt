package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.transport.domain.model.ViaPoint
import pl.dakil.transport.ui.components.SingleChoiceConnectedRow

/** Label for one of [ViaPoint.STAY_PRESETS_MINUTES]. */
private fun stayLabel(minutes: Int): String = if (minutes == 0) "Pass" else "$minutes min"

/**
 * One intermediate stop on the route card: a numbered marker, the stop field, a remove button,
 * and — once a stop is picked — how long the journey has to stay there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ViaStopRow(
    index: Int,
    via: ViaPoint,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onMinimumStayChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LocationField(
                label = "Stop along the way",
                value = via.location.name,
                onClick = onPick,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRemove,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove stop ${index + 1}")
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 36.dp, end = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Minimum stay",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceConnectedRow(
                options = ViaPoint.STAY_PRESETS_MINUTES,
                selected = via.minimumStayMinutes,
                onSelect = onMinimumStayChange,
                label = ::stayLabel,
            )
            Text(
                text = if (via.isPassThrough) {
                    "Only has to be passed through — staying on the same vehicle counts, " +
                        "so no transfer is forced here."
                } else {
                    "Forces a stopover of at least ${via.minimumStayMinutes} minutes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The "Add stop" affordance on the route card. Collapses away once the API's cap is reached
 * ([ViaPoint.MAX_VIA_POINTS]), replaced by a line saying why.
 */
@Composable
internal fun AddViaRow(canAdd: Boolean, onAdd: () -> Unit) {
    AnimatedVisibility(
        visible = canAdd,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add stop along the way")
        }
    }
    AnimatedVisibility(
        visible = !canAdd,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Text(
            text = "Up to ${ViaPoint.MAX_VIA_POINTS} stops along the way can be routed through.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}
