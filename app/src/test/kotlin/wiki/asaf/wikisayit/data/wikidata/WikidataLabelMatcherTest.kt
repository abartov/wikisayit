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
            val results = matcher.search("Jerusalem", WbEntityType.ITEM, "en")

            assertEquals(1, results.size)
            assertEquals("Q1218", results[0].id)
            assertEquals("Jerusalem", results[0].label)
            assertEquals("capital city", results[0].description)
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
            val results = matcher.search("kestrel", WbEntityType.LEXEME, "en")

            assertEquals(2, results.size)
            assertTrue(results.any { it.id == "L8842" })
            assertTrue(results.any { it.id == "L44120" })
        }

    @Test
    fun `no hits returns empty list`() =
        runTest {
            val matcher = matcherFor("""{"search":[]}""")
            val results = matcher.search("zzzznotaword", WbEntityType.ITEM, "en")

            assertEquals(0, results.size)
        }

    @Test
    fun `network failure returns empty list`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val httpClient =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val matcher = WikidataLabelMatcher(WikimediaClients(httpClient, NoAuthTokenProvider))

            val results = matcher.search("water", WbEntityType.LEXEME, "en")

            assertEquals(0, results.size)
        }
}
