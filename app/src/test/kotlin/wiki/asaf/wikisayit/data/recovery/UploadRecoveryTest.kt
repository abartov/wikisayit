package wiki.asaf.wikisayit.data.recovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients

class UploadRecoveryTest {
    private fun wikitext(
        label: String,
        evidenceId: String,
        languageClause: String = "Hebrew",
    ) = """
        =={{int:filedesc}}==
        {{Information
        |description={{en|1=Pronunciation of "$label" ($evidenceId) in $languageClause, by [[User:Ijon|Ijon]], a native speaker, recorded via Wiki-Say-It!}}
        |date=2026-09-20
        |source={{own}}
        |author=[[User:Ijon|Ijon]]
        }}
        """.trimIndent()

    @Test
    fun `recognizes an item recording, even with Commons' capitalized first letter`() {
        val recording =
            recognizeRecording(
                RecentUpload("He-Q432522-יונתן רטוש-Ijon.ogg", wikitext("יונתן רטוש", "Q432522"), ""),
                "Ijon",
            )

        assertEquals("Q432522", recording?.entityId)
        assertEquals("יונתן רטוש", recording?.label)
        assertEquals("", recording?.speakerName)
        assertNull(recording?.formId)
    }

    @Test
    fun `recognizes a lexeme recording's form from the description, plus speaker and dialect`() {
        val recording =
            recognizeRecording(
                RecentUpload(
                    "Uk-L708539-мо-ва-Ijon-Olena.ogg",
                    wikitext("мо-ва", "L708539-F2", languageClause = "Ukrainian (Lviv)"),
                    "",
                ),
                "Ijon",
            )

        assertEquals("L708539", recording?.entityId)
        assertEquals("L708539-F2", recording?.formId)
        assertEquals("мо-ва", recording?.label)
        assertEquals("Olena", recording?.speakerName)
        assertEquals("Lviv", recording?.dialect)
    }

    @Test
    fun `an upload comment mention is enough metadata`() {
        val upload = RecentUpload("En-Q42-Douglas Adams-Ijon.ogg", "no description", "Uploaded via Wiki-Say-It! 0.2.6")

        assertEquals("Q42", recognizeRecording(upload, "Ijon")?.entityId)
    }

    @Test
    fun `ignores files without a Wiki-Say-It mention or matching name`() {
        assertNull(recognizeRecording(RecentUpload("En-Q42-Douglas Adams-Ijon.ogg", "my own recording", ""), "Ijon"))
        assertNull(recognizeRecording(RecentUpload("Sunset.jpg", wikitext("x", "Q1"), ""), "Ijon"))
        assertNull(
            recognizeRecording(RecentUpload("En-Q42-Douglas Adams-Someone.ogg", wikitext("x", "Q42"), ""), "Ijon"),
        )
    }

    @Test
    fun `file comparison ignores underscores and first-letter case`() {
        assertTrue(sameFile("he-Q1-שלום_עולם-Ijon.ogg", "He-Q1-שלום עולם-Ijon.ogg"))
    }

    private val uploadsResponse =
        buildJsonObject {
            putJsonObject("query") {
                putJsonArray("pages") {
                    add(page("File:He-Q1-אחד-Ijon.ogg", wikitext("אחד", "Q1"), "2026-09-20T10:00:03Z"))
                    add(page("File:He-Q2-שתיים-Ijon.ogg", wikitext("שתיים", "Q2"), "2026-09-20T10:00:02Z"))
                    add(page("File:He-L3-שלוש-Ijon.ogg", wikitext("שלוש", "L3-F1"), "2026-09-20T10:00:01Z"))
                    add(page("File:Holiday.jpg", "a photo", "2026-09-20T10:00:00Z"))
                }
            }
        }.toString()

    private fun page(
        title: String,
        text: String,
        timestamp: String,
    ) = buildJsonObject {
        put("title", title)
        putJsonArray("revisions") {
            add(buildJsonObject { putJsonObject("slots") { putJsonObject("main") { put("content", text) } } })
        }
        putJsonArray("imageinfo") { add(buildJsonObject { put("timestamp", timestamp) }) }
    }

