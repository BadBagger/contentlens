package com.smithware.contentlens.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smithware.contentlens.data.AppSettings
import com.smithware.contentlens.data.ContentLensDatabase
import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.ContentReportEntity
import com.smithware.contentlens.data.LocalContentReportRepository
import com.smithware.contentlens.data.MediaTitleEntity
import com.smithware.contentlens.data.ProfileSensitivityEntity
import com.smithware.contentlens.data.SettingsStore
import com.smithware.contentlens.data.UserProfileEntity
import com.smithware.contentlens.data.WatchlistItemEntity
import com.smithware.contentlens.data.tmdb.ImageUrlBuilder
import com.smithware.contentlens.data.tmdb.NormalizedMediaResult
import com.smithware.contentlens.data.tmdb.TmdbClient
import com.smithware.contentlens.data.tmdb.TmdbImageConfiguration
import com.smithware.contentlens.data.tmdb.TmdbSearchError
import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.FitLabel
import com.smithware.contentlens.domain.LensRating
import com.smithware.contentlens.domain.LocalPersonalFitEngine
import com.smithware.contentlens.domain.LocalRatingEngine
import com.smithware.contentlens.domain.RatingSummary
import com.smithware.contentlens.domain.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.UUID

data class TitleLens(
    val title: MediaTitleEntity,
    val entries: List<ContentRatingEntryEntity> = emptyList(),
    val summary: RatingSummary = RatingSummary(LensRating.InsufficientData, emptyList(), 0),
    val fit: FitLabel = FitLabel.InsufficientData,
    val isWatchlisted: Boolean = false
)

data class AppUiState(
    val titles: List<MediaTitleEntity> = emptyList(),
    val selectedTitleId: String? = null,
    val selectedTitle: TitleLens? = null,
    val profiles: List<UserProfileEntity> = emptyList(),
    val sensitivities: List<ProfileSensitivityEntity> = emptyList(),
    val watchlist: List<WatchlistItemEntity> = emptyList(),
    val reports: List<ContentReportEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val remoteSearch: RemoteSearchUiState = RemoteSearchUiState.Initial
)

sealed class RemoteSearchUiState {
    data object Initial : RemoteSearchUiState()
    data class Waiting(val query: String) : RemoteSearchUiState()
    data class Loading(val query: String) : RemoteSearchUiState()
    data class Results(
        val query: String,
        val results: List<NormalizedMediaResult>,
        val imageUrlBuilder: ImageUrlBuilder,
        val page: Int,
        val totalPages: Int,
        val totalResults: Int,
        val isLoadingMore: Boolean = false
    ) : RemoteSearchUiState() {
        val hasMore: Boolean get() = page < totalPages
    }
    data class NoResults(val query: String) : RemoteSearchUiState()
    data class Offline(val query: String) : RemoteSearchUiState()
    data class ConfigurationError(val query: String, val message: String) : RemoteSearchUiState()
    data class ServerError(val query: String, val message: String) : RemoteSearchUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContentLensViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ContentLensDatabase.get(application).dao()
    private val settingsStore = SettingsStore(application)
    private val reportRepository = LocalContentReportRepository(dao)
    private val ratingEngine = LocalRatingEngine()
    private val fitEngine = LocalPersonalFitEngine()
    private val tmdbClient = TmdbClient()

