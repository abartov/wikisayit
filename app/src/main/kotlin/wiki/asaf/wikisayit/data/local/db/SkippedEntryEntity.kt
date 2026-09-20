package wiki.asaf.wikisayit.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One entry the speaker skipped during recording, remembered so later list builds can leave it
 * out (s-fi0.2). Keyed by the entry's Wikidata evidence id — the QID for items, the form id for
 * lexeme forms — since that's what a freshly built list can be matched against.
 */
@Entity(tableName = "skipped_entries")
data class SkippedEntryEntity(
    @PrimaryKey @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "skipped_at_millis") val skippedAtMillis: Long,
)