    private fun p443(file: String) =
        buildJsonArray {
            add(
                buildJsonObject {
                    putJsonObject("mainsnak") { putJsonObject("datavalue") { put("value", file) } }
                },
            )
        }

    /** Q1 already links its file, Q2 has no P443, L3's form F1 has none either. */
    private val entitiesResponse =
        buildJsonObject {
            putJsonObject("entities") {
                putJsonObject("Q1") { putJsonObject("claims") { put("P443", p443("he-Q1-אחד-Ijon.ogg")) } }
                putJsonObject("Q2") { putJsonObject("claims") {} }
                putJsonObject("L3") {
                    putJsonArray("forms") {
                        add(buildJsonObject { put("id", "L3-F1") })
                        add(buildJsonObject { put("id", "L3-F2") })
                    }
                }
            }
        }.toString()

    private fun recoveryFor(
        uploads: String = uploadsResponse,
        claimResponse: String = """{"success":1}""",
        onClaim: (String) -> Unit = {},
    ): UploadRecovery {
        val engine =
            MockEngine { request ->
                val params = request.url.parameters
                val body =
                    when {
                        request.method == HttpMethod.Post -> {
                            onClaim((request.body as FormDataContent).formData.toString())
                            claimResponse
                        }
                        params["meta"] == "tokens" -> """{"query":{"tokens":{"csrftoken":"t"}}}"""
                        params["generator"] == "allimages" -> uploads
                        params["action"] == "wbgetentities" -> entitiesResponse
                        else -> error("unexpected request ${request.url}")
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val clients = WikimediaClients(httpClient, NoAuthTokenProvider)
        return UploadRecovery(clients, P443StatementWriter(clients))
    }

    @Test
    fun `links only the recordings that lack P443, targeting the described form`() =
        runTest {
            val claims = mutableListOf<String>()
            val report = recoveryFor(onClaim = { claims += it }).run("Ijon", 40)

            assertEquals(4, report.scannedCount)
            assertEquals(
                listOf(
                    RecoveryFinding("He-Q1-אחד-Ijon.ogg", "Q1", RecoveryOutcome.ALREADY_LINKED),
                    RecoveryFinding("He-Q2-שתיים-Ijon.ogg", "Q2", RecoveryOutcome.LINK_ADDED),
                    RecoveryFinding("He-L3-שלוש-Ijon.ogg", "L3-F1", RecoveryOutcome.LINK_ADDED),
                ),
                report.findings,
            )
            assertEquals(2, claims.size)
            assertTrue(claims[0].contains("entity=[Q2]"))
            assertTrue(claims[1].contains("entity=[L3-F1]"))
        }

    @Test
    fun `a failed link is reported rather than aborting the run`() =
        runTest {
            val report =
                recoveryFor(claimResponse = """{"error":{"code":"permissiondenied","info":"nope"}}""").run("Ijon", 40)

            assertEquals(1, report.count(RecoveryOutcome.ALREADY_LINKED))
            assertEquals(2, report.count(RecoveryOutcome.LINK_FAILED))
        }

    @Test
    fun `a lexeme recording with no described form and no matching spelling is unresolved`() =
        runTest {
            val uploads =
                buildJsonObject {
                    putJsonObject("query") {
                        putJsonArray("pages") {
                            add(page("File:He-L3-שלוש-Ijon.ogg", "recorded via Wiki-Say-It!", "2026-09-20T10:00:01Z"))
                        }
                    }
                }.toString()

            val report = recoveryFor(uploads = uploads).run("Ijon", 40)

            assertEquals(listOf(RecoveryOutcome.UNRESOLVED), report.findings.map { it.outcome })
        }

    @Test
    fun `failing to list uploads fails the whole run`() =
        runTest {
            val engine =
                MockEngine { respond("""{"error":"boom"}""", HttpStatusCode.InternalServerError) }
            val clients =
                WikimediaClients(
                    HttpClient(engine) {
                        expectSuccess = false
                        install(ContentNegotiation) { json() }
                    },
                    NoAuthTokenProvider,
                )
            val recovery = UploadRecovery(clients, P443StatementWriter(clients))

            assertThrows(
                MediaWikiApiException::class.java,
            ) { kotlinx.coroutines.runBlocking { recovery.run("Ijon", 40) } }
        }
}
