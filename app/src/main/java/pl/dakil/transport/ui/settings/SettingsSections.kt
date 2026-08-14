package pl.dakil.transport.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.ConnectionTimesMode
import pl.dakil.transport.domain.model.DefaultTab
import pl.dakil.transport.domain.model.ExportDelivery
import pl.dakil.transport.domain.model.ExportFileName
import pl.dakil.transport.domain.model.LineColorMode
import pl.dakil.transport.domain.model.MapTheme
import pl.dakil.transport.domain.model.OfflineCacheSettings
import pl.dakil.transport.domain.model.VehicleMotionSettings
import pl.dakil.transport.ui.components.IntSliderRow
import pl.dakil.transport.ui.components.LabeledSliderRow
import pl.dakil.transport.ui.components.SingleChoiceToggleFlow
import pl.dakil.transport.ui.components.SteppedSliderRow
import pl.dakil.transport.ui.components.SwitchRow
import pl.dakil.transport.ui.components.parseRouteColor

/**
 * The settings sections, in the order the index lists them: everyday choices first, the deep
 * interpolation tuning last. Each one is a screen of its own — [SettingsSectionScreen] draws it,
 * and the index only needs the title, the summary and the icon from here.
 */
enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    GENERAL(R.string.settings_group_general, R.string.settings_group_general_summary, Icons.Default.Tune),
    SEARCH(R.string.settings_group_search, R.string.settings_group_search_summary, Icons.Default.Search),
    LINE_COLORS(
        R.string.settings_group_line_colors,
        R.string.settings_group_line_colors_summary,
        Icons.Default.Palette,
    ),
    EXPORT(R.string.settings_group_export, R.string.settings_group_export_summary, Icons.Default.Share),
    MAP_DETAIL(
        R.string.settings_group_map_detail,
        R.string.settings_group_map_detail_summary,
        Icons.Default.Layers,
    ),
    OFFLINE(R.string.settings_group_offline, R.string.settings_group_offline_summary, Icons.Default.CloudDownload),
    AUTO_REFRESH(
        R.string.settings_group_auto_refresh,
        R.string.settings_group_auto_refresh_summary,
        Icons.Default.Refresh,
    ),
    VEHICLE_MOVEMENT(
        R.string.settings_group_vehicle_movement,
        R.string.settings_group_vehicle_movement_summary,
        Icons.Default.DirectionsBus,
    ),
}

