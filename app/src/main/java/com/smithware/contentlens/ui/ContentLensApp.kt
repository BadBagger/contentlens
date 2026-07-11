package com.smithware.contentlens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smithware.contentlens.data.ContentRatingEntryEntity
import com.smithware.contentlens.data.ContentReportEntity
import com.smithware.contentlens.data.MediaTitleEntity
import com.smithware.contentlens.data.tmdb.ImageUrlBuilder
import com.smithware.contentlens.data.tmdb.NormalizedMediaResult
import com.smithware.contentlens.data.UserProfileEntity
import com.smithware.contentlens.domain.ContentCategory
import com.smithware.contentlens.domain.FitLabel
import com.smithware.contentlens.domain.Severity

private enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Search("Search", Icons.Outlined.Search),
    Watchlist("Watchlist", Icons.Outlined.BookmarkAdd),
    Profiles("Profiles", Icons.Outlined.Person),
    Report("Report", Icons.Outlined.RateReview),
    Settings("Settings", Icons.Outlined.Settings)
}

private val LensColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFFA16207),
    surface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFFE2E8F0),
    background = Color(0xFFF8FAFC),
    onPrimary = Color.White,
    onSurface = Color(0xFF0F172A)
)

@Composable
fun ContentLensApp(viewModel: ContentLensViewModel) {
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    MaterialTheme(colorScheme = LensColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (screen) {
                    Screen.Home -> HomeScreen(state, viewModel, onOpenSearch = { screen = Screen.Search })
                    Screen.Search -> SearchScreen(state, viewModel, onOpenReport = { screen = Screen.Report })
                    Screen.Watchlist -> WatchlistScreen(state, viewModel)
                    Screen.Profiles -> ProfilesScreen(state, viewModel)
                    Screen.Report -> SubmitReportScreen(state, viewModel)
                    Screen.Settings -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, viewModel: ContentLensViewModel, onOpenSearch: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Header(
                title = "ContentLens",
                subtitle = "Ratings that explain themselves."
            )
            Text(
                "ContentLens ratings are informational and based on content reports, not official certification.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            state.selectedTitle?.let {
                TitleDetailCard(it, state, viewModel)
            } ?: EmptyState("No titles yet", "Demo titles will appear after local data is seeded.")
        }
        item {
            Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search or add titles")
            }
        }
        item {
            SectionTitle("Recent demo titles")
        }
        items(state.titles.take(5), key = { it.id }) { title ->
            TitleResultRow(
                title = title,
                selected = title.id == state.selectedTitleId,
                onClick = { viewModel.selectTitle(title.id) }
            )
        }
    }
}

