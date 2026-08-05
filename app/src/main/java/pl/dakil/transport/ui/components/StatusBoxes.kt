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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.transport.R
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
                Text(
                    text = actionLabel ?: stringResource(R.string.action_try_again),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (details != null) {
            var expanded by remember(details) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    stringResource(
                        if (expanded) R.string.error_details_hide else R.string.error_details_show,
                    ),
                )
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
                error.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = error.shortMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null && error.isRetryable) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

/** Icon + wording each error case is shown with. */
data class ErrorCopy(val icon: ImageVector, val title: String, val description: String)

/** One-line phrasing for inline spots (map panels) that have no room for a full screen. */
val AppError.shortMessage: String
    @Composable get() = stringResource(
        when (this) {
            is AppError.NoConnection -> R.string.error_short_no_connection
            is AppError.Timeout -> R.string.error_short_timeout
            is AppError.NotFound -> R.string.error_short_not_found
            is AppError.RateLimited -> R.string.error_short_rate_limited
            is AppError.BadRequest -> R.string.error_short_bad_request
            is AppError.ServerError -> R.string.error_short_server
            is AppError.MalformedResponse -> R.string.error_short_malformed
            is AppError.Unknown -> R.string.error_short_unknown
        },
    )

/** The icon each error case is shown with — the only part of [presentation] that isn't text. */
val AppError.icon: ImageVector
    get() = when (this) {
        is AppError.NoConnection -> Icons.Default.SignalWifiOff
        is AppError.Timeout -> Icons.Default.HourglassEmpty
        is AppError.NotFound -> Icons.Default.SearchOff
        is AppError.RateLimited -> Icons.Default.Speed
        is AppError.BadRequest -> Icons.Default.ReportProblem
        is AppError.ServerError -> Icons.Default.CloudOff
        is AppError.MalformedResponse -> Icons.Default.Warning
        is AppError.Unknown -> Icons.Default.SentimentDissatisfied
    }

@Composable
fun AppError.presentation(): ErrorCopy = when (this) {
    is AppError.NoConnection -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_no_connection),
        description = stringResource(R.string.error_body_no_connection),
    )
    is AppError.Timeout -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_timeout),
        description = stringResource(R.string.error_body_timeout),
    )
    is AppError.NotFound -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_not_found),
        description = stringResource(R.string.error_body_not_found),
    )
    is AppError.RateLimited -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_rate_limited),
        description = stringResource(R.string.error_body_rate_limited),
    )
    is AppError.BadRequest -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_bad_request),
        description = stringResource(R.string.error_body_bad_request, code),
    )
    is AppError.ServerError -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_server),
        description = stringResource(R.string.error_body_server, code),
    )
    is AppError.MalformedResponse -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_malformed),
        description = stringResource(R.string.error_body_malformed),
    )
    is AppError.Unknown -> ErrorCopy(
        icon = icon,
        title = stringResource(R.string.error_title_unknown),
        description = stringResource(R.string.error_body_unknown),
    )
}
