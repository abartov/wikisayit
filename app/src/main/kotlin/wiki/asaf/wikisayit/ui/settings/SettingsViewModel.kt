package wiki.asaf.wikisayit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.auth.TokenStore
import wiki.asaf.wikisayit.data.local.settings.AppSettings
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

        val signedInUsername: StateFlow<String?> =
            tokenStore.tokens.map { it?.username }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        /** Forgets the stored OAuth token (s-nrb); the caller navigates back to sign-in once this completes. */
        suspend fun logOut() {
            tokenStore.clear()
        }

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
