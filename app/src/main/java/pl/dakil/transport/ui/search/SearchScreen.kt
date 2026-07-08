package pl.dakil.transport.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import pl.dakil.transport.ui.navigation.DeparturesRoute
import pl.dakil.transport.ui.navigation.ResultsRoute

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSearchConnections: (ResultsRoute) -> Unit,
    onSearchDepartures: (DeparturesRoute) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var maxTransfersExpanded by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.mode == SearchMode.CONNECTIONS,
                    onClick = { viewModel.setMode(SearchMode.CONNECTIONS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Connections") }
                SegmentedButton(
                    selected = uiState.mode == SearchMode.DEPARTURES,
                    onClick = { viewModel.setMode(SearchMode.DEPARTURES) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Departures") }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LocationField(
                    label = "Start",
                    query = uiState.fromQuery,
                    suggestions = uiState.fromSuggestions,
                    onQueryChange = viewModel::onFromQueryChange,
                    onSelect = viewModel::selectFrom,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.mode == SearchMode.CONNECTIONS) {
                    IconButton(onClick = viewModel::swapFromTo) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Swap start and destination")
                    }
                }
            }

            if (uiState.mode == SearchMode.CONNECTIONS) {
                LocationField(
                    label = "Destination",
                    query = uiState.toQuery,
                    suggestions = uiState.toSuggestions,
                    onQueryChange = viewModel::onToQueryChange,
                    onSelect = viewModel::selectTo,
                    modifier = Modifier.fillMaxWidth(),
                )

                MaxTransfersField(
                    value = uiState.maxTransfers,
                    expanded = maxTransfersExpanded,
                    onExpandedChange = { maxTransfersExpanded = it },
                    onSelect = viewModel::setMaxTransfers,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.dateTime.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick date")
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.dateTime.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                        }
                    },
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
                            ),
                        )
                    }
                },
                enabled = uiState.canSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxTransfersField(
    value: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf<Int?>(null, 0, 1, 2, 3, 4, 5)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value?.toString() ?: "Any",
            onValueChange = {},
            readOnly = true,
            label = { Text("Max transfers") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option?.toString() ?: "Any") },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
