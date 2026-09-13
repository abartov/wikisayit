package wiki.asaf.wikisayit.ui.session

import wiki.asaf.wikisayit.data.local.db.LanguageProficiency

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

data class SelectedLanguage(
    val isoCode: String,
    val name: String,
    val proficiency: LanguageProficiency,
    val dialect: String,
)

enum class ListSourceType { PASTE, QUERY, CATEGORY }

enum class MatchAs { ITEMS, LEXEMES }

enum class CategoryDepth { NONE, TWO, FIVE }

enum class ListBuildStage { PICK_SOURCE, SOURCE_FORM, FRESH, CHECKING, CHECKED, EMPTY }

enum class RecordingPhase { READY, SPEAKING, SILENCE }

sealed interface RecordingBlocker {
    data object MicPermissionDenied : RecordingBlocker

    data object Interrupted : RecordingBlocker
}
