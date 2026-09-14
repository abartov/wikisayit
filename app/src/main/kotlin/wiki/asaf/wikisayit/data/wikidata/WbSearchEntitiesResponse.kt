package wiki.asaf.wikisayit.data.wikidata

import kotlinx.serialization.Serializable

/** Response shape of `action=wbsearchentities` (only the fields disambiguation needs). */
@Serializable
data class WbSearchEntitiesResponse(
    val search: List<WbSearchResult> = emptyList(),
)

@Serializable
data class WbSearchResult(
    val id: String,
    val label: String? = null,
    val description: String? = null,
    val match: WbMatch? = null,
)

/** The language and text `wbsearchentities` actually matched the query against — for lexemes,
 * this can differ from the requested `language` since lemma spelling is matched across all
 * languages (e.g. Ukrainian and Russian both spell "мова" the same way). */
@Serializable
data class WbMatch(
    val language: String? = null,
)
