package wiki.asaf.wikisayit.data.wikidata

import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataCannedQueriesTest {
    @Test
    fun `all-lexemes query binds lexeme and the given language, with no category filter`() {
        val query = CannedSparqlQuery.ALL_LEXEMES.buildQuery("en")

        assertTrue(query.contains("SELECT DISTINCT ?lexeme WHERE"))
        assertTrue(query.contains("?language wdt:P218 \"en\" ."))
        assertTrue(!query.contains("wikibase:lexicalCategory"))
    }

    @Test
    fun `category-scoped queries filter by lexicalCategory`() {
        val query = CannedSparqlQuery.NOUNS.buildQuery("fr")

        assertTrue(query.contains("?language wdt:P218 \"fr\" ."))
        assertTrue(query.contains("?lexeme wikibase:lexicalCategory wd:Q1084 ."))
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
