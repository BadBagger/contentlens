package com.smithware.contentlens.data.safety

import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.Severity
import org.json.JSONArray
import org.json.JSONObject

object DoesTheDogDieNormalizer {
    fun parseItems(body: String): List<DoesTheDogDieItem> {
        val array = JSONArray(body)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optInt("id", 0)
                val name = item.optString("name")
                if (id == 0 || name.isBlank()) continue
                add(
                    DoesTheDogDieItem(
                        id = id,
                        name = name,
                        itemTypeName = item.optString("itemTypeName"),
                        tmdbId = item.optInt("tmdbId", 0).takeIf { it > 0 },
                        releaseYear = item.optInt("releaseYear", 0).takeIf { it > 0 }
                    )
                )
            }
        }
    }

    fun parseReport(body: String): ExternalSafetyReport {
        val root = JSONObject(body)
        val sourceItemId = root.optInt("id", 0)
        val sourceItemName = root.optString("name").ifBlank { "Matched title" }
        val stats = root.optJSONArray("topicItemStats") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until stats.length()) {
                val stat = stats.optJSONObject(index) ?: continue
                normalizeStat(stat)?.let(::add)
            }
        }.sortedWith(compareByDescending<ExternalSafetyEntry> { it.severity.score }.thenByDescending { it.yesCount })

        return ExternalSafetyReport(
            source = "DoesTheDogDie community",
            sourceItemId = sourceItemId,
            sourceItemName = sourceItemName,
            reportCount = entries.sumOf { it.yesCount + it.noCount },
            entries = entries.take(12),
            attribution = "Content warnings are provided by DoesTheDogDie community data.",
            sourceUrl = "https://www.doesthedogdie.com/"
        )
    }

    private fun normalizeStat(stat: JSONObject): ExternalSafetyEntry? {
        val topicId = stat.optInt("topicId", 0)
        val topicName = stat.optString("topicName")
        if (topicId == 0 || topicName.isBlank()) return null
        val yes = stat.optInt("yesSum", 0).coerceAtLeast(0)
        val no = stat.optInt("noSum", 0).coerceAtLeast(0)
        val comments = stat.optInt("numComments", 0).coerceAtLeast(0)
        if (yes == 0 && no == 0) return null
        val category = mapTopicToCategory(topicName)
        val severity = inferSeverity(yes, no, comments)
        if (severity == Severity.None) return null
        return ExternalSafetyEntry(
            topicId = topicId,
            topicName = topicName,
            category = category,
            severity = severity,
            yesCount = yes,
            noCount = no,
            commentCount = comments,
            explanation = explanation(topicName, yes, no, comments)
        )
    }

    fun mapTopicToCategory(topicName: String): ContentCategory {
        val name = topicName.lowercase()
        return when {
            name.contains("dog") || name.contains("cat") || name.contains("animal") || name.contains("pet") ||
                name.contains("horse") || name.contains("rabbit") -> ContentCategory.AnimalHarm
            name.contains("sexual assault") || name.contains("rape") || name.contains("pedophilia") -> ContentCategory.SexualAssault
            name.contains("sex") || name.contains("sexual") -> ContentCategory.SexualContent
            name.contains("nud") -> ContentCategory.Nudity
            name.contains("suicide") -> ContentCategory.SuicideThemes
            name.contains("self harm") || name.contains("self-harm") || name.contains("cutting") -> ContentCategory.SelfHarm
            name.contains("drug") || name.contains("overdose") -> ContentCategory.Drugs
            name.contains("alcohol") || name.contains("drunk") -> ContentCategory.Alcohol
            name.contains("smok") || name.contains("vaping") -> ContentCategory.SmokingVaping
            name.contains("gore") || name.contains("blood") || name.contains("mutilation") || name.contains("amputation") -> ContentCategory.BloodGore
            name.contains("jump scare") || name.contains("sudden loud") -> ContentCategory.JumpScares
            name.contains("flashing") || name.contains("seizure") -> ContentCategory.FlashingLights
            name.contains("violence") || name.contains("gun") || name.contains("torture") || name.contains("stabbing") -> ContentCategory.Violence
            name.contains("child") || name.contains("kid") || name.contains("baby") || name.contains("infant") -> ContentCategory.ChildDanger
            name.contains("language") || name.contains("slur") || name.contains("obscene") -> ContentCategory.Language
            name.contains("bully") -> ContentCategory.Bullying
            name.contains("domestic") -> ContentCategory.DomesticAbuse
            name.contains("disturb") || name.contains("body horror") || name.contains("vomit") -> ContentCategory.DisturbingImagery
            name.contains("scary") || name.contains("fear") || name.contains("horror") || name.contains("demonic") -> ContentCategory.ScaryScenes
            else -> ContentCategory.MatureThemes
        }
    }

    private fun inferSeverity(yes: Int, no: Int, comments: Int): Severity {
        if (yes <= 0) return Severity.None
        val total = yes + no
        val ratio = if (total == 0) 1.0 else yes.toDouble() / total
        return when {
            yes >= 50 && ratio >= 0.85 -> Severity.GraphicHeavy
            yes >= 15 && ratio >= 0.75 -> Severity.Strong
            yes >= 5 && ratio >= 0.55 -> Severity.Moderate
            comments >= 3 && yes >= 3 -> Severity.Moderate
            else -> Severity.Mild
        }
    }

    private fun explanation(topicName: String, yes: Int, no: Int, comments: Int): String {
        val commentText = if (comments > 0) ", with $comments context comments" else ""
        return "Community reports indicate \"$topicName\" ($yes yes / $no no$commentText)."
    }
}

data class DoesTheDogDieItem(
    val id: Int,
    val name: String,
    val itemTypeName: String,
    val tmdbId: Int?,
    val releaseYear: Int?
)
