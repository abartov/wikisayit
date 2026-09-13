package wiki.asaf.wikisayit.network.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.data.auth.StoredOAuthTokens
import wiki.asaf.wikisayit.data.auth.TokenStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeTokenStore : TokenStore {
    val state = MutableStateFlow<StoredOAuthTokens?>(null)
    override val tokens: Flow<StoredOAuthTokens?> = state

    override suspend fun save(tokens: StoredOAuthTokens) {
        state.value = tokens
    }

    override suspend fun clear() {
        state.value = null
    }
}

class OAuthLoginControllerTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)

    private fun oAuthClientRespondingWith(username: String = "Ijon"): WikimediaOAuthClient {
        val engine =
            MockEngine { request ->
                if (request.url.toString() == OAuthConfig.TOKEN_URL) {
                    respond(
                        content = """{"access_token":"at-1","refresh_token":"rt-1","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        content = """{"username":"$username"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return WikimediaOAuthClient(httpClient, fixedClock)
    }

    private fun stateFrom(authorizeUrl: String) = Regex("state=([^&]+)").find(authorizeUrl)!!.groupValues[1]

    @Test
    fun `startAuthorization builds a PKCE authorize url`() =
        runTest {
            val controller = OAuthLoginController(oAuthClientRespondingWith(), FakeTokenStore())

            val url = controller.startAuthorization()

            assertTrue(url.startsWith("${OAuthConfig.AUTHORIZE_URL}?"))
            assertTrue(url.contains("response_type=code"))
            assertTrue(url.contains("client_id=${OAuthConfig.CLIENT_ID}"))
            assertTrue(url.contains("code_challenge_method=S256"))
        }

    @Test
    fun `handleRedirect completes login and stores tokens on matching state`() =
        runTest {
            val tokenStore = FakeTokenStore()
            val controller = OAuthLoginController(oAuthClientRespondingWith(username = "Ijon Tichy"), tokenStore)
            val state = stateFrom(controller.startAuthorization())

            controller.handleRedirect("${OAuthConfig.REDIRECT_URI}?code=auth-code&state=$state")

            assertEquals("Ijon Tichy", tokenStore.state.value?.username)
            assertEquals("at-1", tokenStore.state.value?.accessToken)
        }

    @Test
    fun `handleRedirect rejects a mismatched state and does not store tokens`() =
        runTest {
            val tokenStore = FakeTokenStore()
            val controller = OAuthLoginController(oAuthClientRespondingWith(), tokenStore)
            controller.startAuthorization()

            controller.handleRedirect("${OAuthConfig.REDIRECT_URI}?code=auth-code&state=wrong-state")

            assertNull(tokenStore.state.value)
        }

    @Test
    fun `handleRedirect surfaces an error query parameter as a failure`() =
        runTest {
            val tokenStore = FakeTokenStore()
            val controller = OAuthLoginController(oAuthClientRespondingWith(), tokenStore)
            controller.startAuthorization()

            controller.handleRedirect(
                "${OAuthConfig.REDIRECT_URI}?error=access_denied&error_description=User+cancelled",
            )

            assertNull(tokenStore.state.value)
        }

    @Test
    fun `results emits success after a completed login`() =
        runTest {
            val controller = OAuthLoginController(oAuthClientRespondingWith(), FakeTokenStore())
            val state = stateFrom(controller.startAuthorization())

            var result: OAuthLoginResult? = null
            val collector = launch(Dispatchers.Unconfined) { result = controller.results.first() }

            controller.handleRedirect("${OAuthConfig.REDIRECT_URI}?code=auth-code&state=$state")
            collector.join()

            assertTrue(result is OAuthLoginResult.Success)
        }
}
