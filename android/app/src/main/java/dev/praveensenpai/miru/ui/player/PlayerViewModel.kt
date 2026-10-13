package dev.praveensenpai.miru.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.MiruRepository
import dev.praveensenpai.miru.data.library.LibraryRepository
import dev.praveensenpai.miru.data.model.DislikeResponse
import dev.praveensenpai.miru.data.model.SponsorSegment
import dev.praveensenpai.miru.data.model.StreamsResponse
import dev.praveensenpai.miru.data.model.VideoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(
        val detail: VideoDetail,
        val streams: StreamsResponse,
        /** Best muxed or video-only URL Media3 should load first. */
        val playUrl: String,
        val audioUrl: String?,
        /** SponsorBlock segments to auto-skip, in ascending start order. */
        val segments: List<SponsorSegment>,
        /** Estimated likes/dislikes, or null if unavailable. */
        val votes: DislikeResponse? = null,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    private val videoId: String,
    private val repository: MiruRepository = MiruRepository(),
    private val library: LibraryRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Whether the current video's channel is subscribed to. */
    private val _subscribed = MutableStateFlow(false)
    val subscribed: StateFlow<Boolean> = _subscribed.asStateFlow()

    private var channelId: String? = null

    init {
        load()
    }

    fun load() {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            val detailResult = repository.video(videoId)
            val streamsResult = repository.streams(videoId)
            // Non-fatal: a SponsorBlock failure just means nothing is skipped.
            val segments = repository.sponsorblock(videoId).getOrDefault(emptyList())
            // Non-fatal: the dislike row is simply hidden when unavailable.
            val votes = repository.dislike(videoId).getOrNull()

            val detail = detailResult.getOrElse {
                _state.value = PlayerUiState.Error(it.message ?: "Failed to load video")
                return@launch
            }

            // Record the watch as soon as the video is resolved. History is
            // local and cheap, so this never blocks playback.
            library?.recordWatch(detail)
            refreshSubscription(detail.channel.id)
            val streams = streamsResult.getOrElse {
                _state.value = PlayerUiState.Error(it.message ?: "Failed to load streams")
                return@launch
            }

            // Prefer a video-only track paired with separate audio: it is both
            // higher quality and, unlike some legacy muxed formats, reliably
            // playable. Fall back to a muxed stream when no video-only exists.
            val videoOnly = streams.video.maxByOrNull { it.height ?: 0 }
            val muxed = streams.combined.maxByOrNull { it.height ?: 0 }
            val bestAudio = streams.audio.maxByOrNull { it.bitrate ?: 0.0 }
            val playUrl = videoOnly?.url ?: muxed?.url

            if (playUrl == null) {
                _state.value = PlayerUiState.Error("No playable stream found")
                return@launch
            }

            _state.value = PlayerUiState.Ready(
                detail = detail,
                streams = streams,
                playUrl = playUrl,
                // Separate audio is needed whenever the video track has none.
                audioUrl = if (videoOnly != null) bestAudio?.url else null,
                segments = segments.sortedBy { it.start },
                votes = votes,
            )
        }
    }

    /** Mirror the channel's subscription state into [subscribed]. */
    private fun refreshSubscription(id: String?) {
        channelId = id
        val lib = library ?: return
        if (id.isNullOrBlank()) return
        viewModelScope.launch {
            lib.observeSubscribed(id).collect { _subscribed.value = it }
        }
    }

    /** Subscribe or unsubscribe from the current video's channel. */
    fun toggleSubscription() {
        val lib = library ?: return
        val id = channelId ?: return
        val detail = (_state.value as? PlayerUiState.Ready)?.detail ?: return
        viewModelScope.launch {
            if (_subscribed.value) {
                lib.unsubscribe(id)
            } else {
                lib.subscribe(id, detail.channel.name.orEmpty(), detail.channel.avatar)
            }
        }
    }
}