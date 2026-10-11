package dev.praveensenpai.miru.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.MiruRepository
import dev.praveensenpai.miru.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    /** Nothing typed yet — show recent/suggestions area, currently empty. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Ready(val videos: List<VideoCard>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val repository: MiruRepository = MiruRepository(),
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) _state.value = SearchUiState.Idle
    }

    fun submit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _state.value = SearchUiState.Loading
        viewModelScope.launch {
            repository.search(q)
                .onSuccess { _state.value = SearchUiState.Ready(it) }
                .onFailure { _state.value = SearchUiState.Error(it.message ?: "Search failed") }
        }
    }
}