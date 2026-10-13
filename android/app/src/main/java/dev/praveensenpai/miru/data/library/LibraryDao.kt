package dev.praveensenpai.miru.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Subscriptions and watch history.
 *
 * Both are observed as flows so the Subscriptions and Library tabs update the
 * moment the player records a change — no manual refresh.
 */
@Dao
interface LibraryDao {

    // --- Subscriptions ---

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :channelId)")
    fun observeSubscribed(channelId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribe(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun unsubscribe(channelId: String)

    // --- History ---

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun deleteHistory(videoId: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}