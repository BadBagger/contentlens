# ContentLens Project Context

## Current State

- App: ContentLens
- Package: `com.smithware.contentlens`
- Repo target: `BadBagger/contentlens`
- Latest published release: `v0.1.3-search-cursor-fix`
- Current development stage: Phase 1/2 TMDB search and artwork repair is implemented locally but not published; real result verification is blocked until a TMDB read access token is configured.
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

- TMDB read access token configured through ignored `local.properties` or `TMDB_READ_ACCESS_TOKEN`
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

## Phase 1/2 Search Repair Notes

- Root cause of the original search failure: Search only filtered the seeded local demo `MediaTitleEntity` list in memory. It did not call a remote movie database, did not have network permission, did not normalize TMDB movie/TV responses, and did not have poster image loading.
- Implemented a TMDB client that calls `/search/movie` and `/search/tv` concurrently, URL-encodes search text, uses bearer-token auth from local build configuration, fetches TMDB image configuration, and distinguishes offline, authentication/configuration, parsing, server, no-results, loading, waiting, and initial states.
- Implemented normalized search results with `tmdbId`, media type, title/original title, overview, poster/backdrop paths, release date/year, genres, popularity, vote data, adult flag, and original language.
- Implemented centralized image URL construction and poster-card search results with Coil image loading, crossfade, memory/disk cache keys, fixed poster aspect ratio, and fallback artwork.
- Added unit tests for movie normalization, TV normalization, missing title handling, image URL construction, missing token handling, auth/server error handling, and URL-encoded search requests.
- Remote search state is now held separately from the Room-backed app state so pagination appends are not lost when local profiles, reports, settings, or watchlist data emit updates.
- Search results and Settings include TMDB source attribution. Developer-safe logging reports status codes, endpoint paths, and parsing/network failures without logging API tokens.
