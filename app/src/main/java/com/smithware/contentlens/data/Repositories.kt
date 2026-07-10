package com.smithware.contentlens.data

import com.smithware.contentlens.domain.ContentReportRepository
import com.smithware.contentlens.domain.MediaMetadataRepository

class LocalMediaMetadataRepository(
    private val dao: ContentLensDao
) : MediaMetadataRepository {
    override suspend fun searchTitles(query: String): List<MediaTitleEntity> = dao.searchTitles(query)
}

class LocalContentReportRepository(
    private val dao: ContentLensDao
) : ContentReportRepository {
    override suspend fun submitReport(report: ContentReportEntity) {
        dao.upsertReport(report)
        dao.upsertEntries(
            listOf(
                ContentRatingEntryEntity(
                    id = "local-${report.id}",
                    titleId = report.titleId,
                    category = report.category,
                    severity = report.severity,
                    explanation = report.explanation,
                    spoilerNote = report.spoilerNote,
                    season = report.season,
                    episode = report.episode,
                    source = "Local report"
                )
            )
        )
    }
}
