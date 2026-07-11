package com.smithware.contentlens.data.safety

import com.smithware.contentlens.BuildConfig
import com.smithware.contentlens.data.tmdb.RemoteMediaType
import com.smithware.contentlens.data.tmdb.SafeLog
import com.smithware.contentlens.data.tmdb.TmdbTitleDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

class ContentLensProxySafetyClient(
    private val baseUrl: String = BuildConfig.CONTENTLENS_API_BASE_URL
) : ContentSafetySource {
    override suspend fun reportFor(details: TmdbTitleDetails): ExternalSafetyReport? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw ProxySafetyError.MissingBaseUrl()
        try {
            val result = details.result
            val type = when (result.mediaType) {
                RemoteMediaType.Movie -> "movie"
                RemoteMediaType.Tv -> "tv"
            }
            val title = URLEncoder.encode(result.title, Charsets.UTF_8.name())
            val year = result.releaseYear?.toString().orEmpty()
            val path = "/v1/safety/tmdb/$type/${result.tmdbId}?title=$title&year=$year"
            ExternalSafetyJsonParser.parseReport(get(path))
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "ContentLens proxy parse failed: ${error.message}")
            throw ProxySafetyError.Parsing(error)
        }
    }

    private fun get(path: String): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val snippet = body.take(180)
                SafeLog.warn(TAG, "ContentLens proxy failed status=$status path=${path.substringBefore('?')} body=$snippet")
                when (status) {
                    404 -> throw ProxySafetyError.NoMatch()
                    429 -> throw ProxySafetyError.RateLimited()
                    503 -> throw ProxySafetyError.ProviderNotConfigured()
                    else -> throw ProxySafetyError.Server(status)
                }
            }
            body
        } catch (error: ProxySafetyError) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ProxySafetyError.Offline(error)
        } catch (error: IOException) {
            throw ProxySafetyError.Offline(error)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ContentLensProxy"
    }
}

sealed class ProxySafetyError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingBaseUrl : ProxySafetyError("ContentLens API base URL is not configured.")
    class NoMatch : ProxySafetyError("No external safety match was found.")
    class ProviderNotConfigured : ProxySafetyError("ContentLens API safety provider is not configured.")
    class RateLimited : ProxySafetyError("ContentLens API rate limit was reached.")
    class Server(val statusCode: Int) : ProxySafetyError("ContentLens API request failed.")
    class Offline(cause: Throwable) : ProxySafetyError("ContentLens API could not be reached.", cause)
    class Parsing(cause: Throwable) : ProxySafetyError("ContentLens API response could not be parsed.", cause)
}
