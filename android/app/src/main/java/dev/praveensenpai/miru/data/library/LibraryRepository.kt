package dev.praveensenpai.miru.data.library

import android.content.Context
import dev.praveensenpai.miru.data.download.MiruDatabase
import dev.praveensenpai.miru.data.model.VideoCard
import dev.praveensenpai.miru.data.model.VideoDetail
import kotlinx.coroutines.flow.Flow

/**
 * Subscriptions and watch history, backed by Room.
 *
 * This is deliberately local-first: subscribing or recording a watch never
 * touches the network, so the tabs work offline and instantly. Optional server
 * sync is a later concern and would slot in behind this same API.
 */
class LibraryRepository(context: Context) {

    private val dao = MiruDatabase.get(context).library()

    fun observeSubscriptions(): Flow<List<SubscriptionEntity>> = dao.observeSubscriptions()

    fun observeSubscribed(channelId: String): Flow<Boolean> = dao.observeSubscribed(channelId)

    fun observeHistory(limit: Int = 100): Flow<List<HistoryEntity>> = dao.observeHistory(limit)

    suspend fun subscribe(channelId: String, name: String, avatar: String?) {
        dao.subscribe(
            SubscriptionEntity(
                channelId = channelId,
                name = name,
                avatar = avatar,
                subscribedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unsubscribe(channelId: String) = dao.unsubscribe(channelId)

    /** Record a play. Re-watching refreshes recency rather than duplicating. */
    suspend fun recordWatch(detail: VideoDetail) {
        dao.record(
            HistoryEntity(
                videoId = detail.id,
                title = detail.title,
                channel = detail.channel.name,
                channelId = detail.channel.id,
                thumbnail = detail.thumbnail,
                duration = detail.duration,
                watchedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeHistory(videoId: String) = dao.deleteHistory(videoId)

    suspend fun clearHistory() = dao.clearHistory()
}

/** Convert a history row back into a feed card, so the Library tab reuses [VideoCardItem]. */
fun HistoryEntity.toVideoCard(): VideoCard = VideoCard(
    id = videoId,
    title = title,
    thumbnail = thumbnail,
    duration = duration,
    channel = channel,
    channelId = channelId,
)
