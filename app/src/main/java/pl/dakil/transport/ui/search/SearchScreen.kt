package pl.dakil.transport.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import pl.dakil.transport.ui.navigation.DeparturesRoute
import pl.dakil.transport.ui.navigation.ResultsRoute

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onSearchConnections: (ResultsRoute) -> Unit,
    onSearchDepartures: (DeparturesRoute) -> Unit,
    onPickLocation: (isFrom: Boolean) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        // No TopAppBar here, so this screen owns the top/horizontal system bar insets itself.
        // Bottom is intentionally excluded: the app-level bottom navigation bar (shown for this
        // route) already clears the navigation bar inset, so adding it again would leave a gap.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SearchHeader(mode = uiState.mode)

            ModeToggle(mode = uiState.mode, onModeChange = viewModel::setMode)

            RouteCard(
                uiState = uiState,
                onPickFrom = { onPickLocation(true) },
                onPickTo = { onPickLocation(false) },
                onSwap = viewModel::swapFromTo,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Pick date")
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.dateTime.format(dateFormatter))
                }
                FilledTonalButton(
                    onClick = { showTimePicker = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.dateTime.format(timeFormatter))
                }
            }

            AnimatedVisibility(visible = uiState.mode == SearchMode.CONNECTIONS) {
                MaxTransfersPicker(
                    value = uiState.maxTransfers,
                    onSelect = viewModel::setMaxTransfers,
                )
            }

            Button(
                onClick = {
                    val state = uiState
                    val from = state.fromSelected ?: return@Button
                    if (state.mode == SearchMode.CONNECTIONS) {
                        val to = state.toSelected ?: return@Button
                        onSearchConnections(
                            ResultsRoute(
                                fromName = from.name,
                                fromLat = from.lat,
                                fromLon = from.lon,
                                fromStopId = from.stopId,
                                toName = to.name,
                                toLat = to.lat,
                                toLon = to.lon,
                                toStopId = to.stopId,
                                maxTransfers = state.maxTransfers,
                                timeIso = state.dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            ),
                        )
                    } else {
                        onSearchDepartures(
                            DeparturesRoute(
                                stopName = from.name,
                                lat = from.lat,
                                lon = from.lon,
                                stopId = from.stopId,
                                timeIso = state.dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            ),
                        )
                    }
                },
                enabled = uiState.canSearch,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.LargeContainerHeight),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.LargeContainerHeight)))
                Text("Search", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dateTime.toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.setDateTime(
                            uiState.dateTime.withYear(newDate.year).withMonth(newDate.monthValue).withDayOfMonth(newDate.dayOfMonth),
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.dateTime.hour,
            initialMinute = uiState.dateTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDateTime(
                        uiState.dateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute),
                    )
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchHeader(mode: SearchMode) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        AnimatedContent(targetState = mode, label = "headline") { target ->
            Text(
                text = when (target) {
                    SearchMode.CONNECTIONS -> "Where to?"
                    SearchMode.DEPARTURES -> "Leaving from?"
                },
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeToggle(mode: SearchMode, onModeChange: (SearchMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = mode == SearchMode.CONNECTIONS,
            onCheckedChange = { if (it) onModeChange(SearchMode.CONNECTIONS) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
        ) {
            Icon(Icons.Default.Route, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Connections")
        }
        ToggleButton(
            checked = mode == SearchMode.DEPARTURES,
            onCheckedChange = { if (it) onModeChange(SearchMode.DEPARTURES) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
        ) {
            Icon(Icons.Default.DepartureBoard, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Departures")
        }
    }
}

/**
 * Transit-style route card: origin/destination fields joined by a dotted "route rail",
 * with a shape-morphing swap button on the divider between them. The fields open the
 * full-screen location picker rather than editing in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteCard(
    uiState: SearchUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TripOrigin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
                LocationField(
                    label = if (uiState.mode == SearchMode.CONNECTIONS) "Start" else "Stop",
                    value = uiState.fromSelected?.name,
                    onClick = onPickFrom,
                    modifier = Modifier.weight(1f),
                )
            }

            AnimatedVisibility(visible = uiState.mode == SearchMode.CONNECTIONS) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        )
                        FilledTonalIconButton(
                            onClick = onSwap,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Swap start and destination")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp),
                        )
                        LocationField(
                            label = "Destination",
                            value = uiState.toSelected?.name,
                            onClick = onPickTo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaxTransfersPicker(value: Int?, onSelect: (Int?) -> Unit) {
    val options = listOf<Int?>(null, 0, 1, 2, 3, 4, 5)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Max transfers", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                ToggleButton(
                    checked = value == option,
                    onCheckedChange = { if (it) onSelect(option) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Text(option?.toString() ?: "Any")
                }
            }
        }
    }
}
