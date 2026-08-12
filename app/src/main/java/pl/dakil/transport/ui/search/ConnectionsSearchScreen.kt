package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R
import pl.dakil.transport.ui.components.SingleChoiceConnectedRow
import pl.dakil.transport.ui.navigation.ResultsRoute

/**
 * The Connections tab: start/destination, when to travel, and every plan-API option, ending
 * in the search action that opens the results list.
 */
@Composable
fun ConnectionsSearchScreen(
    onSearch: (ResultsRoute) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onPickVia: (index: Int) -> Unit,
    viewModel: ConnectionsSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var openSheet by rememberSaveable { mutableStateOf<SearchOptionsSheet?>(null) }

    Scaffold(
        // No TopAppBar here, so this screen owns the top/horizontal system bar insets itself.
        // Bottom is intentionally excluded: the app-level bottom navigation bar (shown for this
        // route) already clears the navigation bar inset, so adding it again would leave a gap.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SearchHeader(icon = Icons.Default.Route, title = stringResource(R.string.connections_title))

            RouteCard(
                uiState = uiState,
                onPickFrom = onPickFrom,
                onPickTo = onPickTo,
                onPickVia = onPickVia,
                onAddVia = { onPickVia(uiState.vias.size) },
                onRemoveVia = viewModel::removeVia,
                onViaMinimumStayChange = viewModel::setViaMinimumStay,
                onSwap = viewModel::swapFromTo,
                onClearFrom = viewModel::clearFrom,
                onClearTo = viewModel::clearTo,
            )

            // Explains why Search is disabled; the route card itself gives no hint that the
            // two picked places are one and the same.
            AnimatedVisibility(visible = uiState.hasSameEndpoints) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.connections_same_endpoints),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SingleChoiceConnectedRow(
                options = listOf(false, true),
                selected = uiState.options.arriveBy,
                onSelect = { arriveBy -> viewModel.updateOptions { it.copy(arriveBy = arriveBy) } },
                label = { arriveBy ->
                    stringResource(
                        if (arriveBy) R.string.connections_arrive_by else R.string.connections_depart_at,
                    )
                },
            )

            // Only worth offering when the picked time has actually drifted from the clock.
            DateTimeRow(
                dateTime = uiState.dateTime,
                onDateTimeChange = viewModel::setDateTime,
                onResetToNow = viewModel::setDateTimeToNow,
                showResetToNow = isAwayFromNow(uiState.dateTime),
            )

            SearchOptionsBar {
                SearchOptionsButton(
                    icon = Icons.Default.ChangeCircle,
                    title = stringResource(R.string.advanced_group_transfers),
                    onClick = { openSheet = SearchOptionsSheet.TRANSFERS },
                    // The only option worth reading without opening its sheet: how many
                    // transfers the results may have. Null means no limit, so no badge.
                    badge = uiState.options.maxTransfers?.toString(),
                )
                SearchOptionsButton(
                    icon = Icons.Default.DirectionsTransit,
                    title = stringResource(R.string.advanced_transit_modes),
                    onClick = { openSheet = SearchOptionsSheet.TRANSIT_MODES },
                )
                SearchOptionsButton(
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    title = stringResource(R.string.advanced_group_street_legs),
                    onClick = { openSheet = SearchOptionsSheet.STREET_LEGS },
                )
                SearchOptionsButton(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.advanced_group_pace),
                    onClick = { openSheet = SearchOptionsSheet.PACE },
                )
                SearchOptionsButton(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.advanced_options_title),
                    onClick = { openSheet = SearchOptionsSheet.ADVANCED },
                )
            }

            SearchButton(
                onClick = {
                    val from = uiState.from ?: return@SearchButton
                    val to = uiState.to ?: return@SearchButton
                    onSearch(
                        ResultsRoute(
                            fromName = from.name,
                            fromLat = from.lat,
                            fromLon = from.lon,
                            fromStopId = from.stopId,
                            toName = to.name,
                            toLat = to.lat,
                            toLon = to.lon,
                            toStopId = to.stopId,
                            timeIso = uiState.dateTime.toRouteArg(),
                            viasJson = viewModel.viasRouteArg(),
                        ),
                    )
                },
                enabled = uiState.canSearch,
            )
        }
    }

    SearchOptionsSheetHost(
        sheet = openSheet,
        options = uiState.options,
        onUpdate = viewModel::updateOptions,
        onDismiss = { openSheet = null },
    )
}

/**
 * Transit-style route card: origin/destination fields joined by a dotted "route rail",
 * with a shape-morphing swap button on the divider between them. The fields open the
 * full-screen location picker rather than editing in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteCard(
    uiState: ConnectionsUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onPickVia: (index: Int) -> Unit,
    onAddVia: () -> Unit,
    onRemoveVia: (index: Int) -> Unit,
    onViaMinimumStayChange: (index: Int, minutes: Int) -> Unit,
    onSwap: () -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TripOrigin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
                LocationField(
                    label = stringResource(R.string.connections_field_start),
                    value = uiState.from?.name,
                    onClick = onPickFrom,
                    modifier = Modifier.weight(1f),
                    onClear = onClearFrom,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                FilledTonalIconButton(
                    onClick = onSwap,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.connections_swap))
                }
            }

            // Intermediate stops sit between the two endpoints, in travel order.
            uiState.vias.forEachIndexed { index, via ->
                key(via.location.favoriteKey, index) {
                    ViaStopRow(
                        index = index,
                        via = via,
                        onPick = { onPickVia(index) },
                        onRemove = { onRemoveVia(index) },
                        onMinimumStayChange = { minutes -> onViaMinimumStayChange(index, minutes) },
                    )
                }
            }
            AddViaRow(canAdd = uiState.canAddVia, onAdd = onAddVia)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
                LocationField(
                    label = stringResource(R.string.connections_field_destination),
                    value = uiState.to?.name,
                    onClick = onPickTo,
                    modifier = Modifier.weight(1f),
                    onClear = onClearTo,
                )
            }
        }
    }
}
