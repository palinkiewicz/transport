package pl.dakil.transport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.dakil.transport.domain.model.TransportMode

/** Parses a GTFS `RRGGBB` route color, falling back (e.g. to the mode color) when absent/invalid. */
fun parseRouteColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(fallback)
}

/** A colored line badge showing the mode icon and short line name, e.g. a bus/tram badge. */
@Composable
fun ModeChip(
    mode: TransportMode,
    label: String,
    routeColorHex: String? = null,
    modifier: Modifier = Modifier,
) {
    val background = parseRouteColor(routeColorHex, mode.color)
    val onColor = if (background.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.name,
                tint = onColor,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = label,
                color = onColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
