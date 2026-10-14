package dev.praveensenpai.miru.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import android.util.Rational
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.praveensenpai.miru.data.download.DownloadEnqueuer
import dev.praveensenpai.miru.data.model.SponsorSegment
import dev.praveensenpai.miru.player.PlayerCommands
import dev.praveensenpai.miru.player.PlaybackService
import kotlinx.coroutines.delay

/**
 * Full-screen player.
 *
 * The video surface is edge to edge. Controls are hidden by default and reveal
 * on tap, then auto-hide after a few seconds — the gesture-first pattern
 * LibreTube popularised, rather than a permanent toolbar.
 *
 * Playback itself lives in [PlaybackService]; this screen only sends it the
 * chosen streams and renders the surface, so audio keeps playing (and PiP keeps
 * showing) when the screen is backgrounded.
 */
@Composable
fun PlayerScreen(
    videoId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        key = videoId,
        factory = PlayerViewModelFactory(videoId, context.applicationContext),
    )
    val state by viewModel.state.collectAsState()
    val controller = rememberMediaController(context)
    val subscribed by viewModel.subscribed.collectAsState()

    // Hand the resolved streams to the service once, when both the controller
    // is connected and the video is ready.
    LaunchedEffect(controller, state) {
        val ready = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        c.sendCustomCommand(
            PlayerCommands.SET_MEDIA,
            bundleOf(
                PlayerCommands.KEY_PLAY_URL to ready.playUrl,
                PlayerCommands.KEY_AUDIO_URL to ready.audioUrl,
                PlayerCommands.KEY_VIDEO_ID to ready.detail.id,
                PlayerCommands.KEY_TITLE to ready.detail.title,
                PlayerCommands.KEY_ARTIST to ready.detail.channel.name,
                PlayerCommands.KEY_ARTWORK to ready.detail.thumbnail,
            ),
        )
    }

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
                if (controller == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                } else {
                    VideoSurface(
                        controller = controller,
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
                        s.votes?.let { votes ->
                            Text(
                                text = "👍 ${formatCount(votes.likes)}   👎 ${formatCount(votes.dislikes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!s.detail.channel.id.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = viewModel::toggleSubscription,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(if (subscribed) "Subscribed" else "Subscribe")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Connect to [PlaybackService] and expose its [MediaController].
 *
 * The future is released on dispose, which disconnects this UI but leaves the
 * session — and therefore playback — running.
 */
@Composable
private fun rememberMediaController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = future.get() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { MediaController.releaseFuture(future) }
    }

    return controller
}

@Composable
private fun VideoSurface(
    controller: MediaController,
    segments: List<SponsorSegment>,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var skipNotice by remember { mutableStateOf(false) }
    var isInPip by remember { mutableStateOf(false) }

    // Mirror playback state and drive picture-in-picture from the surface.
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, controller) {
        val act = activity ?: return@DisposableEffect onDispose {}
        val pipListener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        val leaveListener = Runnable {
            if (controller.isPlaying) enterPip(act)
        }
        act.addOnPictureInPictureModeChangedListener(pipListener)
        act.addOnUserLeaveHintListener(leaveListener)
        onDispose {
            act.removeOnPictureInPictureModeChangedListener(pipListener)
            act.removeOnUserLeaveHintListener(leaveListener)
        }
    }

    // Attach the surface the controller renders into. Re-attached whenever the
    // controller is swapped so video output follows the player.
    val surfaceView = remember { SurfaceView(context) }
    LaunchedEffect(controller) {
        controller.setVideoSurfaceView(surfaceView)
    }
    DisposableEffect(controller) {
        onDispose { controller.clearVideoSurfaceView(surfaceView) }
    }

    // SponsorBlock: poll playback position and seek past any segment we are
    // inside. Each segment is skipped at most once, so a user who rewinds into
    // a sponsor read is not yanked forward again.
    val skipped = remember { mutableSetOf<Int>() }
    LaunchedEffect(segments, sponsorBlockEnabled) {
        while (true) {
            delay(500)
            if (sponsorBlockEnabled && controller.isPlaying) {
                val pos = controller.currentPosition / 1000.0
                segments.forEachIndexed { index, seg ->
                    val inside = pos >= seg.start && pos < seg.end
                    if (inside && skipped.add(index)) {
                        controller.seekTo((seg.end * 1000).toLong())
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible && !isInPip,
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
                        if (controller.isPlaying) controller.pause() else controller.play()
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
            visible = skipNotice && !isInPip,
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

/** 1234567 -> "1.2M". Compact vote counts for the metadata row. */
private fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

/** Enter picture-in-picture with a 16:9 window, on devices that support it. */
private fun enterPip(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()
    activity.enterPictureInPictureMode(params)
}

/** Unwrap the [ComponentActivity] hosting a (possibly wrapped) composable context. */
private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
