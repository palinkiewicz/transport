package pl.dakil.transport.ui.itinerary

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.launch
import pl.dakil.transport.BuildConfig
import pl.dakil.transport.R
import pl.dakil.transport.data.export.ExportLabels
import pl.dakil.transport.domain.model.ExportDelivery
import pl.dakil.transport.domain.model.ExportFormat
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.TransportMode

/**
 * The top bar's export button: a menu of the formats the journey can leave as, because which one
 * is right depends on where the file is going and there is nothing to guess from.
 */
@Composable
internal fun ExportMenuButton(onPick: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExportFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(stringResource(format.labelRes)) },
                    onClick = {
                        open = false
                        onPick(format)
                    },
                )
            }
        }
    }
}

/** What the top bar's export button does, and the state its dialogs read. */
internal class ItineraryExport(
    val start: (ExportFormat) -> Unit,
    val share: () -> Unit,
    val save: () -> Unit,
    /** True while the "share or save?" sheet is up — only ever with [ExportDelivery.ASK]. */
    val choosing: Boolean,
    val onDismissChoice: () -> Unit,
    val failed: Boolean,
    val onDismissFailure: () -> Unit,
)

/**
 * Wires the picked format to the delivery the user chose in settings: straight to the share sheet,
 * straight to the document picker, or a sheet asking which. Everything user-visible in the file
 * itself is resolved here and handed to the writer as [ExportLabels] — the writer sits below the
 * Compose layer and has no resources of its own.
 */
@Composable
internal fun rememberItineraryExport(
    journey: Journey?,
    fromName: String,
    toName: String,
    /** Per-leg colours from the itinerary list, so the file opens in the colours it was seen in. */
    legColors: List<Color?>,
    viewModel: ItineraryViewModel,
): ItineraryExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.export.collectAsStateWithLifecycle()
    val labels = rememberExportLabels(fromName, toName, journey, legColors)
    val subject = stringResource(R.string.export_subject, fromName, toName)
    val chooserTitle = stringResource(R.string.export_action)

    var choosing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    /** The format the menu picked, held while the sheet or the document picker is up. */
    var format by remember { mutableStateOf(ExportFormat.entries.first()) }

    // One launcher per format: `CreateDocument` freezes its mime type at construction, and the
    // picker suggesting the wrong type is what makes a saved file open in the wrong app.
    val saveLaunchers = ExportFormat.entries.associateWith { launcherFormat ->
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(launcherFormat.mimeType),
        ) { uri ->
            // A null uri is the user backing out of the picker, not a failure.
            if (uri != null && journey != null) {
                scope.launch {
                    failed = viewModel.exportTo(uri, launcherFormat, journey, labels).isFailure
                }
            }
        }
    }

    val shareAs = { chosen: ExportFormat ->
        choosing = false
        if (journey != null) {
            scope.launch {
                val fileName = viewModel.fileNameFor(journey, chosen, fromName, toName)
                viewModel.exportToCache(journey, chosen, fileName, labels)
                    .onSuccess { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = chosen.mimeType
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

    val saveAs = { chosen: ExportFormat ->
        choosing = false
        if (journey != null) {
            saveLaunchers.getValue(chosen)
                .launch(viewModel.fileNameFor(journey, chosen, fromName, toName))
        }
        Unit
    }

    return ItineraryExport(
        start = { picked ->
            // Remembered for the "share or save?" sheet, which answers after this returns.
            format = picked
            when (settings.delivery) {
                ExportDelivery.SHARE -> shareAs(picked)
                ExportDelivery.SAVE -> saveAs(picked)
                ExportDelivery.ASK -> choosing = true
            }
        },
        share = { shareAs(format) },
        save = { saveAs(format) },
        choosing = choosing,
        onDismissChoice = { choosing = false },
        failed = failed,
        onDismissFailure = { failed = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItineraryExportDialogs(export: ItineraryExport) {
    if (export.choosing) {
        ModalBottomSheet(onDismissRequest = export.onDismissChoice) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.export_choose),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ExportChoiceRow(Icons.Default.Share, stringResource(R.string.export_share), export.share)
                ExportChoiceRow(Icons.Default.Save, stringResource(R.string.export_save), export.save)
            }
        }
    }
    if (export.failed) {
        AlertDialog(
            onDismissRequest = export.onDismissFailure,
            title = { Text(stringResource(R.string.export_failed_title)) },
            text = { Text(stringResource(R.string.export_failed_body)) },
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
 * The app-authored text that ends up inside the exported file, plus the colour each leg is drawn
 * in. Mode names come from the same `labelRes` the rest of the UI reads. The two parameterized
 * labels are read here as their raw patterns and filled in later, because the writer calls them
 * from a plain lambda that cannot be composable — and resolving them off `LocalContext` instead
 * would skip the resource system's configuration handling.
 */
@Composable
private fun rememberExportLabels(
    fromName: String,
    toName: String,
    journey: Journey?,
    legColors: List<Color?>,
): ExportLabels {
    val appName = stringResource(R.string.app_name)
    val documentName = stringResource(R.string.format_route_arrow, fromName, toName)
    val modeNames = TransportMode.entries.associateWith { stringResource(it.labelRes) }
    val board = stringResource(R.string.export_waypoint_board)
    val transfer = stringResource(R.string.export_waypoint_transfer)
    val alight = stringResource(R.string.export_waypoint_alight)
    val separator = stringResource(R.string.export_desc_separator)
    val stopsFolder = stringResource(R.string.export_folder_stops)
    val routeFolder = stringResource(R.string.export_folder_route)
    val trackPattern = stringResource(R.string.format_track_short)
    val towardsPattern = stringResource(R.string.format_towards)
    val legs = journey?.legs.orEmpty()
    return remember(documentName, modeNames, board, transfer, alight, separator, legs, legColors) {
        ExportLabels(
            documentName = documentName,
            originName = fromName,
            destinationName = toName,
            creator = "$appName ${BuildConfig.VERSION_NAME}",
            accessLegName = { leg -> modeNames[leg.mode] ?: leg.mode.name },
            board = board,
            transfer = transfer,
            alight = alight,
            descSeparator = separator,
            stopsFolder = stopsFolder,
            routeFolder = routeFolder,
            track = { track -> String.format(Locale.getDefault(), trackPattern, track) },
            towards = { headsign -> String.format(Locale.getDefault(), towardsPattern, headsign) },
            // By identity: two legs of one journey can be equal by value (the same line ridden
            // twice), and they still deserve the colour their own position earned.
            legColor = { leg ->
                legColors.getOrNull(legs.indexOfFirst { it === leg })?.toRouteHex()
            },
        )
    }
}

/** A resolved colour as the GTFS-shaped `RRGGBB` the export and the feed both speak. */
private fun Color.toRouteHex(): String = String.format(Locale.US, "%06X", toArgb() and 0xFFFFFF)
