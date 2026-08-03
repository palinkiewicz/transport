package pl.dakil.transport.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.VehicleMotionSettings

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Held locally (seeded from disk once) rather than read straight from the repository flow,
    // so dragging a slider never races the DataStore write round-trip — same reasoning as the
    // map filter menu.
    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings

    init {
        viewModelScope.launch {
            _settings.value = settingsRepository.settings.first()
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        viewModelScope.launch { settingsRepository.save(updated) }
    }

    fun updateMotion(transform: (VehicleMotionSettings) -> VehicleMotionSettings) =
        update { it.copy(vehicleMotion = transform(it.vehicleMotion)) }

    fun resetAll() = update { AppSettings.DEFAULT }

    fun resetMotion() = update { it.copy(vehicleMotion = VehicleMotionSettings.DEFAULT) }
}
