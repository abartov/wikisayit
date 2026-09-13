package wiki.asaf.wikisayit.data.wikidata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry

class P443StatementWriterTest {
    private fun writerFor(
        responseBody: String,
        captureRequestBody: (String) -> Unit = {},
    ): P443StatementWriter {
        val engine =
            MockEngine { request ->
                captureRequestBody((request.body as FormDataContent).formData.toString())
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
        return P443StatementWriter(WikimediaClients(httpClient, NoAuthTokenProvider))
    }

    @Test
    fun `successful claim creation for an item completes without error`() =
        runTest {
            var body = ""
            val writer = writerFor("""{"success":1,"claim":{"id":"Q42${'$'}abc"}}""") { body = it }
            val entry = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "item", qid = "Q42")

            writer.addPronunciation(entry, "uk-Q42-мова-Ijon.ogg")

            assertTrue(body.contains("Q42"))
            assertTrue(body.contains("P443"))
        }

    @Test
    fun `claim creation for a lexeme form targets the specific form id, not the lexeme`() =
        runTest {
            var body = ""
            val writer = writerFor("""{"success":1}""") { body = it }
            val entry =
                QueueEntry(
                    label = "мова",
                    kind = EntryKind.FORM,
                    detail = "form",
                    lexemeId = "L708539",
                    formId = "L708539-F1",
                )

            writer.addPronunciation(entry, "uk-L708539-мова-Ijon.ogg")

            assertTrue(body.contains("L708539-F1"))
        }

    @Test
    fun `a missing success flag surfaces the API error`() {
        val writer = writerFor("""{"error":{"code":"permissiondenied","info":"not allowed"}}""")
        val entry = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "item", qid = "Q42")

        val exception =
            assertThrows(MediaWikiApiException::class.java) {
                runTest { writer.addPronunciation(entry, "uk-Q42-мова-Ijon.ogg") }
            }
        assertEquals("permissiondenied", exception.errorKey)
    }

    @Test
    fun `refuses to write a claim for an entry with no Wikidata id`() {
        val writer = writerFor("""{"success":1}""")
        val entry = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "unresolved")

        assertThrows(IllegalArgumentException::class.java) {
            runTest { writer.addPronunciation(entry, "uk-mova-Ijon.ogg") }
        }
    }
}
