package wiki.asaf.wikisayit.data.wikidata

import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataCannedQueriesTest {
    @Test
    fun `all-lexemes query binds lexeme and the given language, with no category filter`() {
        val query = CannedSparqlQuery.ALL_LEXEMES.buildQuery("en")

        assertTrue(query.contains("SELECT DISTINCT ?lexeme WHERE"))
        assertTrue(query.contains("?language wdt:P218|wdt:P220|wdt:P424 \"en\" ."))
        assertTrue(!query.contains("wikibase:lexicalCategory"))
    }

    @Test
    fun `category-scoped queries filter by lexicalCategory`() {
        val query = CannedSparqlQuery.NOUNS.buildQuery("fr")

        assertTrue(query.contains("?language wdt:P218|wdt:P220|wdt:P424 \"fr\" ."))
        assertTrue(query.contains("?lexeme wikibase:lexicalCategory wd:Q1084 ."))
    }

    @Test
    fun `a language with only an ISO 639-3 code is matched via P220 or the Wikimedia code`() {
        val query = CannedSparqlQuery.ALL_LEXEMES.buildQuery("dag")

        assertTrue(query.contains("?language wdt:P218|wdt:P220|wdt:P424 \"dag\" ."))
    }

    @Test
    fun `every canned query is well-formed SPARQL braces`() {
        for (canned in CannedSparqlQuery.entries) {
            val query = canned.buildQuery("he")
            assertTrue(query.startsWith("PREFIX"))
            assertTrue(query.trimEnd().endsWith("}"))
        }
    }
}
