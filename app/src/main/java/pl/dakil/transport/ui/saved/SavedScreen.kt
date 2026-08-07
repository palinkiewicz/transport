package pl.dakil.transport.ui.saved

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.LocationListItem
import pl.dakil.transport.ui.components.LineColorRequest
import pl.dakil.transport.ui.components.rememberLineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.rememberDateFormatter
import pl.dakil.transport.ui.components.rememberTimeFormatter
import pl.dakil.transport.ui.navigation.ResultsRoute
import pl.dakil.transport.ui.navigation.SavedItineraryRoute
import pl.dakil.transport.ui.navigation.TripRoute

/**
 * The Saved tab: everything the user has starred — places, connections (start→end pairs,
 * searched for "now" on tap), lines (opening their trip timetable) and whole journeys (opening
 * their stored copy, which works with no connection).
 *
 * There is no "keep offline" switch anywhere in here on purpose: everything the app has fetched
 * is cached already, and starring is only how the user marks which of it matters.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SavedScreen(
    onOpenConnectionsSearch: () -> Unit,
    onOpenConnection: (ResultsRoute) -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
    onOpenItinerary: (SavedItineraryRoute) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val itineraries by viewModel.itineraries.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Bottom inset intentionally excluded: the app-level bottom navigation bar shown for
        // this route already clears the navigation bar inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.saved_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val isEmpty = favorites.locations.isEmpty() &&
            favorites.connections.isEmpty() &&
            favorites.lines.isEmpty() &&
            itineraries.isEmpty()
        if (isEmpty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
            ) {
                Icon(
                    Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(R.string.saved_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.saved_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val lineColors = rememberLineColors(
                favorites.lines.map { line -> LineColorRequest(line.routeColor, line.mode.color) },
            )
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                if (favorites.locations.isNotEmpty()) {
                    sectionHeader("places-header", R.string.saved_section_places)
                    items(
                        count = favorites.locations.size,
                        key = { "loc:${favorites.locations[it].favoriteKey}" },
                    ) { index ->
                        val location = favorites.locations[index]
                        LocationListItem(
                            location = location,
                            onClick = {
                                viewModel.setSearchDestination(location)
                                onOpenConnectionsSearch()
                            },
                            trailingContent = {
                                FavoriteButton(
                                    isFavorite = true,
                                    onToggle = { viewModel.removeLocation(location) },
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (favorites.connections.isNotEmpty()) {
                    sectionHeader("connections-header", R.string.saved_section_connections)
                    items(
                        count = favorites.connections.size,
                        key = { "conn:${favorites.connections[it].key}" },
                    ) { index ->
                        val connection = favorites.connections[index]
                        ConnectionListItem(
                            connection = connection,
                            onClick = { onOpenConnection(connection.toResultsRoute()) },
                            onRemove = { viewModel.removeConnection(connection) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (favorites.lines.isNotEmpty()) {
                    sectionHeader("lines-header", R.string.saved_section_lines)
                    items(
                        count = favorites.lines.size,
                        key = { "line:${favorites.lines[it].key}" },
                    ) { index ->
                        val line = favorites.lines[index]
                        LineListItem(
                            line = line,
                            containerColor = lineColors.at(index, line.mode.color),
                            onClick = { onOpenTrip(line.toTripRoute()) },
                            onRemove = { viewModel.removeLine(line) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (itineraries.isNotEmpty()) {
                    sectionHeader("trips-header", R.string.saved_section_trips)
                    items(
                        count = itineraries.size,
                        key = { "trip:${itineraries[it].id}" },
                    ) { index ->
                        val itinerary = itineraries[index]
                        SavedItineraryListItem(
                            itinerary = itinerary,
                            onClick = { onOpenItinerary(SavedItineraryRoute(itinerary.id)) },
                            onRemove = { viewModel.removeItinerary(itinerary.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A pinned journey. The subtitle leads with when it leaves, because that — not the endpoints,
 * which the title already gives — is what distinguishes two saved runs of the same trip.
 */
@Composable
private fun SavedItineraryListItem(
    itinerary: SavedItinerary,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = rememberTimeFormatter()
    val dateFormatter = rememberDateFormatter()
    val journey = itinerary.journey
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = stringResource(
                    R.string.format_route_arrow,
                    itinerary.fromName,
                    itinerary.toName,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.saved_itinerary_subtitle,
                    dateFormatter.format(journey.departureScheduledTime),
                    timeFormatter.format(journey.departureScheduledTime),
                    timeFormatter.format(journey.arrivalScheduledTime),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { FavoriteButton(isFavorite = true, onToggle = onRemove) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private fun LazyListScope.sectionHeader(key: String, @StringRes titleRes: Int) {
    item(key = key) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

/** A saved connection, searched again for the current time (date/time isn't part of the favourite). */
private fun FavoriteConnection.toResultsRoute(): ResultsRoute = ResultsRoute(
    fromName = from.name,
    fromLat = from.lat,
    fromLon = from.lon,
    fromStopId = from.stopId,
    toName = to.name,
    toLat = to.lat,
    toLon = to.lon,
    toStopId = to.stopId,
    timeIso = null,
)

private fun FavoriteLine.toTripRoute(): TripRoute = TripRoute(
    tripId = tripId,
    lineLabel = label,
    headsign = headsign,
    modeName = mode.name,
    routeColor = routeColor,
)

@Composable
private fun ConnectionListItem(
    connection: FavoriteConnection,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(
                    R.string.format_route_arrow,
                    connection.from.name,
                    connection.to.name,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val fromCity = connection.from.city
            val toCity = connection.to.city
            val label = when {
                fromCity != null && toCity != null && fromCity != toCity ->
                    stringResource(R.string.format_route_arrow, fromCity, toCity)
                else -> fromCity ?: toCity
            }
            label?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { FavoriteButton(isFavorite = true, onToggle = onRemove) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LineListItem(
    line: FavoriteLine,
    containerColor: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = line.headsign?.let { stringResource(R.string.format_headsign, it) }
                    ?: stringResource(line.mode.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(stringResource(line.mode.labelRes)) },
        leadingContent = {
            ModeChip(mode = line.mode, label = line.label, containerColor = containerColor)
        },
        trailingContent = { FavoriteButton(isFavorite = true, onToggle = onRemove) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
