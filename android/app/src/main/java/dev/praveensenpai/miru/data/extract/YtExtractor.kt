package dev.praveensenpai.miru.data.extract

import dev.praveensenpai.miru.data.model.ChannelRef
import dev.praveensenpai.miru.data.model.ChannelResponse
import dev.praveensenpai.miru.data.model.StreamFormat
import dev.praveensenpai.miru.data.model.StreamsResponse
import dev.praveensenpai.miru.data.model.VideoCard
import dev.praveensenpai.miru.data.model.VideoDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/**
 * Turns InnerTube responses into Miru's models.
 *
 * This is the on-device replacement for the old Rust server's extractor. The
 * shapes are intentionally the same, so the player, downloads, Room cache, and
 * every screen keep working unchanged.
 *
 * All parsing is defensive: InnerTube payloads change without notice, so a
 * missing or renamed field degrades gracefully instead of crashing playback.
 */
object YtExtractor {

    /** Opaque params selecting a channel page's "Videos" tab. */
    private const val CHANNEL_VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"

    /** Videos shown on Home. `FEtrending` was removed by YouTube; Home works. */
    suspend fun trending(): List<VideoCard> {
        val home = runCatching { InnerTube.browse("FEwhat_to_watch") }.getOrNull()
        val items = home?.findAll("compactVideoRenderer")
            ?.mapNotNull { (it as? JsonObject)?.let(::card) }
            .orEmpty()
        return items.ifEmpty { runCatching { search("trending") }.getOrDefault(emptyList()) }
    }

    suspend fun search(query: String): List<VideoCard> {
        val response = InnerTube.search(query)
        return response.findAll("compactVideoRenderer")
            .mapNotNull { (it as? JsonObject)?.let(::card) }
    }

    suspend fun video(id: String): VideoDetail {
        val response = InnerTube.player(id)
        val status = response.obj("playabilityStatus")
        val state = status?.str("status")
        if (state != null && state != "OK") {
            throw IOException(status.str("reason") ?: "This video is unavailable")
        }
        val details = response.obj("videoDetails")
            ?: throw IOException("YouTube returned no video details")
        val micro = response.find("playerMicroformatRenderer") as? JsonObject
        return VideoDetail(
            id = id,
            title = details.str("title").orEmpty(),
            description = details.str("shortDescription").orEmpty(),
            duration = details.str("lengthSeconds")?.toLongOrNull(),
            channel = ChannelRef(
                id = details.str("channelId"),
                name = details.str("author"),
            ),
            thumbnail = details.obj("thumbnail")?.thumbUrl()
                ?: micro?.obj("thumbnail")?.thumbUrl(),
            viewCount = details.str("viewCount")?.toLongOrNull(),
            uploadDate = micro?.str("publishDate"),
            isLive = details.bool("isLive") ?: false,
            recommended = response.findAll("compactVideoRenderer")
                .mapNotNull { (it as? JsonObject)?.let(::card) },
        )
    }

    suspend fun streams(id: String): StreamsResponse {
        val response = InnerTube.player(id)
        val streaming = response.obj("streamingData")
            ?: throw IOException("YouTube returned no streams for this video")

        val raw = mutableListOf<JsonObject>()
        streaming.array("formats")?.forEach { (it as? JsonObject)?.let(raw::add) }
        streaming.array("adaptiveFormats")?.forEach { (it as? JsonObject)?.let(raw::add) }

        val video = mutableListOf<StreamFormat>()
        val audio = mutableListOf<StreamFormat>()
        val combined = mutableListOf<StreamFormat>()
        for (node in raw) {
            val parsed = format(node) ?: continue
            val hasVideo = parsed.vcodec != null && parsed.vcodec != "none"
            val hasAudio = parsed.acodec != null && parsed.acodec != "none"
            when {
                hasVideo && hasAudio -> combined += parsed
                hasVideo -> video += parsed
                hasAudio -> audio += parsed
            }
        }

        // Prefer the original-language audio track. YouTube lists every dubbed
        // language beside it, and a dub often has a higher bitrate than the
        // original, so a plain "best bitrate" pick can select a dub the user did
        // not ask for. Dubs are also the tracks most likely to be rejected.
        val originalAudio = raw
            .filter { it.obj("audioTrack")?.bool("audioIsDefault") == true }
            .mapNotNull { it.str("url") }
            .toSet()
        val languagePool: MutableList<StreamFormat> = if (originalAudio.isEmpty()) {
            audio
        } else {
            audio.filter { it.url in originalAudio }.toMutableList()
        }

        // Prefer AAC-in-MP4 audio (itag 139/140, `acodec == "mp4a"`) when it is
        // offered. Across every video tested it was the only audio rendition
        // googlevideo served consistently; the WebM/Opus and other variants were
        // intermittently rejected with HTTP 403.
        val aac = languagePool.filter { it.acodec == "mp4a" }
        val codecPool: MutableList<StreamFormat> = if (aac.isEmpty()) languagePool else aac.toMutableList()

        // Drop Dynamic Range Compression variants when a plain encode exists.
        // googlevideo rejects some `drc=1` tracks with HTTP 403 while the
        // equivalent uncompressed track streams normally, and the DRC copy is
        // often the highest bitrate so a plain sort would always pick it.
        val plain = codecPool.filterNot {
            it.url.contains("drc%3D1") || it.url.contains("drc=1")
        }
        val preferred: MutableList<StreamFormat> = if (plain.isEmpty()) {
            codecPool
        } else {
            plain.toMutableList()
        }

        video.sortByDescending { it.height ?: 0 }
        preferred.sortByDescending { it.bitrate ?: 0.0 }
        combined.sortByDescending { it.height ?: 0 }
        return StreamsResponse(
            id = id,
            kind = "direct",
            video = video,
            audio = preferred,
            combined = combined,
        )
    }

