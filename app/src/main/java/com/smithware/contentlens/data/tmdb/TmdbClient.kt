package com.smithware.contentlens.data.tmdb

import com.smithware.contentlens.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

class TmdbClient(
    private val readAccessToken: String = BuildConfig.TMDB_READ_ACCESS_TOKEN,
    private val apiKey: String = BuildConfig.TMDB_API_KEY,
    private val baseUrl: String = "https://api.themoviedb.org/3"
) {
    suspend fun searchAll(query: String, page: Int = 1): Pair<TmdbSearchPage, TmdbImageConfiguration> {
        val trimmed = query.trim()
        if (readAccessToken.isBlank() && apiKey.isBlank()) throw TmdbSearchError.MissingToken()
        return coroutineScope {
            val movie = async { search(RemoteMediaType.Movie, trimmed, page) }
            val tv = async { search(RemoteMediaType.Tv, trimmed, page) }
            val config = async { configuration() }
            val moviePage = movie.await()
            val tvPage = tv.await()
            val merged = merge(moviePage, tvPage)
            merged to config.await()
        }
    }

    suspend fun configuration(): TmdbImageConfiguration = withContext(Dispatchers.IO) {
        try {
            TmdbNormalizer.parseImageConfiguration(get("/configuration"))
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "TMDB image configuration parse failed: ${error.message}")
            throw TmdbSearchError.Parsing(error)
        }
    }

    suspend fun search(mediaType: RemoteMediaType, query: String, page: Int = 1): TmdbSearchPage = withContext(Dispatchers.IO) {
        val endpoint = when (mediaType) {
            RemoteMediaType.Movie -> "/search/movie"
            RemoteMediaType.Tv -> "/search/tv"
        }
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val safePage = page.coerceAtLeast(1)
        val path = withApiKey("$endpoint?query=$encodedQuery&page=$safePage&include_adult=false&language=en-US")
        try {
            TmdbNormalizer.parseSearchPage(get(path), mediaType)
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "TMDB ${mediaType.name} search parse failed: ${error.message}")
            throw TmdbSearchError.Parsing(error)
        }
    }

    suspend fun details(result: NormalizedMediaResult): Pair<TmdbTitleDetails, TmdbImageConfiguration> = coroutineScope {
        if (readAccessToken.isBlank() && apiKey.isBlank()) throw TmdbSearchError.MissingToken()
        val details = async {
            when (result.mediaType) {
                RemoteMediaType.Movie -> movieDetails(result.tmdbId)
                RemoteMediaType.Tv -> tvDetails(result.tmdbId)
            }
        }
        val config = async { configuration() }
        details.await() to config.await()
    }

    suspend fun details(mediaType: RemoteMediaType, tmdbId: Int): Pair<TmdbTitleDetails, TmdbImageConfiguration> = coroutineScope {
        if (readAccessToken.isBlank() && apiKey.isBlank()) throw TmdbSearchError.MissingToken()
        val details = async {
            when (mediaType) {
                RemoteMediaType.Movie -> movieDetails(tmdbId)
                RemoteMediaType.Tv -> tvDetails(tmdbId)
            }
        }
        val config = async { configuration() }
        details.await() to config.await()
    }

    private suspend fun movieDetails(tmdbId: Int): TmdbTitleDetails = withContext(Dispatchers.IO) {
        try {
            TmdbNormalizer.parseMovieDetails(
                get("/movie/$tmdbId?append_to_response=credits,similar,watch/providers,release_dates&language=en-US")
            )
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "TMDB movie details parse failed: ${error.message}")
            throw TmdbSearchError.Parsing(error)
        }
    }

    private suspend fun tvDetails(tmdbId: Int): TmdbTitleDetails = withContext(Dispatchers.IO) {
        try {
            TmdbNormalizer.parseTvDetails(
                get("/tv/$tmdbId?append_to_response=credits,similar,watch/providers,content_ratings&language=en-US")
            )
        } catch (error: JSONException) {
            SafeLog.warn(TAG, "TMDB TV details parse failed: ${error.message}")
            throw TmdbSearchError.Parsing(error)
        }
    }

    private fun merge(movie: TmdbSearchPage, tv: TmdbSearchPage): TmdbSearchPage {
        val mergedResults = (movie.results + tv.results)
            .distinctBy { it.mediaType to it.tmdbId }
            .sortedWith(compareByDescending<NormalizedMediaResult> { it.popularity }.thenByDescending { it.voteCount })
        return TmdbSearchPage(
            results = mergedResults,
            page = minOf(movie.page, tv.page),
            totalPages = maxOf(movie.totalPages, tv.totalPages),
            totalResults = movie.totalResults + tv.totalResults
        )
    }

    private fun get(path: String): String {
        val authenticatedPath = withApiKey(path)
        val url = URL(baseUrl.trimEnd('/') + authenticatedPath)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            if (readAccessToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $readAccessToken")
            }
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val snippet = body.take(180)
                SafeLog.warn(TAG, "TMDB request failed status=$status path=${authenticatedPath.substringBefore('?')} body=$snippet")
                if (status == 401 || status == 403) {
                    throw TmdbSearchError.Authentication(status, snippet)
                }
                throw TmdbSearchError.Server(status, snippet)
            }
            SafeLog.debug(TAG, "TMDB request ok status=$status path=${authenticatedPath.substringBefore('?')}")
            body
        } catch (error: TmdbSearchError) {
            throw error
        } catch (error: SocketTimeoutException) {
            SafeLog.warn(TAG, "TMDB request timed out path=${authenticatedPath.substringBefore('?')}")
            throw TmdbSearchError.Offline(error)
        } catch (error: IOException) {
            SafeLog.warn(TAG, "TMDB network failure path=${authenticatedPath.substringBefore('?')} message=${error.message}")
            throw TmdbSearchError.Offline(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun withApiKey(path: String): String {
        if (readAccessToken.isNotBlank() || apiKey.isBlank() || path.contains("api_key=")) return path
        val separator = if (path.contains("?")) "&" else "?"
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        return "$path${separator}api_key=$encodedKey"
    }

    private companion object {
        const val TAG = "TmdbClient"
    }
}
