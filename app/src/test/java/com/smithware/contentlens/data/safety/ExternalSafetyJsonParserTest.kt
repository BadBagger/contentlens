package com.smithware.contentlens.data.safety

import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalSafetyJsonParserTest {
    @Test
    fun parseReport_readsContentLensProxyShape() {
        val report = ExternalSafetyJsonParser.parseReport(
            """
            {
              "source":"DoesTheDogDie community",
              "sourceItemId":10752,
              "sourceItemName":"Old Yeller",
              "reportCount":60,
              "attribution":"Content warnings are provided by DoesTheDogDie community data.",
              "sourceUrl":"https://www.doesthedogdie.com/",
              "entries":[
                {
                  "topicId":153,
                  "topicName":"a dog dies",
                  "category":"AnimalHarm",
                  "severity":"GraphicHeavy",
                  "yesCount":57,
                  "noCount":3,
                  "commentCount":7,
                  "explanation":"Community reports indicate a dog dies."
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("DoesTheDogDie community", report.source)
        assertEquals(10752, report.sourceItemId)
        assertEquals(ContentCategory.AnimalHarm, report.entries.single().category)
        assertEquals(Severity.GraphicHeavy, report.entries.single().severity)
    }
}
