package pl.dakil.transport.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
import pl.dakil.transport.ui.components.parseRouteColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onBack: () -> Unit,
    onJourneySelected: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("${viewModel.fromName} → ${viewModel.toName}") },
                subtitle = { Text("Connections · refreshes in $secondsUntilRefresh sec") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
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
                        item(key = "show-previous") {
                            PageButton(
                                label = "Show previous",
                                icon = Icons.Default.KeyboardArrowUp,
                                enabled = state.result.previousPageCursor != null,
                                onClick = viewModel::showPrevious,
                            )
                        }
                        items(state.result.journeys.size) { index ->
                            JourneyCard(
                                journey = state.result.journeys[index],
                                onClick = { onJourneySelected(index) },
                            )
                        }
                        item(key = "show-next") {
                            PageButton(
                                label = "Show next",
                                icon = Icons.Default.KeyboardArrowDown,
                                enabled = state.result.nextPageCursor != null,
                                onClick = viewModel::showNext,
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
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val minutesUntilDeparture = Duration.between(OffsetDateTime.now(), journey.departureTime).toMinutes()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = departingLabel(minutesUntilDeparture),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (minutesUntilDeparture < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${formatDuration(journey.transitDurationSeconds)} · ${transfersLabel(journey.transfers)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LegTimelineBar(journey)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                journey.walkToFirstStopMeters?.let { WalkDistance(it) }
                journey.legs.filter { it.isTransit }.forEach { leg ->
                    ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                }
                if (journey.legs.none { it.isTransit }) {
                    ModeChip(mode = journey.legs.first().mode, label = journey.legs.first().mode.label)
                }
                journey.walkFromLastStopMeters?.let { WalkDistance(it) }
            }

            HorizontalDivider()

            StopTimeRow(
                stopName = journey.firstStopName,
                time = journey.departureTime,
                scheduledTime = journey.departureScheduledTime,
            )
            StopTimeRow(
                stopName = journey.lastStopName,
                time = journey.arrivalTime,
                scheduledTime = journey.arrivalScheduledTime,
            )
        }
    }
}

/**
 * Proportional strip of the journey: one segment per leg, width proportional to leg duration,
 * colored by route/mode (walk legs in a muted tone) — a glanceable shape of the trip.
 */
@Composable
private fun LegTimelineBar(journey: Journey) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        journey.legs.forEach { leg ->
            val color = if (leg.isTransit) {
                parseRouteColor(leg.routeColor, leg.mode.color)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(leg.duration.coerceAtLeast(60).toFloat())
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
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

private fun transfersLabel(transfers: Int): String = when (transfers) {
    0 -> "direct"
    1 -> "1 transfer"
    else -> "$transfers transfers"
}

private fun formatWalkDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

private fun departingLabel(minutesUntil: Long): String {
    val minutes = if (minutesUntil < 0) -minutesUntil else minutesUntil
    val relative = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    return when {
        minutesUntil < 0 -> "Departed $relative ago"
        minutesUntil == 0L -> "Departing now"
        else -> "Departing in $relative"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
