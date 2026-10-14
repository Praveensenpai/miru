package dev.praveensenpai.miru.data

import dev.praveensenpai.miru.data.extract.DislikeClient
import dev.praveensenpai.miru.data.extract.SponsorBlockClient
import dev.praveensenpai.miru.data.extract.YtExtractor
import dev.praveensenpai.miru.data.model.ChannelResponse
import dev.praveensenpai.miru.data.model.DislikeResponse
import dev.praveensenpai.miru.data.model.SponsorSegment
import dev.praveensenpai.miru.data.model.StreamsResponse
import dev.praveensenpai.miru.data.model.VideoCard
import dev.praveensenpai.miru.data.model.VideoDetail

/**
 * Single access point to YouTube data.
 *
 * Miru is serverless: there is no backend to configure or reach. Everything is
 * pulled straight from YouTube via [YtExtractor] on the device, and the
 * community APIs (SponsorBlock, Return YouTube Dislike) are called directly.
 *
 * This class stays deliberately thin and normalises every failure into a
 * [Result], so ViewModels never deal with IO exceptions and a missing
 * SponsorBlock/Dislike response degrades to an empty value rather than an error.
 */
class MiruRepository {

    suspend fun trending(): Result<List<VideoCard>> = runCatching {
        YtExtractor.trending()
    }

    suspend fun search(query: String, page: Int = 1): Result<List<VideoCard>> = runCatching {
        YtExtractor.search(query)
    }

    suspend fun video(id: String): Result<VideoDetail> = runCatching {
        YtExtractor.video(id)
    }

    suspend fun streams(id: String): Result<StreamsResponse> = runCatching {
        YtExtractor.streams(id)
    }

    /** A channel's profile and recent videos. */
    suspend fun channel(id: String): Result<ChannelResponse> = runCatching {
        YtExtractor.channel(id)
    }

    /**
     * SponsorBlock segments for a video.
     *
     * Failure is non-fatal: the player simply skips nothing.
     */
    suspend fun sponsorblock(id: String): Result<List<SponsorSegment>> = runCatching {
        SponsorBlockClient.segments(id)
    }

    /**
     * Estimated like/dislike counts.
     *
     * Failure is non-fatal: the player just hides the dislike row.
     */
    suspend fun dislike(id: String): Result<DislikeResponse> = runCatching {
        DislikeClient.votes(id)
    }
}