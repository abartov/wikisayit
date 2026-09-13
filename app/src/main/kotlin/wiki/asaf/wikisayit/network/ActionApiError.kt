package wiki.asaf.wikisayit.network

import kotlinx.serialization.Serializable

/**
 * The `error` envelope MediaWiki's `action=` API (`w/api.php`) returns on a logical failure —
 * as opposed to [MediaWikiApiException], which wraps a REST API HTTP-status failure.
 */
@Serializable
data class ActionApiError(
    val code: String,
    val info: String,
)
