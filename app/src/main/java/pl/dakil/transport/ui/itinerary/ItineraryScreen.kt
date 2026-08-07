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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import pl.dakil.transport.data.export.GpxLabels
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.GpxDelivery
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.ui.components.EmptyBox
import pl.dakil.transport.ui.components.formatDistance
import pl.dakil.transport.ui.components.formatDuration
import pl.dakil.transport.ui.components.InlineRealTimeText
import pl.dakil.transport.ui.components.LineColorMap
import pl.dakil.transport.ui.components.LineColorRequest
import pl.dakil.transport.ui.components.lineColorKey
import pl.dakil.transport.ui.components.rememberLineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.rememberTimeFormatter
import pl.dakil.transport.ui.map.RouteMap
import pl.dakil.transport.ui.map.RouteMapPoint
import pl.dakil.transport.ui.map.rememberJourneyRouteLines
import pl.dakil.transport.ui.navigation.TripRoute

/** A stop the itinerary names in both views, so a tap in one can point at it in the other. */
private data class ItineraryStop(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val time: OffsetDateTime,
    val scheduledTime: OffsetDateTime,
    val mode: TransportMode,
    val lineLabel: String,
    val routeColor: String?,
    val terminus: Boolean,
    /** True for a boarding stop; false where the journey gets off. */
    val boarding: Boolean,
)

/**
 * One line of the map pane's condensed itinerary: a place the traveller has to act at, with
 * the time they get there and the time they leave again. A same-stop transfer collapses into
 * a single line carrying both; a transfer that walks between two stops stays two lines,
 * because it really is two places.
 */
private data class ItineraryWaypoint(
    /** Map point ids this line stands for — more than one when two stops were merged. */
    val ids: List<String>,
    val name: String,
    val arrival: OffsetDateTime?,
    val scheduledArrival: OffsetDateTime?,
    val departure: OffsetDateTime?,
    val scheduledDeparture: OffsetDateTime?,
    val mode: TransportMode,
    val lineLabel: String,
    val routeColor: String?,
)

/**
 * [stops] — which alternate board, alight, board, alight — folded into the pane's lines.
 * Consecutive alight/board pairs at the same named stop become one line showing both times.
 */
private fun waypoints(stops: List<ItineraryStop>): List<ItineraryWaypoint> {
    fun ItineraryStop.asWaypoint() = ItineraryWaypoint(
        ids = listOf(id),
        name = name,
        arrival = time.takeIf { !boarding },
        scheduledArrival = scheduledTime.takeIf { !boarding },
        departure = time.takeIf { boarding },
        scheduledDeparture = scheduledTime.takeIf { boarding },
        mode = mode,
        lineLabel = lineLabel,
        routeColor = routeColor,
    )

    val result = mutableListOf<ItineraryWaypoint>()
    var index = 0
    while (index < stops.size) {
        val stop = stops[index]
        val next = stops.getOrNull(index + 1)
        if (!stop.boarding && next != null && next.boarding && next.name == stop.name) {
            result += ItineraryWaypoint(
                ids = listOf(stop.id, next.id),
                name = stop.name,
                arrival = stop.time,
                scheduledArrival = stop.scheduledTime,
                departure = next.time,
                scheduledDeparture = next.scheduledTime,
                // The line you leave on is the one that matters at a transfer.
                mode = next.mode,
                lineLabel = next.lineLabel,
                routeColor = next.routeColor,
            )
            index += 2
        } else {
            result += stop.asWaypoint()
            index++
        }
    }
    return result
}

/**
 * The stops where the journey boards and alights, in order. Intermediate stops are left out on
 * purpose: they are pass-throughs, and labelling every one of them buries the map in text.
 * Coordinates come from the leg geometry — [JourneyLeg] carries no endpoint coordinates of its
 * own, and its path ends are exactly those points.
 */
