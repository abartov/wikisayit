package wiki.asaf.wikisayit.data.wikidata

/**
 * Prepackaged SPARQL queries offered on the "Wikidata query" list source, per s-xxb: each finds
 * lexemes in the recording language, optionally restricted to one lexical category. They all
 * bind `?lexeme` (never a specific form), same as a hand-written query would — the per-form
 * "missing pronunciation" expansion already happens downstream in [WikidataExistenceChecker]
 * once the built list goes through the existing "Check for existing recordings" step, same as a
 * pasted list matched as lexemes.
 */
enum class CannedSparqlQuery(val lexicalCategoryQid: String?) {
    ALL_LEXEMES(lexicalCategoryQid = null),
    NOUNS(lexicalCategoryQid = "Q1084"),
    VERBS(lexicalCategoryQid = "Q24905"),
    ADJECTIVES(lexicalCategoryQid = "Q34698"),
    ADVERBS(lexicalCategoryQid = "Q380057"),
    PHRASES(lexicalCategoryQid = "Q187931"),
}

/** Builds this query's SPARQL text for [isoCode] (a two-letter ISO 639-1 code, matched against
 * Wikidata's P218 statement on the language item — the same code [LanguageCatalog] uses). */
fun CannedSparqlQuery.buildQuery(isoCode: String): String {
    val lines =
        mutableListOf(
            "PREFIX wd: <http://www.wikidata.org/entity/>",
            "PREFIX wdt: <http://www.wikidata.org/prop/direct/>",
            "PREFIX wikibase: <http://wikiba.se/ontology#>",
            "PREFIX dct: <http://purl.org/dc/terms/>",
            "SELECT DISTINCT ?lexeme WHERE {",
            "  ?lexeme dct:language ?language .",
            "  ?language wdt:P218 \"$isoCode\" .",
        )
    lexicalCategoryQid?.let { lines += "  ?lexeme wikibase:lexicalCategory wd:$it ." }
    lines += "}"
    return lines.joinToString("\n")
}