@Composable
private fun SearchScreen(state: AppUiState, viewModel: ContentLensViewModel, onOpenReport: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var searchField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query, selection = TextRange(state.query.length)))
    }
    LaunchedEffect(state.query) {
        if (state.query != searchField.text) {
            searchField = TextFieldValue(state.query, selection = TextRange(state.query.length))
        }
    }
    fun closeKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val searchText = searchField.text
    Column(Modifier.fillMaxSize().imePadding().padding(18.dp)) {
        Header("Search", "Find local demo movies and shows, then inspect the specific content notes.")
        OutlinedTextField(
            value = searchField,
            onValueChange = {
                searchField = it
                viewModel.updateQuery(it.text)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Search titles") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotBlank()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.clickable {
                            searchField = TextFieldValue("")
                            viewModel.updateQuery("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { closeKeyboard() },
                onDone = { closeKeyboard() }
            ),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { closeKeyboard() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
            OutlinedButton(
                onClick = {
                    closeKeyboard()
                    onOpenReport()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Report title")
            }
        }
        Spacer(Modifier.height(12.dp))
        RemoteSearchContent(
            state = state.remoteSearch,
            onRetry = {
                closeKeyboard()
                viewModel.retrySearch()
            },
            onClear = {
                closeKeyboard()
                searchField = TextFieldValue("")
                viewModel.updateQuery("")
            },
            onLoadMore = viewModel::loadMoreRemoteResults
        )
    }
}

@Composable
private fun RemoteSearchContent(
    state: RemoteSearchUiState,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onLoadMore: () -> Unit
) {
    when (state) {
        RemoteSearchUiState.Initial -> EmptyState("Search movies and TV", "Type at least two characters to search TMDB for movies and shows.")
        is RemoteSearchUiState.Waiting -> EmptyState("Ready to search", "Keep typing or tap Search. Requests wait briefly so stale searches are cancelled.")
        is RemoteSearchUiState.Loading -> LoadingState("Searching TMDB", "Looking for movies and TV shows with artwork.")
        is RemoteSearchUiState.NoResults -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EmptyState("No matching titles", "TMDB did not return movies or shows for \"${state.query}\".")
            TextButton(onClick = onClear) { Text("Clear search") }
        }
        is RemoteSearchUiState.Offline -> RetryState("Offline", "ContentLens could not reach TMDB. Check your connection and try again.", onRetry)
        is RemoteSearchUiState.ConfigurationError -> RetryState("Search not configured", state.message, onRetry)
        is RemoteSearchUiState.ServerError -> RetryState("Search error", state.message, onRetry)
        is RemoteSearchUiState.Results -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${state.totalResults} results from TMDB",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Movie and TV metadata and images are provided by TMDB.",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            items(state.results, key = { "${it.mediaType}-${it.tmdbId}" }) { result ->
                RemotePosterResultCard(result, state.imageUrlBuilder)
            }
            if (state.hasMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoadingMore
                    ) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isLoadingMore) "Loading more" else "Load more")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePosterResultCard(result: NormalizedMediaResult, imageUrlBuilder: ImageUrlBuilder) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            PosterArtwork(
                url = imageUrlBuilder.poster(result.posterPath),
                contentDescription = "Poster for ${result.title}",
                modifier = Modifier.width(92.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(onClick = {}, label = { Text(result.mediaType.label) })
                }
                Text(
                    listOfNotNull(result.releaseYear?.toString(), "TMDB ${String.format("%.1f", result.voteAverage)}").joinToString(" • "),
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    result.overview.ifBlank { "No overview is available yet." },
                    color = Color(0xFF334155),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text("Compatibility pending") })
                    AssistChip(onClick = {}, label = { Text("Match: title search") })
                }
            }
        }
    }
}

@Composable
private fun PosterArtwork(url: String?, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
    ) {
        if (url == null || failed) {
            MissingPosterPlaceholder()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .diskCacheKey(url)
                    .memoryCacheKey(url)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        }
    }
}

@Composable
private fun MissingPosterPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun LoadingState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RetryState(title: String, body: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun WatchlistScreen(state: AppUiState, viewModel: ContentLensViewModel) {
    val watchlistTitles = state.watchlist.mapNotNull { item -> state.titles.firstOrNull { it.id == item.titleId } }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Watchlist", "Saved titles stay on this device.") }
        if (watchlistTitles.isEmpty()) {
            item { EmptyState("No watchlist yet", "Save a title from its detail view to compare later.") }
        } else {
            items(watchlistTitles, key = { it.id }) { title ->
                TitleResultRow(title, title.id == state.selectedTitleId) {
                    viewModel.selectTitle(title.id)
                }
            }
        }
    }
}

