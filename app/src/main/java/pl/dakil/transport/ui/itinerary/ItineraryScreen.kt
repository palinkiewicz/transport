package pl.dakil.transport.ui.itinerary

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.coroutines.launch
import pl.dakil.transport.BuildConfig
import pl.dakil.transport.R
import pl.dakil.transport.data.export.ExportLabels
import pl.dakil.transport.domain.model.ExportDelivery
import pl.dakil.transport.domain.model.ExportFormat
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.EmptyBox
import pl.dakil.transport.ui.components.formatDistance
import pl.dakil.transport.ui.components.formatDuration
import pl.dakil.transport.ui.components.InlineRealTimeText
import pl.dakil.transport.ui.components.LineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.rememberTimeFormatter
import pl.dakil.transport.ui.map.hasDrawableGeometry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItineraryScreen(
    journey: Journey?,
    fromName: String,
    toName: String,
    onBack: () -> Unit,
    onOpenTrip: (PendingMapTrip) -> Unit,
    /**
     * Shows this journey on the Map screen, optionally opened focused on one of its stops. The map
     * is a pushed destination rather than a mode of this screen: one map implementation draws every
     * route the app shows, and back from it lands here.
     */
    onShowOnMap: (PendingMapJourney) -> Unit,
    /**
     * The endpoints this journey was planned between. Given, the screen offers a star that pins
     * the whole journey for offline use; the saved-itinerary screen passes null because a
     * journey opened from the Saved tab is already pinned.
     */
    endpoints: Pair<TransitLocation, TransitLocation>? = null,
    /** Shown above the itinerary when its times might not be the API's current ones. */
    savedNote: String? = null,
    /** Offered beside [savedNote] where checking again could actually change the answer. */
    onRetryRefresh: (() -> Unit)? = null,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    // Only offer the map when the journey actually carries drawable leg geometry.
    val canShowMap = journey?.hasDrawableGeometry() == true
    val stops = remember(journey, fromName, toName) {
        journey?.let { journeyStops(it, fromName, toName) }.orEmpty()
    }
    val colors = rememberJourneyColors(journey)
    val export = rememberItineraryExport(journey, fromName, toName, colors.legColors, viewModel)

    /** Opens the map on this journey, focused on [stopId] where a stop's row asked for it. */
    fun showOnMap(stopId: String? = null) {
        if (journey == null) return
        onShowOnMap(
            PendingMapJourney(
                journey = journey,
                fromName = fromName,
                toName = toName,
                endpoints = endpoints,
                selectedStopId = stopId,
            ),
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.itinerary_title)) },
                subtitle = { Text(stringResource(R.string.format_route_arrow, fromName, toName)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (journey != null && endpoints != null) {
                        val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
                        val (from, to) = endpoints
                        FavoriteButton(
                            isFavorite = SavedItinerary.idFor(from, to, journey) in savedIds,
                            onToggle = { viewModel.toggleSaved(from, to, journey) },
                        )
                    }
                    if (journey != null) {
                        ExportMenuButton(onPick = export.start)
                    }
                    if (canShowMap) {
                        IconButton(onClick = { showOnMap() }) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.itinerary_show_on_map))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            journey == null -> EmptyBox(
                title = stringResource(R.string.itinerary_unavailable_title),
                description = stringResource(R.string.itinerary_unavailable_body),
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                if (savedNote != null) {
                    item(key = "saved-note") {
                        SavedCopyNote(savedNote, onRetryRefresh)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                item(key = "summary") {
                    JourneySummary(journey)
                    Spacer(Modifier.height(20.dp))
                }
                items(journey.legs.size) { index ->
                    LegRow(
                        leg = journey.legs[index],
                        legIndex = index,
                        fromNameOverride = if (index == 0) fromName else null,
                        toNameOverride = if (index == journey.legs.lastIndex) toName else null,
                        // Tapping a stop in the list is a request to see where it is; only
                        // stops the map can actually point at are tappable.
                        lineColors = colors.lineColors,
                        colorIndex = colors.transitOrder[index],
                        onOpenTrip = onOpenTrip,
                        onStopClick = if (!canShowMap) {
                            null
                        } else {
                            { id: String -> if (stops.any { it.id == id }) showOnMap(id) }
                        },
                    )
                }
            }
        }
    }

    ItineraryExportDialogs(export)
}

/**
 * Says why the times below might not be the API's current ones.
 *
 * Deliberately an informational tile and not an error: a saved journey is meant to be openable
 * offline, so falling back to its stored times is the feature working, not something going
 * wrong. It is shown only when there is something to say — a run that was just checked gets no
 * tile at all.
 */
