package dev.praveensenpai.miru.data.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal InnerTube (YouTube's internal API) client.
 *
 * Miru has no server: extraction happens on the device by calling the same
 * `youtubei/v1` endpoints the official apps use.
 *
 * Two client profiles are used because YouTube gates them differently:
 *
 *  - **Catalog** (`search`, `browse`) uses `ANDROID_VR`, which still returns the
 *    classic `compactVideoRenderer` feeds the UI is built around.
 *  - **Player** (`player`) uses `IOS`. As of late 2025 every `ANDROID_VR` version
 *    is bot-gated (`LOGIN_REQUIRED`, "Sign in to confirm you're not a bot") for
 *    ordinary feed videos, while `IOS` 20.44–21.20 still returns plain,
 *    un-ciphered `url` fields up to 2160p. Because the URLs are not
 *    `signatureCipher`, no JavaScript signature deciphering (n-sig/PO token) is
 *    required, and they honour HTTP Range requests (206) instead of being
 *    throttled. IOS exposes only adaptive (separate video/audio) formats, which
 *    the player already merges.
 *
 * Requests run without an API key: the keyed ANDROID/IOS clients reject anonymous
 * callers with `FAILED_PRECONDITION` when the client version is stale, so the
 * versions below must be kept current.
 */
object InnerTube {

    private const val BASE = "https://www.youtube.com/youtubei/v1"

    /**
     * Must match the client that extracted the stream URLs, for googlevideo
     * playback. This is the **player** (IOS) user agent; the catalog UA differs.
     */
    const val USER_AGENT =
        "com.google.ios.youtube/21.20.2 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"

    private const val CATALOG_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.60.2 (Linux; U; Android 12L) gzip"

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Which app Miru pretends to be for a given request. */
    private enum class Profile(val userAgent: String, val context: JsonObject) {
        CATALOG(
            CATALOG_USER_AGENT,
            buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.60.2")
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("androidSdkVersion", 32)
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("hl", "en")
                    put("gl", "US")
                })
            },
        ),
        PLAYER(
            USER_AGENT,
            buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "IOS")
                    put("clientVersion", "21.20.2")
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.1.0.22B83")
                    put("hl", "en")
                    put("gl", "US")
                })
            },
        ),
    }

    /** POST a JSON body to an InnerTube endpoint and return the parsed object. */
    private suspend fun post(
        endpoint: String,
        body: JsonObject,
        profile: Profile,
    ): JsonObject = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", profile.context)
            body.forEach { (key, value) -> put(key, value) }
        }

        val request = Request.Builder()
            .url("$BASE/$endpoint")
            .header("Content-Type", "application/json")
            .header("User-Agent", profile.userAgent)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("InnerTube $endpoint failed: HTTP ${response.code}")
                }
                json.parseToJsonElement(text).jsonObject
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("InnerTube $endpoint returned unreadable JSON", e)
        }
    }

    /** `/player` — full metadata plus streaming URLs for one video. */
    suspend fun player(videoId: String): JsonObject = post(
        "player",
        buildJsonObject {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        },
        Profile.PLAYER,
    )

    /** `/search` — flat list of matching videos. */
    suspend fun search(query: String): JsonObject = post(
        "search",
        buildJsonObject { put("query", query) },
        Profile.CATALOG,
    )

    /** `/browse` — feeds, channel pages, and trending, selected by [browseId]. */
    suspend fun browse(browseId: String, params: String? = null): JsonObject = post(
        "browse",
        buildJsonObject {
            put("browseId", browseId)
            if (params != null) put("params", params)
        },
        Profile.CATALOG,
    )

    // ---- JsonObject traversal helpers -------------------------------------
    // InnerTube responses are deeply nested and change shape often, so we walk
    // raw JSON rather than binding to rigid data classes.
}

/** Null-safe child lookup. */
internal fun JsonObject.obj(key: String): JsonObject? =
    (this[key] as? JsonObject)

internal fun JsonObject.array(key: String): JsonArray? =
    (this[key] as? JsonArray)

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.dbl(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

/** First `runs[].text` joined together, or `simpleText` when present. */
internal fun JsonObject.runsText(): String? {
    str("simpleText")?.let { return it }
    val runs = array("runs") ?: return null
    val text = runs.mapNotNull { (it as? JsonObject)?.str("text") }.joinToString("")
    return text.ifBlank { null }
}

/** Depth-first search for the first value stored under [key]. */
internal fun JsonElement.find(key: String): JsonElement? {
    when (this) {
        is JsonObject -> {
            this[key]?.let { return it }
            for (value in values) value.find(key)?.let { return it }
        }
        is JsonArray -> for (value in this) value.find(key)?.let { return it }
        else -> Unit
    }
    return null
}

/** Depth-first search for every value stored under [key]. */
internal fun JsonElement.findAll(key: String): List<JsonElement> {
    val out = mutableListOf<JsonElement>()
    fun walk(node: JsonElement) {
        when (node) {
            is JsonObject -> node.forEach { (k, v) ->
                if (k == key) out.add(v)
                walk(v)
            }
            is JsonArray -> node.forEach { walk(it) }
            else -> Unit
        }
    }
    walk(this)
    return out
}
