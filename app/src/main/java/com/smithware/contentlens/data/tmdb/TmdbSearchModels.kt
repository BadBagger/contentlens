package com.smithware.contentlens.data.tmdb

enum class RemoteMediaType(val label: String) {
    Movie("Movie"),
    Tv("TV")
}

data class NormalizedMediaResult(
    val tmdbId: Int,
    val mediaType: RemoteMediaType,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val releaseYear: Int?,
    val genreIds: List<Int>,
    val popularity: Double,
    val voteAverage: Double,
    val voteCount: Int,
    val adult: Boolean,
    val originalLanguage: String
)

data class TmdbSearchPage(
    val results: List<NormalizedMediaResult>,
    val page: Int,
    val totalPages: Int,
    val totalResults: Int
) {
    val hasMore: Boolean get() = page < totalPages
}

data class TmdbTitleDetails(
    val result: NormalizedMediaResult,
    val runtimeMinutes: Int?,
    val episodeRuntimeMinutes: Int?,
    val genres: List<String>,
    val status: String?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val certification: String?,
    val cast: List<TmdbCastMember>,
    val similar: List<NormalizedMediaResult>,
    val watchProviders: List<String>
)

data class TmdbCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profilePath: String?
)

data class TmdbImageConfiguration(
    val secureBaseUrl: String = "https://image.tmdb.org/t/p/",
    val posterSizes: List<String> = listOf("w342", "w500", "original"),
    val backdropSizes: List<String> = listOf("w780", "w1280", "original"),
    val profileSizes: List<String> = listOf("w185", "h632", "original"),
    val logoSizes: List<String> = listOf("w154", "w300", "original")
)

sealed class TmdbSearchError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingToken : TmdbSearchError("TMDB read access token is not configured.")
    class Offline(cause: Throwable) : TmdbSearchError("Network is unavailable.", cause)
    class Authentication(val statusCode: Int, val bodySnippet: String) : TmdbSearchError("TMDB authentication failed with HTTP $statusCode.")
    class Server(val statusCode: Int, val bodySnippet: String) : TmdbSearchError("TMDB request failed with HTTP $statusCode.")
    class Parsing(cause: Throwable) : TmdbSearchError("TMDB response could not be parsed.", cause)
}
