package wiki.asaf.wikisayit.data.wikidata

import wiki.asaf.wikisayit.network.WikimediaClients
import javax.inject.Inject

/** `wbsearchentities` type parameter for the two kinds of entity list sourcing can match. */
enum class WbEntityType(val apiValue: String) {
    ITEM("item"),
    LEXEME("lexeme"),
}

private const val SEARCH_LIMIT = "10"

/** Hits for one pasted line, plus whether the search request itself failed — a network error
 * or bad response looks the same as a genuine zero-hit line from [candidates] alone, so callers
 * that need to tell them apart (e.g. to avoid mislabeling a failed lookup as "no match found")
 * use [hadError]. */
data class LabelSearchResult(
    val candidates: List<WbSearchResult>,
    val hadError: Boolean,
)

/**
 * Matches one pasted list-sourcing line against Wikidata via `action=wbsearchentities`, per the
 * s-dbm.2 spec: [RecordingFlowViewModel] auto-resolves a single hit, sends 2+ hits to the
 * disambiguation screen, and (same fail-open philosophy as [WikidataExistenceChecker]) keeps a
 * zero-hit line in the queue unresolved rather than dropping it.
 */
class WikidataLabelMatcher
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
    ) {
        suspend fun search(
            label: String,
            type: WbEntityType,
            language: String,
        ): LabelSearchResult {
            val result =
                runCatching {
                    wikimediaClients.wikidata.getAction<WbSearchEntitiesResponse>(
                        mapOf(
                            "action" to "wbsearchentities",
                            "search" to label,
                            "language" to language,
                            "uselang" to language,
                            "type" to type.apiValue,
                            "limit" to SEARCH_LIMIT,
                        ),
                    ).search
                }.getOrNull()
            return LabelSearchResult(result ?: emptyList(), hadError = result == null)
        }
    }
