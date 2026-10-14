package dev.praveensenpai.miru.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide preferences, persisted with Jetpack DataStore. */
private val Context.dataStore by preferencesDataStore(name = "miru_settings")

/**
 * The ceiling the player applies when choosing a video track.
 *
 * [AUTO] means "take the highest available" (subject to what the extractor
 * returns); every other entry clamps to that height. Stored as an int so the
 * DataStore value stays forward-compatible if we add taller options later.
 */
enum class VideoQuality(val maxHeight: Int) {
    AUTO(0),
    UHD_2160(2160),
    QHD_1440(1440),
    FHD_1080(1080),
    HD_720(720),
    SD_480(480),
    LOW_360(360),
}

/**
 * Read/write access to user preferences.
 *
 * Kept deliberately tiny: one observable quality setting today. The player
 * reads it on each load, so changing it takes effect on the next video without
 * an app restart.
 */
class SettingsRepository(private val context: Context) {

    /** The current preferred quality, defaulting to [VideoQuality.AUTO]. */
    val quality: Flow<VideoQuality> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_QUALITY] ?: VideoQuality.AUTO.maxHeight
        VideoQuality.entries.firstOrNull { it.maxHeight == stored } ?: VideoQuality.AUTO
    }

    /** Persist a new preferred quality. */
    suspend fun setQuality(value: VideoQuality) {
        context.dataStore.edit { prefs -> prefs[KEY_QUALITY] = value.maxHeight }
    }

    private companion object {
        val KEY_QUALITY = intPreferencesKey("video_quality")
    }
}
