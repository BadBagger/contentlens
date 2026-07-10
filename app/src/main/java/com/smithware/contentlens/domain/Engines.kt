package com.smithware.contentlens.domain

import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.ProfileSensitivityEntity

class LocalRatingEngine : RatingEngine {
    override fun summarize(entries: List<ContentRatingEntryEntity>): RatingSummary {
        val meaningful = entries.filter { it.severity != Severity.None }
        if (meaningful.isEmpty()) {
            return RatingSummary(LensRating.InsufficientData, emptyList(), entries.size)
        }

        val max = meaningful.maxOf { it.severity.score }
        val sensitiveMax = meaningful
            .filter {
                it.category in setOf(
                    ContentCategory.SexualAssault,
                    ContentCategory.SelfHarm,
                    ContentCategory.SuicideThemes,
                    ContentCategory.ChildDanger,
                    ContentCategory.DomesticAbuse
                )
            }
            .maxOfOrNull { it.severity.score } ?: 0

        val rating = when {
            max >= Severity.GraphicHeavy.score -> LensRating.AdultGraphic
            sensitiveMax >= Severity.Strong.score -> LensRating.EighteenPlus
            max >= Severity.Strong.score -> LensRating.SixteenPlus
            max >= Severity.Moderate.score && sensitiveMax >= Severity.Moderate.score -> LensRating.SixteenPlus
            max >= Severity.Moderate.score -> LensRating.ThirteenPlus
            max == Severity.Mild.score && meaningful.size >= 4 -> LensRating.TenPlus
            max == Severity.Mild.score -> LensRating.SevenPlus
            else -> LensRating.Everyone
        }

        val topWarnings = meaningful
            .sortedWith(compareByDescending<ContentRatingEntryEntity> { it.severity.score }.thenBy { it.category.label })
            .take(4)
            .map { "${it.category.label}: ${it.severity.label}" }

        return RatingSummary(rating, topWarnings, entries.size)
    }
}

class LocalPersonalFitEngine : PersonalFitEngine {
    override fun evaluate(
        entries: List<ContentRatingEntryEntity>,
        sensitivities: List<ProfileSensitivityEntity>
    ): FitLabel {
        if (entries.isEmpty()) return FitLabel.InsufficientData
        if (sensitivities.isEmpty()) return FitLabel.GoodFit

        var score = 0
        entries.forEach { entry ->
            val sensitivity = sensitivities.firstOrNull { it.category == entry.category }?.sensitivity ?: Sensitivity.DontCare
            val triggered = when (sensitivity) {
                Sensitivity.DontCare -> 0
                Sensitivity.MildConcern -> if (entry.severity.score >= Severity.Moderate.score) 1 else 0
                Sensitivity.AvoidModeratePlus -> if (entry.severity.score >= Severity.Moderate.score) 2 else 0
                Sensitivity.AvoidStrongPlus -> if (entry.severity.score >= Severity.Strong.score) 2 else 0
                Sensitivity.AvoidAny -> if (entry.severity != Severity.None) 3 else 0
            }
            score = maxOf(score, triggered)
        }

        return when (score) {
            0 -> FitLabel.GoodFit
            1 -> FitLabel.MildCaution
            2 -> FitLabel.Caution
            else -> FitLabel.NotRecommended
        }
    }
}
