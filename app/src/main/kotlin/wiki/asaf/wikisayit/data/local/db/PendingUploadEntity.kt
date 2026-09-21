package wiki.asaf.wikisayit.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One approved recording that failed (or never got a turn) during contribution and was left
 * for later — durable enough to survive process death and a subsequent auto-retry (s-o8f). Kept
 * independent of any in-memory session state: [audioFilePath] points at a copy moved out of
 * [wiki.asaf.wikisayit.audio.RecordingFileStore]'s cache directory into durable app storage. */
@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val kind: String,
    val qid: String?,
    @ColumnInfo(name = "lexeme_id") val lexemeId: String?,
    @ColumnInfo(name = "form_id") val formId: String?,
    /** Comma-joined [wiki.asaf.wikisayit.ui.session.QueueEntry.scriptVariants]. */
    @ColumnInfo(name = "script_variants") val scriptVariants: String,
    @ColumnInfo(name = "audio_file_path") val audioFilePath: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "iso_code") val isoCode: String,
    val username: String,
    @ColumnInfo(name = "speaker_name") val speakerName: String,
    @ColumnInfo(name = "dialect", defaultValue = "") val dialect: String = "",
    /** The speaker's proficiency in this language, for the Commons file description. Empty for
     * rows queued before it was persisted — the description then just omits the clause rather
     * than guessing a level. */
    @ColumnInfo(name = "proficiency", defaultValue = "") val proficiency: String = "",
    @ColumnInfo(name = "commons_done") val commonsDone: Boolean,
    @ColumnInfo(name = "p443_done") val p443Done: Boolean,
    @ColumnInfo(name = "rename_suffix") val renameSuffix: Int,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)
