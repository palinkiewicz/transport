package pl.dakil.transport.ui.search

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R
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

@Composable
private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) {
        stringResource(R.string.format_hours, minutes / 60)
    } else {
        stringResource(R.string.format_minutes, minutes)
    }

@Composable
private fun formatSpeed(metersPerSecond: Float): String =
    stringResource(R.string.format_speed, metersPerSecond)

/**
 * One entry of a group's info dialog: the option's name and what it does, both as resource
 * ids so the dialog can be built here and translated with the rest of the app.
 */
private data class OptionInfo(@StringRes val nameRes: Int, @StringRes val descriptionRes: Int)

private val ROUTING_INFO = listOf(
    OptionInfo(R.string.advanced_transit_modes, R.string.info_transit_modes),
    OptionInfo(R.string.advanced_min_transfer_time, R.string.info_min_transfer_time),
    OptionInfo(R.string.advanced_extra_transfer_buffer, R.string.info_extra_transfer_buffer),
    OptionInfo(R.string.advanced_transfer_time_factor, R.string.info_transfer_time_factor),
    OptionInfo(R.string.advanced_limit_travel_time, R.string.info_limit_travel_time),
    OptionInfo(R.string.advanced_routed_transfers, R.string.info_routed_transfers),
)

private val ACCESSIBILITY_INFO = listOf(
    OptionInfo(R.string.info_pedestrian_profile_title, R.string.info_pedestrian_profile),
    OptionInfo(R.string.advanced_custom_walking_speed, R.string.info_walking_speed),
    OptionInfo(R.string.advanced_custom_cycling_speed, R.string.info_cycling_speed),
    OptionInfo(R.string.advanced_avoid_inclines, R.string.info_avoid_inclines),
    OptionInfo(R.string.advanced_bike_carriage, R.string.info_bike_carriage),
    OptionInfo(R.string.advanced_car_carriage, R.string.info_car_carriage),
)

private val STREET_LEGS_INFO = listOf(
    OptionInfo(R.string.leg_context_direct, R.string.info_direct),
    OptionInfo(R.string.leg_context_first_mile, R.string.info_first_mile),
    OptionInfo(R.string.leg_context_last_mile, R.string.info_last_mile),
    OptionInfo(R.string.advanced_modes, R.string.info_street_modes),
    OptionInfo(R.string.advanced_max_time, R.string.info_max_time),
    OptionInfo(R.string.info_rental_title, R.string.info_rental),
    OptionInfo(R.string.advanced_ignore_return_constraints, R.string.info_ignore_return_constraints),
)

private val RESULTS_INFO = listOf(
    OptionInfo(R.string.advanced_search_window, R.string.info_search_window),
    OptionInfo(R.string.advanced_min_itineraries, R.string.info_min_itineraries),
    OptionInfo(R.string.advanced_fastest_direct_factor, R.string.info_fastest_direct_factor),
    OptionInfo(R.string.advanced_slow_direct, R.string.info_slow_direct),
    OptionInfo(R.string.advanced_passengers_luggage, R.string.info_passengers_luggage),
)

