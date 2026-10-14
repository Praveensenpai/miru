package dev.praveensenpai.miru.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import dev.praveensenpai.miru.data.extract.InnerTube
import dev.praveensenpai.miru.data.extract.YtExtractor

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
 * It is deliberately defensive: each window opens on a fresh connection, a
 * refused window is recovered by minting a fresh URL for the same rendition,
 * and a window past the end of the resource is treated as end-of-input rather
 * than an error.
 */
@UnstableApi
class MiruDataSourceFactory(
    /**
     * Video the stream belongs to. When set, a window that keeps failing is
     * recovered by minting a fresh URL for the same rendition; when null the
     * source can only retry the URL it was given.
     */
    private val videoId: String? = null,
) : DataSource.Factory {

    private val upstream = DefaultHttpDataSource.Factory()
        .setUserAgent(InnerTube.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(30_000)
        .setReadTimeoutMs(30_000)

    override fun createDataSource(): DataSource =
        BoundedDataSource(upstream.createDataSource(), videoId)
}

/**
 * Serves a stream through a sequence of small ranged reads.
 *
 * The upstream source is opened for at most [CHUNK_START] bytes at a time; when
 * a window is exhausted the next one is opened just after it. A window that is
 * rejected with 403 once past the first window is recovered by minting a fresh
 * URL for the same rendition and continuing at the same offset, so a URL that
 * stops serving mid-stream does not kill playback. If no video id is available,
 * the window is instead retried on a fresh connection with a halved request
 * size, so throttling degrades into smaller windows.
 */
@UnstableApi
private class BoundedDataSource(
    private val delegate: DataSource,
    private val videoId: String?,
) : DataSource {

    private var baseSpec: DataSpec? = null

    /** URL serving the current window; replaced when a fresh one is minted. */
    private var currentUrl: String? = null

    /**
     * URI of the load currently being served.
     *
     * ExoPlayer re-opens the source with the same [DataSpec] after a failed read,
     * and each re-open used to hand out a fresh refresh budget, so a throttled
     * endpoint could be re-minted against without limit. The budget is only
     * reset when the URI actually changes, i.e. for a genuinely new load.
     */
    private var loadUri: String? = null

    /** Fresh URLs fetched in a row without managing to read anything. */
    private var refreshStreak = 0

    /** File offset the next window starts at. */
    private var nextPosition = 0L

    /** Bytes the current window can still return. */
    private var windowRemaining = 0L

    /** Bytes read from the current window so far. */
    private var windowRead = 0L

    /** Bytes still owed to the reader, or [Long.MAX_VALUE] when unbounded. */
    private var remaining = 0L

    /** Current window size; shrinks when the endpoint throttles us. */
    private var windowSize = CHUNK_START

    override fun open(dataSpec: DataSpec): Long {
        val unbounded = dataSpec.length == C.LENGTH_UNSET.toLong()
        baseSpec = dataSpec
        nextPosition = dataSpec.position
        remaining = if (unbounded) Long.MAX_VALUE else dataSpec.length
        windowSize = CHUNK_START
        windowRemaining = 0
        windowRead = 0
        val uri = dataSpec.uri.toString()
        currentUrl = uri
        // Only a new load gets a fresh refresh budget; a retry of the same load
        // keeps its remaining one, so a throttled endpoint cannot be hammered by
        // the player's re-open loop. A new load arrives as a new DataSource (and
        // a new DataSpec URI), e.g. when the user hits play again.
        if (loadUri != uri) {
            loadUri = uri
            refreshStreak = 0
        }
        if (remaining != 0L) openWindow()
        return if (unbounded) C.LENGTH_UNSET.toLong() else dataSpec.length
    }

    /**
     * Open the next window.
     *
     * The range is expressed as an `&range=start-end` query parameter on a
     * request that carries **no** `Range` header. These `rqh=1` URLs reject a
     * header-based range past the first window with 403, but honour the query
     * parameter and return exactly that many bytes. The delegate is therefore
     * opened at position 0 with an unknown length; [read] caps how much it
     * consumes.
     */
    private fun openWindow() {
        val base = baseSpec ?: return
        var attempt = 0
        while (true) {
            val uri = currentUrl ?: return
            val want = if (remaining == Long.MAX_VALUE) windowSize else minOf(windowSize, remaining)
            val sub = base.buildUpon()
                .setUri(withRange(uri, nextPosition, nextPosition + want - 1))
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
            runCatching { delegate.close() }
            try {
                val resolved = delegate.open(sub)
                windowRemaining =
                    if (resolved == C.LENGTH_UNSET.toLong()) want else minOf(resolved, want)
                windowRead = 0
                refreshStreak = 0
                return
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                val code = e.responseCode
                Log.d(TAG, "open pos=$nextPosition len=$want code=$code attempt=$attempt")
                if (code == 416 || (code == 400 && nextPosition > 0L)) {
                    // Past the end of the resource: nothing left to serve.
                    // The query-param form reports this as 400, the header form
                    // as 416. Only treat 400 as EOF once we have served bytes,
                    // so a genuinely malformed first request still surfaces.
                    windowRemaining = 0
                    windowRead = 0
                    remaining = 0
                    return
                }
                if (code != 403) throw e
                // Past the first window a 403 means this URL has stopped serving.
                // Mint a fresh one and carry on at the same offset straight away:
                // these URLs are short-lived, so refreshing is a normal part of
                // long playback, not an error.
                if (nextPosition > 0L) {
                    if (refreshUrl()) {
                        attempt = 0
                        windowSize = CHUNK_START
                        continue
                    }
                    throw e
                }
                // At the very start a 403 may just be transient, so retry with a
                // smaller window and a growing backoff before re-minting.
                if (attempt >= RETRIES) {
                    if (refreshUrl()) {
                        attempt = 0
                        windowSize = CHUNK_START
                        continue
                    }
                    throw e
                }
                if (windowSize > CHUNK_MIN) windowSize = maxOf(windowSize / 2, CHUNK_MIN)
                val backoff = minOf(BACKOFF_MS shl attempt, BACKOFF_MAX_MS)
                Log.d(TAG, "throttled; windowSize=$windowSize backoff=${backoff}ms")
                Thread.sleep(backoff)
                attempt++
            }
        }
    }

    /**
     * Replace [currentUrl] with a freshly minted URL for the same rendition.
     *
     * The itag and its `xtags` are carried over so the replacement is the exact
     * track being played, never a different language of the same itag. Returns
     * whether a usable URL was obtained. Consecutive refresh failures are capped
     * so a persistently failing endpoint cannot spin forever. This blocks on the
     * network, which is fine: it runs on the player's loader thread.
     */
    private fun refreshUrl(): Boolean {
        val video = videoId ?: return false
        if (refreshStreak >= MAX_REFRESHES) return false
        val url = currentUrl ?: return false
        val itag = ITAG.find(url)?.groupValues?.getOrNull(1) ?: return false
        val xtags = XTAGS.find(url)?.groupValues?.getOrNull(1)
        refreshStreak++
        val fresh = runCatching { YtExtractor.freshStreamUrl(video, itag, xtags) }.getOrNull()
        if (fresh.isNullOrBlank()) return false
        currentUrl = fresh
        Log.d(TAG, "refreshed itag=$itag xtags=$xtags (streak $refreshStreak)")
        return true
    }

    /** Append a `range=start-end` query parameter, replacing any existing one. */
    private fun withRange(url: String, start: Long, end: Long): String {
        val stripped = url.replace(Regex("[&?]range=[^&]*"), "")
        val sep = if (stripped.contains('?')) '&' else '?'
        return "$stripped${sep}range=$start-$end"
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
            if (windowRemaining <= 0L) {
                nextPosition += windowRead
                if (remaining <= 0L) return C.RESULT_END_OF_INPUT
                openWindow()
                if (windowRemaining <= 0L) {
                    if (remaining <= 0L) return C.RESULT_END_OF_INPUT
                    // A zero-length window with bytes still owed means the
                    // window could not be served; report end rather than spin.
                    return C.RESULT_END_OF_INPUT
                }
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
        const val TAG = "MiruDS"

        /**
         * First window size; also the size windows grow back toward.
         *
         * Each window is one HTTP request, and googlevideo rate-limits by request
         * count, so windows are deliberately large: a 1 MiB window for a 1080p
         * video is a request every few seconds, which trips throttling long before
         * the video ends. The 403 fallback halves this toward [CHUNK_MIN].
         */
        const val CHUNK_START = 4L * 1024 * 1024

        /** Smallest window we will fall back to under throttling. */
        const val CHUNK_MIN = 1024L * 1024

        /** Re-opens attempted for a throttled (403) request. */
        const val RETRIES = 4

        /** First backoff step; doubled after each failed attempt. */
        const val BACKOFF_MS = 400L

        /** Backoff ceiling so a stubborn window still fails in reasonable time. */
        const val BACKOFF_MAX_MS = 8000L

        /** Fresh URLs fetched per open before giving up on the rendition. */
        const val MAX_REFRESHES = 4

        /** Pulls the `itag` out of a googlevideo URL. */
        val ITAG = Regex("[?&]itag=(\\d+)")

        /** Pulls the `xtags` (audio-track identity) out of a googlevideo URL. */
        val XTAGS = Regex("[?&]xtags=([^&]*)")
    }
}
