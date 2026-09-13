package wiki.asaf.wikisayit.network.oauth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wiki.asaf.wikisayit.data.auth.StoredOAuthTokens
import wiki.asaf.wikisayit.data.auth.TokenStore
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OAuthLoginResult {
    data object Success : OAuthLoginResult

    data class Failure(val message: String) : OAuthLoginResult
}

private data class PendingAuthorization(val codeVerifier: String, val state: String)

/**
 * Drives the OAuth 2.0 + PKCE login flow: [startAuthorization] returns the URL the UI opens in
 * a Custom Tab (the consent sheet itself is `meta.wikimedia.org`, not app UI); [handleRedirect]
 * takes the redirect URL [wiki.asaf.wikisayit.MainActivity] receives back via its
 * [OAuthConfig.REDIRECT_SCHEME] intent-filter and completes the token exchange.
 *
 * The in-flight [PendingAuthorization] lives in memory only, not persisted — if the OS kills the
 * process mid-flow the user just retries; the code_verifier is short-lived secret material, not
 * worth the complexity of surviving process death for that rare case.
 */
@Singleton
class OAuthLoginController
    @Inject
    constructor(
        private val oAuthClient: WikimediaOAuthClient,
        private val tokenStore: TokenStore,
    ) {
        private val mutex = Mutex()
        private var pending: PendingAuthorization? = null

        private val _results = MutableSharedFlow<OAuthLoginResult>(extraBufferCapacity = 1)
        val results: SharedFlow<OAuthLoginResult> = _results.asSharedFlow()

        suspend fun startAuthorization(): String {
            val codeVerifier = Pkce.generateCodeVerifier()
            val state = Pkce.generateState()
            mutex.withLock { pending = PendingAuthorization(codeVerifier, state) }
            return buildAuthorizeUrl(codeChallenge = Pkce.deriveCodeChallenge(codeVerifier), state = state)
        }

        suspend fun handleRedirect(redirectUrl: String) {
            val outcome = runCatching { completeLogin(redirectUrl) }
            _results.emit(
                outcome.fold(
                    onSuccess = { OAuthLoginResult.Success },
                    onFailure = { OAuthLoginResult.Failure(it.message ?: "Sign-in failed") },
                ),
            )
        }

        private suspend fun completeLogin(redirectUrl: String) {
            val params = parseQueryParameters(redirectUrl)
            params["error"]?.let { throw OAuthException(it, params["error_description"]) }
            val code = params["code"] ?: throw OAuthException("missing_code", "No authorization code in redirect")

            val authorization = mutex.withLock { pending?.also { pending = null } }
            requireNotNull(authorization) { "No sign-in was in progress" }
            require(authorization.state == params["state"]) { "OAuth state did not match" }

            val tokens = oAuthClient.exchangeAuthorizationCode(code, authorization.codeVerifier)
            val username = oAuthClient.fetchAuthenticatedUsername(tokens.accessToken)
            tokenStore.save(
                StoredOAuthTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAtEpochMillis = tokens.expiresAtEpochMillis,
                    username = username,
                ),
            )
        }

        private fun buildAuthorizeUrl(
            codeChallenge: String,
            state: String,
        ): String {
            val params =
                linkedMapOf(
                    "response_type" to "code",
                    "client_id" to OAuthConfig.CLIENT_ID,
                    "redirect_uri" to OAuthConfig.REDIRECT_URI,
                    "code_challenge" to codeChallenge,
                    "code_challenge_method" to "S256",
                    "state" to state,
                )
            val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
            return "${OAuthConfig.AUTHORIZE_URL}?$query"
        }

        private fun parseQueryParameters(url: String): Map<String, String> {
            val queryStart = url.indexOf('?')
            if (queryStart == -1) return emptyMap()
            return url.substring(queryStart + 1)
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { pair ->
                    val parts = pair.split('=', limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
                }
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
    }
