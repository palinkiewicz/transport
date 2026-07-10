package pl.dakil.transport.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.InlineRealTimeText
import pl.dakil.transport.ui.components.LoadingBox
import pl.dakil.transport.ui.components.ModeChip
import pl.dakil.transport.ui.components.VehicleAmenityChips
import pl.dakil.transport.ui.components.parseRouteColor

/** Timetable of a single vehicle run: every stop on the route with live times. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripScreen(
    onBack: () -> Unit,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeChip(mode = viewModel.mode, label = viewModel.lineLabel, routeColorHex = viewModel.routeColor)
                        viewModel.headsign?.let {
                            Text(it, maxLines = 1)
                        }
                    }
                },
                subtitle = { Text("Trip timetable · refreshes in $secondsUntilRefresh sec") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FavoriteButton(isFavorite = isFavorite, onToggle = viewModel::toggleFavorite)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is TripUiState.Loading -> LoadingBox(Modifier.padding(innerPadding))
            is TripUiState.Error -> ErrorBox(state.message, Modifier.padding(innerPadding))
            is TripUiState.Content -> {
                val railColor = parseRouteColor(viewModel.routeColor, viewModel.mode.color)
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
                    items(state.stops.size) { index ->
                        TripStopRow(
                            stop = state.stops[index],
                            railColor = railColor,
                            isFirst = index == 0,
                            isLast = index == state.stops.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStopRow(
    stop: TripStop,
    railColor: androidx.compose.ui.graphics.Color,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
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
            stop.track?.let { TrackPill(it) }
        }
    }
}

@Composable
private fun TrackPill(track: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Pl. $track",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