/**
 * One section's controls, under the section's own top app bar.
 *
 * The section's per-group reset moves into the bar's actions, where the index keeps "Reset all" —
 * the card header it used to sit in is now the screen itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Null where the section has nothing of its own to reset, or is already at its defaults.
    val onReset: (() -> Unit)? = when (section) {
        SettingsSection.LINE_COLORS -> viewModel::resetLineColors.takeIf { !settings.lineColorsAreDefault }
        SettingsSection.EXPORT -> viewModel::resetItineraryExport.takeIf { !settings.export.isDefault }
        SettingsSection.OFFLINE -> viewModel::resetOfflineCache.takeIf { !settings.offlineCache.isDefault }
        SettingsSection.VEHICLE_MOVEMENT -> viewModel::resetMotion.takeIf { !settings.vehicleMotion.isDefault }
        else -> null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(section.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (onReset != null) {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsCard {
                when (section) {
                    SettingsSection.GENERAL -> GeneralSettings(settings, viewModel)
                    SettingsSection.SEARCH -> SearchAndResultsSettings(settings, viewModel)
                    SettingsSection.LINE_COLORS -> LineColorsSettings(settings, viewModel)
                    SettingsSection.EXPORT -> ItineraryExportSettings(settings, viewModel)
                    SettingsSection.MAP_DETAIL -> MapDetailSettings(settings, viewModel)
                    SettingsSection.OFFLINE -> OfflineDataSettings(settings, viewModel)
                    SettingsSection.AUTO_REFRESH -> DataRefreshSettings(settings, viewModel)
                    SettingsSection.VEHICLE_MOVEMENT -> VehicleMovementSettings(settings, viewModel)
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** The card the controls used to sit in as a group, kept so the rows keep their surface and spacing. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

/**
 * The one thing worth saying before any of the sliders make sense: the moving markers are an
 * estimate, not a tracked position. Collapsed to a single tappable line — the headline is what most
 * people need, the reasoning is one tap away and costs no space until asked for.
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
                    text = stringResource(R.string.settings_not_gps_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_not_gps_hide else R.string.settings_not_gps_show,
                    ),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = stringResource(R.string.settings_not_gps_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, end = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun GeneralSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_opening_screen), style = MaterialTheme.typography.titleSmall)
        // A wrapping flow, not a connected row: four tab names don't fit one row without
        // being cut off mid-word.
        SingleChoiceToggleFlow(
            options = DefaultTab.entries,
            selected = settings.defaultTab,
            onSelect = { tab -> viewModel.update { it.copy(defaultTab = tab) } },
            label = { stringResource(it.labelRes) },
        )
        Text(
            text = stringResource(R.string.settings_opening_screen_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_remember_search),
        checked = settings.rememberLastSearch,
        onCheckedChange = { on -> viewModel.update { it.copy(rememberLastSearch = on) } },
        supportingText = stringResource(R.string.settings_remember_search_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_stay_on_map),
        checked = settings.stayOnMapWhenPickingRoute,
        onCheckedChange = { on -> viewModel.update { it.copy(stayOnMapWhenPickingRoute = on) } },
        supportingText = stringResource(R.string.settings_stay_on_map_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_remember_map),
        checked = settings.rememberMapCamera,
        onCheckedChange = { on -> viewModel.update { it.copy(rememberMapCamera = on) } },
        supportingText = stringResource(R.string.settings_remember_map_note),
    )
}

@Composable
private fun SearchAndResultsSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_connection_times), style = MaterialTheme.typography.titleSmall)
        // Wrapping flow like the opening-screen choice above: "Door to door" is far too long
        // to share one row with the other mode without being cut off.
        SingleChoiceToggleFlow(
            options = ConnectionTimesMode.entries,
            selected = settings.connectionTimesMode,
            onSelect = { mode -> viewModel.update { it.copy(connectionTimesMode = mode) } },
            label = { stringResource(it.labelRes) },
        )
        Text(
            text = stringResource(R.string.settings_connection_times_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IntSliderRow(
        title = stringResource(R.string.settings_recent_places),
        value = settings.recentPlacesLimit,
        onValueCommit = { count -> viewModel.update { it.copy(recentPlacesLimit = count) } },
        min = AppSettings.RECENT_PLACES_OFF,
        max = AppSettings.RECENT_PLACES_MAX,
        valueLabel = { count ->
            if (count <= AppSettings.RECENT_PLACES_OFF) {
                stringResource(R.string.settings_recent_places_off)
            } else {
                count.toString()
            }
        },
        supportingText = stringResource(R.string.settings_recent_places_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_pin_recent_places),
        checked = settings.pinRecentPlaces,
        onCheckedChange = { on -> viewModel.update { it.copy(pinRecentPlaces = on) } },
        supportingText = stringResource(R.string.settings_pin_recent_places_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_keep_first_cached),
        checked = settings.keepFirstCachedResult,
        onCheckedChange = { on -> viewModel.update { it.copy(keepFirstCachedResult = on) } },
        supportingText = stringResource(R.string.settings_keep_first_cached_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_sort_by_distance),
        checked = settings.sortSuggestionsByDistance,
        onCheckedChange = { on -> viewModel.update { it.copy(sortSuggestionsByDistance = on) } },
        supportingText = stringResource(R.string.settings_sort_by_distance_note),
    )
    IntSliderRow(
        title = stringResource(R.string.settings_search_bias),
        value = settings.searchBiasStrength,
        onValueCommit = { strength -> viewModel.update { it.copy(searchBiasStrength = strength) } },
        min = AppSettings.SEARCH_BIAS_NONE,
        max = AppSettings.SEARCH_BIAS_MAX,
        valueLabel = { strength ->
            if (strength <= AppSettings.SEARCH_BIAS_NONE) {
                stringResource(R.string.settings_search_bias_none)
            } else {
                strength.toString()
            }
        },
        supportingText = stringResource(R.string.settings_search_bias_note),
    )
}

/**
 * Where line badges take their colour from, and the palette the two non-server modes draw on.
 * Only the list screens honour this — see [pl.dakil.transport.ui.components.rememberLineColors].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineColorsSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val palette = settings.palette

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_line_colors), style = MaterialTheme.typography.titleSmall)
        SingleChoiceToggleFlow(
            options = LineColorMode.entries,
            selected = settings.lineColorMode,
            onSelect = { mode -> viewModel.update { it.copy(lineColorMode = mode) } },
            label = { stringResource(it.labelRes) },
        )
        // One note per mode rather than one paragraph covering all three: what the selected
        // mode does is the only thing worth reading here.
        Text(
            text = stringResource(
                when (settings.lineColorMode) {
                    LineColorMode.TRANSITOUS -> R.string.settings_line_colors_note_transitous
                    LineColorMode.CUSTOM -> R.string.settings_line_colors_note_custom
                    LineColorMode.AUTO -> R.string.settings_line_colors_note_auto
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Transitous never reads the palette, so offering it there is noise.
    AnimatedVisibility(visible = settings.lineColorMode != LineColorMode.TRANSITOUS) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_line_colors_palette), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                palette.forEachIndexed { index, hex ->
                    val description = stringResource(R.string.settings_line_color_swatch, index + 1)
                    Surface(
                        onClick = { editingIndex = index },
                        shape = CircleShape,
                        color = parseRouteColor(hex, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = description },
                    ) {}
                }
            }
            Text(
                text = stringResource(R.string.settings_line_colors_palette_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingIndex?.let { index ->
        ColorPresetDialog(
            selected = palette[index],
            onPick = { hex ->
                viewModel.update { current ->
                    current.copy(customLineColors = current.palette.toMutableList().also { it[index] = hex })
                }
                editingIndex = null
            },
            onDismiss = { editingIndex = null },
        )
    }
}

/**
 * What an exported itinerary contains, and how it leaves the app — the same choices whichever
 * format the share menu picks. The readers disagree wildly about what they want out of a file —
 * some ignore waypoints, others choke on long tracks — so the shape of the export is the user's to
 * decide.
 */
