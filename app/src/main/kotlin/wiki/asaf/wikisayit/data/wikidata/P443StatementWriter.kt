package wiki.asaf.wikisayit.data.wikidata

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.network.ActionApiError
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

private const val P443 = "P443"

/**
 * MediaWiki bypasses CSRF-token verification for OAuth-authenticated write requests (the check
 * only guards cookie-based sessions); any non-empty placeholder is accepted in its place.
 */
private const val OAUTH_EDIT_TOKEN = "+\\"

/**
 * Adds the P443 ("pronunciation audio") statement once a [entry]'s recording has been uploaded
 * to Commons, per s-1s5.3 (item level) and s-1s5.4 (form level). One `wbcreateclaim` call covers
 * both: [QueueEntry.evidenceId] is already the right target for either case — the specific form
 * id (e.g. "L3302-F1") for a lexeme entry, or the item's QID — since Wikibase treats a form as
 * just another statement-bearing entity.
 */
class P443StatementWriter
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
    ) {
        suspend fun addPronunciation(
            entry: QueueEntry,
            commonsFilename: String,
        ) {
            val entityId = entry.evidenceId
            require(entityId.isNotEmpty()) { "Cannot add P443 for '${entry.label}': no Wikidata id" }

            val response =
                wikimediaClients.wikidata.postAction<CreateClaimResponse>(
                    mapOf(
                        "action" to "wbcreateclaim",
                        "entity" to entityId,
                        "property" to P443,
                        "snaktype" to "value",
                        "value" to Json.encodeToString(commonsFilename),
                        "token" to OAUTH_EDIT_TOKEN,
                        "summary" to "Added pronunciation recorded via WikiSayIt",
                    ),
                )
            if (response.success != 1) {
                throw MediaWikiApiException(
                    statusCode = 200,
                    errorKey = response.error?.code,
                    errorMessage = response.error?.info,
                    rawBody = "wbcreateclaim for $entityId returned no success",
                )
            }
        }
    }

@Serializable
data class CreateClaimResponse(
    val success: Int? = null,
    val error: ActionApiError? = null,
)
