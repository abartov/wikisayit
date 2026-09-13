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
import wiki.asaf.wikisayit.ui.session.EntryKind

class WikidataQueryListBuilderTest {
    private fun builderFor(
        sparqlBody: String,
        entitiesBody: String,
    ): WikidataQueryListBuilder {
        val engine =
            MockEngine { request ->
                val body =
                    if (request.url.host == "query.wikidata.org") {
                        sparqlBody
                    } else {
                        entitiesBody
                    }
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val sparqlClient = WikidataSparqlClient(httpClient)
        val wikimediaClients = WikimediaClients(httpClient, NoAuthTokenProvider)
        return WikidataQueryListBuilder(sparqlClient, wikimediaClients)
    }

    @Test
    fun `item hit resolves to a labeled item entry`() =
        runTest {
            val builder =
                builderFor(
                    sparqlBody =
                        """
                        {"results":{"bindings":[
                            {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q42"}}
                        ]}}
                        """.trimIndent(),
                    entitiesBody =
                        """{"entities":{"Q42":{"labels":{"en":{"language":"en","value":"Douglas Adams"}}}}}""",
                )
            val result = builder.build("SELECT ?item WHERE {}", preferredLanguage = "en")

            assertEquals(1, result.entries.size)
            assertEquals("Douglas Adams", result.entries[0].label)
            assertEquals(EntryKind.ITEM, result.entries[0].kind)
            assertEquals("Q42", result.entries[0].qid)
            assertFalse(result.hadFetchError)
        }

    @Test
    fun `lexeme hit resolves to a lemma-labeled form entry`() =
        runTest {
            val builder =
                builderFor(
                    sparqlBody =
                        """
                        {"results":{"bindings":[
                            {"lexeme":{"type":"uri","value":"http://www.wikidata.org/entity/L2"}}
                        ]}}
                        """.trimIndent(),
                    entitiesBody =
                        """{"entities":{"L2":{"lemmas":{"en":{"language":"en","value":"water"}}}}}""",
                )
            val result = builder.build("SELECT ?lexeme WHERE {}", preferredLanguage = "en")

            assertEquals(1, result.entries.size)
            assertEquals("water", result.entries[0].label)
            assertEquals(EntryKind.FORM, result.entries[0].kind)
            assertEquals("L2", result.entries[0].lexemeId)
        }

    @Test
    fun `missing label in preferred language falls back to another available one`() =
        runTest {
            val builder =
                builderFor(
                    sparqlBody =
                        """
                        {"results":{"bindings":[
                            {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"}}
                        ]}}
                        """.trimIndent(),
                    entitiesBody =
                        """{"entities":{"Q1":{"labels":{"fr":{"language":"fr","value":"Terre"}}}}}""",
                )
            val result = builder.build("SELECT ?item WHERE {}", preferredLanguage = "en")

            assertEquals("Terre", result.entries[0].label)
        }

    @Test
    fun `no sparql results yields empty list without calling wbgetentities`() =
        runTest {
            val builder = builderFor("""{"results":{"bindings":[]}}""", entitiesBody = """{"entities":{}}""")
            val result = builder.build("SELECT ?item WHERE {}", preferredLanguage = "en")

            assertTrue(result.entries.isEmpty())
            assertFalse(result.hadFetchError)
        }

    @Test
    fun `sparql request failure is reported distinctly from a genuinely empty result`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val httpClient =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val builder =
                WikidataQueryListBuilder(
                    WikidataSparqlClient(httpClient),
                    WikimediaClients(httpClient, NoAuthTokenProvider),
                )

            val result = builder.build("SELECT ?item WHERE {}", preferredLanguage = "en")

            assertTrue(result.entries.isEmpty())
            assertTrue(result.hadFetchError)
        }
}
