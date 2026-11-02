# AniReku — Android anime client: design spec

Status: draft for review · Target: Android 7.0+ (minSdk 24), phone + TV-friendly layout
Scope rule: this app is a **client**. It ships no copyrighted video, no indexer and no scraped catalogue.
It talks only to sources the user configures, plus optional public metadata (AniList).

---

## 0. Non-goals (explicit)

- No scraped copy of any specific app's catalogue. The recon report (`~/work/animex/docs/REPORT.md`) documents
  why and what is reusable; the reusable parts are listed in §2.
- No request signing / integrity bypass for third-party backends.
- No ad SDKs of any kind. There is no ads module in this codebase.
- No bundled streams, no bundled artwork beyond the app icon.

---

## 1. What we take from the recon (and what we don't)

| From the analysed app | What we do |
|---|---|
| Media3 player: HLS + progressive, per-host UA/Referer/Authorization headers | Reimplemented independently with stock Media3 APIs |
| `HdCheck` (an "HD" track below 1000×600 is not HD) | Reimplemented as a track validator, threshold configurable |
| Resumable segmented HLS download | Reimplemented on WorkManager + a foreground service |
| `get_config(version_code)` → maintenance / new version / notes | Same *pattern*, our own endpoint + our own release feed |
| Bookmark per anime + History + Resume position (SQLite) | Same data shape, implemented with Room |
| Tabs: Latest / Ongoing / Complete / Popular / Studio / Genre+A–Z+Year / Search | Same information architecture, drawn from AniList |
| Episode list two-column with SD/HD/FHD labels, prev/next, next-episode countdown | Included |
| Cast (Chromecast) / AirPlay | Cast only (Media3 Cast) — out of scope for v1 |
| Ads / points / Pro / rewarded unlock | Not present at all |
| Signing check / Play Integrity / native guard / cert pinning | Not present at all |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ :app            Compose UI, navigation, ViewModels          │
├─────────────────────────────────────────────────────────────┤
│ :core:model     Anime, Episode, SourceRef, StreamLink, ...   │
│ :core:database  Room: bookmarks, history, resume, downloads  │
│ :core:player    ExoPlayer wrapper, track/HdCheck, downloader │
│ :core:network   OkHttp + per-source header policy, cache     │
│ :core:update    Release feed, APK download, installer        │
│ :core:ui        Design system (Material 3, dark/AMOLED)      │
├─────────────────────────────────────────────────────────────┤
│ Source SPI (interface, one module per source)                │
│   :source:anilist   (metadata + catalogue, public API)       │
│   :source:userfeed  (user-supplied JSON / M3U / RSS)         │
│   :source:webdav    (user's own storage)                     │
└─────────────────────────────────────────────────────────────┘
```

Gradle multi-module, Kotlin 2.x, KSP, no kapt. Version catalog (`gradle/libs.versions.toml`).
CI builds a debug APK and a release APK signed with a repo-local keystore whose password lives in
GitHub Actions secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`).

### 2.1 Source SPI — the only data path

```kotlin
interface AnimeSource {
    val id: String                    // "anilist", "userfeed", ...
    val capabilities: Set<Capability> // CATALOGUE, EPISODES, STREAMS
    suspend fun search(query: String, page: Int): PagedResult<AnimeSummary>
    suspend fun trending(page: Int): PagedResult<AnimeSummary>
    suspend fun seasonal(season: Season, year: Int, page: Int): PagedResult<AnimeSummary>
    suspend fun byGenre(genre: String, page: Int): PagedResult<AnimeSummary>
    suspend fun detail(animeId: String): AnimeDetail
    suspend fun episodes(animeId: String): List<Episode>
    suspend fun streams(episode: Episode): List<StreamLink>   // may return empty
}
```

`StreamLink = (url, quality, headers: Map<String,String>, mimeType, isHls)`.
Rules:
- Headers are per-link, set by the source, never read from disk.
- A source that returns no streams degrades gracefully: the episode page shows "No stream source configured"
  with a link to Settings → Sources.
- Streaming url is fetched at play time, with the source's short-lived cache honoured.
- Timeouts: 15 s connect, 30 s read, 2 retries on IO. All source calls run on `Dispatchers.IO`.

### 2.2 Sources in v1

**`anilist`** (metadata only — verified live: `POST https://graphql.anilist.co` answers)
- Provides: catalogue, search, trending, seasonal, genre/year filters, studio filter, episode counts,
  titles (romaji/english/native), synopsis, cover art, banner, score, status, airing schedule.
- Rate: 90 req/min. Cache TTL 6 h for lists, 24 h for details; ETag/If-None-Match on repeated calls.
- Does not provide streams. With only this source enabled the app is a **tracker + browser** — that is a valid
  v1 state, not an error.

