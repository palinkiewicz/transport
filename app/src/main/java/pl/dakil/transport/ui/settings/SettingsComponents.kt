package pl.dakil.transport.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pl.dakil.transport.ui.components.SingleChoiceSegmentedRow

/**
 * The settings screens' whole visual vocabulary.
 *
 * Every row is built on one [SettingRow], so a switch, a slider and a submenu line up down the
 * screen without any of them naming a padding. No cards and no dividers — grouping is a
 * [SectionHeader] and whitespace, which is what a long list of unrelated knobs actually needs and
 * what the previous per-section card could not give it.
 *
 * The sliders are deliberately *not* the ones in `ui/components/OptionControls.kt`: those are the
 * search sheets' and the map filter menu's, laid out as titled blocks rather than list rows. What
 * is shared with them is the behaviour that matters — the value is held locally while the finger
 * is down and committed once on release, because every settings change writes DataStore.
 */

private const val DISABLED_ALPHA = 0.38f

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * The base every other row is made of.
 *
 * [trailing] is sized to its own content rather than a fixed column, so a switch, a chevron and a
 * value readout can all sit at the same edge without agreeing on a width beforehand.
 */
@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = if (summary != null || supporting != null) {
            {
                Column {
                    summary?.let { Text(it) }
                    supporting?.invoke()
                }
            }
        } else {
            null
        },
        leadingContent = leading,
        trailingContent = trailing,
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        // The whole row is the target, not just the switch: a toggle you have to hit at the far edge
        // of the screen is a toggle people miss.
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

/**
 * A handful of choices laid out across the full width, all visible at once.
 *
 * For a set small and short-named enough to read without opening anything — the two theme modes
 * are System/Light/Dark — where a menu would hide two of three options behind a tap. It carries no
 * row title: the [SectionHeader] above it already names what is being chosen, and repeating that on
 * the row is the same words twice. Anything longer or larger belongs in [SettingSelectRow].
 *
 * The control itself is the search forms' [SingleChoiceSegmentedRow]; only the indent that lines it
 * up with the rows around it belongs to the settings screens.
 */
@Composable
fun <T> SettingSegmentedRow(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        label = label,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A short closed set of choices, picked from a menu rather than spread across the row. */
@Composable
fun <T> SettingSelectRow(
    title: String,
    summary: String? = null,
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    // Resolved up front: `label` reads string resources, and a DropdownMenuItem's text slot is a
    // fine place to call it but the collapsed readout is not inside the menu.
    val labels = options.map { it to label(it) }

    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { expanded = true },
        trailing = {
            // Anchored inside the trailing slot so the menu opens against the right edge, under the
            // value it is replacing, rather than over the title.
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = labels.firstOrNull { it.first == selected }?.second.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                ) {
                    labels.forEach { (value, text) ->
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = {
                                onSelect(value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

/** A row that opens another screen, marked with a trailing chevron. */
@Composable
fun SettingNavigationRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = icon?.let { { Icon(it, contentDescription = null) } },
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * A continuous value.
 *
 * [onValueCommit] fires on release, never during the drag: the drag position is held locally and
 * only the settled value reaches the view model, so a slow sweep does not queue a DataStore write
 * per pixel. The readout still follows the finger.
 */
@Composable
fun SettingSliderRow(
    title: String,
    summary: String? = null,
    value: Float,
    onValueCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: @Composable (Float) -> String,
    enabled: Boolean = true,
) {
    var dragged by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(value) }
    val shown = if (dragged) draft else value

    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        supporting = {
            Slider(
                value = shown,
                onValueChange = {
                    dragged = true
                    draft = it
                },
                onValueChangeFinished = {
                    dragged = false
                    onValueCommit(draft)
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
            )
        },
        trailing = {
            Text(valueLabel(shown), color = MaterialTheme.colorScheme.primary)
        },
    )
}

/** [SettingSliderRow] over whole numbers, optionally in strides of [step]. */
@Composable
fun SettingIntSliderRow(
    title: String,
    summary: String? = null,
    value: Int,
    onValueCommit: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int = 1,
    valueLabel: @Composable (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val stepCount = ((max - min) / step) - 1
    SettingSliderRow(
        title = title,
        summary = summary,
        value = value.toFloat(),
        onValueCommit = { onValueCommit(snapToStep(it, min, max, step)) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = stepCount.coerceAtLeast(0),
        valueLabel = { valueLabel(snapToStep(it, min, max, step)) },
        enabled = enabled,
    )
}

private fun snapToStep(raw: Float, min: Int, max: Int, step: Int): Int =
    (min + ((raw - min) / step).roundToInt() * step).coerceIn(min, max)

/**
 * A slider over an explicit, unevenly spaced list of values.
 *
 * [distance] says how far apart two entries are for the purpose of picking the nearest one, which
 * matters where the steps are a geometric ladder rather than a ramp — the frame interval runs
 * 50 ms to 30 s, and treating those as equally spaced would make the fine end unusable.
 */
@Composable
fun <T> SettingSteppedSliderRow(
    title: String,
    summary: String? = null,
    values: List<T>,
    value: T,
    onValueCommit: (T) -> Unit,
    valueLabel: @Composable (T) -> String,
    distance: (T, T) -> Float = { a, b -> if (a == b) 0f else 1f },
    enabled: Boolean = true,
) {
    if (values.isEmpty()) return
    val index = values.indices.minByOrNull { distance(values[it], value) } ?: 0

    SettingSliderRow(
        title = title,
        summary = summary,
        value = index.toFloat(),
        onValueCommit = { onValueCommit(values[it.roundToInt().coerceIn(values.indices)]) },
        valueRange = 0f..(values.size - 1).toFloat(),
        steps = (values.size - 2).coerceAtLeast(0),
        valueLabel = { valueLabel(values[it.roundToInt().coerceIn(values.indices)]) },
        enabled = enabled,
    )
}
