package dev.praveensenpai.miru.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.library.HistoryEntity
import dev.praveensenpai.miru.data.library.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Library tab (watch history).
 *
 * The list is a live Room query ordered by recency, so a video watched in the
 * player jumps to the top here without any manual refresh.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val library = LibraryRepository(app)

    val history: StateFlow<List<HistoryEntity>> = library.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(videoId: String) {
        viewModelScope.launch { library.removeHistory(videoId) }
    }

    fun clear() {
        viewModelScope.launch { library.clearHistory() }
    }
}