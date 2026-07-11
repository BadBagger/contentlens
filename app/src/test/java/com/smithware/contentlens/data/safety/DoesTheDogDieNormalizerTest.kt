package com.smithware.contentlens.data.safety

import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoesTheDogDieNormalizerTest {
    @Test
    fun parseReport_mapsTopicStatsToContentLensCategories() {
        val report = DoesTheDogDieNormalizer.parseReport(
            """
            {
              "id": 10752,
              "name": "Old Yeller",
              "topicItemStats": [
                {"topicId":153,"topicName":"a dog dies","yesSum":57,"noSum":3,"numComments":7},
                {"topicId":222,"topicName":"there are jump scares","yesSum":4,"noSum":9,"numComments":1},
                {"topicId":333,"topicName":"there is sexual assault","yesSum":16,"noSum":1,"numComments":4}
              ]
            }
            """.trimIndent()
        )

        assertEquals("DoesTheDogDie community", report.source)
        assertEquals(90, report.reportCount)
        assertTrue(report.entries.any { it.category == ContentCategory.AnimalHarm && it.severity == Severity.GraphicHeavy })
        assertTrue(report.entries.any { it.category == ContentCategory.SexualAssault && it.severity == Severity.Strong })
        assertTrue(report.entries.any { it.category == ContentCategory.JumpScares && it.severity == Severity.Mild })
    }

    @Test
    fun parseItems_readsTmdbMovieMatch() {
        val items = DoesTheDogDieNormalizer.parseItems(
            """
            [
              {"id":10752,"name":"Old Yeller","releaseYear":1957,"itemTypeName":"Movie","tmdbId":22660}
            ]
            """.trimIndent()
        )

        assertEquals(10752, items.single().id)
        assertEquals(22660, items.single().tmdbId)
        assertEquals("Movie", items.single().itemTypeName)
    }
}
