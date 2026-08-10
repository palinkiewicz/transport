package pl.dakil.transport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.dakil.transport.domain.model.TransportMode
import androidx.compose.ui.res.stringResource

/** Parses a GTFS `RRGGBB` route color, falling back (e.g. to the mode color) when absent/invalid. */
fun parseRouteColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(fallback)
}

/**
 * A colored line badge showing the mode icon and short line name, e.g. a bus/tram badge.
 *
 * Passing [onClick] turns the badge into a button. The trailing chevron marks *navigation*, not
 * clickability — a chip that leads somewhere (the line's full timetable) looks different from one
 * that only labels, and a chip that merely toggles something in place passes [showChevron] false.
 */
@Composable
fun ModeChip(
    mode: TransportMode,
    label: String,
    /** Already resolved — see [rememberLineColors]; the badge only decides its own contrast. */
    containerColor: Color = mode.color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Announced for the tap target; ignored when [onClick] is null. */
    clickLabel: String? = null,
    /** Whether a clickable chip draws its trailing chevron; ignored when [onClick] is null. */
    showChevron: Boolean = true,
) {
    val onColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = clickLabel, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = mode.icon,
                contentDescription = stringResource(mode.labelRes),
                tint = onColor,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = label,
                color = onColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            if (onClick != null && showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
