package wiki.asaf.wikisayit.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class RecordingEntryType { WIKIDATA_ITEM, LEXEME_FORM }

/** One successfully contributed recording, logged for the Stats screen. */
@Entity(
    tableName = "recording_stats",
    foreignKeys = [
        ForeignKey(
            entity = SpeakerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecordingStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", index = true) val profileId: Long,
    @ColumnInfo(name = "entry_type") val entryType: RecordingEntryType,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
)
