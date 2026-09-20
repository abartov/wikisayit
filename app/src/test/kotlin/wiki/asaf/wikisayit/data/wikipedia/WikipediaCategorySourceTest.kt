package wiki.asaf.wikisayit.data.wikipedia

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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.ui.session.CategoryDepth
import wiki.asaf.wikisayit.ui.session.EntryKind
import kotlin.random.Random

/**
 * Root -> {page Sparrow, subcat Sub}; Sub -> {page Owl (no Wikidata item), subcat Root (cycle
 * back to Root, must not be re-queued or re-fetched)}.
 */
private fun categoryGraphEngine() =
    MockEngine { request ->
        val params = request.url.parameters
        val body =
            when {
                params["list"] == "categorymembers" ->
                    when (params["cmtitle"]) {
                        "Category:Root" ->
                            """{"query":{"categorymembers":[
                                {"title":"Sparrow","ns":0},
                                {"title":"Category:Sub","ns":14}
                            ]}}"""
                        "Category:Sub" ->
                            """{"query":{"categorymembers":[
                                {"title":"Owl","ns":0},
                                {"title":"Category:Root","ns":14}
                            ]}}"""
                        else -> """{"query":{"categorymembers":[]}}"""
                    }
                params["prop"] == "pageprops" -> {
                    val titles = params["titles"]?.split("|").orEmpty()
                    val pages =
                        titles.mapIndexed { index, title ->
                            val props = if (title == "Sparrow") """"wikibase_item":"Q1"""" else ""
                            """"$index":{"title":"$title","pageprops":{$props}}"""
                        }.joinToString(",")
                    """{"query":{"pages":{$pages}}}"""
                }
                else -> """{"query":{}}"""
            }
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

/** A single category holding [pageCount] pages, `P1`..`P<pageCount>`, each linked to its own
 * Wikidata item — enough material for the pool/sampling behaviour to show. */
private fun numberedPagesEngine(pageCount: Int) =
    MockEngine { request ->
        val params = request.url.parameters
        val body =
            when {
                params["list"] == "categorymembers" -> {
                    val members = (1..pageCount).joinToString(",") { """{"title":"P$it","ns":0}""" }
                    """{"query":{"categorymembers":[$members]}}"""
                }
                params["prop"] == "pageprops" -> {
                    val titles = params["titles"]?.split("|").orEmpty()
                    val pages =
                        titles.joinToString(",") { title ->
                            val number = title.removePrefix("P")
                            """"$number":{"title":"$title","pageprops":{"wikibase_item":"Q$number"}}"""
                        }
                    """{"query":{"pages":{$pages}}}"""
                }
                else -> """{"query":{}}"""
            }
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

private fun sourceFor(engine: MockEngine): WikipediaCategorySource {
    val httpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    return WikipediaCategorySource(httpClient)
}

class WikipediaCategorySourceTest {
    @Test
    fun `depth NONE only collects the root category's own pages`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val result = source.build("Root", "en", CategoryDepth.NONE)

            assertEquals(1, result.entries.size)
            assertEquals("Sparrow", result.entries[0].label)
            assertEquals("Q1", result.entries[0].qid)
            assertEquals(EntryKind.ITEM, result.entries[0].kind)
            assertFalse(result.hadFetchError)
        }

    @Test
    fun `depth TWO recurses into subcategories and detects the cycle back to root`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val result = source.build("Category:Root", "en", CategoryDepth.TWO)

            // Owl has no wikibase_item, so it's dropped; Sparrow is the only resolvable entry.
            assertEquals(1, result.entries.size)
            assertEquals("Sparrow", result.entries[0].label)
        }

    @Test
    fun `page without a linked Wikidata item is dropped`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val result = source.build("Sub", "en", CategoryDepth.NONE)

            assertTrue(result.entries.none { it.label == "Owl" })
        }

    @Test
    fun `network failure yields an empty list flagged as an error rather than throwing`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val source = sourceFor(engine)

            val result = source.build("Anything", "en", CategoryDepth.FIVE)

            assertTrue(result.entries.isEmpty())
            assertTrue(result.hadFetchError)
        }

    @Test
    fun `result is truncated to maxListSize`() =
        runTest {
            val source = sourceFor(numberedPagesEngine(pageCount = 4))

            val result = source.build("Big", "en", CategoryDepth.NONE, maxListSize = 2)

            assertEquals(2, result.entries.size)
            assertTrue(result.entries.map { it.label }.all { it in listOf("P1", "P2", "P3", "P4") })
        }

    @Test
    fun `two builds of the same category draw different samples from the pool`() =
        runTest {
            val source = sourceFor(numberedPagesEngine(pageCount = 40))

            val first = source.build("Big", "en", CategoryDepth.NONE, maxListSize = 4, random = Random(1))
            val second = source.build("Big", "en", CategoryDepth.NONE, maxListSize = 4, random = Random(2))

            assertEquals(4, first.entries.size)
            assertEquals(4, second.entries.size)
            assertNotEquals(first.entries.map { it.label }, second.entries.map { it.label })
        }

    @Test
    fun `filtered-out candidates are replaced from deeper in the pool`() =
        runTest {
            // Only the first pageprops batch's worth of pages would be resolved without the
            // filter; dropping all of them must send the source back for the next batch.
            val source = sourceFor(numberedPagesEngine(pageCount = 120))
            val dropped = mutableListOf<String>()

            val result =
                source.build("Big", "en", CategoryDepth.NONE, maxListSize = 20, random = Random(7)) { candidates ->
                    val kept = candidates.filter { dropped.size >= 50 }
                    dropped += candidates.filterNot { it in kept }.map { it.label }
                    kept
                }

            assertEquals(20, result.entries.size)
            assertEquals(50, dropped.size)
            assertTrue(result.entries.none { it.label in dropped })
        }

    @Test
    fun `a filter that empties the pool yields an empty list rather than an error`() =
        runTest {
            val source = sourceFor(numberedPagesEngine(pageCount = 20))

            val result = source.build("Big", "en", CategoryDepth.NONE, maxListSize = 5) { emptyList() }

            assertTrue(result.entries.isEmpty())
            assertFalse(result.hadFetchError)
        }

    @Test
    fun `a genuinely empty category is not flagged as an error`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val result = source.build("Category:Nonexistent", "en", CategoryDepth.NONE)

            assertTrue(result.entries.isEmpty())
            assertFalse(result.hadFetchError)
        }
}
