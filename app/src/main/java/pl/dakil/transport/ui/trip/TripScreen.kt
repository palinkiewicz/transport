package pl.dakil.transport.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.lastPassedIndex
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.LineColorRequest
import pl.dakil.transport.ui.components.rememberLineColors
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.RefreshButton
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.refreshSubtitle
import pl.dakil.transport.ui.navigation.DepartureBoardRoute

/** Timetable of a single vehicle run: every stop on the route with live times. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripScreen(
    onBack: () -> Unit,
    onOpenDepartures: (DepartureBoardRoute) -> Unit,
    onShowOnMap: () -> Unit,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberTickingNow()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // One badge on the whole screen, so it has no neighbour to clash with: AUTO comes out as the
    // operator's colour and CUSTOM as the first palette entry.
    val lineColor = rememberLineColors(
        listOf(LineColorRequest(viewModel.routeColor, viewModel.mode.color)),
    ).at(0, viewModel.mode.color)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeChip(mode = viewModel.mode, label = viewModel.lineLabel, containerColor = lineColor)
                        viewModel.headsign?.let {
                            Text(it, maxLines = 1)
                        }
                    }
                },
                subtitle = { Text(refreshSubtitle(R.string.refresh_subtitle_trip, secondsUntilRefresh)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    RefreshButton(secondsUntilRefresh, onRefresh = viewModel::refreshNow)
                    // Held back until the timetable is there: the map is handed this run's stops,
                    // not just its id. A run that is not on the road goes over as its route and
                    // stops alone — there is no vehicle to draw, but the line is still worth
                    // seeing.
                    if (uiState is TripUiState.Content) {
                        IconButton(
                            onClick = {
                                viewModel.showOnMap()
                                onShowOnMap()
                            },
                        ) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.trip_show_on_map))
                        }
                    }
                    FavoriteButton(isFavorite = isFavorite, onToggle = viewModel::toggleFavorite)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is TripUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is TripUiState.Error -> ErrorBox(
                error = state.error,
                modifier = Modifier.padding(innerPadding),
                onRetry = viewModel::refreshNow,
            )
            is TripUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (state.wheelchairAccessible == true || state.bikesAllowed == true) {
                        item(key = "amenities") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            ) {
                                VehicleAmenityChips(
                                    wheelchairAccessible = state.wheelchairAccessible,
                                    bikesAllowed = state.bikesAllowed,
                                )
                            }
                        }
                    }
                    tripTimetable(
                        stops = state.stops,
                        railColor = lineColor,
                        highlightedIndex = state.stops.lastPassedIndex(now),
                        // The trip's own stop id is the pole the vehicle calls at, so the
                        // board opens with this direction's group already on top.
                        onStopClick = { stop ->
                            onOpenDepartures(
                                DepartureBoardRoute(
                                    stopName = stop.place.name,
                                    lat = stop.place.lat,
                                    lon = stop.place.lon,
                                    stopId = stop.place.stopId,
                                    timeIso = null,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
