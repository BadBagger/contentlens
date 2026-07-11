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
import com.smithware.contentlens.data.safety.FeaturedFeedClient
import com.smithware.contentlens.data.safety.ProxySafetyError
import com.smithware.contentlens.data.UserProfileEntity
import com.smithware.contentlens.data.WatchlistItemEntity
import com.smithware.contentlens.data.tmdb.ImageUrlBuilder
import com.smithware.contentlens.data.tmdb.NormalizedMediaResult
import com.smithware.contentlens.data.tmdb.RemoteMediaType
import com.smithware.contentlens.data.tmdb.TmdbClient
import com.smithware.contentlens.data.tmdb.TmdbImageConfiguration
import com.smithware.contentlens.data.tmdb.TmdbSearchError
import com.smithware.contentlens.data.tmdb.TmdbTitleDetails
import com.smithware.contentlens.data.tmdb.remoteMediaKey
import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.BoundaryEvaluation
import com.smithware.contentlens.domain.BoundaryStatus
import com.smithware.contentlens.domain.FitLabel
import com.smithware.contentlens.domain.LensRating
import com.smithware.contentlens.domain.LocalPersonalFitEngine
import com.smithware.contentlens.domain.LocalRatingEngine
import com.smithware.contentlens.domain.RatingSummary
import com.smithware.contentlens.domain.Sensitivity
import com.smithware.contentlens.domain.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class TitleLens(
    val title: MediaTitleEntity,
    val entries: List<ContentRatingEntryEntity> = emptyList(),
    val summary: RatingSummary = RatingSummary(LensRating.InsufficientData, emptyList(), 0),
    val fit: FitLabel = FitLabel.InsufficientData,
    val boundary: BoundaryEvaluation = BoundaryEvaluation(true, BoundaryStatus.InsufficientInformation, 45),
    val isWatchlisted: Boolean = false
)

data class AppUiState(
    val titles: List<MediaTitleEntity> = emptyList(),
    val selectedTitleId: String? = null,
    val selectedTitle: TitleLens? = null,
    val profiles: List<UserProfileEntity> = emptyList(),
    val sensitivities: List<ProfileSensitivityEntity> = emptyList(),
    val allSensitivities: List<ProfileSensitivityEntity> = emptyList(),
    val activeProfileIds: Set<String> = emptySet(),
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
    val allResults: List<NormalizedMediaResult> = results,
    val imageUrlBuilder: ImageUrlBuilder? = null,
    val loading: Boolean = false,
    val error: String? = null
)

private data class DiscoveryPreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val seeds: List<DiscoverySeed>
)

private data class DiscoverySeed(
    val mediaType: RemoteMediaType,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double,
    val voteCount: Int,
    val originalLanguage: String = "en"
)

