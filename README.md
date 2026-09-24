# MeghTV

![Release APK](https://github.com/meghsohor/iptv-android-tv/actions/workflows/release.yml/badge.svg)

Android TV app for browsing and watching live TV channels sourced from [iptv-org](https://github.com/iptv-org), organized by category and country, with local bookmarks — no account, no cloud sync.

## Features

- Bookmarks as the default view on launch, with the most recently bookmarked channel autoplaying immediately
- Single sliding panel for navigation: pinned Refresh / Search / Favourites / Categories, drilling into Categories → Countries → channel lists
- Manual "Refresh Channels" pulls the latest channel/category/country data + stream URLs from iptv-org and diffs it against what's stored locally, preserving bookmarks and any manual source picks
- Channels with multiple mirrored stream URLs fall back automatically on playback failure
- Built for both low-end TV hardware and 4K displays (lazy-loaded lists, no bitmap/logo loading, density-independent UI)

## Tech stack

Kotlin, Jetpack Compose (`androidx.tv` for TV-specific components), Media3/ExoPlayer for playback, Room for local storage, OkHttp for fetching iptv-org's data.

## Building locally

Command-line only, no Android Studio required.

```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # release build (R8 shrinking on, unsigned)
```

To build and sign a release APK the same way CI does, without needing to push anything:

```bash
scripts/build-local-release.sh
```

This needs the release keystore + its passwords available locally (kept outside the repo — never commit a keystore). See the script for how to point it at a different location if needed.

## Releasing

- All changes go through a PR — nothing gets pushed to `main` directly.
- Bump `versionName` (and `versionCode`) in `app/build.gradle.kts` as part of the PR when you want a new release to go out.
- On merge, [`.github/workflows/release.yml`](.github/workflows/release.yml) checks whether `versionName` actually increased since the last published release. If it did, it builds, signs, and publishes a new GitHub Release tagged by that version, with the signed APK attached. If it didn't, the merge is a no-op for releases — nothing gets rebuilt or republished.
