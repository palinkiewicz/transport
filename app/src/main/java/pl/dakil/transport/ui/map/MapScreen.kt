package pl.dakil.transport.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.material3.DisappearingCompassButton
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.navigation.DeparturesRoute

private const val STOPS_FETCH_MIN_ZOOM = 13f

private fun stopKey(location: TransitLocation): String = location.stopId ?: "${location.lat},${location.lon}"

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(
    onOpenTimetable: (DeparturesRoute) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stops by viewModel.stops.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var pendingLocateMe by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
    }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(target = Position(latitude = 50.0, longitude = 10.0), zoom = 5.0),
    )
    val styleState = rememberStyleState()

    var selectedStop by remember { mutableStateOf<TransitLocation?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val stopsById = remember(stops) { stops.associateBy(::stopKey) }
    val stopFeatures = remember(stops) {
        FeatureCollection(
            stops.map { stop ->
                Feature<Point, JsonObject?>(
                    id = JsonPrimitive(stopKey(stop)),
                    geometry = Point(Position(latitude = stop.lat, longitude = stop.lon)),
                    properties = null,
                )
            },
        )
    }

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving }
            .debounce(400)
            .filter { moving -> !moving }
            .collect {
                if (cameraState.position.zoom >= STOPS_FETCH_MIN_ZOOM) {
                    val bbox = cameraState.projection?.queryVisibleBoundingBox()
                    if (bbox != null) {
                        viewModel.onViewportSettled(bbox.south, bbox.west, bbox.north, bbox.east)
                    }
                }
            }
    }

    val locationState = if (hasLocationPermission) {
        val locationProvider = rememberDefaultLocationProvider()
        rememberUserLocationState(locationProvider)
    } else {
        null
    }

    LaunchedEffect(pendingLocateMe, hasLocationPermission, locationState) {
        if (pendingLocateMe && hasLocationPermission && locationState != null) {
            val position = locationState.location?.position?.value
            if (position != null) {
                cameraState.animateTo(CameraPosition(target = position, zoom = 15.0))
                pendingLocateMe = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            styleState = styleState,
        ) {
            val stopsSource = rememberGeoJsonSource(data = GeoJsonData.Features(stopFeatures))
            CircleLayer(
                id = "transport-stops",
                source = stopsSource,
                onClick = { features ->
                    val id = features.firstOrNull()?.id?.content
                    val stop = id?.let { stopsById[it] }
                    if (stop != null) {
                        selectedStop = stop
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                },
            )
            if (locationState != null) {
                LocationPuck(
                    idPrefix = "user",
                    location = locationState.location,
                    cameraState = cameraState,
                )
            }
        }

        DisappearingCompassButton(
            cameraState = cameraState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        )

        ExpandingAttributionButton(
            cameraState = cameraState,
            styleState = styleState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            contentAlignment = Alignment.BottomStart,
        )

        // Transitous API usage guidelines require a visible link to the data sources.
        Text(
            text = "Data: transitous.org",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 72.dp, bottom = 20.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    RoundedCornerShape(4.dp),
                )
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://transitous.org/sources/")),
                    )
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        FloatingActionButton(
            onClick = {
                if (!hasLocationPermission) {
                    pendingLocateMe = true
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                } else {
                    pendingLocateMe = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Locate me")
        }
    }

    val stop = selectedStop
    if (stop != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedStop = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                BottomSheetAction(
                    text = "Stop timetable",
                    icon = Icons.Default.Schedule,
                    onClick = {
                        scope.launch { sheetState.hide() }
                        selectedStop = null
                        onOpenTimetable(
                            DeparturesRoute(stopName = stop.name, lat = stop.lat, lon = stop.lon, stopId = stop.stopId),
                        )
                    },
                )
                BottomSheetAction(
                    text = "Begin here",
                    icon = Icons.Default.NearMe,
                    onClick = {
                        scope.launch { sheetState.hide() }
                        selectedStop = null
                        viewModel.beginHere(stop)
                        onNavigateToSearch()
                    },
                )
                BottomSheetAction(
                    text = "Finish here",
                    icon = Icons.Default.Flag,
                    onClick = {
                        scope.launch { sheetState.hide() }
                        selectedStop = null
                        viewModel.finishHere(stop)
                        onNavigateToSearch()
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomSheetAction(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