@Composable
private fun ItineraryExportSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val export = settings.export
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_export_delivery), style = MaterialTheme.typography.titleSmall)
        SingleChoiceToggleFlow(
            options = ExportDelivery.entries,
            selected = export.delivery,
            onSelect = { delivery -> viewModel.updateExport { it.copy(delivery = delivery) } },
            label = { stringResource(it.labelRes) },
        )
        Text(stringResource(R.string.settings_export_filename), style = MaterialTheme.typography.titleSmall)
        SingleChoiceToggleFlow(
            options = ExportFileName.entries,
            selected = export.fileName,
            onSelect = { name -> viewModel.updateExport { it.copy(fileName = name) } },
            label = { stringResource(it.labelRes) },
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_export_tracks),
        checked = export.includeTracks,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeTracks = on) } },
        supportingText = stringResource(R.string.settings_export_tracks_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_export_access_legs),
        checked = export.includeAccessLegs,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeAccessLegs = on) } },
        supportingText = stringResource(R.string.settings_export_access_legs_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_export_intermediate_stops),
        checked = export.includeIntermediateStops,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeIntermediateStops = on) } },
        supportingText = stringResource(R.string.settings_export_intermediate_stops_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_export_times),
        checked = export.includeTimes,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeTimes = on) } },
        supportingText = stringResource(R.string.settings_export_times_note),
    )
    // Only meaningful once something is being timestamped.
    AnimatedVisibility(visible = export.includeTimes) {
        SwitchRow(
            title = stringResource(R.string.settings_export_real_times),
            checked = export.useRealTimes,
            onCheckedChange = { on -> viewModel.updateExport { it.copy(useRealTimes = on) } },
            supportingText = stringResource(R.string.settings_export_real_times_note),
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_export_descriptions),
        checked = export.includeDescriptions,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeDescriptions = on) } },
        supportingText = stringResource(R.string.settings_export_descriptions_note),
    )
}

/**
 * Twelve hue families in two tones each — enough range to build a readable set of six without
 * asking anyone to reason about hex, and every entry mid-toned so a badge's black-or-white label
 * stays legible on it.
 */
private val LINE_COLOR_PRESETS = listOf(
    "B3261E", "E46962", "9A4B00", "D97706", "8B6B00", "C9A227",
    "4C6B1F", "7CB342", "006D3B", "3FA96A", "00696D", "00A5A8",
    "00658F", "3AA0CC", "1A56DB", "5B8DEF", "3730A3", "6D63D6",
    "6750A4", "9A82DB", "8E24AA", "B85FCB", "984061", "C7809A",
)

