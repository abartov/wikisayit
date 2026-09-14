package wiki.asaf.wikisayit.ui.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.auth.TokenStore
import wiki.asaf.wikisayit.network.oauth.OAuthLoginController
import wiki.asaf.wikisayit.network.oauth.OAuthLoginResult
import wiki.asaf.wikisayit.ui.navigation.WikiSayItRoute
import javax.inject.Inject

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val authorizeUrl: String? = null,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
    val justLoggedOut: Boolean = false,
)

/**
 * Backs [SignInScreen] (`2a`): kicks off the OAuth 2.0 + PKCE flow via [OAuthLoginController].
 * Also skips straight past this screen (s-nrb) when [tokenStore] already holds a token from a
 * previous session — [wiki.asaf.wikisayit.network.oauth.OAuthAuthTokenProvider] refreshes it
 * transparently later, so its mere presence is enough to consider the user still signed in.
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val oAuthLoginController: OAuthLoginController,
        private val tokenStore: TokenStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SignInUiState(justLoggedOut = savedStateHandle.toRoute<WikiSayItRoute.SignIn>().justLoggedOut),
            )
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
            viewModelScope.launch {
                if (tokenStore.tokens.first() != null) {
                    _uiState.update { it.copy(signedIn = true) }
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
