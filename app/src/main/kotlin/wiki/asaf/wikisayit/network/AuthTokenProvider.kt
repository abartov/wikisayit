package wiki.asaf.wikisayit.network

/**
 * Supplies the bearer token to attach to authenticated MediaWiki REST API requests.
 *
 * The production binding is [wiki.asaf.wikisayit.network.oauth.OAuthAuthTokenProvider]; this
 * seam lets the shared networking layer stay agnostic of how the token was obtained (and lets
 * tests substitute [NoAuthTokenProvider] or a fake).
 */
interface AuthTokenProvider {
    /** Returns the current access token, or null to make the request unauthenticated. */
    suspend fun currentAccessToken(): String?
}

/** Default provider for anonymous, read-only access (no Authorization header is sent). */
object NoAuthTokenProvider : AuthTokenProvider {
    override suspend fun currentAccessToken(): String? = null
}
