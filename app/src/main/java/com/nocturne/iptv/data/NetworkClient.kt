package com.nocturne.iptv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Thin OkHttp wrapper used to fetch playlists and EPG feeds. */
class NetworkClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Fetches a remote text resource, honouring a friendly User-Agent. */
    fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        const val USER_AGENT = "Nocturne/1.0 (Android IPTV)"
    }
}