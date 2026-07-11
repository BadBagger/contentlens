package com.smithware.contentlens.data.tmdb

import org.json.JSONArray
import org.json.JSONException
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

    fun parseMovieDetails(body: String): TmdbTitleDetails {
        val root = JSONObject(body)
        val base = normalizeMovie(root) ?: throw JSONException("Movie details missing title or id")
        val releaseResults = root.optJSONObject("release_dates")
            ?.optJSONArray("results")
            ?.findCountry("US")
            ?.optJSONArray("release_dates")
        val certification = releaseResults?.firstCertification()
        return TmdbTitleDetails(
            result = base,
            runtimeMinutes = root.optInt("runtime", 0).takeIf { it > 0 },
            episodeRuntimeMinutes = null,
            genres = root.optGenreNames(),
            status = root.optNullableString("status"),
            numberOfSeasons = null,
            numberOfEpisodes = null,
            certification = certification,
            cast = root.optJSONObject("credits").optCast(),
            similar = root.optJSONObject("similar").optResults(RemoteMediaType.Movie),
            watchProviders = root.optJSONObject("watch/providers").optUsProviders()
        )
    }

    fun parseTvDetails(body: String): TmdbTitleDetails {
        val root = JSONObject(body)
        val base = normalizeTv(root) ?: throw JSONException("TV details missing title or id")
        val contentRatings = root.optJSONObject("content_ratings")
            ?.optJSONArray("results")
            ?.findCountry("US")
            ?.optString("rating")
            ?.ifBlank { null }
        return TmdbTitleDetails(
            result = base,
            runtimeMinutes = null,
            episodeRuntimeMinutes = root.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 },
            genres = root.optGenreNames(),
            status = root.optNullableString("status"),
            numberOfSeasons = root.optInt("number_of_seasons", 0).takeIf { it > 0 },
            numberOfEpisodes = root.optInt("number_of_episodes", 0).takeIf { it > 0 },
            certification = contentRatings,
            cast = root.optJSONObject("credits").optCast(),
            similar = root.optJSONObject("similar").optResults(RemoteMediaType.Tv),
            watchProviders = root.optJSONObject("watch/providers").optUsProviders()
        )
    }
}

internal fun JSONObject.optNullableString(name: String): String? =
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

private fun JSONObject?.optCast(): List<TmdbCastMember> {
    val array = this?.optJSONArray("cast") ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), 12)) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            add(
                TmdbCastMember(
                    id = item.optInt("id"),
                    name = name,
                    character = item.optString("character"),
                    profilePath = item.optNullableString("profile_path")
                )
            )
        }
    }
}

private fun JSONObject?.optResults(mediaType: RemoteMediaType): List<NormalizedMediaResult> {
    val array = this?.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), 10)) {
            val item = array.optJSONObject(index) ?: continue
            val normalized = when (mediaType) {
                RemoteMediaType.Movie -> TmdbNormalizer.normalizeMovie(item)
                RemoteMediaType.Tv -> TmdbNormalizer.normalizeTv(item)
            }
            if (normalized != null) add(normalized)
        }
    }
}

private fun JSONObject.optGenreNames(): List<String> {
    val array = optJSONArray("genres") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val name = array.optJSONObject(index)?.optString("name").orEmpty()
            if (name.isNotBlank()) add(name)
        }
    }
}

private fun JSONArray.findCountry(country: String): JSONObject? {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        if (item.optString("iso_3166_1").equals(country, ignoreCase = true)) return item
    }
    return null
}

private fun JSONArray.firstCertification(): String? {
    for (index in 0 until length()) {
        val certification = optJSONObject(index)?.optString("certification").orEmpty()
        if (certification.isNotBlank()) return certification
    }
    return null
}

private fun JSONObject?.optUsProviders(): List<TmdbWatchProvider> {
    val us = this?.optJSONObject("results")?.optJSONObject("US") ?: return emptyList()
    val arrays = listOf("flatrate", "free", "ads", "rent", "buy")
    return arrays.flatMap { key ->
        val array = us.optJSONArray(key) ?: return@flatMap emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val provider = array.optJSONObject(index) ?: continue
                val providerName = provider.optString("provider_name")
                if (providerName.isNotBlank()) {
                    add(
                        TmdbWatchProvider(
                            id = provider.optInt("provider_id", 0),
                            name = providerName,
                            logoPath = provider.optNullableString("logo_path")
                        )
                    )
                }
            }
        }
    }.distinctBy { it.id.takeIf { id -> id != 0 }?.toString() ?: it.name }.take(8)
}
