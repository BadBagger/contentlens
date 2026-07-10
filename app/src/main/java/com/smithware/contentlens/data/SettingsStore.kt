package com.smithware.contentlens.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("contentlens_settings")

data class AppSettings(
    val defaultProfileId: String? = null,
    val spoilerFreeMode: Boolean = true,
    val showOfficialRatings: Boolean = true,
    val showUserReports: Boolean = true
)

class SettingsStore(private val context: Context) {
    private val defaultProfileKey = stringPreferencesKey("default_profile_id")
    private val spoilerFreeKey = booleanPreferencesKey("spoiler_free_mode")
    private val officialRatingsKey = booleanPreferencesKey("show_official_ratings")
    private val userReportsKey = booleanPreferencesKey("show_user_reports")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultProfileId = prefs[defaultProfileKey],
            spoilerFreeMode = prefs[spoilerFreeKey] ?: true,
            showOfficialRatings = prefs[officialRatingsKey] ?: true,
            showUserReports = prefs[userReportsKey] ?: true
        )
    }

    suspend fun setDefaultProfile(profileId: String) {
        context.dataStore.edit { it[defaultProfileKey] = profileId }
    }

    suspend fun setSpoilerFreeMode(enabled: Boolean) {
        context.dataStore.edit { it[spoilerFreeKey] = enabled }
    }

    suspend fun setShowOfficialRatings(enabled: Boolean) {
        context.dataStore.edit { it[officialRatingsKey] = enabled }
    }

    suspend fun setShowUserReports(enabled: Boolean) {
        context.dataStore.edit { it[userReportsKey] = enabled }
    }
}
