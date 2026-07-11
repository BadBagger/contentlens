package com.smithware.contentlens.data.safety

import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.tmdb.TmdbTitleDetails
import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.Severity

data class ExternalSafetyReport(
    val source: String,
    val sourceItemId: Int,
    val sourceItemName: String,
    val reportCount: Int,
    val entries: List<ExternalSafetyEntry>,
    val attribution: String,
    val sourceUrl: String
) {
    fun toRatingEntries(titleKey: String): List<ContentRatingEntryEntity> {
        return entries.map {
            ContentRatingEntryEntity(
                id = "$source-${it.topicId}",
                titleId = titleKey,
                category = it.category,
                severity = it.severity,
                explanation = it.explanation,
                spoilerNote = null,
                source = source
            )
        }
    }
}

data class ExternalSafetyEntry(
    val topicId: Int,
    val topicName: String,
    val category: ContentCategory,
    val severity: Severity,
    val yesCount: Int,
    val noCount: Int,
    val commentCount: Int,
    val explanation: String
)

sealed class ExternalSafetyState {
    data object NotConfigured : ExternalSafetyState()
    data object Loading : ExternalSafetyState()
    data object NoMatch : ExternalSafetyState()
    data class Loaded(val report: ExternalSafetyReport) : ExternalSafetyState()
    data class UpgradeRequired(val message: String) : ExternalSafetyState()
    data class Error(val message: String) : ExternalSafetyState()
}

interface ContentSafetySource {
    suspend fun reportFor(details: TmdbTitleDetails): ExternalSafetyReport?
}
