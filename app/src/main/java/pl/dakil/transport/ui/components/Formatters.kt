package pl.dakil.transport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import pl.dakil.transport.R

/** "830 m" below a kilometer, "1.2 km" above. */
@Composable
fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        stringResource(R.string.format_distance_meters, meters.roundToInt())
    } else {
        stringResource(R.string.format_distance_kilometers, (meters / 1000).toFloat())
    }

/** "45 min" under an hour, "2 h" on the hour, "1 h 20 min" otherwise. */
@Composable
fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.format_minutes, minutes)
        minutes == 0 -> stringResource(R.string.format_hours, hours)
        else -> stringResource(R.string.format_hours_minutes, hours, minutes)
    }
}

/**
 * Clock-time formatter built from the locale's own pattern (`format_time_pattern`), so a
 * translation can switch to a 12-hour clock without touching any screen.
 */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val pattern = stringResource(R.string.format_time_pattern)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
}

/** Short date formatter, likewise driven by the locale's `format_date_pattern`. */
@Composable
fun rememberDateFormatter(): DateTimeFormatter {
    val pattern = stringResource(R.string.format_date_pattern)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
}
