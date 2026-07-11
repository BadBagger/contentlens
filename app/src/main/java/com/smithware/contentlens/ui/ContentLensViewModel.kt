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
import com.smithware.contentlens.data.RemoteContentReportEntity
import com.smithware.contentlens.data.SettingsStore
import com.smithware.contentlens.data.safety.ConfiguredContentSafetySource
import com.smithware.contentlens.data.safety.DoesTheDogDieError
import com.smithware.contentlens.data.safety.ExternalSafetyState
import com.smithware.contentlens.data.safety.ProxySafetyError
import com.smithware.contentlens.data.UserProfileEntity
import com.smithware.contentlens.data.WatchlistItemEntity
import com.smithware.contentlens.data.tmdb.ImageUrlBuilder
import com.smithware.contentlens.data.tmdb.NormalizedMediaResult
import com.smithware.contentlens.data.tmdb.TmdbClient
import com.smithware.contentlens.data.tmdb.TmdbImageConfiguration
import com.smithware.contentlens.data.tmdb.TmdbSearchError
import com.smithware.contentlens.data.tmdb.TmdbTitleDetails
import com.smithware.contentlens.data.tmdb.remoteMediaKey
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
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
    val remoteReports: List<RemoteContentReportEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val remoteSearch: RemoteSearchUiState = RemoteSearchUiState.Initial,
    val remoteSafety: Map<String, ExternalSafetyState> = emptyMap(),
    val discoverySections: List<DiscoverySectionState> = emptyList(),
    val remoteDetail: RemoteDetailUiState = RemoteDetailUiState.None
)

data class DiscoverySectionState(
    val key: String,
    val title: String,
    val subtitle: String,
    val results: List<NormalizedMediaResult> = emptyList(),
    val imageUrlBuilder: ImageUrlBuilder? = null,
    val loading: Boolean = true,
    val error: String? = null
)

private data class DiscoveryPreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val seeds: List<String>
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

sealed class RemoteDetailUiState {
    data object None : RemoteDetailUiState()
    data class Loading(val result: NormalizedMediaResult) : RemoteDetailUiState()
    data class Loaded(
        val details: TmdbTitleDetails,
        val imageUrlBuilder: ImageUrlBuilder,
        val reports: List<RemoteContentReportEntity> = emptyList(),
        val externalSafety: ExternalSafetyState = ExternalSafetyState.NotConfigured,
        val summary: RatingSummary = RatingSummary(LensRating.InsufficientData, emptyList(), 0),
        val fit: FitLabel = FitLabel.InsufficientData
    ) : RemoteDetailUiState()
    data class Error(val result: NormalizedMediaResult, val message: String) : RemoteDetailUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContentLensViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ContentLensDatabase.get(application).dao()
    private val settingsStore = SettingsStore(application)
    private val reportRepository = LocalContentReportRepository(dao)
    private val ratingEngine = LocalRatingEngine()
    private val fitEngine = LocalPersonalFitEngine()
    private val tmdbClient = TmdbClient()
    private val safetySource = ConfiguredContentSafetySource()