    private val selectedTitleId = MutableStateFlow<String?>(null)
    private val selectedProfileId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val searchRetryNonce = MutableStateFlow(0)
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val remoteSearch = combine(query, searchRetryNonce) { search, _ -> search }
                .map { it.trim() }
                .flatMapLatest { trimmed ->
                    when {
                        trimmed.isBlank() -> flowOf(RemoteSearchUiState.Initial)
                        trimmed.length < 2 -> flowOf(RemoteSearchUiState.Waiting(trimmed))
                        else -> flow {
                            emit(RemoteSearchUiState.Waiting(trimmed))
                            kotlinx.coroutines.delay(400)
                            emit(RemoteSearchUiState.Loading(trimmed))
                            val (page, config) = tmdbClient.searchAll(trimmed, page = 1)
                            if (page.results.isEmpty()) {
                                emit(RemoteSearchUiState.NoResults(trimmed))
                            } else {
                                emit(
                                    RemoteSearchUiState.Results(
                                        query = trimmed,
                                        results = page.results,
                                        imageUrlBuilder = ImageUrlBuilder(config),
                                        page = page.page,
                                        totalPages = page.totalPages,
                                        totalResults = page.totalResults
                                    )
                                )
                            }
                        }.catch { error ->
                            emit(error.toSearchUiState(trimmed))
                        }
                    }
                }
            val storedBase = combine(
                dao.observeTitles(),
                dao.observeProfiles(),
                dao.observeWatchlistItems(),
                dao.observeReports(),
                settingsStore.settings
            ) { titles, profiles, watchlist, reports, settings ->
                StoredBase(titles, profiles, watchlist, reports, settings)
            }
            val storedState = combine(storedBase, remoteSearch) { stored, searchState ->
                StoredState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    watchlist = stored.watchlist,
                    reports = stored.reports,
                    settings = stored.settings,
                    remoteSearch = searchState
                )
            }
            val controls = combine(selectedTitleId, selectedProfileId, query) { titleId, profileId, search ->
                ControlState(titleId, profileId, search)
            }
            combine(storedState, controls) { stored, control ->
                val activeProfileId = control.selectedProfileId ?: stored.settings.defaultProfileId ?: stored.profiles.firstOrNull()?.id
                BaseState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    watchlist = stored.watchlist,
                    reports = stored.reports,
                    settings = stored.settings,
                    selectedTitleId = control.selectedTitleId ?: stored.titles.firstOrNull()?.id,
                    activeProfileId = activeProfileId,
                    query = control.query,
                    remoteSearch = stored.remoteSearch
                )
            }.flatMapLatest { base ->
                val titleId = base.selectedTitleId
                val entriesFlow = titleId?.let { dao.observeEntries(it) } ?: flowOf(emptyList())
                val sensitivityFlow = base.activeProfileId?.let { dao.observeSensitivities(it) } ?: flowOf(emptyList())
                combine(entriesFlow, sensitivityFlow) { entries, sensitivities ->
                    val selected = base.titles.firstOrNull { it.id == titleId }
                    val titleLens = selected?.let {
                        TitleLens(
                            title = it,
                            entries = entries,
                            summary = ratingEngine.summarize(entries),
                            fit = fitEngine.evaluate(entries, sensitivities),
                            isWatchlisted = base.watchlist.any { item -> item.titleId == it.id }
                        )
                    }
                    AppUiState(
                        titles = base.titles,
                        selectedTitleId = titleId,
                        selectedTitle = titleLens,
                        profiles = base.profiles,
                        sensitivities = sensitivities,
                        watchlist = base.watchlist,
                        reports = base.reports,
                        settings = base.settings,
                        query = base.query,
                        remoteSearch = base.remoteSearch
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    fun selectTitle(id: String) {
        selectedTitleId.value = id
    }

    fun selectProfile(id: String) {
        selectedProfileId.value = id
        viewModelScope.launch { settingsStore.setDefaultProfile(id) }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun retrySearch() {
        searchRetryNonce.value = searchRetryNonce.value + 1
    }

    fun loadMoreRemoteResults() {
        val current = uiState.value.remoteSearch as? RemoteSearchUiState.Results ?: return
        if (!current.hasMore || current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = uiState.value.copy(remoteSearch = current.copy(isLoadingMore = true))
            try {
                val (page, config) = tmdbClient.searchAll(current.query, page = current.page + 1)
                val merged = (current.results + page.results).distinctBy { it.mediaType to it.tmdbId }
                _uiState.value = uiState.value.copy(
                    remoteSearch = current.copy(
                        results = merged,
                        imageUrlBuilder = ImageUrlBuilder(config),
                        page = page.page,
                        totalPages = page.totalPages,
                        totalResults = page.totalResults,
                        isLoadingMore = false
                    )
                )
            } catch (error: Throwable) {
                _uiState.value = uiState.value.copy(remoteSearch = error.toSearchUiState(current.query))
            }
        }
    }

    fun toggleWatchlist(titleId: String) {
        viewModelScope.launch {
            val listed = uiState.value.watchlist.any { it.titleId == titleId }
            if (listed) dao.removeWatchlistItem(titleId) else dao.upsertWatchlistItem(WatchlistItemEntity(titleId, System.currentTimeMillis()))
        }
    }

    fun submitReport(
        titleId: String,
        category: ContentCategory,
        severity: Severity,
        explanation: String,
        spoilerNote: String?,
        season: Int?,
        episode: Int?
    ) {
        if (explanation.isBlank()) return
        viewModelScope.launch {
            reportRepository.submitReport(
                ContentReportEntity(
                    id = UUID.randomUUID().toString(),
                    titleId = titleId,
                    category = category,
                    severity = severity,
                    explanation = explanation.trim(),
                    spoilerNote = spoilerNote?.trim()?.ifBlank { null },
                    season = season,
                    episode = episode,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun setSpoilerFreeMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSpoilerFreeMode(enabled) }
    }

    fun setShowOfficialRatings(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowOfficialRatings(enabled) }
    }

    fun setShowUserReports(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowUserReports(enabled) }
    }

    private data class BaseState(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val settings: AppSettings,
        val selectedTitleId: String?,
        val activeProfileId: String?,
        val query: String,
        val remoteSearch: RemoteSearchUiState
    )

    private data class StoredState(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val settings: AppSettings,
        val remoteSearch: RemoteSearchUiState
    )

    private data class StoredBase(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val settings: AppSettings
    )

    private data class ControlState(
        val selectedTitleId: String?,
        val selectedProfileId: String?,
        val query: String
    )
}

private fun Throwable.toSearchUiState(query: String): RemoteSearchUiState = when (this) {
    is TmdbSearchError.MissingToken -> RemoteSearchUiState.ConfigurationError(
        query,
        "TMDB is not configured. Add tmdbReadAccessToken to local.properties or set TMDB_READ_ACCESS_TOKEN."
    )
    is TmdbSearchError.Authentication -> RemoteSearchUiState.ConfigurationError(
        query,
        "TMDB rejected the configured token. Check the read access token."
    )
    is TmdbSearchError.Offline -> RemoteSearchUiState.Offline(query)
    is TmdbSearchError.Server -> RemoteSearchUiState.ServerError(query, "TMDB returned HTTP $statusCode. Try again.")
    is TmdbSearchError.Parsing -> RemoteSearchUiState.ServerError(query, "TMDB returned data this build could not read.")
    else -> RemoteSearchUiState.ServerError(query, message ?: "Search failed. Try again.")
}

class ContentLensViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContentLensViewModel(application) as T
    }
}
