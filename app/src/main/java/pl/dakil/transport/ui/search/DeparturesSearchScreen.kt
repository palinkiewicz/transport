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
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            SearchHeader(icon = Icons.Default.DepartureBoard, title = "Leaving from?")

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
                        label = "Stop",
                        value = uiState.stop?.name,
                        onClick = onPickStop,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Both sides of the board map to the plan/stoptimes `arriveBy` parameter.
            SingleChoiceConnectedRow(
                options = listOf(false, true),
                selected = uiState.options.arriveBy,
                onSelect = { arriveBy -> viewModel.updateOptions { it.copy(arriveBy = arriveBy) } },
                label = { arriveBy -> if (arriveBy) "Arrivals" else "Departures" },
            )

            DateTimeRow(dateTime = uiState.dateTime, onDateTimeChange = viewModel::setDateTime)

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

            DeparturesAdvancedOptions(
                options = uiState.options,
                onUpdate = viewModel::updateOptions,
            )
        }
    }
}
