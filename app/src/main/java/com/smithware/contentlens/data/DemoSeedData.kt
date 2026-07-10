package com.smithware.contentlens.data

import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.MediaType
import com.smithware.contentlens.domain.Sensitivity
import com.smithware.contentlens.domain.Severity

object DemoSeedData {
    suspend fun seedIfNeeded(dao: ContentLensDao) {
        if (dao.titleCount() > 0) return
        dao.upsertTitles(titles)
        dao.upsertEntries(entries)
        dao.upsertProfiles(profiles)
        dao.upsertSensitivities(sensitivities)
    }

    val titles = listOf(
        MediaTitleEntity("stellar-harbor", "Stellar Harbor", 2025, MediaType.Movie, "PG-13", "A stranded engineer joins a rescue crew near a collapsing orbital port.", "Blue"),
        MediaTitleEntity("quiet-corners", "Quiet Corners", 2024, MediaType.Movie, "PG", "A family mystery told through old letters, town archives, and careful conversations.", "Green"),
        MediaTitleEntity("midnight-platform", "Midnight Platform", 2023, MediaType.Movie, "R", "Commuters piece together a tense night after a city-wide power failure.", "Indigo"),
        MediaTitleEntity("kitchen-table-season", "Kitchen Table Season", 2026, MediaType.TvShow, "TV-14", "A warm ensemble drama about a community diner changing hands.", "Amber"),
        MediaTitleEntity("signal-nine", "Signal Nine", 2022, MediaType.TvShow, "TV-MA", "A tech investigation series centered on leaked files and personal compromises.", "Slate"),
        MediaTitleEntity("cloud-trail-kids", "Cloud Trail Kids", 2021, MediaType.TvShow, "TV-Y7", "Four friends map imaginary weather routes around their neighborhood.", "Sky"),
        MediaTitleEntity("old-bridge-road", "Old Bridge Road", 2020, MediaType.Movie, "PG-13", "A road trip comedy-drama about siblings settling a family estate.", "Rose"),
        MediaTitleEntity("glass-house-file", "The Glass House File", 2026, MediaType.Movie, null, "A restrained investigative thriller about a witness protection error.", "Teal"),
        MediaTitleEntity("after-the-rainfall", "After the Rainfall", 2019, MediaType.Movie, "PG", "A quiet sports story about rebuilding a neighborhood field after flooding.", "Mint"),
        MediaTitleEntity("north-stairwell", "North Stairwell", 2024, MediaType.TvShow, "TV-14", "An anthology of apartment-building stories with suspense and family conflict.", "Violet")
    )