private val DEPARTURES_INFO = listOf(
    OptionInfo(R.string.advanced_transit_modes, R.string.info_departures_modes),
    OptionInfo(R.string.advanced_departures_results, R.string.info_departures_results),
    OptionInfo(R.string.advanced_departures_radius, R.string.info_departures_radius),
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
        title = stringResource(R.string.advanced_options_title),
        icon = Icons.Default.Tune,
        badge = stringResource(R.string.badge_modified).takeIf { modified },
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
            ) { Text(stringResource(R.string.action_reset)) }
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
        title = stringResource(R.string.advanced_options_title),
        icon = Icons.Default.Tune,
        badge = stringResource(R.string.badge_modified).takeIf { modified },
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
            ) { Text(stringResource(R.string.action_reset)) }
        },
        modifier = modifier,
    ) {
        GroupTitle(stringResource(R.string.advanced_group_departures), DEPARTURES_INFO)
        CategoryFlow(
            title = stringResource(R.string.advanced_transit_modes),
            selected = options.departuresCategories,
            onSelectedChange = { categories -> onUpdate { it.copy(departuresCategories = categories) } },
        )
        IntSliderRow(
            title = stringResource(R.string.advanced_departures_results),
            value = options.departuresCount,
            onValueCommit = { count -> onUpdate { it.copy(departuresCount = count) } },
            min = 5,
            max = 50,
            step = 5,
        )
        IntSliderRow(
            title = stringResource(R.string.advanced_departures_radius),
            value = options.departuresRadiusMeters,
            onValueCommit = { radius -> onUpdate { it.copy(departuresRadiusMeters = radius) } },
            min = 100,
            max = 1000,
            step = 100,
            valueLabel = { stringResource(R.string.format_meters, it) },
        )
        Text(
            text = stringResource(R.string.advanced_departures_radius_note),
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
private fun GroupTitle(title: String, info: List<OptionInfo>) {
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
                contentDescription = stringResource(R.string.advanced_options_about, title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.action_ok)) }
            },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    info.forEach { entry ->
                        Column {
                            Text(
                                text = stringResource(entry.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(entry.descriptionRes),
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
            ) {
                Text(
                    stringResource(
                        if (allSelected) R.string.action_select_none else R.string.action_select_all,
                    ),
                )
            }
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

@Composable
private fun RoutingGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle(stringResource(R.string.advanced_group_routing), ROUTING_INFO)
        CategoryFlow(
            title = stringResource(R.string.advanced_transit_modes),
            selected = options.transitCategories,
            onSelectedChange = { categories -> onUpdate { it.copy(transitCategories = categories) } },
        )
        IntSliderRow(
            title = stringResource(R.string.advanced_min_transfer_time),
            value = options.minTransferTimeMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(minTransferTimeMinutes = minutes) } },
            min = 0,
            max = 30,
            valueLabel = ::formatMinutes,
        )
        IntSliderRow(
            title = stringResource(R.string.advanced_extra_transfer_buffer),
            value = options.additionalTransferTimeMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(additionalTransferTimeMinutes = minutes) } },
            min = 0,
            max = 30,
            valueLabel = ::formatMinutes,
        )
        LabeledSliderRow(
            title = stringResource(R.string.advanced_transfer_time_factor),
            value = options.transferTimeFactor,
            onValueCommit = { factor -> onUpdate { it.copy(transferTimeFactor = factor) } },
            valueRange = 1f..3f,
            steps = 7,
            valueLabel = { stringResource(R.string.format_factor_two_decimals, it) },
        )
        SwitchRow(
            title = stringResource(R.string.advanced_limit_travel_time),
            supportingText = stringResource(R.string.advanced_limit_travel_time_note),
            checked = options.maxTravelTimeMinutes != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(maxTravelTimeMinutes = if (on) 240 else null) }
            },
        )
        AnimatedVisibility(visible = options.maxTravelTimeMinutes != null) {
            IntSliderRow(
                title = stringResource(R.string.advanced_max_travel_time),
                value = options.maxTravelTimeMinutes ?: 240,
                onValueCommit = { minutes -> onUpdate { it.copy(maxTravelTimeMinutes = minutes) } },
                min = 30,
                max = 720,
                step = 30,
                valueLabel = ::formatMinutes,
            )
        }
        SwitchRow(
            title = stringResource(R.string.advanced_routed_transfers),
            supportingText = stringResource(R.string.advanced_routed_transfers_note),
            checked = options.useRoutedTransfers,
            onCheckedChange = { on -> onUpdate { it.copy(useRoutedTransfers = on) } },
        )
    }
}

