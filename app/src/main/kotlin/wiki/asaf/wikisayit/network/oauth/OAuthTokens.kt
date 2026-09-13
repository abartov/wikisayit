package wiki.asaf.wikisayit.network.oauth

/** Tokens returned by a successful [WikimediaOAuthClient] exchange or refresh call. */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
)
