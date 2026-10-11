package dev.praveensenpai.miru.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoCard(
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val channel: String? = null,
    @SerialName("channelId") val channelId: String? = null,
    @SerialName("viewCount") val viewCount: Long? = null,
    val uploaded: String? = null,
    @SerialName("isLive") val isLive: Boolean = false,
)

@Serializable
data class ChannelRef(
    val id: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)

@Serializable
data class Chapter(
    val title: String,
    val start: Double,
)

@Serializable
data class VideoDetail(
    val id: String,
    val title: String,
    val description: String = "",
    val duration: Long? = null,
    val channel: ChannelRef = ChannelRef(),
    val thumbnail: String? = null,
    @SerialName("viewCount") val viewCount: Long? = null,
    @SerialName("uploadDate") val uploadDate: String? = null,
    @SerialName("ageLimit") val ageLimit: Long = 0,
    @SerialName("isLive") val isLive: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val recommended: List<VideoCard> = emptyList(),
)

@Serializable
data class StreamFormat(
    val itag: String,
    val url: String,
    val ext: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val height: Long? = null,
    val fps: Double? = null,
    val bitrate: Double? = null,
    val size: Long? = null,
)

@Serializable
data class StreamsResponse(
    val id: String,
    val kind: String,
    val video: List<StreamFormat> = emptyList(),
    val audio: List<StreamFormat> = emptyList(),
    val combined: List<StreamFormat> = emptyList(),
)

@Serializable
data class SearchResponse(
    val query: String,
    val page: Int,
    val items: List<VideoCard> = emptyList(),
)

@Serializable
data class TrendingResponse(
    val items: List<VideoCard> = emptyList(),
)

@Serializable
data class ChannelResponse(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val banner: String? = null,
    @SerialName("subscriberCount") val subscriberCount: Long? = null,
    val items: List<VideoCard> = emptyList(),
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val authenticated: Boolean,
)