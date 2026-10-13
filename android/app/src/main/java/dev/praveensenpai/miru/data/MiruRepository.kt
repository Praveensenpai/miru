package dev.praveensenpai.miru.data

import dev.praveensenpai.miru.data.model.ChannelResponse
import dev.praveensenpai.miru.data.model.DislikeResponse
import dev.praveensenpai.miru.data.model.SponsorSegment
import dev.praveensenpai.miru.data.model.StreamsResponse
import dev.praveensenpai.miru.data.model.VideoCard
import dev.praveensenpai.miru.data.model.VideoDetail
import dev.praveensenpai.miru.data.remote.MiruApi
import dev.praveensenpai.miru.data.remote.NetworkModule

/**
 * Single access point to the backend.
 *
 * Deliberately thin for v1: it wraps the API and normalises failures into
 * [Result] so ViewModels never touch Retrofit types. Local caching (Room) will
 * be layered underneath this without changing call sites.
 */
class MiruRepository(private val api: MiruApi = NetworkModule.api) {

    suspend fun trending(): Result<List<VideoCard>> = runCatching {
        api.trending().items
    }

    suspend fun search(query: String, page: Int = 1): Result<List<VideoCard>> = runCatching {
        api.search(query, page).items
    }

    suspend fun video(id: String): Result<VideoDetail> = runCatching {
        api.video(id)
    }

    suspend fun streams(id: String): Result<StreamsResponse> = runCatching {
        api.streams(id)
    }

    /** A channel's profile and recent videos. */
    suspend fun channel(id: String): Result<ChannelResponse> = runCatching {
        api.channel(id)
    }

    /**
     * SponsorBlock segments for a video.
     *
     * Failure is non-fatal: the player simply skips nothing.
     */
    suspend fun sponsorblock(id: String): Result<List<SponsorSegment>> = runCatching {
        api.sponsorblock(id).segments
    }

    /**
     * Estimated like/dislike counts.
     *
     * Failure is non-fatal: the player just hides the dislike row.
     */
    suspend fun dislike(id: String): Result<DislikeResponse> = runCatching {
        api.dislike(id)
    }
}