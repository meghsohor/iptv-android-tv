<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tv_banner.webp" alt="MeghTV" width="720">
</p>

# MeghTV

![Release APK](https://github.com/meghsohor/iptv-android-tv/actions/workflows/release.yml/badge.svg)

Live TV for Android TV and Android phones — thousands of free channels sourced from [iptv-org](https://github.com/iptv-org), browsable by category and country, with local favourites. No account, no cloud sync.

## Features

**Browsing**
- Opens on your Favourites (or Categories if you have none) and waits for you to pick a channel — nothing autoplays
- One side panel for everything: Refresh, Search, Favourites and Categories, drilling into All Channels, a category, or Countries → a country
- Every channel list is alphabetical and shows its channel count; categories and countries with no playable channels are left out
- Search by channel name, updating as you type
- Star any channel to add it to Favourites

**Watching**
- Live playback of HLS, DASH, SmoothStreaming and RTSP streams; on a phone, captions and audio tracks (when a channel has them) from the player's settings
- Channels with several mirrored streams fall back to the next one automatically; if they all fail, a clear message (and whether it's your connection or the channel) with a Retry button
- A loading spinner while a channel starts, and switching channels always starts clean

**On a TV remote**
- Arrow keys reveal the panel, which hides itself after a few seconds of no input (not while searching)
- With the panel away, OK shows the player controls and then plays/pauses; the remote's play/pause key works any time
- Channel Up / Down steps through the list you picked from, without opening the panel
- Back peels off one layer at a time: player controls, then a hidden panel comes back, then up one level

**On a phone**
- Landscape, full screen (status bar hidden), with the screen kept awake while playing
- The panel stays open until you tap outside it; the arrow tab on the right edge brings it back
- Tap the video to show the controls, tap the controls to play/pause, or double-tap any time; on-screen volume and mute
- Playback stops when you leave the app and rejoins live when you come back

**Refreshing**
- "Refresh Channels" pulls the latest channels, categories, countries and stream URLs from iptv-org and diffs them against what's stored locally, keeping your favourites — runs automatically on first launch

Built for low-end TV hardware as well as 4K displays: lazy-loaded lists, no logo/bitmap loading, density-independent UI.

## Tech stack

Kotlin, Jetpack Compose, Media3/ExoPlayer for playback, Room for local storage, OkHttp for fetching iptv-org's data.

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
