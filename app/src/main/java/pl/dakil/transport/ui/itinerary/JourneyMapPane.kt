package pl.dakil.transport.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.InlineRealTimeText
import pl.dakil.transport.ui.components.LineColors
import pl.dakil.transport.ui.components.formatDuration
import pl.dakil.transport.ui.components.rememberTimeFormatter

/** How much of the sheet the places-to-act list may take before it scrolls inside it. */
private val WAYPOINTS_MAX_HEIGHT = 168.dp

/**
 * The itinerary as the map's bottom sheet shows it: the trip at a glance, the lines to ride, and
 * the handful of places the traveller actually has to do something at — with the time they arrive
 * and the time they leave again. Tapping a line focuses that stop on the map; selecting one on the
 * map highlights it here.
 *
 * The star and the share menu ride along in the header. On the map there is no top bar to put them
 * in, and a journey the user is looking at is the same journey whichever view it is in — sending
 * them back to the list to save or export it would be the app's layout leaking into their task.
 *
 * No surface of its own: this is sheet content, so the container and the handle belong to the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JourneyMapPane(
    pinned: PendingMapJourney,
    colors: JourneyColors,
    stops: List<ItineraryStop>,
    /** The stop the map is pointing at, highlighted here too; null for none. */
    selectedStopId: String?,
    onWaypointClick: (String) -> Unit,
    onOpenTrip: (PendingMapTrip) -> Unit,
    /** What this measures — the height the sheet opens to, and what the camera aims around. */
    onHeightChange: (Dp) -> Unit,
    /** Height of the journey's own row — where the sheet rests once it is taken down to its header. */
    onHeaderHeightChange: (Dp) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val journey = pinned.journey
    val timeFormatter = rememberTimeFormatter()
    val waypoints = remember(stops) { waypoints(stops) }
    val export = rememberItineraryExport(
        journey = journey,
        fromName = pinned.fromName,
        toName = pinned.toName,
        legColors = colors.legColors,
        viewModel = viewModel,
    )

    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .onSizeChanged { size -> onHeightChange(with(density) { size.height.toDp() }) }
            .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onSizeChanged { size ->
                onHeaderHeightChange(with(density) { size.height.toDp() })
            },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.format_route_arrow, pinned.fromName, pinned.toName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.itinerary_map_summary,
                        journey.departureTime.format(timeFormatter),
                        journey.arrivalTime.format(timeFormatter),
                        formatDuration(journey.transitDurationSeconds),
                        pluralStringResource(
                            R.plurals.plural_transfers,
                            journey.transfers,
                            journey.transfers,
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Only where there is something to save: a journey opened from the Saved tab arrives
            // without its endpoints, is already pinned, and is unpinned from that tab.
            pinned.endpoints?.let { (from, to) ->
                val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
                FavoriteButton(
                    isFavorite = SavedItinerary.idFor(from, to, journey) in savedIds,
                    onToggle = { viewModel.toggleSaved(from, to, journey) },
                )
            }
            ExportMenuButton(onPick = export.start)
        }

        val transitLegs = remember(journey) { journey.legs.filter { it.isTransit } }
        if (transitLegs.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            ) {
                transitLegs.forEachIndexed { transitIndex, leg ->
                    LegModeChip(
                        leg = leg,
                        containerColor = colors.lineColors.at(transitIndex, leg.mode.color),
                        onOpenTrip = onOpenTrip,
                    )
                }
            }
        }
        if (waypoints.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 12.dp))
            // Scrolls inside the sheet rather than growing it: a long journey must not push the
            // route it belongs to off the screen. Capped, not weighted — the sheet rests at what
            // this measures, so the pane has to have a height of its own to report.
            Column(
                modifier = Modifier
                    .heightIn(max = WAYPOINTS_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, end = 8.dp),
            ) {
                waypoints.forEach { waypoint ->
                    WaypointRow(
                        waypoint = waypoint,
                        lineColors = colors.lineColors,
                        selected = selectedStopId in waypoint.ids,
                        onClick = { onWaypointClick(waypoint.ids.first()) },
                    )
                }
            }
        }
    }

    ItineraryExportDialogs(export)
}

/** One pane line: `20:01 → 20:05  Wilanowska`, with each time keeping its delay treatment. */
@Composable
private fun WaypointRow(
    waypoint: ItineraryWaypoint,
    lineColors: LineColors,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(lineColors.at(waypoint.colorIndex, waypoint.mode.color)),
        )
        waypoint.arrival?.let { arrival ->
            InlineRealTimeText(time = arrival, scheduledTime = waypoint.scheduledArrival ?: arrival)
        }
        if (waypoint.arrival != null && waypoint.departure != null) {
            Text(
                text = "→",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        waypoint.departure?.let { departure ->
            InlineRealTimeText(time = departure, scheduledTime = waypoint.scheduledDeparture ?: departure)
        }
        Text(
            text = waypoint.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
