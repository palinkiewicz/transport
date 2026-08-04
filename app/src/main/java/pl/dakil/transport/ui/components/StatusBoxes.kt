package pl.dakil.transport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.transport.domain.model.AppError

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

/** Icon, headline and explanation shown when a load failed or came back empty. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusBox(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    details: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = containerColor,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (onAction != null) {
            Button(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = actionLabel ?: "Try again", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (details != null) {
            var expanded by remember(details) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (expanded) "Hide technical details" else "Technical details")
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Full-screen, human-readable rendering of a failed load. */
@Composable
fun ErrorBox(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val copy = error.presentation()
    StatusBox(
        icon = copy.icon,
        title = copy.title,
        description = copy.description,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onAction = onRetry?.takeIf { error.isRetryable },
        details = error.detail,
    )
}

/** Nothing went wrong — there is simply nothing to show. */
@Composable
fun EmptyBox(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.SearchOff,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StatusBox(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/** Compact failure banner for screens whose content can't be replaced wholesale (lists, panels). */
@Composable
fun InlineErrorRow(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                error.presentation().icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = error.shortMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null && error.isRetryable) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** Icon + wording each error case is shown with. */
data class ErrorCopy(val icon: ImageVector, val title: String, val description: String)

/** One-line phrasing for inline spots (map panels) that have no room for a full screen. */
val AppError.shortMessage: String
    get() = when (this) {
        is AppError.NoConnection -> "No connection"
        is AppError.Timeout -> "Took too long to respond"
        is AppError.NotFound -> "Not available any more"
        is AppError.RateLimited -> "Too many requests — try again shortly"
        is AppError.BadRequest -> "This request could not be handled"
        is AppError.ServerError -> "The transit service is having trouble"
        is AppError.MalformedResponse -> "Unexpected response from the service"
        is AppError.Unknown -> "Something went wrong"
    }

fun AppError.presentation(): ErrorCopy = when (this) {
    is AppError.NoConnection -> ErrorCopy(
        icon = Icons.Default.SignalWifiOff,
        title = "You're offline",
        description = "We couldn't reach the transit service. Check your internet connection " +
            "and try again.",
    )
    is AppError.Timeout -> ErrorCopy(
        icon = Icons.Default.HourglassEmpty,
        title = "This is taking too long",
        description = "The transit service didn't answer in time. It may be busy — trying " +
            "again usually helps.",
    )
    is AppError.NotFound -> ErrorCopy(
        icon = Icons.Default.SearchOff,
        title = "Not available",
        description = "This stop or trip isn't in the timetable any more. It may have been " +
            "cancelled or removed from the schedule.",
    )
    is AppError.RateLimited -> ErrorCopy(
        icon = Icons.Default.Speed,
        title = "Slow down a moment",
        description = "The transit service is limiting how often it can be asked. Wait a few " +
            "seconds, then try again.",
    )
    is AppError.BadRequest -> ErrorCopy(
        icon = Icons.Default.ReportProblem,
        title = "This search couldn't be run",
        description = "The transit service rejected it (error $code). Try changing your stops, " +
            "time, or advanced search options.",
    )
    is AppError.ServerError -> ErrorCopy(
        icon = Icons.Default.CloudOff,
        title = "The service is having trouble",
        description = "The transit service reported an error ($code). It's not something on " +
            "your side — please try again in a moment.",
    )
    is AppError.MalformedResponse -> ErrorCopy(
        icon = Icons.Default.Warning,
        title = "Unexpected response",
        description = "The transit service sent back something this app couldn't read. Trying " +
            "again may help.",
    )
    is AppError.Unknown -> ErrorCopy(
        icon = Icons.Default.SentimentDissatisfied,
        title = "Something went wrong",
        description = "The data couldn't be loaded. Please try again.",
    )
}
