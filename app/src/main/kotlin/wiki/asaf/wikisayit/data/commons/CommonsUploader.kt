package wiki.asaf.wikisayit.data.commons

import io.ktor.http.ContentType
import wiki.asaf.wikisayit.BuildConfig
import wiki.asaf.wikisayit.data.language.LanguageCatalog
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
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
 * tagged with the two Wiki-Say-It! categories (by language and by uploader).
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
            speakerName: String = "",
            dialect: String = "",
            proficiency: LanguageProficiency? = null,
        ): String {
            val audioFile = requireNotNull(entry.audioFile) { "Cannot upload '${entry.label}': no recorded audio" }
            val filename = entry.commonsFilename(isoCode, username, speakerName)
            val csrfToken = wikimediaClients.commons.fetchCsrfToken()
            val response =
                wikimediaClients.commons.postActionMultipart<UploadActionResponse>(
                    parameters =
                        mapOf(
                            "action" to "upload",
                            "filename" to filename,
                            "text" to buildUploadWikitext(entry, isoCode, username, speakerName, dialect, proficiency, clock),
                            "comment" to "Uploaded via Wiki-Say-It! ${BuildConfig.VERSION_NAME}",
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
 * Wiki-Say-It! categories. Internal (rather than private) so [CommonsUploaderTest] can check its
 * content directly instead of parsing the multipart request wire format.
 *
 * The description names the language in English ("Hebrew", not "he") — the file page is read by
 * people who have no reason to know ISO codes — and states the speaker's [proficiency] right
 * after it, since "who said this and how well do they know the language" is the first thing a
 * reuser of a pronunciation recording needs. A null [proficiency] (only possible for uploads
 * queued by an older build, before it was persisted) just omits that clause.
 *
 * When [speakerName] is set and differs from [username] — someone else is speaking on this
 * account — the credited author is the speaker, not the uploader: `author=` names the speaker
 * plainly (they're not necessarily a Wikimedia account to link to) and the uploader is only
 * named in the description, as "facilitated by" (s-tu2). [dialect], if given, is folded into the
 * description too.
 */
internal fun buildUploadWikitext(
    entry: QueueEntry,
    isoCode: String,
    username: String,
    speakerName: String = "",
    dialect: String = "",
    proficiency: LanguageProficiency? = null,
    clock: Clock = Clock.systemUTC(),
): String {
    val isSpeakerCredited = speakerName.isNotBlank() && speakerName != username
    val authorLine = if (isSpeakerCredited) speakerName else "[[User:$username|$username]]"
    val languageName = LanguageCatalog.englishName(isoCode)
    val languageClause = if (dialect.isNotBlank()) "$languageName ($dialect)" else languageName
    val proficiencyClause =
        when (proficiency) {
            LanguageProficiency.NATIVE -> ", a native speaker"
            LanguageProficiency.PROFICIENT -> ", a proficient speaker"
            null -> ""
        }
    val facilitatedClause = if (isSpeakerCredited) ", facilitated by [[User:$username|$username]]." else ""
    return """
        =={{int:filedesc}}==
        {{Information
        |description={{en|1=Pronunciation of "${entry.label}" (${entry.evidenceId}) in $languageClause, by $authorLine$proficiencyClause, recorded via Wiki-Say-It!$facilitatedClause}}
        |date=${LocalDate.now(clock)}
        |source={{own}}
        |author=$authorLine
        }}

        =={{int:license-header}}==
        {{cc-zero}}

        [[Category:Wiki-Say-It! pronunciations: $languageName]]
        [[Category:Wiki-Say-It! pronunciations by $username]]
        """.trimIndent()
}

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
