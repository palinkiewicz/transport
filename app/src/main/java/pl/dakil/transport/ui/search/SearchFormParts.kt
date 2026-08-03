package pl.dakil.transport.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
internal val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Display-time ISO timestamp for the nav routes that carry the searched-for moment. */
internal fun OffsetDateTime.toRouteArg(): String = format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/** Shaped icon tile + display headline, shared by both search tabs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchHeader(icon: ImageVector, title: String) {
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(text = title, style = MaterialTheme.typography.displaySmall)
    }
}

/**
 * Side-by-side date and time buttons that open their pickers. Dialog visibility is internal
 * state; the caller only sees the committed [onDateTimeChange].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DateTimeRow(
    dateTime: OffsetDateTime,
    onDateTimeChange: (OffsetDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        FilledTonalButton(
            onClick = { showDatePicker = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = "Pick date")
            Spacer(Modifier.width(8.dp))
            Text(dateTime.format(dateFormatter))
        }
        FilledTonalButton(
            onClick = { showTimePicker = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = "Pick time")
            Spacer(Modifier.width(8.dp))
            Text(dateTime.format(timeFormatter))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateTimeChange(
                            dateTime
                                .withYear(newDate.year)
                                .withMonth(newDate.monthValue)
                                .withDayOfMonth(newDate.dayOfMonth),
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
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateTimeChange(
                        dateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute),
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

/** The tall primary "Search" button both forms end with. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
        modifier = modifier
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
