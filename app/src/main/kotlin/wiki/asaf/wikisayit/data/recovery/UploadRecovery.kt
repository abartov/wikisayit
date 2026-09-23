package wiki.asaf.wikisayit.data.recovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.data.wikidata.WbEntity
import wiki.asaf.wikisayit.data.wikidata.WbGetEntitiesResponse
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

private const val P443 = "P443"

/** The most uploads recovery mode will look back through in one run. */
const val MAX_RECOVERY_UPLOADS = 500

const val DEFAULT_RECOVERY_UPLOADS = 40

/** Max pages per request when asking for revision content (`prop=revisions&rvprop=content`),
 * and max ids per `wbgetentities` request, for accounts without apihighlimits. */
private const val BATCH_SIZE = 50

private const val WIKI_SAY_IT_MARKER = "wiki-say-it"

/** `<iso>-<QID|LID>-<rest>.ogg`, as built by [wiki.asaf.wikisayit.data.commons.buildCommonsFilename].
 * The iso code may itself contain hyphens (e.g. `zh-hant`), hence the lazy first group. */
private val FILENAME_PATTERN = Regex("""^(.+?)-([QL][1-9]\d*)-(.+)\.ogg$""", RegexOption.IGNORE_CASE)

/** The `(Q123)` / `(L123-F4)` evidence id [wiki.asaf.wikisayit.data.commons.buildUploadWikitext]
 * puts in the description, right after the quoted label. */
private val EVIDENCE_ID_PATTERN = Regex("""\((Q[1-9]\d*|L[1-9]\d*-F[1-9]\d*)\)""")

/** The dialect clause of the description: `in Hebrew (Yemenite), by …`. */
private val DIALECT_PATTERN = Regex("""\bin [^()\n]+ \(([^()\n]+)\), by """)

/** One uploaded file, as listed by [UploadRecovery.fetchRecentUploads]. */
data class RecentUpload(
    /** Without the `File:` namespace prefix, e.g. `He-Q432522-יונתן רטוש-Ijon.ogg`. */
    val filename: String,
    val wikitext: String,
    val uploadComment: String,
)

/** A [RecentUpload] recognized as a Wiki-Say-It! recording, with everything needed to re-link it. */
data class RecognizedRecording(
    val filename: String,
    /** The QID for an item recording, or the lexeme id for a lexeme one. */
    val entityId: String,
    /** The specific form the recording belongs to, when the description names one. */
    val formId: String?,
    val label: String,
    val speakerName: String,
    val dialect: String,
) {
    val isLexeme: Boolean get() = entityId.startsWith("L")
}

enum class RecoveryOutcome {
    /** P443 already points at this file. */
    ALREADY_LINKED,

    /** P443 was missing and recovery mode added it. */
    LINK_ADDED,

    /** P443 was missing, but adding it failed. */
    LINK_FAILED,

    /** Couldn't work out where the link belongs (entity gone, or no way to tell which form). */
    UNRESOLVED,
}

data class RecoveryFinding(
    val filename: String,
    /** Where the P443 statement lives (or would): the QID or form id, or the bare lexeme id when
     * the form couldn't be determined. */
    val targetId: String,
    val outcome: RecoveryOutcome,
    val message: String? = null,
)

data class RecoveryReport(
    val scannedCount: Int,
    val findings: List<RecoveryFinding>,
) {
    fun count(outcome: RecoveryOutcome): Int = findings.count { it.outcome == outcome }
}

/** What [UploadRecovery.run] is currently doing, for progress display. */
sealed interface RecoveryProgress {
    data class Listing(val fetched: Int, val limit: Int) : RecoveryProgress

    data object CheckingWikidata : RecoveryProgress

    data class Linking(val done: Int, val total: Int) : RecoveryProgress
}

/**
 * Recovery mode: for when an earlier session got its recordings onto Commons but never added the
 * matching P443 statements (a crash, lost connectivity, a revoked token…). Looks back through the
 * signed-in user's most recent Commons uploads, picks out the Wiki-Say-It! recordings, and adds
 * P443 wherever it's missing.
 */
