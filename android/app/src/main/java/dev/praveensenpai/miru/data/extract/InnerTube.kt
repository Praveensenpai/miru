package dev.praveensenpai.miru.data.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * A single client profile is used for every call: **ANDROID_VR 1.65.10**.
 *
 * YouTube's clients are gated differently:
 *
 *  - `IOS` returns plain, un-ciphered `url` fields, but as of 2026 its
 *    googlevideo URLs carry `GVS_PO_TOKEN_POLICY: required` — without a PO
 *    token every ranged read is refused with HTTP 403.
 *  - `ANDROID_VR` at version **1.60.2** is bot-gated (`LOGIN_REQUIRED`,
 *    "Sign in to confirm you're not a bot").
 *  - `ANDROID_VR` at version **1.65.10**, paired with a `visitorData` value,
 *    returns `OK` with plain, un-ciphered `url` fields for both adaptive audio
 *    and video, and those URLs serve ranged reads with HTTP 206.
 *
 * [visitorData] is minted once from `/visitor_id` (no API key) and sent as
 * `X-Goog-Visitor-Id`; it is what lifts the bot gate. Because the URLs are not
 * `signatureCipher`, no JavaScript signature deciphering (n-sig/PO token) is
 * required on device.
 *
 * Requests run without an API key: the keyed clients reject anonymous callers
 * with `FAILED_PRECONDITION` when the client version is stale, so the version
 * below must be kept current.
 */
object InnerTube {

    private const val BASE = "https://www.youtube.com/youtubei/v1"

    /**
     * Must match the client that extracted the stream URLs, for googlevideo
     * playback.
     */
    const val USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * ANDROID_VR 1.65.10 client context.
     *
     * YouTube bot-gates older versions of this client, and the IOS client's
     * googlevideo URLs now require a PO token Miru has no way to mint. 1.65.10
     * paired with a `visitorData` value returns plain, un-ciphered `url` fields
     * that serve ranged reads with HTTP 206.
     */
    private val clientContext: JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", "ANDROID_VR")
            put("clientVersion", CLIENT_VERSION)
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("androidSdkVersion", 32)
            put("osName", "Android")
            put("osVersion", "12L")
            put("hl", "en")
            put("gl", "US")
        })
    }

    /** Client version; must stay in step with the user agent above. */
    private const val CLIENT_VERSION = "1.65.10"

    /** Numeric id YouTube assigns the ANDROID_VR client. */
    private const val CLIENT_NAME = "28"

    /**
     * Visitor id that lifts ANDROID_VR's `LOGIN_REQUIRED` bot gate.
     *
     * Minted once from `/visitor_id` and reused; it is cheap to fetch and stable
     * for a session, so there is no need to persist it.
     */
    @Volatile
    private var visitorData: String? = null

    private val visitorLock = Mutex()

    /**
     * Return a cached visitor id, minting one on first use.
     *
     * `/visitor_id` needs no API key and returns the id in
     * `responseContext.visitorData`. This deliberately does **not** go through
     * [post], which itself needs a visitor id to succeed.
     */
    private suspend fun visitor(): String? {
        visitorData?.let { return it }
        return visitorLock.withLock {
            visitorData?.let { return@withLock it }
            val minted = runCatching { mintVisitor() }.getOrNull()
            if (minted != null) visitorData = minted
            minted
        }
    }

    /** One `/visitor_id` round trip; see [visitor]. */
    private suspend fun mintVisitor(): String? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("context", clientContext) }
        val request = Request.Builder()
            .url("$BASE/visitor_id")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_NAME)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            json.parseToJsonElement(response.body?.string().orEmpty())
                .jsonObject.obj("responseContext")?.str("visitorData")
        }
    }

    /** POST a JSON body to an InnerTube endpoint and return the parsed object. */
    private suspend fun post(
        endpoint: String,
        body: JsonObject,
    ): JsonObject = withContext(Dispatchers.IO) {
        val visitor = visitor()
        val payload = buildJsonObject {
            put("context", clientContext)
            body.forEach { (key, value) -> put(key, value) }
        }

        val request = Request.Builder()
            .url("$BASE/$endpoint")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_NAME)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
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
    )

    /** `/search` — flat list of matching videos. */
    suspend fun search(query: String): JsonObject = post(
        "search",
        buildJsonObject { put("query", query) },
    )

    /** `/browse` — feeds, channel pages, and trending, selected by [browseId]. */
    suspend fun browse(browseId: String, params: String? = null): JsonObject = post(
        "browse",
        buildJsonObject {
            put("browseId", browseId)
            if (params != null) put("params", params)
        },
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
