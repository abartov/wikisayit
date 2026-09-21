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
import wiki.asaf.wikisayit.ui.session.EntryKind

class WikidataSparqlClientTest {
    private fun clientFor(
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): WikidataSparqlClient {
        val engine =
            MockEngine { request ->
                assertEquals("https://query.wikidata.org/sparql", request.url.toString().substringBefore('?'))
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/sparql-results+json"),
                )
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return WikidataSparqlClient(httpClient)
    }

    @Test
    fun `item bindings resolve to item refs`() =
        runTest {
            val client =
                clientFor(
                    """
                    {"results":{"bindings":[
                        {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q42"}}
                    ]}}
                    """.trimIndent(),
                )
            val result = client.execute("SELECT ?item WHERE {}")

            assertEquals(1, result.refs.size)
            assertEquals(SparqlEntityRef("Q42", EntryKind.ITEM), result.refs[0])
            assertFalse(result.hadError)
        }

    @Test
    fun `lexeme bindings resolve to form-kind refs`() =
        runTest {
            val client =
                clientFor(
                    """
                    {"results":{"bindings":[
                        {"lexeme":{"type":"uri","value":"http://www.wikidata.org/entity/L2"}}
                    ]}}
                    """.trimIndent(),
                )
            val result = client.execute("SELECT ?lexeme WHERE {}")

            assertEquals(1, result.refs.size)
            assertEquals(SparqlEntityRef("L2", EntryKind.FORM), result.refs[0])
            assertFalse(result.hadError)
        }

    @Test
    fun `rows without item or lexeme bindings are skipped without an error`() =
        runTest {
            val client =
                clientFor(
                    """
                    {"results":{"bindings":[
                        {"itemLabel":{"type":"literal","value":"Douglas Adams"}}
                    ]}}
                    """.trimIndent(),
                )
            val result = client.execute("SELECT ?itemLabel WHERE {}")

            assertTrue(result.refs.isEmpty())
            assertFalse(result.hadError)
        }

    @Test
    fun `http failure yields empty list flagged as an error`() =
        runTest {
            val client = clientFor("""{"error":"bad query"}""", status = HttpStatusCode.BadRequest)
            val result = client.execute("not sparql")

            assertTrue(result.refs.isEmpty())
            assertTrue(result.hadError)
        }

    @Test
    fun `network failure yields empty list flagged as an error`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val httpClient =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val client = WikidataSparqlClient(httpClient)

            val result = client.execute("SELECT ?item WHERE {}")

            assertTrue(result.refs.isEmpty())
            assertTrue(result.hadError)
        }

    @Test
    fun `query with no LIMIT clause gets one appended at the result limit`() {
        val query = "SELECT ?item WHERE { ?item wdt:P31 wd:Q5 }"
        assertEquals("$query\nLIMIT 50", applyMaxListSize(query, resultLimit = 50))
    }

    @Test
    fun `query LIMIT larger than the result limit is lowered to match`() {
        val query = "SELECT ?item WHERE { ?item wdt:P31 wd:Q5 } LIMIT 5000"
        assertEquals("SELECT ?item WHERE { ?item wdt:P31 wd:Q5 } LIMIT 50", applyMaxListSize(query, resultLimit = 50))
    }

    @Test
    fun `query LIMIT smaller than the result limit is left alone`() {
        val query = "SELECT ?item WHERE { ?item wdt:P31 wd:Q5 } LIMIT 10"
        assertEquals(query, applyMaxListSize(query, resultLimit = 50))
    }

    @Test
    fun `query LIMIT equal to the result limit is left alone`() {
        val query = "SELECT ?item WHERE { ?item wdt:P31 wd:Q5 } limit 50"
        assertEquals(query, applyMaxListSize(query, resultLimit = 50))
    }
}
