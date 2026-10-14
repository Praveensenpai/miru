package dev.praveensenpai.miru.player

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Contract between the player UI and [PlaybackService].
 *
 * Playback lives in the service so it survives the player screen closing; the
 * UI sends these commands to hand the service a new video. The command carries
 * plain strings only, so it stays trivially inspectable in logs.
 */
object PlayerCommands {

    const val ACTION_SET_MEDIA = "dev.praveensenpai.miru.SET_MEDIA"

    /** The single custom command the UI is allowed to send. */
    val SET_MEDIA = SessionCommand(ACTION_SET_MEDIA, Bundle.EMPTY)

    const val KEY_PLAY_URL = "playUrl"
    const val KEY_AUDIO_URL = "audioUrl"
    /** Identifies the video so the data source can re-mint expired stream URLs. */
    const val KEY_VIDEO_ID = "videoId"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ARTWORK = "artwork"
}
