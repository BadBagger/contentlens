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

    @Test
    fun movieDetailsParseCertificationCastProvidersAndSimilar() {
        val details = TmdbNormalizer.parseMovieDetails(
            """
            {
              "id": 1,
              "title": "Moana",
              "release_date": "2016-11-23",
              "runtime": 107,
              "genres": [{"id": 16, "name": "Animation"}],
              "release_dates": {
                "results": [
                  {"iso_3166_1": "US", "release_dates": [{"certification": "PG"}]}
                ]
              },
              "credits": {"cast": [{"id": 10, "name": "Auli'i Cravalho", "character": "Moana", "profile_path": "/profile.jpg"}]},
              "similar": {"results": [{"id": 2, "title": "Moana 2", "release_date": "2024-11-27"}]},
              "watch/providers": {"results": {"US": {"flatrate": [{"provider_id": 337, "provider_name": "Disney Plus", "logo_path": "/logo.jpg"}]}}}
            }
            """.trimIndent()
        )

        assertEquals("Moana", details.result.title)
        assertEquals(107, details.runtimeMinutes)
        assertEquals("PG", details.certification)
        assertEquals(listOf("Animation"), details.genres)
        assertEquals("Auli'i Cravalho", details.cast.single().name)
        assertEquals("Moana 2", details.similar.single().title)
        assertEquals("Disney Plus", details.watchProviders.single().name)
        assertEquals("/logo.jpg", details.watchProviders.single().logoPath)
        assertEquals(WatchAccessType.Subscription, details.watchProviders.single().accessType)
    }

    @Test
    fun tvDetailsParseContentRatingAndSeasonCounts() {
        val details = TmdbNormalizer.parseTvDetails(
            """
            {
              "id": 82728,
              "name": "Bluey",
              "first_air_date": "2018-10-01",
              "episode_run_time": [7],
              "number_of_seasons": 4,
              "number_of_episodes": 154,
              "status": "Returning Series",
              "genres": [{"id": 10762, "name": "Kids"}],
              "content_ratings": {"results": [{"iso_3166_1": "US", "rating": "TV-G"}]},
              "credits": {"cast": [{"id": 11, "name": "David McCormack", "character": "Bandit"}]},
              "similar": {"results": [{"id": 3, "name": "Hey Duggee", "first_air_date": "2014-12-17"}]},
              "watch/providers": {"results": {"US": {"flatrate": [{"provider_name": "Disney Plus"}]}}}
            }
            """.trimIndent()
        )

        assertEquals("Bluey", details.result.title)
        assertEquals("TV-G", details.certification)
        assertEquals(7, details.episodeRuntimeMinutes)
        assertEquals(4, details.numberOfSeasons)
        assertEquals(154, details.numberOfEpisodes)
        assertEquals("Returning Series", details.status)
        assertEquals(listOf("Kids"), details.genres)
    }
}
