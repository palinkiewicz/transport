package pl.dakil.transport.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.transport.R
import pl.dakil.transport.domain.model.AppColorTheme
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.ConnectionTimesMode
import pl.dakil.transport.domain.model.DarkThemeOption
import pl.dakil.transport.domain.model.DefaultTab
import pl.dakil.transport.domain.model.ExportDelivery
import pl.dakil.transport.domain.model.ExportFileName
import pl.dakil.transport.domain.model.LineColorMode
import pl.dakil.transport.domain.model.MapTheme
import pl.dakil.transport.domain.model.OfflineCacheSettings
import pl.dakil.transport.domain.model.VehicleMotionSettings
import pl.dakil.transport.ui.components.parseRouteColor
import pl.dakil.transport.ui.theme.colorSchemeFor
import pl.dakil.transport.ui.theme.resolveDark

/**
 * The settings sections, in the order the index lists them: how the app looks first, then how it
 * behaves, then the map and the deep interpolation tuning, then the cache.
 *
 * Five screens rather than the eight this started with — a section holding two switches was a
 * navigation step for nothing, and the split cut across the settings it was meant to organise
 * (the vehicles' minimum zoom sat on one screen while the rest of their motion tuning, and the
 * reset that would change it, sat on another). Grouping inside a screen is a [SectionHeader]
 * rather than a screen of its own.
 */
enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    APPEARANCE(
        R.string.settings_group_appearance,
        R.string.settings_group_appearance_summary,
        Icons.Default.Palette,
    ),
    GENERAL(R.string.settings_group_general, R.string.settings_group_general_summary, Icons.Default.Tune),
    SEARCH(R.string.settings_group_search, R.string.settings_group_search_summary, Icons.Default.Search),
    MAP(R.string.settings_group_map, R.string.settings_group_map_summary, Icons.Default.Layers),
    OFFLINE(R.string.settings_group_offline, R.string.settings_group_offline_summary, Icons.Default.CloudDownload),
}

/**
 * One section's controls, under the section's own top app bar.
 *
 * The section's reset sits in the bar's actions, where the index keeps "Reset all".
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
        SettingsSection.APPEARANCE -> viewModel::resetAppearance.takeIf { !settings.appearanceIsDefault }
        // Only the export block: the rest of this screen is flat on AppSettings with nothing to
        // scope a reset to.
        SettingsSection.SEARCH -> viewModel::resetItineraryExport.takeIf { !settings.export.isDefault }
        SettingsSection.MAP -> viewModel::resetMotion.takeIf { !settings.vehicleMotion.isDefault }
        SettingsSection.OFFLINE -> viewModel::resetOfflineCache.takeIf { !settings.offlineCache.isDefault }
        SettingsSection.GENERAL -> null
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
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> AppearanceSettings(settings, viewModel)
                SettingsSection.GENERAL -> GeneralSettings(settings, viewModel)
                SettingsSection.SEARCH -> SearchSettings(settings, viewModel)
                SettingsSection.MAP -> MapSettings(settings, viewModel)
                SettingsSection.OFFLINE -> OfflineDataSettings(settings, viewModel)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Appearance ---------------------------------------------------------------------------------

/**
 * Everything about how the app looks: its own palette, its dark mode, and the two places it hands
 * colours to something that is not a Material surface — the basemap and the line badges.
 */