    suspend fun channel(id: String): ChannelResponse {
        val response = InnerTube.browse(id, params = CHANNEL_VIDEOS_TAB)
        val header = response.find("c4TabbedHeaderRenderer") as? JsonObject
        val metadata = response.find("channelMetadataRenderer") as? JsonObject

        val name = header?.obj("title")?.runsText()
            ?: metadata?.str("title")
            ?: ""

        val items = response.findAll("videoWithContextRenderer")
            .mapNotNull { (it as? JsonObject)?.let(::card) }
            .ifEmpty {
                response.findAll("gridVideoRenderer")
                    .mapNotNull { (it as? JsonObject)?.let(::card) }
            }
            .ifEmpty {
                response.findAll("compactVideoRenderer")
                    .mapNotNull { (it as? JsonObject)?.let(::card) }
            }

        return ChannelResponse(
            id = id,
            name = name,
            avatar = header?.obj("avatar")?.thumbUrl() ?: metadata?.obj("avatar")?.thumbUrl(),
            subscriberCount = parseCount(header?.obj("subscriberCountText")?.runsText()),
            items = items,
        )
    }

    // ---- mapping -----------------------------------------------------------

    /**
     * Maps any of YouTube's video "renderer" shapes to a [VideoCard].
     *
     * Search and feeds use `compactVideoRenderer`, channel shelves use
     * `videoWithContextRenderer`/`gridVideoRenderer`, and all of them expose the
     * same handful of fields under slightly different keys — hence the fallbacks.
     */
    private fun card(node: JsonObject): VideoCard? {
        val id = node.str("videoId")
            ?: node.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: return null
        val title = (node.obj("title") ?: node.obj("headline"))?.runsText() ?: return null

        val byline = node.obj("shortBylineText")
            ?: node.obj("longBylineText")
            ?: node.obj("ownerText")
        val channelId = byline?.array("runs")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.obj("navigationEndpoint")
            ?.obj("browseEndpoint")
            ?.str("browseId")

        return VideoCard(
            id = id,
            title = title,
            thumbnail = node.obj("thumbnail")?.thumbUrl(),
            duration = parseDuration(node.obj("lengthText")?.runsText()),
            channel = byline?.runsText(),
            channelId = channelId,
            viewCount = parseCount(
                node.obj("viewCountText")?.runsText()
                    ?: node.obj("shortViewCountText")?.runsText(),
            ),
            uploaded = node.obj("publishedTimeText")?.runsText(),
            isLive = isLive(node),
        )
    }

    private fun format(node: JsonObject): StreamFormat? {
        val url = node.str("url") ?: return null
        val mime = node.str("mimeType").orEmpty()
        val codecs = mime.substringAfter("codecs=\"", "").trimEnd('"')

        val vcodec = when {
            codecs.contains("avc1") || codecs.contains("avc3") -> "avc1"
            codecs.contains("vp9") || codecs.contains("vp09") -> "vp9"
            codecs.contains("av01") -> "av01"
            mime.startsWith("video/") -> "video"
            else -> "none"
        }
        val acodec = when {
            codecs.contains("mp4a") -> "mp4a"
            codecs.contains("opus") -> "opus"
            codecs.contains("vorbis") -> "vorbis"
            else -> "none"
        }

        return StreamFormat(
            itag = node.long("itag")?.toString() ?: node.str("formatId").orEmpty(),
            url = url,
            ext = mime.substringBefore(';').substringAfter('/', "").ifBlank { null },
            vcodec = vcodec,
            acodec = acodec,
            height = node.long("height"),
            fps = node.long("fps")?.toDouble(),
            bitrate = node.long("bitrate")?.let { it / 1000.0 },
            size = node.long("contentLength"),
        )
    }

    private fun isLive(node: JsonObject): Boolean {
        val badged = node.array("badges")?.any { badge ->
            (badge as? JsonObject)
                ?.obj("metadataBadgeRenderer")
                ?.str("label")
                ?.contains("LIVE", ignoreCase = true) == true
        } == true
        if (badged) return true
        return node.array("thumbnailOverlays")?.any { overlay ->
            (overlay as? JsonObject)
                ?.obj("thumbnailOverlayTimeStatusRenderer")
                ?.str("style")
                ?.equals("LIVE", ignoreCase = true) == true
        } == true
    }
}

/** Largest URL under a `{ thumbnails: [...] }` node. */
internal fun JsonObject.thumbUrl(): String? {
    val thumbs: JsonArray = obj("thumbnails")?.array("thumbnails")
        ?: array("thumbnails")
        ?: return null
    return thumbs.mapNotNull { (it as? JsonObject)?.str("url") }.lastOrNull()
}

/** `"3:51"` → `231`, `"1:02:03"` → `3725`, `"LIVE"` → `null`. */
internal fun parseDuration(text: String?): Long? {
    val parts = text?.trim()?.split(':') ?: return null
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toLongOrNull() ?: return null }
    return when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        else -> null
    }
}

private val COUNT = Regex("""([\d.,]+)\s*([KMB]?)""", RegexOption.IGNORE_CASE)

/**
 * Parses a human count such as `"14,773,970 views"`, `"106K views"`, or
 * `"4.55M subscribers"`. Only the suffix immediately after the number counts, so
 * the `b` in "subscribers" is never mistaken for "billion".
 */
internal fun parseCount(text: String?): Long? {
    val match = text?.let { COUNT.find(it) } ?: return null
    val value = match.groupValues[1].replace(",", "").trimEnd('.').toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "B" -> 1_000_000_000L
        "M" -> 1_000_000L
        "K" -> 1_000L
        else -> 1L
    }
    return (value * multiplier).toLong()
}