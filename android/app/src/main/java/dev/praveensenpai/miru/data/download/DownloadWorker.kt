package dev.praveensenpai.miru.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.praveensenpai.miru.data.extract.InnerTube
import dev.praveensenpai.miru.data.extract.YtExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads a single video's media to app storage.
 *
 * Miru is serverless: the stream URL is extracted on-device via [YtExtractor]
 * and fetched directly with the same VR User-Agent that obtained it, so
 * googlevideo serves the bytes without any proxy. Only muxed (video+audio)
 * streams are supported for now; separate-track muxing is a later task.
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

        dao.updateProgress(id, -1, DownloadState.RUNNING)

        try {
            val streams = YtExtractor.streams(id)
            val stream = streams.combined.maxByOrNull { it.height ?: 0 }
                ?: streams.video.maxByOrNull { it.height ?: 0 }
            if (stream == null) {
                dao.updateProgress(id, 0, DownloadState.FAILED)
                return@withContext Result.failure()
            }
            val target = File(downloadDir(applicationContext), "$id.${stream.ext ?: "mp4"}")

            val request = Request.Builder()
                .url(stream.url)
                .header("User-Agent", InnerTube.USER_AGENT)
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

        /**
         * Dedicated client for media downloads: no overall read timeout, because
         * a long video streams for minutes, but a firm connect timeout.
         */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        }

        /** App-private directory for downloaded media; created on first use. */
        fun downloadDir(context: Context): File =
            File(context.filesDir, "downloads").apply { mkdirs() }
    }
}
