package wiki.asaf.wikisayit.data.auth

import kotlinx.coroutines.flow.Flow

/** The signed-in user's OAuth identity and tokens, as persisted by [TokenStore]. */
data class StoredOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
    val username: String,
)

interface TokenStore {
    val tokens: Flow<StoredOAuthTokens?>

    suspend fun save(tokens: StoredOAuthTokens)

    suspend fun clear()
}
