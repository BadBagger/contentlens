package com.smithware.contentlens.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentLensDao {
    @Query("SELECT * FROM media_titles ORDER BY title")
    fun observeTitles(): Flow<List<MediaTitleEntity>>

    @Query("SELECT * FROM media_titles WHERE title LIKE '%' || :query || '%' ORDER BY title")
    suspend fun searchTitles(query: String): List<MediaTitleEntity>

    @Query("SELECT * FROM media_titles WHERE id = :id")
    suspend fun getTitle(id: String): MediaTitleEntity?

    @Query("SELECT * FROM content_rating_entries WHERE titleId = :titleId ORDER BY category")
    fun observeEntries(titleId: String): Flow<List<ContentRatingEntryEntity>>

    @Query("SELECT * FROM content_rating_entries WHERE titleId = :titleId ORDER BY category")
    suspend fun getEntries(titleId: String): List<ContentRatingEntryEntity>

    @Query("SELECT * FROM content_reports ORDER BY createdAtMillis DESC")
    fun observeReports(): Flow<List<ContentReportEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY name")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM profile_sensitivities WHERE profileId = :profileId")
    fun observeSensitivities(profileId: String): Flow<List<ProfileSensitivityEntity>>

    @Query("SELECT * FROM profile_sensitivities WHERE profileId = :profileId")
    suspend fun getSensitivities(profileId: String): List<ProfileSensitivityEntity>

    @Query("SELECT * FROM watchlist_items ORDER BY addedAtMillis DESC")
    fun observeWatchlistItems(): Flow<List<WatchlistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitles(items: List<MediaTitleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(items: List<ContentRatingEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(items: List<UserProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSensitivities(items: List<ProfileSensitivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(item: ContentReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlistItem(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist_items WHERE titleId = :titleId")
    suspend fun removeWatchlistItem(titleId: String)

    @Query("SELECT COUNT(*) FROM media_titles")
    suspend fun titleCount(): Int
}

@Database(
    entities = [
        MediaTitleEntity::class,
        ContentRatingEntryEntity::class,
        ContentReportEntity::class,
        UserProfileEntity::class,
        ProfileSensitivityEntity::class,
        WatchlistItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(ContentLensConverters::class)
abstract class ContentLensDatabase : RoomDatabase() {
    abstract fun dao(): ContentLensDao

    companion object {
        @Volatile private var instance: ContentLensDatabase? = null

        fun get(context: Context): ContentLensDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ContentLensDatabase::class.java,
                    "contentlens.db"
                ).build().also { instance = it }
            }
    }
}
