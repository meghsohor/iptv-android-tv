# AGENTS.md

Operational notes for any AI agent working in this repo — the stuff that isn't in the README because it's about environment/tooling quirks and hard-won gotchas, not the app itself. The README only lists the main features; the detailed navigation and playback behaviour is documented in comments in `TvHomeScreen.kt`, `VideoPlayer.kt` and `TvHomeViewModel.kt`.

## Current status (as of 2026-09-25)

- The app runs on both Android TV (D-pad) and Android phones (touch, landscape) — verified on the `tv_1080p` and `medium_phone` emulators against real iptv-org data.
- PRs #1 (branding + release gating), #2 (CI check) and #3 (panel auto-hide, channel zap, captions, style) are merged; releases `v1.1` and `v1.2` are published.
- **PR #4** (`mobile-layout-and-touch-fixes`) is open: phone support, player controls, TV remote fixes, DASH/SmoothStreaming/RTSP, performance work, README banner. It bumps to `1.3`/versionCode 4, so merging it publishes `v1.3`. Two independent review passes and two Copilot reviews are addressed, with a reply on every Copilot thread; one Copilot suggestion ("0 channels" on empty lists) was declined with reasons on the thread.
- Next, after PR #4 merges: a tests PR (TV key routing, ViewModel startup and channel zap, DAO ordering and pruning).

## Dev environment — command-line only, no Android Studio

