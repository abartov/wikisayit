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
    entities = [SpeakerProfileEntity::class, ProfileLanguageEntity::class, RecordingStatEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(RecordingEnumConverters::class)
abstract class WikiSayItDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun recordingStatDao(): RecordingStatDao

    companion object {
        const val DATABASE_NAME = "wikisayit.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE speaker_profiles ADD COLUMN speaker_name TEXT NOT NULL DEFAULT ''")
                }
            }
    }
}
