package com.smithware.contentlens.data.tmdb

class ImageUrlBuilder(
    private val configuration: TmdbImageConfiguration = TmdbImageConfiguration()
) {
    fun poster(path: String?, size: ImageSize = ImageSize.MediumPoster): String? =
        build(path, pickSize(configuration.posterSizes, size.preferredPoster))

    fun backdrop(path: String?, size: ImageSize = ImageSize.LargeBackdrop): String? =
        build(path, pickSize(configuration.backdropSizes, size.preferredBackdrop))

    fun profile(path: String?, size: ImageSize = ImageSize.SmallProfile): String? =
        build(path, pickSize(configuration.profileSizes, size.preferredProfile))

    fun logo(path: String?, size: ImageSize = ImageSize.ProviderLogo): String? =
        build(path, pickSize(configuration.logoSizes, size.preferredLogo))

    private fun build(path: String?, size: String): String? {
        if (path.isNullOrBlank()) return null
        val cleanBase = configuration.secureBaseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$cleanBase/$size/$cleanPath"
    }

    private fun pickSize(available: List<String>, preferred: List<String>): String =
        preferred.firstOrNull { it in available } ?: available.lastOrNull() ?: "original"
}

enum class ImageSize(
    val preferredPoster: List<String> = emptyList(),
    val preferredBackdrop: List<String> = emptyList(),
    val preferredProfile: List<String> = emptyList(),
    val preferredLogo: List<String> = emptyList()
) {
    MediumPoster(preferredPoster = listOf("w342", "w500", "original")),
    LargeBackdrop(preferredBackdrop = listOf("w1280", "w780", "original")),
    SmallProfile(preferredProfile = listOf("w185", "h632", "original")),
    ProviderLogo(preferredLogo = listOf("w154", "w300", "original"))
}
