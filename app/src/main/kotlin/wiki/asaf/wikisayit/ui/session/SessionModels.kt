package wiki.asaf.wikisayit.ui.session

import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
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
) {
    /** The monospace evidence id shown under the word: the QID, or the specific form id. */
    val evidenceId: String get() = formId ?: qid.orEmpty()
}

/** Commons filename scheme from the product spec: `<iso>-<QID|LID>-<label>-<username>.ogg`. */
fun QueueEntry.commonsFilename(
    isoCode: String,
    username: String,
): String {
    val id = if (kind == EntryKind.FORM) lexemeId else qid
    return "$isoCode-$id-$label-$username.ogg"
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
