package pl.dakil.transport.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.VehicleMotionSettings
import pl.dakil.transport.ui.components.IntSliderRow
import pl.dakil.transport.ui.components.LabeledSliderRow
import pl.dakil.transport.ui.components.SteppedSliderRow
import pl.dakil.transport.ui.components.SwitchRow

/**
 * App-wide settings. Most of it is the vehicle-motion pipeline: because the API serves
 * timetables rather than vehicle positions, how a marker moves is a rendering decision, and
 * every part of that decision is exposed here rather than baked in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Bottom inset intentionally excluded: the app-level bottom navigation bar shown for
        // this route already clears the navigation bar inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                actions = {
                    TextButton(
                        enabled = !settings.isDefault,
                        onClick = viewModel::resetAll,
                    ) { Text("Reset all") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            VehicleMotionGroup(settings, viewModel)
            DataRefreshGroup(settings, viewModel)
            MapDetailGroup(settings, viewModel)
            Spacer(Modifier.size(8.dp))
        }
    }
}

/**
 * The one thing worth saying before any of the sliders make sense: the moving markers are an
 * estimate, not a tracked position. Collapsed to a single tappable line inside the vehicle
 * movement card — the headline is what most people need, the reasoning is one tap away and
 * costs no space until asked for.
 */
@Composable
private fun NotGpsNotice(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "notice-chevron")

    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Vehicles are estimated, not tracked",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide explanation" else "Show explanation",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "Transitous serves stop-to-stop timetables, never GPS positions. A " +
                        "vehicle's place between two stops is interpolated from its schedule, " +
                        "corrected by real-time delays where a feed provides them — so \"Live\" " +
                        "on the map means accurate times, not an observed position.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, end = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun VehicleMotionGroup(settings: AppSettings, viewModel: SettingsViewModel) {
    val motion = settings.vehicleMotion
    SettingsGroup(
        title = "Vehicle movement",
        icon = Icons.Default.DirectionsBus,
        onReset = viewModel::resetMotion.takeIf { !motion.isDefault },
    ) {
        NotGpsNotice()
        SwitchRow(
            title = "Never move backwards",
            checked = motion.monotonicProgress,
            onCheckedChange = { on -> viewModel.updateMotion { it.copy(monotonicProgress = on) } },
            supportingText = "Real-time feeds revise delays continuously. Without this, a " +
                "revision rewinds a marker mid-route; with it, the vehicle waits where it is " +
                "until its schedule catches up.",
        )
        SteppedSliderRow(
            title = "Frame interval",
            values = VehicleMotionSettings.FRAME_INTERVAL_STEPS,
            value = motion.frameIntervalMillis,
            onValueCommit = { value -> viewModel.updateMotion { it.copy(frameIntervalMillis = value) } },
            distance = { a, b -> abs(a - b).toFloat() },
            valueLabel = ::frameIntervalLabel,
            supportingText = "How often positions are redrawn. Costs CPU, never extra requests.",
        )
        IntSliderRow(
            title = "Fetch interval",
            value = motion.refreshIntervalSeconds,
            onValueCommit = { value -> viewModel.updateMotion { it.copy(refreshIntervalSeconds = value) } },
            min = 10,
            max = 120,
            step = 5,
            valueLabel = { "$it s" },
            supportingText = "How often new segments are fetched. Transitous is a shared " +
                "community service — keep this as high as you can live with.",
        )
        IntSliderRow(
            title = "Fetch window",
            value = motion.fetchWindowSeconds,
            onValueCommit = { value -> viewModel.updateMotion { it.copy(fetchWindowSeconds = value) } },
            min = 60,
            max = 600,
            step = 30,
            valueLabel = { "$it s" },
            supportingText = "How far ahead each fetch looks. Wider keeps long runs between " +
                "stops from dropping out mid-journey.",
        )
        IntSliderRow(
            title = "Keep missing trips",
            value = motion.segmentRetentionSeconds,
            onValueCommit = { value ->
                viewModel.updateMotion { it.copy(segmentRetentionSeconds = value) }
            },
            min = 0,
            max = 600,
            step = 30,
            valueLabel = { seconds -> if (seconds == 0) "Off" else "$seconds s" },
            supportingText = "How long a trip absent from the latest fetch keeps moving on what " +
                "is already known, instead of vanishing and popping back.",
        )
    }
}

/** Formats a redraw interval: sub-second values read as frame rates, longer ones as seconds. */
private fun frameIntervalLabel(millis: Int): String = when {
    millis < 1_000 -> "$millis ms · ${(1_000f / millis).roundToInt()} fps"
    millis % 1_000 == 0 -> "${millis / 1_000} s"
    else -> String.format(Locale.getDefault(), "%.1f s", millis / 1_000f)
}

@Composable
private fun DataRefreshGroup(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsGroup(title = "Auto-refresh", icon = Icons.Default.Refresh) {
        SwitchRow(
            title = "Refresh while open",
            checked = settings.autoRefreshEnabled,
            onCheckedChange = { on -> viewModel.update { it.copy(autoRefreshEnabled = on) } },
            supportingText = "Off, the connections list, departures board and trip view load " +
                "once and are reloaded only by their refresh button.",
        )
        if (settings.autoRefreshEnabled) {
            IntSliderRow(
                title = "Results & departures",
                value = settings.resultsRefreshSeconds,
                onValueCommit = { value -> viewModel.update { it.copy(resultsRefreshSeconds = value) } },
                min = 10,
                max = 300,
                step = 10,
                valueLabel = { "$it s" },
                supportingText = "How often those screens reload while open. Applies to searches " +
                    "started from now on.",
            )
        }
    }
}

@Composable
private fun MapDetailGroup(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsGroup(title = "Map detail", icon = Icons.Default.Layers) {
        LabeledSliderRow(
            title = "Stops from zoom",
            value = settings.stopsMinZoom,
            onValueCommit = { value -> viewModel.update { it.copy(stopsMinZoom = value) } },
            valueRange = 8f..18f,
            steps = 19,
            valueLabel = { String.format(Locale.getDefault(), "%.1f", it) },
            supportingText = "Lower shows stops earlier when zooming out, at the cost of a " +
                "denser map and larger responses.",
        )
        LabeledSliderRow(
            title = "Vehicles from zoom",
            value = settings.vehicleMotion.minZoom,
            onValueCommit = { value -> viewModel.updateMotion { it.copy(minZoom = value) } },
            valueRange = 5f..16f,
            steps = 21,
            valueLabel = { String.format(Locale.getDefault(), "%.1f", it) },
            supportingText = "Below this zoom no vehicles are fetched at all.",
        )
    }
}

/** A settings card: shaped icon tile, title, optional per-group reset, and its controls. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onReset != null) {
                    OutlinedButton(onClick = onReset) { Text("Reset") }
                }
            }
            content()
        }
    }
}
