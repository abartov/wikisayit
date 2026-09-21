package wiki.asaf.wikisayit.data.wikidata

import wiki.asaf.wikisayit.data.local.settings.DEFAULT_MAX_LIST_SIZE
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.ListBuildResult
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject
import kotlin.math.min

/** `wbgetentities` accepts at most 50 ids per request without special rights. */
private const val BATCH_SIZE = 50

/** How many query hits to ask for per entry the list will actually hold, so that a [build]
 * `filter` dropping entries (already recorded, previously skipped) has spare hits to draw on
 * instead of handing back a short list — the query-source counterpart of
 * [wiki.asaf.wikisayit.data.wikipedia.WikipediaCategorySource]'s candidate pool (s-3ux). */
private const val CANDIDATE_POOL_FACTOR = 5

/** Ceiling on the raised `LIMIT`, so a small configured list size scaled up by
 * [CANDIDATE_POOL_FACTOR] still can't ask the query service for an unreasonable result set. */
private const val MAX_QUERY_HITS = 2000

/**
 * Turns a pasted SPARQL query into list-sourcing [QueueEntry]s, per the s-dbm.3 spec: run the
 * query via [WikidataSparqlClient], then batch-resolve each hit's label (item) or lemma (lexeme)
 * in [preferredLanguage], falling back to the entity's own default label/lemma when nothing
 * matches that language — same fallback as [WikidataExistenceChecker] uses for form labels.
 *
 * Hits are kept in the order the query returned them (a deliberate `ORDER BY` is the user's, not
 * ours to shuffle away), and resolved a batch at a time so a `filter` that drops most of what it
 * sees reaches further down the result set without every build paying for the whole pool.
 */
class WikidataQueryListBuilder
    @Inject
    constructor(
        private val sparqlClient: WikidataSparqlClient,
        private val wikimediaClients: WikimediaClients,
    ) {
        /**
         * @param filter applied to each freshly resolved batch of candidates, letting the caller
         *   drop entries while there are still unresolved hits left to replace them with.
         *   Whatever it returns is appended to the list.
         */
        suspend fun build(
            query: String,
            preferredLanguage: String,
            maxListSize: Int = DEFAULT_MAX_LIST_SIZE,
            filter: suspend (List<QueueEntry>) -> List<QueueEntry> = { it },
        ): ListBuildResult {
            val poolSize = min(maxListSize.toLong() * CANDIDATE_POOL_FACTOR, MAX_QUERY_HITS.toLong()).toInt()
            val sparqlResult = sparqlClient.execute(query, poolSize)
            val refs = sparqlResult.refs.distinctBy { it.id }
            if (refs.isEmpty()) return ListBuildResult(emptyList(), hadFetchError = sparqlResult.hadError)

            val entries = mutableListOf<QueueEntry>()
            var hadError = false
            for (batch in refs.chunked(BATCH_SIZE)) {
                if (entries.size >= maxListSize) break
                val itemIds = batch.filter { it.kind == EntryKind.ITEM }.map { it.id }
                val lexemeIds = batch.filter { it.kind == EntryKind.FORM }.map { it.id }
                val (itemLabels, itemLabelsHadError) = fetchRepresentations(itemIds, props = "labels") { it.labels }
                val (lemmas, lemmasHadError) = fetchRepresentations(lexemeIds, props = "lemmas") { it.lemmas }
                if (itemLabelsHadError || lemmasHadError) hadError = true
                val resolved =
                    batch.map { ref ->
                        if (ref.kind == EntryKind.ITEM) {
                            val label = itemLabels[ref.id]?.labelFor(preferredLanguage, fallback = ref.id) ?: ref.id
                            QueueEntry(label = label, kind = EntryKind.ITEM, detail = "Wikidata item", qid = ref.id)
                        } else {
                            val label = lemmas[ref.id]?.labelFor(preferredLanguage, fallback = ref.id) ?: ref.id
                            QueueEntry(label = label, kind = EntryKind.FORM, detail = "lexeme", lexemeId = ref.id)
                        }
                    }
                entries += filter(resolved)
            }
            return ListBuildResult(
                entries.take(maxListSize),
                hadFetchError = sparqlResult.hadError || hadError,
            )
        }

        /** @return the resolved representations, plus whether any batch's request failed. */
        private suspend fun fetchRepresentations(
            ids: List<String>,
            props: String,
            representationsOf: (WbEntity) -> Map<String, WbRepresentation>,
        ): Pair<Map<String, Map<String, WbRepresentation>>, Boolean> {
            if (ids.isEmpty()) return emptyMap<String, Map<String, WbRepresentation>>() to false
            val result = mutableMapOf<String, Map<String, WbRepresentation>>()
            var hadError = false
            for (batch in ids.chunked(BATCH_SIZE)) {
                val response =
                    runCatching {
                        wikimediaClients.wikidata.getAction<WbGetEntitiesResponse>(
                            mapOf(
                                "action" to "wbgetentities",
                                "ids" to batch.joinToString("|"),
                                "props" to props,
                            ),
                        )
                    }.getOrNull()
                if (response == null) hadError = true
                response?.entities?.forEach { (id, entity) -> result[id] = representationsOf(entity) }
            }
            return result to hadError
        }
    }
