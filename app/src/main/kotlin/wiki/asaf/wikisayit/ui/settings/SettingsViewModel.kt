package wiki.asaf.wikisayit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.local.settings.AppSettings
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

        fun setAutoUseLastProfile(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAutoUseLastProfile(enabled) }
        }

        fun setTrimSilenceAutomatically(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setTrimSilenceAutomatically(enabled) }
        }

        fun setSilenceThresholdSeconds(seconds: Float) {
            viewModelScope.launch { settingsRepository.setSilenceThresholdSeconds(seconds) }
        }

        fun setInterfaceLanguageTag(tag: String) {
            viewModelScope.launch { settingsRepository.setInterfaceLanguageTag(tag) }
        }

        fun setMaxListSize(size: Int) {
            viewModelScope.launch { settingsRepository.setMaxListSize(size) }
        }
    }
