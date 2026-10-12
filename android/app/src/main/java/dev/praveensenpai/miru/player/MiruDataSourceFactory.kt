package dev.praveensenpai.miru.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import dev.praveensenpai.miru.data.remote.NetworkModule

/**
 * Data source with transparent proxy fallback.
 *
 * googlevideo locks each URL to the IP of the host that extracted it (the
 * server), so a direct fetch from the device intermittently answers 403. When
 * that happens we retry the same request through the server's `/proxy`, which
 * fetches from the correct IP. This lives below the player, so seeking, resume,
 * and background playback all get the fallback for free.
 */
@UnstableApi
class MiruDataSourceFactory : DataSource.Factory {

    private val upstream = DefaultHttpDataSource.Factory()
        .setUserAgent("miru/0.0.1")
        .setAllowCrossProtocolRedirects(true)

    override fun createDataSource(): DataSource =
        ProxyFallbackDataSource(upstream.createDataSource())
}

@UnstableApi
private class ProxyFallbackDataSource(private val upstream: DataSource) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        return try {
            upstream.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403) throw e
            upstream.close()
            upstream.open(dataSpec.withUri(proxyUri(dataSpec.uri)))
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        upstream.responseHeaders

    override fun close() = upstream.close()

    private fun proxyUri(uri: Uri): Uri =
        Uri.parse(NetworkModule.proxyUrl(uri.toString()))
}
