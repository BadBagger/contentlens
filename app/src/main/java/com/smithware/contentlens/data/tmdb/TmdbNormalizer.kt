package com.smithware.contentlens.data.tmdb

import org.json.JSONArray
import org.json.JSONObject

object TmdbNormalizer {
    fun normalizeMovie(json: JSONObject): NormalizedMediaResult? {
        val id = json.optInt("id", 0)
        val title = json.optString("title").ifBlank { json.optString("original_title") }
        if (id == 0 || title.isBlank()) return null
        val releaseDate = json.optString("release_date").ifBlank { null }
        return NormalizedMediaResult(
            tmdbId = id,
            mediaType = RemoteMediaType.Movie,
            title = title,
            originalTitle = json.optString("original_title").ifBlank { title },
            overview = json.optString("overview"),
            posterPath = json.optNullableString("poster_path"),
            backdropPath = json.optNullableString("backdrop_path"),
            releaseDate = releaseDate,
            releaseYear = releaseDate?.take(4)?.toIntOrNull(),
            genreIds = json.optIntList("genre_ids"),
            popularity = json.optDouble("popularity", 0.0),
            voteAverage = json.optDouble("vote_average", 0.0),
            voteCount = json.optInt("vote_count", 0),
            adult = json.optBoolean("adult", false),
            originalLanguage = json.optString("original_language")
        )
    }

    fun normalizeTv(json: JSONObject): NormalizedMediaResult? {
        val id = json.optInt("id", 0)
        val title = json.optString("name").ifBlank { json.optString("original_name") }
        if (id == 0 || title.isBlank()) return null
        val firstAirDate = json.optString("first_air_date").ifBlank { null }
        return NormalizedMediaResult(
            tmdbId = id,
            mediaType = RemoteMediaType.Tv,
            title = title,
            originalTitle = json.optString("original_name").ifBlank { title },
            overview = json.optString("overview"),
            posterPath = json.optNullableString("poster_path"),
            backdropPath = json.optNullableString("backdrop_path"),
            releaseDate = firstAirDate,
            releaseYear = firstAirDate?.take(4)?.toIntOrNull(),
            genreIds = json.optIntList("genre_ids"),
            popularity = json.optDouble("popularity", 0.0),
            voteAverage = json.optDouble("vote_average", 0.0),
            voteCount = json.optInt("vote_count", 0),
            adult = json.optBoolean("adult", false),
            originalLanguage = json.optString("original_language")
        )
    }

    fun parseSearchPage(body: String, mediaType: RemoteMediaType): TmdbSearchPage {
        val root = JSONObject(body)
        val resultsJson = root.optJSONArray("results") ?: JSONArray()
        val results = buildList {
            for (index in 0 until resultsJson.length()) {
                val item = resultsJson.optJSONObject(index) ?: continue
                val normalized = when (mediaType) {
                    RemoteMediaType.Movie -> normalizeMovie(item)
                    RemoteMediaType.Tv -> normalizeTv(item)
                }
                if (normalized != null) add(normalized)
            }
        }
        return TmdbSearchPage(
            results = results,
            page = root.optInt("page", 1),
            totalPages = root.optInt("total_pages", 1).coerceAtLeast(1),
            totalResults = root.optInt("total_results", results.size)
        )
    }

    fun parseImageConfiguration(body: String): TmdbImageConfiguration {
        val images = JSONObject(body).getJSONObject("images")
        return TmdbImageConfiguration(
            secureBaseUrl = images.optString("secure_base_url").ifBlank { "https://image.tmdb.org/t/p/" },
            posterSizes = images.optStringList("poster_sizes"),
            backdropSizes = images.optStringList("backdrop_sizes"),
            profileSizes = images.optStringList("profile_sizes"),
            logoSizes = images.optStringList("logo_sizes")
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).ifBlank { null }

private fun JSONObject.optIntList(name: String): List<Int> {
    val array = optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) add(array.optInt(index))
    }
}

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}
