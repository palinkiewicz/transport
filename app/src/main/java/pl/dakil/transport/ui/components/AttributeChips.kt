package pl.dakil.transport.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Small icon+label pill for one vehicle attribute (live status, wheelchair, bikes). */
@Composable
fun AttributeChip(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/**
 * Positive-only vehicle amenity chips: a chip is shown only when the feed confirms the amenity
 * exists ("Wheelchair access", "Bikes allowed"); `false` and unknown both render nothing.
 * Emits zero or more chips, so call it from inside a Row/FlowRow.
 */
@Composable
fun VehicleAmenityChips(wheelchairAccessible: Boolean?, bikesAllowed: Boolean?) {
    if (wheelchairAccessible == true) {
        AttributeChip(Icons.Default.Accessible, "Wheelchair access")
    }
    if (bikesAllowed == true) {
        AttributeChip(Icons.Default.PedalBike, "Bikes allowed")
    }
}
