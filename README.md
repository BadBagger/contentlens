# ContentLens

ContentLens is a local-first Smithware Studios Android app for movie and TV content ratings that explain themselves.

The MVP uses Kotlin, Jetpack Compose, Room, and DataStore. It ships with original demo data, local profiles, local watchlist storage, spoiler-free content breakdowns, and local-only report submission.

ContentLens ratings are informational and based on content reports, not official certification.

## Build

```powershell
$env:JAVA_HOME='C:\Users\KyleB\Documents\Codex\2026-07-04\build-a-native-android-app-using\.local-jdk\jdk-17.0.19+10'
$env:ANDROID_HOME='C:\Users\KyleB\Documents\Codex\2026-07-04\build-a-native-android-app-using\.android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

Release builds require an ignored local `keystore.properties` file based on `keystore.properties.example`.

## TMDB Search Configuration

Remote movie and TV search uses TMDB. Add either a TMDB v3 API key or API Read Access Token locally before building an APK that should return real media results:

```properties
tmdbApiKey=YOUR_TMDB_API_KEY
tmdbReadAccessToken=YOUR_TMDB_READ_ACCESS_TOKEN
```

The key/token can be stored in ignored `local.properties` or provided as the `TMDB_API_KEY` / `TMDB_READ_ACCESS_TOKEN` environment variable. Do not commit API tokens.

Without this configuration, the app builds and shows a Search not configured state instead of silently returning no results.

## Content Safety Source Configuration

ContentLens can use a Smithware-owned proxy API for provider-backed content safety data. Production/public builds should use the proxy so provider keys are never embedded in an APK:

```properties
contentLensApiBaseUrl=https://YOUR_CONTENTLENS_API_HOST
```

Current deployed proxy, also used as the checked-in public Android default:

```properties
contentLensApiBaseUrl=https://contentlens-api.sassyboii69.chatgpt.site
```

The backend lives in `backend/contentlens-api` and expects the DoesTheDogDie provider key as a server-side environment variable:

```powershell
cd backend/contentlens-api
$env:DOES_THE_DOG_DIE_API_KEY='YOUR_KEY'
npm test
npm start
```

For local-only Android testing, ContentLens can still call DoesTheDogDie API v3 directly. Add the key locally before building a private APK:

```properties
doesTheDogDieApiKey=YOUR_DOES_THE_DOG_DIE_API_KEY
```

The key can be stored in ignored `local.properties` or provided as the `DOES_THE_DOG_DIE_API_KEY` environment variable. Do not commit API tokens.

Without proxy or direct-provider configuration, title details show a content safety source not configured state and continue to use local reports plus TMDB certification metadata.

Cold provider-backed safety lookups can take several seconds. The Android proxy client uses a longer read timeout for this path than TMDB search so a slow first lookup does not incorrectly appear as an offline API.

Search results prefetch lightweight safety summaries for the first visible cards. TMDB title/poster results still render first; safety chips update in place as provider data returns.
