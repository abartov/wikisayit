package wiki.asaf.wikisayit.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.network.oauth.OAuthLoginController
import wiki.asaf.wikisayit.network.oauth.OAuthLoginResult
import javax.inject.Inject

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val authorizeUrl: String? = null,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
)

/** Backs [SignInScreen] (`2a`): kicks off the OAuth 2.0 + PKCE flow via [OAuthLoginController]. */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val oAuthLoginController: OAuthLoginController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignInUiState())
        val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                oAuthLoginController.results.collect { result ->
                    _uiState.update {
                        when (result) {
                            OAuthLoginResult.Success ->
                                it.copy(
                                    isSigningIn = false,
                                    signedIn = true,
                                    errorMessage = null,
                                )
                            is OAuthLoginResult.Failure -> it.copy(isSigningIn = false, errorMessage = result.message)
                        }
                    }
                }
            }
        }

        fun onSignInClicked() {
            _uiState.update { it.copy(isSigningIn = true, errorMessage = null) }
            viewModelScope.launch {
                val url = oAuthLoginController.startAuthorization()
                _uiState.update { it.copy(authorizeUrl = url) }
            }
        }

        fun onAuthorizeUrlConsumed() {
            _uiState.update { it.copy(authorizeUrl = null) }
        }

        /**
         * Called when [SignInScreen] resumes (e.g. the user closed the Custom Tab without
         * finishing sign-in). Gives an in-flight redirect a grace period to land — the
         * [android.content.Intent] delivery that follows a real redirect resumes this screen
         * too — before treating the attempt as cancelled and re-enabling the button.
         */
        fun onResumed() {
            if (!_uiState.value.isSigningIn) return
            viewModelScope.launch {
                delay(RESUME_GRACE_PERIOD_MILLIS)
                _uiState.update { if (it.isSigningIn && !it.signedIn) it.copy(isSigningIn = false) else it }
            }
        }

        private companion object {
            const val RESUME_GRACE_PERIOD_MILLIS = 1_500L
        }
    }
