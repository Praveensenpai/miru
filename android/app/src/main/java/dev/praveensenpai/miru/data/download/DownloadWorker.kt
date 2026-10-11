package dev.praveensenpai.miru.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.praveensenpai.miru.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Downloads a single video's media to app storage.
 *
 * v1 downloads one file: the best muxed stream when YouTube offers it,
 * otherwise the highest-quality video-only track. Downloading separate video
 * and audio tracks and muxing them together is a v2 task (it needs Media3's
 * Transformer or a MediaMuxer pass). This keeps the first release honest and
 * small rather than half-implementing muxing.
 *
 * Progress is written to the Room row as it streams, so the Downloads tab can
 * observe it live.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val dao = MiruDatabase.get(applicationContext).downloads()
        val client = NetworkModule.downloadClient

        dao.updateProgress(id, -1, DownloadState.RUNNING)

        try {
            val streams = NetworkModule.api.streams(id)
            // Prefer a muxed stream (has both audio and video in one file).
            val chosen = streams.combined.maxByOrNull { it.height ?: 0 }
                ?: streams.video.maxByOrNull { it.height ?: 0 }
                ?: return@withContext Result.failure()

            val ext = chosen.ext ?: "mp4"
            val target = File(downloadDir(applicationContext), "$id.$ext")

            val request = Request.Builder()
                .url(chosen.url)
                .header("User-Agent", "miru/0.0.1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    dao.updateProgress(id, 0, DownloadState.FAILED)
                    return@withContext Result.retry()
                }
                val body = response.body ?: run {
                    dao.updateProgress(id, 0, DownloadState.FAILED)
                    return@withContext Result.retry()
                }
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val pct = ((written * 100) / total).toInt()
                                dao.updateProgress(id, pct, DownloadState.RUNNING)
                            }
                        }
                    }
                }
            }

            dao.markDone(id, target.absolutePath, DownloadState.DONE)
            Result.success()
        } catch (e: Exception) {
            dao.updateProgress(id, 0, DownloadState.FAILED)
            Result.retry()
        }
    }

    companion object {
        const val KEY_ID = "video_id"

        /** App-private directory for downloaded media; created on first use. */
        fun downloadDir(context: Context): File =
            File(context.filesDir, "downloads").apply { mkdirs() }
    }
}