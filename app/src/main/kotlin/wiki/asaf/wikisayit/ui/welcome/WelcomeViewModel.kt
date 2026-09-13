package wiki.asaf.wikisayit.ui.welcome

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class WelcomeUiState(val getStartedTapCount: Int = 0)

/**
 * Reference implementation of the state-holder pattern every screen ViewModel follows:
 * a private mutable state, exposed read-only as [StateFlow], mutated only through
 * explicit event-handling functions (never from the UI directly).
 */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow(WelcomeUiState())
        val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

        fun onGetStartedClicked() {
            _uiState.update { it.copy(getStartedTapCount = it.getStartedTapCount + 1) }
        }
    }
