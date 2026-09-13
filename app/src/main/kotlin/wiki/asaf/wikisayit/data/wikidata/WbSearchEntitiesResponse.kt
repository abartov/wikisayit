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
)
