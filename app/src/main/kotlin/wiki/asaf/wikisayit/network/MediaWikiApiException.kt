package wiki.asaf.wikisayit.network

/**
 * Thrown when a MediaWiki/Wikibase REST API call returns a non-2xx response.
 *
 * The REST API and Wikibase REST API don't share one error envelope shape, so [errorKey]
 * and [errorMessage] are extracted best-effort from whichever JSON fields are present
 * (`errorKey`/`messageTranslations` for core REST errors, `code`/`message` for Wikibase ones).
 * [rawBody] is always preserved so callers can fall back to it.
 */
class MediaWikiApiException(
    val statusCode: Int,
    val errorKey: String?,
    val errorMessage: String?,
    val rawBody: String,
) : Exception("MediaWiki API error $statusCode${errorKey?.let { " ($it)" } ?: ""}: ${errorMessage ?: rawBody}")
