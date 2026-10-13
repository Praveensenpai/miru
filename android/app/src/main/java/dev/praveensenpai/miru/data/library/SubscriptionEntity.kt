package dev.praveensenpai.miru.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A channel the user subscribes to. Local-only; no account involved. */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val avatar: String?,
    val subscribedAt: Long,
)
