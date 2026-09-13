package wiki.asaf.wikisayit.data.stats

import kotlinx.coroutines.flow.Flow
import wiki.asaf.wikisayit.data.local.db.EntryTypeCount
import wiki.asaf.wikisayit.data.local.db.MonthlyEntryTypeCount
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.RecordingStatDao
import wiki.asaf.wikisayit.data.local.db.RecordingStatEntity
import java.time.Clock
import javax.inject.Inject

interface StatsRepository {
    suspend fun recordContribution(
        profileId: Long,
        entryType: RecordingEntryType,
    )

    fun observeTotalsByType(): Flow<List<EntryTypeCount>>

    fun observeMonthlyTotalsByType(): Flow<List<MonthlyEntryTypeCount>>
}

class RoomStatsRepository
    @Inject
    constructor(
        private val recordingStatDao: RecordingStatDao,
        private val clock: Clock = Clock.systemUTC(),
    ) : StatsRepository {
        override suspend fun recordContribution(
            profileId: Long,
            entryType: RecordingEntryType,
        ) {
            recordingStatDao.insert(
                RecordingStatEntity(profileId = profileId, entryType = entryType, timestampMillis = clock.millis()),
            )
        }

        override fun observeTotalsByType(): Flow<List<EntryTypeCount>> = recordingStatDao.observeTotalsByType()

        override fun observeMonthlyTotalsByType(): Flow<List<MonthlyEntryTypeCount>> =
            recordingStatDao.observeMonthlyTotalsByType()
    }
