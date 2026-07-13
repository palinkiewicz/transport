package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.Locale
import pl.dakil.transport.domain.model.ElevationCosts
import pl.dakil.transport.domain.model.LegContext
import pl.dakil.transport.domain.model.PedestrianProfile
import pl.dakil.transport.domain.model.RentalFormFactor
import pl.dakil.transport.domain.model.RentalPropulsionType
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.StreetMode
import pl.dakil.transport.domain.model.TransitFilterCategory
import pl.dakil.transport.ui.components.ExpandableSection
import pl.dakil.transport.ui.components.IntSliderRow
import pl.dakil.transport.ui.components.LabeledSliderRow
import pl.dakil.transport.ui.components.MultiChoiceToggleFlow
import pl.dakil.transport.ui.components.SingleChoiceConnectedRow
import pl.dakil.transport.ui.components.SwitchRow

private val StreetMode.icon: ImageVector
    get() = when (this) {
        StreetMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        StreetMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
        StreetMode.CAR -> Icons.Default.DirectionsCar
        StreetMode.RENTAL -> Icons.Default.ElectricScooter
    }

private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

private fun formatSpeed(metersPerSecond: Float): String =
    String.format(Locale.getDefault(), "%.1f m/s", metersPerSecond)

private val ROUTING_INFO = listOf(
    "Transit modes" to "Kinds of transit the journey may use. Deselecting a category " +
        "excludes all its lines; with nothing selected no transit connections are computed.",
    "Min transfer time" to "Minimum time reserved for every transfer between vehicles.",
    "Extra transfer buffer" to "Additional safety margin added on top of each transfer's " +
        "required time.",
    "Transfer time factor" to "Multiplies the minimum required transfer times. Useful if you " +
        "walk slower than the schedule assumes.",
    "Limit travel time" to "Caps the total journey duration. Warning: a low cap can hide " +
        "otherwise optimal journeys (e.g. ones with fewer transfers) and slow the search down.",
    "Routed transfers" to "Computes transfers on the street network instead of using " +
        "precomputed ones — more precise, but slower.",
)

private val ACCESSIBILITY_INFO = listOf(
    "On foot / Wheelchair" to "Accessibility profile used for transfers and the first/last " +
        "mile. Wheelchair prefers step-free paths.",
    "Custom walking speed" to "Average walking speed used for street routing. Typical walking " +
        "pace is about 1.2 m/s.",
    "Custom cycling speed" to "Average cycling speed used for bike routing. A relaxed pace is " +
        "around 4 m/s.",
    "Avoid inclines" to "Prefers flatter routes even when they are longer. Low adds a small " +
        "penalty for climbs, High a strong one.",
    "Bike carriage" to "Only returns journeys where a bike may be taken along on every " +
        "transit leg.",
    "Car carriage" to "Only returns journeys where a car may be taken along (e.g. car " +
        "trains and some ferries).",
)

private val STREET_LEGS_INFO = listOf(
    "Direct" to "Connections from start to destination without using transit at all.",
    "First mile" to "The street leg from the start to the first transit stop.",
    "Last mile" to "The street leg from the last transit stop to the destination.",
    "Modes" to "Ways the selected leg may be travelled. At least one stays selected. Note: " +
        "transit journeys slower than the fastest direct connection are not returned.",
    "Max time" to "Time cap for the selected leg. Trips whose leg exceeds it are not found.",
    "Rental vehicles & propulsion" to "Only applies when the Rental mode is selected. " +
        "Restricts shared vehicles by type and drive; nothing selected allows every kind.",
    "Ignore return constraints" to "Also considers rental vehicles that would normally have " +
        "to be returned to a station, as if they could be left anywhere.",
)

private val RESULTS_INFO = listOf(
    "Search window" to "How far past the chosen time (or before it, when arriving by) " +
        "departures are scanned.",
    "Min itineraries" to "Minimum number of journeys to compute; the search window is " +
        "extended until this many are found.",
    "Fastest direct factor" to "Also returns transit journeys up to this factor slower than " +
        "the fastest direct connection.",
    "Slow direct connections" to "Keeps direct connections in the results even when transit " +
        "would be faster.",
    "Passengers & luggage" to "Experimental. Passed to on-demand transport and fare " +
        "calculation where supported.",
)

private val DEPARTURES_INFO = listOf(
    "Transit modes" to "Kinds of transport shown on the board.",
    "Results" to "Number of departures or arrivals fetched per page.",
    "Search radius" to "How far around the chosen coordinates stops are collected. Only " +
        "applies when the picked place is not a stop itself.",
)

