package com.smithware.contentlens.data.safety

import android.content.Context
import com.smithware.contentlens.BuildConfig
import com.smithware.contentlens.data.tmdb.RemoteMediaType
import com.smithware.contentlens.data.tmdb.SafeLog
import com.smithware.contentlens.data.tmdb.remoteMediaKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class FeaturedFeedClient(
    context: Context? = null,
    private val baseUrl: String = BuildConfig.CONTENTLENS_API_BASE_URL
) {
    private val cacheFile = context?.applicationContext?.filesDir?.resolve(CACHE_FILE_NAME)

    suspend fun safetyStates(): Map<String, ExternalSafetyState> = withContext(Dispatchers.IO) {
        val cachedStates = readCachedStates()
        if (cachedStates.isNotEmpty()) return@withContext cachedStates

        if (baseUrl.isBlank()) return@withContext emptyMap()
        try {
            val body = get("/v1/featured")
            writeCachedFeed(body)
            parseFeatured(body)
        } catch (error: Exception) {
            SafeLog.warn(TAG, "Featured feed unavailable: ${error.message}")
            emptyMap()
        }
    }

    internal fun parseFeatured(body: String): Map<String, ExternalSafetyState> {
        val root = JSONObject(body)
        val sections = root.optJSONArray("sections") ?: return emptyMap()
        val states = mutableMapOf<String, ExternalSafetyState>()
        for (sectionIndex in 0 until sections.length()) {
            val items = sections.optJSONObject(sectionIndex)?.optJSONArray("items") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val mediaType = when (item.optString("mediaType")) {
                    "movie" -> RemoteMediaType.Movie
                    "tv" -> RemoteMediaType.Tv
                    else -> continue
                }
                val tmdbId = item.optInt("tmdbId", 0)
                if (tmdbId == 0) continue
                val safety = item.optJSONObject("safety")
                states[remoteMediaKey(tmdbId, mediaType)] = if (safety == null) {
                    ExternalSafetyState.NoMatch
                } else {
                    ExternalSafetyState.Loaded(ExternalSafetyJsonParser.parseReport(safety))
                }
            }
        }
        return states
    }

    private fun readCachedStates(): Map<String, ExternalSafetyState> {
        val file = cacheFile ?: return emptyMap()
        if (!file.exists() || file.length() <= 0L) return emptyMap()
        if (System.currentTimeMillis() - file.lastModified() > FEATURED_CACHE_MAX_AGE_MS) return emptyMap()
        return try {
            parseFeatured(file.readText())
        } catch (error: Exception) {
            SafeLog.warn(TAG, "Cached featured feed could not be read: ${error.message}")
            emptyMap()
        }
    }

    private fun writeCachedFeed(body: String) {
        val file = cacheFile ?: return
        try {
            file.writeText(body)
        } catch (error: Exception) {
            SafeLog.warn(TAG, "Cached featured feed could not be written: ${error.message}")
        }
    }

    private fun get(path: String): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Featured feed returned HTTP $status.")
            body
        } catch (error: SocketTimeoutException) {
            throw IOException("Featured feed timed out.", error)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "FeaturedFeed"
        const val CACHE_FILE_NAME = "featured-feed-v1.json"
        const val FEATURED_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
