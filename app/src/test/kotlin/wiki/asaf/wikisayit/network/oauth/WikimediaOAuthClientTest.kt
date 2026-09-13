package wiki.asaf.wikisayit.network.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WikimediaOAuthClientTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)

    private fun httpClientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    @Test
    fun `exchangeAuthorizationCode posts PKCE parameters and computes expiry from clock`() =
        runTest {
            var capturedBody: String? = null
            val engine =
                MockEngine { request ->
                    assertEquals(OAuthConfig.TOKEN_URL, request.url.toString())
                    capturedBody = String((request.body as OutgoingContent.ByteArrayContent).bytes())
                    respond(
                        content = """{"access_token":"at-1","refresh_token":"rt-1","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = WikimediaOAuthClient(httpClientWith(engine), fixedClock)

            val tokens = client.exchangeAuthorizationCode(code = "auth-code", codeVerifier = "verifier-1")

            assertEquals("at-1", tokens.accessToken)
            assertEquals("rt-1", tokens.refreshToken)
            assertEquals(fixedClock.millis() + 3600_000L, tokens.expiresAtEpochMillis)
            val body = capturedBody!!
            assertEquals(true, body.contains("grant_type=authorization_code"))
            assertEquals(true, body.contains("code=auth-code"))
            assertEquals(true, body.contains("code_verifier=verifier-1"))
            assertEquals(true, body.contains("client_id=${OAuthConfig.CLIENT_ID}"))
        }

    @Test
    fun `refresh posts refresh_token grant`() =
        runTest {
            var capturedBody: String? = null
            val engine =
                MockEngine { request ->
                    capturedBody = String((request.body as OutgoingContent.ByteArrayContent).bytes())
                    respond(
                        content = """{"access_token":"at-2","expires_in":100}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = WikimediaOAuthClient(httpClientWith(engine), fixedClock)

            val tokens = client.refresh(refreshToken = "old-refresh")

            assertEquals("at-2", tokens.accessToken)
            assertNull(tokens.refreshToken)
            val body = capturedBody!!
            assertEquals(true, body.contains("grant_type=refresh_token"))
            assertEquals(true, body.contains("refresh_token=old-refresh"))
        }

    @Test
    fun `fetchAuthenticatedUsername sends bearer token and returns username`() =
        runTest {
            var capturedAuthHeader: String? = null
            val engine =
                MockEngine { request ->
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    assertEquals(OAuthConfig.PROFILE_URL, request.url.toString())
                    respond(
                        content = """{"username":"Ijon Tichy"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = WikimediaOAuthClient(httpClientWith(engine), fixedClock)

            val username = client.fetchAuthenticatedUsername("at-1")

            assertEquals("Ijon Tichy", username)
            assertEquals("Bearer at-1", capturedAuthHeader)
        }

    @Test
    fun `error response throws OAuthException with parsed error fields`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"invalid_grant","error_description":"code expired"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = WikimediaOAuthClient(httpClientWith(engine), fixedClock)

            try {
                client.exchangeAuthorizationCode(code = "expired", codeVerifier = "verifier-1")
                fail("Expected OAuthException to be thrown")
            } catch (exception: OAuthException) {
                assertEquals("invalid_grant", exception.errorCode)
                assertEquals("code expired", exception.errorDescription)
            }
        }
}
