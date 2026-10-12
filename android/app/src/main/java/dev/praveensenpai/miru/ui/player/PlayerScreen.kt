package dev.praveensenpai.miru.ui.player

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.praveensenpai.miru.data.download.DownloadEnqueuer
import dev.praveensenpai.miru.data.model.SponsorSegment
import dev.praveensenpai.miru.data.remote.NetworkModule
import kotlinx.coroutines.delay

/**
 * Full-screen player.
 *
 * The video surface is edge to edge. Controls are hidden by default and reveal
 * on tap, then auto-hide after a few seconds — the gesture-first pattern
 * LibreTube popularised, rather than a permanent toolbar.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(
        key = videoId,
        factory = PlayerViewModelFactory(videoId),
    ),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val s = state) {
            is PlayerUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            is PlayerUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Playback failed", color = Color.White)
                    Text(
                        text = s.message,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }

            is PlayerUiState.Ready -> {
                VideoSurface(
                    playUrl = s.playUrl,
                    audioUrl = s.audioUrl,
                    segments = s.segments,
                    onBack = onBack,
                    onDownload = {
                        DownloadEnqueuer.enqueue(
                            context = context,
                            detail = s.detail,
                        )
                    },
                )

                // Metadata below the fold, scrollable over the paused surface.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = s.detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    s.detail.channel.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    playUrl: String,
    audioUrl: String?,
    segments: List<SponsorSegment>,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var skipNotice by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // googlevideo locks each URL to the IP of the host that extracted it, so a
    // direct fetch from the device can 403. On that error we retry once through
    // the server's /proxy, which fetches from the correct IP.
    var useProxy by remember(playUrl) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                    ?.responseCode
                if (code == 403 && !useProxy) useProxy = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Build the media source. When the video track is video-only, merge the
    // separate audio track so playback has sound.
    LaunchedEffect(playUrl, audioUrl, useProxy) {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setUserAgent("miru/0.0.1")
                .setAllowCrossProtocolRedirects(true),
        )
        val sourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

        val videoUri = if (useProxy) NetworkModule.proxyUrl(playUrl) else playUrl
        val videoSource = sourceFactory.createMediaSource(MediaItem.fromUri(videoUri))
        val source = if (audioUrl != null) {
            val audioUri = if (useProxy) NetworkModule.proxyUrl(audioUrl) else audioUrl
            val audioSource = sourceFactory.createMediaSource(MediaItem.fromUri(audioUri))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        player.setMediaSource(source)
        player.prepare()
    }

    // SponsorBlock: poll playback position and seek past any segment we are
    // inside. Each segment is skipped at most once, so a user who rewinds into
    // a sponsor read is not yanked forward again.
    val skipped = remember { mutableSetOf<Int>() }
    LaunchedEffect(playUrl, segments, sponsorBlockEnabled) {
        while (true) {
            delay(500)
            if (sponsorBlockEnabled && player.isPlaying) {
                val pos = player.currentPosition / 1000.0
                segments.forEachIndexed { index, seg ->
                    val inside = pos >= seg.start && pos < seg.end
                    if (inside && skipped.add(index)) {
                        android.util.Log.i(
                            "MiruPlayer",
                            "SponsorBlock skip: ${seg.category} $pos -> ${seg.end}",
                        )
                        player.seekTo((seg.end * 1000).toLong())
                        skipNotice = true
                    }
                }
            }
        }
    }

    // Fade the skip notice after a moment.
    LaunchedEffect(skipNotice) {
        if (skipNotice) {
            delay(2500)
            skipNotice = false
        }
    }

    // Auto-hide controls after 3 seconds of being shown.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { player.setVideoSurfaceView(it) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                    )
                }

                if (segments.isNotEmpty()) {
                    IconButton(
                        onClick = { sponsorBlockEnabled = !sponsorBlockEnabled },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 56.dp),
                    ) {
                        Icon(
                            Icons.Filled.ContentCut,
                            contentDescription = if (sponsorBlockEnabled) {
                                "SponsorBlock on"
                            } else {
                                "SponsorBlock off"
                            },
                            tint = if (sponsorBlockEnabled) Color.White else Color.Gray,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = skipNotice,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 96.dp),
        ) {
            Text(
                text = "Skipped sponsor",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}