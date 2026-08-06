package pl.dakil.transport.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.ui.components.LineColorSettings
import pl.dakil.transport.ui.components.parseRouteColor

/**
 * Feeds the line-colour preference to every list screen through
 * [pl.dakil.transport.ui.components.LocalLineColorSettings].
 *
 * Live, unlike [StartDestinationViewModel]'s one-shot read: a colour the user just picked should
 * be on the board by the time they get back from Settings.
 */
@HiltViewModel
class LineColorSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val lineColors: StateFlow<LineColorSettings> = settingsRepository.settings
        .map { settings ->
            LineColorSettings(
                mode = settings.lineColorMode,
                palette = settings.palette.map { parseRouteColor(it, Color.Gray) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LineColorSettings.DEFAULT)
}
