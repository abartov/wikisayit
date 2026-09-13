package wiki.asaf.wikisayit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.WikiSayItDatabase
import wiki.asaf.wikisayit.data.local.settings.DataStoreSettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileLanguageInput
import wiki.asaf.wikisayit.data.profile.RoomProfileRepository
import wiki.asaf.wikisayit.data.stats.RoomStatsRepository
import wiki.asaf.wikisayit.data.uploads.PendingUploadItem
import wiki.asaf.wikisayit.data.uploads.RoomPendingUploadRepository
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private lateinit var database: WikiSayItDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, WikiSayItDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun insertProfile(): Long =
        RoomProfileRepository(database.profileDao()).saveProfile(
            wikimediaUsername = "Ijon",
            languages = listOf(ProfileLanguageInput("uk", "Ukrainian", LanguageProficiency.NATIVE)),
        )

    @Test
    fun profileRepository_roundTripsProfileAndLanguages() =
        runTest {
            val repository = RoomProfileRepository(database.profileDao())

            val profileId =
                repository.saveProfile(
                    wikimediaUsername = "Ijon",
                    languages =
                        listOf(
                            ProfileLanguageInput("uk", "Ukrainian", LanguageProficiency.NATIVE),
                            ProfileLanguageInput("he", "Hebrew", LanguageProficiency.PROFICIENT, dialect = "Modern"),
                        ),
                )

            val saved = repository.observeProfile(profileId).first()

            assertEquals("Ijon", saved?.profile?.wikimediaUsername)
            assertEquals(2, saved?.languages?.size)
            assertTrue(saved!!.languages.any { it.isoCode == "uk" && it.proficiency == LanguageProficiency.NATIVE })
            assertTrue(saved.languages.any { it.isoCode == "he" && it.dialect == "Modern" })
        }

    @Test
    fun profileRepository_replacingLanguagesDropsOldOnes() =
        runTest {
            val repository = RoomProfileRepository(database.profileDao())
            val profileId =
                repository.saveProfile(
                    wikimediaUsername = "Ijon",
                    languages = listOf(ProfileLanguageInput("uk", "Ukrainian", LanguageProficiency.NATIVE)),
                )

            repository.saveProfile(
                wikimediaUsername = "Ijon",
                languages = listOf(ProfileLanguageInput("he", "Hebrew", LanguageProficiency.PROFICIENT)),
                profileId = profileId,
            )

            val saved = repository.observeProfile(profileId).first()
            assertEquals(1, saved?.languages?.size)
            assertEquals("he", saved?.languages?.first()?.isoCode)
        }

    @Test
    fun statsRepository_aggregatesTotalsByType() =
        runTest {
            val profileId = insertProfile()
            val fixedClock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)
            val repository = RoomStatsRepository(database.recordingStatDao(), fixedClock)

            repository.recordContribution(profileId = profileId, entryType = RecordingEntryType.WIKIDATA_ITEM)
            repository.recordContribution(profileId = profileId, entryType = RecordingEntryType.WIKIDATA_ITEM)
            repository.recordContribution(profileId = profileId, entryType = RecordingEntryType.LEXEME_FORM)

            val totals = repository.observeTotalsByType().first().associate { it.entryType to it.count }

            assertEquals(2, totals[RecordingEntryType.WIKIDATA_ITEM])
            assertEquals(1, totals[RecordingEntryType.LEXEME_FORM])
        }

    @Test
    fun statsRepository_groupsMonthlyTotalsByYearMonth() =
        runTest {
            val profileId = insertProfile()
            val fixedClock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC)
            val repository = RoomStatsRepository(database.recordingStatDao(), fixedClock)

            repository.recordContribution(profileId = profileId, entryType = RecordingEntryType.WIKIDATA_ITEM)

            val monthly = repository.observeMonthlyTotalsByType().first()

            assertEquals(1, monthly.size)
            assertEquals("2026-09", monthly.first().yearMonth)
            assertEquals(RecordingEntryType.WIKIDATA_ITEM, monthly.first().entryType)
        }

    @Test
    fun pendingUploadRepository_roundTripsAndDeletesAnItem() =
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val profileId = insertProfile()
            val repository = RoomPendingUploadRepository(database.pendingUploadDao(), context)
            val audioFile = File.createTempFile("take", ".ogg").apply { deleteOnExit() }

            repository.saveForLater(
                listOf(
                    PendingUploadItem(
                        entry =
                            QueueEntry(
                                label = "мова",
                                kind = EntryKind.ITEM,
                                detail = "item",
                                qid = "Q42",
                                audioFile = audioFile,
                            ),
                        profileId = profileId,
                        isoCode = "uk",
                        username = "Ijon",
                        speakerName = "",
                    ),
                ),
            )

            val loaded = repository.loadAll()
            assertEquals(1, loaded.size)
            val item = loaded.first()
            assertEquals("мова", item.entry.label)
            assertTrue(item.entry.audioFile!!.exists())
            // The source file was moved, not copied, into durable storage.
            assertTrue(!audioFile.exists())

            repository.markCommonsDone(item.id)
            repository.markP443Done(item.id)
            val updated = repository.loadAll().first()
            assertTrue(updated.commonsDone)
            assertTrue(updated.p443Done)

            val durableFile = updated.entry.audioFile!!
            repository.delete(item.id)
            assertTrue(repository.loadAll().isEmpty())
            assertTrue(!durableFile.exists())
        }

    @Test
    fun pendingUploadRepository_dropsAnItemWhoseAudioFileIsGone() =
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val profileId = insertProfile()
            val repository = RoomPendingUploadRepository(database.pendingUploadDao(), context)
            val audioFile = File.createTempFile("take", ".ogg").apply { deleteOnExit() }

            repository.saveForLater(
                listOf(
                    PendingUploadItem(
                        entry = QueueEntry(label = "мова", kind = EntryKind.ITEM, detail = "item", qid = "Q42", audioFile = audioFile),
                        profileId = profileId,
                        isoCode = "uk",
                        username = "Ijon",
                        speakerName = "",
                    ),
                ),
            )
            val durableFile = repository.loadAll().first().entry.audioFile!!
            durableFile.delete()

            assertTrue(repository.loadAll().isEmpty())
        }

    @Test
    fun settingsRepository_roundTripsPreferences() =
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testFile = File(context.cacheDir, "test_settings.preferences_pb")
            testFile.delete()
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })
            val repository = DataStoreSettingsRepository(dataStore)

            val defaults = repository.settings.first()
            assertEquals(false, defaults.autoUseLastProfile)
            assertNull(defaults.lastUsedProfileId)

            repository.setAutoUseLastProfile(true)
            repository.setLastUsedProfileId(42L)

            val updated = repository.settings.first()
            assertEquals(true, updated.autoUseLastProfile)
            assertEquals(42L, updated.lastUsedProfileId)

            testFile.delete()
        }
}