@Composable
private fun AppearanceSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionHeader(stringResource(R.string.settings_theme_colors))

    // Dynamic colour is the platform's wallpaper palette; below Android 12 there is no wallpaper
    // palette to read, so the option is not offered rather than offered and inert.
    val themes = AppColorTheme.entries.filter {
        it != AppColorTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(themes, key = { it.name }) { theme ->
            ThemeColorSwatch(
                theme = theme,
                darkTheme = settings.darkTheme.resolveDark(),
                isSelected = theme == settings.colorTheme,
                onClick = { viewModel.update { it.copy(colorTheme = theme) } },
            )
        }
    }

    SectionHeader(stringResource(R.string.settings_dark_mode))

    SettingSegmentedRow(
        selected = settings.darkTheme,
        options = DarkThemeOption.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { option -> viewModel.update { it.copy(darkTheme = option) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_pure_black),
        summary = stringResource(R.string.settings_pure_black_note),
        checked = settings.pureBlack,
        onCheckedChange = { on -> viewModel.update { it.copy(pureBlack = on) } },
        // It has no effect at all in light mode; a switch you can flip that changes nothing reads
        // as broken.
        enabled = settings.darkTheme != DarkThemeOption.LIGHT,
    )

    SectionHeader(stringResource(R.string.settings_map_theme))

    // The same three-way control as the app's own dark mode above, because it is the same choice
    // asked about a different surface — offering one as a switch row and the other as a menu would
    // make them look like unrelated settings.
    SettingSegmentedRow(
        selected = settings.mapTheme,
        options = MapTheme.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { theme -> viewModel.update { it.copy(mapTheme = theme) } },
    )
    SectionNote(stringResource(R.string.settings_map_theme_note))

    LineColorsSettings(settings, viewModel)
}

/**
 * One theme, painted in its own colours.
 *
 * The circle is split the way the platform's own theme picker splits it — the accent across the
 * top, its two supporting containers below — so the swatch shows what the theme will actually look
 * like rather than reducing it to a single dot.
 *
 * [darkTheme] is the app's *resolved* dark flag, not the system's: someone forcing light mode on a
 * dark phone should be shown the light schemes they are about to get.
 */
