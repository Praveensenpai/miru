package dev.praveensenpai.miru.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Supplies [PlayerViewModel] with the video id it needs.
 *
 * The player is a per-video screen, so the id must be injected rather than read
 * from a shared state holder. This is the minimal factory for that case.
 */
class PlayerViewModelFactory(
    private val videoId: String,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return PlayerViewModel(videoId) as T
    }
}