package wiki.asaf.wikisayit.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkippedEntryDao {
    /** Re-skipping an entry just refreshes its timestamp rather than failing on the key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SkippedEntryEntity)

    @Query("SELECT entity_id FROM skipped_entries")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM skipped_entries")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM skipped_entries")
    suspend fun deleteAll()
}
