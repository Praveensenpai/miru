package dev.praveensenpai.miru.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.settings.SettingsRepository
import dev.praveensenpai.miru.data.settings.VideoQuality
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Settings screen with the persisted [VideoQuality] preference. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app.applicationContext)

    /** The current preferred quality, hot so the UI tracks external changes. */
    val quality: StateFlow<VideoQuality> = repository.quality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoQuality.AUTO,
    )

    /** Persist a newly chosen quality. */
    fun setQuality(value: VideoQuality) {
        viewModelScope.launch { repository.setQuality(value) }
    }
}