@Composable
private fun ProfilesScreen(state: AppUiState, viewModel: ContentLensViewModel) {
    val activeProfile = state.settings.defaultProfileId ?: state.profiles.firstOrNull()?.id
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Profiles", "Sensitivity settings shape the Personal Fit Score.") }
        if (state.profiles.isEmpty()) {
            item { EmptyState("No profile created", "Create profiles later for parents, teens, classrooms, or personal viewing.") }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(profile, selected = profile.id == activeProfile) {
                    viewModel.selectProfile(profile.id)
                }
            }
        }
        item {
            SectionTitle("Active profile settings")
            if (state.sensitivities.isEmpty()) {
                EmptyState("No profile sensitivities", "This profile has no category limits yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sensitivities.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.category.label, fontWeight = FontWeight.Medium)
                            Text(it.sensitivity.label, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmitReportScreen(state: AppUiState, viewModel: ContentLensViewModel) {
    var titleId by rememberSaveable(state.titles) { mutableStateOf(state.selectedTitleId ?: state.titles.firstOrNull()?.id.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(ContentCategory.Language) }
    var severity by rememberSaveable { mutableStateOf(Severity.Mild) }
    var explanation by rememberSaveable { mutableStateOf("") }
    var spoilerNote by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf("") }
    var episode by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("Submit Report", "Add or correct a local content report for the MVP.")
        if (state.titles.isEmpty()) {
            EmptyState("No titles yet", "Local reports need a title to attach to.")
            return@Column
        }

        TitleDropdown("Title", state.titles, state.titles.firstOrNull { it.id == titleId } ?: state.titles.first()) {
            titleId = it.id
        }
        EnumDropdown("Category", ContentCategory.entries, category, { it.label }) { category = it }
        EnumDropdown("Severity", Severity.entries, severity, { it.label }) { severity = it }
        OutlinedTextField(
            value = explanation,
            onValueChange = { explanation = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Spoiler-free explanation") }
        )
        OutlinedTextField(
            value = spoilerNote,
            onValueChange = { spoilerNote = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("Detailed spoiler note optional") },
            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(season, { season = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Season") }, singleLine = true)
            OutlinedTextField(episode, { episode = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Episode") }, singleLine = true)
        }
        Button(
            onClick = {
                viewModel.submitReport(
                    titleId = titleId,
                    category = category,
                    severity = severity,
                    explanation = explanation,
                    spoilerNote = spoilerNote,
                    season = season.toIntOrNull(),
                    episode = episode.toIntOrNull()
                )
                explanation = ""
                spoilerNote = ""
                season = ""
                episode = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = explanation.isNotBlank()
        ) {
            Text("Submit locally")
        }
        ReportsList(state.reports)
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, viewModel: ContentLensViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Settings", "Local-first controls for privacy and display preferences.")
        SettingSwitch("Spoiler-free mode", "Detailed spoilers stay hidden behind tap-to-reveal.", state.settings.spoilerFreeMode, viewModel::setSpoilerFreeMode)
        SettingSwitch("Show official ratings", "Display known official ratings when demo data includes them.", state.settings.showOfficialRatings, viewModel::setShowOfficialRatings)
        SettingSwitch("Show user reports", "Include local user-submitted report notes in the app.", state.settings.showUserReports, viewModel::setShowUserReports)
        SectionTitle("Default profile")
        state.profiles.forEach { profile ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.selectProfile(profile.id) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text(profile.description, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
                if (state.settings.defaultProfileId == profile.id) AssistChip(onClick = {}, label = { Text("Default") })
            }
        }
        SectionTitle("Data/privacy")
        InfoCard("Local-first storage", "Titles, profiles, watchlist items, settings, and reports are stored on this device for the MVP. No login or backend is required.")
        SectionTitle("About ContentLens")
        InfoCard("Informational ratings", "ContentLens ratings are informational and based on content reports, not official certification.")
        InfoCard("Search data source", "Movie and TV search metadata and images are provided by TMDB when a local TMDB read access token is configured.")
    }
}

@Composable
private fun TitleDetailCard(titleLens: TitleLens, state: AppUiState, viewModel: ContentLensViewModel) {
    var showSpoilers by rememberSaveable(titleLens.title.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosterPlaceholder(titleLens.title.posterTone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleLens.title.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${titleLens.title.year} • ${titleLens.title.type.label}", color = Color(0xFF64748B))
                    if (state.settings.showOfficialRatings) Text("Official rating: ${titleLens.title.officialRating ?: "Unknown"}", color = Color(0xFF475569))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("ContentLens: ${titleLens.summary.rating.label}") })
                AssistChip(onClick = {}, label = { Text(titleLens.fit.label) })
            }
            Text(titleLens.title.summary, color = Color(0xFF334155))
            if (titleLens.fit == FitLabel.Caution) Text("Caution for this profile.", color = Color(0xFFA16207), fontWeight = FontWeight.SemiBold)
            SectionTitle("Top content warnings")
            if (titleLens.summary.topWarnings.isEmpty()) EmptyState("Insufficient data", "No content warnings are available for this title yet.") else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titleLens.summary.topWarnings.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            SectionTitle("Detailed breakdown")
            ContentBreakdown(titleLens.entries, state.settings.spoilerFreeMode, showSpoilers)
            if (titleLens.entries.any { it.spoilerNote != null }) {
                TextButton(onClick = { showSpoilers = !showSpoilers }) {
                    Text(if (showSpoilers) "Hide detailed spoilers" else "Reveal detailed spoilers")
                }
            }
            OutlinedButton(onClick = { viewModel.toggleWatchlist(titleLens.title.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (titleLens.isWatchlisted) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (titleLens.isWatchlisted) "Remove from watchlist" else "Save to watchlist")
            }
        }
    }
}

@Composable
private fun ContentBreakdown(entries: List<ContentRatingEntryEntity>, spoilerFreeMode: Boolean, showSpoilers: Boolean) {
    if (entries.isEmpty()) {
        EmptyState("No reports available", "Submit a local report to start building the breakdown.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.category.label, fontWeight = FontWeight.SemiBold)
                        Text(entry.severity.label, color = SeverityColor(entry.severity), fontWeight = FontWeight.SemiBold)
                    }
                    Text(entry.explanation, color = Color(0xFF475569))
                    val episodeText = listOfNotNull(entry.season?.let { "S$it" }, entry.episode?.let { "E$it" }).joinToString(" ")
                    if (episodeText.isNotBlank()) Text(episodeText, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    if (!spoilerFreeMode || showSpoilers) {
                        entry.spoilerNote?.let { Text("Spoiler detail: $it", color = Color(0xFF334155), modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleResultRow(title: MediaTitleEntity, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEFF6FF) else Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterPlaceholder(title.posterTone, compact = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${title.year} • ${title.type.label}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: UserProfileEntity, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFECFDF5) else Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF0F766E))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold)
                Text(profile.description, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            if (selected) AssistChip(onClick = onClick, label = { Text("Active") })
        }
    }
}

@Composable
private fun ReportsList(reports: List<ContentReportEntity>) {
    SectionTitle("Local submissions")
    if (reports.isEmpty()) {
        EmptyState("No reports available", "Submitted reports will appear here and remain local.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reports.take(5).forEach {
                InfoCard("${it.category.label}: ${it.severity.label}", it.explanation)
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PosterPlaceholder(tone: String, compact: Boolean = false) {
    val color = when (tone) {
        "Green", "Mint" -> Color(0xFF0F766E)
        "Amber" -> Color(0xFFA16207)
        "Rose" -> Color(0xFFBE123C)
        "Teal" -> Color(0xFF0F766E)
        "Violet" -> Color(0xFF7C3AED)
        "Sky", "Blue" -> Color(0xFF2563EB)
        else -> Color(0xFF334155)
    }
    Box(
        modifier = Modifier
            .size(if (compact) 48.dp else 86.dp)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.62f))), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 24.dp else 38.dp))
    }
}

@Composable
private fun SeverityColor(severity: Severity): Color = when (severity) {
    Severity.None -> Color(0xFF64748B)
    Severity.Mild -> Color(0xFF0F766E)
    Severity.Moderate -> Color(0xFFA16207)
    Severity.Strong -> Color(0xFFB45309)
    Severity.GraphicHeavy -> Color(0xFFBE123C)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(label: String, items: List<T>, selected: T, itemLabel: (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = itemLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach {
                DropdownMenuItem(text = { Text(itemLabel(it)) }, onClick = {
                    onPick(it)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDropdown(label: String, items: List<MediaTitleEntity>, selected: MediaTitleEntity, onPick: (MediaTitleEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected.title,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach {
                DropdownMenuItem(text = { Text("${it.title} (${it.year})") }, onClick = {
                    onPick(it)
                    expanded = false
                })
            }
        }
    }
}
