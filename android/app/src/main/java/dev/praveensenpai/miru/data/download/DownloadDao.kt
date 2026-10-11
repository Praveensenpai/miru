package dev.praveensenpai.miru.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun find(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, state = :state WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, state: String)

    @Query("UPDATE downloads SET filePath = :path, progress = 100, state = :state WHERE id = :id")
    suspend fun markDone(id: String, path: String, state: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}