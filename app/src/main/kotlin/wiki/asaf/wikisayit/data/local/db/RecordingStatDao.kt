package wiki.asaf.wikisayit.data.local.db

import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class EntryTypeCount(val entryType: RecordingEntryType, val count: Int)

data class MonthlyEntryTypeCount(val yearMonth: String, val entryType: RecordingEntryType, val count: Int)

@androidx.room.Dao
interface RecordingStatDao {
    @Insert
    suspend fun insert(stat: RecordingStatEntity)

    @Query("SELECT entry_type AS entryType, COUNT(*) AS count FROM recording_stats GROUP BY entry_type")
    fun observeTotalsByType(): Flow<List<EntryTypeCount>>

    @Query("SELECT COUNT(*) FROM recording_stats WHERE profile_id = :profileId")
    fun observeCountForProfile(profileId: Long): Flow<Int>

    @Query(
        """
        SELECT strftime('%Y-%m', timestamp_millis / 1000, 'unixepoch') AS yearMonth,
               entry_type AS entryType,
               COUNT(*) AS count
        FROM recording_stats
        GROUP BY yearMonth, entry_type
        ORDER BY yearMonth
        """,
    )
    fun observeMonthlyTotalsByType(): Flow<List<MonthlyEntryTypeCount>>
}
