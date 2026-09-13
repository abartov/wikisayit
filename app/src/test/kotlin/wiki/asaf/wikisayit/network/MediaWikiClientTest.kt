package wiki.asaf.wikisayit.network

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@Serializable
private data class Widget(val id: Int, val name: String)

private class FixedTokenProvider(private val token: String?) : AuthTokenProvider {
    override suspend fun currentAccessToken(): String? = token
}

class MediaWikiClientTest {
    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    @Test
    fun `get sends authorization header and decodes success body`() =
        runTest {
            var capturedAuthHeader: String? = null
            val engine =
                MockEngine { request ->
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    assertEquals("https://commons.wikimedia.org/w/rest.php/v1/widgets/42", request.url.toString())
                    respond(
                        content = """{"id":42,"name":"gear"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = MediaWikiClient(MediaWikiSite.COMMONS, clientWith(engine), FixedTokenProvider("secret-token"))

            val widget: Widget = client.get("v1/widgets/42")

            assertEquals(Widget(42, "gear"), widget)
            assertEquals("Bearer secret-token", capturedAuthHeader)
        }

    @Test
    fun `get omits authorization header when unauthenticated`() =
        runTest {
            var capturedAuthHeader: String? = null
            val engine =
                MockEngine { request ->
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"id":1,"name":"anon"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = MediaWikiClient(MediaWikiSite.WIKIDATA, clientWith(engine), NoAuthTokenProvider)

            client.get<Widget>("v1/widgets/1")

            assertNull(capturedAuthHeader)
        }

    @Test
    fun `non-2xx response throws MediaWikiApiException with parsed error fields`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"httpCode":404,"httpReason":"Not Found","errorKey":"rest-nonexistent-title"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = MediaWikiClient(MediaWikiSite.COMMONS, clientWith(engine), NoAuthTokenProvider)

            try {
                client.get<Widget>("v1/widgets/missing")
                fail("Expected MediaWikiApiException to be thrown")
            } catch (exception: MediaWikiApiException) {
                assertEquals(404, exception.statusCode)
                assertEquals("rest-nonexistent-title", exception.errorKey)
                assertEquals("Not Found", exception.errorMessage)
            }
        }

    @Test
    fun `fetchCsrfToken parses the token out of a tokens response`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("query", request.url.parameters["action"])
                    assertEquals("csrf", request.url.parameters["type"])
                    respond(
                        content = """{"query":{"tokens":{"csrftoken":"abc123+\\"}}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = MediaWikiClient(MediaWikiSite.COMMONS, clientWith(engine), NoAuthTokenProvider)

            assertEquals("abc123+\\", client.fetchCsrfToken())
        }

    @Test
    fun `post sends json body to resolved url`() =
        runTest {
            var capturedBody: String? = null
            val engine =
                MockEngine { request ->
                    assertEquals(
                        "https://www.wikidata.org/w/rest.php/v1/widgets",
                        request.url.toString(),
                    )
                    capturedBody = String((request.body as OutgoingContent.ByteArrayContent).bytes())
                    respond(
                        content = """{"id":7,"name":"created"}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = MediaWikiClient(MediaWikiSite.WIKIDATA, clientWith(engine), NoAuthTokenProvider)

            val created: Widget = client.post("v1/widgets", Widget(id = 0, name = "created"))

            assertEquals(Widget(7, "created"), created)
            assertTrue(capturedBody!!.contains("\"name\":\"created\""))
        }
}
