package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
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
        GroupTitle("Routing")
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
        GroupTitle("Accessibility & street")
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
        GroupTitle("Direct & first/last mile")
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
        GroupTitle("Results")
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
