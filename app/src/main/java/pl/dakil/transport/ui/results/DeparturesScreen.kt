package pl.dakil.transport.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.RealTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    onBack: () -> Unit,
    viewModel: DeparturesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.stopName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is DeparturesUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is DeparturesUiState.Error -> ErrorBox(state.message, Modifier.padding(innerPadding))
            is DeparturesUiState.Content -> {
                if (state.departures.departures.isEmpty()) {
                    ErrorBox("No upcoming departures", Modifier.padding(innerPadding))
                } else {
                    val groups = state.departures.departures.groupedByPole(viewModel.clickedPoleStopId)
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "header-${group.poleStopId}") {
                                DepartureGroupHeader(group.header)
                            }
                            items(group.departures.size, key = { "${group.poleStopId}-$it" }) { index ->
                                DepartureRow(group.departures[index])
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeChip(mode = departure.mode, label = departure.lineLabel, routeColorHex = departure.routeColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = departure.headsign ?: "",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (departure.cancelled || departure.tripCancelled) TextDecoration.LineThrough else null,
            )
        }
        RealTimeText(
            time = departure.time,
            scheduledTime = departure.scheduledTime,
            realTime = departure.realTime,
        )
    }
}

@Composable
private fun DepartureGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
