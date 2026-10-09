package wiki.asaf.wikisayit.data.wikidata

/**
 * Prepackaged SPARQL queries offered on the "Wikidata query" list source, per s-xxb: each finds
 * lexemes in the recording language, optionally restricted to one lexical category. They all
 * bind `?lexeme` (never a specific form), same as a hand-written query would — the per-form
 * "missing pronunciation" expansion already happens downstream in [WikidataExistenceChecker],
 * either while the list is built (the default gaps-only path, s-3ux) or, when the user asked to
 * include items that already have pronunciations, at the "Check for existing recordings" step.
 */
enum class CannedSparqlQuery(val lexicalCategoryQid: String?) {
    ALL_LEXEMES(lexicalCategoryQid = null),
    NOUNS(lexicalCategoryQid = "Q1084"),
    VERBS(lexicalCategoryQid = "Q24905"),
    ADJECTIVES(lexicalCategoryQid = "Q34698"),
    ADVERBS(lexicalCategoryQid = "Q380057"),
    PHRASES(lexicalCategoryQid = "Q187931"),
}

/** Builds this query's SPARQL text for [isoCode], a Wikimedia language code as [LanguageCatalog]
 * offers it. That is a two-letter ISO 639-1 code (Wikidata P218) for most languages, but a
 * language with no such code — Dagbani, say — has only its ISO 639-3 code (P220), so both are
 * tried, along with P424 ("Wikimedia language code"), which also covers the codes (like
 * `be-tarask`) that are neither. */
fun CannedSparqlQuery.buildQuery(isoCode: String): String {
    val lines =
        mutableListOf(
            "PREFIX wd: <http://www.wikidata.org/entity/>",
            "PREFIX wdt: <http://www.wikidata.org/prop/direct/>",
            "PREFIX wikibase: <http://wikiba.se/ontology#>",
            "PREFIX dct: <http://purl.org/dc/terms/>",
            "SELECT DISTINCT ?lexeme WHERE {",
            "  ?lexeme dct:language ?language .",
            "  ?language wdt:P218|wdt:P220|wdt:P424 \"$isoCode\" .",
        )
    lexicalCategoryQid?.let { lines += "  ?lexeme wikibase:lexicalCategory wd:$it ." }
    lines += "}"
    return lines.joinToString("\n")
}
