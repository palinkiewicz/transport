package pl.dakil.transport.ui.itinerary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.InlineRealTimeText
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.parseRouteColor

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItineraryScreen(journey: Journey?, fromName: String, toName: String, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Itinerary") },
                subtitle = { Text("$fromName → $toName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (journey == null) {
            ErrorBox("Itinerary not available", Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item(key = "summary") {
                    JourneySummary(journey)
                    Spacer(Modifier.height(20.dp))
                }
                items(journey.legs.size) { index ->
                    LegRow(
                        leg = journey.legs[index],
                        fromNameOverride = if (index == 0) fromName else null,
                        toNameOverride = if (index == journey.legs.lastIndex) toName else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneySummary(journey: Journey) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryStat("Depart", journey.departureTime.format(timeFormatter))
            SummaryStat("Duration", formatDuration(journey.transitDurationSeconds))
            SummaryStat("Transfers", journey.transfers.toString())
            SummaryStat("Arrive", journey.arrivalTime.format(timeFormatter))
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegRow(leg: JourneyLeg, fromNameOverride: String? = null, toNameOverride: String? = null) {
    val legColor = if (leg.isTransit) parseRouteColor(leg.routeColor, leg.mode.color) else MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Rail: colored dot + connector line (dashed for walk legs).
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(legColor),
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .drawBehind {
                        drawLine(
                            color = legColor,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = size.width,
                            cap = StrokeCap.Round,
                            pathEffect = if (leg.isTransit) {
                                null
                            } else {
                                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                            },
                        )
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            if (leg.isTransit || fromNameOverride != null) {
                StopRow(
                    time = leg.startTime,
                    scheduledTime = leg.scheduledStartTime,
                    name = fromNameOverride ?: leg.fromName,
                    track = leg.fromTrack,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (leg.isTransit) {
                ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                leg.headsign?.let {
                    Text("towards $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                IntermediateStopsSection(leg, legColor)
            } else {
                Text(
                    text = buildString {
                        append("${leg.mode.label} · ${leg.duration / 60} min")
                        leg.distanceMeters?.let { append(" · ${formatDistance(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (leg.isTransit || toNameOverride != null) {
                Spacer(Modifier.height(8.dp))
                StopRow(
                    time = leg.endTime,
                    scheduledTime = leg.scheduledEndTime,
                    name = toNameOverride ?: leg.toName,
                    track = leg.toTrack,
                )
            }
        }
    }
}

@Composable
private fun StopRow(time: OffsetDateTime, scheduledTime: OffsetDateTime, name: String, track: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineRealTimeText(time = time, scheduledTime = scheduledTime)
        Text(name, style = MaterialTheme.typography.bodyMedium)
        track?.let { TrackPill(it) }
    }
}

@Composable
private fun TrackPill(track: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Pl. $track",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "n stops · m min" row that expands into the list of intermediate stops with arrival times. */
@Composable
private fun IntermediateStopsSection(leg: JourneyLeg, legColor: androidx.compose.ui.graphics.Color) {
    val rideLabel = "${leg.duration / 60} min ride"
    if (leg.intermediateStops.isEmpty()) {
        Text(
            text = rideLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded }
            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Hide intermediate stops" else "Show intermediate stops",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "${leg.intermediateStops.size} stops · $rideLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            leg.intermediateStops.forEach { stop ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(legColor),
                    )
                    stop.arrivalTime?.let { arrival ->
                        val scheduled = stop.scheduledArrivalTime
                        if (scheduled != null) {
                            InlineRealTimeText(
                                time = arrival,
                                scheduledTime = scheduled,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            Text(
                                text = arrival.format(timeFormatter),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stop.name, style = MaterialTheme.typography.bodySmall)
                    stop.track?.let { TrackPill(it) }
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
