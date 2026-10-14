package dev.praveensenpai.miru.data.extract

import dev.praveensenpai.miru.data.model.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.security.MessageDigest

/**
 * Direct SponsorBlock client.
 *
 * SponsorBlock no longer serves the legacy `?videoID=` lookup; the only working
 * endpoint is the privacy-preserving one keyed by the first four hex characters
 * of `sha256(videoId)`. The response is a list of entries, so we filter to the
 * one matching our full id.
 *
 * Failure is non-fatal everywhere: a missing or empty result simply means the
 * player skips nothing.
 */
object SponsorBlockClient {

    private const val API = "https://sponsor.ajay.app/api/skipSegments"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun segments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val prefix = sha256Hex(videoId).take(4)
        val request = Request.Builder()
            .url("$API/$prefix")
            .header("User-Agent", Http.USER_AGENT)
            .build()

        val body = Http.client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string().orEmpty()
        }
        if (body.isBlank()) return@withContext emptyList()

        val entries = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
            ?: return@withContext emptyList()

        (entries.firstOrNull { (it as? JsonObject)?.str("videoID") == videoId } as? JsonObject)
            ?.array("segments")
            ?.mapNotNull(::segment)
            .orEmpty()
    }

    private fun segment(element: JsonElement): SponsorSegment? {
        val node = element as? JsonObject ?: return null
        // Only `skip` segments are actionable; `mute`/`full`/`poi` are ignored.
        if ((node.str("actionType") ?: "skip") != "skip") return null
        val range: JsonArray = node.array("segment") ?: return null
        if (range.size < 2) return null
        val start = runCatching { range[0].jsonPrimitive.double }.getOrNull() ?: return null
        val end = runCatching { range[1].jsonPrimitive.double }.getOrNull() ?: return null
        return SponsorSegment(
            category = node.str("category") ?: "sponsor",
            start = start,
            end = end,
        )
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}