    val entries = listOf(
        entry("stellar-harbor", ContentCategory.Language, Severity.Moderate, "Several tense exchanges include moderate profanity."),
        entry("stellar-harbor", ContentCategory.Violence, Severity.Moderate, "Sci-fi peril and rescue scenes include impacts and injuries without graphic focus."),
        entry("stellar-harbor", ContentCategory.FlashingLights, Severity.Mild, "Brief warning lights and alarm panels appear during action scenes."),
        entry("quiet-corners", ContentCategory.MatureThemes, Severity.Mild, "Family grief and regret are discussed in gentle terms."),
        entry("quiet-corners", ContentCategory.Alcohol, Severity.Mild, "Adults drink wine during meals."),
        entry("midnight-platform", ContentCategory.Violence, Severity.Strong, "Several intense confrontations include punches, threats, and aftermath injuries."),
        entry("midnight-platform", ContentCategory.Language, Severity.Strong, "Frequent strong language appears during stressful scenes."),
        entry("midnight-platform", ContentCategory.BloodGore, Severity.Moderate, "Visible blood is shown after a station accident."),
        entry("kitchen-table-season", ContentCategory.Alcohol, Severity.Moderate, "Recurring bar and restaurant scenes include drinking and recovery discussions.", season = 1),
        entry("kitchen-table-season", ContentCategory.Bullying, Severity.Mild, "Some episodes include workplace teasing that is challenged by others.", season = 1),
        entry("signal-nine", ContentCategory.SexualContent, Severity.Moderate, "Adult relationships and implied intimacy appear in several episodes.", season = 1),
        entry("signal-nine", ContentCategory.SelfHarm, Severity.Moderate, "A character's past crisis is discussed without visual detail.", season = 1, spoiler = "A late-season interview references a prior hospital stay."),
        entry("signal-nine", ContentCategory.Language, Severity.Strong, "Frequent strong language appears across the season.", season = 1),
        entry("cloud-trail-kids", ContentCategory.ScaryScenes, Severity.Mild, "A few pretend storm sequences may feel tense for younger viewers.", season = 1),
        entry("old-bridge-road", ContentCategory.Language, Severity.Moderate, "Occasional moderate profanity appears during arguments."),
        entry("old-bridge-road", ContentCategory.MatureThemes, Severity.Moderate, "Inheritance, estrangement, and caregiving are discussed throughout."),
        entry("glass-house-file", ContentCategory.DomesticAbuse, Severity.Moderate, "A case file references coercive control and family intimidation without explicit visuals."),
        entry("glass-house-file", ContentCategory.Violence, Severity.Moderate, "Threats and a brief attack are shown with limited injury detail."),
        entry("after-the-rainfall", ContentCategory.ChildDanger, Severity.Mild, "A flood flashback shows children briefly separated from adults."),
        entry("after-the-rainfall", ContentCategory.MatureThemes, Severity.Mild, "The story includes displacement and financial stress."),
        entry("north-stairwell", ContentCategory.JumpScares, Severity.Moderate, "Some episodes use sudden reveals and loud sound cues.", season = 1),
        entry("north-stairwell", ContentCategory.DisturbingImagery, Severity.Moderate, "A few suspense images are unsettling but not graphic.", season = 1),
        entry("north-stairwell", ContentCategory.HateSpeechSlurs, Severity.Mild, "One episode references biased language while clearly challenging it.", season = 1)
    )

    val profiles = listOf(
        UserProfileEntity("parent", "Parent profile", "Extra review for gore, sexual content, and self-harm themes."),
        UserProfileEntity("teen", "Teen profile", "Moderate sensitivity to intense violence and mature themes."),
        UserProfileEntity("personal", "My personal profile", "A balanced profile for everyday viewing."),
        UserProfileEntity("classroom", "Classroom profile", "Avoids strong language, sexual content, and graphic material.")
    )

    val sensitivities = buildList {
        add(ProfileSensitivityEntity("parent", ContentCategory.BloodGore, Sensitivity.AvoidModeratePlus))
        add(ProfileSensitivityEntity("parent", ContentCategory.SexualContent, Sensitivity.AvoidStrongPlus))
        add(ProfileSensitivityEntity("parent", ContentCategory.SelfHarm, Sensitivity.AvoidModeratePlus))
        add(ProfileSensitivityEntity("teen", ContentCategory.Violence, Sensitivity.MildConcern))
        add(ProfileSensitivityEntity("teen", ContentCategory.MatureThemes, Sensitivity.MildConcern))
        add(ProfileSensitivityEntity("personal", ContentCategory.JumpScares, Sensitivity.AvoidStrongPlus))
        add(ProfileSensitivityEntity("personal", ContentCategory.FlashingLights, Sensitivity.AvoidAny))
        add(ProfileSensitivityEntity("classroom", ContentCategory.Language, Sensitivity.AvoidModeratePlus))
        add(ProfileSensitivityEntity("classroom", ContentCategory.SexualContent, Sensitivity.AvoidAny))
        add(ProfileSensitivityEntity("classroom", ContentCategory.BloodGore, Sensitivity.AvoidAny))
    }

    private fun entry(
        titleId: String,
        category: ContentCategory,
        severity: Severity,
        explanation: String,
        season: Int? = null,
        episode: Int? = null,
        spoiler: String? = null
    ) = ContentRatingEntryEntity(
        id = "$titleId-${category.name}",
        titleId = titleId,
        category = category,
        severity = severity,
        explanation = explanation,
        season = season,
        episode = episode,
        spoilerNote = spoiler
    )
}
