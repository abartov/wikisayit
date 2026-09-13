package wiki.asaf.wikisayit.network.oauth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wiki.asaf.wikisayit.data.auth.StoredOAuthTokens
import wiki.asaf.wikisayit.data.auth.TokenStore
import wiki.asaf.wikisayit.network.AuthTokenProvider
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current OAuth access token to [wiki.asaf.wikisayit.network.MediaWikiClient],
 * transparently refreshing it via [WikimediaOAuthClient] when it's within [REFRESH_SKEW_MILLIS]
 * of expiry. Returns null (unauthenticated) if the user hasn't signed in, or a refresh fails.
 */
@Singleton
class OAuthAuthTokenProvider
    @Inject
    constructor(
        private val tokenStore: TokenStore,
        private val oAuthClient: WikimediaOAuthClient,
        private val clock: Clock,
    ) : AuthTokenProvider {
        private val mutex = Mutex()

        override suspend fun currentAccessToken(): String? =
            mutex.withLock {
                val stored = tokenStore.tokens.first() ?: return@withLock null
                if (clock.millis() < stored.expiresAtEpochMillis - REFRESH_SKEW_MILLIS) {
                    stored.accessToken
                } else {
                    refreshAndSave(stored)
                }
            }

        private suspend fun refreshAndSave(stored: StoredOAuthTokens): String? {
            val refreshToken = stored.refreshToken ?: return null
            return runCatching { oAuthClient.refresh(refreshToken) }
                .onSuccess { refreshed ->
                    tokenStore.save(
                        stored.copy(
                            accessToken = refreshed.accessToken,
                            refreshToken = refreshed.refreshToken ?: refreshToken,
                            expiresAtEpochMillis = refreshed.expiresAtEpochMillis,
                        ),
                    )
                }
                .map { it.accessToken }
                .getOrNull()
        }

        private companion object {
            const val REFRESH_SKEW_MILLIS = 60_000L
        }
    }
