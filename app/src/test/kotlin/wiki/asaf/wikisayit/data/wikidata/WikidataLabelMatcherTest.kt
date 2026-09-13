package wiki.asaf.wikisayit.data.wikidata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients

class WikidataLabelMatcherTest {
    private fun matcherFor(responseBody: String): WikidataLabelMatcher {
        val engine =
            MockEngine { request ->
                assertEquals("https://www.wikidata.org/w/api.php", request.url.toString().substringBefore('?'))
                assertEquals("wbsearchentities", request.url.parameters["action"])
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return WikidataLabelMatcher(WikimediaClients(httpClient, NoAuthTokenProvider))
    }

    @Test
    fun `single hit is returned`() =
        runTest {
            val matcher =
                matcherFor(
                    """{"search":[{"id":"Q1218","label":"Jerusalem","description":"capital city"}]}""",
                )
            val result = matcher.search("Jerusalem", WbEntityType.ITEM, "en")

            assertEquals(1, result.candidates.size)
            assertEquals("Q1218", result.candidates[0].id)
            assertEquals("Jerusalem", result.candidates[0].label)
            assertEquals("capital city", result.candidates[0].description)
            assertFalse(result.hadError)
        }

    @Test
    fun `multiple hits are all returned`() =
        runTest {
            val matcher =
                matcherFor(
                    """
                    {"search":[
                        {"id":"L8842","label":"kestrel","description":"small falcon"},
                        {"id":"L44120","label":"Kestrel","description":"aircraft engine"}
                    ]}
                    """.trimIndent(),
                )
            val result = matcher.search("kestrel", WbEntityType.LEXEME, "en")

            assertEquals(2, result.candidates.size)
            assertTrue(result.candidates.any { it.id == "L8842" })
            assertTrue(result.candidates.any { it.id == "L44120" })
            assertFalse(result.hadError)
        }

    @Test
    fun `no hits returns empty list without an error`() =
        runTest {
            val matcher = matcherFor("""{"search":[]}""")
            val result = matcher.search("zzzznotaword", WbEntityType.ITEM, "en")

            assertEquals(0, result.candidates.size)
            assertFalse(result.hadError)
        }

    @Test
    fun `network failure returns empty list flagged as an error`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val httpClient =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val matcher = WikidataLabelMatcher(WikimediaClients(httpClient, NoAuthTokenProvider))

            val result = matcher.search("water", WbEntityType.LEXEME, "en")

            assertEquals(0, result.candidates.size)
            assertTrue(result.hadError)
        }
}
