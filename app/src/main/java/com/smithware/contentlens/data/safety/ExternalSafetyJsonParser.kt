package com.smithware.contentlens.data.safety

import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.Severity
import org.json.JSONArray
import org.json.JSONObject

object ExternalSafetyJsonParser {
    fun parseReport(body: String): ExternalSafetyReport {
        return parseReport(JSONObject(body))
    }

    fun parseReport(root: JSONObject): ExternalSafetyReport {
        return ExternalSafetyReport(
            source = root.optString("source").ifBlank { "External content safety source" },
            sourceItemId = root.optInt("sourceItemId", 0),
            sourceItemName = root.optString("sourceItemName").ifBlank { "Matched title" },
            reportCount = root.optInt("reportCount", 0),
            entries = root.optJSONArray("entries").toEntries(),
            attribution = root.optString("attribution"),
            sourceUrl = root.optString("sourceUrl")
        )
    }

    private fun JSONArray?.toEntries(): List<ExternalSafetyEntry> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val category = runCatching {
                    ContentCategory.valueOf(item.optString("category"))
                }.getOrElse { ContentCategory.MatureThemes }
                val severity = runCatching {
                    Severity.valueOf(item.optString("severity"))
                }.getOrElse { Severity.Mild }
                add(
                    ExternalSafetyEntry(
                        topicId = item.optInt("topicId", 0),
                        topicName = item.optString("topicName"),
                        category = category,
                        severity = severity,
                        yesCount = item.optInt("yesCount", 0),
                        noCount = item.optInt("noCount", 0),
                        commentCount = item.optInt("commentCount", 0),
                        explanation = item.optString("explanation")
                    )
                )
            }
        }
    }
}