@Composable
private fun ColorPresetDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val selectedLabel = stringResource(R.string.settings_line_color_selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_line_color_pick)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LINE_COLOR_PRESETS.size) { index ->
                    val hex = LINE_COLOR_PRESETS[index]
                    val color = parseRouteColor(hex, MaterialTheme.colorScheme.surfaceVariant)
                    Surface(
                        onClick = { onPick(hex) },
                        shape = CircleShape,
                        color = color,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (hex.equals(selected, ignoreCase = true)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = selectedLabel,
                                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun VehicleMovementSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val motion = settings.vehicleMotion
    NotGpsNotice()
    SwitchRow(
        title = stringResource(R.string.settings_monotonic),
        checked = motion.monotonicProgress,
        onCheckedChange = { on -> viewModel.updateMotion { it.copy(monotonicProgress = on) } },
        supportingText = stringResource(R.string.settings_monotonic_note),
    )
    SteppedSliderRow(
        title = stringResource(R.string.settings_frame_interval),
        values = VehicleMotionSettings.FRAME_INTERVAL_STEPS,
        value = motion.frameIntervalMillis,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(frameIntervalMillis = value) } },
        distance = { a, b -> abs(a - b).toFloat() },
        valueLabel = ::frameIntervalLabel,
        supportingText = stringResource(R.string.settings_frame_interval_note),
    )
    IntSliderRow(
        title = stringResource(R.string.settings_fetch_interval),
        value = motion.refreshIntervalSeconds,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(refreshIntervalSeconds = value) } },
        min = 10,
        max = 120,
        step = 5,
        valueLabel = { stringResource(R.string.format_seconds, it) },
        supportingText = stringResource(R.string.settings_fetch_interval_note),
    )
    IntSliderRow(
        title = stringResource(R.string.settings_fetch_window),
        value = motion.fetchWindowSeconds,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(fetchWindowSeconds = value) } },
        min = 60,
        max = 600,
        step = 30,
        valueLabel = { stringResource(R.string.format_seconds, it) },
        supportingText = stringResource(R.string.settings_fetch_window_note),
    )
    IntSliderRow(
        title = stringResource(R.string.settings_segment_retention),
        value = motion.segmentRetentionSeconds,
        onValueCommit = { value ->
            viewModel.updateMotion { it.copy(segmentRetentionSeconds = value) }
        },
        min = 0,
        max = 600,
        step = 30,
        valueLabel = { seconds ->
            if (seconds == 0) {
                stringResource(R.string.settings_segment_retention_off)
            } else {
                stringResource(R.string.format_seconds, seconds)
            }
        },
        supportingText = stringResource(R.string.settings_segment_retention_note),
    )
}

/** Formats a redraw interval: sub-second values read as frame rates, longer ones as seconds. */
@Composable
private fun frameIntervalLabel(millis: Int): String = when {
    millis < 1_000 -> stringResource(
        R.string.format_frame_interval_millis,
        millis,
        (1_000f / millis).roundToInt(),
    )
    millis % 1_000 == 0 -> stringResource(R.string.format_frame_interval_seconds, millis / 1_000)
    else -> stringResource(R.string.format_frame_interval_seconds_decimal, millis / 1_000f)
}

@Composable
private fun DataRefreshSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    SwitchRow(
        title = stringResource(R.string.settings_refresh_while_open),
        checked = settings.autoRefreshEnabled,
        onCheckedChange = { on -> viewModel.update { it.copy(autoRefreshEnabled = on) } },
        supportingText = stringResource(R.string.settings_refresh_while_open_note),
    )
    if (settings.autoRefreshEnabled) {
        IntSliderRow(
            title = stringResource(R.string.settings_refresh_interval),
            value = settings.resultsRefreshSeconds,
            onValueCommit = { value -> viewModel.update { it.copy(resultsRefreshSeconds = value) } },
            min = 10,
            max = 300,
            step = 10,
            valueLabel = { stringResource(R.string.format_seconds, it) },
            supportingText = stringResource(R.string.settings_refresh_interval_note),
        )
    }
}

