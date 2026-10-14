package dev.praveensenpai.miru.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import dev.praveensenpai.miru.data.extract.InnerTube

/**
 * Player data source.
 *
 * googlevideo gates the `rqh=1` URLs InnerTube hands out: a request with no
 * `Range` header, or one asking for too many bytes at once, is rejected with
 * HTTP 403, while a small bounded range is served normally. Media3 by default
 * sends either no range or an open-ended one, which is why playback of these
 * videos failed.
 *
 * [BoundedDataSource] turns each open into a run of small bounded requests and
 * stitches them back together, so the player still sees one continuous stream.
 */
@UnstableApi
class MiruDataSourceFactory : DataSource.Factory {

    private val upstream = DefaultHttpDataSource.Factory()
        .setUserAgent(InnerTube.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)

    override fun createDataSource(): DataSource =
        BoundedDataSource(upstream.createDataSource())
}

/**
 * Serves a stream through a sequence of small ranged reads.
 *
 * The upstream source is opened for at most [CHUNK] bytes at a time; when a
 * window is exhausted the next one is opened just after it. A window that is
 * rejected with 403 (the endpoint is throttling) is retried after a short
 * backoff before the read is given up on.
 */
@UnstableApi
private class BoundedDataSource(
    private val delegate: DataSource,
) : DataSource {

    private var baseSpec: DataSpec? = null

    /** File offset the next window starts at. */
    private var nextPosition = 0L

    /** Bytes the current window can still return. */
    private var windowRemaining = 0L

    /** Bytes read from the current window so far. */
    private var windowRead = 0L

    /** Bytes still owed to the reader, or [Long.MAX_VALUE] when unbounded. */
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val unbounded = dataSpec.length == C.LENGTH_UNSET.toLong()
        baseSpec = dataSpec
        nextPosition = dataSpec.position
        remaining = if (unbounded) Long.MAX_VALUE else dataSpec.length
        windowRemaining = 0
        windowRead = 0
        if (remaining != 0L) openWindow()
        return if (unbounded) C.LENGTH_UNSET.toLong() else dataSpec.length
    }

    /** Open the next window, retrying transient throttle rejections. */
    private fun openWindow() {
        val spec = baseSpec ?: return
        val want = if (remaining == Long.MAX_VALUE) CHUNK else minOf(CHUNK, remaining)
        val sub = spec.buildUpon()
            .setPosition(nextPosition)
            .setLength(want)
            .build()
        var attempt = 0
        while (true) {
            try {
                delegate.open(sub)
                windowRemaining = want
                windowRead = 0
                return
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode != 403 || attempt >= RETRIES) throw e
                delegate.close()
                Thread.sleep(BACKOFF_MS shl attempt)
                attempt++
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
            if (windowRemaining <= 0L) {
                nextPosition += windowRead
                if (remaining <= 0L) return C.RESULT_END_OF_INPUT
                openWindow()
            }
            val toRead = minOf(length.toLong(), windowRemaining, remaining).toInt()
            if (toRead <= 0) return C.RESULT_END_OF_INPUT
            val read = delegate.read(buffer, offset, toRead)
            if (read == C.RESULT_END_OF_INPUT) {
                // The server closed this window early; fall through to the next.
                nextPosition += windowRead
                windowRemaining = 0
                if (remaining == Long.MAX_VALUE) return C.RESULT_END_OF_INPUT
                continue
            }
            windowRemaining -= read
            windowRead += read
            if (remaining != Long.MAX_VALUE) remaining -= read
            return read
        }
    }

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun getUri(): Uri? = delegate.uri

    override fun close() = delegate.close()

    private companion object {
        /**
         * Bytes requested per connection. googlevideo serves an `rqh=1` URL
         * only up to a per-request cap; ask for more and it answers 403. The
         * cap shrinks the harder the endpoint is hit, so this stays small and
         * playback simply opens the next window as it reads on.
         */
        const val CHUNK = 1024L * 1024

        /** Re-opens attempted for a throttled (403) request. */
        const val RETRIES = 3

        /** First backoff step; doubled after each failed attempt. */
        const val BACKOFF_MS = 250L
    }
}
