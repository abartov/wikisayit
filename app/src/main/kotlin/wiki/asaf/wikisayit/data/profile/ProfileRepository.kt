package wiki.asaf.wikisayit.data.profile

import kotlinx.coroutines.flow.Flow
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.ProfileDao
import wiki.asaf.wikisayit.data.local.db.ProfileLanguageEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import javax.inject.Inject

data class ProfileLanguageInput(
    val isoCode: String,
    val languageName: String,
    val proficiency: LanguageProficiency,
    val dialect: String = "",
)

interface ProfileRepository {
    fun observeProfiles(): Flow<List<SpeakerProfileWithLanguages>>

    fun observeProfile(profileId: Long): Flow<SpeakerProfileWithLanguages?>

    suspend fun saveProfile(
        wikimediaUsername: String,
        languages: List<ProfileLanguageInput>,
        profileId: Long? = null,
    ): Long

    suspend fun deleteProfile(profile: SpeakerProfileEntity)
}

class RoomProfileRepository
    @Inject
    constructor(
        private val profileDao: ProfileDao,
    ) : ProfileRepository {
        override fun observeProfiles(): Flow<List<SpeakerProfileWithLanguages>> =
            profileDao.observeProfilesWithLanguages()

        override fun observeProfile(profileId: Long): Flow<SpeakerProfileWithLanguages?> =
            profileDao.observeProfileWithLanguages(profileId)

        override suspend fun saveProfile(
            wikimediaUsername: String,
            languages: List<ProfileLanguageInput>,
            profileId: Long?,
        ): Long {
            // @Upsert returns -1 (not the row id) when it takes the update path, so an explicit
            // profileId must win over the return value; only a brand-new insert needs it.
            val insertedId =
                profileDao.upsertProfile(
                    SpeakerProfileEntity(id = profileId ?: 0, wikimediaUsername = wikimediaUsername),
                )
            val savedId = profileId ?: insertedId
            profileDao.deleteLanguagesForProfile(savedId)
            profileDao.insertLanguages(
                languages.map { language ->
                    ProfileLanguageEntity(
                        profileId = savedId,
                        isoCode = language.isoCode,
                        languageName = language.languageName,
                        proficiency = language.proficiency,
                        dialect = language.dialect,
                    )
                },
            )
            return savedId
        }

        override suspend fun deleteProfile(profile: SpeakerProfileEntity) = profileDao.deleteProfile(profile)
    }
