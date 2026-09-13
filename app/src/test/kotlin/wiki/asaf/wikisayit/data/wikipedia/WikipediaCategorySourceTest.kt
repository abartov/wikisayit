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
            val entries = source.build("Root", "en", CategoryDepth.NONE)

            assertEquals(1, entries.size)
            assertEquals("Sparrow", entries[0].label)
            assertEquals("Q1", entries[0].qid)
            assertEquals(EntryKind.ITEM, entries[0].kind)
        }

    @Test
    fun `depth TWO recurses into subcategories and detects the cycle back to root`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val entries = source.build("Category:Root", "en", CategoryDepth.TWO)

            // Owl has no wikibase_item, so it's dropped; Sparrow is the only resolvable entry.
            assertEquals(1, entries.size)
            assertEquals("Sparrow", entries[0].label)
        }

    @Test
    fun `page without a linked Wikidata item is dropped`() =
        runTest {
            val source = sourceFor(categoryGraphEngine())
            val entries = source.build("Sub", "en", CategoryDepth.NONE)

            assertTrue(entries.none { it.label == "Owl" })
        }

    @Test
    fun `network failure yields an empty list rather than throwing`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("boom") }
            val source = sourceFor(engine)

            val entries = source.build("Anything", "en", CategoryDepth.FIVE)

            assertTrue(entries.isEmpty())
        }
}
