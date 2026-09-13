package wiki.asaf.wikisayit.ui.session

import wiki.asaf.wikisayit.data.commons.buildCommonsFilename
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.network.MediaWikiApiException
import java.io.File

enum class EntryKind { ITEM, FORM }

/** One word/name/phrase in a recording queue — a Wikidata item or a specific lexeme form. */
data class QueueEntry(
    val label: String,
    val kind: EntryKind,
    val detail: String,
    val qid: String? = null,
    /** Bare lexeme id, e.g. "L3302" — the filename uses this, not [formId]. */
    val lexemeId: String? = null,
    /** Specific form, e.g. "L3302-F1" — where the P443 statement is added. */
    val formId: String? = null,
    /** The cropped, padded, Ogg Vorbis-encoded take from [wiki.asaf.wikisayit.audio.RecordingEngine],
     * set once recording finishes; null until then. */
    val audioFile: File? = null,
    /** Every representation Wikidata has for this form (e.g. a Hebrew form's plain and
     * niqqud/diacritics spellings) — see s-51l. Empty for items, and for forms with only one. */
    val scriptVariants: List<String> = emptyList(),
) {
    /** The monospace evidence id shown under the word: the QID, or the specific form id. */
    val evidenceId: String get() = formId ?: qid.orEmpty()

    /** What to actually show the user for this word: every known spelling variant when there's
     * more than one — recording from just [label] risks guessing at unwritten diacritics — else
     * [label] alone. */
    val displayText: String get() = scriptVariants.takeIf { it.size > 1 }?.joinToString("  ·  ") ?: label
}

/** Commons filename scheme from the product spec: `<iso>-<QID|LID>-<label>-<username>.ogg`,
 * plus the speaker's name when it differs from [username] (see [buildCommonsFilename]). */
fun QueueEntry.commonsFilename(
    isoCode: String,
    username: String,
    speakerName: String = "",
): String {
    val id = (if (kind == EntryKind.FORM) lexemeId else qid).orEmpty()
    return buildCommonsFilename(isoCode, id, label, username, speakerName)
}

/** One Wikidata hit offered when a pasted line matched more than one item or lexeme. */
data class DisambiguationCandidate(
    val id: String,
    val label: String,
    val description: String?,
)

fun DisambiguationCandidate.toQueueEntry(kind: EntryKind): QueueEntry =
    if (kind == EntryKind.ITEM) {
        QueueEntry(label = label, kind = EntryKind.ITEM, detail = description ?: "Wikidata item", qid = id)
    } else {
        QueueEntry(label = label, kind = EntryKind.FORM, detail = description ?: "lexeme", lexemeId = id)
    }

/** A pasted line still waiting for the user to pick which Wikidata entity it means. */
data class DisambiguationCase(
    val originalLabel: String,
    val kind: EntryKind,
    val candidates: List<DisambiguationCandidate>,
)

data class SelectedLanguage(
    val isoCode: String,
    val name: String,
    val proficiency: LanguageProficiency,
    val dialect: String,
)

enum class ListSourceType { PASTE, QUERY, CATEGORY }

enum class MatchAs { ITEMS, LEXEMES }

enum class CategoryDepth { NONE, TWO, FIVE }

enum class ListBuildStage { PICK_SOURCE, SOURCE_FORM, RESOLVING, DISAMBIGUATING, FRESH, CHECKING, CHECKED, EMPTY }

enum class RecordingPhase { READY, SPEAKING, SILENCE }

sealed interface RecordingBlocker {
    data object MicPermissionDenied : RecordingBlocker

    data object Interrupted : RecordingBlocker
}

/**
 * One-shot signal for the transitions that happen asynchronously inside the ViewModel's
 * timing loops rather than as the direct result of a button tap in the composable that's
 * currently on screen (e.g. a word's silence timeout auto-advancing into review). Screens
 * that can be left this way observe it via `LaunchedEffect(uiState.autoNavigateTo)` and act
 * only on the values that mean "time for me to leave" — arriving at a screen because this
 * field already holds that screen's own value is a no-op.
 */
enum class FlowScreen { RECORDING, REVIEW, SUMMARY, DONE, LIST_SOURCE }

/** Which of the two per-entry contribution steps failed (2e). */
enum class UploadStepFailure { COMMONS, P443 }

/** The status tag shown on a "needs attention" row (2e). [NAME_TAKEN] is the only one that
 * needs a human decision (rename or discard); [WAITING] and [RETRYING] just wait for
 * "Retry all now" or the next launch. */
enum class UploadFailureStatus { RETRYING, NAME_TAKEN, WAITING }

/** Per-entry contribution progress, indexed in step with [RecordingFlowUiState.approved]. The
 * two real network calls are [commonsDone] (upload, which also lands both categories in the
 * same edit — see [categoriesDone]) and [p443Done]. [renameSuffix] backs the "Rename and
 * upload" action: a positive value is appended to the entry's label before the next retry so a
 * name conflict doesn't repeat. */
data class UploadEntryState(
    val commonsDone: Boolean = false,
    val categoriesDone: Boolean = false,
    val p443Done: Boolean = false,
    val failedStep: UploadStepFailure? = null,
    val failureStatus: UploadFailureStatus? = null,
    val errorMessage: String? = null,
    val renameSuffix: Int = 0,
) {
    val needsAttention: Boolean get() = failedStep != null
    val isComplete: Boolean get() = commonsDone && p443Done
}

/** Classifies an upload/statement failure into the status tags from `2e`: a connectivity-level
 * exception (no HTTP response at all) means the device is offline ([UploadFailureStatus.WAITING]);
 * a Commons "name taken" API error needs a human decision ([UploadFailureStatus.NAME_TAKEN]);
 * anything else is a transient failure that a plain retry might clear ([UploadFailureStatus.RETRYING]). */
fun classifyUploadFailure(error: Throwable): Pair<UploadFailureStatus, String> =
    when {
        error is MediaWikiApiException && error.errorKey?.contains("fileexists") == true ->
            UploadFailureStatus.NAME_TAKEN to (error.errorMessage ?: error.errorKey.orEmpty())
        error is MediaWikiApiException ->
            UploadFailureStatus.RETRYING to (error.errorMessage ?: error.errorKey.orEmpty())
        else -> UploadFailureStatus.WAITING to (error.message ?: "connection lost")
    }
