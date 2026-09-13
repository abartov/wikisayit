package wiki.asaf.wikisayit.data.wikidata

import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

private const val P443 = "P443"

/** `wbgetentities` accepts at most 50 ids per request without special rights. */
private const val BATCH_SIZE = 50

/**
 * The result of running [QueueEntry] candidates from list sourcing through the P443
 * ("pronunciation audio") existence check described in the product spec: items that already
 * have P443 are dropped, and lexemes are expanded into one entry per form that still lacks it
 * (dropped entirely if they have no forms, or every form already has it).
 */
data class ExistenceCheckResult(
    val finalQueue: List<QueueEntry>,
    val excludedCount: Int,
    val formsAddedCount: Int,
)

/**
 * Checks list-sourcing candidates against live Wikidata for the P443 (pronunciation audio)
 * property, per the s-dbm.5 spec. Uses the `action=wbgetentities` API (not the Wikibase REST
 * API) because, as of this writing, the REST API has no lexeme/form endpoints.
 *
 * Network failures, and ids Wikidata reports as missing, fail open: the candidate is kept
 * unchanged rather than excluded, since skipping a real recording is worse than occasionally
 * re-recording one that's already covered.
 */
class WikidataExistenceChecker
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
    ) {
        suspend fun check(
            candidates: List<QueueEntry>,
            preferredLanguage: String,
            onProgress: (checked: Int) -> Unit = {},
        ): ExistenceCheckResult {
            val ids = candidates.mapNotNull { it.qid ?: it.lexemeId }.distinct()
            val entitiesById = fetchEntities(ids)

            val finalQueue = mutableListOf<QueueEntry>()
            var excludedCount = 0
            var formsAddedCount = 0
            var done = 0

            for (candidate in candidates) {
                when (candidate.kind) {
                    EntryKind.ITEM -> {
                        val entity = candidate.qid?.let { entitiesById[it] }?.takeUnless { it.missing != null }
                        if (entity != null && P443 in entity.claims) {
                            excludedCount++
                        } else {
                            finalQueue += candidate
                        }
                    }

                    EntryKind.FORM -> {
                        val entity = candidate.lexemeId?.let { entitiesById[it] }?.takeUnless { it.missing != null }
                        if (entity == null) {
                            // Couldn't resolve (network failure or unknown id) — keep as-is.
                            finalQueue += candidate
                        } else {
                            val missingForms = entity.forms.filterNot { P443 in it.claims }
                            if (missingForms.isEmpty()) {
                                excludedCount++
                            } else {
                                missingForms.forEachIndexed { index, form ->
                                    finalQueue +=
                                        candidate.copy(
                                            label = form.labelFor(preferredLanguage, fallback = candidate.label),
                                            formId = form.id,
                                        )
                                    if (index > 0) formsAddedCount++
                                }
                            }
                        }
                    }
                }
                done++
                onProgress(done)
            }

            return ExistenceCheckResult(finalQueue, excludedCount, formsAddedCount)
        }

        private suspend fun fetchEntities(ids: List<String>): Map<String, WbEntity> {
            if (ids.isEmpty()) return emptyMap()
            val entities = mutableMapOf<String, WbEntity>()
            for (batch in ids.chunked(BATCH_SIZE)) {
                val response =
                    runCatching {
                        wikimediaClients.wikidata.getAction<WbGetEntitiesResponse>(
                            mapOf(
                                "action" to "wbgetentities",
                                "ids" to batch.joinToString("|"),
                                "props" to "claims",
                            ),
                        )
                    }.getOrNull()
                response?.let { entities.putAll(it.entities) }
            }
            return entities
        }
    }

private fun WbForm.labelFor(
    isoCode: String,
    fallback: String,
): String = representations[isoCode]?.value ?: representations.values.firstOrNull()?.value ?: fallback
