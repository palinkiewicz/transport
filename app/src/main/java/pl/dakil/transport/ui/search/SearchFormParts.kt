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
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.delay
import pl.dakil.transport.R
import pl.dakil.transport.ui.components.rememberDateFormatter
import pl.dakil.transport.ui.components.rememberTimeFormatter

/** How far [dateTime] may drift from the clock before it counts as a deliberately picked time. */
private const val NOW_TOLERANCE_SECONDS = 60L

/**
 * Whether [dateTime] has drifted away from "now". Re-checked on a timer, not just on
 * recomposition: the search tabs' ViewModels outlive the screen, so a form left open at its
 * seeded time goes stale on its own with nothing to recompose it.
 */
@Composable
internal fun isAwayFromNow(dateTime: OffsetDateTime): Boolean {
    val away by produceState(initialValue = false, key1 = dateTime) {
        while (true) {
            value = abs(Duration.between(OffsetDateTime.now(), dateTime).seconds) > NOW_TOLERANCE_SECONDS
            delay(NOW_TOLERANCE_SECONDS * 1_000 / 2)
        }
    }
    return away
}

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
 *
 * [onResetToNow] adds a reset button; pass null to hide it. The tab's ViewModel outlives the
 * screen, so a time seeded at creation quietly goes stale — this is how the user gets back to
 * "now" without opening both pickers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DateTimeRow(
    dateTime: OffsetDateTime,
    onDateTimeChange: (OffsetDateTime) -> Unit,
    modifier: Modifier = Modifier,
    onResetToNow: (() -> Unit)? = null,
) {
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = { showDatePicker = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.datetime_pick_date))
            Spacer(Modifier.width(8.dp))
            Text(dateTime.format(dateFormatter))
        }
        FilledTonalButton(
            onClick = { showTimePicker = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.datetime_pick_time))
            Spacer(Modifier.width(8.dp))
            Text(dateTime.format(timeFormatter))
        }
        if (onResetToNow != null) {
            FilledTonalIconButton(onClick = onResetToNow, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.datetime_use_now))
            }
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
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
        Text(
            text = stringResource(R.string.action_search),
            style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight),
        )
    }
}
