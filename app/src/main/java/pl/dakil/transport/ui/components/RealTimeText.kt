package pl.dakil.transport.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Color signalling how a real-time timestamp deviates from schedule:
 * red when late, green when early, null when on time.
 */
@Composable
fun timeDeviationColor(time: OffsetDateTime, scheduledTime: OffsetDateTime): Color? = when {
    time.isAfter(scheduledTime) -> MaterialTheme.colorScheme.error
    time.isBefore(scheduledTime) -> if (isSystemInDarkTheme()) Color(0xFF86D993) else Color(0xFF1E7B34)
    else -> null
}

/** Shows a departure/arrival time, striking through the scheduled time when it differs from real-time. */
@Composable
fun RealTimeText(
    time: OffsetDateTime,
    scheduledTime: OffsetDateTime,
    realTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val deviationColor = if (realTime) timeDeviationColor(time, scheduledTime) else null
    Column(modifier = modifier) {
        Text(
            text = time.format(timeFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = deviationColor ?: MaterialTheme.colorScheme.onSurface,
        )
        if (time != scheduledTime) {
            Text(
                text = scheduledTime.format(timeFormatter),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontal variant of [RealTimeText]: bold deviation-colored actual time with the
 * struck-through scheduled time beside it. For timeline rows (itinerary, trip route).
 */
@Composable
fun InlineRealTimeText(
    time: OffsetDateTime,
    scheduledTime: OffsetDateTime,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = time.format(timeFormatter),
            style = style,
            fontWeight = FontWeight.Bold,
            color = timeDeviationColor(time, scheduledTime) ?: MaterialTheme.colorScheme.onSurface,
        )
        if (time != scheduledTime) {
            Text(
                text = scheduledTime.format(timeFormatter),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