private fun journeyStops(journey: Journey, fromName: String, toName: String): List<ItineraryStop> {
    val transitIndices = journey.legs.indices.filter { journey.legs[it].isTransit }
    return transitIndices.flatMapIndexed { order, index ->
        val leg = journey.legs[index]
        if (leg.path.size < 2) return@flatMapIndexed emptyList()
        listOf(
            ItineraryStop(
                id = stopId(index, from = true),
                name = if (index == 0) fromName else leg.fromName,
                point = leg.path.first(),
                time = leg.startTime,
                scheduledTime = leg.scheduledStartTime,
                mode = leg.mode,
                lineLabel = leg.lineLabel,
                routeColor = leg.routeColor,
                terminus = order == 0,
                boarding = true,
            ),
            ItineraryStop(
                id = stopId(index, from = false),
                name = if (index == journey.legs.lastIndex) toName else leg.toName,
                point = leg.path.last(),
                time = leg.endTime,
                scheduledTime = leg.scheduledEndTime,
                mode = leg.mode,
                lineLabel = leg.lineLabel,
                routeColor = leg.routeColor,
                terminus = order == transitIndices.lastIndex,
                boarding = false,
            ),
        )
    }
}

/** Shared between the list rows and the map features so a tap in either resolves in the other. */
private fun stopId(legIndex: Int, from: Boolean): String = "leg-$legIndex-${if (from) "from" else "to"}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItineraryScreen(
    journey: Journey?,
    fromName: String,
    toName: String,
    onBack: () -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    var showMap by rememberSaveable { mutableStateOf(false) }
    var selectedStopId by rememberSaveable { mutableStateOf<String?>(null) }
    val showStopNames by viewModel.showStopNames.collectAsStateWithLifecycle()
    // Only offer the map when the journey actually carries drawable leg geometry.
    val canShowMap = journey?.legs?.any { it.path.size >= 2 } == true
    if (!canShowMap && showMap) showMap = false

    val stops = remember(journey, fromName, toName) {
        journey?.let { journeyStops(it, fromName, toName) }.orEmpty()
    }

    // One journey is one sequence, so its transit legs are each other's neighbours. The list and
    // the map pane share the result; the route overlay itself keeps the feed's colours.
    val lineColors = rememberLineColors(
        journey?.legs.orEmpty().filter { it.isTransit }.map { leg ->
            LineColorRequest(
                key = lineColorKey(leg.mode, leg.lineLabel),
                serverHex = leg.routeColor,
                fallback = leg.mode.color,
            )
        },
    )

    val export = rememberItineraryExport(journey, fromName, toName, viewModel)

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
                    if (journey != null) {
                        IconButton(onClick = export.start) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.gpx_export))
                        }
                    }
                    if (canShowMap) {
                        IconButton(onClick = { showMap = !showMap }) {
                            if (showMap) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.itinerary_show_as_list),
                                )
                            } else {
                                Icon(Icons.Default.Map, contentDescription = stringResource(R.string.itinerary_show_on_map))
                            }
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
            showMap -> ItineraryMap(
                journey = journey,
                fromName = fromName,
                toName = toName,
                stops = stops,
                lineColors = lineColors,
                selectedStopId = selectedStopId,
                showStopNames = showStopNames,
                onStopClick = { selectedStopId = it },
                onOpenTrip = onOpenTrip,
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
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
                        lineColors = lineColors,
                        onOpenTrip = onOpenTrip,
                        onStopClick = if (!canShowMap) {
                            null
                        } else {
                            { id: String ->
                                if (stops.any { it.id == id }) {
                                    selectedStopId = id
                                    showMap = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    ItineraryExportDialogs(export)
}

/** What the top bar's export button does, and the state its dialogs read. */
private class ItineraryExport(
    val start: () -> Unit,
    val share: () -> Unit,
    val save: () -> Unit,
    /** True while the "share or save?" sheet is up — only ever with [GpxDelivery.ASK]. */
    val choosing: Boolean,
    val onDismissChoice: () -> Unit,
    val failed: Boolean,
    val onDismissFailure: () -> Unit,
)

private const val GPX_MIME_TYPE = "application/gpx+xml"

/**
 * Wires the export button to the settings the user picked: straight to the share sheet, straight
 * to the document picker, or a sheet asking which. Everything user-visible in the file itself is
 * resolved here and handed to the writer as [GpxLabels] — the writer sits below the Compose layer
 * and has no resources of its own.
 */
@Composable
private fun rememberItineraryExport(
    journey: Journey?,
    fromName: String,
    toName: String,
    viewModel: ItineraryViewModel,
): ItineraryExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.gpxExport.collectAsStateWithLifecycle()
    val labels = rememberGpxLabels(fromName, toName)
    val subject = stringResource(R.string.gpx_export_subject, fromName, toName)
    val chooserTitle = stringResource(R.string.gpx_export)

    var choosing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(GPX_MIME_TYPE),
    ) { uri ->
        // A null uri is the user backing out of the picker, not a failure.
        if (uri != null && journey != null) {
            scope.launch { failed = viewModel.exportTo(uri, journey, labels).isFailure }
        }
    }

    val share = {
        choosing = false
        if (journey != null) {
            scope.launch {
                val fileName = viewModel.fileNameFor(journey, fromName, toName)
                viewModel.exportToCache(journey, fileName, labels)
                    .onSuccess { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = GPX_MIME_TYPE
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(send, chooserTitle)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }
                    .onFailure { failed = true }
            }
            Unit
        }
    }

    val save = {
        choosing = false
        if (journey != null) saveLauncher.launch(viewModel.fileNameFor(journey, fromName, toName))
    }

    return ItineraryExport(
        start = {
            when (settings.delivery) {
                GpxDelivery.SHARE -> share()
                GpxDelivery.SAVE -> save()
                GpxDelivery.ASK -> choosing = true
            }
        },
        share = share,
        save = save,
        choosing = choosing,
        onDismissChoice = { choosing = false },
        failed = failed,
        onDismissFailure = { failed = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItineraryExportDialogs(export: ItineraryExport) {
    if (export.choosing) {
        ModalBottomSheet(onDismissRequest = export.onDismissChoice) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.gpx_export_choose),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ExportChoiceRow(Icons.Default.Share, stringResource(R.string.gpx_export_share), export.share)
                ExportChoiceRow(Icons.Default.Save, stringResource(R.string.gpx_export_save), export.save)
            }
        }
    }
    if (export.failed) {
        AlertDialog(
            onDismissRequest = export.onDismissFailure,
            title = { Text(stringResource(R.string.gpx_export_failed_title)) },
            text = { Text(stringResource(R.string.gpx_export_failed_body)) },
            confirmButton = {
                TextButton(onClick = export.onDismissFailure) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun ExportChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The app-authored text that ends up inside the exported file. Mode names come from the same
 * `labelRes` the rest of the UI reads. The two parameterized labels are read here as their raw
 * patterns and filled in later, because the writer calls them from a plain lambda that cannot be
 * composable — and resolving them off `LocalContext` instead would skip the resource system's
 * configuration handling.
 */
@Composable
private fun rememberGpxLabels(fromName: String, toName: String): GpxLabels {
    val appName = stringResource(R.string.app_name)
    val documentName = stringResource(R.string.format_route_arrow, fromName, toName)
    val modeNames = TransportMode.entries.associateWith { stringResource(it.labelRes) }
    val board = stringResource(R.string.gpx_waypoint_board)
    val transfer = stringResource(R.string.gpx_waypoint_transfer)
    val alight = stringResource(R.string.gpx_waypoint_alight)
    val separator = stringResource(R.string.gpx_desc_separator)
    val trackPattern = stringResource(R.string.format_track_short)
    val towardsPattern = stringResource(R.string.format_towards)
    return remember(documentName, modeNames, board, transfer, alight, separator) {
        GpxLabels(
            documentName = documentName,
            originName = fromName,
            destinationName = toName,
            creator = "$appName ${BuildConfig.VERSION_NAME}",
            accessLegName = { leg -> modeNames[leg.mode] ?: leg.mode.name },
            board = board,
            transfer = transfer,
            alight = alight,
            descSeparator = separator,
            track = { track -> String.format(Locale.getDefault(), trackPattern, track) },
            towards = { headsign -> String.format(Locale.getDefault(), towardsPattern, headsign) },
        )
    }
}

/**
 * The journey drawn on the app's base map (leg colors matching the list view), with a docked
 * bottom pane carrying the at-a-glance summary and the sequence of lines to ride.
 */
@Composable
private fun ItineraryMap(
    journey: Journey,
    fromName: String,
    toName: String,
    stops: List<ItineraryStop>,
    lineColors: LineColorMap,
    selectedStopId: String?,
    showStopNames: Boolean,
    onStopClick: (String?) -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = rememberJourneyRouteLines(journey)
    val points = remember(stops) {
        stops.map {
            RouteMapPoint(
                id = it.id,
                point = it.point,
                name = it.name,
                mode = it.mode,
                routeColor = it.routeColor,
                terminus = it.terminus,
            )
        }
    }
    RouteMap(
        lines = lines,
        points = points,
        selectedPointId = selectedStopId,
        showPointLabels = showStopNames,
        onPointClick = onStopClick,
        modifier = modifier.fillMaxSize(),
    ) {
        ItineraryMapPane(
            journey = journey,
            fromName = fromName,
            toName = toName,
            stops = stops,
            lineColors = lineColors,
            selectedStopId = selectedStopId,
            onWaypointClick = onStopClick,
            onOpenTrip = onOpenTrip,
        )
    }
}

/**
 * The map's docked pane: the trip at a glance, the lines to ride, and the handful of places
 * the traveller actually has to do something at — with the time they arrive and the time they
 * leave again. Tapping a line focuses that stop on the map; selecting one on the map
 * highlights it here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItineraryMapPane(
    journey: Journey,
    fromName: String,
    toName: String,
    stops: List<ItineraryStop>,
    lineColors: LineColorMap,
    selectedStopId: String?,
    onWaypointClick: (String) -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
) {
    val timeFormatter = rememberTimeFormatter()
    val waypoints = remember(stops) { waypoints(stops) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.format_route_arrow, fromName, toName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
            val transitLegs = remember(journey) { journey.legs.filter { it.isTransit } }
            if (transitLegs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    transitLegs.forEach { leg ->
                        LegModeChip(leg, lineColors, onOpenTrip)
                    }
                }
            }
            if (waypoints.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                // Scrolls rather than growing: a long journey must not push the map off screen.
                Column(
                    modifier = Modifier
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    waypoints.forEach { waypoint ->
                        WaypointRow(
                            waypoint = waypoint,
                            lineColors = lineColors,
                            selected = selectedStopId in waypoint.ids,
                            onClick = { onWaypointClick(waypoint.ids.first()) },
                        )
                    }
                }
            }
        }
    }
}

/** One pane line: `20:01 → 20:05  Wilanowska`, with each time keeping its delay treatment. */
@Composable
private fun WaypointRow(
    waypoint: ItineraryWaypoint,
    lineColors: LineColorMap,
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
                .background(
                    lineColors.of(
                        lineColorKey(waypoint.mode, waypoint.lineLabel),
                        waypoint.mode.color,
                    ),
                ),
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
    lineColors: LineColorMap,
    fromNameOverride: String? = null,
    toNameOverride: String? = null,
    onOpenTrip: (TripRoute) -> Unit,
    /** Null leaves the stop rows inert (nothing to show on a map). */
    onStopClick: ((String) -> Unit)? = null,
) {
    val legColor = if (leg.isTransit) {
        lineColors.of(lineColorKey(leg.mode, leg.lineLabel), leg.mode.color)
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
                LegModeChip(leg, lineColors, onOpenTrip)
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

/**
 * The leg's line badge. Transit legs carry a trip id, so the badge doubles as the way into the
 * line's full timetable; legs the feed gives no trip id for stay inert rather than looking
 * tappable and doing nothing.
 */
@Composable
private fun LegModeChip(leg: JourneyLeg, lineColors: LineColorMap, onOpenTrip: (TripRoute) -> Unit) {
    ModeChip(
        mode = leg.mode,
        label = leg.lineLabel,
        containerColor = lineColors.of(lineColorKey(leg.mode, leg.lineLabel), leg.mode.color),
        clickLabel = stringResource(R.string.itinerary_open_trip),
        onClick = leg.tripId?.let { tripId ->
            {
                onOpenTrip(
                    TripRoute(
                        tripId = tripId,
                        lineLabel = leg.lineLabel,
                        headsign = leg.headsign,
                        modeName = leg.mode.name,
                        routeColor = leg.routeColor,
                    ),
                )
            }
        },
    )
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
