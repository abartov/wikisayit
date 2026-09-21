package wiki.asaf.wikisayit.data.commons

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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CommonsUploaderTest {
    private fun uploaderFor(responseBody: String): CommonsUploader {
        val engine =
            MockEngine { request ->
                val body =
                    if (request.url.parameters["action"] == "query") {
                        """{"query":{"tokens":{"csrftoken":"fake+csrf+token"}}}"""
                    } else {
                        responseBody
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
        return CommonsUploader(WikimediaClients(httpClient, NoAuthTokenProvider))
    }

    private fun entryWithAudio(kind: EntryKind = EntryKind.ITEM): QueueEntry {
        val audioFile = File.createTempFile("take", ".ogg").apply { deleteOnExit() }
        return if (kind == EntryKind.ITEM) {
            QueueEntry(label = "мова", kind = kind, detail = "item", qid = "Q42", audioFile = audioFile)
        } else {
            QueueEntry(label = "мова", kind = kind, detail = "form", lexemeId = "L708539", audioFile = audioFile)
        }
    }

    @Test
    fun `successful upload returns the server-confirmed filename`() =
        runTest {
            val uploader = uploaderFor("""{"upload":{"result":"Success","filename":"uk-Q42-мова-Ijon.ogg"}}""")
            val title = uploader.upload(entryWithAudio(), isoCode = "uk", username = "Ijon")
            assertEquals("uk-Q42-мова-Ijon.ogg", title)
        }

    @Test
    fun `a Warning result throws rather than being treated as success`() {
        val uploader = uploaderFor("""{"upload":{"result":"Warning","filename":"uk-Q42-мова-Ijon.ogg"}}""")
        assertThrows(MediaWikiApiException::class.java) {
            runTest { uploader.upload(entryWithAudio(), isoCode = "uk", username = "Ijon") }
        }
    }

    @Test
    fun `a generic API error surfaces its code and message`() {
        val uploader = uploaderFor("""{"error":{"code":"fileexists-no-change","info":"already exists"}}""")
        val exception =
            assertThrows(MediaWikiApiException::class.java) {
                runTest { uploader.upload(entryWithAudio(), isoCode = "uk", username = "Ijon") }
            }
        assertEquals("fileexists-no-change", exception.errorKey)
    }

    @Test
    fun `upload requires a recorded audio file`() {
        val uploader = uploaderFor("""{"upload":{"result":"Success"}}""")
        val entryWithoutAudio = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "item", qid = "Q42")
        assertThrows(IllegalArgumentException::class.java) {
            runTest { uploader.upload(entryWithoutAudio, isoCode = "uk", username = "Ijon") }
        }
    }

    @Test
    fun `wikitext carries an Information template with date, source, author, and the cc-zero license`() {
        val entry = entryWithAudio(EntryKind.FORM)
        val clock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)
        val wikitext = buildUploadWikitext(entry, isoCode = "he", username = "Ijon", clock = clock)

        assertTrue(wikitext.contains("{{Information"))
        assertTrue(wikitext.contains("|date=2026-09-13"))
        assertTrue(wikitext.contains("|source={{own}}"))
        assertTrue(wikitext.contains("|author=[[User:Ijon|Ijon]]"))
        assertTrue(wikitext.contains("{{cc-zero}}"))
        assertTrue(wikitext.contains("[[Category:Wiki-Say-It! pronunciations: Hebrew]]"))
        assertTrue(wikitext.contains("[[Category:Wiki-Say-It! pronunciations by Ijon]]"))
    }

    @Test
    fun `the description names the language in English and states the speaker's proficiency`() {
        val entry = entryWithAudio()
        val wikitext =
            buildUploadWikitext(
                entry,
                isoCode = "he",
                username = "Ijon",
                proficiency = LanguageProficiency.NATIVE,
            )

        assertTrue(
            wikitext.contains("in Hebrew, by [[User:Ijon|Ijon]], a native speaker, recorded via Wiki-Say-It!"),
        )
        assertTrue(!wikitext.contains("in he,"))
    }

    @Test
    fun `a proficient - not native - speaker is described as such`() {
        val entry = entryWithAudio()
        val wikitext =
            buildUploadWikitext(entry, isoCode = "he", username = "Ijon", proficiency = LanguageProficiency.PROFICIENT)

        assertTrue(wikitext.contains("by [[User:Ijon|Ijon]], a proficient speaker,"))
    }

    @Test
    fun `an upload queued before proficiency was recorded just omits the clause`() {
        val entry = entryWithAudio()
        val wikitext = buildUploadWikitext(entry, isoCode = "he", username = "Ijon", proficiency = null)

        assertTrue(wikitext.contains("in Hebrew, by [[User:Ijon|Ijon]], recorded via Wiki-Say-It!"))
        assertTrue(!wikitext.contains("speaker,"))
    }

    @Test
    fun `an iso code with no English name in CLDR falls back to the code itself`() {
        val entry = entryWithAudio()
        val wikitext = buildUploadWikitext(entry, isoCode = "zzz", username = "Ijon")

        assertTrue(wikitext.contains("in zzz, by"))
        assertTrue(wikitext.contains("[[Category:Wiki-Say-It! pronunciations: zzz]]"))
    }

    @Test
    fun `a speaker name that differs from the username is credited as author, uploader is only facilitator`() {
        val entry = entryWithAudio(EntryKind.FORM)
        val wikitext = buildUploadWikitext(entry, isoCode = "he", username = "Ijon", speakerName = "Yonatan")

        assertTrue(wikitext.contains("|author=Yonatan"))
        assertTrue(wikitext.contains("facilitated by [[User:Ijon|Ijon]]"))
        assertTrue(!wikitext.contains("|author=[[User:Ijon|Ijon]]"))
    }

    @Test
    fun `a speaker name identical to the username is not treated as a separate credit`() {
        val entry = entryWithAudio()
        val wikitext = buildUploadWikitext(entry, isoCode = "he", username = "Ijon", speakerName = "Ijon")

        assertTrue(wikitext.contains("|author=[[User:Ijon|Ijon]]"))
        assertTrue(!wikitext.contains("facilitated by"))
    }

    @Test
    fun `a dialect is folded into the description`() {
        val entry = entryWithAudio()
        val wikitext = buildUploadWikitext(entry, isoCode = "yi", username = "Ijon", dialect = "Litvish")

        assertTrue(wikitext.contains("in Yiddish (Litvish), by [[User:Ijon|Ijon]], recorded via Wiki-Say-It!"))
    }
}