private fun DiscoverySeed.toResult(): NormalizedMediaResult = NormalizedMediaResult(
    tmdbId = tmdbId,
    mediaType = mediaType,
    title = title,
    originalTitle = title,
    overview = "",
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    releaseYear = releaseDate?.take(4)?.toIntOrNull(),
    genreIds = emptyList(),
    popularity = 0.0,
    voteAverage = voteAverage,
    voteCount = voteCount,
    adult = false,
    originalLanguage = originalLanguage
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
        val fit: FitLabel = FitLabel.InsufficientData,
        val boundary: BoundaryEvaluation = BoundaryEvaluation(true, BoundaryStatus.InsufficientInformation, 45)
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
    private val featuredFeedClient = FeaturedFeedClient(application.applicationContext)

    private val selectedTitleId = MutableStateFlow<String?>(null)
    private val selectedProfileIds = MutableStateFlow<Set<String>>(emptySet())
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
            val profileBase = combine(dao.observeProfiles(), dao.observeAllSensitivities()) { profiles, sensitivities ->
                ProfileBase(profiles, sensitivities)
            }
            val storedBase = combine(
                dao.observeTitles(),
                profileBase,
                dao.observeWatchlistItems(),
                reportBase,
                settingsStore.settings
            ) { titles, profileState, watchlist, reports, settings ->
                StoredBase(titles, profileState.profiles, profileState.sensitivities, watchlist, reports.reports, reports.remoteReports, settings)
            }
            val storedState = combine(storedBase, remoteSearch, remoteSafety, discoverySections) { stored, searchState, safetyState, discovery ->
                StoredState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    allSensitivities = stored.allSensitivities,
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
            val controls = combine(selectedTitleId, selectedProfileIds, query) { titleId, profileIds, search ->
                ControlState(titleId, profileIds, search)
            }
            combine(appStoredState, controls) { storedWithDetail, control ->
                val stored = storedWithDetail.stored
                val fallbackProfileId = stored.settings.defaultProfileId ?: stored.profiles.firstOrNull()?.id
                val activeProfileIds = control.selectedProfileIds.ifEmpty { fallbackProfileId?.let { setOf(it) } ?: emptySet() }
                BaseState(
                    titles = stored.titles,
                    profiles = stored.profiles,
                    allSensitivities = stored.allSensitivities,
                    watchlist = stored.watchlist,
                    reports = stored.reports,
                    remoteReports = stored.remoteReports,
                    settings = stored.settings,
                    selectedTitleId = control.selectedTitleId,
                    activeProfileIds = activeProfileIds,
                    query = control.query,
                    remoteSearch = stored.remoteSearch,
                    remoteSafety = stored.remoteSafety,
                    discoverySections = stored.discoverySections,
                    remoteDetail = storedWithDetail.remoteDetail
                )
            }.flatMapLatest { base ->
                val titleId = base.selectedTitleId
                val entriesFlow = titleId?.let { dao.observeEntries(it) } ?: flowOf(emptyList())
                entriesFlow.combine(flowOf(Unit)) { entries, _ ->
                    val activeProfiles = base.profiles.filter { it.id in base.activeProfileIds }
                    val sensitivities = base.allSensitivities.filter { it.profileId in base.activeProfileIds }
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
                            fit = fitEngine.evaluate(allEntries, sensitivities),
                            boundary = fitEngine.evaluateDetailed(allEntries, sensitivities, activeProfiles)
                        )
                    } ?: base.remoteDetail
                    val titleLens = selected?.let {
                        val boundary = fitEngine.evaluateDetailed(entries, sensitivities, activeProfiles)
                        TitleLens(
                            title = it,
                            entries = entries,
                            summary = ratingEngine.summarize(entries),
                            fit = fitEngine.evaluate(entries, sensitivities),
                            boundary = boundary,
                            isWatchlisted = base.watchlist.any { item -> item.titleId == it.id }
                        )
                    }
                    AppUiState(
                        titles = base.titles,
                        selectedTitleId = titleId,
                        selectedTitle = titleLens,
                        profiles = base.profiles,
                        sensitivities = sensitivities,
                        allSensitivities = base.allSensitivities,
                        activeProfileIds = base.activeProfileIds,
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
                    fit = fitEngine.evaluate(localEntries, uiState.value.sensitivities),
                    boundary = fitEngine.evaluateDetailed(localEntries, uiState.value.sensitivities, uiState.value.profiles.filter { it.id in uiState.value.activeProfileIds })
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
        selectedProfileIds.value = setOf(id)
        viewModelScope.launch { settingsStore.setDefaultProfile(id) }
    }

    fun toggleProfileForViewing(id: String) {
        val current = selectedProfileIds.value
        selectedProfileIds.value = if (id in current) {
            (current - id).ifEmpty { setOf(id) }
        } else {
            current + id
        }
    }

    fun updateProfileBoundary(profileId: String, category: ContentCategory, sensitivity: Sensitivity) {
        viewModelScope.launch {
            dao.upsertSensitivities(listOf(ProfileSensitivityEntity(profileId, category, sensitivity)))
        }
    }

    fun restorePreferenceBackup(json: String) {
        viewModelScope.launch {
            runCatching {
                val root = JSONObject(json)
                val profilesJson = root.optJSONArray("profiles") ?: return@runCatching
                val profiles = buildList {
                    for (index in 0 until profilesJson.length()) {
                        val item = profilesJson.optJSONObject(index) ?: continue
                        val id = item.optString("id").ifBlank { continue }
                        add(
                            UserProfileEntity(
                                id = id,
                                name = item.optString("name").ifBlank { id },
                                description = item.optString("description").ifBlank { "Restored local viewing profile." }
                            )
                        )
                    }
                }
                val sensitivities = buildList {
                    for (index in 0 until profilesJson.length()) {
                        val item = profilesJson.optJSONObject(index) ?: continue
                        val profileId = item.optString("id").ifBlank { continue }
                        val boundaries = item.optJSONArray("boundaries") ?: continue
                        for (boundaryIndex in 0 until boundaries.length()) {
                            val boundary = boundaries.optJSONObject(boundaryIndex) ?: continue
                            val category = runCatching { ContentCategory.valueOf(boundary.optString("category")) }.getOrNull() ?: continue
                            val sensitivity = runCatching { Sensitivity.valueOf(boundary.optString("sensitivity")) }.getOrNull() ?: continue
                            add(ProfileSensitivityEntity(profileId, category, sensitivity))
                        }
                    }
                }
                if (profiles.isNotEmpty()) {
                    dao.upsertProfiles(profiles)
                    profiles.forEach { dao.deleteSensitivitiesForProfile(it.id) }
                    dao.upsertSensitivities(sensitivities)
                    selectedProfileIds.value = profiles.map { it.id }.take(1).toSet()
                }
            }
        }
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
        val results = section.allResults.ifEmpty { section.results }
        if (builder != null && results.isNotEmpty()) {
            remoteSearch.value = RemoteSearchUiState.Results(
                query = section.title,
                results = results,
                imageUrlBuilder = builder,
                page = 1,
                totalPages = 1,
                totalResults = results.size
            )
            prefetchSearchSafety(results)
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
                    fit = fitEngine.evaluate(entries, uiState.value.sensitivities),
                    boundary = fitEngine.evaluateDetailed(entries, uiState.value.sensitivities, uiState.value.profiles.filter { it.id in uiState.value.activeProfileIds })
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
            fit = fitEngine.evaluate(allEntries, uiState.value.sensitivities),
            boundary = fitEngine.evaluateDetailed(allEntries, uiState.value.sensitivities, uiState.value.profiles.filter { it.id in uiState.value.activeProfileIds })
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
            val feedStates = featuredFeedClient.safetyStates()
            if (feedStates.isNotEmpty()) {
                remoteSafety.value = remoteSafety.value + feedStates
                return@launch
            }
            defaultDiscoveryPresets().flatMap { it.seeds }.map { it.toResult() }.take(8).forEach { result ->
                launch {
                    val state = try {
                        val report = safetySource.reportFor(result.toLightweightDetails())
                        if (report == null) ExternalSafetyState.NoMatch else ExternalSafetyState.Loaded(report)
                    } catch (error: Throwable) {
                        error.toExternalSafetyState()
                    }
                    remoteSafety.value = remoteSafety.value + (remoteMediaKey(result.tmdbId, result.mediaType) to state)
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
        val allSensitivities: List<ProfileSensitivityEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val remoteReports: List<RemoteContentReportEntity>,
        val settings: AppSettings,
        val selectedTitleId: String?,
        val activeProfileIds: Set<String>,
        val query: String,
        val remoteSearch: RemoteSearchUiState,
        val remoteSafety: Map<String, ExternalSafetyState>,
        val discoverySections: List<DiscoverySectionState>,
        val remoteDetail: RemoteDetailUiState
    )

    private data class StoredState(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val allSensitivities: List<ProfileSensitivityEntity>,
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

    private data class ProfileBase(
        val profiles: List<UserProfileEntity>,
        val sensitivities: List<ProfileSensitivityEntity>
    )

    private data class StoredBase(
        val titles: List<MediaTitleEntity>,
        val profiles: List<UserProfileEntity>,
        val allSensitivities: List<ProfileSensitivityEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val reports: List<ContentReportEntity>,
        val remoteReports: List<RemoteContentReportEntity>,
        val settings: AppSettings
    )

    private data class ControlState(
        val selectedTitleId: String?,
        val selectedProfileIds: Set<String>,
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
        DiscoverySectionState(
            key = it.key,
            title = it.title,
            subtitle = it.subtitle,
            results = it.seeds.take(4).map { seed -> seed.toResult() },
            allResults = it.seeds.map { seed -> seed.toResult() },
            imageUrlBuilder = ImageUrlBuilder(),
            loading = false
        )
    }
}

private fun defaultDiscoveryPresets(): List<DiscoveryPreset> = listOf(
    DiscoveryPreset(
        key = "little-kids",
        title = "Young children",
        subtitle = "Gentle preschool and toddler-friendly starting points.",
        seeds = listOf(
            tv(82728, "Bluey", "/b9mY0X5T20ZM073hoa5n0dgmbfN.jpg", "/g88VMPtog8sl8riaIRtz4U80dMK.jpg", "2018-10-01", 8.6, 711),
            tv(40050, "Daniel Tiger's Neighborhood", "/pUbHrEFTWegyhIxFmtpOpAQsbfT.jpg", "/yAIpWHcLmS1O2aCrx4oBhXtwoHt.jpg", "2012-09-03", 7.1, 33),
            tv(69926, "Puffin Rock", "/8ZYTNkpubnG8epqlJLjaWbbO30R.jpg", "/9z945wZwFzJ0EbzY8vsk5ELCZeJ.jpg", "2015-01-12", 7.6, 11),
            tv(2005, "The New Adventures of Winnie the Pooh", "/iJFbF43bN1HX5EZl4OFAVmJDl1u.jpg", "/pFRX1VUIm0j7a9VI1lQke9tOMZY.jpg", "1988-09-10", 7.7, 273),
            tv(12225, "Peppa Pig", "/iwKVo3HlsyVNXCzFEkd0xHz3kGi.jpg", "/fnyS6gOv9tTjhHPl4A1aKEDgWEG.jpg", "2004-05-31", 6.5, 751),
            tv(65047, "Tumble Leaf", "/8MbsSjulW9HZHfLecV2S2dyzk77.jpg", "/iGqDxkX7NY6gx0Ts4reV8zQOAm.jpg", "2013-04-19", 7.5, 6),
            tv(92885, "Blue's Clues & You!", "/wXgrjyQTjTYaaS0iNcKWXl4UgSK.jpg", "/ofNbWXNtRrPB1oe7w7ugSCKJyua.jpg", "2019-11-11", 7.0, 42),
            tv(15, "Mister Rogers' Neighborhood", "/qhbeRYVg120cBmc9XvGxvk6EmJF.jpg", "/bvbOrvK0hQveMB6SmQoAwIuRToh.jpg", "1968-02-19", 6.1, 123)
        )
    ),
    DiscoveryPreset(
        key = "preschool-favorites",
        title = "Preschool favorites",
        subtitle = "Bright, familiar shows for early learners.",
        seeds = listOf(
            tv(502, "Sesame Street", "/14k9BfZ2p4rQBMeJ5crKTfUZVwD.jpg", "/tNi2aJPdCKfielGYzIi3IKxStz9.jpg", "1969-11-10", 7.1, 286),
            tv(656, "Curious George", "/1FsihcjjTrzkmD9i8hRPzE6GhZf.jpg", "/vn9iFlwSvl8B9YE1qayrKWMxoEV.jpg", "2006-09-04", 7.2, 135),
            tv(37472, "Octonauts", "/iYUhUSKWKpSBahaWLUQIPckAy8p.jpg", "/lbdBBEnto6xPQ8oKHt5P9r3JCfH.jpg", "2010-10-05", 7.2, 57),
            tv(93548, "Molly of Denali", "/8HdIoG6HHJJaf8M3SAVQrGgCvHp.jpg", "/pvyxHVkUoLoT97ildtGKH2BHSGm.jpg", "2019-07-15", 7.0, 1),
            tv(79, "Dora the Explorer", "/iaJNOSpEQgU4yePsbcC6yxneRNF.jpg", "/uEPbIAmsZENHL5B50WNYSyItFjf.jpg", "2000-08-14", 6.3, 344),
            tv(14766, "Super Why!", "/lJttSVfKx56a2wN2pg2yR4IHzwy.jpg", "/U9VKLLo9IeQU1aQAOA6s4Na5Pc.jpg", "2007-09-03", 7.1, 19),
            tv(106009, "Numberblocks", "/vt872bR11jYDRSzTSfLuOmb2SMt.jpg", "/u9H3GJHNxlQwVeyJDxGGD7Uu9iH.jpg", "2017-01-23", 8.4, 24),
            tv(8379, "Clifford the Big Red Dog", "/tZLS8aT8Iqnbw8oMKIi1WHidCA1.jpg", "/4XKhVE10MOao7dieT4QP34vKk9K.jpg", "2000-09-04", 6.6, 88)
        )
    ),
    DiscoveryPreset(
        key = "early-elementary",
        title = "Early elementary",
        subtitle = "Friendly adventures for growing attention spans.",
        seeds = listOf(
            tv(35094, "Wild Kratts", "/7Nn1AZb6LJv0WedeK7o4yU6Aioh.jpg", "/lQ1WqzCSyjH3Zhe11776cnP7ESO.jpg", "2011-01-03", 7.5, 64),
            tv(7248, "The Magic School Bus", "/3A70wxUpplD4V3IcL8WmNFBgAbV.jpg", "/pWZjTb2ixwVEzvnsvv3KYz5pvIs.jpg", "1994-09-10", 7.7, 134),
            movie(227973, "The Peanuts Movie", "/aiwdwnl7RFs1vcBanOKr13ye3wE.jpg", "/361quVX94drEaExOKScbZYsxijG.jpg", "2015-11-05", 7.0, 1811),
            tv(3902, "Shaun the Sheep", "/z2gPfTQd3I0JLWOAEdKYMQRLoun.jpg", "/onwny8s0VTTN7te9TJnItgvyWHy.jpg", "2007-03-05", 7.6, 282),
            tv(4217, "Zoboomafoo", "/5JMgrHBaspcU9KIfC7nTfPFNL9x.jpg", "/OMZrF60GxJa1iUA78SwwRDjd0k.jpg", "1999-01-25", 6.8, 37),
            tv(2153, "Arthur", "/nYN8okmcsmhd4bGVcqifZ5OCumB.jpg", "/5k3JpB88vubqYVSHHiAhk2T8z17.jpg", "1996-10-07", 7.0, 186),
            tv(70437, "Ask the Storybots", "/6IY8bmJcUGmgcB9ERnx8hLw7Qz5.jpg", "/947dkEryFtsyO0pNlVRJV53ZbEb.jpg", "2016-08-12", 7.0, 13),
            movie(313297, "Kubo and the Two Strings", "/ewcOCkuuKAKULGUnbBVaO1htt0D.jpg", "/bhspYsRMHgMqzUlxiVpIY8OrqMt.jpg", "2016-08-18", 7.6, 3839)
        )
    ),
    DiscoveryPreset(
        key = "older-kids",
        title = "Older kids",
        subtitle = "Bigger stories to review for intensity and scares.",
        seeds = listOf(
            movie(10191, "How to Train Your Dragon", "/ygGmAO60t8GyqUo9xYeYxSZAR3b.jpg", "/59vDC1BuEQvti24OMr0ZvtAK6R1.jpg", "2010-03-18", 7.9, 14467),
            tv(82456, "Hilda", "/giWufGBC7uxrmK9ZESdk9D1cyGm.jpg", "/x5YFTfPDug6eF76vVuDnbaoNVQK.jpg", "2018-09-21", 8.4, 240),
            tv(246, "Avatar: The Last Airbender", "/9RQhVb3r3mCMqYVhLoCu4EvuipP.jpg", "/kU98MbVVgi72wzceyrEbClZmMFe.jpg", "2005-02-21", 8.8, 4943),
            movie(501929, "The Mitchells vs. the Machines", "/mI2Di7HmskQQ34kz0iau6J1vr70.jpg", "/vsZLf5uog08pAnfsMuDWrsLWUUF.jpg", "2021-04-22", 7.8, 3401),
            movie(313297, "Kubo and the Two Strings", "/ewcOCkuuKAKULGUnbBVaO1htt0D.jpg", "/bhspYsRMHgMqzUlxiVpIY8OrqMt.jpg", "2016-08-18", 7.6, 3839),
            movie(177572, "Big Hero 6", "/2mxS4wUimwlLmI1xp6QW6NSU361.jpg", "/4s2d3xdyqotiVNHTlTlJjrr3q0H.jpg", "2014-10-24", 7.7, 16676),
            movie(324857, "Spider-Man: Into the Spider-Verse", "/iiZZdoQBEYBv6id8su7ImL0oCbD.jpg", "/8mnXR9rey5uQ08rZAvzojKWbDQS.jpg", "2018-12-06", 8.4, 17395),
            movie(9806, "The Incredibles", "/2LqaLgk4Z226KkgPJuiOQ58wvrm.jpg", "/lxwzY9vNwjDgxWKt3zZ6zcU6rEJ.jpg", "2004-10-27", 7.7, 19012)
        )
    ),
    DiscoveryPreset(
        key = "family-night",
        title = "Family night",
        subtitle = "Broad, familiar picks for mixed-age viewing.",
        seeds = listOf(
            movie(277834, "Moana", "/9tzN8sPbyod2dsa0lwuvrwBDWra.jpg", "/iYLKMV7PIBtFmtygRrhSiyzcVsF.jpg", "2016-10-13", 7.6, 13810),
            movie(8587, "The Lion King", "/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg", "/q00H8EqULYSK74lgevMkhmGGLHn.jpg", "1994-06-15", 8.3, 19813),
            movie(116149, "Paddington", "/wpchRGhRhvhtU083PfX2yixXtiw.jpg", "/kfofK4GzJsJZhCShqZY438c8Y4Y.jpg", "2014-11-24", 7.1, 4273),
            movie(862, "Toy Story", "/uXDfjJbdP4ijW5hWSBrPrlKpxab.jpg", "/3Rfvhy1Nl6sSGJwyjb0QiZzZYlB.jpg", "1995-11-22", 8.0, 20132),
            movie(109445, "Frozen", "/itAKcobTYGpYT8Phwjd8c9hleTo.jpg", "/rj58WQ9ImI0mYDptXdM7euX1Wjt.jpg", "2013-11-20", 7.2, 17657),
            movie(12, "Finding Nemo", "/5lc6nQc0VhWFYFbNv016xze8Jvy.jpg", "/eCynaAOgYYiw5yN5lBwz3IxqvaW.jpg", "2003-05-30", 7.8, 20667),
            movie(354912, "Coco", "/6Ryitt95xrO8KXuqRGm1fUuNwqF.jpg", "/g7CHF8gTLGooTbP4GznIGwaqAGL.jpg", "2017-10-27", 8.2, 21111),
            movie(568124, "Encanto", "/4j0PNHkMr5ax3IA8tjtxcmPU3QT.jpg", "/3G1Q5xF40HkUBJXxt2DQgQzKTp5.jpg", "2021-10-13", 7.6, 10264)
        )
    ),
    DiscoveryPreset(
        key = "low-intensity",
        title = "Low intensity",
        subtitle = "Calmer stories to check first when intensity matters.",
        seeds = listOf(
            movie(16859, "Kiki's Delivery Service", "/Aufa4YdZIv4AXpR9rznwVA5SEfd.jpg", "/h5pAEVma835u8xoE60kmLVopLct.jpg", "1989-07-29", 7.8, 4620, "ja"),
            movie(8392, "My Neighbor Totoro", "/rtGDOeG9LzoerkDGZF9dnVeLppL.jpg", "/zkThiZAaAie8Lw7RAc5yPTOewBV.jpg", "1988-04-16", 8.1, 8897, "ja"),
            movie(263109, "Shaun the Sheep Movie", "/1GMvKNy2Ht5QwI0oV0ycYnxzWdC.jpg", "/sQbpfYoGiEBUnr8tBjHP51heNNT.jpg", "2015-02-05", 7.0, 1525),
            movie(227973, "The Peanuts Movie", "/aiwdwnl7RFs1vcBanOKr13ye3wE.jpg", "/361quVX94drEaExOKScbZYsxijG.jpg", "2015-11-05", 7.0, 1811),
            movie(12429, "Ponyo", "/yp8vEZflGynlEylxEesbYasc06i.jpg", "/shqLeIkqPAAXM8iT6wVDiXUYz1p.jpg", "2008-07-19", 7.8, 4864, "ja"),
            movie(116149, "Paddington", "/wpchRGhRhvhtU083PfX2yixXtiw.jpg", "/kfofK4GzJsJZhCShqZY438c8Y4Y.jpg", "2014-11-24", 7.1, 4273),
            movie(51162, "Winnie the Pooh", "/xlFs85nq62jeR4a9iHNGB113m6x.jpg", "/e0SlSnLQzIhdcr86akVJinA1Jau.jpg", "2011-04-06", 6.9, 972),
            movie(51739, "The Secret World of Arrietty", "/3lSRaSjDp2nkXMQkzzjpRi3035O.jpg", "/7Z7WVzJsSReG8B0CaPk0bvWD7tK.jpg", "2010-07-16", 7.7, 3200, "ja")
        )
    ),
    DiscoveryPreset(
        key = "no-nudity-starting-points",
        title = "Review first: nudity concern",
        subtitle = "Popular picks to review when nudity is a hard concern.",
        seeds = listOf(
            movie(12, "Finding Nemo", "/5lc6nQc0VhWFYFbNv016xze8Jvy.jpg", "/eCynaAOgYYiw5yN5lBwz3IxqvaW.jpg", "2003-05-30", 7.8, 20667),
            movie(150540, "Inside Out", "/2H1TmgdfNtsKlU9jKdeNyYL5y8T.jpg", "/o3i6AfTcWAuNvzAUV3q5lOmi6Gx.jpg", "2015-06-17", 7.9, 23585),
            movie(9806, "The Incredibles", "/2LqaLgk4Z226KkgPJuiOQ58wvrm.jpg", "/lxwzY9vNwjDgxWKt3zZ6zcU6rEJ.jpg", "2004-10-27", 7.7, 19012),
            movie(324857, "Spider-Man: Into the Spider-Verse", "/iiZZdoQBEYBv6id8su7ImL0oCbD.jpg", "/8mnXR9rey5uQ08rZAvzojKWbDQS.jpg", "2018-12-06", 8.4, 17395),
            movie(862, "Toy Story", "/uXDfjJbdP4ijW5hWSBrPrlKpxab.jpg", "/3Rfvhy1Nl6sSGJwyjb0QiZzZYlB.jpg", "1995-11-22", 8.0, 20132),
            movie(585, "Monsters, Inc.", "/wFSpyMsp7H0ttERbxY7Trlv8xry.jpg", "/sDTnMOJ3H5wI38OxObmCtK7wfd5.jpg", "2001-11-01", 7.9, 19924),
            movie(14160, "Up", "/mFvoEwSfLqbcWwFsDjQebn9bzFe.jpg", "/hGGC9gKo7CFE3fW07RA587e5kol.jpg", "2009-05-28", 8.0, 21797),
            movie(10681, "WALL-E", "/hbhFnRzzg6ZDmm8YAmxBnQpQIPh.jpg", "/nYs4ZwnJBK4AgljhvzwNz7fpr3E.jpg", "2008-06-26", 8.1, 20453)
        )
    ),
    DiscoveryPreset(
        key = "short-watches",
        title = "Short watches",
        subtitle = "Easy options for limited time windows.",
        seeds = listOf(
            tv(80616, "Wallace & Gromit's Cracking Contraptions", "/vpEk64myGeCCCmnG7r9EiBymfZt.jpg", "/snFImD0BfsFJSuTDuUKAc5u6t0O.jpg", "2002-10-15", 8.4, 16),
            movie(13187, "A Charlie Brown Christmas", "/vtaufTzJBMJAeziQA1eP4BLU24C.jpg", "/ucWnzjWWWd6CmRal8J3tWz6m1A7.jpg", "1965-12-09", 7.7, 786),
            tv(3902, "Shaun the Sheep", "/z2gPfTQd3I0JLWOAEdKYMQRLoun.jpg", "/onwny8s0VTTN7te9TJnItgvyWHy.jpg", "2007-03-05", 7.6, 282),
            tv(114501, "Dug Days", "/e5kT33XH2gX7xBFIK1uUJAvU5dj.jpg", "/pgWgB8AfFOtwKSSoGYbmWsO5Mfq.jpg", "2021-09-01", 7.4, 543),
            tv(323849, "Forky Asks a Question", "/kd8Tuc1110dkhdWELC3APLvIJOK.jpg", "/kdnIvDYDXTSJeEtEVzihVl7VtKu.jpg", "2019-11-12", 0.0, 0),
            tv(91249, "Snoopy in Space: The Search for Life", "/ssAYRDytRsA8S0TAYMrr8dJgrlT.jpg", "/rKAMgHfUpT7Xc5jFMOuG9G6jumG.jpg", "2019-11-01", 7.5, 60),
            tv(46879, "Mickey Mouse", "/aAJ5T2Ab28vbbP9s6wWqdtK3arQ.jpg", "/qqaCwVzDmocRBI5Kl4RGgfwabn1.jpg", "2013-06-28", 7.6, 239),
            tv(3934, "Mickey Mouse Clubhouse", "/gHtEhlAZHxMawOiPq7JoKwkmETQ.jpg", "/89BPrKS6BoktBPTfWcHMQrFNo8s.jpg", "2006-05-05", 6.8, 388)
        )
    ),
    DiscoveryPreset(
        key = "teen-adventure",
        title = "Tweens and teens",
        subtitle = "Higher-energy titles worth checking against profile limits.",
        seeds = listOf(
            movie(671, "Harry Potter and the Philosopher's Stone", "/wuMc08IPKEatf9rnMNXvIDxqP4W.jpg", "/1XAC6RPT01UX9EQGy2JVn5c8pgy.jpg", "2001-11-16", 7.9, 29774),
            movie(411, "The Chronicles of Narnia", "/iREd0rNCjYdf5Ar0vfaW32yrkm.jpg", "/tuDhEdza074bA497bO9WFEPs6O6.jpg", "2005-12-07", 7.1, 11582),
            tv(103540, "Percy Jackson and the Olympians", "/40eFcTzZier3DWLqldsP5VHxeoD.jpg", "/danN2NzJTMouaBEkDWTnyDjDxUt.jpg", "2023-12-19", 7.3, 785),
            tv(40075, "Gravity Falls", "/qwi3p6PzKfQZ4YXBzv3CP5pO2dE.jpg", "/lhg7eA6CTOCL10QNVdKiyxkgPsL.jpg", "2012-06-15", 8.6, 3511),
            movie(11, "Star Wars", "/6FfCtAuVAW8XJjZ7eWeLibRLWTw.jpg", "/yUiXA68FfQeA8cRBhd0Ao0jIRZt.jpg", "1977-05-25", 8.2, 22479),
            movie(329, "Jurassic Park", "/63viWuPfYQjRYLSZSZNq7dglJP5.jpg", "/o7LzVmlOSYc3EspyVMC9bsTTARc.jpg", "1993-06-11", 8.0, 17990),
            movie(70160, "The Hunger Games", "/yXCbOiVDCxO71zI7cuwBRXdftq8.jpg", "/yVBQ65YBYZ9UhzJ4NGdRgeXtyTL.jpg", "2012-03-12", 7.2, 23403),
            tv(119051, "Wednesday", "/36xXlhEpQqVVPuiZhfoQuaY4OlA.jpg", "/iHSwvRVsRyxpX7FE7GbviaDvgGZ.jpg", "2022-11-23", 8.3, 10622)
        )
    )
)

private fun movie(
    tmdbId: Int,
    title: String,
    posterPath: String?,
    backdropPath: String?,
    releaseDate: String?,
    voteAverage: Double,
    voteCount: Int,
    originalLanguage: String = "en"
): DiscoverySeed = DiscoverySeed(RemoteMediaType.Movie, tmdbId, title, posterPath, backdropPath, releaseDate, voteAverage, voteCount, originalLanguage)

private fun tv(
    tmdbId: Int,
    title: String,
    posterPath: String?,
    backdropPath: String?,
    releaseDate: String?,
    voteAverage: Double,
    voteCount: Int,
    originalLanguage: String = "en"
): DiscoverySeed = DiscoverySeed(RemoteMediaType.Tv, tmdbId, title, posterPath, backdropPath, releaseDate, voteAverage, voteCount, originalLanguage)

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
