package wiki.asaf.wikisayit.data.wikidata

import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

/** `wbgetentities` accepts at most 50 ids per request without special rights. */
private const val BATCH_SIZE = 50

/**
 * Turns a pasted SPARQL query into list-sourcing [QueueEntry]s, per the s-dbm.3 spec: run the
 * query via [WikidataSparqlClient], then batch-resolve each hit's label (item) or lemma (lexeme)
 * in [preferredLanguage], falling back to the entity's own default label/lemma when nothing
 * matches that language — same fallback as [WikidataExistenceChecker] uses for form labels.
 */
class WikidataQueryListBuilder
    @Inject
    constructor(
        private val sparqlClient: WikidataSparqlClient,
        private val wikimediaClients: WikimediaClients,
    ) {
        suspend fun build(
            query: String,
            preferredLanguage: String,
        ): List<QueueEntry> {
            val refs = sparqlClient.execute(query)
            if (refs.isEmpty()) return emptyList()

            val itemIds = refs.filter { it.kind == EntryKind.ITEM }.map { it.id }.distinct()
            val lexemeIds = refs.filter { it.kind == EntryKind.FORM }.map { it.id }.distinct()
            val itemLabels = fetchRepresentations(itemIds, props = "labels") { it.labels }
            val lexemeLemmas = fetchRepresentations(lexemeIds, props = "lemmas") { it.lemmas }

            return refs.map { ref ->
                if (ref.kind == EntryKind.ITEM) {
                    val label = itemLabels[ref.id]?.labelFor(preferredLanguage, fallback = ref.id) ?: ref.id
                    QueueEntry(label = label, kind = EntryKind.ITEM, detail = "Wikidata item", qid = ref.id)
                } else {
                    val label = lexemeLemmas[ref.id]?.labelFor(preferredLanguage, fallback = ref.id) ?: ref.id
                    QueueEntry(label = label, kind = EntryKind.FORM, detail = "lexeme", lexemeId = ref.id)
                }
            }
        }

        private suspend fun fetchRepresentations(
            ids: List<String>,
            props: String,
            representationsOf: (WbEntity) -> Map<String, WbRepresentation>,
        ): Map<String, Map<String, WbRepresentation>> {
            if (ids.isEmpty()) return emptyMap()
            val result = mutableMapOf<String, Map<String, WbRepresentation>>()
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
                response?.entities?.forEach { (id, entity) -> result[id] = representationsOf(entity) }
            }
            return result
        }
    }
