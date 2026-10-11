package dev.praveensenpai.miru.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.praveensenpai.miru.data.download.DownloadEntity
import dev.praveensenpai.miru.data.download.DownloadState
import dev.praveensenpai.miru.data.download.DownloadWorker
import dev.praveensenpai.miru.data.download.MiruDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Downloads tab.
 *
 * The list is a live Room query, so progress updates appear without manual
 * polling. Enqueuing a download is a one-liner here; the worker does the rest.
 */
class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = MiruDatabase.get(app).downloads()

    val downloads: StateFlow<List<DownloadEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Queue a new download, or re-queue one that previously failed. */
    fun enqueue(entity: DownloadEntity) {
        viewModelScope.launch {
            dao.upsert(entity.copy(state = DownloadState.QUEUED, progress = 0))
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_ID to entity.id))
                .build()
            WorkManager.getInstance(getApplication()).enqueue(request)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }
}
