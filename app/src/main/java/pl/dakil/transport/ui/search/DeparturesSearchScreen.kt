package pl.dakil.transport.ui.search

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
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import pl.dakil.transport.ui.navigation.DepartureBoardRoute

/**
 * The Departures tab: one stop, a moment in time, and the stoptimes-API options — opens the
 * departures/arrivals board for the picked stop.
 */
@Composable
fun DeparturesSearchScreen(
    onSearch: (DepartureBoardRoute) -> Unit,
    onPickStop: () -> Unit,
    viewModel: DeparturesSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var openSheet by rememberSaveable { mutableStateOf<SearchOptionsSheet?>(null) }

    Scaffold(
        // See ConnectionsSearchScreen: the app-level bottom bar already clears the bottom inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SearchHeader(
                icon = Icons.Default.DepartureBoard,
                title = stringResource(R.string.departures_title),
            )

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.TripOrigin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                    )
                    LocationField(
                        label = stringResource(R.string.departures_field_stop),
                        value = uiState.stop?.name,
                        onClick = onPickStop,
                        modifier = Modifier.weight(1f),
                        onClear = viewModel::clearStop,
                    )
                }
            }

            // Both sides of the board map to the plan/stoptimes `arriveBy` parameter.
            SingleChoiceConnectedRow(
                options = listOf(false, true),
                selected = uiState.options.arriveBy,
                onSelect = { arriveBy -> viewModel.updateOptions { it.copy(arriveBy = arriveBy) } },
                label = { arriveBy ->
                    stringResource(
                        if (arriveBy) R.string.departures_mode_arrivals else R.string.departures_mode_departures,
                    )
                },
            )

            // Same rule as the connections form: offer the reset once the time has drifted.
            DateTimeRow(
                dateTime = uiState.dateTime,
                onDateTimeChange = viewModel::setDateTime,
                onResetToNow = viewModel::setDateTimeToNow,
                showResetToNow = isAwayFromNow(uiState.dateTime),
            )

            SearchOptionsBar {
                SearchOptionsButton(
                    icon = Icons.Default.DirectionsTransit,
                    title = stringResource(R.string.advanced_transit_modes),
                    onClick = { openSheet = SearchOptionsSheet.DEPARTURES_MODES },
                )
                SearchOptionsButton(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.advanced_options_title),
                    onClick = { openSheet = SearchOptionsSheet.DEPARTURES_BOARD },
                )
            }

            SearchButton(
                onClick = {
                    val stop = uiState.stop ?: return@SearchButton
                    onSearch(
                        DepartureBoardRoute(
                            stopName = stop.name,
                            lat = stop.lat,
                            lon = stop.lon,
                            stopId = stop.stopId,
                            timeIso = uiState.dateTime.toRouteArg(),
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