@Composable
private fun ThemeColorSwatch(
    theme: AppColorTheme,
    darkTheme: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = colorSchemeFor(colorTheme = theme, darkTheme = darkTheme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                )
                // The ring is drawn on the outside edge, so the painted circle has to shrink inside
                // it or the two overlap.
                .padding(if (isSelected) 6.dp else 0.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(scheme.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(scheme.secondaryContainer, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(scheme.tertiaryContainer, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(theme.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
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

    SectionHeader(stringResource(R.string.settings_header_line_colors))

    SettingSelectRow(
        title = stringResource(R.string.settings_line_colors),
        // One note per mode rather than one paragraph covering all three: what the selected mode
        // does is the only thing worth reading here.
        summary = stringResource(
            when (settings.lineColorMode) {
                LineColorMode.TRANSITOUS -> R.string.settings_line_colors_note_transitous
                LineColorMode.CUSTOM -> R.string.settings_line_colors_note_custom
                LineColorMode.AUTO -> R.string.settings_line_colors_note_auto
            },
        ),
        selected = settings.lineColorMode,
        options = LineColorMode.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { mode -> viewModel.update { it.copy(lineColorMode = mode) } },
    )

    // Transitous never reads the palette, so offering it there is noise.
    AnimatedVisibility(visible = settings.lineColorMode != LineColorMode.TRANSITOUS) {
        Column {
            SectionHeader(stringResource(R.string.settings_line_colors_palette))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            SectionNote(stringResource(R.string.settings_line_colors_palette_note))
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

// ---- General ------------------------------------------------------------------------------------

@Composable
private fun GeneralSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionHeader(stringResource(R.string.settings_header_startup))

    SettingSelectRow(
        title = stringResource(R.string.settings_opening_screen),
        summary = stringResource(R.string.settings_opening_screen_note),
        selected = settings.defaultTab,
        options = DefaultTab.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { tab -> viewModel.update { it.copy(defaultTab = tab) } },
    )

    SectionHeader(stringResource(R.string.settings_header_remembering))

    SettingSwitchRow(
        title = stringResource(R.string.settings_remember_search),
        summary = stringResource(R.string.settings_remember_search_note),
        checked = settings.rememberLastSearch,
        onCheckedChange = { on -> viewModel.update { it.copy(rememberLastSearch = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_remember_map),
        summary = stringResource(R.string.settings_remember_map_note),
        checked = settings.rememberMapCamera,
        onCheckedChange = { on -> viewModel.update { it.copy(rememberMapCamera = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_stay_on_map),
        summary = stringResource(R.string.settings_stay_on_map_note),
        checked = settings.stayOnMapWhenPickingRoute,
        onCheckedChange = { on -> viewModel.update { it.copy(stayOnMapWhenPickingRoute = on) } },
    )

    SectionHeader(stringResource(R.string.settings_header_auto_refresh))

    SettingSwitchRow(
        title = stringResource(R.string.settings_refresh_while_open),
        summary = stringResource(R.string.settings_refresh_while_open_note),
        checked = settings.autoRefreshEnabled,
        onCheckedChange = { on -> viewModel.update { it.copy(autoRefreshEnabled = on) } },
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_refresh_interval),
        summary = stringResource(R.string.settings_refresh_interval_note),
        value = settings.resultsRefreshSeconds,
        onValueCommit = { value -> viewModel.update { it.copy(resultsRefreshSeconds = value) } },
        min = 10,
        max = 300,
        step = 10,
        valueLabel = { stringResource(R.string.format_seconds, it) },
        // Dimmed rather than removed: it stays visible so the interval it would use is still
        // readable, which is what the switch above is deciding about.
        enabled = settings.autoRefreshEnabled,
    )
}

// ---- Search, connections and export --------------------------------------------------------------

@Composable
private fun SearchSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionHeader(stringResource(R.string.settings_header_suggestions))

    SettingSwitchRow(
        title = stringResource(R.string.settings_keep_first_cached),
        summary = stringResource(R.string.settings_keep_first_cached_note),
        checked = settings.keepFirstCachedResult,
        onCheckedChange = { on -> viewModel.update { it.copy(keepFirstCachedResult = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_sort_by_distance),
        summary = stringResource(R.string.settings_sort_by_distance_note),
        checked = settings.sortSuggestionsByDistance,
        onCheckedChange = { on -> viewModel.update { it.copy(sortSuggestionsByDistance = on) } },
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_search_bias),
        summary = stringResource(R.string.settings_search_bias_note),
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
    )

    SectionHeader(stringResource(R.string.settings_header_recent_places))

    SettingIntSliderRow(
        title = stringResource(R.string.settings_recent_places),
        summary = stringResource(R.string.settings_recent_places_note),
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
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_pin_recent_places),
        summary = stringResource(R.string.settings_pin_recent_places_note),
        checked = settings.pinRecentPlaces,
        onCheckedChange = { on -> viewModel.update { it.copy(pinRecentPlaces = on) } },
        enabled = settings.recentPlacesLimit > AppSettings.RECENT_PLACES_OFF,
    )

    SectionHeader(stringResource(R.string.settings_header_connections))

    SettingSelectRow(
        title = stringResource(R.string.settings_connection_times),
        summary = stringResource(R.string.settings_connection_times_note),
        selected = settings.connectionTimesMode,
        options = ConnectionTimesMode.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { mode -> viewModel.update { it.copy(connectionTimesMode = mode) } },
    )

    ItineraryExportSettings(settings, viewModel)
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

    SectionHeader(stringResource(R.string.settings_header_export))

    SettingSelectRow(
        title = stringResource(R.string.settings_export_delivery),
        selected = export.delivery,
        options = ExportDelivery.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { delivery -> viewModel.updateExport { it.copy(delivery = delivery) } },
    )
    SettingSelectRow(
        title = stringResource(R.string.settings_export_filename),
        selected = export.fileName,
        options = ExportFileName.entries,
        label = { stringResource(it.labelRes) },
        onSelect = { name -> viewModel.updateExport { it.copy(fileName = name) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_export_tracks),
        summary = stringResource(R.string.settings_export_tracks_note),
        checked = export.includeTracks,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeTracks = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_export_access_legs),
        summary = stringResource(R.string.settings_export_access_legs_note),
        checked = export.includeAccessLegs,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeAccessLegs = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_export_intermediate_stops),
        summary = stringResource(R.string.settings_export_intermediate_stops_note),
        checked = export.includeIntermediateStops,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeIntermediateStops = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_export_times),
        summary = stringResource(R.string.settings_export_times_note),
        checked = export.includeTimes,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeTimes = on) } },
    )
    // Only meaningful once something is being timestamped.
    AnimatedVisibility(visible = export.includeTimes) {
        SettingSwitchRow(
            title = stringResource(R.string.settings_export_real_times),
            summary = stringResource(R.string.settings_export_real_times_note),
            checked = export.useRealTimes,
            onCheckedChange = { on -> viewModel.updateExport { it.copy(useRealTimes = on) } },
        )
    }
    SettingSwitchRow(
        title = stringResource(R.string.settings_export_descriptions),
        summary = stringResource(R.string.settings_export_descriptions_note),
        checked = export.includeDescriptions,
        onCheckedChange = { on -> viewModel.updateExport { it.copy(includeDescriptions = on) } },
    )
}

// ---- Map and vehicles ----------------------------------------------------------------------------

@Composable
private fun MapSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val motion = settings.vehicleMotion

    SectionHeader(stringResource(R.string.settings_header_map_detail))

    SettingSliderRow(
        title = stringResource(R.string.settings_stops_from_zoom),
        summary = stringResource(R.string.settings_stops_from_zoom_note),
        value = settings.stopsMinZoom,
        onValueCommit = { value -> viewModel.update { it.copy(stopsMinZoom = value) } },
        valueRange = 8f..18f,
        steps = 19,
        valueLabel = { stringResource(R.string.format_zoom_level, it) },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_itinerary_stop_names),
        summary = stringResource(R.string.settings_itinerary_stop_names_note),
        checked = settings.showItineraryStopNames,
        onCheckedChange = { on -> viewModel.update { it.copy(showItineraryStopNames = on) } },
    )

    SectionHeader(stringResource(R.string.settings_header_vehicles))

    // Lives here rather than under Movement below, next to the stops' own zoom threshold it mirrors
    // — and on the same screen as the reset that changes it, which it was not before.
    SettingSliderRow(
        title = stringResource(R.string.settings_vehicles_from_zoom),
        summary = stringResource(R.string.settings_vehicles_from_zoom_note),
        value = motion.minZoom,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(minZoom = value) } },
        valueRange = 5f..16f,
        steps = 21,
        valueLabel = { stringResource(R.string.format_zoom_level, it) },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_focus_vehicle),
        summary = stringResource(R.string.settings_focus_vehicle_note),
        checked = settings.focusSelectedVehicle,
        onCheckedChange = { on -> viewModel.update { it.copy(focusSelectedVehicle = on) } },
    )

    SectionHeader(stringResource(R.string.settings_header_movement))

    NotGpsNotice(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

    SettingSwitchRow(
        title = stringResource(R.string.settings_monotonic),
        summary = stringResource(R.string.settings_monotonic_note),
        checked = motion.monotonicProgress,
        onCheckedChange = { on -> viewModel.updateMotion { it.copy(monotonicProgress = on) } },
    )
    SettingSteppedSliderRow(
        title = stringResource(R.string.settings_frame_interval),
        summary = stringResource(R.string.settings_frame_interval_note),
        values = VehicleMotionSettings.FRAME_INTERVAL_STEPS,
        value = motion.frameIntervalMillis,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(frameIntervalMillis = value) } },
        distance = { a, b -> abs(a - b).toFloat() },
        valueLabel = ::frameIntervalLabel,
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_fetch_interval),
        summary = stringResource(R.string.settings_fetch_interval_note),
        value = motion.refreshIntervalSeconds,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(refreshIntervalSeconds = value) } },
        min = 10,
        max = 120,
        step = 5,
        valueLabel = { stringResource(R.string.format_seconds, it) },
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_fetch_window),
        summary = stringResource(R.string.settings_fetch_window_note),
        value = motion.fetchWindowSeconds,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(fetchWindowSeconds = value) } },
        min = 60,
        max = 600,
        step = 30,
        valueLabel = { stringResource(R.string.format_seconds, it) },
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_segment_retention),
        summary = stringResource(R.string.settings_segment_retention_note),
        value = motion.segmentRetentionSeconds,
        onValueCommit = { value -> viewModel.updateMotion { it.copy(segmentRetentionSeconds = value) } },
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
    )
}

