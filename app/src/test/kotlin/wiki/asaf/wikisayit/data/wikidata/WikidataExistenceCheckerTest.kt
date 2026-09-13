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
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry

private fun itemEntry(
    label: String,
    qid: String,
) = QueueEntry(
    label = label,
    kind = EntryKind.ITEM,
    detail = "Wikidata item",
    qid = qid,
)

private fun formEntry(
    label: String,
    lexemeId: String,
) = QueueEntry(
    label = label,
    kind = EntryKind.FORM,
    detail = "lexeme form",
    lexemeId = lexemeId,
    formId = "$lexemeId-F1",
)

class WikidataExistenceCheckerTest {
    private fun checkerFor(responseBody: String): WikidataExistenceChecker {
        val engine =
            MockEngine { request ->
                assertEquals("https://www.wikidata.org/w/api.php", request.url.toString().substringBefore('?'))
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
        val clients = WikimediaClients(httpClient, NoAuthTokenProvider)
        return WikidataExistenceChecker(clients)
    }

    @Test
    fun `item with P443 is excluded`() =
        runTest {
            val checker =
                checkerFor(
                    """{"entities":{"Q1":{"claims":{"P443":[{}]}}}}""",
                )
            val result = checker.check(listOf(itemEntry("Jerusalem", "Q1")), preferredLanguage = "en")

            assertEquals(0, result.finalQueue.size)
            assertEquals(1, result.excludedCount)
            assertEquals(0, result.formsAddedCount)
        }

    @Test
    fun `item without P443 is kept`() =
        runTest {
            val checker = checkerFor("""{"entities":{"Q1":{"claims":{}}}}""")
            val result = checker.check(listOf(itemEntry("Jerusalem", "Q1")), preferredLanguage = "en")

            assertEquals(1, result.finalQueue.size)
            assertEquals(0, result.excludedCount)
            assertEquals(0, result.formsAddedCount)
        }

    @Test
    fun `lexeme with all forms already having P443 is excluded`() =
        runTest {
            val checker =
                checkerFor(
                    """
                    {"entities":{"L1":{"claims":{},"forms":[
                        {"id":"L1-F1","representations":{},"claims":{"P443":[{}]}}
                    ]}}}
                    """.trimIndent(),
                )
            val result = checker.check(listOf(formEntry("water", "L1")), preferredLanguage = "en")

            assertEquals(0, result.finalQueue.size)
            assertEquals(1, result.excludedCount)
            assertEquals(0, result.formsAddedCount)
        }

    @Test
    fun `lexeme with no forms is excluded`() =
        runTest {
            val checker = checkerFor("""{"entities":{"L1":{"claims":{},"forms":[]}}}""")
            val result = checker.check(listOf(formEntry("water", "L1")), preferredLanguage = "en")

            assertEquals(0, result.finalQueue.size)
            assertEquals(1, result.excludedCount)
        }

    @Test
    fun `lexeme with one missing form expands to one entry with no forms-added surplus`() =
        runTest {
            val checker =
                checkerFor(
                    """
                    {"entities":{"L1":{"claims":{},"forms":[
                        {"id":"L1-F1","representations":{"en":{"language":"en","value":"water"}},"claims":{}}
                    ]}}}
                    """.trimIndent(),
                )
            val result = checker.check(listOf(formEntry("water", "L1")), preferredLanguage = "en")

            assertEquals(1, result.finalQueue.size)
            assertEquals("L1-F1", result.finalQueue[0].formId)
            assertEquals("water", result.finalQueue[0].label)
            assertEquals(0, result.excludedCount)
            assertEquals(0, result.formsAddedCount)
        }

    @Test
    fun `lexeme with two missing forms expands to two entries and counts one as added`() =
        runTest {
            val checker =
                checkerFor(
                    """
                    {"entities":{"L1":{"claims":{},"forms":[
                        {"id":"L1-F1","representations":{"en":{"language":"en","value":"water"}},"claims":{}},
                        {"id":"L1-F2","representations":{"en":{"language":"en","value":"waters"}},"claims":{}}
                    ]}}}
                    """.trimIndent(),
                )
            val result = checker.check(listOf(formEntry("water", "L1")), preferredLanguage = "en")

            assertEquals(2, result.finalQueue.size)
            assertEquals(setOf("L1-F1", "L1-F2"), result.finalQueue.map { it.formId }.toSet())
            assertEquals(0, result.excludedCount)
            assertEquals(1, result.formsAddedCount)
        }

    @Test
    fun `form carries every representation as a script variant, not just the preferred language`() =
        runTest {
            val checker =
                checkerFor(
                    """
                    {"entities":{"L1":{"claims":{},"forms":[
                        {"id":"L1-F1","representations":{
                            "he":{"language":"he","value":"בית"},
                            "he-x-Q21283070":{"language":"he-x-Q21283070","value":"בֵּית"}
                        },"claims":{}}
                    ]}}}
                    """.trimIndent(),
                )
            val result = checker.check(listOf(formEntry("בית", "L1")), preferredLanguage = "he")

            assertEquals(setOf("בית", "בֵּית"), result.finalQueue[0].scriptVariants.toSet())
        }

    @Test
    fun `form label falls back to another language then to the original label`() =
        runTest {
            val checker =
                checkerFor(
                    """
                    {"entities":{"L1":{"claims":{},"forms":[
                        {"id":"L1-F1","representations":{"fr":{"language":"fr","value":"eau"}},"claims":{}}
                    ]}}}
                    """.trimIndent(),
                )
            val result = checker.check(listOf(formEntry("water", "L1")), preferredLanguage = "en")

            assertEquals("eau", result.finalQueue[0].label)
        }

    @Test
    fun `missing entity is kept unchanged`() =
        runTest {
            val checker = checkerFor("""{"entities":{"Q404":{"missing":""}}}""")
            val result = checker.check(listOf(itemEntry("Ghost", "Q404")), preferredLanguage = "en")

            assertEquals(1, result.finalQueue.size)
            assertEquals(0, result.excludedCount)
        }

    @Test
    fun `network failure keeps candidates unchanged`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val httpClient =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val checker = WikidataExistenceChecker(WikimediaClients(httpClient, NoAuthTokenProvider))

            val result =
                checker.check(
                    listOf(itemEntry("Jerusalem", "Q1"), formEntry("water", "L1")),
                    preferredLanguage = "en",
                )

            assertEquals(2, result.finalQueue.size)
            assertEquals(0, result.excludedCount)
            assertTrue(result.finalQueue.any { it.qid == "Q1" })
            assertTrue(result.finalQueue.any { it.lexemeId == "L1" })
        }

    @Test
    fun `progress is reported once per candidate`() =
        runTest {
            val checker = checkerFor("""{"entities":{"Q1":{"claims":{}},"Q2":{"claims":{}}}}""")
            val progress = mutableListOf<Int>()

            checker.check(
                listOf(itemEntry("a", "Q1"), itemEntry("b", "Q2")),
                preferredLanguage = "en",
                onProgress = { progress += it },
            )

            assertEquals(listOf(1, 2), progress)
        }
}
