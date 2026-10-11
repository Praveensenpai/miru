package dev.praveensenpai.miru.data.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.praveensenpai.miru.data.model.VideoDetail

/**
 * Fire-and-forget entry point for starting a download.
 *
 * Both the player and (later) long-press menus need to queue a download without
 * pulling in a ViewModel, so the Room write and WorkManager enqueue live here.
 */
object DownloadEnqueuer {

    fun enqueue(
        context: Context,
        detail: VideoDetail,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val dao = MiruDatabase.get(appContext).downloads()
            dao.upsert(
                DownloadEntity(
                    id = detail.id,
                    title = detail.title,
                    channel = detail.channel.name,
                    thumbnail = detail.thumbnail,
                    filePath = null,
                    progress = 0,
                    state = DownloadState.QUEUED,
                    createdAt = System.currentTimeMillis(),
                )
            )

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_ID to detail.id))
                .build()
            WorkManager.getInstance(appContext).enqueue(request)
        }
    }
}