/**
 * Expandable panel with every plan-API option the Connections search supports, grouped into
 * Routing / Accessibility & street / Direct & first-last mile / Results. Changes flow as
 * transforms through [onUpdate] and are persisted by the caller.
 */
@Composable
fun ConnectionsAdvancedOptions(
    options: SearchOptions,
    onUpdate: ((SearchOptions) -> SearchOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the fields this panel edits count as "modified" — the transfers slider and
    // depart/arrive toggle have their own visible controls, departures its own panel.
    val default = SearchOptions.DEFAULT
    val modified = options.copy(
        maxTransfers = default.maxTransfers,
        arriveBy = default.arriveBy,
        departuresCategories = default.departuresCategories,
        departuresCount = default.departuresCount,
        departuresRadiusMeters = default.departuresRadiusMeters,
    ) != default

    ExpandableSection(
        title = "Advanced options",
        icon = Icons.Default.Tune,
        badge = "Modified".takeIf { modified },
        headerActions = {
            TextButton(
                enabled = modified,
                onClick = {
                    onUpdate { current ->
                        default.copy(
                            maxTransfers = current.maxTransfers,
                            arriveBy = current.arriveBy,
                            departuresCategories = current.departuresCategories,
                            departuresCount = current.departuresCount,
                            departuresRadiusMeters = current.departuresRadiusMeters,
                        )
                    }
                },
            ) { Text("Reset") }
        },
        modifier = modifier,
    ) {
        RoutingGroup(options, onUpdate)
        HorizontalDivider()
        AccessibilityGroup(options, onUpdate)
        HorizontalDivider()
        StreetLegsGroup(options, onUpdate)
        HorizontalDivider()
        ResultsGroup(options, onUpdate)
    }
}

/** Expandable panel with the stoptimes-API options for the Departures board. */
@Composable
fun DeparturesAdvancedOptions(
    options: SearchOptions,
    onUpdate: ((SearchOptions) -> SearchOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val default = SearchOptions.DEFAULT
    val modified = options.departuresCategories != default.departuresCategories ||
        options.departuresCount != default.departuresCount ||
        options.departuresRadiusMeters != default.departuresRadiusMeters

    ExpandableSection(
        title = "Advanced options",
        icon = Icons.Default.Tune,
        badge = "Modified".takeIf { modified },
        headerActions = {
            TextButton(
                enabled = modified,
                onClick = {
                    onUpdate {
                        it.copy(
                            departuresCategories = default.departuresCategories,
                            departuresCount = default.departuresCount,
                            departuresRadiusMeters = default.departuresRadiusMeters,
                        )
                    }
                },
            ) { Text("Reset") }
        },
        modifier = modifier,
    ) {
        GroupTitle("Departures board", DEPARTURES_INFO)
        CategoryFlow(
            title = "Transit modes",
            selected = options.departuresCategories,
            onSelectedChange = { categories -> onUpdate { it.copy(departuresCategories = categories) } },
        )
        IntSliderRow(
            title = "Results",
            value = options.departuresCount,
            onValueCommit = { count -> onUpdate { it.copy(departuresCount = count) } },
            min = 5,
            max = 50,
            step = 5,
        )
        IntSliderRow(
            title = "Search radius",
            value = options.departuresRadiusMeters,
            onValueCommit = { radius -> onUpdate { it.copy(departuresRadiusMeters = radius) } },
            min = 100,
            max = 1000,
            step = 100,
            valueLabel = { "$it m" },
        )
        Text(
            text = "Radius applies when searching around coordinates (a place without a stop id).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Group header with an info button that opens a dialog defining every option in the group
 * ([info] pairs of option name to explanation).
 */
@Composable
private fun GroupTitle(title: String, info: List<Pair<String, String>>) {
    var showInfo by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showInfo = true }) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "About $title options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    info.forEach { (name, description) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** Transit category multi-select with the map-filters All/None quick action. */
@Composable
private fun CategoryFlow(
    title: String,
    selected: Set<TransitFilterCategory>,
    onSelectedChange: (Set<TransitFilterCategory>) -> Unit,
) {
    val allSelected = selected.size == TransitFilterCategory.entries.size
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onSelectedChange(
                        if (allSelected) emptySet() else TransitFilterCategory.entries.toSet(),
                    )
                },
            ) { Text(if (allSelected) "None" else "All") }
        }
        MultiChoiceToggleFlow(
            options = TransitFilterCategory.entries,
            selected = selected,
            onSelectedChange = onSelectedChange,
            label = { it.label },
            icon = { it.icon },
        )
    }
}

