package dev.praveensenpai.miru.data.remote

import dev.praveensenpai.miru.data.model.ChannelResponse
import dev.praveensenpai.miru.data.model.DislikeResponse
import dev.praveensenpai.miru.data.model.HealthResponse
import dev.praveensenpai.miru.data.model.SearchResponse
import dev.praveensenpai.miru.data.model.SponsorBlockResponse
import dev.praveensenpai.miru.data.model.StreamsResponse
import dev.praveensenpai.miru.data.model.TrendingResponse
import dev.praveensenpai.miru.data.model.VideoDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit binding for the miru server contract documented in docs/API.md. */
interface MiruApi {

    @GET("health")
    suspend fun health(): HealthResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
    ): SearchResponse

    @GET("trending")
    suspend fun trending(): TrendingResponse

    @GET("video/{id}")
    suspend fun video(@Path("id") id: String): VideoDetail

    @GET("streams/{id}")
    suspend fun streams(@Path("id") id: String): StreamsResponse

    @GET("channel/{id}")
    suspend fun channel(@Path("id") id: String): ChannelResponse

    @GET("sponsorblock/{id}")
    suspend fun sponsorblock(@Path("id") id: String): SponsorBlockResponse

    @GET("dislike/{id}")
    suspend fun dislike(@Path("id") id: String): DislikeResponse
}