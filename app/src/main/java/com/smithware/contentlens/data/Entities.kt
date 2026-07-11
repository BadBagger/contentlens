package com.smithware.contentlens.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.MediaType
import com.smithware.contentlens.domain.Sensitivity
import com.smithware.contentlens.domain.Severity
import com.smithware.contentlens.data.tmdb.WatchAccessType

@Entity(tableName = "media_titles")
data class MediaTitleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val year: Int,
    val type: MediaType,
    val officialRating: String?,
    val summary: String,
    val posterTone: String
)

@Entity(tableName = "content_rating_entries")
data class ContentRatingEntryEntity(
    @PrimaryKey val id: String,
    val titleId: String,
    val category: ContentCategory,
    val severity: Severity,
    val explanation: String,
    val isSpoilerFree: Boolean = true,
    val spoilerNote: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val timestampHint: String? = null,
    val source: String = "Demo report"
)

@Entity(tableName = "content_reports")
data class ContentReportEntity(
    @PrimaryKey val id: String,
    val titleId: String,
    val category: ContentCategory,
    val severity: Severity,
    val explanation: String,
    val spoilerNote: String?,
    val season: Int?,
    val episode: Int?,
    val createdAtMillis: Long,
    val moderationStatus: String = "local_only"
)

@Entity(tableName = "remote_content_reports", indices = [Index("remoteKey")])
data class RemoteContentReportEntity(
    @PrimaryKey val id: String,
    val remoteKey: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val releaseYear: Int?,
    val category: ContentCategory,
    val severity: Severity,
    val explanation: String,
    val spoilerNote: String?,
    val season: Int?,
    val episode: Int?,
    val createdAtMillis: Long,
    @ColumnInfo(defaultValue = "'Local user report'") val source: String = "Local user report",
    @ColumnInfo(defaultValue = "'local_only'") val moderationStatus: String = "local_only"
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String
)

@Entity(tableName = "profile_sensitivities", primaryKeys = ["profileId", "category"])
data class ProfileSensitivityEntity(
    val profileId: String,
    val category: ContentCategory,
    val sensitivity: Sensitivity
)

@Entity(tableName = "watchlist_items")
data class WatchlistItemEntity(
    @PrimaryKey val titleId: String,
    val addedAtMillis: Long
)

@Entity(tableName = "streaming_services")
data class StreamingServiceEntity(
    @PrimaryKey val providerId: Int,
    val providerName: String,
    val logoPath: String?,
    val enabled: Boolean = false,
    val region: String = "US",
    val integrationKind: String = "Manual selection",
    val accountConnected: Boolean = false,
    val watchHistoryConnected: Boolean = false,
    val plexServerConnected: Boolean = false,
    val selectedAccessTypes: String = "Subscription,Free,Ads"
) {
    fun accessTypes(): Set<WatchAccessType> = selectedAccessTypes
        .split(',')
        .mapNotNull { raw -> runCatching { WatchAccessType.valueOf(raw.trim()) }.getOrNull() }
        .toSet()
}

class ContentLensConverters {
    @TypeConverter fun categoryToString(value: ContentCategory): String = value.name
    @TypeConverter fun stringToCategory(value: String): ContentCategory = ContentCategory.valueOf(value)
    @TypeConverter fun severityToString(value: Severity): String = value.name
    @TypeConverter fun stringToSeverity(value: String): Severity = Severity.valueOf(value)
    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.name
    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
    @TypeConverter fun sensitivityToString(value: Sensitivity): String = value.name
    @TypeConverter fun stringToSensitivity(value: String): Sensitivity = Sensitivity.valueOf(value)
}
