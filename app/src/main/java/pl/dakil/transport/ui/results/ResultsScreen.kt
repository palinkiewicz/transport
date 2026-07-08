package pl.dakil.transport.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.RealTimeText

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyCard(journey: Journey, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                journey.walkToFirstStopMeters?.let { WalkDistance(it) }
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    journey.legs.filter { it.isTransit }.forEach { leg ->
                        ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                    }
                    if (journey.legs.none { it.isTransit }) {
                        ModeChip(mode = journey.legs.first().mode, label = journey.legs.first().mode.name)
                    }
                }
                journey.walkFromLastStopMeters?.let { WalkDistance(it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            StopTimeRow(
                stopName = journey.firstStopName,
                time = journey.departureTime,
                scheduledTime = journey.departureScheduledTime,
            )
            Spacer(Modifier.size(4.dp))
            StopTimeRow(
                stopName = journey.lastStopName,
                time = journey.arrivalTime,
                scheduledTime = journey.arrivalScheduledTime,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = departingInLabel(journey.departureTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(journey.transitDurationSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WalkDistance(meters: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = "Walk",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatWalkDistance(meters),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StopTimeRow(stopName: String, time: OffsetDateTime, scheduledTime: OffsetDateTime) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stopName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        RealTimeText(time = time, scheduledTime = scheduledTime, realTime = true)
    }
}

private fun formatWalkDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

@Composable
private fun departingInLabel(departureTime: OffsetDateTime): String {
    val minutes = Duration.between(OffsetDateTime.now(), departureTime).toMinutes()
    return when {
        minutes <= 0 -> "Departing now"
        minutes < 60 -> "Departing in $minutes min"
        else -> "Departing in ${minutes / 60} h ${minutes % 60} min"
    }
}
