package pl.dakil.transport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.TripStop

/** How often the "where is it now" highlight re-evaluates against the clock. */
private const val NOW_TICK_MILLIS = 15_000L

/**
 * Wall clock that re-emits every [NOW_TICK_MILLIS], so the passed-stop highlight advances
 * along the run without waiting for the screen's own data refresh.
 */
@Composable
fun rememberTickingNow(): OffsetDateTime {
    val now by produceState(OffsetDateTime.now()) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            value = OffsetDateTime.now()
        }
    }
    return now
}

/**
 * The stop list of one vehicle run: a continuous route rail with a dot per stop, terminus stops
 * emphasized, and one row ([highlightedIndex]) marked as where the run is — the stop being
 * approached, for the map's vehicle panel that draws it.
 *
 * [onStopClick] is null where there is nowhere to go, which is the map panel's case: it keeps the
 * map on screen.
 */
fun LazyListScope.tripTimetable(
    stops: List<TripStop>,
    railColor: Color,
    highlightedIndex: Int,
    onStopClick: ((TripStop) -> Unit)? = null,
) {
    items(stops.size) { index ->
        TripStopRow(
            stop = stops[index],
            railColor = railColor,
            isFirst = index == 0,
            isLast = index == stops.lastIndex,
            isCurrent = index == highlightedIndex,
            onClick = onStopClick?.let { click -> { click(stops[index]) } },
        )
    }
}

@Composable
private fun TripStopRow(
    stop: TripStop,
    railColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Continuous route rail with a stop dot; terminus dots are larger.
        Row(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    drawLine(
                        color = railColor,
                        start = Offset(cx, if (isFirst) cy else 0f),
                        end = Offset(cx, if (isLast) cy else size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                    drawCircle(
                        color = railColor,
                        radius = (if (isFirst || isLast) 6.dp else 4.dp).toPx(),
                        center = Offset(cx, cy),
                    )
                },
        ) {}
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InlineRealTimeText(time = stop.time, scheduledTime = stop.scheduledTime)
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFirst || isLast) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
            stop.track?.let { TrackPill(track = it, onHighlightedRow = isCurrent) }
        }
    }
}

@Composable
private fun TrackPill(track: String, onHighlightedRow: Boolean) {
    // The pill shares the highlight's colour, so it swaps to the plain surface to stay legible.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (onHighlightedRow) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = stringResource(R.string.format_track_short, track),
            style = MaterialTheme.typography.labelSmall,
            color = if (onHighlightedRow) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
