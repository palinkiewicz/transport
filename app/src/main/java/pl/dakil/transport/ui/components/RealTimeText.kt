package pl.dakil.transport.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Shows a departure/arrival time, striking through the scheduled time when it differs from real-time. */
@Composable
fun RealTimeText(
    time: OffsetDateTime,
    scheduledTime: OffsetDateTime,
    realTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val delayed = time != scheduledTime
    Column(modifier = modifier) {
        Text(
            text = time.format(timeFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = if (realTime && delayed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (delayed) {
            Text(
                text = scheduledTime.format(timeFormatter),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
