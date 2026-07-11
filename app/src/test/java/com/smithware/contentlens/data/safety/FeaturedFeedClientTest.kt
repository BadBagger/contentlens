package com.smithware.contentlens.data.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedFeedClientTest {
    @Test
    fun parseFeatured_mapsLoadedReportsAndNullSafetyStates() {
        val states = FeaturedFeedClient(baseUrl = "https://contentlens.test").parseFeatured(
            """
            {
              "schemaVersion": 1,
              "sections": [
                {
                  "key": "little-kids",
                  "items": [
                    {
                      "mediaType": "tv",
                      "tmdbId": 82728,
                      "safety": {
                        "source": "DoesTheDogDie community",
                        "sourceItemId": 1,
                        "sourceItemName": "Bluey",
                        "reportCount": 4,
                        "entries": [
                          {
                            "topicId": 10,
                            "topicName": "there are jump scares",
                            "category": "JumpScares",
                            "severity": "Mild",
                            "yesCount": 3,
                            "noCount": 1,
                            "commentCount": 0,
                            "explanation": "Community reports indicate jump scares."
                          }
                        ]
                      }
                    },
                    {
                      "mediaType": "movie",
                      "tmdbId": 862,
                      "safety": null
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(states["tmdb:tv:82728"] is ExternalSafetyState.Loaded)
        assertEquals(ExternalSafetyState.NoMatch, states["tmdb:movie:862"])
    }
}
