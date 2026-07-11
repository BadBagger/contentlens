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

Remote movie and TV search uses TMDB. Add a TMDB API Read Access Token locally before building an APK that should return real media results:

```properties
tmdbReadAccessToken=YOUR_TMDB_READ_ACCESS_TOKEN
```

The token can be stored in ignored `local.properties` or provided as the `TMDB_READ_ACCESS_TOKEN` environment variable. Do not commit API tokens.

Without this configuration, the app builds and shows a Search not configured state instead of silently returning no results.
