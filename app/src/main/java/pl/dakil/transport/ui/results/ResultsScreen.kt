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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import pl.dakil.transport.domain.model.ConnectionTimesMode
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.ui.components.EmptyBox
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.formatDistance
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.RealTimeText
import pl.dakil.transport.ui.components.RefreshButton
import pl.dakil.transport.ui.components.refreshSubtitle
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
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val timesMode by viewModel.connectionTimesMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    // Intermediate stops are part of the route, so they belong in the title
                    // rather than being silently applied to the results.
                    val stops = listOf(viewModel.fromName) + viewModel.viaNames + viewModel.toName
                    Text(stops.joinToString(" → "))
                },
                subtitle = { Text(refreshSubtitle("Connections", secondsUntilRefresh)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    RefreshButton(secondsUntilRefresh, onRefresh = viewModel::refreshNow)
                    FavoriteButton(isFavorite = isFavorite, onToggle = viewModel::toggleFavorite)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is ResultsUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is ResultsUiState.Error -> ErrorBox(
                error = state.error,
                modifier = Modifier.padding(innerPadding),
                onRetry = viewModel::refreshNow,
            )
            is ResultsUiState.Content -> {
                if (state.result.journeys.isEmpty()) {
                    EmptyBox(
                        title = "No connections found",
                        description = "Nothing runs between these places at this time. Try another " +
                            "departure time, or relax the advanced search options.",
                        modifier = Modifier.padding(innerPadding),
                        actionLabel = "Search again",
                        onAction = viewModel::refreshNow,
                    )
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
                                fromName = viewModel.fromName,
                                toName = viewModel.toName,
                                timesMode = timesMode,
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
private fun JourneyCard(
    journey: Journey,
    /** Searched origin/destination, shown when the journey has no transit stops to name. */
    fromName: String,
    toName: String,
    timesMode: ConnectionTimesMode,
    onClick: () -> Unit,
) {
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
            // Whether the walk to the first stop is part of the headline. Only true when there
            // is a walk at all — otherwise "leave" and "depart" are the same moment.
            val doorToDoor = timesMode.includesDoorToDoor && journey.startTime != journey.departureTime
            val countdownFrom = if (doorToDoor) journey.startTime else journey.departureTime
            val minutesUntilDeparture = Duration.between(OffsetDateTime.now(), countdownFrom).toMinutes()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = departingLabel(minutesUntilDeparture, leaving = doorToDoor),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (minutesUntilDeparture < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = buildString {
                        val seconds = if (timesMode == ConnectionTimesMode.DOOR_TO_DOOR) {
                            journey.duration.toLong()
                        } else {
                            journey.transitDurationSeconds
                        }
                        append(formatDuration(seconds))
                        append(" · ")
                        append(transfersLabel(journey.transfers))
                    },
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
                journey.firstMileLeg?.let { AccessLegDistance(it) }
                journey.legs.filter { it.isTransit }.forEach { leg ->
                    ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                }
                if (journey.legs.none { it.isTransit }) {
                    ModeChip(mode = journey.legs.first().mode, label = journey.legs.first().mode.label)
                }
                journey.lastMileLeg?.let { AccessLegDistance(it) }
            }

            HorizontalDivider()

            // A journey done entirely on foot/bike/car has no stops to name: its ends are the
            // searched places themselves, which the API labels "START"/"END".
            val isDirect = journey.legs.none { it.isTransit }
            if (timesMode == ConnectionTimesMode.DOOR_TO_DOOR) {
                // The overall times have no scheduled counterpart, so they are drawn plainly —
                // any delay is already folded into them.
                StopTimeRow(stopName = fromName, time = journey.startTime, scheduledTime = null)
                StopTimeRow(stopName = toName, time = journey.endTime, scheduledTime = null)
            } else {
                if (timesMode == ConnectionTimesMode.BOTH && doorToDoor) {
                    DoorToDoorRow(journey, fromName, toName)
                }
                StopTimeRow(
                    stopName = if (isDirect) fromName else journey.firstStopName,
                    time = journey.departureTime,
                    scheduledTime = journey.departureScheduledTime,
                )
                StopTimeRow(
                    stopName = if (isDirect) toName else journey.lastStopName,
                    time = journey.arrivalTime,
                    scheduledTime = journey.arrivalScheduledTime,
                )
            }
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
private fun AccessLegDistance(leg: JourneyLeg) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            leg.mode.icon,
            contentDescription = leg.mode.label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        leg.distanceMeters?.let { meters ->
            Text(
                text = formatDistance(meters),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The whole trip end to end, walk included — the line that answers "when do I leave and when
 * am I there", above the stop times that answer "when does the vehicle go".
 */
@Composable
private fun DoorToDoorRow(journey: Journey, fromName: String, toName: String) {
    Text(
        text = "${journey.startTime.format(cardTimeFormatter)} → " +
            "${journey.endTime.format(cardTimeFormatter)} · $fromName → $toName",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** [scheduledTime] null means there is no schedule to compare against; the time is drawn plainly. */
@Composable
private fun StopTimeRow(stopName: String, time: OffsetDateTime, scheduledTime: OffsetDateTime?) {
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
        if (scheduledTime == null) {
            Text(text = time.format(cardTimeFormatter), style = MaterialTheme.typography.bodyLarge)
        } else {
            RealTimeText(time = time, scheduledTime = scheduledTime, realTime = true)
        }
    }
}

private val cardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun transfersLabel(transfers: Int): String = when (transfers) {
    0 -> "direct"
    1 -> "1 transfer"
    else -> "$transfers transfers"
}

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

private const val MINUTES_PER_DAY = 24 * 60

/** [leaving] switches the wording to the door-to-door question: when to set off, not when the vehicle goes. */
private fun departingLabel(minutesUntil: Long, leaving: Boolean): String {
    val minutes = if (minutesUntil < 0) -minutesUntil else minutesUntil
    // A day or more out, the hours and minutes are noise (and stale by the time it matters).
    val relative = when {
        minutes >= MINUTES_PER_DAY -> {
            val days = minutes / MINUTES_PER_DAY
            "$days ${if (days == 1L) "day" else "days"}"
        }
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
    return when {
        minutesUntil < 0 -> if (leaving) "Left $relative ago" else "Departed $relative ago"
        minutesUntil == 0L -> if (leaving) "Leave now" else "Departing now"
        else -> if (leaving) "Leave in $relative" else "Departing in $relative"
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
