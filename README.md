<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tv_banner.webp" alt="MeghTV" width="720">
</p>

# MeghTV

![Release APK](https://github.com/meghsohor/iptv-android-tv/actions/workflows/release.yml/badge.svg)

An Android TV and phone app for watching the live TV channels listed by [iptv-org](https://github.com/iptv-org).

## Features

- Runs on Android TV (remote control) and Android phones (touch, landscape).
- Channels browsed by category or country, with search and favourites stored on the device.
- Plays HLS, DASH, SmoothStreaming and RTSP streams, and tries a channel's other stream URLs when one fails.
- "Refresh Channels" updates the channel list from iptv-org.

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

Bump `versionName` and `versionCode` in `app/build.gradle.kts` in the PR. On merge, a signed APK is published as a GitHub Release, but only if `versionName` increased.
