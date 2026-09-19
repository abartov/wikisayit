package wiki.asaf.wikisayit.data.wikidata

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.BuildConfig
import wiki.asaf.wikisayit.network.ActionApiError
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

private const val P443 = "P443"

/** Speaker name qualifier (s-tu2): set on the P443 statement when the profile's speaker name
 * differs from the uploader's Wikimedia username, i.e. someone else is speaking on this account. */
private const val P2093 = "P2093"

/** Dialect/region qualifier (s-tu2). Modeled here as a plain string value rather than the item
 * value P518 usually takes — there's no reliable Wikidata item to match free-text dialect/region
 * input against. */
private const val P518 = "P518"

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
        /** [speakerName] and [username] are compared here (rather than requiring the caller to
         * pre-resolve "is this speaker actually credited") so this stays the one place that
         * decides whether the P2093 qualifier belongs on the statement. */
        suspend fun addPronunciation(
            entry: QueueEntry,
            commonsFilename: String,
            speakerName: String = "",
            username: String = "",
            dialect: String = "",
        ) {
            val entityId = entry.evidenceId
            require(entityId.isNotEmpty()) { "Cannot add P443 for '${entry.label}': no Wikidata id" }

            val csrfToken = wikimediaClients.wikidata.fetchCsrfToken()
            val response =
                wikimediaClients.wikidata.postAction<CreateClaimResponse>(
                    mapOf(
                        "action" to "wbcreateclaim",
                        "entity" to entityId,
                        "property" to P443,
                        "snaktype" to "value",
                        "value" to Json.encodeToString(commonsFilename),
                        "token" to csrfToken,
                        "summary" to "Added pronunciation recorded via WikiSayIt ${BuildConfig.VERSION_NAME}",
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

            val claimId = response.claim?.id ?: return
            val isSpeakerCredited = speakerName.isNotBlank() && speakerName != username
            if (isSpeakerCredited) setQualifier(claimId, P2093, speakerName, csrfToken)
            if (dialect.isNotBlank()) setQualifier(claimId, P518, dialect, csrfToken)
        }

        private suspend fun setQualifier(
            claimId: String,
            property: String,
            value: String,
            csrfToken: String,
        ) {
            val response =
                wikimediaClients.wikidata.postAction<CreateClaimResponse>(
                    mapOf(
                        "action" to "wbsetqualifier",
                        "claim" to claimId,
                        "property" to property,
                        "snaktype" to "value",
                        "value" to Json.encodeToString(value),
                        "token" to csrfToken,
                        "summary" to "Added qualifier via WikiSayIt ${BuildConfig.VERSION_NAME}",
                    ),
                )
            if (response.success != 1) {
                throw MediaWikiApiException(
                    statusCode = 200,
                    errorKey = response.error?.code,
                    errorMessage = response.error?.info,
                    rawBody = "wbsetqualifier for $claimId/$property returned no success",
                )
            }
        }
    }

@Serializable
data class CreateClaimResponse(
    val success: Int? = null,
    val error: ActionApiError? = null,
    val claim: ClaimResult? = null,
)

@Serializable
data class ClaimResult(
    val id: String? = null,
)
