package pl.dakil.transport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    valueLabel: @Composable (Float) -> String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
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
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    valueLabel: @Composable (Int) -> String = { it.toString() },
    supportingText: String? = null,
) {
    LabeledSliderRow(
        title = title,
        value = value.toFloat(),
        onValueCommit = { onValueCommit(it.roundToInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / step - 1,
        valueLabel = { valueLabel(it.roundToInt()) },
        modifier = modifier,
        supportingText = supportingText,
    )
}

/**
 * A slider over an explicit list of [values] rather than a numeric range: the slider moves
 * through indices, so the steps can be as unevenly spaced as the setting deserves. A [value]
 * that isn't in the list snaps to the nearest entry.
 */
@Composable
fun <T> SteppedSliderRow(
    title: String,
    values: List<T>,
    value: T,
    onValueCommit: (T) -> Unit,
    valueLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    distance: (T, T) -> Float = { a, b -> if (a == b) 0f else 1f },
) {
    val index = remember(values, value) {
        values.indices.minByOrNull { distance(values[it], value) } ?: 0
    }
    LabeledSliderRow(
        title = title,
        value = index.toFloat(),
        onValueCommit = { onValueCommit(values[it.roundToInt().coerceIn(values.indices)]) },
        valueRange = 0f..values.lastIndex.toFloat(),
        steps = values.size - 2,
        valueLabel = { position ->
            valueLabel(values[position.roundToInt().coerceIn(values.indices)])
        },
        modifier = modifier,
        supportingText = supportingText,
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
 * A vertical single-select list of radio rows. Unlike [SingleChoiceConnectedRow] each option
 * gets a full line, so it suits a handful of choices whose labels are sentences rather than
 * one-word segments — and it reads as an either/or list even when one choice reveals a control
 * of its own underneath.
 */
@Composable
fun <T> SingleChoiceRadioColumn(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A single-select row of connected [ToggleButton]s (expressive segmented-button idiom),
 * each option weighted equally across the full width.
 *
 * Equal weights mean a segment gets whatever width the row has to give, not the width its label
 * wants — with four options on a narrow row the default button padding leaves the label almost no
 * space, and a clipped single line renders as nothing at all. So the padding is trimmed and the
 * label auto-sizes down to [LABEL_MIN_FONT_SIZE] to fit what is left. Options whose names are too
 * long to survive that shrinking belong in [SingleChoiceToggleFlow] instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SingleChoiceConnectedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                icon?.let {
                    Icon(it(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label(option),
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = LABEL_MIN_FONT_SIZE,
                        maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                        stepSize = 0.5.sp,
                    ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Floor for [SingleChoiceConnectedRow]'s shrinking labels — below this they stop being readable. */
private val LABEL_MIN_FONT_SIZE = 10.sp

/**
 * A wrapping single-select flow of [ToggleButton]s. Unlike [SingleChoiceConnectedRow] the
 * buttons size to their labels and wrap, so it suits options whose names are too long to share
 * one row without being cut off.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SingleChoiceToggleFlow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
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
                checked = option == selected,
                onCheckedChange = { if (it) onSelect(option) },
                modifier = Modifier.semantics { role = Role.RadioButton },
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

/** A wrapping multi-select flow of checkable [ToggleButton]s with optional leading icons. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MultiChoiceToggleFlow(
    options: List<T>,
    selected: Set<T>,
    onSelectedChange: (Set<T>) -> Unit,
    label: @Composable (T) -> String,
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
