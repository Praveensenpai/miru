package dev.praveensenpai.miru.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.MiruRepository
import dev.praveensenpai.miru.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loading / loaded / failed states for the Home feed. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val videos: List<VideoCard>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val repository: MiruRepository = MiruRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.trending()
                .onSuccess { _state.value = HomeUiState.Ready(it) }
                .onFailure { _state.value = HomeUiState.Error(it.message ?: "Failed to load feed") }
        }
    }
}