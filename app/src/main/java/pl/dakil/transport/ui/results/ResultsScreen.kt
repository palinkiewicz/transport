package pl.dakil.transport.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onBack: () -> Unit,
    onJourneySelected: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${viewModel.fromName} → ${viewModel.toName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is ResultsUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is ResultsUiState.Error -> ErrorBox(state.message, Modifier.padding(innerPadding))
            is ResultsUiState.Content -> {
                if (state.result.journeys.isEmpty()) {
                    ErrorBox("No connections found", Modifier.padding(innerPadding))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.result.journeys.size) { index ->
                            JourneyCard(
                                journey = state.result.journeys[index],
                                onClick = { onJourneySelected(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(journey: Journey, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = journey.startTime.format(timeFormatter),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = " – ${journey.endTime.format(timeFormatter)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "(${journey.duration / 60} min, ${journey.transfers} transfers)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                journey.legs.filter { it.isTransit }.forEach { leg ->
                    ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                }
                if (journey.legs.none { it.isTransit }) {
                    ModeChip(mode = journey.legs.first().mode, label = journey.legs.first().mode.name)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = "${journey.fromName} → ${journey.toName}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
