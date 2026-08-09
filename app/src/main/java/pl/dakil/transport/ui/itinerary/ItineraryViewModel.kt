package pl.dakil.transport.ui.itinerary

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.transport.data.export.ExportLabels
import pl.dakil.transport.data.export.exportFileName
import pl.dakil.transport.data.export.writeExport
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.repo.SavedItineraryRepository
import pl.dakil.transport.domain.model.ExportFormat
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.domain.model.TransitLocation

/**
 * The itinerary screen is otherwise stateless — it is handed its [Journey] by the results graph's
 * ViewModel. This exists to feed it the display settings and to turn a journey into an exported
 * file — GPX, KMZ or GeoJSON, whichever the share menu asked for.
 */
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedItineraryRepository: SavedItineraryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Ids of every pinned journey. The screen compares against it rather than being told a
     * boolean, because the results loop hands it a new [Journey] object on every refresh and the
     * identity has to be recomputed from that journey each time.
     */
    val savedIds: StateFlow<Set<String>> = savedItineraryRepository.itineraries
        .map { saved -> saved.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleSaved(from: TransitLocation, to: TransitLocation, journey: Journey) {
        viewModelScope.launch {
            val id = SavedItinerary.idFor(from, to, journey)
            if (id in savedIds.value) {
                savedItineraryRepository.delete(id)
            } else {
                savedItineraryRepository.save(from, to, journey)
            }
        }
    }

    /** Whether the itinerary map labels the stops where you board and alight. */
    val showStopNames: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showItineraryStopNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** What an exported file contains and how it leaves the app. */
    val export: StateFlow<ExportSettings> = settingsRepository.settings
        .map { it.export }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportSettings.DEFAULT)

    /** The name to suggest for [journey]'s file, per the current settings and chosen [format]. */
    fun fileNameFor(
        journey: Journey,
        format: ExportFormat,
        fromName: String,
        toName: String,
    ): String = exportFileName(journey, export.value.fileName, format, fromName, toName)

    /**
     * Writes [journey] into the app's cache and returns a content uri for it. The directory is
     * emptied first: an export is a hand-off, not an archive, and stale files would otherwise
     * accumulate where nothing ever cleans them up.
     */
    suspend fun exportToCache(
        journey: Journey,
        format: ExportFormat,
        fileName: String,
        labels: ExportLabels,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, EXPORTS_DIRECTORY)
            directory.deleteRecursively()
            directory.mkdirs()
            val file = File(directory, fileName)
            file.outputStream().use { writeExport(it, format, journey, export.value, labels) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /** Writes [journey] to a destination the user picked through the document picker. */
    suspend fun exportTo(
        uri: Uri,
        format: ExportFormat,
        journey: Journey,
        labels: ExportLabels,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open $uri for writing")
            stream.use { writeExport(it, format, journey, export.value, labels) }
        }
    }

    private companion object {
        /** Must match the `cache-path` in `res/xml/file_paths.xml`. */
        const val EXPORTS_DIRECTORY = "exports"
    }
}
