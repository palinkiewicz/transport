package pl.dakil.transport.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.dakil.transport.domain.model.MapFilters
import pl.dakil.transport.domain.model.TransitFilterCategory
import pl.dakil.transport.domain.model.VehicleSource

/**
 * Power-user map layer filter: a compact button that expands into a dense panel with
 * per-category toggles for stops and vehicles, a vehicle data-source selector, and reset.
 * State changes are pushed through [onUpdate] as transforms of the current [filters].
 */
@Composable
fun MapFiltersMenu(
    filters: MapFilters,
    onUpdate: ((MapFilters) -> MapFilters) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(targetState = expanded, modifier = modifier, label = "map-filters") { open ->
        if (open) {
            FiltersPanel(
                filters = filters,
                onUpdate = onUpdate,
                onReset = onReset,
                onClose = { expanded = false },
            )
        } else {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                BadgedBox(
                    badge = { if (!filters.isDefault) Badge() },
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Map filters")
                }
            }
        }
    }
}

@Composable
private fun FiltersPanel(
    filters: MapFilters,
    onUpdate: ((MapFilters) -> MapFilters) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 400.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Map filters",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = !filters.isDefault) {
                    Text("Reset")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close filters")
                }
            }

            FilterSection(
                title = "Stops",
                selected = filters.stopCategories,
                onSelectedChange = { categories -> onUpdate { it.copy(stopCategories = categories) } },
            )

            FilterSection(
                title = "Vehicles",
                selected = filters.vehicleCategories,
                onSelectedChange = { categories -> onUpdate { it.copy(vehicleCategories = categories) } },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                VehicleSourcesToggle(
                    selected = filters.vehicleSources,
                    onSelectedChange = { sources -> onUpdate { it.copy(vehicleSources = sources) } },
                )
            }
        }
    }
}

/**
 * A titled group of per-category toggles with an all/none quick action, plus optional
 * [extraContent] rendered between the title row and the toggles (e.g. a sub-selector).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterSection(
    title: String,
    selected: Set<TransitFilterCategory>,
    onSelectedChange: (Set<TransitFilterCategory>) -> Unit,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val allSelected = selected.size == TransitFilterCategory.entries.size
    Column(modifier = modifier.padding(end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onSelectedChange(if (allSelected) emptySet() else TransitFilterCategory.entries.toSet())
                },
            ) {
                Text(if (allSelected) "None" else "All")
            }
        }
        extraContent?.let {
            it()
            Spacer(Modifier.size(8.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransitFilterCategory.entries.forEach { category ->
                val checked = category in selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { on ->
                        onSelectedChange(if (on) selected + category else selected - category)
                    },
                    modifier = Modifier.semantics { role = Role.Checkbox },
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(category.label)
                }
            }
        }
    }
}

/** Multi-select Live/Timetable toggle; at least one source always stays selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VehicleSourcesToggle(
    selected: Set<VehicleSource>,
    onSelectedChange: (Set<VehicleSource>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        VehicleSource.entries.forEachIndexed { index, option ->
            ToggleButton(
                checked = option in selected,
                onCheckedChange = { on ->
                    val updated = if (on) selected + option else selected - option
                    if (updated.isNotEmpty()) onSelectedChange(updated)
                },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Checkbox },
            ) {
                Text(option.label)
            }
        }
    }
}
