package pl.dakil.transport.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.MapFilters
import pl.dakil.transport.domain.model.TransitFilterCategory
import pl.dakil.transport.domain.model.VehicleSource
import pl.dakil.transport.ui.components.MultiChoiceToggleFlow

/**
 * Power-user map layer filter: a compact button that expands into a dense panel with
 * per-category toggles for stops and vehicles, a vehicle data-source selector, and reset.
 * State changes are pushed through [onUpdate] as transforms of the current [filters].
 *
 * [expanded] is hoisted rather than local so the map screen's back handler can close the panel
 * before it starts dismissing selections.
 */
@Composable
fun MapFiltersMenu(
    filters: MapFilters,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdate: ((MapFilters) -> MapFilters) -> Unit,
    onReset: () -> Unit,
    areaDownload: AreaDownloadState,
    onDownloadArea: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(targetState = expanded, modifier = modifier, label = "map-filters") { open ->
        if (open) {
            FiltersPanel(
                filters = filters,
                onUpdate = onUpdate,
                onReset = onReset,
                onClose = { onExpandedChange(false) },
                areaDownload = areaDownload,
                onDownloadArea = onDownloadArea,
            )
        } else {
            Surface(
                onClick = { onExpandedChange(true) },
                shape = CircleShape,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                BadgedBox(
                    badge = { if (!filters.isDefault) Badge() },
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.map_filters_open))
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
    areaDownload: AreaDownloadState,
    onDownloadArea: () -> Unit,
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
                    text = stringResource(R.string.map_filters_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = !filters.isDefault) {
                    Text(stringResource(R.string.action_reset))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.map_filters_close))
                }
            }

            FilterSection(
                title = stringResource(R.string.map_filters_stops),
                selected = filters.stopCategories,
                onSelectedChange = { categories -> onUpdate { it.copy(stopCategories = categories) } },
            )

            FilterSection(
                title = stringResource(R.string.map_filters_vehicles),
                selected = filters.vehicleCategories,
                onSelectedChange = { categories -> onUpdate { it.copy(vehicleCategories = categories) } },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                VehicleSourcesToggle(
                    selected = filters.vehicleSources,
                    onSelectedChange = { sources -> onUpdate { it.copy(vehicleSources = sources) } },
                )
            }

            DownloadAreaRow(
                state = areaDownload,
                onDownload = onDownloadArea,
                modifier = Modifier.padding(top = 12.dp, end = 8.dp),
            )
        }
    }
}

/**
 * "Download this area" and whatever it last did.
 *
 * The automatic refresh already keeps everywhere the user goes on the phone; this is for the one
 * case it cannot anticipate — being about to lose signal and wanting the area now, whether or
 * not the cache considers it fresh.
 */
@Composable
private fun DownloadAreaRow(
    state: AreaDownloadState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onDownload,
                enabled = state != AreaDownloadState.Running,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.map_download_area))
            }
            if (state == AreaDownloadState.Running) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        val message = when (state) {
            is AreaDownloadState.Done ->
                pluralStringResource(R.plurals.map_download_area_done, state.areas, state.areas)
            AreaDownloadState.Failed -> stringResource(R.string.map_download_area_failed)
            AreaDownloadState.TooLarge -> stringResource(R.string.map_download_area_too_large)
            else -> null
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * A titled group of per-category toggles with an all/none quick action, plus optional
 * [extraContent] rendered between the title row and the toggles (e.g. a sub-selector).
 */
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
                Text(stringResource(if (allSelected) R.string.action_select_none else R.string.action_select_all))
            }
        }
        extraContent?.let {
            it()
            Spacer(Modifier.size(8.dp))
        }
        MultiChoiceToggleFlow(
            options = TransitFilterCategory.entries,
            selected = selected,
            onSelectedChange = onSelectedChange,
            label = { stringResource(it.labelRes) },
            icon = { it.icon },
        )
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
                Text(stringResource(option.labelRes))
            }
        }
    }
    // "Live" reads as GPS tracking, which the API does not offer — it distinguishes vehicles
    // whose *times* a real-time feed corrected from ones running on the plain timetable.
    Text(
        text = stringResource(R.string.map_filters_source_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
