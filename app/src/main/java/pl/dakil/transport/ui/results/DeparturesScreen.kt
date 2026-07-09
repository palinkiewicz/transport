package pl.dakil.transport.ui.results

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeparturesScreen(
    onBack: () -> Unit,
    viewModel: DeparturesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedLines by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(viewModel.stopName) },
                subtitle = { Text("Live departures · refreshes every 30 s") },
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
            is DeparturesUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is DeparturesUiState.Error -> ErrorBox(state.message, Modifier.padding(innerPadding))
            is DeparturesUiState.Content -> {
                val all = state.departures.departures
                if (all.isEmpty()) {
                    ErrorBox("No upcoming departures", Modifier.padding(innerPadding))
                } else {
                    val lines = remember(all) {
                        all.map { it.lineLabel }
                            .distinct()
                            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
                    }
                    // Drop stale selections (lines can disappear as the timetable window moves).
                    val activeSelection = selectedLines intersect lines.toSet()
                    val filtered = if (activeSelection.isEmpty()) all else all.filter { it.lineLabel in activeSelection }

                    Column(modifier = Modifier.padding(innerPadding)) {
                        if (lines.size > 1) {
                            LineFilterRow(
                                lines = lines,
                                selected = activeSelection,
                                onToggle = { line ->
                                    selectedLines = if (line in activeSelection) activeSelection - line else activeSelection + line
                                },
                            )
                        }
                        val groups = filtered.groupedByPole(viewModel.clickedPoleStopId)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
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
}

/** Horizontally scrollable row of line toggle buttons; empty selection means "show all". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LineFilterRow(
    lines: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        lines.forEach { line ->
            ToggleButton(
                checked = line in selected,
                onCheckedChange = { onToggle(line) },
            ) {
                Text(line)
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
        DepartureCountdown(departure)
    }
}

/** Trailing countdown: big relative time on top, absolute (plus struck scheduled when delayed) below. */
@Composable
private fun DepartureCountdown(departure: Departure) {
    val cancelled = departure.cancelled || departure.tripCancelled
    val delayed = departure.time != departure.scheduledTime
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (cancelled) "Cancelled" else countdownLabel(departure.time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                cancelled -> MaterialTheme.colorScheme.error
                delayed && departure.realTime -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = departure.time.format(timeFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (delayed) {
                Text(
                    text = departure.scheduledTime.format(timeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun countdownLabel(time: OffsetDateTime): String {
    val minutes = Duration.between(OffsetDateTime.now(), time).toMinutes()
    return when {
        minutes <= 0 -> "now"
        minutes < 60 -> "$minutes min"
        else -> time.format(timeFormatter)
    }
}

@Composable
private fun DepartureGroupHeader(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
