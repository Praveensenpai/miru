package dev.praveensenpai.miru.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One watch-history entry per video.
 *
 * Re-watching a video upserts the same row and refreshes [watchedAt], so the
 * history is a recency-ordered set rather than an append-only log. That is what
 * a "Continue watching" list needs.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String?,
    val channelId: String?,
    val thumbnail: String?,
    val duration: Long?,
    val watchedAt: Long,
)
