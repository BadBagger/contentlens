package com.smithware.contentlens.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RemoteMediaKeysTest {
    @Test
    fun remoteMediaKey_keepsMovieAndTvReportsSeparate() {
        val movieKey = remoteMediaKey(550, RemoteMediaType.Movie)
        val tvKey = remoteMediaKey(550, RemoteMediaType.Tv)

        assertEquals("tmdb:movie:550", movieKey)
        assertEquals("tmdb:tv:550", tvKey)
        assertNotEquals(movieKey, tvKey)
    }
}
