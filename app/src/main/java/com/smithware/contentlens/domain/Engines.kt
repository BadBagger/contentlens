package com.smithware.contentlens.domain

import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.ProfileSensitivityEntity
import com.smithware.contentlens.data.UserProfileEntity

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
    ): FitLabel = when (evaluateDetailed(entries, sensitivities).status) {
        BoundaryStatus.Safe -> FitLabel.GoodFit
        BoundaryStatus.Warning -> FitLabel.MildCaution
        BoundaryStatus.Conflict -> FitLabel.Caution
        BoundaryStatus.Blocked -> FitLabel.NotRecommended
        BoundaryStatus.InsufficientInformation -> FitLabel.InsufficientData
    }

    override fun evaluateDetailed(
        entries: List<ContentRatingEntryEntity>,
        sensitivities: List<ProfileSensitivityEntity>,
        profiles: List<UserProfileEntity>
    ): BoundaryEvaluation {
        val effective = strictestSensitivities(sensitivities)
        if (entries.isEmpty()) {
            val approvalProfiles = sensitivities
                .filter { it.sensitivity == Sensitivity.UnknownRequiresApproval }
                .map { it.profileLabel(profiles) }
                .distinct()
            return if (approvalProfiles.isEmpty()) {
                BoundaryEvaluation(
                    eligible = true,
                    status = BoundaryStatus.InsufficientInformation,
                    compatibilityScore = 45,
                    warningReasons = listOf("Detailed content information is not available yet."),
                    explanation = "Insufficient content information. Detailed warnings are not available yet."
                )
            } else {
                BoundaryEvaluation(
                    eligible = false,
                    status = BoundaryStatus.Blocked,
                    compatibilityScore = 0,
                    blockedReasons = listOf("Unknown content requires approval for ${approvalProfiles.joinToString(", ")}."),
                    explanation = "Blocked by a profile rule. Unknown content requires approval for ${approvalProfiles.joinToString(", ")}."
                )
            }
        }

        val blocked = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val matched = mutableListOf<String>()
        effective.forEach { sensitivity ->
            val entry = entries
                .filter { sensitivity.category.matches(it.category) }
                .maxByOrNull { it.severity.score }
            val profileNames = sensitivities
                .filter { it.category == sensitivity.category && it.sensitivity.strictness == sensitivity.sensitivity.strictness }
                .map { it.profileLabel(profiles) }
                .distinct()
                .ifEmpty { listOf("selected profile") }
            if (entry == null) {
                if (sensitivity.sensitivity == Sensitivity.UnknownRequiresApproval) {
                    blocked += "${sensitivity.category.label} is unknown and requires approval for ${profileNames.joinToString(", ")}."
                }
                return@forEach
            }
            val profileText = profileNames.joinToString(", ")
            when (sensitivity.sensitivity.boundaryAction(entry.severity)) {
                BoundaryAction.None -> matched += "${sensitivity.category.label} is within ${profileText} preferences."
                BoundaryAction.Warn -> warnings += "${entry.category.label} is ${entry.severity.label.lowercase()} for ${profileText}."
                BoundaryAction.Conflict -> warnings += "${entry.category.label} may not fit ${profileText} preferences."
                BoundaryAction.Block -> blocked += "${entry.category.label} is ${entry.severity.label.lowercase()} and is blocked for ${profileText}."
            }
        }

        return when {
            blocked.isNotEmpty() -> BoundaryEvaluation(
                eligible = false,
                status = BoundaryStatus.Blocked,
                compatibilityScore = 0,
                blockedReasons = blocked,
                warningReasons = warnings,
                matchedPreferences = matched.take(3),
                explanation = "Blocked by a profile rule. ${blocked.first()}"
            )
            warnings.any { it.contains("may not fit") } -> BoundaryEvaluation(
                eligible = true,
                status = BoundaryStatus.Conflict,
                compatibilityScore = 45,
                warningReasons = warnings,
                matchedPreferences = matched.take(3),
                explanation = "Conflicts with your preferences. ${warnings.first()}"
            )
            warnings.isNotEmpty() -> BoundaryEvaluation(
                eligible = true,
                status = BoundaryStatus.Warning,
                compatibilityScore = 72,
                warningReasons = warnings,
                matchedPreferences = matched.take(3),
                explanation = if (warnings.size == 1) "Contains one warning. ${warnings.first()}" else "Contains ${warnings.size} warnings. ${warnings.first()}"
            )
            else -> BoundaryEvaluation(
                eligible = true,
                status = BoundaryStatus.Safe,
                compatibilityScore = 96,
                matchedPreferences = matched.take(4),
                explanation = "Safe for your selected profiles. No selected boundaries were triggered."
            )
        }
    }

    private fun strictestSensitivities(sensitivities: List<ProfileSensitivityEntity>): List<ProfileSensitivityEntity> =
        sensitivities
            .groupBy { it.category }
            .map { (_, items) -> items.maxBy { it.sensitivity.strictness } }
}

