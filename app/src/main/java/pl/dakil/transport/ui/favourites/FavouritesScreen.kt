package pl.dakil.transport.ui.favourites

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.domain.model.FavoriteConnection
import pl.dakil.transport.domain.model.FavoriteLine
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.LocationListItem
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.navigation.ResultsRoute
import pl.dakil.transport.ui.navigation.TripRoute

/**
 * The Favourites tab: everything the user has starred — places, connections (start→end
 * pairs, searched for "now" on tap) and lines (opening their trip timetable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavouritesScreen(
    onOpenSearch: () -> Unit,
    onOpenConnection: (ResultsRoute) -> Unit,
    onOpenTrip: (TripRoute) -> Unit,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Bottom inset intentionally excluded: the app-level bottom navigation bar shown for
        // this route already clears the navigation bar inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Favourites") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val isEmpty = favorites.locations.isEmpty() &&
            favorites.connections.isEmpty() &&
            favorites.lines.isEmpty()
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
                    text = "Nothing starred yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Star places while searching, connections from their results, " +
                        "and lines from a vehicle or trip — they will all gather here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                if (favorites.locations.isNotEmpty()) {
                    sectionHeader("places-header", "Places")
                    items(
                        count = favorites.locations.size,
                        key = { "loc:${favorites.locations[it].favoriteKey}" },
                    ) { index ->
                        val location = favorites.locations[index]
                        LocationListItem(
                            location = location,
                            onClick = {
                                viewModel.setSearchDestination(location)
                                onOpenSearch()
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
                    sectionHeader("connections-header", "Connections")
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
                    sectionHeader("lines-header", "Lines")
                    items(
                        count = favorites.lines.size,
                        key = { "line:${favorites.lines[it].key}" },
                    ) { index ->
                        val line = favorites.lines[index]
                        LineListItem(
                            line = line,
                            onClick = { onOpenTrip(line.toTripRoute()) },
                            onRemove = { viewModel.removeLine(line) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, title: String) {
    item(key = key) {
        Text(
            text = title,
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
    maxTransfers = null,
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
                text = "${connection.from.name} → ${connection.to.name}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val fromCity = connection.from.city
            val toCity = connection.to.city
            val label = when {
                fromCity != null && toCity != null && fromCity != toCity -> "$fromCity → $toCity"
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = line.headsign?.let { "→ $it" } ?: line.mode.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(line.mode.label) },
        leadingContent = {
            ModeChip(mode = line.mode, label = line.label, routeColorHex = line.routeColor)
        },
        trailingContent = { FavoriteButton(isFavorite = true, onToggle = onRemove) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
