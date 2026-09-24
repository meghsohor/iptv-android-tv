# AGENTS.md

Operational notes for any AI agent working in this repo — the stuff that isn't in the README because it's about environment/tooling quirks and hard-won gotchas, not the app itself. Read [`README.md`](README.md) first for what the app does before touching navigation/data-layer behavior.

## Current status (as of 2026-09-23)

- The app itself: built and verified working end-to-end on the local TV emulator against real iptv-org data. See the README's Features section for what's built.
- **PR #1** (`meghtv-branding-and-ci`) — open, not yet merged. Contains: MeghTV branding, `scripts/build-local-release.sh`, README rewrite, this file, the version bump to `1.1`/versionCode 2, and the release-workflow version-gating logic. Merging it **will** trigger a real `v1.1` release (versionName increased from the currently-published `1.0`) — that's intentional, it's the point of this PR.
- **PR #2** (`add-pr-check-workflow`) — merged. Added `.github/workflows/ci.yml` (the informational `build` check described below).
- Next natural step, whenever picking this back up: review and merge PR #1.

## Dev environment — command-line only, no Android Studio

- JDK 17 via Homebrew (`openjdk@17`), keg-only — not on PATH by default. Every build command needs `export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"` first.
- The real Android SDK lives at `~/Library/Android/sdk`, managed by the **`android` CLI** (Google's newer agent-oriented tool — `android sdk install`, `android emulator`, `android run`, `android screen`, etc.), not the classic `sdkmanager`/`avdmanager` workflow. Run `android --help` or load the `android-cli` skill (already installed under `.claude/skills/`, `.codex/skills/`, etc.) for its command surface.
- Homebrew's `android-commandlinetools` cask is also installed (gives `avdmanager`, `sdkmanager` binaries at `/opt/homebrew/bin`), because `android emulator create` has no TV device profile — creating/editing the TV AVD has to go through classic `avdmanager` instead.
- **Gotcha**: `avdmanager` self-locates its SDK root relative to its own binary path and ignores `$ANDROID_HOME`/`$ANDROID_SDK_ROOT` entirely (unlike `sdkmanager`, which respects an explicit `--sdk_root=` flag). Its resolved root ends up being `/opt/homebrew/share/android-commandlinetools` — which has no platforms/system-images of its own, since those were installed via the `android` CLI into `~/Library/Android/sdk`. Fix already in place: `system-images`, `platforms`, `platform-tools`, and `emulator` under the homebrew path are **symlinked** to the real ones in `~/Library/Android/sdk`. If `avdmanager create avd` ever fails again with "Valid system image paths are: null", check those symlinks first before assuming a package is missing.
- The TV AVD is named `tv_1080p` (Android TV, API 34, arm64-v8a, Google TV image). Boot with `android emulator start tv_1080p`.

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

## Release signing

- Keystore + passwords are deliberately kept **outside this repo**, at `~/Shuvo/Documents/iptv-android-tv-signing/` (`release.keystore`, `passwords.txt`). Never commit a keystore or its passwords.
- Same keystore is base64-encoded into the `ANDROID_KEYSTORE_BASE64` GitHub Actions secret (plus `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`) for CI signing, and referenced by path for local signing via `scripts/build-local-release.sh`.
- `scripts/build-local-release.sh` uses `sed` (not `grep -P`) to pull `versionName` out of `build.gradle.kts`, because macOS ships BSD `grep`/`sort` (no `-P`, no `-V`) — the CI workflow runs on `ubuntu-latest` (GNU tools) and can use `grep -P`/`sort -V` freely, but local scripts on this Mac cannot.
- `r0adkll/sign-android-release`'s own default build-tools version (29.0.3) isn't reliably present on GitHub-hosted runners — the workflow explicitly installs and pins `build-tools;34.0.0` via `BUILD_TOOLS_VERSION` env.
- OkHttp is pinned to `4.12.0`, not 5.x — OkHttp 5's `-android` variant declares a `compileSdk 37` floor that our AGP version doesn't support yet. Don't bump it without checking that constraint again.

## Git workflow

- **No direct pushes to `main`.** Everything goes through a PR, even solo work.
- Version bumps (`versionName` + `versionCode` in `app/build.gradle.kts`) belong in the PR that should trigger a release.
- The release workflow (`.github/workflows/release.yml`) has a `check-version` gate: it only builds+signs+publishes if `versionName` increased since the last published release. A merge that doesn't bump it is a no-op for releases (no rebuild, no republish) — this is intentional, not a bug.
- `.github/workflows/ci.yml` builds the debug APK on every PR targeting `main` (check name: `build`) — **informational only**, not a hard merge gate. Classic branch protection *and* the newer Rulesets API both refused with "Upgrade to GitHub Pro or make this repository public" — a private repo on a free personal account can't enforce required status checks via GitHub's own merge-blocking. Don't re-attempt this without one of those two things changing; it's a real account-tier wall, not a config mistake.

## Known gaps (deliberately deferred, not forgotten)

- No automated tests yet — the original template's tests were deleted because they referenced the placeholder code they replaced.
- Search's live query-as-you-type UX hasn't been walked through end-to-end on-device.
- No manual Source-switcher UI in the player yet (the data model and automatic fallback-on-error both already support multiple sources per channel; just no on-screen "Source 2 of 3" control).
- `androidx.tv` (tv-foundation/tv-material) is a dependency but the UI currently uses plain Compose Foundation/Material3 widgets with manual focus handling, not the TV-specific component set — a deliberate risk-aversion choice made under time pressure, not a final decision.
- Paging 3 is a dependency but not actually wired into any query yet — "All Channels" (~11k rows) still loads as a plain `Flow<List<ChannelEntity>>`, which is the one list where this will eventually matter for low-end-device memory.
