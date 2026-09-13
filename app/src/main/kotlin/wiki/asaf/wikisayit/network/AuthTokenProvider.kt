package wiki.asaf.wikisayit.network

/**
 * Supplies the bearer token to attach to authenticated MediaWiki REST API requests.
 *
 * The actual OAuth 2.0 login/refresh flow is implemented by the authentication epic;
 * this seam lets the shared networking layer stay agnostic of how the token was obtained.
 */
interface AuthTokenProvider {
    /** Returns the current access token, or null to make the request unauthenticated. */
    suspend fun currentAccessToken(): String?
}

/** Default provider for anonymous, read-only access (no Authorization header is sent). */
object NoAuthTokenProvider : AuthTokenProvider {
    override suspend fun currentAccessToken(): String? = null
}
