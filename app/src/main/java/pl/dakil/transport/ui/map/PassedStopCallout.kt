package pl.dakil.transport.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position

/**
 * The name of a tapped pass-through stop, held above its dot on the map.
 *
 * Drawn in Compose rather than as a map symbol so it can sit on a bubble of its own: the dot is
 * deliberately small and the basemap's labels crowd it, so bare text beside it is easy to lose.
 * It is placed from the camera in the *layout* phase, which re-runs on every camera frame without
 * recomposing, so the bubble stays on its dot through a pan or zoom. Colours are fixed rather than
 * themed, because what it has to stand out from is the basemap, not the app's surfaces.
 */
@Composable
internal fun PassedStopCallout(
    stop: RoutePassedStop,
    cameraState: CameraState,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.White,
        contentColor = Color(0xFF212121),
        shadowElevation = 3.dp,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                // Read for its invalidation: the projection itself is not observable state.
                cameraState.position
                val anchor = cameraState.projection?.screenLocationFromPosition(
                    Position(latitude = stop.point.lat, longitude = stop.point.lon),
                ) ?: return@layout
                placeable.place(
                    x = anchor.x.roundToPx() - placeable.width / 2,
                    y = anchor.y.roundToPx() - placeable.height - CALLOUT_GAP.roundToPx(),
                )
            }
        },
    ) {
        Text(
            text = stop.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Clearance between the bubble's bottom edge and the centre of the dot it names. */
private val CALLOUT_GAP = 10.dp