@Composable
private fun SavedCopyNote(note: String, onRetry: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun JourneySummary(journey: Journey) {
    val timeFormatter = rememberTimeFormatter()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryStat(
                stringResource(R.string.itinerary_stat_depart),
                journey.departureTime.format(timeFormatter),
            )
            SummaryStat(
                stringResource(R.string.itinerary_stat_duration),
                formatDuration(journey.transitDurationSeconds),
            )
            SummaryStat(stringResource(R.string.itinerary_stat_transfers), journey.transfers.toString())
            SummaryStat(
                stringResource(R.string.itinerary_stat_arrive),
                journey.arrivalTime.format(timeFormatter),
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegRow(
    leg: JourneyLeg,
    legIndex: Int,
    lineColors: LineColors,
    /** This leg's slot in the palette order; null for a leg that takes none (a walk). */
    colorIndex: Int?,
    fromNameOverride: String? = null,
    toNameOverride: String? = null,
    onOpenTrip: (PendingMapTrip) -> Unit,
    /** Null leaves the stop rows inert (nothing to show on a map). */
    onStopClick: ((String) -> Unit)? = null,
) {
    val legColor = if (leg.isTransit && colorIndex != null) {
        lineColors.at(colorIndex, leg.mode.color)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Rail: colored dot + connector line (dashed for walk legs).
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(legColor),
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .drawBehind {
                        drawLine(
                            color = legColor,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = size.width,
                            cap = StrokeCap.Round,
                            pathEffect = if (leg.isTransit) {
                                null
                            } else {
                                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                            },
                        )
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            if (leg.isTransit || fromNameOverride != null) {
                StopRow(
                    time = leg.startTime,
                    scheduledTime = leg.scheduledStartTime,
                    name = fromNameOverride ?: leg.fromName,
                    track = leg.fromTrack,
                    onClick = onStopClick?.let { { it(stopId(legIndex, from = true)) } },
                )
                Spacer(Modifier.height(4.dp))
            }
            if (leg.isTransit) {
                LegModeChip(leg, lineColors.at(colorIndex ?: 0, leg.mode.color), onOpenTrip)
                leg.headsign?.let {
                    Text(
                        text = stringResource(R.string.format_towards, it),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (leg.wheelchairAccessible == true || leg.bikesAllowed == true) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        VehicleAmenityChips(
                            wheelchairAccessible = leg.wheelchairAccessible,
                            bikesAllowed = leg.bikesAllowed,
                        )
                    }
                }
                IntermediateStopsSection(leg, legColor)
            } else {
                Text(
                    text = run {
                        val mode = stringResource(leg.mode.labelRes)
                        val duration = stringResource(R.string.format_minutes, leg.duration / 60)
                        val distance = leg.distanceMeters?.let { formatDistance(it) }
                        if (distance == null) {
                            stringResource(R.string.itinerary_access_leg, mode, duration)
                        } else {
                            stringResource(R.string.itinerary_access_leg_distance, mode, duration, distance)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (leg.isTransit || toNameOverride != null) {
                Spacer(Modifier.height(8.dp))
                StopRow(
                    time = leg.endTime,
                    scheduledTime = leg.scheduledEndTime,
                    name = toNameOverride ?: leg.toName,
                    track = leg.toTrack,
                    onClick = onStopClick?.let { { it(stopId(legIndex, from = false)) } },
                )
            }
        }
    }
}

@Composable
private fun StopRow(
    time: OffsetDateTime,
    scheduledTime: OffsetDateTime,
    name: String,
    track: String?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp)
        },
    ) {
        InlineRealTimeText(time = time, scheduledTime = scheduledTime)
        Text(name, style = MaterialTheme.typography.bodyMedium)
        track?.let { TrackPill(it) }
    }
}

@Composable
private fun TrackPill(track: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.format_track_short, track),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "n stops · m min" row that expands into the list of intermediate stops with arrival times. */
@Composable
private fun IntermediateStopsSection(leg: JourneyLeg, legColor: androidx.compose.ui.graphics.Color) {
    val timeFormatter = rememberTimeFormatter()
    val rideLabel = pluralStringResource(
        R.plurals.itinerary_ride_duration,
        leg.duration / 60,
        leg.duration / 60,
    )
    if (leg.intermediateStops.isEmpty()) {
        Text(
            text = rideLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded }
            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = stringResource(
                if (expanded) {
                    R.string.itinerary_hide_intermediate_stops
                } else {
                    R.string.itinerary_show_intermediate_stops
                },
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(
                R.string.itinerary_stops_and_ride,
                pluralStringResource(
                    R.plurals.plural_stops,
                    leg.intermediateStops.size,
                    leg.intermediateStops.size,
                ),
                rideLabel,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            leg.intermediateStops.forEach { stop ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(legColor),
                    )
                    stop.arrivalTime?.let { arrival ->
                        val scheduled = stop.scheduledArrivalTime
                        if (scheduled != null) {
                            InlineRealTimeText(
                                time = arrival,
                                scheduledTime = scheduled,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            Text(
                                text = arrival.format(timeFormatter),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stop.name, style = MaterialTheme.typography.bodySmall)
                    stop.track?.let { TrackPill(it) }
                }
            }
        }
    }
}