/**
 * The one thing worth saying before any of the movement sliders make sense: the moving markers are
 * an estimate, not a tracked position. Collapsed to a single tappable line — the headline is what
 * most people need, the reasoning is one tap away and costs no space until asked for.
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

// ---- Offline and storage -------------------------------------------------------------------------

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

    SectionHeader(stringResource(R.string.settings_header_cache_refresh))

    SettingIntSliderRow(
        title = stringResource(R.string.settings_stop_cache_ttl),
        summary = stringResource(R.string.settings_stop_cache_ttl_note),
        value = cache.stopCacheTtlDays,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(stopCacheTtlDays = value) } },
        min = OfflineCacheSettings.MIN_TTL_DAYS,
        max = OfflineCacheSettings.MAX_TTL_DAYS,
        valueLabel = { pluralStringResource(R.plurals.plural_days, it, it) },
    )
    SettingIntSliderRow(
        title = stringResource(R.string.settings_search_cache_ttl),
        summary = stringResource(R.string.settings_search_cache_ttl_note),
        value = cache.searchCacheTtlDays,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(searchCacheTtlDays = value) } },
        min = OfflineCacheSettings.MIN_TTL_DAYS,
        max = OfflineCacheSettings.MAX_TTL_DAYS,
        valueLabel = { pluralStringResource(R.plurals.plural_days, it, it) },
    )

    SectionHeader(stringResource(R.string.settings_header_offline_search))

    SettingSwitchRow(
        title = stringResource(R.string.settings_offline_search),
        summary = stringResource(R.string.settings_offline_search_note),
        checked = cache.offlineSearchEnabled,
        onCheckedChange = { on -> viewModel.updateOfflineCache { it.copy(offlineSearchEnabled = on) } },
    )
    SettingSwitchRow(
        title = stringResource(R.string.settings_show_expired_cache),
        summary = stringResource(R.string.settings_show_expired_cache_note),
        checked = cache.showExpiredCache,
        onCheckedChange = { on -> viewModel.updateOfflineCache { it.copy(showExpiredCache = on) } },
    )

    SectionHeader(stringResource(R.string.settings_header_storage))

    SettingSteppedSliderRow(
        title = stringResource(R.string.settings_cache_limit),
        summary = stringResource(R.string.settings_cache_limit_note),
        values = OfflineCacheSettings.MAX_PLACES_STEPS,
        value = cache.maxCachedPlaces,
        onValueCommit = { value -> viewModel.updateOfflineCache { it.copy(maxCachedPlaces = value) } },
        valueLabel = { pluralStringResource(R.plurals.plural_places, it, it) },
    )
    stats?.let { current ->
        SectionNote(
            stringResource(
                R.string.settings_cache_stats,
                pluralStringResource(R.plurals.plural_stops, current.stops, current.stops),
                pluralStringResource(R.plurals.plural_areas, current.areas, current.areas),
            ),
        )
    }
    TextButton(
        enabled = stats?.let { it.places > 0 } == true,
        onClick = { confirmClear = true },
        modifier = Modifier.padding(horizontal = 16.dp),
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

/** A line of prose belonging to a group rather than to any one row — indented to match the rows. */
@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
