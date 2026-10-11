package dev.praveensenpai.miru.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A downloaded (or in-progress) video stored on device. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channel: String?,
    val thumbnail: String?,
    /** Absolute path to the finished media file. Null until complete. */
    val filePath: String?,
    /** 0..100. -1 indicates an unknown/indeterminate state. */
    val progress: Int,
    val state: String,
    val createdAt: Long,
)

/** Lifecycle states for a download, stored as strings for forward-compat. */
object DownloadState {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
}