- JDK 17 via Homebrew (`openjdk@17`), keg-only — not on PATH by default. Every build command needs `export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"` first.
- The real Android SDK lives at `~/Library/Android/sdk`, managed by the **`android` CLI** (Google's newer agent-oriented tool — `android sdk install`, `android emulator`, `android run`, `android screen`, etc.), not the classic `sdkmanager`/`avdmanager` workflow. Run `android --help` or load the `android-cli` skill (already installed under `.claude/skills/`, `.codex/skills/`, etc.) for its command surface.
- Homebrew's `android-commandlinetools` cask is also installed (gives `avdmanager`, `sdkmanager` binaries at `/opt/homebrew/bin`), because `android emulator create` has no TV device profile — creating/editing the TV AVD has to go through classic `avdmanager` instead.
- **Gotcha**: `avdmanager` self-locates its SDK root relative to its own binary path and ignores `$ANDROID_HOME`/`$ANDROID_SDK_ROOT` entirely (unlike `sdkmanager`, which respects an explicit `--sdk_root=` flag). Its resolved root ends up being `/opt/homebrew/share/android-commandlinetools` — which has no platforms/system-images of its own, since those were installed via the `android` CLI into `~/Library/Android/sdk`. Fix already in place: `system-images`, `platforms`, `platform-tools`, and `emulator` under the homebrew path are **symlinked** to the real ones in `~/Library/Android/sdk`. If `avdmanager create avd` ever fails again with "Valid system image paths are: null", check those symlinks first before assuming a package is missing.
- The TV AVD is named `tv_1080p` (Android TV, API 34, arm64-v8a, Google TV image, 2 GB RAM — deliberately low-end). The phone AVD is `medium_phone` (API 36, made with `android emulator create medium_phone`); its RAM is raised to 4 GB in `~/.android/avd/medium_phone.avd/config.ini`, because at the default 2 GB the guest thrashed and the app looked frozen for 15+ s at launch.

## Emulator gotchas (both AVDs)

- **Always cold-boot** (`android emulator start --cold <avd>`, or `emulator -avd <avd> -no-snapshot-load`). A snapshot resume brings back a stale clock, and every HTTPS stream then fails certificate validation ("validity interval is out-of-date").
- **The GPU mode changes what you can measure.** `android emulator start` picks SwiftShader, a software renderer: frame timing is meaningless there (All Channels flings measured 88% janky frames on it, versus 2.4% on the real GPU). For any performance number, boot with `emulator -avd <avd> -gpu host`. SwiftShader is still useful: its decoder reproduced a real bug, the old channel's frame stuck around a lower-resolution new one, that `-gpu host` hides.
- **`uiautomator dump` hangs while video plays**, because it waits for the UI to go idle. Find coordinates from screenshots instead (`adb exec-out screencap -p`).
- `adb shell input text` with `(` or spaces: quote the whole shell command, e.g. `adb shell "input text 'RTV%s(720p)'"` (`%s` = space).
- **A debug-signed install blocks the release APK** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and vice versa. Uninstall first, which wipes favourites. Same on real devices: always hand out the signed release build.
- Performance profiling: a fresh sideload runs mostly interpreted until Android's idle-time dexopt. Reproduce that with `adb shell cmd package compile --reset <pkg>`, or the steady state with `-m speed -f`. `simpleperf` needs `-e cpu-clock` on the emulator (no hardware counters) and a temporary `<profileable android:shell="true"/>` in the manifest — never commit that.

## Emulator input is broken on this machine — known, not fixable from here

Physical keyboard/mouse input to the emulator's own window does not work on this machine (macOS 27.0, build 26A428 — a very new OS release; the emulator is Qt/QEMU-based and this combination has a known history of host-input-forwarding breakage on brand-new macOS versions). Confirmed:
- Not a remote-desktop/virtual-display artifact — this is the user's own physical MacBook Pro (M3 Pro) with a real external Dell monitor.
- Not fixed by launching the emulator from the user's own interactive terminal instead of a background process.
- Not fixed by a newer emulator version (37.1.11 was already the latest available at investigation time).
- `adb shell input tap`/`input keyevent` work perfectly regardless — they inject events through ADB directly into the guest OS, bypassing the host's window/input stack entirely. This is why all agent-driven interaction in this project uses `adb`/`android screen capture`, never the emulator's own window.

**Don't re-litigate this** unless something material changes (OS update, emulator update). For a human to interact with the app directly, the real fix is sideloading onto a physical Android TV/Fire TV/Chromecast/Shield device — the full build+sign+`adb install` pipeline already works for that.

## iptv-org data source — non-obvious things learned by inspecting it directly

- Source repos: metadata (`channels.csv`, `feeds.csv`, `categories.csv`, `countries.csv`) from `iptv-org/database`; actual stream URLs from the compiled per-country playlists (`streams/<cc>.m3u`) in `iptv-org/iptv`.
- A channel can have **multiple feeds** (regional/quality variants, e.g. Sydney vs Melbourne) — each is a distinct row, keyed `channelId@feedId`. It can *also* have **multiple URLs for the same feed** (mirrors) — checked across several countries, this is the norm, not an edge case (e.g. `France24.fr@English` had 11 mirror URLs). These are collapsed into one row with an ordered URL list, not shown as duplicates.
- Multi-value fields (categories, owners) are semicolon-joined by the source, not comma-joined — don't naive-split on commas.
- `.m3u` parsing: tvg-id can be blank (`tvg-id=""`) — skip those entries, there's no channel/feed to map them to.
- CSVs need real RFC4180 parsing (quoted fields can contain commas) — see `data/remote/CsvParser.kt`. Don't swap in a naive split.
- The metadata defines more than the playlists carry. Of 30 categories, "XXX" has no stream at all, and 73 of 250 countries (Antarctica, Bouvet Island, …) have none either, so refresh drops any category or country without a channel. `channels.csv` also has an `is_nsfw` column: ~375 channels are flagged, but only one of them ever has a stream in the public playlists. The user decided to leave adult channels alone.
- Stream formats, across 17.5k URLs: ~16.9k HLS, ~210 DASH, ~290 with no file extension, and a handful of RTSP/RTMP/SRT/MMS. Media3 needs a module per format, and without one it threw from `setMediaItem()`, crashing the app (every DASH channel did that up to v1.2). DASH, SmoothStreaming and RTSP modules are included. **RTMP was left out on purpose**: its native library costs ~380 KB of APK for the 6 RTMP-only channels. SRT and MMS have no Media3 support at all. Any format that can't be opened now counts as a failed source, never a crash. Media3 picks the format from the URL extension, so an extension-less URL that fails as "unrecognized" gets one retry as HLS.

## Release signing

- Keystore + passwords are deliberately kept **outside this repo**, at `~/Shuvo/Documents/iptv-android-tv-signing/` (`release.keystore`, `passwords.txt`). Never commit a keystore or its passwords.
- Same keystore is base64-encoded into the `ANDROID_KEYSTORE_BASE64` GitHub Actions secret (plus `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`) for CI signing, and referenced by path for local signing via `scripts/build-local-release.sh`.
- `scripts/build-local-release.sh` uses `sed` (not `grep -P`) to pull `versionName` out of `build.gradle.kts`, because macOS ships BSD `grep`/`sort` (no `-P`, no `-V`) — the CI workflow runs on `ubuntu-latest` (GNU tools) and can use `grep -P`/`sort -V` freely, but local scripts on this Mac cannot.
- `r0adkll/sign-android-release`'s own default build-tools version (29.0.3) isn't reliably present on GitHub-hosted runners — the workflow explicitly installs and pins `build-tools;34.0.0` via `BUILD_TOOLS_VERSION` env.
- OkHttp is pinned to `4.12.0`, not 5.x — OkHttp 5's `-android` variant declares a `compileSdk 37` floor that our AGP version doesn't support yet. Don't bump it without checking that constraint again.

## Git workflow

- **No direct pushes to `main`.** Everything goes through a PR, even solo work.
- **Commit messages are one line, no body.** The repo squash-merges with `COMMIT_MESSAGES` and `release.yml` publishes releases without notes, so GitHub shows the merge commit's message — every commit message in the PR, concatenated — on the release page. Long bodies are why the v1.2 release page is 105 lines.
- Docs and PR descriptions are plain statements of what the app does: no selling tone, and no internal history such as fixed bugs or reviewer finding IDs.
- A force-push while a Copilot review is running doesn't cancel it; the review lands on the old commits.
- Version bumps (`versionName` + `versionCode` in `app/build.gradle.kts`) belong in the PR that should trigger a release.
- The release workflow (`.github/workflows/release.yml`) has a `check-version` gate: it only builds+signs+publishes if `versionName` increased since the last published release. A merge that doesn't bump it is a no-op for releases (no rebuild, no republish) — this is intentional, not a bug.
- `.github/workflows/ci.yml` builds the debug APK on every PR targeting `main` (check name: `build`) — **informational only**, not a hard merge gate. Classic branch protection *and* the newer Rulesets API both refused with "Upgrade to GitHub Pro or make this repository public" — a private repo on a free personal account can't enforce required status checks via GitHub's own merge-blocking. Don't re-attempt this without one of those two things changing; it's a real account-tier wall, not a config mistake.

## Known gaps (deliberately deferred, not forgotten)

- No automated tests yet — the original template's tests were deleted because they referenced the placeholder code they replaced.
- On TV, the player's CC and settings buttons can't be reached with the D-pad: the controller is kept out of the focus chain so that OK and Back behave (see `VideoPlayer.kt`). On a phone they're tappable.
- A category filter inside Search was discussed and deferred by the user.
- No manual Source-switcher UI in the player yet (the data model and automatic fallback-on-error both already support multiple sources per channel; just no on-screen "Source 2 of 3" control).
- `androidx.tv` (tv-foundation/tv-material) is a dependency but the UI currently uses plain Compose Foundation/Material3 widgets with manual focus handling, not the TV-specific component set — a deliberate risk-aversion choice made under time pressure, not a final decision.
- Channel lists sort with `COLLATE NOCASE`, which only folds ASCII case, so non-Latin names sort after Latin ones.
- On Android 16, screens 600dp and wider (tablets, unfolded foldables) ignore the landscape lock.
- Paging 3 is a dependency but not actually wired into any query yet — "All Channels" (~11k rows) still loads as a plain `Flow<List<ChannelEntity>>`, which is the one list where this will eventually matter for low-end-device memory.