@Composable
private fun AccessibilityGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle(stringResource(R.string.advanced_group_accessibility), ACCESSIBILITY_INFO)
        SingleChoiceConnectedRow(
            options = PedestrianProfile.entries,
            selected = options.pedestrianProfile,
            onSelect = { profile -> onUpdate { it.copy(pedestrianProfile = profile) } },
            label = { stringResource(it.labelRes) },
        )
        SwitchRow(
            title = stringResource(R.string.advanced_custom_walking_speed),
            checked = options.pedestrianSpeed != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(pedestrianSpeed = if (on) 1.2f else null) }
            },
        )
        AnimatedVisibility(visible = options.pedestrianSpeed != null) {
            LabeledSliderRow(
                title = stringResource(R.string.advanced_walking_speed),
                value = options.pedestrianSpeed ?: 1.2f,
                onValueCommit = { speed -> onUpdate { it.copy(pedestrianSpeed = speed) } },
                valueRange = 0.5f..2.5f,
                steps = 19,
                valueLabel = ::formatSpeed,
            )
        }
        SwitchRow(
            title = stringResource(R.string.advanced_custom_cycling_speed),
            checked = options.cyclingSpeed != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(cyclingSpeed = if (on) 4.2f else null) }
            },
        )
        AnimatedVisibility(visible = options.cyclingSpeed != null) {
            LabeledSliderRow(
                title = stringResource(R.string.advanced_cycling_speed),
                value = options.cyclingSpeed ?: 4.2f,
                onValueCommit = { speed -> onUpdate { it.copy(cyclingSpeed = speed) } },
                valueRange = 1f..8f,
                steps = 13,
                valueLabel = ::formatSpeed,
            )
        }
        Column {
            Text(
                text = stringResource(R.string.advanced_avoid_inclines),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SingleChoiceConnectedRow(
                options = ElevationCosts.entries,
                selected = options.elevationCosts,
                onSelect = { costs -> onUpdate { it.copy(elevationCosts = costs) } },
                label = { stringResource(it.labelRes) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.advanced_bike_carriage),
            supportingText = stringResource(R.string.advanced_bike_carriage_note),
            checked = options.requireBikeTransport,
            onCheckedChange = { on -> onUpdate { it.copy(requireBikeTransport = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.advanced_car_carriage),
            supportingText = stringResource(R.string.advanced_car_carriage_note),
            checked = options.requireCarTransport,
            onCheckedChange = { on -> onUpdate { it.copy(requireCarTransport = on) } },
        )
    }
}

@Composable
private fun StreetLegsGroup(options: SearchOptions, onUpdate: ((SearchOptions) -> SearchOptions) -> Unit) {
    var context by rememberSaveable { mutableStateOf(LegContext.DIRECT) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle(stringResource(R.string.advanced_group_street_legs), STREET_LEGS_INFO)
        SingleChoiceConnectedRow(
            options = LegContext.entries,
            selected = context,
            onSelect = { context = it },
            label = { stringResource(it.labelRes) },
        )
        AnimatedContent(targetState = context, label = "leg-context") { target ->
            val leg = options.legOptions(target)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = stringResource(R.string.advanced_modes),
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
                        label = { stringResource(it.labelRes) },
                        icon = { it.icon },
                    )
                }
                IntSliderRow(
                    title = stringResource(R.string.advanced_max_time),
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
                                text = stringResource(R.string.advanced_rental_vehicles),
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
                                label = { stringResource(it.labelRes) },
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.advanced_rental_propulsion),
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
                                label = { stringResource(it.labelRes) },
                            )
                        }
                        Text(
                            text = stringResource(R.string.advanced_rental_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SwitchRow(
                            title = stringResource(R.string.advanced_ignore_return_constraints),
                            supportingText = stringResource(R.string.advanced_ignore_return_constraints_note),
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
        GroupTitle(stringResource(R.string.advanced_group_results), RESULTS_INFO)
        IntSliderRow(
            title = stringResource(R.string.advanced_search_window),
            value = options.searchWindowMinutes,
            onValueCommit = { minutes -> onUpdate { it.copy(searchWindowMinutes = minutes) } },
            min = 5,
            max = 120,
            step = 5,
            valueLabel = ::formatMinutes,
        )
        IntSliderRow(
            title = stringResource(R.string.advanced_min_itineraries),
            value = options.numItineraries,
            onValueCommit = { count -> onUpdate { it.copy(numItineraries = count) } },
            min = 1,
            max = 10,
        )
        LabeledSliderRow(
            title = stringResource(R.string.advanced_fastest_direct_factor),
            value = options.fastestDirectFactor,
            onValueCommit = { factor -> onUpdate { it.copy(fastestDirectFactor = factor) } },
            valueRange = 1f..5f,
            steps = 7,
            valueLabel = { stringResource(R.string.format_factor_one_decimal, it) },
        )
        SwitchRow(
            title = stringResource(R.string.advanced_slow_direct),
            supportingText = stringResource(R.string.advanced_slow_direct_note),
            checked = options.slowDirect,
            onCheckedChange = { on -> onUpdate { it.copy(slowDirect = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.advanced_passengers_luggage),
            supportingText = stringResource(R.string.advanced_passengers_luggage_note),
            checked = options.passengers != null,
            onCheckedChange = { on ->
                onUpdate { it.copy(passengers = if (on) 1 else null, luggage = if (on) 0 else null) }
            },
        )
        AnimatedVisibility(visible = options.passengers != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IntSliderRow(
                    title = stringResource(R.string.advanced_passengers),
                    value = options.passengers ?: 1,
                    onValueCommit = { count -> onUpdate { it.copy(passengers = count) } },
                    min = 1,
                    max = 8,
                )
                IntSliderRow(
                    title = stringResource(R.string.advanced_luggage),
                    value = options.luggage ?: 0,
                    onValueCommit = { count -> onUpdate { it.copy(luggage = count) } },
                    min = 0,
                    max = 8,
                )
            }
        }
    }
}
