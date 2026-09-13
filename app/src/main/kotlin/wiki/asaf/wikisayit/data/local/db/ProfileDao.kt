package wiki.asaf.wikisayit.data.local.db

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface ProfileDao {
    @Transaction
    @Query("SELECT * FROM speaker_profiles ORDER BY wikimedia_username")
    fun observeProfilesWithLanguages(): Flow<List<SpeakerProfileWithLanguages>>

    @Transaction
    @Query("SELECT * FROM speaker_profiles WHERE id = :profileId")
    fun observeProfileWithLanguages(profileId: Long): Flow<SpeakerProfileWithLanguages?>

    @Upsert
    suspend fun upsertProfile(profile: SpeakerProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<ProfileLanguageEntity>)

    @Query("DELETE FROM profile_languages WHERE profile_id = :profileId")
    suspend fun deleteLanguagesForProfile(profileId: Long)

    @Delete
    suspend fun deleteProfile(profile: SpeakerProfileEntity)
}
