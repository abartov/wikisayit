package wiki.asaf.wikisayit.data.commons

import io.ktor.http.ContentType
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.commonsFilename
import javax.inject.Inject

/**
 * MediaWiki bypasses CSRF-token verification for OAuth-authenticated write requests (the check
 * only guards cookie-based sessions); any non-empty placeholder is accepted in its place.
 */
private const val OAUTH_EDIT_TOKEN = "+\\"
private val OGG_CONTENT_TYPE = ContentType("audio", "ogg")

/**
 * Uploads an approved take to Commons per the s-1s5.2 spec: named via [commonsFilename] and
 * tagged with the two WikiSayIt categories (by language and by uploader).
 */
class CommonsUploader
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
    ) {
        /** Returns the uploaded file's title. Throws [MediaWikiApiException] on any non-success result. */
        suspend fun upload(
            entry: QueueEntry,
            isoCode: String,
            username: String,
        ): String {
            val audioFile = requireNotNull(entry.audioFile) { "Cannot upload '${entry.label}': no recorded audio" }
            val filename = entry.commonsFilename(isoCode, username)
            val response =
                wikimediaClients.commons.postActionMultipart<UploadActionResponse>(
                    parameters =
                        mapOf(
                            "action" to "upload",
                            "filename" to filename,
                            "text" to buildUploadWikitext(entry, isoCode, username),
                            "comment" to "Uploaded via WikiSayIt",
                            "token" to OAUTH_EDIT_TOKEN,
                        ),
                    fileFieldName = "file",
                    file = audioFile,
                    fileContentType = OGG_CONTENT_TYPE,
                )
            return response.requireSuccess(filename).filename ?: filename
        }
    }

/** The file description page wikitext: CC0 license tag, a human-readable caption, and both
 * WikiSayIt categories. Internal (rather than private) so [CommonsUploaderTest] can check its
 * content directly instead of parsing the multipart request wire format. */
internal fun buildUploadWikitext(
    entry: QueueEntry,
    isoCode: String,
    username: String,
): String =
    """
    {{cc-zero}}

    Pronunciation of "${entry.label}" (${entry.evidenceId}) in $isoCode, recorded via WikiSayIt.

    [[Category:WikiSayIt pronunciations: $isoCode]]
    [[Category:WikiSayIt pronunciations by $username]]
    """.trimIndent()

private fun UploadActionResponse.requireSuccess(filename: String): UploadResult {
    val result =
        upload ?: throw MediaWikiApiException(
            statusCode = 200,
            errorKey = error?.code,
            errorMessage = error?.info,
            rawBody = "upload of $filename returned no result",
        )
    if (result.result != "Success") {
        throw MediaWikiApiException(
            statusCode = 200,
            errorKey = result.result,
            errorMessage = "Commons upload of $filename returned '${result.result}'",
            rawBody = result.toString(),
        )
    }
    return result
}
