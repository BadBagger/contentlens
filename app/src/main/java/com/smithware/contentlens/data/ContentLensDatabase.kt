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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("SELECT * FROM remote_content_reports ORDER BY createdAtMillis DESC")
    fun observeRemoteReports(): Flow<List<RemoteContentReportEntity>>

    @Query("SELECT * FROM remote_content_reports WHERE remoteKey = :remoteKey ORDER BY severity DESC, category")
    suspend fun getRemoteReports(remoteKey: String): List<RemoteContentReportEntity>

    @Query("SELECT * FROM user_profiles ORDER BY name")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM profile_sensitivities WHERE profileId = :profileId")
    fun observeSensitivities(profileId: String): Flow<List<ProfileSensitivityEntity>>

    @Query("SELECT * FROM profile_sensitivities")
    fun observeAllSensitivities(): Flow<List<ProfileSensitivityEntity>>

    @Query("SELECT * FROM profile_sensitivities WHERE profileId = :profileId")
    suspend fun getSensitivities(profileId: String): List<ProfileSensitivityEntity>

    @Query("SELECT * FROM watchlist_items ORDER BY addedAtMillis DESC")
    fun observeWatchlistItems(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM streaming_services ORDER BY providerName")
    fun observeStreamingServices(): Flow<List<StreamingServiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitles(items: List<MediaTitleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(items: List<ContentRatingEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(items: List<UserProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSensitivities(items: List<ProfileSensitivityEntity>)

    @Query("DELETE FROM profile_sensitivities WHERE profileId = :profileId")
    suspend fun deleteSensitivitiesForProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(item: ContentReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteReport(item: RemoteContentReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlistItem(item: WatchlistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreamingServices(items: List<StreamingServiceEntity>)

    @Query("UPDATE streaming_services SET enabled = :enabled WHERE providerId = :providerId")
    suspend fun setStreamingServiceEnabled(providerId: Int, enabled: Boolean)

    @Query("DELETE FROM watchlist_items WHERE titleId = :titleId")
    suspend fun removeWatchlistItem(titleId: String)

    @Query("SELECT COUNT(*) FROM media_titles")
    suspend fun titleCount(): Int

    @Query("SELECT COUNT(*) FROM streaming_services")
    suspend fun streamingServiceCount(): Int
}

@Database(
    entities = [
        MediaTitleEntity::class,
        ContentRatingEntryEntity::class,
        ContentReportEntity::class,
        RemoteContentReportEntity::class,
        UserProfileEntity::class,
        ProfileSensitivityEntity::class,
        WatchlistItemEntity::class,
        StreamingServiceEntity::class
    ],
    version = 3,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS remote_content_reports (
                        id TEXT NOT NULL PRIMARY KEY,
                        remoteKey TEXT NOT NULL,
                        tmdbId INTEGER NOT NULL,
                        mediaType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        releaseYear INTEGER,
                        category TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        explanation TEXT NOT NULL,
                        spoilerNote TEXT,
                        season INTEGER,
                        episode INTEGER,
                        createdAtMillis INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'Local user report',
                        moderationStatus TEXT NOT NULL DEFAULT 'local_only'
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_content_reports_remoteKey ON remote_content_reports(remoteKey)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS streaming_services (
                        providerId INTEGER NOT NULL PRIMARY KEY,
                        providerName TEXT NOT NULL,
                        logoPath TEXT,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        region TEXT NOT NULL DEFAULT 'US',
                        integrationKind TEXT NOT NULL DEFAULT 'Manual selection',
                        accountConnected INTEGER NOT NULL DEFAULT 0,
                        watchHistoryConnected INTEGER NOT NULL DEFAULT 0,
                        plexServerConnected INTEGER NOT NULL DEFAULT 0,
                        selectedAccessTypes TEXT NOT NULL DEFAULT 'Subscription,Free,Ads'
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
