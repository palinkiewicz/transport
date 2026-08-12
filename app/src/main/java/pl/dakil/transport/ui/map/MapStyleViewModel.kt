package pl.dakil.transport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.data.repo.MapStyleRepository
import pl.dakil.transport.domain.model.MapTheme

/**
 * The patched basemap style, and which of the two colourways it is — for every screen that
 * embeds a map ([MapScreen] and [RouteMap] alike), so the choice is resolved in one place.
 *
 * Whether the map is dark is a question only Compose can answer for [MapTheme.SYSTEM], so the
 * screen reports the device's setting in and everything else is derived from it here. Both flows
 * stay null until that report arrives: emitting a light style first and swapping it a frame later
 * would flash a white map at someone whose phone is in dark mode.
 */
@HiltViewModel
class MapStyleViewModel @Inject constructor(
    private val mapStyleRepository: MapStyleRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val systemInDarkTheme = MutableStateFlow<Boolean?>(null)

    /** Whether the map is currently drawn dark; null until the system setting has been reported. */
    val darkMap: StateFlow<Boolean?> = combine(
        settingsRepository.settings.map { it.mapTheme },
        systemInDarkTheme.filterNotNull(),
    ) { theme, systemDark -> theme.isDark(systemDark) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), null)

    /** Patched style JSON; null only while the asset is being read. */
    val styleJson: StateFlow<String?> = darkMap.filterNotNull()
        .map { dark -> mapStyleRepository.transitFreeGmapsStyle(dark) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), null)

    /** Reported by the screen from `isSystemInDarkTheme()`, which only a composable can read. */
    fun setSystemInDarkTheme(dark: Boolean) {
        systemInDarkTheme.value = dark
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
