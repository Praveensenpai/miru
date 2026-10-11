package dev.praveensenpai.miru.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * Builds the single Retrofit client used by the app.
 *
 * The base URL points at the host machine from the Android emulator
 * (10.0.2.2 is the emulator's alias for the host loopback). A physical device
 * would need the server's LAN address here instead.
 */
object NetworkModule {

    const val BASE_URL = "http://10.0.2.2:8787/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Extraction spawns yt-dlp, which can take a while on cold lookups.
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    val api: MiruApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MiruApi::class.java)
    }

    /**
     * Client for bulk media downloads.
     *
     * Separate from [client] because downloads are long-lived: no read timeout,
     * and a distinct user agent so the server can tell streams from API calls.
     */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}