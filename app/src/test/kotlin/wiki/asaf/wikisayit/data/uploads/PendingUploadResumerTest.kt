package wiki.asaf.wikisayit.data.uploads

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.data.commons.CommonsUploader
import wiki.asaf.wikisayit.data.local.db.EntryTypeCount
import wiki.asaf.wikisayit.data.local.db.MonthlyEntryTypeCount
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import java.io.File

private class FakePendingUploadRepository : PendingUploadRepository {
    private val items = mutableMapOf<Long, PendingUploadItem>()
    private var nextId = 1L

    fun seed(item: PendingUploadItem): Long {
        val id = nextId++
        items[id] = item.copy(id = id)
        return id
    }

    fun contains(id: Long) = items.containsKey(id)

    override suspend fun saveForLater(items: List<PendingUploadItem>) {
        for (item in items) seed(item)
    }

    override suspend fun loadAll(): List<PendingUploadItem> = items.values.toList()

    override suspend fun markCommonsDone(id: Long) {
        items[id] = items.getValue(id).copy(commonsDone = true)
    }

    override suspend fun markP443Done(id: Long) {
        items[id] = items.getValue(id).copy(p443Done = true)
    }

    override suspend fun delete(id: Long) {
        items.remove(id)
    }
}

private class FakeStatsRepository : StatsRepository {
    val recorded = mutableListOf<Pair<Long, RecordingEntryType>>()

    override suspend fun recordContribution(
        profileId: Long,
        entryType: RecordingEntryType,
    ) {
        recorded += profileId to entryType
    }

    override fun observeTotalsByType(): Flow<List<EntryTypeCount>> = emptyFlow()

    override fun observeMonthlyTotalsByType(): Flow<List<MonthlyEntryTypeCount>> = emptyFlow()

    override fun observeRecordingCountForProfile(profileId: Long): Flow<Int> = emptyFlow()
}

class PendingUploadResumerTest {
    private fun clients(
        commonsUploadResponse: String,
        p443Response: String,
    ): WikimediaClients {
        val engine =
            MockEngine { request ->
                val body =
                    when {
                        request.method == HttpMethod.Get -> """{"query":{"tokens":{"csrftoken":"fake+csrf+token"}}}"""
                        request.url.host == "commons.wikimedia.org" -> commonsUploadResponse
                        else -> p443Response
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
        return WikimediaClients(httpClient, NoAuthTokenProvider)
    }

    private fun itemWithAudio(
        commonsDone: Boolean = false,
        p443Done: Boolean = false,
    ): PendingUploadItem {
        val audioFile = File.createTempFile("take", ".ogg").apply { deleteOnExit() }
        return PendingUploadItem(
            entry = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "item", qid = "Q42", audioFile = audioFile),
            profileId = 7L,
            isoCode = "uk",
            username = "Ijon",
            speakerName = "",
            commonsDone = commonsDone,
            p443Done = p443Done,
        )
    }

    @Test
    fun `a fully-pending item is uploaded, claimed, and removed`() =
        runTest {
            val repository = FakePendingUploadRepository()
            val id = repository.seed(itemWithAudio())
            val stats = FakeStatsRepository()
            val wikimediaClients =
                clients(
                    commonsUploadResponse = """{"upload":{"result":"Success","filename":"uk-Q42-мова-Ijon.ogg"}}""",
                    p443Response = """{"success":1}""",
                )
            val resumer =
                PendingUploadResumer(
                    repository,
                    CommonsUploader(wikimediaClients),
                    P443StatementWriter(wikimediaClients),
                    stats,
                )

            resumer.resumeAll()

            assertTrue(!repository.contains(id))
            assertEquals(listOf(7L to RecordingEntryType.WIKIDATA_ITEM), stats.recorded)
        }

    @Test
    fun `an item whose Commons step already succeeded is not re-uploaded`() =
        runTest {
            val repository = FakePendingUploadRepository()
            var commonsCalls = 0
            val id = repository.seed(itemWithAudio(commonsDone = true))
            val engine =
                MockEngine { request ->
                    val body =
                        when {
                            request.method == HttpMethod.Get -> """{"query":{"tokens":{"csrftoken":"fake+csrf+token"}}}"""
                            request.url.host == "commons.wikimedia.org" -> {
                                commonsCalls++
                                """{"upload":{"result":"Success","filename":"uk-Q42-мова-Ijon.ogg"}}"""
                            }
                            else -> """{"success":1}"""
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
            val wikimediaClients = WikimediaClients(httpClient, NoAuthTokenProvider)
            val resumer =
                PendingUploadResumer(
                    repository,
                    CommonsUploader(wikimediaClients),
                    P443StatementWriter(wikimediaClients),
                    FakeStatsRepository(),
                )

            resumer.resumeAll()

            assertEquals(0, commonsCalls)
            assertTrue(!repository.contains(id))
        }

    @Test
    fun `one item failing to upload does not stop the rest from being attempted`() =
        runTest {
            val repository = FakePendingUploadRepository()
            val entryWithoutAudio =
                PendingUploadItem(
                    entry = QueueEntry(label = "no audio", kind = EntryKind.ITEM, detail = "item", qid = "Q1"),
                    profileId = 7L,
                    isoCode = "uk",
                    username = "Ijon",
                    speakerName = "",
                )
            val failingId = repository.seed(entryWithoutAudio)
            val succeedingId = repository.seed(itemWithAudio())
            val wikimediaClients =
                clients(
                    commonsUploadResponse = """{"upload":{"result":"Success","filename":"uk-Q42-мова-Ijon.ogg"}}""",
                    p443Response = """{"success":1}""",
                )
            val resumer =
                PendingUploadResumer(
                    repository,
                    CommonsUploader(wikimediaClients),
                    P443StatementWriter(wikimediaClients),
                    FakeStatsRepository(),
                )

            resumer.resumeAll()

            assertTrue(repository.contains(failingId))
            assertTrue(!repository.contains(succeedingId))
        }
}
