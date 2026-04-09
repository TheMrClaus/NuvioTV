package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.EmbyCredentialsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbyMediaService"

/**
 * Resolves Emby direct-play stream URLs.
 *
 * Authentication is performed via the `X-Emby-Token` request header rather than
 * embedding the API key as a query parameter. This avoids leaking credentials
 * through HTTP logs, proxies, or copy-paste, and is forward-compatible with
 * Jellyfin's planned deprecation of the `api_key` query parameter.
 */
@Singleton
class EmbyMediaService @Inject constructor(
    private val embyCredentialsDataStore: EmbyCredentialsDataStore
) {

    /**
     * Holds the resolved stream URL together with the HTTP headers that must be
     * sent with every request to that URL (most importantly `X-Emby-Token`).
     */
    data class EmbyStreamResult(
        val url: String,
        val headers: Map<String, String>
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Resolves a playable stream URL for the given Emby item ID.
     *
     * @param itemId The Emby item identifier (e.g. obtained from a prior search).
     * @return [EmbyStreamResult] containing the stream URL and the required auth
     *         header, or `null` if the credentials are not configured.
     */
    suspend fun resolveStreamUrl(itemId: String): EmbyStreamResult? =
        withContext(Dispatchers.IO) {
            val credentials = embyCredentialsDataStore.credentials.first()
            if (!credentials.isConfigured) {
                Log.w(TAG, "Emby credentials not configured — skipping stream resolution")
                return@withContext null
            }

            val serverUrl = credentials.serverUrl
            val apiKey = credentials.apiKey

            // Build the stream URL without embedding the API key as a query parameter.
            // Authentication is handled via the X-Emby-Token header so that the key
            // is never exposed in URLs (logs, proxies, clipboard history, etc.).
            val streamUrl = "$serverUrl/Videos/$itemId/stream?static=true"

            Log.d(TAG, "Emby stream resolved: $itemId -> ${streamUrl.substringBefore("?")}")

            EmbyStreamResult(
                url = streamUrl,
                headers = mapOf("X-Emby-Token" to apiKey)
            )
        }

    /**
     * Searches the Emby library for items matching [query] and returns the first
     * result, or `null` when nothing is found or credentials are missing.
     *
     * Every request to the Emby REST API uses the `X-Emby-Token` header for
     * authentication — the API key is never appended to a URL.
     */
    suspend fun findItemByName(
        query: String,
        includeTypes: String = "Movie,Episode"
    ): JSONObject? = withContext(Dispatchers.IO) {
        val credentials = embyCredentialsDataStore.credentials.first()
        if (!credentials.isConfigured) {
            Log.w(TAG, "Emby credentials not configured — skipping item search")
            return@withContext null
        }

        val serverUrl = credentials.serverUrl
        val apiKey = credentials.apiKey
        val userId = credentials.userId

        val userSegment = if (userId.isNotBlank()) "/Users/$userId" else ""
        val searchUrl = "$serverUrl$userSegment/Items" +
            "?SearchTerm=${query.encodeUrl()}" +
            "&IncludeItemTypes=$includeTypes" +
            "&Recursive=true" +
            "&Limit=1" +
            "&Fields=Id,Name"

        try {
            val request = Request.Builder()
                .url(searchUrl)
                // Authenticate via header — never append api_key to the URL.
                .header("X-Emby-Token", apiKey)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Emby item search failed: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val items: JSONArray = JSONObject(body).optJSONArray("Items")
                ?: return@withContext null

            if (items.length() == 0) return@withContext null
            items.getJSONObject(0)
        } catch (e: Exception) {
            Log.e(TAG, "Emby item search error: ${e.message}")
            null
        }
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
