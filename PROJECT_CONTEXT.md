# ContentLens Project Context

## Current State

- App: ContentLens
- Package: `com.smithware.contentlens`
- Repo target: `BadBagger/contentlens`
- Latest published release: `v0.1.1-keyboard-fix`
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

- Optional metadata API integration
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