@Composable
private fun RoutingGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle("Routing", ROUTING_INFO)
        CategoryFlow(
            title = "Transit modes",
            selected = options.transitCategories,
            onSelectedChange = { categories -> onUpdate { it.copy(transitCategories = categories) } },
        )
        IntSliderRow(
            title = "Min transfer time",
            value = options.minTransferTimeMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(minTransferTimeMinutes = minutes) } },
            min = 0,
            max = 30,
            valueLabel = ::formatMinutes,
        )
        IntSliderRow(
            title = "Extra transfer buffer",
            value = options.additionalTransferTimeMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(additionalTransferTimeMinutes = minutes) } },
            min = 0,
            max = 30,
            valueLabel = ::formatMinutes,
        )
        LabeledSliderRow(
            title = "Transfer time factor",
            value = options.transferTimeFactor,
            onValueCommit = { factor -> onUpdate { it.copy(transferTimeFactor = factor) } },
            valueRange = 1f..3f,
            steps = 7,
            valueLabel = { String.format(Locale.getDefault(), "%.2f×", it) },
        )
        SwitchRow(
            title = "Limit travel time",
            supportingText = "Too low a limit can hide otherwise optimal journeys",
            checked = options.maxTravelTimeMinutes != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(maxTravelTimeMinutes = if (on) 240 else null) }
            },
        )
        AnimatedVisibility(visible = options.maxTravelTimeMinutes != null) {
            IntSliderRow(
                title = "Max travel time",
                value = options.maxTravelTimeMinutes ?: 240,
                onValueCommit = { minutes -> onUpdate { it.copy(maxTravelTimeMinutes = minutes) } },
                min = 30,
                max = 720,
                step = 30,
                valueLabel = ::formatMinutes,
            )
        }
        SwitchRow(
            title = "Routed transfers",
            supportingText = "Compute transfers on the street network instead of using precomputed ones",
            checked = options.useRoutedTransfers,
            onCheckedChange = { on -> onUpdate { it.copy(useRoutedTransfers = on) } },
        )
    }
}

@Composable
private fun AccessibilityGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle("Accessibility & street", ACCESSIBILITY_INFO)
        SingleChoiceConnectedRow(
            options = PedestrianProfile.entries,
            selected = options.pedestrianProfile,
            onSelect = { profile -> onUpdate { it.copy(pedestrianProfile = profile) } },
            label = { it.label },
        )
        SwitchRow(
            title = "Custom walking speed",
            checked = options.pedestrianSpeed != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(pedestrianSpeed = if (on) 1.2f else null) }
            },
        )
        AnimatedVisibility(visible = options.pedestrianSpeed != null) {
            LabeledSliderRow(
                title = "Walking speed",
                value = options.pedestrianSpeed ?: 1.2f,
                onValueCommit = { speed -> onUpdate { it.copy(pedestrianSpeed = speed) } },
                valueRange = 0.5f..2.5f,
                steps = 19,
                valueLabel = ::formatSpeed,
            )
        }
        SwitchRow(
            title = "Custom cycling speed",
            checked = options.cyclingSpeed != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(cyclingSpeed = if (on) 4.2f else null) }
            },
        )
        AnimatedVisibility(visible = options.cyclingSpeed != null) {
            LabeledSliderRow(
                title = "Cycling speed",
                value = options.cyclingSpeed ?: 4.2f,
                onValueCommit = { speed -> onUpdate { it.copy(cyclingSpeed = speed) } },
                valueRange = 1f..8f,
                steps = 13,
                valueLabel = ::formatSpeed,
            )
        }
        Column {
            Text(
                text = "Avoid inclines",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SingleChoiceConnectedRow(
                options = ElevationCosts.entries,
                selected = options.elevationCosts,
                onSelect = { costs -> onUpdate { it.copy(elevationCosts = costs) } },
                label = { it.label },
            )
        }
        SwitchRow(
            title = "Bike carriage",
            supportingText = "Only journeys that allow taking a bike along",
            checked = options.requireBikeTransport,
            onCheckedChange = { on -> onUpdate { it.copy(requireBikeTransport = on) } },
        )
        SwitchRow(
            title = "Car carriage",
            supportingText = "Only journeys that allow taking a car along",
            checked = options.requireCarTransport,
            onCheckedChange = { on -> onUpdate { it.copy(requireCarTransport = on) } },
        )
    }
}

