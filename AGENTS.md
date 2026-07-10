# ContentLens Agent Instructions

Future Codex chats working in this repository must read this file and `PROJECT_CONTEXT.md` before editing source, changing app identity, or publishing releases.

## Identity

- App name: ContentLens
- Package: `com.smithware.contentlens`
- Owner: Smithware Studios
- Promise: "Ratings that explain themselves."

Do not rename the package or merge this app into another Smithware app unless the user explicitly asks.

## Product Boundaries

- Keep the app local-first.
- No required login.
- No backend or paid metadata API is required for the MVP.
- Do not copy IMDb, Common Sense Media, Rotten Tomatoes, or any existing app's branding, UI text, ratings, or content database.
- Keep ContentLens ratings clearly informational, not official certification.

## Publishing

Publishing means a GitHub Release with APK assets attached. Source-only pushes are not enough.

Release signing must use ignored local `keystore.properties` and an external keystore. Never commit keystores, passwords, credentials, or Play Store service account files.
