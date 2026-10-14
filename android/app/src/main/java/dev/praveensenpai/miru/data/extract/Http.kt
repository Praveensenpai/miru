package dev.praveensenpai.miru.data.extract

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared OkHttp client for the extractor's third-party APIs. */
internal object Http {

    const val USER_AGENT = "miru/0.3.0 (Android)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}