private enum class BoundaryAction {
    None,
    Warn,
    Conflict,
    Block
}

private val Sensitivity.strictness: Int
    get() = when (this) {
        Sensitivity.Allow, Sensitivity.DontCare -> 0
        Sensitivity.WarnMe, Sensitivity.MildConcern -> 1
        Sensitivity.AvoidWhenPossible, Sensitivity.AvoidModeratePlus -> 2
        Sensitivity.AvoidStrongPlus -> 3
        Sensitivity.NeverShow, Sensitivity.AvoidAny -> 4
        Sensitivity.UnknownRequiresApproval -> 5
    }

private fun Sensitivity.boundaryAction(severity: Severity): BoundaryAction = when (this) {
    Sensitivity.Allow, Sensitivity.DontCare -> BoundaryAction.None
    Sensitivity.WarnMe -> if (severity != Severity.None) BoundaryAction.Warn else BoundaryAction.None
    Sensitivity.MildConcern -> if (severity.score >= Severity.Moderate.score) BoundaryAction.Warn else BoundaryAction.None
    Sensitivity.AvoidWhenPossible, Sensitivity.AvoidModeratePlus -> if (severity.score >= Severity.Moderate.score) BoundaryAction.Conflict else BoundaryAction.None
    Sensitivity.AvoidStrongPlus -> if (severity.score >= Severity.Strong.score) BoundaryAction.Block else BoundaryAction.None
    Sensitivity.NeverShow, Sensitivity.AvoidAny -> if (severity != Severity.None) BoundaryAction.Block else BoundaryAction.None
    Sensitivity.UnknownRequiresApproval -> BoundaryAction.None
}

private fun ProfileSensitivityEntity.profileLabel(profiles: List<UserProfileEntity>): String =
    profiles.firstOrNull { it.id == profileId }?.name ?: profileId

private fun ContentCategory.matches(entryCategory: ContentCategory): Boolean =
    this == entryCategory || when (this) {
        ContentCategory.Profanity -> entryCategory == ContentCategory.Language || entryCategory == ContentCategory.HateSpeechSlurs
        ContentCategory.GraphicViolence -> entryCategory == ContentCategory.Violence
        ContentCategory.Gore -> entryCategory == ContentCategory.BloodGore
        ContentCategory.DrugUse -> entryCategory == ContentCategory.Drugs
        ContentCategory.AlcoholUse -> entryCategory == ContentCategory.Alcohol
        ContentCategory.Smoking -> entryCategory == ContentCategory.SmokingVaping
        ContentCategory.Horror -> entryCategory == ContentCategory.ScaryScenes || entryCategory == ContentCategory.DisturbingImagery
        ContentCategory.Suicide -> entryCategory == ContentCategory.SuicideThemes
        ContentCategory.ChildHarm -> entryCategory == ContentCategory.ChildDanger
        ContentCategory.DisturbingMedicalImagery -> entryCategory == ContentCategory.DisturbingImagery || entryCategory == ContentCategory.BloodGore
        ContentCategory.LoudSuddenSounds -> entryCategory == ContentCategory.JumpScares
        else -> false
    }
