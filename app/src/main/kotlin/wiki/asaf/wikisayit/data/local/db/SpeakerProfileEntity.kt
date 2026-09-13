package wiki.asaf.wikisayit.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A speaker profile: the Wikimedia account a recording session runs under. */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "wikimedia_username") val wikimediaUsername: String,
)

enum class LanguageProficiency { NATIVE, PROFICIENT }

/** One language a profile can record in. [dialect] is empty for the language's standard form. */
@Entity(
    tableName = "profile_languages",
    foreignKeys = [
        ForeignKey(
            entity = SpeakerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProfileLanguageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", index = true) val profileId: Long,
    @ColumnInfo(name = "iso_code") val isoCode: String,
    @ColumnInfo(name = "language_name") val languageName: String,
    val proficiency: LanguageProficiency,
    val dialect: String = "",
)

data class SpeakerProfileWithLanguages(
    @Embedded val profile: SpeakerProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val languages: List<ProfileLanguageEntity>,
)
