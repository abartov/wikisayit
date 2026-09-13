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
import org.junit.Assert.assertTrue
import org.junit.Test
import wiki.asaf.wikisayit.ui.session.CategoryDepth
import wiki.asaf.wikisayit.ui.session.EntryKind

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
    fun `a genuinely empty category is not flagged as an error`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val result = source.build("Category:Nonexistent", "en", CategoryDepth.NONE)

            assertTrue(result.entries.isEmpty())
            assertFalse(result.hadFetchError)
        }
}
