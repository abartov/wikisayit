package wiki.asaf.wikisayit.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class RecordingEnumConverters {
    @TypeConverter
    fun fromLanguageProficiency(value: LanguageProficiency): String = value.name

    @TypeConverter
    fun toLanguageProficiency(value: String): LanguageProficiency = LanguageProficiency.valueOf(value)

    @TypeConverter
    fun fromRecordingEntryType(value: RecordingEntryType): String = value.name

    @TypeConverter
    fun toRecordingEntryType(value: String): RecordingEntryType = RecordingEntryType.valueOf(value)
}

@Database(
    entities = [
        SpeakerProfileEntity::class,
        ProfileLanguageEntity::class,
        RecordingStatEntity::class,
        PendingUploadEntity::class,
        SkippedEntryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(RecordingEnumConverters::class)
abstract class WikiSayItDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun recordingStatDao(): RecordingStatDao

    abstract fun pendingUploadDao(): PendingUploadDao

    abstract fun skippedEntryDao(): SkippedEntryDao

    companion object {
        const val DATABASE_NAME = "wikisayit.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE speaker_profiles ADD COLUMN speaker_name TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `pending_uploads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `label` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `qid` TEXT,
                        `lexeme_id` TEXT,
                        `form_id` TEXT,
                        `script_variants` TEXT NOT NULL,
                        `audio_file_path` TEXT NOT NULL,
                        `profile_id` INTEGER NOT NULL,
                        `iso_code` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `speaker_name` TEXT NOT NULL,
                        `commons_done` INTEGER NOT NULL,
                        `p443_done` INTEGER NOT NULL,
                        `rename_suffix` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL)
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE pending_uploads ADD COLUMN dialect TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `skipped_entries` (
                        `entity_id` TEXT PRIMARY KEY NOT NULL,
                        `label` TEXT NOT NULL,
                        `skipped_at_millis` INTEGER NOT NULL)
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE pending_uploads ADD COLUMN proficiency TEXT NOT NULL DEFAULT ''")
                }
            }
    }
}
