package dev.praveensenpai.miru.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Foreground playback service.
 *
 * Owns the [ExoPlayer] and its [MediaSession]. Keeping the player here rather
 * than in the player screen is what makes background audio, the system media
 * notification, and picture-in-picture work: the UI attaches to this session as
 * a [androidx.media3.session.MediaController] and can come and go without
 * interrupting playback.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    /** Grants the UI the set-media command, and nothing else custom. */
    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PlayerCommands.SET_MEDIA)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction != PlayerCommands.ACTION_SET_MEDIA) {
                return super.onCustomCommand(session, controller, command, args)
            }
            applyMedia(args)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** Build the media source from the URLs and metadata the UI supplied. */
    private fun applyMedia(args: Bundle) {
        val playUrl = args.getString(PlayerCommands.KEY_PLAY_URL) ?: return
        val audioUrl = args.getString(PlayerCommands.KEY_AUDIO_URL)
        val videoId = args.getString(PlayerCommands.KEY_VIDEO_ID)

        // Built per video so the data source can re-mint stream URLs that stop
        // serving mid-playback.
        val sourceFactory = ProgressiveMediaSource.Factory(MiruDataSourceFactory(videoId))

        val metadata = MediaMetadata.Builder()
            .setTitle(args.getString(PlayerCommands.KEY_TITLE))
            .setArtist(args.getString(PlayerCommands.KEY_ARTIST))
            .setArtworkUri(args.getString(PlayerCommands.KEY_ARTWORK)?.toUri())
            .build()

        val videoItem = MediaItem.Builder()
            .setUri(playUrl)
            .setMediaMetadata(metadata)
            .build()
        val videoSource = sourceFactory.createMediaSource(videoItem)

        // Metadata rides on the first source, so the notification shows the
        // real title and artwork even when audio is merged in separately.
        val source = if (audioUrl != null) {
            MergingMediaSource(
                videoSource,
                sourceFactory.createMediaSource(MediaItem.fromUri(audioUrl)),
            )
        } else {
            videoSource
        }

        player?.run {
            setMediaSource(source)
            prepare()
            play()
        }
    }
}
