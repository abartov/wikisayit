package wiki.asaf.wikisayit.data.wikidata

import wiki.asaf.wikisayit.network.WikimediaClients
import javax.inject.Inject

/** `wbsearchentities` type parameter for the two kinds of entity list sourcing can match. */
enum class WbEntityType(val apiValue: String) {
    ITEM("item"),
    LEXEME("lexeme"),
}

private const val SEARCH_LIMIT = "10"

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
        ): List<WbSearchResult> =
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
            }.getOrDefault(emptyList())
    }
