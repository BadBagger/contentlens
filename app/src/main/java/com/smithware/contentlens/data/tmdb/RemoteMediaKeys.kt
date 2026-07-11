package com.smithware.contentlens.data.tmdb

fun remoteMediaKey(tmdbId: Int, mediaType: RemoteMediaType): String {
    return "tmdb:${mediaType.name.lowercase()}:$tmdbId"
}
