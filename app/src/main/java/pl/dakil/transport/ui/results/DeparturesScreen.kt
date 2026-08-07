package pl.dakil.transport.ui.results

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.OffsetDateTime
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.Departure
import pl.dakil.transport.ui.components.EmptyBox
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.LineColorRequest
import pl.dakil.transport.ui.components.rememberLineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.rememberTimeFormatter
import pl.dakil.transport.ui.components.RefreshButton
import pl.dakil.transport.ui.components.refreshSubtitle
import pl.dakil.transport.ui.components.timeDeviationColor
import pl.dakil.transport.ui.navigation.TripRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeparturesScreen(
    onBack: () -> Unit,
    onDepartureSelected: (TripRoute) -> Unit,
    viewModel: DeparturesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedLines by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(viewModel.stopName) },
                subtitle = { Text(refreshSubtitle(R.string.refresh_subtitle_departures, secondsUntilRefresh)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    RefreshButton(secondsUntilRefresh, onRefresh = viewModel::refreshNow)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is DeparturesUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is DeparturesUiState.Error -> ErrorBox(
                error = state.error,
                modifier = Modifier.padding(innerPadding),
                onRetry = viewModel::refreshNow,
            )
            is DeparturesUiState.Content -> {
                val all = state.departures.departures
                if (all.isEmpty()) {
                    EmptyBox(
                        title = stringResource(R.string.departures_empty_title),
                        description = stringResource(
                            if (viewModel.clickedPoleStopId == null) {
                                R.string.departures_empty_body_area
                            } else {
                                R.string.departures_empty_body
                            },
                        ),
                        modifier = Modifier.padding(innerPadding),
                        actionLabel = stringResource(R.string.departures_empty_action),
                        onAction = viewModel::refreshNow,
                    )
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
                        // Flattened in draw order: the palette is handed out down the board, and
                        // "neighbouring lines" means what it looks like on screen rather than what
                        // the API happened to return.
                        val lineColors = rememberLineColors(
                            groups.flatMap { group ->
                                group.departures.map { departure ->
                                    LineColorRequest(departure.routeColor, departure.mode.color)
                                }
                            },
                        )
                        // Where each pole's rows start in that flat order.
                        val colorOffsets = remember(groups) {
                            groups.runningFold(0) { total: Int, group: DepartureGroup ->
                                total + group.departures.size
                            }
                        }
                        // Which stop a departure leaves from is only worth naming when the
                        // board spans more than one — an around-a-point search, or a station
                        // whose poles the feed names individually.
                        val showStopNames = remember(groups) {
                            viewModel.clickedPoleStopId == null || groups.distinctBy { it.stopName }.size > 1
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            groups.forEachIndexed { groupIndex, group ->
                                item(key = "header-${group.key}") {
                                    DepartureGroupHeader(group, showStopName = showStopNames)
                                }
                                items(group.departures.size, key = { "${group.key}-$it" }) { index ->
                                    val departure = group.departures[index]
                                    DepartureRow(
                                        departure = departure,
                                        containerColor = lineColors.at(
                                            colorOffsets[groupIndex] + index,
                                            departure.mode.color,
                                        ),
                                        onClick = departure.tripId?.let { tripId ->
                                            {
                                                onDepartureSelected(
                                                    TripRoute(
                                                        tripId = tripId,
                                                        lineLabel = departure.lineLabel,
                                                        headsign = departure.headsign,
                                                        modeName = departure.mode.name,
                                                        routeColor = departure.routeColor,
                                                    ),
                                                )
                                            }
                                        },
                                    )
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
private fun DepartureRow(departure: Departure, containerColor: Color, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeChip(mode = departure.mode, label = departure.lineLabel, containerColor = containerColor)
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
    val timeFormatter = rememberTimeFormatter()
    val cancelled = departure.cancelled || departure.tripCancelled
    val minutesUntil = Duration.between(OffsetDateTime.now(), departure.time).toMinutes()
    val delayed = departure.time != departure.scheduledTime
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (cancelled) {
                stringResource(R.string.departures_cancelled)
            } else {
                countdownLabel(minutesUntil, departure.time)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                cancelled || minutesUntil < 0 -> MaterialTheme.colorScheme.error
                delayed && departure.realTime ->
                    timeDeviationColor(departure.time, departure.scheduledTime) ?: MaterialTheme.colorScheme.primary
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

@Composable
private fun countdownLabel(minutesUntil: Long, time: OffsetDateTime): String = when {
    minutesUntil < 0 -> pluralStringResource(
        R.plurals.departures_minutes_ago,
        (-minutesUntil).toInt(),
        (-minutesUntil).toInt(),
    )
    minutesUntil == 0L -> stringResource(R.string.departures_now)
    minutesUntil < 60 -> stringResource(R.string.format_minutes, minutesUntil.toInt())
    else -> time.format(rememberTimeFormatter())
}

/**
 * Header naming the direction a pole serves. Built here rather than in the ViewModel so its
 * wording stays translatable; the feed's own destination and platform names pass through.
 */
@Composable
private fun DepartureGroupHeader(group: DepartureGroup, showStopName: Boolean) {
    val parts = buildList {
        if (group.headsigns.isNotEmpty()) {
            add(stringResource(R.string.departures_group_towards, group.headsigns.joinToString(" / ")))
        }
        group.track?.let { add(stringResource(R.string.format_platform, it)) }
    }
    val text = parts.joinToString(" · ").ifEmpty { stringResource(R.string.departures_group_default) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // The stop leads when it is in question — a board around a point, or a station
            // whose poles the feed names separately — because it is what tells the poles apart.
            if (showStopName) {
                Text(
                    text = group.stopName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
