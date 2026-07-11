package com.smithware.contentlens.domain

import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.ProfileSensitivityEntity
import com.smithware.contentlens.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPersonalFitEngineTest {
    private val engine = LocalPersonalFitEngine()
    private val profiles = listOf(
        UserProfileEntity("adult", "Adult", "Main viewer"),
        UserProfileEntity("child", "Child", "Strict child profile")
    )

    @Test
    fun strictestSelectedProfileBlocksSharedViewing() {
        val result = engine.evaluateDetailed(
            entries = listOf(entry(ContentCategory.Nudity, Severity.Mild)),
            sensitivities = listOf(
                ProfileSensitivityEntity("adult", ContentCategory.Nudity, Sensitivity.Allow),
                ProfileSensitivityEntity("child", ContentCategory.Nudity, Sensitivity.NeverShow)
            ),
            profiles = profiles
        )

        assertFalse(result.eligible)
        assertEquals(BoundaryStatus.Blocked, result.status)
        assertTrue(result.explanation.contains("Blocked by a profile rule"))
    }

    @Test
    fun unknownApprovalBlocksWhenContentDataIsMissing() {
        val result = engine.evaluateDetailed(
            entries = emptyList(),
            sensitivities = listOf(ProfileSensitivityEntity("child", ContentCategory.Suicide, Sensitivity.UnknownRequiresApproval)),
            profiles = profiles
        )

        assertFalse(result.eligible)
        assertEquals(BoundaryStatus.Blocked, result.status)
        assertTrue(result.blockedReasons.single().contains("Unknown content requires approval"))
    }

    @Test
    fun boundaryAliasesMapUserFacingCategoriesToExistingReports() {
        val result = engine.evaluateDetailed(
            entries = listOf(entry(ContentCategory.BloodGore, Severity.Moderate)),
            sensitivities = listOf(ProfileSensitivityEntity("adult", ContentCategory.Gore, Sensitivity.AvoidWhenPossible)),
            profiles = profiles
        )

        assertTrue(result.eligible)
        assertEquals(BoundaryStatus.Conflict, result.status)
        assertTrue(result.warningReasons.single().contains("Blood/gore"))
    }

    private fun entry(category: ContentCategory, severity: Severity): ContentRatingEntryEntity =
        ContentRatingEntryEntity(
            id = category.name,
            titleId = "title",
            category = category,
            severity = severity,
            explanation = "Test entry"
        )
}