class UploadRecovery
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
        private val p443StatementWriter: P443StatementWriter,
    ) {
        /** Throws if the uploads can't be listed at all; per-file failures are reported instead. */
        suspend fun run(
            username: String,
            limit: Int,
            onProgress: (RecoveryProgress) -> Unit = {},
        ): RecoveryReport {
            val uploads = fetchRecentUploads(username, limit.coerceIn(1, MAX_RECOVERY_UPLOADS), onProgress)
            val recordings = uploads.mapNotNull { recognizeRecording(it, username) }

            onProgress(RecoveryProgress.CheckingWikidata)
            val entities = fetchEntities(recordings.map { it.entityId }.distinct())

            val findings = mutableListOf<RecoveryFinding>()
            val toLink = mutableListOf<Pair<RecognizedRecording, String>>()
            for (recording in recordings) {
                val entity = entities[recording.entityId]?.takeUnless { it.missing != null }
                if (entity == null) {
                    findings += recording.unresolved(recording.entityId, "${recording.entityId} not found on Wikidata")
                    continue
                }
                val target = resolveTarget(recording, entity)
                when {
                    target == null ->
                        findings +=
                            recording.unresolved(recording.entityId, "couldn't tell which form of ${recording.entityId} this is")
                    target.claims.p443Values().any { sameFile(it, recording.filename) } ->
                        findings += RecoveryFinding(recording.filename, target.id, RecoveryOutcome.ALREADY_LINKED)
                    else -> toLink += recording to target.id
                }
            }

            toLink.forEachIndexed { index, (recording, targetId) ->
                onProgress(RecoveryProgress.Linking(index, toLink.size))
                findings += link(recording, targetId, username)
            }
            if (toLink.isNotEmpty()) onProgress(RecoveryProgress.Linking(toLink.size, toLink.size))

            // Keep the report in upload order (newest first), however the checks above interleaved.
            val order = recordings.withIndex().associate { (index, recording) -> recording.filename to index }
            return RecoveryReport(uploads.size, findings.sortedBy { order[it.filename] })
        }

        private suspend fun link(
            recording: RecognizedRecording,
            targetId: String,
            username: String,
        ): RecoveryFinding {
            val entry =
                if (recording.isLexeme) {
                    QueueEntry(
                        label = recording.label,
                        kind = EntryKind.FORM,
                        detail = "",
                        lexemeId = recording.entityId,
                        formId = targetId,
                    )
                } else {
                    QueueEntry(label = recording.label, kind = EntryKind.ITEM, detail = "", qid = targetId)
                }
            return runCatching {
                p443StatementWriter.addPronunciation(
                    entry = entry,
                    commonsFilename = recording.filename,
                    speakerName = recording.speakerName,
                    username = username,
                    dialect = recording.dialect,
                )
            }.fold(
                onSuccess = { RecoveryFinding(recording.filename, targetId, RecoveryOutcome.LINK_ADDED) },
                onFailure = {
                    RecoveryFinding(
                        recording.filename,
                        targetId,
                        RecoveryOutcome.LINK_FAILED,
                        it.message ?: it.toString(),
                    )
                },
            )
        }

        /**
         * The user's [limit] most recent uploads, newest first, each with its description wikitext
         * and upload comment — fetched in one pass via `generator=allimages`, so the wikitext
         * doesn't need a second round of title-based lookups.
         */
        private suspend fun fetchRecentUploads(
            username: String,
            limit: Int,
            onProgress: (RecoveryProgress) -> Unit,
        ): List<RecentUpload> {
            val pages = linkedMapOf<String, AllImagesPage>()
            var continuation = emptyMap<String, String>()
            onProgress(RecoveryProgress.Listing(0, limit))
            while (pages.size < limit) {
                val response =
                    wikimediaClients.commons.getAction<AllImagesResponse>(
                        mapOf(
                            "action" to "query",
                            "formatversion" to "2",
                            "generator" to "allimages",
                            "gaiuser" to username,
                            "gaisort" to "timestamp",
                            "gaidir" to "descending",
                            "gailimit" to minOf(BATCH_SIZE, limit - pages.size).toString(),
                            "prop" to "revisions|imageinfo",
                            "rvprop" to "content",
                            "rvslots" to "main",
                            "iiprop" to "timestamp|comment",
                        ) + continuation,
                    )
                response.query?.pages.orEmpty().forEach { page ->
                    // A continued batch can repeat pages already seen; merge rather than duplicate.
                    val previous = pages[page.title]
                    pages[page.title] =
                        if (previous == null) {
                            page
                        } else {
                            previous.copy(
                                revisions = page.revisions.ifEmpty { previous.revisions },
                                imageinfo = page.imageinfo.ifEmpty { previous.imageinfo },
                            )
                        }
                }
                onProgress(RecoveryProgress.Listing(minOf(pages.size, limit), limit))
                continuation = response.`continue` ?: break
            }
            return pages.values
                // Generator results come back in page-id order, not the generator's own order.
                .sortedByDescending { it.imageinfo.firstOrNull()?.timestamp.orEmpty() }
                .take(limit)
                .map { page ->
                    RecentUpload(
                        filename = page.title.substringAfter(':'),
                        wikitext = page.revisions.firstOrNull()?.slots?.main?.content.orEmpty(),
                        uploadComment = page.imageinfo.firstOrNull()?.comment.orEmpty(),
                    )
                }
        }

        private suspend fun fetchEntities(ids: List<String>): Map<String, WbEntity> {
            val entities = mutableMapOf<String, WbEntity>()
            for (batch in ids.chunked(BATCH_SIZE)) {
                val response =
                    wikimediaClients.wikidata.getAction<WbGetEntitiesResponse>(
                        mapOf(
                            "action" to "wbgetentities",
                            "ids" to batch.joinToString("|"),
                            "props" to "claims",
                        ),
                    )
                entities.putAll(response.entities)
            }
            return entities
        }
    }