@Composable
private fun StreetLegsGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    var context by rememberSaveable { mutableStateOf(LegContext.DIRECT) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle("Direct & first/last mile", STREET_LEGS_INFO)
        SingleChoiceConnectedRow(
            options = LegContext.entries,
            selected = context,
            onSelect = { context = it },
            label = { it.label },
        )
        AnimatedContent(targetState = context, label = "leg-context") { target ->
            val leg = options.legOptions(target)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "Modes",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    MultiChoiceToggleFlow(
                        options = StreetMode.entries,
                        selected = leg.modes,
                        onSelectedChange = { modes ->
                            // At least one mode must stay selected — an empty mode list would
                            // disable this leg entirely (and encode as an empty query param).
                            if (modes.isNotEmpty()) {
                                onUpdate { it.copyLeg(target) { l -> l.copy(modes = modes) } }
                            }
                        },
                        label = { it.label },
                        icon = { it.icon },
                    )
                }
                IntSliderRow(
                    title = "Max time",
                    value = leg.maxTimeMinutes,
                    onValueCommit = { minutes ->
                        onUpdate { it.copyLeg(target) { l -> l.copy(maxTimeMinutes = minutes) } }
                    },
                    min = 5,
                    max = 60,
                    step = 5,
                    valueLabel = ::formatMinutes,
                )
                AnimatedVisibility(visible = StreetMode.RENTAL in leg.modes) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = "Rental vehicles",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            MultiChoiceToggleFlow(
                                options = RentalFormFactor.entries,
                                selected = leg.rentalFormFactors,
                                onSelectedChange = { formFactors ->
                                    onUpdate {
                                        it.copyLeg(target) { l -> l.copy(rentalFormFactors = formFactors) }
                                    }
                                },
                                label = { it.label },
                            )
                        }
                        Column {
                            Text(
                                text = "Rental propulsion",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            MultiChoiceToggleFlow(
                                options = RentalPropulsionType.entries,
                                selected = leg.rentalPropulsionTypes,
                                onSelectedChange = { propulsionTypes ->
                                    onUpdate {
                                        it.copyLeg(target) { l ->
                                            l.copy(rentalPropulsionTypes = propulsionTypes)
                                        }
                                    }
                                },
                                label = { it.label },
                            )
                        }
                        Text(
                            text = "Nothing selected means every rental type is allowed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SwitchRow(
                            title = "Ignore return constraints",
                            supportingText = "Allow leaving rental vehicles anywhere",
                            checked = leg.ignoreRentalReturnConstraints,
                            onCheckedChange = { on ->
                                onUpdate {
                                    it.copyLeg(target) { l -> l.copy(ignoreRentalReturnConstraints = on) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle("Results", RESULTS_INFO)
        IntSliderRow(
            title = "Search window",
            value = options.searchWindowMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(searchWindowMinutes = minutes) } },
            min = 5,
            max = 120,
            step = 5,
            valueLabel = ::formatMinutes,
        )
        IntSliderRow(
            title = "Min itineraries",
            value = options.numItineraries,
            onValueCommit = { count -> onUpdate { it.copy(numItineraries = count) } },
            min = 1,
            max = 10,
        )
        LabeledSliderRow(
            title = "Fastest direct factor",
            value = options.fastestDirectFactor,
            onValueCommit = { factor -> onUpdate { it.copy(fastestDirectFactor = factor) } },
            valueRange = 1f..5f,
            steps = 7,
            valueLabel = { String.format(Locale.getDefault(), "%.1f×", it) },
        )
        SwitchRow(
            title = "Slow direct connections",
            supportingText = "Keep direct connections even when transit is faster",
            checked = options.slowDirect,
            onCheckedChange = { on -> onUpdate { it.copy(slowDirect = on) } },
        )
        SwitchRow(
            title = "Passengers & luggage",
            supportingText = "Experimental — used for on-demand transport and fares",
            checked = options.passengers != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(passengers = if (on) 1 else null, luggage = if (on) 0 else null) }
            },
        )
        AnimatedVisibility(visible = options.passengers != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IntSliderRow(
                    title = "Passengers",
                    value = options.passengers ?: 1,
                    onValueCommit = { count -> onUpdate { it.copy(passengers = count) } },
                    min = 1,
                    max = 8,
                )
                IntSliderRow(
                    title = "Luggage",
                    value = options.luggage ?: 0,
                    onValueCommit = { count -> onUpdate { it.copy(luggage = count) } },
                    min = 0,
                    max = 8,
                )
            }
        }
    }
}
