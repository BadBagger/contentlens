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

class DoesTheDogDieClient(
    private val apiKey: String = BuildConfig.DOES_THE_DOG_DIE_API_KEY,
    private val baseUrl: String = "https://www.doesthedogdie.com/api/v3"
) : ContentSafetySource {
    override suspend fun reportFor(details: TmdbTitleDetails): ExternalSafetyReport? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw DoesTheDogDieError.MissingApiKey()
        try {
            val item = findItem(details) ?: return@withContext null
            DoesTheDogDieNormalizer.parseReport(get("/items/${item.id}"))
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "DoesTheDogDie parse failed: ${error.message}")
            throw DoesTheDogDieError.Parsing(error)
        }
    }

    private fun findItem(details: TmdbTitleDetails): DoesTheDogDieItem? {
        val result = details.result
        val byTmdb = DoesTheDogDieNormalizer.parseItems(get("/items?tmdb=${result.tmdbId}"))
            .firstOrNull { it.tmdbId == result.tmdbId && it.itemTypeName.matches(result.mediaType) }
        if (byTmdb != null) return byTmdb

        val encodedName = URLEncoder.encode(result.title, Charsets.UTF_8.name())
        val byName = DoesTheDogDieNormalizer.parseItems(
            get("/items?name=$encodedName&releaseYear=${result.releaseYear ?: ""}")
        )
        return byName.firstOrNull { it.itemTypeName.matches(result.mediaType) }
    }

    private fun get(path: String): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("X-API-KEY", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val snippet = body.take(180)
                SafeLog.warn(TAG, "DoesTheDogDie request failed status=$status path=${path.substringBefore('?')} body=$snippet")
                when (status) {
                    401 -> throw DoesTheDogDieError.Authentication(status)
                    403 -> throw DoesTheDogDieError.UpgradeRequired(status)
                    429 -> throw DoesTheDogDieError.RateLimited(status)
                    else -> throw DoesTheDogDieError.Server(status)
                }
            }
            SafeLog.debug(TAG, "DoesTheDogDie request ok status=$status path=${path.substringBefore('?')}")
            body
        } catch (error: DoesTheDogDieError) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw DoesTheDogDieError.Offline(error)
        } catch (error: IOException) {
            throw DoesTheDogDieError.Offline(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.matches(mediaType: RemoteMediaType): Boolean {
        return when (mediaType) {
            RemoteMediaType.Movie -> equals("Movie", ignoreCase = true)
            RemoteMediaType.Tv -> equals("TV", ignoreCase = true) || equals("TV Show", ignoreCase = true)
        }
    }

    private companion object {
        const val TAG = "DoesTheDogDie"
    }
}

sealed class DoesTheDogDieError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey : DoesTheDogDieError("DoesTheDogDie API key is not configured.")
    class Authentication(val statusCode: Int) : DoesTheDogDieError("DoesTheDogDie authentication failed.")
    class UpgradeRequired(val statusCode: Int) : DoesTheDogDieError("DoesTheDogDie endpoint requires a higher API tier.")
    class RateLimited(val statusCode: Int) : DoesTheDogDieError("DoesTheDogDie rate limit was reached.")
    class Server(val statusCode: Int) : DoesTheDogDieError("DoesTheDogDie request failed.")
    class Offline(cause: Throwable) : DoesTheDogDieError("DoesTheDogDie could not be reached.", cause)
    class Parsing(cause: Throwable) : DoesTheDogDieError("DoesTheDogDie response could not be parsed.", cause)
}
