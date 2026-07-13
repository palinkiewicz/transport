package pl.dakil.transport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A titled slider with a right-aligned live readout of the current value. Drag state is
 * internal; [onValueCommit] fires once when the drag ends (or the track is tapped), so
 * committing can safely persist without per-frame writes.
 */
@Composable
fun LabeledSliderRow(
    title: String,
    value: Float,
    onValueCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel(dragValue),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueCommit(dragValue) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** Integer convenience over [LabeledSliderRow]: whole-number steps between [min] and [max]. */
@Composable
fun IntSliderRow(
    title: String,
    value: Int,
    onValueCommit: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
) {
    LabeledSliderRow(
        title = title,
        value = value.toFloat(),
        onValueCommit = { onValueCommit(it.roundToInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / step - 1,
        valueLabel = { valueLabel(it.roundToInt()) },
        modifier = modifier,
    )
}

/** A full-width labeled switch row with optional supporting text under the title. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A single-select row of connected [ToggleButton]s (expressive segmented-button idiom),
 * each option weighted equally across the full width.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SingleChoiceConnectedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { if (it) onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                icon?.let {
                    Icon(it(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(option), maxLines = 1)
            }
        }
    }
}

/** A wrapping multi-select flow of checkable [ToggleButton]s with optional leading icons. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MultiChoiceToggleFlow(
    options: List<T>,
    selected: Set<T>,
    onSelectedChange: (Set<T>) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        options.forEach { option ->
            ToggleButton(
                checked = option in selected,
                onCheckedChange = { on ->
                    onSelectedChange(if (on) selected + option else selected - option)
                },
                modifier = Modifier.semantics { role = Role.Checkbox },
            ) {
                icon?.let {
                    Icon(it(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(option))
            }
        }
    }
}
