package com.smithware.contentlens.data.tmdb

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbNormalizerTest {
    @Test
    fun movieResponseNormalizesTitleAndReleaseDate() {
        val result = TmdbNormalizer.normalizeMovie(
            JSONObject(
                """
                {
                  "id": 11,
                  "title": "Star Wars",
                  "original_title": "Star Wars",
                  "overview": "A space opera.",
                  "poster_path": "/poster.jpg",
                  "backdrop_path": "/backdrop.jpg",
                  "release_date": "1977-05-25",
                  "genre_ids": [12, 878],
                  "popularity": 99.5,
                  "vote_average": 8.2,
                  "vote_count": 12000,
                  "adult": false,
                  "original_language": "en"
                }
                """.trimIndent()
            )
        )

        requireNotNull(result)
        assertEquals(11, result.tmdbId)
        assertEquals(RemoteMediaType.Movie, result.mediaType)
        assertEquals("Star Wars", result.title)
        assertEquals("1977-05-25", result.releaseDate)
        assertEquals(1977, result.releaseYear)
        assertEquals(listOf(12, 878), result.genreIds)
        assertFalse(result.adult)
    }

    @Test
    fun tvResponseNormalizesNameAndFirstAirDate() {
        val result = TmdbNormalizer.normalizeTv(
            JSONObject(
                """
                {
                  "id": 1396,
                  "name": "Breaking Bad",
                  "original_name": "Breaking Bad",
                  "overview": "A chemistry teacher changes course.",
                  "poster_path": "/tv-poster.jpg",
                  "backdrop_path": "/tv-backdrop.jpg",
                  "first_air_date": "2008-01-20",
                  "genre_ids": [18, 80],
                  "popularity": 200.0,
                  "vote_average": 8.9,
                  "vote_count": 15000,
                  "adult": false,
                  "original_language": "en"
                }
                """.trimIndent()
            )
        )

        requireNotNull(result)
        assertEquals(1396, result.tmdbId)
        assertEquals(RemoteMediaType.Tv, result.mediaType)
        assertEquals("Breaking Bad", result.title)
        assertEquals("2008-01-20", result.releaseDate)
        assertEquals(2008, result.releaseYear)
        assertEquals(listOf(18, 80), result.genreIds)
    }

    @Test
    fun missingRequiredTitleReturnsNull() {
        assertNull(TmdbNormalizer.normalizeMovie(JSONObject("""{"id": 1}""")))
    }
}
