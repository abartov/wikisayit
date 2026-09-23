package wiki.asaf.wikisayit.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.auth.TokenStore
import wiki.asaf.wikisayit.data.recovery.DEFAULT_RECOVERY_UPLOADS
import wiki.asaf.wikisayit.data.recovery.MAX_RECOVERY_UPLOADS
import wiki.asaf.wikisayit.data.recovery.RecoveryProgress
import wiki.asaf.wikisayit.data.recovery.RecoveryReport
import wiki.asaf.wikisayit.data.recovery.UploadRecovery
import javax.inject.Inject

enum class RecoveryStage { SETUP, RUNNING, DONE, FAILED }

data class RecoveryUiState(
    val stage: RecoveryStage = RecoveryStage.SETUP,
    /** Kept as typed text so the field can be briefly empty while editing. */
    val limitText: String = DEFAULT_RECOVERY_UPLOADS.toString(),
    val progress: RecoveryProgress? = null,
    val report: RecoveryReport? = null,
    val errorMessage: String? = null,
) {
    val limit: Int? get() = limitText.toIntOrNull()?.takeIf { it in 1..MAX_RECOVERY_UPLOADS }
}

/** Backs recovery mode (s-7f3): re-linking past Wiki-Say-It! uploads that never got their P443. */
@HiltViewModel
class RecoveryViewModel
    @Inject
    constructor(
        private val uploadRecovery: UploadRecovery,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecoveryUiState())
        val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

        fun updateLimitText(text: String) {
            _uiState.update { it.copy(limitText = text.filter(Char::isDigit).take(3)) }
        }

        fun start() {
            val limit = _uiState.value.limit ?: return
            if (_uiState.value.stage == RecoveryStage.RUNNING) return
            _uiState.update {
                it.copy(
                    stage = RecoveryStage.RUNNING,
                    progress = null,
                    report = null,
                    errorMessage = null,
                )
            }
            viewModelScope.launch {
                runCatching {
                    val username = requireNotNull(tokenStore.tokens.first()?.username) { "Not signed in" }
                    uploadRecovery.run(username, limit) { progress -> _uiState.update { it.copy(progress = progress) } }
                }.fold(
                    onSuccess = { report -> _uiState.update { it.copy(stage = RecoveryStage.DONE, report = report) } },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(stage = RecoveryStage.FAILED, errorMessage = error.message ?: error.toString())
                        }
                    },
                )
            }
        }

        fun reset() {
            _uiState.update { RecoveryUiState(limitText = it.limitText) }
        }
    }
