package wiki.asaf.wikisayit.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUploadDao {
    @Insert
    suspend fun insert(entity: PendingUploadEntity): Long

    @Query("SELECT * FROM pending_uploads")
    suspend fun getAll(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE id = :id")
    suspend fun getById(id: Long): PendingUploadEntity?

    @Update
    suspend fun update(entity: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_uploads")
    fun observeCount(): Flow<Int>
}
