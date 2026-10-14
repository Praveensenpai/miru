package dev.praveensenpai.miru.data.extract

import dev.praveensenpai.miru.data.model.DislikeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request

/**
 * Direct Return YouTube Dislike client.
 *
 * YouTube removed public dislike counts, so this community API exposes its own
 * estimate. Counts are always labelled as estimates in the UI. A failure here
 * is non-fatal: the player just hides the dislike row.
 */
object DislikeClient {

    private const val API = "https://returnyoutubedislikeapi.com/votes"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun votes(videoId: String): DislikeResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API?videoId=$videoId")
            .header("User-Agent", Http.USER_AGENT)
            .build()

        val body = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext DislikeResponse(id = videoId)
            response.body?.string().orEmpty()
        }
        if (body.isBlank()) return@withContext DislikeResponse(id = videoId)

        val node = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() as? JsonObject
            ?: return@withContext DislikeResponse(id = videoId)

        DislikeResponse(
            id = videoId,
            likes = node.long("likes") ?: 0,
            dislikes = node.long("dislikes") ?: 0,
            rating = node.dbl("rating") ?: 0.0,
        )
    }
}