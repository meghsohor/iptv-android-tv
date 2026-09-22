# Famelack.com — Findings & Android TV Feasibility

Source: exploration of https://famelack.com/ on 2026-09-22.

## What the site does

Free live TV, online radio, and live webcams aggregator, organized by country and category.

- **Landing view**: a 3D rotating globe in the center, colored by country, with a right-hand panel listing all countries (flag emoji + name), searchable via a "Filter Countries..." box.
- **Left drawer/nav**:
  - About, Favorites, Random Channel
  - Category list ("Explore"): All Channels, Top News, News, Music, Sports, Auto, Animation, Business, Classic, Comedy, Cooking, Culture, Documentary, Education, Entertainment, Family, General, Kids, Legislative, Lifestyle, Movies, Outdoor, Relax, Religious, Series, Science, Shop, Travel, Weather
  - Footer: FAQ, Privacy Policy, Feedback
- **Top bar**: Mode switch (**TV / Radio / Webcam**), Random Channel, Search.
- **Country selected** (e.g. `/tv/us`): right panel shows country name, capital city, local time, and a searchable ("Filter Channels...") list of channels — each with flag, name, and language code tag (ENG, FRA, SPA, etc.).
- **Channel selected** (e.g. `/tv/us/BTtTvg520N96K8`): embeds a live video player in place of the globe. Player controls: mute, volume slider, "LIVE" badge, captions toggle, Picture-in-Picture, fullscreen, close (X), add-to-favorites star.

## Technical findings (from network traffic)

- Channel/country data is fetched from a **public GitHub repo**: `raw.githubusercontent.com/famelack/famelack-data/main/tv/compressed/countries/<cc>.json` — one JSON file per country containing flag, name, category, language, and stream URL.
- Playback is a mix of:
  - **Direct HLS streams** (`.m3u8` manifests + `.ts` segments), played through a custom web video player.
  - **Embedded YouTube live streams** (`youtube-nocookie.com`) for channels that source from YouTube.
- The per-channel schema (country + category + language + stream URL) closely mirrors the well-known open-source **iptv-org** dataset (github.com/iptv-org/iptv), which is the common backbone for most free IPTV aggregator apps.

## Feature → Android TV mapping

| Famelack feature | Android TV equivalent |
|---|---|
| Country/category sidebar | Leanback-style browse rows (Jetpack Compose for TV — `androidx.tv:tv-material`), D-pad navigable |
| Country → channel list | Row/grid per country, or a "Browse by Country" catalog row |
| TV / Radio / Webcam mode switch | Top-level tabs |
| Search | Android TV global search (voice-enabled) or in-app search screen |
| Random Channel | Pick random entry from current filtered list |
| Favorites | Local persistence (Room or DataStore), surfaced as its own row |
| Live playback (HLS) | **Media3/ExoPlayer** — native HLS + DASH, adaptive bitrate, captions |
| Captions / PiP / fullscreen | ExoPlayer handles captions; PiP supported on Android TV; fullscreen is default player state |
| Mute/volume slider | Largely redundant — remote hardware volume keys cover this; consider dropping |
| Globe (center) | **Dropped per requirement** — replaced by country/category list-based browsing only |

## Open decision: data source

Building the catalog is the easy part; sourcing a legally clean, maintained channel list is the real risk area. Two options:

1. **Build on iptv-org's public dataset** (or a similar self-maintained aggregation) — explicitly maintained for this purpose, documents which streams are official/public.
2. **Reuse famelack's GitHub data files directly** — publicly readable, but license/terms should be checked before shipping an app built on someone else's dataset, especially before Play Store submission (Google has pulled IPTV aggregator apps over content-policy issues in the past).

Recommendation: lean toward (1) for anything intended for publication.

## Status

Feasibility confirmed. Not yet started on implementation — waiting on the additional feature list before scaffolding the Android TV project (planned stack: Kotlin, Jetpack Compose for TV, Media3/ExoPlayer).