    private val selectedTitleId = MutableStateFlow<String?>(null)
    private val selectedProfileId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val remoteSearch = MutableStateFlow<RemoteSearchUiState>(RemoteSearchUiState.Initial)
    private val remoteSafety = MutableStateFlow<Map<String, ExternalSafetyState>>(emptyMap())
    private val discoverySections = MutableStateFlow(defaultDiscoverySections())
    private val remoteDetail = MutableStateFlow<RemoteDetailUiState>(RemoteDetailUiState.None)
    private var remoteSearchJob: Job? = null
    private var remoteDetailJob: Job? = null
    private val remoteSafetyJobs = mutableMapOf<String, Job>()
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        loadDiscoveryPresets()
        viewModelScope.launch {
            val reportBase = combine(dao.observeReports(), dao.observeRemoteReports()) { reports, remoteReports ->
                ReportBase(reports, remoteReports)
            }
            val storedBase = combine(
                dao.observeTitles(),
                dao.observeProfiles(),
                dao.observeWatchlistItems(),
                reportBase,
                settingsStore.settings
            ) { titles, profiles, watchlist, reports, settings ->
                StoredBase(titles, profiles, watchlist, reports.reports, reports.remoteReports, settings)
            }
            val storedState = combine(storedBase, remoteSearch, remoteSafety, discoverySections) { stored, searchState, safetyState, discovery ->
                StoredState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    watchlist = stored.watchlist,
                    reports = stored.reports,
                    remoteReports = stored.remoteReports,
                    settings = stored.settings,
                    remoteSearch = searchState,
                    remoteSafety = safetyState,
                    discoverySections = discovery
                )
            }
            val appStoredState = combine(storedState, remoteDetail) { stored, detail ->
                AppStoredState(stored, detail)
            }
            val controls = combine(selectedTitleId, selectedProfileId, query) { titleId, profileId, search ->
                ControlState(titleId, profileId, search)
            }
            combine(appStoredState, controls) { storedWithDetail, control ->
                val stored = storedWithDetail.stored
                val activeProfileId = control.selectedProfileId ?: stored.settings.defaultProfileId ?: stored.profiles.firstOrNull()?.id
                BaseState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    watchlist = stored.watchlist,
                    reports = stored.reports,
                    remoteReports = stored.remoteReports,
                    settings = stored.settings,
                    selectedTitleId = control.selectedTitleId,
                    activeProfileId = activeProfileId,
                    query = control.query,
                    remoteSearch = stored.remoteSearch,
                    remoteSafety = stored.remoteSafety,
                    discoverySections = stored.discoverySections,
                    remoteDetail = storedWithDetail.remoteDetail
                )
            }.flatMapLatest { base ->
                val titleId = base.selectedTitleId
                val entriesFlow = titleId?.let { dao.observeEntries(it) } ?: flowOf(emptyList())
                val sensitivityFlow = base.activeProfileId?.let { dao.observeSensitivities(it) } ?: flowOf(emptyList())
                combine(entriesFlow, sensitivityFlow) { entries, sensitivities ->
                    val selected = base.titles.firstOrNull { it.id == titleId }
                    val remoteDetail = (base.remoteDetail as? RemoteDetailUiState.Loaded)?.let { loaded ->
                        val entries = loaded.reports.map { it.toEntry() }
                        val externalEntries = (loaded.externalSafety as? ExternalSafetyState.Loaded)
                            ?.report
                            ?.toRatingEntries(loaded.details.remoteKey())
                            .orEmpty()
                        val allEntries = entries + externalEntries
                        loaded.copy(
                            summary = ratingEngine.summarize(allEntries),
                            fit = fitEngine.evaluate(allEntries, sensitivities)
                        )
                    } ?: base.remoteDetail
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
                        remoteReports = base.remoteReports,
                        settings = base.settings,
                        query = base.query,
                        remoteSearch = base.remoteSearch,
                        remoteSafety = base.remoteSafety,
                        discoverySections = base.discoverySections,
                        remoteDetail = remoteDetail
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    fun selectTitle(id: String) {
        selectedTitleId.value = id
        remoteDetail.value = RemoteDetailUiState.None
    }

    fun selectRemoteResult(result: NormalizedMediaResult) {
        selectedTitleId.value = null
        remoteDetailJob?.cancel()
        remoteDetailJob = viewModelScope.launch {
            remoteDetail.value = RemoteDetailUiState.Loading(result)
            try {
                val (details, config) = tmdbClient.details(result)
                val reports = dao.getRemoteReports(remoteMediaKey(result.tmdbId, result.mediaType))
                val localEntries = reports.map { it.toEntry() }
                val safetyKey = remoteMediaKey(result.tmdbId, result.mediaType)
                val cachedSafety = remoteSafety.value[safetyKey]
                val loaded = RemoteDetailUiState.Loaded(
                    details = details,
                    imageUrlBuilder = ImageUrlBuilder(config),
                    reports = reports,
                    externalSafety = cachedSafety ?: ExternalSafetyState.Loading,
                    summary = ratingEngine.summarize(localEntries),
                    fit = fitEngine.evaluate(localEntries, uiState.value.sensitivities)
                )
                remoteDetail.value = loaded
                if (cachedSafety !is ExternalSafetyState.Loaded && cachedSafety !is ExternalSafetyState.NoMatch) {
                    loadExternalSafety(loaded)
                }
            } catch (error: Throwable) {
                remoteDetail.value = RemoteDetailUiState.Error(result, error.toSearchUiState(result.title).let {
                    when (it) {
                        is RemoteSearchUiState.ConfigurationError -> it.message
                        is RemoteSearchUiState.ServerError -> it.message
                        is RemoteSearchUiState.Offline -> "ContentLens could not reach TMDB."
                        else -> "Title details could not be loaded."
                    }
                })
            }
        }
    }

    fun clearRemoteDetail() {
        remoteDetailJob?.cancel()
        remoteDetail.value = RemoteDetailUiState.None
    }

    fun selectProfile(id: String) {
        selectedProfileId.value = id
        viewModelScope.launch { settingsStore.setDefaultProfile(id) }
    }

    fun updateQuery(value: String) {
        query.value = value
        startRemoteSearch(value)
    }

    fun retrySearch() {
        startRemoteSearch(query.value, skipDebounce = true)
    }

    fun searchPreset(section: DiscoverySectionState) {
        query.value = section.title
        val builder = section.imageUrlBuilder
        if (builder != null && section.results.isNotEmpty()) {
            remoteSearch.value = RemoteSearchUiState.Results(
                query = section.title,
                results = section.results,
                imageUrlBuilder = builder,
                page = 1,
                totalPages = 1,
                totalResults = section.results.size
            )
            prefetchSearchSafety(section.results)
        } else {
            startRemoteSearch(section.title, skipDebounce = true)
        }
    }

    fun loadMoreRemoteResults() {
        val current = remoteSearch.value as? RemoteSearchUiState.Results ?: return
        if (!current.hasMore || current.isLoadingMore) return
        viewModelScope.launch {
            remoteSearch.value = current.copy(isLoadingMore = true)
            try {
                val (page, config) = tmdbClient.searchAll(current.query, page = current.page + 1)
                val merged = (current.results + page.results).distinctBy { it.mediaType to it.tmdbId }
                remoteSearch.value = current.copy(
                    results = merged,
                    imageUrlBuilder = ImageUrlBuilder(config),
                    page = page.page,
                    totalPages = page.totalPages,
                    totalResults = page.totalResults,
                    isLoadingMore = false
                )
                prefetchSearchSafety(page.results)
            } catch (error: Throwable) {
                remoteSearch.value = error.toSearchUiState(current.query)
            }
        }
    }

    private fun startRemoteSearch(rawQuery: String, skipDebounce: Boolean = false) {
        val trimmed = rawQuery.trim()
        remoteSearchJob?.cancel()
        remoteSearchJob = viewModelScope.launch {
            when {
                trimmed.isBlank() -> remoteSearch.value = RemoteSearchUiState.Initial
                trimmed.length < 2 -> remoteSearch.value = RemoteSearchUiState.Waiting(trimmed)
                else -> {
                    remoteSearch.value = RemoteSearchUiState.Waiting(trimmed)
                    if (!skipDebounce) delay(250)
                    remoteSearch.value = RemoteSearchUiState.Loading(trimmed)
                    try {
                        val (page, config) = tmdbClient.searchAll(trimmed, page = 1)
                        remoteSearch.value = if (page.results.isEmpty()) {
                            RemoteSearchUiState.NoResults(trimmed)
                        } else {
                            RemoteSearchUiState.Results(
                                query = trimmed,
                                results = page.results,
                                imageUrlBuilder = ImageUrlBuilder(config),
                                page = page.page,
                                totalPages = page.totalPages,
                                totalResults = page.totalResults
                            ).also { prefetchSearchSafety(page.results) }
                        }
                    } catch (error: Throwable) {
                        remoteSearch.value = error.toSearchUiState(trimmed)
                    }
                }
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

    fun submitRemoteReport(
        details: TmdbTitleDetails,
        category: ContentCategory,
        severity: Severity,
        explanation: String,
        spoilerNote: String?,
        season: Int?,
        episode: Int?
    ) {
        if (explanation.isBlank()) return
        viewModelScope.launch {
            val result = details.result
            val report = RemoteContentReportEntity(
                id = UUID.randomUUID().toString(),
                remoteKey = remoteMediaKey(result.tmdbId, result.mediaType),
                tmdbId = result.tmdbId,
                mediaType = result.mediaType.name,
                title = result.title,
                releaseYear = result.releaseYear,
                category = category,
                severity = severity,
                explanation = explanation.trim(),
                spoilerNote = spoilerNote?.trim()?.ifBlank { null },
                season = season,
                episode = episode,
                createdAtMillis = System.currentTimeMillis()
            )
            dao.upsertRemoteReport(report)
            val loaded = remoteDetail.value as? RemoteDetailUiState.Loaded
            if (loaded != null && loaded.details.result.tmdbId == result.tmdbId && loaded.details.result.mediaType == result.mediaType) {
                val reports = (listOf(report) + loaded.reports).distinctBy { it.id }
                val entries = reports.map { it.toEntry() } + (loaded.externalSafety as? ExternalSafetyState.Loaded)
                    ?.report
                    ?.toRatingEntries(loaded.details.remoteKey())
                    .orEmpty()
                remoteDetail.value = loaded.copy(
                    reports = reports,
                    summary = ratingEngine.summarize(entries),
                    fit = fitEngine.evaluate(entries, uiState.value.sensitivities)
                )
            }
        }
    }

    private suspend fun loadExternalSafety(loaded: RemoteDetailUiState.Loaded) {
        val state = try {
            val report = safetySource.reportFor(loaded.details)
            if (report == null) {
                ExternalSafetyState.NoMatch
            } else {
                ExternalSafetyState.Loaded(report)
            }
        } catch (error: Throwable) {
            error.toExternalSafetyState()
        }
        val current = remoteDetail.value as? RemoteDetailUiState.Loaded ?: return
        if (current.details.result.tmdbId != loaded.details.result.tmdbId || current.details.result.mediaType != loaded.details.result.mediaType) return
        val externalEntries = (state as? ExternalSafetyState.Loaded)?.report?.toRatingEntries(current.details.remoteKey()).orEmpty()
        val localEntries = current.reports.map { it.toEntry() }
        val allEntries = localEntries + externalEntries
        remoteDetail.value = current.copy(
            externalSafety = state,
            summary = ratingEngine.summarize(allEntries),
            fit = fitEngine.evaluate(allEntries, uiState.value.sensitivities)
        )
        remoteSafety.value = remoteSafety.value + (current.details.remoteKey() to state)
    }

    private fun prefetchSearchSafety(results: List<NormalizedMediaResult>) {
        results.take(3).forEach { result ->
            val key = remoteMediaKey(result.tmdbId, result.mediaType)
            if (remoteSafety.value[key] != null || remoteSafetyJobs[key]?.isActive == true) return@forEach
            remoteSafety.value = remoteSafety.value + (key to ExternalSafetyState.Loading)
            remoteSafetyJobs[key] = viewModelScope.launch {
                val state = try {
                    val report = safetySource.reportFor(result.toLightweightDetails())
                    if (report == null) ExternalSafetyState.NoMatch else ExternalSafetyState.Loaded(report)
                } catch (error: Throwable) {
                    error.toExternalSafetyState()
                }
                remoteSafety.value = remoteSafety.value + (key to state)
            }
        }
    }

    private fun loadDiscoveryPresets() {
        viewModelScope.launch {
            defaultDiscoveryPresets().forEach { preset ->
                launch {
                    try {
                        val seedResults = preset.seeds.map { seed ->
                            async { tmdbClient.searchAll(seed, page = 1) }
                        }.map { it.await() }
                        val config = seedResults.firstOrNull()?.second ?: tmdbClient.configuration()
                        val results = seedResults
                            .flatMap { it.first.results.take(2) }
                            .distinctBy { it.mediaType to it.tmdbId }
                            .sortedWith(compareByDescending<NormalizedMediaResult> { it.voteCount }.thenByDescending { it.voteAverage })
                            .take(8)
                        discoverySections.value = discoverySections.value.map {
                            if (it.key == preset.key) {
                                it.copy(results = results, imageUrlBuilder = ImageUrlBuilder(config), loading = false, error = null)
                            } else {
                                it
                            }
                        }
                        prefetchSearchSafety(results.take(3))
                    } catch (error: Throwable) {
                        discoverySections.value = discoverySections.value.map {
                            if (it.key == preset.key) it.copy(loading = false, error = "Could not load this shelf.") else it
                        }
                    }
                }
            }
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
        val remoteReports: List<RemoteContentReportEntity>,
        val settings: AppSettings,
        val selectedTitleId: String?,
        val activeProfileId: String?,
        val query: String,
        val remoteSearch: RemoteSearchUiState,
        val remoteSafety: Map<String, ExternalSafetyState>,
        val discoverySections: List<DiscoverySectionState>,
        val remoteDetail: RemoteDetailUiState
    )

    private data class StoredState(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val remoteReports: List<RemoteContentReportEntity>,
        val settings: AppSettings,
        val remoteSearch: RemoteSearchUiState,
        val remoteSafety: Map<String, ExternalSafetyState>,
        val discoverySections: List<DiscoverySectionState>
    )

    private data class AppStoredState(
        val stored: StoredState,
        val remoteDetail: RemoteDetailUiState
    )

    private data class ReportBase(
        val reports: List<ContentReportEntity>,
        val remoteReports: List<RemoteContentReportEntity>
    )

    private data class StoredBase(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val remoteReports: List<RemoteContentReportEntity>,
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
        "TMDB is not configured. Add tmdbApiKey or tmdbReadAccessToken to local.properties."
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

private fun Throwable.toExternalSafetyState(): ExternalSafetyState = when (this) {
    is DoesTheDogDieError.MissingApiKey -> ExternalSafetyState.NotConfigured
    is DoesTheDogDieError.Authentication -> ExternalSafetyState.Error("DoesTheDogDie rejected the configured API key.")
    is DoesTheDogDieError.UpgradeRequired -> ExternalSafetyState.UpgradeRequired("This DoesTheDogDie data requires a higher API tier.")
    is DoesTheDogDieError.RateLimited -> ExternalSafetyState.Error("DoesTheDogDie rate limit was reached. Try again later.")
    is DoesTheDogDieError.Offline -> ExternalSafetyState.Error("ContentLens could not reach DoesTheDogDie.")
    is DoesTheDogDieError.Parsing -> ExternalSafetyState.Error("DoesTheDogDie returned data this build could not read.")
    is DoesTheDogDieError.Server -> ExternalSafetyState.Error("DoesTheDogDie returned HTTP $statusCode.")
    is ProxySafetyError.MissingBaseUrl -> ExternalSafetyState.NotConfigured
    is ProxySafetyError.NoMatch -> ExternalSafetyState.NoMatch
    is ProxySafetyError.ProviderNotConfigured -> ExternalSafetyState.Error("ContentLens API is missing provider configuration.")
    is ProxySafetyError.RateLimited -> ExternalSafetyState.Error("ContentLens API rate limit was reached. Try again later.")
    is ProxySafetyError.Offline -> ExternalSafetyState.Error("ContentLens API could not be reached.")
    is ProxySafetyError.Parsing -> ExternalSafetyState.Error("ContentLens API returned data this build could not read.")
    is ProxySafetyError.Server -> ExternalSafetyState.Error("ContentLens API returned HTTP $statusCode.")
    else -> ExternalSafetyState.Error(message ?: "Content safety data could not be loaded.")
}

private fun TmdbTitleDetails.remoteKey(): String = remoteMediaKey(result.tmdbId, result.mediaType)

private fun NormalizedMediaResult.toLightweightDetails(): TmdbTitleDetails = TmdbTitleDetails(
    result = this,
    runtimeMinutes = null,
    episodeRuntimeMinutes = null,
    genres = emptyList(),
    status = null,
    numberOfSeasons = null,
    numberOfEpisodes = null,
    certification = null,
    cast = emptyList(),
    similar = emptyList(),
    watchProviders = emptyList()
)

private fun defaultDiscoverySections(): List<DiscoverySectionState> {
    return defaultDiscoveryPresets().map {
        DiscoverySectionState(key = it.key, title = it.title, subtitle = it.subtitle)
    }
}

private fun defaultDiscoveryPresets(): List<DiscoveryPreset> = listOf(
    DiscoveryPreset(
        key = "little-kids",
        title = "Best for kids under 3",
        subtitle = "Gentle preschool and toddler-friendly starting points.",
        seeds = listOf("Bluey", "Daniel Tiger", "Puffin Rock", "Winnie the Pooh")
    ),
    DiscoveryPreset(
        key = "family-night",
        title = "Family night",
        subtitle = "Broad, familiar picks for mixed-age viewing.",
        seeds = listOf("Moana", "The Lion King", "Paddington", "Toy Story")
    ),
    DiscoveryPreset(
        key = "low-intensity",
        title = "Low intensity",
        subtitle = "Calmer stories to check first when intensity matters.",
        seeds = listOf("Kiki's Delivery Service", "My Neighbor Totoro", "A Shaun the Sheep Movie", "The Peanuts Movie")
    ),
    DiscoveryPreset(
        key = "no-nudity-starting-points",
        title = "Review first: nudity concern",
        subtitle = "Popular picks to review when nudity is a hard concern.",
        seeds = listOf("Finding Nemo", "Inside Out", "The Incredibles", "Spider-Man Into the Spider-Verse")
    ),
    DiscoveryPreset(
        key = "short-watches",
        title = "Short watches",
        subtitle = "Easy options for limited time windows.",
        seeds = listOf("Wallace and Gromit", "Charlie Brown", "Shaun the Sheep", "Dug Days")
    ),
    DiscoveryPreset(
        key = "teen-adventure",
        title = "Teen adventure",
        subtitle = "Higher-energy titles worth checking against profile limits.",
        seeds = listOf("Harry Potter", "Spider-Man", "Percy Jackson", "Avatar The Last Airbender")
    )
)

class ContentLensViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContentLensViewModel(application) as T
    }
}

private fun RemoteContentReportEntity.toEntry(): ContentRatingEntryEntity {
    return ContentRatingEntryEntity(
        id = "remote-$id",
        titleId = remoteKey,
        category = category,
        severity = severity,
        explanation = explanation,
        spoilerNote = spoilerNote,
        season = season,
        episode = episode,
        source = source
    )
}
