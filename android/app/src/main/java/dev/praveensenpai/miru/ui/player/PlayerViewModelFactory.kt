package dev.praveensenpai.miru.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.praveensenpai.miru.data.library.LibraryRepository

/**
 * Supplies [PlayerViewModel] with the video id and a library repository.
 *
 * The player is a per-video screen, so the id must be injected rather than read
 * from a shared state holder. The repository needs a context for Room, which the
 * factory holds as the application context.
 */
class PlayerViewModelFactory(
    private val videoId: String,
    private val context: Context,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return PlayerViewModel(
            videoId = videoId,
            library = LibraryRepository(context),
        ) as T
    }
}