package wiki.asaf.wikisayit.network.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import wiki.asaf.wikisayit.data.auth.StoredOAuthTokens
import wiki.asaf.wikisayit.data.auth.TokenStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeTokenStoreWithInitial(initial: StoredOAuthTokens?) : TokenStore {
    val state = MutableStateFlow(initial)
    override val tokens: Flow<StoredOAuthTokens?> = state

    override suspend fun save(tokens: StoredOAuthTokens) {
        state.value = tokens
    }

    override suspend fun clear() {
        state.value = null
    }
}

class OAuthAuthTokenProviderTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)

    private fun oAuthClientRefreshingTo(accessToken: String): WikimediaOAuthClient {
        val engine =
            MockEngine {
                respond(
                    content = """{"access_token":"$accessToken","refresh_token":"rt-new","expires_in":3600}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return WikimediaOAuthClient(httpClient, fixedClock)
    }

    @Test
    fun `returns null when signed out`() =
        runTest {
            val provider =
                OAuthAuthTokenProvider(FakeTokenStoreWithInitial(null), oAuthClientRefreshingTo("unused"), fixedClock)

            assertNull(provider.currentAccessToken())
        }

    @Test
    fun `returns the stored access token when still fresh`() =
        runTest {
            val stored =
                StoredOAuthTokens(
                    accessToken = "at-fresh",
                    refreshToken = "rt-1",
                    expiresAtEpochMillis = fixedClock.millis() + 3_600_000,
                    username = "Ijon",
                )
            val provider =
                OAuthAuthTokenProvider(FakeTokenStoreWithInitial(stored), oAuthClientRefreshingTo("unused"), fixedClock)

            assertEquals("at-fresh", provider.currentAccessToken())
        }

    @Test
    fun `refreshes and persists a token that is at or past its expiry skew`() =
        runTest {
            val stored =
                StoredOAuthTokens(
                    accessToken = "at-stale",
                    refreshToken = "rt-1",
                    expiresAtEpochMillis = fixedClock.millis() + 1_000,
                    username = "Ijon",
                )
            val tokenStore = FakeTokenStoreWithInitial(stored)
            val provider = OAuthAuthTokenProvider(tokenStore, oAuthClientRefreshingTo("at-refreshed"), fixedClock)

            val token = provider.currentAccessToken()

            assertEquals("at-refreshed", token)
            assertEquals("at-refreshed", tokenStore.state.value?.accessToken)
            assertEquals("rt-new", tokenStore.state.value?.refreshToken)
        }

    @Test
    fun `returns null when a stale token has no refresh token`() =
        runTest {
            val stored =
                StoredOAuthTokens(
                    accessToken = "at-stale",
                    refreshToken = null,
                    expiresAtEpochMillis = fixedClock.millis(),
                    username = "Ijon",
                )
            val provider =
                OAuthAuthTokenProvider(FakeTokenStoreWithInitial(stored), oAuthClientRefreshingTo("unused"), fixedClock)

            assertNull(provider.currentAccessToken())
        }
}