**`userfeed`** (streams the user supplies)
- Input: a JSON file the user places in `Download/anireku/sources.json`, or a URL to such a file.
- Schema (documented in the app and in `docs/sources.md`):
  ```json
  {
    "name": "My library",
    "entries": [
      { "anilistId": 16498,
        "episodes": [
          { "number": 1, "title": "To You, in 2000 Years",
            "streams": [
              { "url": "https://…/ep1/index.m3u8", "quality": "1080p",
                "headers": { "Referer": "https://…/" } }
            ] }
        ] }
    ]
  }
  ```
- The app never invents URLs. It reads what the user put there.

**`webdav`** (optional, v1.1): the user's own Nextcloud/WebDAV folder of video files, listed as episodes.

### 2.3 Player

- `ExoPlayer` via `media3-exoplayer` + `media3-exoplayer-hls` + `media3-ui` (`PlayerView` inside Compose
  `AndroidView`).
- Media source selection: `.m3u8` (or `mimeType == application/x-mpegURL`) → `HlsMediaSource.Factory`,
  everything else → `ProgressiveMediaSource.Factory`. Both fed by an `OkHttpDataSource.Factory` built from the
  link's headers, with `setDefaultRequestProperties` and per-request `User-Agent`/`Referer` overrides.
- Track selection: `TrackSelectionParameters` with `maxVideoSize` from the quality menu; "Auto" clears it.
- Overlay controls (Compose): play/pause, ±10 s, seek bar with chapter-less preview, quality menu, speed
  (0.5–2.0, remembers per app), lock, fullscreen/rotate, next-episode button with a 10 s countdown when the
  final 30 s are reached.
- Gestures: left half vertical = brightness, right half = volume, double-tap left/right = ±10 s, long-press = 2×,
  horizontal drag = scrub.
- `HdCheck`: on first rendered frame, read `Player.getVideoFormat()`. If a link labelled `HD`/`FHD` reports
  width < 1000 or height < 600, mark that link as mislabelled for the session, disable it in the quality menu,
  and pick the next best. Threshold in `PlayerSettings`.
- Resume: position saved every 5 s and on pause/stop to `resume` table; on open, offer "Resume at 12:34" chip.
- Downloads: `DownloadService` (foreground, `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK|DATA_SYNC`),
  `media3-exoplayer-dash`'s download manager for progressive, custom segment walker for HLS with resume from the
  last completed segment. Target dir: app-specific external files, or user-picked SD card via SAF.
- Errors: 403/404 from a stream → toast "Source refused this link" + fall back to the next link; repeated failure →
  offer "Report broken link" that writes a local diagnostic (no telemetry, no upload).

### 2.4 Data model (Room)

```sql
anime(id TEXT PRIMARY KEY, source TEXT, title_romaji TEXT, title_english TEXT, title_native TEXT,
      cover TEXT, banner TEXT, synopsis TEXT, genres TEXT, studio TEXT, year INTEGER,
      status TEXT, episodes_total INTEGER, score REAL, updated_at INTEGER)
bookmark(id INTEGER PRIMARY KEY AUTOINCREMENT, anime_id TEXT NOT NULL, added_at INTEGER,
         UNIQUE(anime_id))
history(id INTEGER PRIMARY KEY AUTOINCREMENT, anime_id TEXT, episode_number INTEGER,
        episode_title TEXT, watched_at INTEGER, position_ms INTEGER, duration_ms INTEGER)
resume(anime_id TEXT, episode_number INTEGER, position_ms INTEGER, updated_at INTEGER,
       PRIMARY KEY(anime_id, episode_number))
download(id TEXT PRIMARY KEY, anime_id TEXT, episode_number INTEGER, url TEXT, quality TEXT,
         state TEXT, bytes_done INTEGER, bytes_total INTEGER, path TEXT, created_at INTEGER)
source_config(source_id TEXT PRIMARY KEY, enabled INTEGER, params TEXT)
```

Indexes on `history(watched_at DESC)`, `resume(anime_id)`, `bookmark(added_at DESC)`.

### 2.5 Screen inventory

1. **Home** — Continue watching row, Trending, Seasonal, per-source "Latest" if the source exposes it.
2. **Browse** — tabs: Ongoing / Complete / Popular / Genres / A–Z by year; studio filter.
3. **Search** — query box with debounce, results grid, filter chips (type, year, sort).
4. **Detail** — banner header, title block, score, synopsis (expandable), genres, bookmark button,
   episode list (two-column, quality badges per available link), "Watch next" card, related anime.
5. **Player** — as §2.3.
6. **Library** — Bookmarks / History / Downloads tabs, sortable, multi-select delete, storage bar.
7. **Settings** — Sources (enable/configure/import/export), Player (default quality, HdCheck toggle, speeds),
   Appearance (dark/AMOLED/system, accent), Storage (dir, clear cache), Updates, About (version, licences, source code).
8. **Onboarding** — three cards: what the app is, pick a source, privacy (nothing leaves the device except
   the sources you enabled).

### 2.6 Update system

Two independent layers, both optional:

**a) App self-update (APK)**
- `GET <releaseFeedUrl>` where the feed is the GitHub Releases API for this repo (default; user-overridable in
  Settings). Reads `tag_name`, `assets[].browser_download_url`, `body`.
- Semver compare with the installed `versionName`. Higher → in-app card on Home and in Settings → Updates,
  showing release notes.
- Download with `DownloadManager` to app cache, verify SHA-256 against the `sha256sums.txt` asset published by CI,
  then `PackageInstaller.Session` with `REQUEST_INSTALL_PACKAGES`. Fallback: `ACTION_INSTALL_PACKAGE` via
  FileProvider if the session API fails.
- Never auto-install silently. Always one tap by the user.
- Channel selector: Stable / Prerelease (prereleases are filtered out of the Stable feed).

**b) Remote config (`config.json`)**
- `GET <configUrl>` — again a file in *this* repo (GitHub raw). Overridable in Settings.
- Shape:
  ```json
  { "schema": 1, "min_version_code": 0, "latest_version_code": 0,
    "maintenance": { "enabled": false, "message": "", "link": "" },
    "notice": { "enabled": false, "message": "", "url": "", "dismissible": true },
    "flags": { "enable_cast": false, "hd_check": true } }
  ```
- `min_version_code > installed` → blocking screen with "Update" button (opens the same updater).
- `maintenance.enabled` → full-screen message; the app is still usable offline (library/history/downloads).
- Fetched at cold start (2 s timeout, cached 12 h), and on app foreground if cache older than 12 h.
- Failure is non-fatal: use last cache, or defaults.

### 2.7 Privacy & security posture

- No analytics, no crash reporter, no ad IDs, no identifiers at all.
- Network permissions only; no contacts/location/phone state.
- Cleartext disabled in the network security config; self-update feed must be HTTPS.
- No WebView in v1 (episode source sniffing is out of scope by design).
- Exported components: only the launcher activity and the install receiver.
- `allowBackup=false` for the DB containing library data unless the user opts into encrypted export
  (Settings → Backup: writes a `.anireku` JSON the user stores themselves).

### 2.8 Testing

- Unit: source contract tests with a fake source; HdCheck; update version compare; config parsing;
  resume position math.
- Room: migration tests + DAO tests (in-memory).
- Player: `TestExoPlayer` not used; instead a `PlayerViewModelTest` with a fake `ExoPlayer` façade.
- Integration: `MockWebServer` serving an HLS playlist fixture and a 4 s video to prove the source → player path.
- CI: `./gradlew assembleDebug test` on every push; release job attaches `app-release.apk` + `sha256sums.txt`
  to the GitHub release.
- Manual checklist in `docs/TESTPLAN.md` per release (device matrix: Android 7 / 10 / 14, one low-RAM device).

---

## 3. Delivery phases

| Phase | Output | Definition of done |
|---|---|---|
| P0 | Repo, Gradle multi-module skeleton, CI, keystore secrets, CONTRIBUTING, LICENSE (GPL-3.0) | `./gradlew assembleDebug` green in CI; first signed release v0.1.0 exists |
| P1 | Room + anilist source + Home/Browse/Search/Detail + bookmark/history | Browse/search works offline-cached; bookmark persists across restart |
| P2 | Player + HdCheck + resume + settings | Plays a local HLS fixture end-to-end on device |
| P3 | userfeed + webdav sources, Downloads service | Import JSON → episode plays; download resumes after kill |
| P4 | Self-update + remote config + maintenance screen | v0.2.0 offers v0.3.0, downloads, verifies hash, installs |
| P5 | Polish: onboarding, AMOLED theme, TV layout, accessibility, localization (id/en) | TalkBack pass on every screen; Indonesian + English strings complete |

---

## 4. Decisions needing the owner's call (defaulted)

1. **Repo name / owner** → default `anireku` under the GitHub account you link; GPL-3.0.
2. **minSdk** → 24 (covers Android 7+; the analysed app is 32, we go wider on purpose).
3. **Language of UI** → Indonesian default, English second (strings are complete in both).
4. **Metadata provider** → AniList only (public, documented, no key). MAL optional later.
5. **Streams** → none bundled; `userfeed` + `webdav` only.
6. **Signing** → CI-held keystore; the keystore itself committed **encrypted** to private storage, not the repo.

---

## 5. Risques

- **Build env**: this VM has no Android SDK and `dl.google.com` repo paths need verification (maven.google.com
  answers 200, the two `repository2-*.xml` paths tried returned 404). Mitigation: install cmdline-tools from the
  canonical URL once located, or build exclusively in GitHub Actions (ubuntu-latest has the SDK preinstalled).
  The spec assumes CI-first builds so the local toolchain is not a blocker.
- **AniList rate limits** — mitigated by caching + single-flight.
- **User-supplied stream URLs rot** — the UI must degrade gracefully and never present a frozen spinner.