@Composable
private fun MapDetailSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_map_theme), style = MaterialTheme.typography.titleSmall)
        SingleChoiceToggleFlow(
            options = MapTheme.entries,
            selected = settings.mapTheme,
            onSelect = { theme -> viewModel.update { it.copy(mapTheme = theme) } },
            label = { stringResource(it.labelRes) },
        )
        Text(
            text = stringResource(R.string.settings_map_theme_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LabeledSliderRow(
        title = stringResource(R.string.settings_stops_from_zoom),
        value = settings.stopsMinZoom,
        onValueCommit = { value -> viewModel.update { it.copy(stopsMinZoom = value) } },
        valueRange = 8f..18f,
        steps = 19,
        valueLabel = { stringResource(R.string.format_zoom_level, it) },
        supportingText = stringResource(R.string.settings_stops_from_zoom_note),
    )
    LabeledSliderRow(
        title = stringResource(R.string.settings_vehicles_from_zoom),
        value = settings.vehicleMotion.minZoom,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(minZoom = value) } },
        valueRange = 5f..16f,
        steps = 21,
        valueLabel = { stringResource(R.string.format_zoom_level, it) },
        supportingText = stringResource(R.string.settings_vehicles_from_zoom_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_focus_vehicle),
        checked = settings.focusSelectedVehicle,
        onCheckedChange = { on -> viewModel.update { it.copy(focusSelectedVehicle = on) } },
        supportingText = stringResource(R.string.settings_focus_vehicle_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_itinerary_stop_names),
        checked = settings.showItineraryStopNames,
        onCheckedChange = { on -> viewModel.update { it.copy(showItineraryStopNames = on) } },
        supportingText = stringResource(R.string.settings_itinerary_stop_names_note),
    )
}

/**
 * How long fetched data is kept, how much of it, and what to do with it.
 *
 * The copy here has one job beyond naming the controls: making clear that none of this ever
 * hides a stop the app already knows about. Expiry only decides when it is worth asking the API
 * again — and "keep showing expired data" is the one switch that does gate what is drawn, which
 * is why it says so plainly.
 */
@Composable
private fun OfflineDataSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val cache = settings.offlineCache
    val stats by viewModel.cacheStats.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    IntSliderRow(
        title = stringResource(R.string.settings_stop_cache_ttl),
        value = cache.stopCacheTtlDays,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(stopCacheTtlDays = value) } },
        min = OfflineCacheSettings.MIN_TTL_DAYS,
        max = OfflineCacheSettings.MAX_TTL_DAYS,
        valueLabel = { pluralStringResource(R.plurals.plural_days, it, it) },
        supportingText = stringResource(R.string.settings_stop_cache_ttl_note),
    )
    IntSliderRow(
        title = stringResource(R.string.settings_search_cache_ttl),
        value = cache.searchCacheTtlDays,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(searchCacheTtlDays = value) } },
        min = OfflineCacheSettings.MIN_TTL_DAYS,
        max = OfflineCacheSettings.MAX_TTL_DAYS,
        valueLabel = { pluralStringResource(R.plurals.plural_days, it, it) },
        supportingText = stringResource(R.string.settings_search_cache_ttl_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_offline_search),
        checked = cache.offlineSearchEnabled,
        onCheckedChange = { on -> viewModel.updateOfflineCache { it.copy(offlineSearchEnabled = on) } },
        supportingText = stringResource(R.string.settings_offline_search_note),
    )
    SwitchRow(
        title = stringResource(R.string.settings_show_expired_cache),
        checked = cache.showExpiredCache,
        onCheckedChange = { on -> viewModel.updateOfflineCache { it.copy(showExpiredCache = on) } },
        supportingText = stringResource(R.string.settings_show_expired_cache_note),
    )
    SteppedSliderRow(
        title = stringResource(R.string.settings_cache_limit),
        values = OfflineCacheSettings.MAX_PLACES_STEPS,
        value = cache.maxCachedPlaces,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(maxCachedPlaces = value) } },
        valueLabel = { pluralStringResource(R.plurals.plural_places, it, it) },
        supportingText = stringResource(R.string.settings_cache_limit_note),
    )
    stats?.let { current ->
        Text(
            text = stringResource(
                R.string.settings_cache_stats,
                pluralStringResource(R.plurals.plural_stops, current.stops, current.stops),
                pluralStringResource(R.plurals.plural_areas, current.areas, current.areas),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(
        enabled = stats?.let { it.places > 0 } == true,
        onClick = { confirmClear = true },
    ) { Text(stringResource(R.string.settings_clear_cache)) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearCache()
                    },
                ) { Text(stringResource(R.string.settings_clear_cache_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
