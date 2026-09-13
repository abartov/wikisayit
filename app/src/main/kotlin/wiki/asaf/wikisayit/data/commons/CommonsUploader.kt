package wiki.asaf.wikisayit.data.commons

import io.ktor.http.ContentType
import wiki.asaf.wikisayit.network.MediaWikiApiException
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.commonsFilename
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private val OGG_CONTENT_TYPE = ContentType("audio", "ogg")

/**
 * Uploads an approved take to Commons per the s-1s5.2 spec: named via [commonsFilename] and
 * tagged with the two WikiSayIt categories (by language and by uploader).
 */
class CommonsUploader
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
        private val clock: Clock = Clock.systemUTC(),
    ) {
        /** Returns the uploaded file's title. Throws [MediaWikiApiException] on any non-success result. */
        suspend fun upload(
            entry: QueueEntry,
            isoCode: String,
            username: String,
        ): String {
            val audioFile = requireNotNull(entry.audioFile) { "Cannot upload '${entry.label}': no recorded audio" }
            val filename = entry.commonsFilename(isoCode, username)
            val csrfToken = wikimediaClients.commons.fetchCsrfToken()
            val response =
                wikimediaClients.commons.postActionMultipart<UploadActionResponse>(
                    parameters =
                        mapOf(
                            "action" to "upload",
                            "filename" to filename,
                            "text" to buildUploadWikitext(entry, isoCode, username, clock),
                            "comment" to "Uploaded via WikiSayIt",
                            "token" to csrfToken,
                        ),
                    fileFieldName = "file",
                    file = audioFile,
                    fileContentType = OGG_CONTENT_TYPE,
                )
            return response.requireSuccess(filename).filename ?: filename
        }
    }

/** The file description page wikitext: a proper `{{Information}}` template (machine-readable
 * `date=`/`source=`/`author=`, per Commons convention — without it, tools and other editors have
 * no structured way to see who recorded this or when), the `{{cc-zero}}` license tag, and both
 * WikiSayIt categories. Internal (rather than private) so [CommonsUploaderTest] can check its
 * content directly instead of parsing the multipart request wire format. */
internal fun buildUploadWikitext(
    entry: QueueEntry,
    isoCode: String,
    username: String,
    clock: Clock = Clock.systemUTC(),
): String =
    """
    =={{int:filedesc}}==
    {{Information
    |description={{en|1=Pronunciation of "${entry.label}" (${entry.evidenceId}) in $isoCode, recorded via WikiSayIt.}}
    |date=${LocalDate.now(clock)}
    |source={{own}}
    |author=[[User:$username|$username]]
    }}

    =={{int:license-header}}==
    {{cc-zero}}

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
