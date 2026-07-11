# ContentLens Project Context

## Current State

- App: ContentLens
- Package: `com.smithware.contentlens`
- Repo target: `BadBagger/contentlens`
- Latest published release: `v0.2.4-report-visibility`
- Current development stage: Phase 1/2 TMDB search and artwork repair is implemented and verified with a locally configured TMDB API key.
- Storage: local Room database plus DataStore settings
- Backend: none for MVP

## MVP Scope

ContentLens is a modern movie and TV content rating app. It helps users understand what is actually in a movie or show and why it received a certain rating.

Implemented MVP:

- Home, Search, Title Details, Content Breakdown, Watchlist, Profiles, Submit Report, and Settings screens
- 10 original demo movie/show titles
- ContentLens informational rating scale
- Personal Fit Score from profile sensitivities
- Spoiler-free mode by default with tap-to-reveal spoiler notes
- Local-only report submission
- Local watchlist
- Data/privacy and about settings copy

## Future Needs

- Continue Phase 3 title detail upgrade using TMDB detail, credits, release dates/certifications, similar titles, and watch providers.
- Real report sync backend
- Moderation queue and trust signals
- Editable profile sensitivity UI
- Timestamp-level entries
- DevHub registry onboarding if ContentLens should appear in the Smithware store app

## Release Notes

- `v0.1.0-mvp`: first signed APK-backed GitHub Release for the local-first ContentLens MVP.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.1.0-mvp`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.1.0-mvp.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.1.1-keyboard-fix`: fixes Search keyboard handling with explicit Search/Done dismissal, a clear-search control, IME padding, result-row keyboard dismissal, and clearer no-results copy.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.1.1-keyboard-fix`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.1.1-keyboard-fix.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.1.2-search-control-fix`: makes the Search page less dependent on keyboard IME behavior by disabling autocorrect/capitalization, adding an explicit on-screen Search button that force-hides the keyboard, adding a Report title shortcut, and adding a clear-search action in the no-results state.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.1.2-search-control-fix`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.1.2-search-control-fix.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.1.3-search-cursor-fix`: changes the Search field to use local `TextFieldValue` state so Gboard composing text no longer moves the cursor after the first typed letter, while still syncing the query text to search results.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.1.3-search-cursor-fix`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.1.3-search-cursor-fix.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.2.0-tmdb-search-artwork`: adds real TMDB movie and TV search with poster-card results, image configuration, poster loading/fallbacks, distinct search/error states, pagination, API-key and bearer-token auth support, TMDB attribution, and tests for response normalization, image URL construction, auth/server errors, URL encoding, and missing configuration.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.2.0-tmdb-search-artwork`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.2.0-tmdb-search-artwork.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- Real TMDB verification passed for `Breaking Bad`, `The Office`, `The Lion King`, `Moana`, `Stranger Things`, and `Bluey`; each returned movie/TV results and a TMDB poster URL returning HTTP 200 `image/jpeg`.
- `v0.2.1-clickable-details-home`: makes TMDB search poster cards tappable, adds remote title details with backdrop, poster, certification/rating, vote score, runtime/episode runtime, TV season counts, genres, overview, cast, providers, similar titles, and source attribution, and cleans Home so it no longer auto-opens the seeded `After the Rainfall` local report.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.2.1-clickable-details-home`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.2.1-clickable-details-home.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.2.2-rating-report`: replaces the vague `Detailed warnings pending` block with a clearer Rating report showing official certification, preliminary ContentLens rating, confidence/source, unknown-data warning, category report status chips, and source explanation. Certification-to-ContentLens mapping is now in the domain layer with unit coverage.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.2.2-rating-report`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.2.2-rating-report.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.2.3-remote-reports`: adds local ContentLens report storage for real TMDB movie/TV results, keyed separately from seeded demo titles; remote title details now show local category reports, computed ContentLens rating, profile fit, spoiler controls, and an inline local report form while keeping unknown-data messaging when no reports exist.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.2.3-remote-reports`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.2.3-remote-reports.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`
- `v0.2.4-report-visibility`: surfaces local TMDB-title report data on search result cards, adds TMDB-title submissions to the Reports screen, and recalculates open remote-title profile fit when the active profile changes.
- Release URL: `https://github.com/BadBagger/contentlens/releases/tag/v0.2.4-report-visibility`
- APK assets: `ContentLens.apk`, `ContentLens-release-v0.2.4-report-visibility.apk`
- Release signing certificate SHA-256: `76eda33cc19ce4ccf514fe9381e6d7da1d8658474fdf06f3b69ebfecd4e2c554`

## Phase 1/2 Search Repair Notes

- Root cause of the original search failure: Search only filtered the seeded local demo `MediaTitleEntity` list in memory. It did not call a remote movie database, did not have network permission, did not normalize TMDB movie/TV responses, and did not have poster image loading.
- Implemented a TMDB client that calls `/search/movie` and `/search/tv` concurrently, URL-encodes search text, uses v3 API-key auth or bearer-token auth from local build configuration, fetches TMDB image configuration, and distinguishes offline, authentication/configuration, parsing, server, no-results, loading, waiting, and initial states.
- Implemented normalized search results with `tmdbId`, media type, title/original title, overview, poster/backdrop paths, release date/year, genres, popularity, vote data, adult flag, and original language.
- Implemented centralized image URL construction and poster-card search results with Coil image loading, crossfade, memory/disk cache keys, fixed poster aspect ratio, and fallback artwork.
- Added unit tests for movie normalization, TV normalization, missing title handling, image URL construction, missing token handling, auth/server error handling, and URL-encoded search requests.
- Remote search state is now held separately from the Room-backed app state so pagination appends are not lost when local profiles, reports, settings, or watchlist data emit updates.
- Search results and Settings include TMDB source attribution. Developer-safe logging reports status codes, endpoint paths, and parsing/network failures without logging API tokens.
