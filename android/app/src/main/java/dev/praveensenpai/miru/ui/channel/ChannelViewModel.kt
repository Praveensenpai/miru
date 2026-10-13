package dev.praveensenpai.miru.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.MiruRepository
import dev.praveensenpai.miru.data.model.ChannelResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loading / loaded / failed states for a channel page. */
sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Ready(val channel: ChannelResponse) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

/** Backs the channel page reached from a subscription. */
class ChannelViewModel(
    private val channelId: String,
    private val repository: MiruRepository = MiruRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = ChannelUiState.Loading
        viewModelScope.launch {
            repository.channel(channelId)
                .onSuccess { _state.value = ChannelUiState.Ready(it) }
                .onFailure {
                    _state.value = ChannelUiState.Error(it.message ?: "Failed to load channel")
                }
        }
    }
}

/** Supplies [ChannelViewModel] with the channel id it needs. */
class ChannelViewModelFactory(
    private val channelId: String,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChannelViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ChannelViewModel(channelId) as T
    }
}