/** Where a recording's P443 statement belongs: an item, or one specific form of a lexeme. */
private data class LinkTarget(
    val id: String,
    val claims: Map<String, JsonElement>,
)

/** For a lexeme recording, the form the description names — or, for a description without one,
 * the one form whose spelling matches the filename's label. Null when neither settles it. */
private fun resolveTarget(
    recording: RecognizedRecording,
    entity: WbEntity,
): LinkTarget? {
    if (!recording.isLexeme) return LinkTarget(recording.entityId, entity.claims)
    val form =
        if (recording.formId != null) {
            entity.forms.firstOrNull { it.id == recording.formId }
        } else {
            entity.forms
                .filter { form -> form.representations.values.any { it.value == recording.label } }
                .singleOrNull()
        }
    return form?.let { LinkTarget(it.id, it.claims) }
}

private fun RecognizedRecording.unresolved(
    targetId: String,
    message: String,
) = RecoveryFinding(filename, targetId, RecoveryOutcome.UNRESOLVED, message)

/**
 * Recognizes [upload] as a Wiki-Say-It! recording made by [username], or returns null. Both
 * signals are required: the filename scheme alone could match someone's hand-named upload, and
 * the Wiki-Say-It! mention alone doesn't say which entity the recording is of.
 */
internal fun recognizeRecording(
    upload: RecentUpload,
    username: String,
): RecognizedRecording? {
    val mentionsWikiSayIt =
        upload.wikitext.contains(WIKI_SAY_IT_MARKER, ignoreCase = true) ||
            upload.uploadComment.contains(WIKI_SAY_IT_MARKER, ignoreCase = true)
    if (!mentionsWikiSayIt) return null
    val match = FILENAME_PATTERN.matchEntire(upload.filename) ?: return null
    val entityId = match.groupValues[2].uppercase()
    val (label, speakerName) = splitLabelAndSpeaker(match.groupValues[3], username) ?: return null

    val describedId = EVIDENCE_ID_PATTERN.findAll(upload.wikitext).map { it.groupValues[1] }
    val formId =
        if (entityId.startsWith("L")) describedId.firstOrNull { it.substringBefore('-') == entityId } else null
    val dialect = DIALECT_PATTERN.find(upload.wikitext)?.groupValues?.get(1).orEmpty()

    return RecognizedRecording(
        filename = upload.filename,
        entityId = entityId,
        formId = formId,
        label = label,
        speakerName = speakerName,
        dialect = dialect,
    )
}

/** Splits the `<label>-<username>[-<speakerName>]` tail of a filename. The label can contain
 * hyphens itself, so this anchors on the (known) username rather than splitting on `-`. */
private fun splitLabelAndSpeaker(
    tail: String,
    username: String,
): Pair<String, String>? {
    val bareSuffix = "-$username"
    if (tail.endsWith(bareSuffix)) return tail.removeSuffix(bareSuffix) to ""
    val index = tail.lastIndexOf("$bareSuffix-")
    if (index <= 0) return null
    return tail.substring(0, index) to tail.substring(index + bareSuffix.length + 1)
}

/** Compares Commons file names the way MediaWiki does: underscores are spaces, and the first
 * letter is case-insensitive (Commons capitalizes it on upload, e.g. `he-…` becomes `He-…`). */
internal fun sameFile(
    a: String,
    b: String,
): Boolean = normalizeFilename(a) == normalizeFilename(b)

private fun normalizeFilename(name: String): String =
    name
        .removePrefix("File:")
        .replace('_', ' ')
        .trim()
        .replaceFirstChar { it.uppercaseChar() }

/** The file names in a `claims` map's P443 statements. */
private fun Map<String, JsonElement>.p443Values(): List<String> =
    (this[P443] as? JsonArray).orEmpty().mapNotNull { statement ->
        val mainsnak = (statement as? JsonObject)?.get("mainsnak") as? JsonObject
        val datavalue = mainsnak?.get("datavalue") as? JsonObject
        (datavalue?.get("value") as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

@Serializable
private data class AllImagesResponse(
    val query: AllImagesQuery? = null,
    val `continue`: Map<String, String>? = null,
)

@Serializable
private data class AllImagesQuery(
    val pages: List<AllImagesPage> = emptyList(),
)

@Serializable
private data class AllImagesPage(
    val title: String,
    val revisions: List<PageRevision> = emptyList(),
    val imageinfo: List<ImageInfo> = emptyList(),
)

@Serializable
private data class PageRevision(
    val slots: RevisionSlots? = null,
)

@Serializable
private data class RevisionSlots(
    val main: RevisionSlot? = null,
)

@Serializable
private data class RevisionSlot(
    val content: String? = null,
)

@Serializable
private data class ImageInfo(
    val timestamp: String? = null,
    val comment: String? = null,
)
