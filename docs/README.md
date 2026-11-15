# MovieBox Explorer

A dark **Kotlin + Jetpack Compose** Android app for browsing the MovieBox catalogue:
posters, metadata, every language track, season/episode counts, and playback.

It is also a **research / audit client**. The catalogue belongs to a third party that
does not publish a public API and does not license its content. Read the
[legal note](#17-legal--ethical-note) before you ship or publish anything.

> **Paths in this guide:** all `app/…`, `core/…` and `smoketest/…` paths are relative to
> the app root, `moviebox-explorer/`. This guide lives in `docs/README.md`; the UI design
> note is `docs/HOME_UI_REDESIGN.md`.

---

## Table of contents

0. [Start here — the 5-minute tour](#0-start-here--the-5-minute-tour)
1. [What this project is](#1-what-this-project-is)
2. [Requirements](#2-requirements)
3. [Quick start](#3-quick-start)
4. [Project layout](#4-project-layout)
5. [Architecture at a glance](#5-architecture-at-a-glance)
6. [The data layer](#6-the-data-layer)
7. [The home page](#7-the-home-page)
8. [Screens and navigation](#8-screens-and-navigation)
9. [Playback](#9-playback)
10. [Risk flags and adult content](#10-risk-flags-and-adult-content)
11. [Settings and host rotation](#11-settings-and-host-rotation)
12. [Build, test and verify](#12-build-test-and-verify)
13. [Troubleshooting](#13-troubleshooting)
14. [API reference](#14-api-reference)
15. [Known limitations](#15-known-limitations)
16. [Where to change what](#16-where-to-change-what)
17. [Legal / ethical note](#17-legal--ethical-note)
18. [Glossary](#18-glossary)

---

## 0. Start here — the 5-minute tour

### What you have

A single Android app, **MovieBox Explorer**, that browses the MovieBox catalogue and
plays episodes. It is written in Kotlin with Jetpack Compose and Material 3.

The workspace has two folders:

```text
playground-2/
├── moviebox-explorer/   # the app source
└── docs/                # this guide
```

### The 30-second version

- The app shows a **home page** with a hero carousel, platform chips, quick-browse
  shortcuts and horizontal content rails.
- It can **search**, open a **detail page** with every dub and season, and **play** an
  episode.
- The data comes from **two MovieBox backends**:
  - the **H5 web API** for the website catalog and **real playback URLs**;
  - the **mobile API** for the app catalog, dub lists and exact episode counts.
- Playback always uses the H5 API. The mobile API's play endpoint only returns a promo
  clip.

### The 5-minute tour

1. **Build and install** the app (see [§3](#3-quick-start)).
2. Open it. The **Home** tab loads the hero and rails.
3. Tap a poster. The **Detail** screen loads the title, its dubs and its seasons.
4. Pick a language, season and episode, then press **Play**. The app resolves a real
   stream and hands it to Media3.
5. Use the bottom bar to browse **Movies**, **TV**, **Animation** and **Search**.

### The mental model

```text
Compose screen
   ↓ calls
ViewModel (StateFlow)
   ↓ calls
MovieBoxRepository
   ↓ calls
MovieBoxApi (H5)  +  MobileApi (mobile)
   ↓
MovieBox servers / CDN
```

The UI never calls the APIs directly. It only talks to the repository. That is why the
data layer can be tested on a desktop with `:smoketest`.

### Where to go next

- New to the code? Read [§4 Project layout](#4-project-layout) and
  [§5 Architecture](#5-architecture-at-a-glance).
- Want to run it? Go to [§3 Quick start](#3-quick-start).
- Want to understand the API? Go to [§6 The data layer](#6-the-data-layer).
- Want to change something? Go to [§16 Where to change what](#16-where-to-change-what).
- Hit an error? Go to [§13 Troubleshooting](#13-troubleshooting).

---

## 1. What this project is

MovieBox has **two backends**:

| Backend | Host | What it gives |
|---|---|---|
| **H5 web BFF** | `https://h5-api.aoneroom.com` | Website catalog, search, detail, subtitles, **real playback manifests** |
| **Mobile BFF** | `https://apig.inmoviebox.com` | App catalog, every dub, per-season episode counts |

The app uses both:

- **H5 is the primary catalog and the only playback path.**
- **Normal title detail:** H5 first.
- **High-risk / unlisted title detail:** mobile API first — the H5 record is often
  partial or hidden, while the mobile record carries the full dub list.
- **Mobile is also a fallback** for search, staff and seasons when H5 is empty.
- Mobile `play-info` only returns a **promo clip**, so it is never used for playback.

The three Gradle modules are:

| Module | Type | Purpose |
|---|---|---|
| `:app` | Android application | Compose UI, ViewModels, navigation, settings, Media3 player |
| `:core` | Pure JVM library | API clients, signing, repository, models, risk policy, playback selection |
| `:smoketest` | JVM application | Runs the real data layer against the live API without a phone |

`:core` is a plain JVM module on purpose: the exact code the phone runs can be
executed on a desktop, which is how the data layer is verified.

---

## 2. Requirements

### Toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | **17** | `java -version` must show 17.x |
| Gradle | **8.14.6+** | Installed `gradle`; there is no wrapper in this repo |
| Android Gradle Plugin | **8.13.2** | Declared in the root `build.gradle.kts` |
| Kotlin | **2.4.21** | With the Compose and serialization plugins |
| Compose BOM | **2026.06.01** | Material 3, foundation, tooling |
| Android SDK Platform | **36** | `compileSdk = 36`, `targetSdk = 36` |
| Build tools | **36.0.0** | Or newer 36.x |
| Ktor | **3.6.1** | CIO engine |
| Coil | **2.7.0** | Image loading |
| Media3 | **1.2.1** | ExoPlayer + DASH |
| Device/emulator | **API 26+** | `minSdk = 26` |
| Network | Internet | The mobile API may be region-blocked |

App identity:

```kotlin
namespace = "com.example.movieboxexplorer"
applicationId = "com.example.movieboxexplorer"
minSdk = 26
targetSdk = 36
versionCode = 1
versionName = "1.0"
```

### Set the Android SDK path

```bash
export ANDROID_HOME=/path/to/Android/sdk
# or
export ANDROID_SDK_ROOT=/path/to/Android/sdk
```

Check it:

```bash
echo $ANDROID_HOME
ls $ANDROID_HOME/platforms
ls $ANDROID_HOME/build-tools
```

Install the required packages if they are missing:

```bash
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

### Create an emulator (optional)

```bash
sdkmanager "system-images;android-36;google_apis_playstore;x86_64"
avdmanager create avd -n moviebox -k "system-images;android-36;google_apis_playstore;x86_64" -d pixel_7
emulator -avd moviebox
```

If the machine has no KVM (`/dev/kvm` missing), the emulator will be very slow or refuse
to start. Use a physical device instead.

### Connect a device

```bash
adb devices
# enable USB debugging on the phone first
```

---

## 3. Quick start

### Build the APK

```bash
cd moviebox-explorer
gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run the data-layer tests

```bash
gradle :core:test
```

### Run the live smoke test

```bash
# Home / movies / series / animation / search
gradle :smoketest:run --args='"catalog"'

# Mobile BFF: search, detail, dubs, seasons
gradle :smoketest:run --args='"mobile"'

# H5 playback for a known title
gradle :smoketest:run --args='"play"'

# Ranking
gradle :smoketest:run --args='"rank"'

# Free-text search
gradle :smoketest:run --args='"black torch"'
```

### Open in Android Studio

Open the `moviebox-explorer` folder. Android Studio will detect the three modules and
the SDK. Press **Run** on the `app` configuration.

### Run on a device

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.movieboxexplorer/.MainActivity
```

### Logs

```bash
adb logcat -s MovieBox:* System.err:* AndroidRuntime:*
```

### Clean

```bash
gradle clean
```

`clean` removes `build/`, `app/build/`, `core/build/` and `smoketest/build/`. It does
not remove the APK in `dist/`.

---

## 4. Project layout

```text
moviebox-explorer/
├── settings.gradle.kts          # modules: :app, :core, :smoketest
├── build.gradle.kts             # AGP / Kotlin / Compose / serialization versions
├── gradle.properties            # JVM args, AndroidX, non-transitive R
├── .gitignore                   # build/, .gradle/, .kotlin/, *.apk, local.properties
│
├── app/                         # Android application
│   ├── build.gradle.kts         # Compose BOM, Coil, Media3, Navigation, Ktor
│   └── src/main/
│       ├── AndroidManifest.xml  # INTERNET permission, MainActivity
│       ├── java/com/example/movieboxexplorer/
│       │   ├── MainActivity.kt  # theme, nav host, bottom bar, settings dialog
│       │   ├── data/
│       │   │   ├── AppSettings.kt  # SharedPreferences: API host, adult flag, revision
│       │   │   └── Downloads.kt    # Android DownloadManager wrapper
│       │   └── ui/
│       │       ├── HomeScreen.kt       # modern home: hero, platforms, rails
│       │       ├── HomeViewModel.kt    # loads homeRows → HomeUiState
│       │       ├── CatalogScreen.kt    # catalog rows/grid + filter bar
│       │       ├── CatalogViewModel.kt # Movies/TV/Animation data
│       │       ├── SearchScreen.kt     # search + suggestions + tabs
│       │       ├── SearchViewModel.kt  # debounce, paging, suggestions
│       │       ├── DetailScreen.kt     # poster, dubs, seasons, episodes, risk
│       │       ├── DetailViewModel.kt  # loadTitle, episode selection, play
│       │       ├── DiagnosticsScreen.kt    # in-app test report
│       │       ├── DiagnosticsViewModel.kt # runs the checks
│       │       ├── DownloadsScreen.kt      # download queue + progress
│       │       ├── DownloadsViewModel.kt   # polls DownloadManager
│       │       ├── RankingScreen.kt    # Most Watched / ranking
│       │       ├── RankingViewModel.kt
│       │       ├── PlayerScreen.kt     # Media3/ExoPlayer
│       │       ├── PlaybackHolder.kt   # passes the resolved stream to the player
│       │       ├── Components.kt       # poster cards, rows, grids, skeletons
│       │       ├── AdultGate.kt        # one-time age gate + per-title block
│       │       └── theme/Theme.kt      # MovieBox dark/light color scheme
│       └── res/
│           ├── drawable/ic_launcher.xml
│           └── values/{strings.xml,themes.xml}
│   └── src/androidTest/java/com/example/movieboxexplorer/
│       └── DiagnosticsInstrumentedTest.kt  # emulator diagnostics report
│
├── core/                        # Pure JVM data layer
│   ├── build.gradle.kts         # Ktor CIO, kotlinx.serialization, coroutines
│   └── src/
│       ├── main/kotlin/com/example/movieboxexplorer/data/
│       │   ├── Models.kt            # SubjectBrief, Subject, DubVariant, Season, …
│       │   ├── Diagnostics.kt       # CheckStatus, CheckResult, DiagnosticsReport
│       │   ├── MobileModels.kt      # mobile response models
│       │   ├── MobileSigner.kt      # HMAC-MD5 request signer
│       │   ├── MobileApi.kt         # mobile BFF client
│       │   ├── MovieBoxApi.kt       # H5 BFF client
│       │   ├── MovieBoxRepository.kt# H5-first / mobile-fallback repository
│       │   ├── NuxtPayload.kt       # SSR `__NUXT_DATA__` reader
│       │   ├── Playback.kt          # PlayData, PlayStream, rendition selection
│       │   ├── RiskPolicy.kt        # RiskFlags / RiskLevel
│       │   ├── SearchRank.kt        # exact-title-first search ranking
│       │   ├── SeasonMath.kt        # season grouping / H5+mobile merge
│       │   └── TitleInfo.kt         # LanguageInfo, SeasonInfo, TitleInfo, Catalog*
│       └── test/kotlin/com/example/movieboxexplorer/data/
│           ├── MobileSignerTest.kt  # canonical string + HMAC vectors
│           ├── MobileModelsTest.kt  # blank numeric fields, direct subject shape
│           ├── PlaybackTest.kt      # MP4/DASH selection, promo rejection
│           ├── RiskPolicyTest.kt
│           └── TitleInfoTest.kt
│
├── smoketest/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/movieboxexplorer/smoke/Main.kt
├── scripts/
│   └── emulator-check.sh       # one-command emulator diagnostics
├── .github/workflows/android-ci.yml
├── dist/
│   └── app-debug.apk           # built debug APK
└── README.md                   # short pointer to ../docs/README.md
```

The documentation lives in `docs/` — this file and `docs/HOME_UI_REDESIGN.md`. From the
app root, that is `../docs/`.

### What each data-layer file does

| File | Responsibility |
|---|---|
| `Models.kt` | Shared JSON models. `LenientIntSerializer` accepts `""` for numeric fields. |
| `Diagnostics.kt` | Check statuses, individual results and the copyable text report. |
| `MobileModels.kt` | Mobile-specific models (`MobileSearchPage`, `MobileDetail`, `MobileDub`, `MobileSeason`, …). |
| `MobileSigner.kt` | Builds the mobile canonical request and the `x-tr-signature` / `x-client-token` headers. |
| `MobileApi.kt` | Calls the mobile BFF: bootstrap, search, get, season-info, dub-info, staff, recommend, comments, ranking, play-info. |
| `MovieBoxApi.kt` | Calls the H5 BFF: session, search, detail, play, captions, home, tab-operating, filter, trending, ranking, SSR pages. |
| `MovieBoxRepository.kt` | The only class the UI talks to. H5 first, mobile fallback, caching, title resolution, playback. |
| `NuxtPayload.kt` | Parses the website's server-rendered `__NUXT_DATA__` payload (used as an SSR fallback for hidden titles). |
| `Playback.kt` | `PlayData`, `PlayStream`, MP4/DASH selection, promo detection, lock reasons. |
| `RiskPolicy.kt` | `webHighRisk` / `isPornographic` / `isForbid` flags and their meaning. |
| `SearchRank.kt` | Client-side search ranking: exact title first, un-suffixed original before language variants. |
| `SeasonMath.kt` | Pure season grouping, split-season merging, H5+mobile resource merge, resolution merge. Unit-tested on the JVM. |
| `TitleInfo.kt` | `TitleInfo`, `LanguageInfo`, `SeasonInfo`, `CatalogRow`, `CatalogGrid`, `CatalogFilter`. |

---

## 5. Architecture at a glance

```text
┌──────────────────────────────────────────────────────────────────────┐
│                          :app (Compose UI)                           │
│                                                                      │
│  MainActivity ──► NavHost ──► Home / Catalog / Search / Detail /     │
│                               Ranking / Player                       │
│                                   │                                  │
│                               ViewModel                              │
│                                   │                                  │
└───────────────────────────────────┼──────────────────────────────────┘
                                    │
                         MovieBoxRepository
                                    │
              ┌─────────────────────┴─────────────────────┐
              │                                           │
     MovieBoxApi (H5)                            MobileApi (mobile)
     h5-api.aoneroom.com                         apig.inmoviebox.com
              │                                           │
     session, search, detail,                    HMAC-MD5 signed requests,
     play, captions, home,                       search, get, dubs,
     tab-operating, filter, SSR                  seasons, staff
              │                                           │
              └─────────────────────┬─────────────────────┘
                                    │
                         NuxtPayload (SSR fallback)
                                    │
                              MovieBox CDN
```

### The request lifecycle

**Browse a title**

```text
1. HomeViewModel → repository.homeRows(host)
2. MovieBoxApi.home(host) → GET /wefeed-h5api-bff/home?host=…
3. MovieBoxApi.trending(page=0) → Suggestions row
4. UI renders hero + platform strip + rails
5. User taps a poster → `resolveBestMatch(title)` picks the best version → DetailViewModel
6. repository.loadTitle(detailPath, subjectId)
7. MovieBoxApi.detail(...) → H5 record
8. For every dub: MovieBoxApi.detail(...) or MobileApi.detail(...)
9. repository.playEpisode(...) when the user presses Play
```

**Play an episode**

```text
1. DetailViewModel.play()
2. repository.playEpisode(detailPath, subjectId, season, episode)
3. MovieBoxApi.play(...) → GET /wefeed-h5api-bff/subject/play
4. PlayData contains MP4 and/or DASH renditions
5. selectPlaybackSource() drops promo clips and picks the best rendition
6. PlaybackHolder stores the URL + headers
7. PlayerScreen hands them to Media3/ExoPlayer
```

**Search fallback**

```text
1. SearchViewModel → repository.searchPage(...)
2. H5 search answers with items?
   ├─ yes → return them
   └─ no  → MobileApi.search(...) → normalise → return them
```

### 5.1 Application startup and state management

#### Startup

`MainActivity.onCreate()`:

1. Calls `MovieBoxTheme { Surface { MovieBoxApp() } }`.
2. `MovieBoxApp()` reads `AppSettings` (SharedPreferences).
3. If the age gate has not been accepted, it shows `AdultGateDialog`.
4. It creates the `MovieBoxRepository` once per settings revision.
5. It builds the `NavHost` with the routes in [§8](#8-screens-and-navigation).
6. It shows the `SettingsDialog` when the user opens Settings.

The repository is recreated when the API base or site host changes:

```kotlin
val revision by settings.revision.collectAsStateWithLifecycle()
key(revision) {
    val repository = remember(revision) {
        MovieBoxRepository(
            MovieBoxApi(settings.apiBase, settings.siteHosts(), "https://${settings.siteHost}")
        )
    }
    // NavHost ...
}
```

That is why changing a host in Settings immediately starts a new session.

#### State management

- Every screen has a `ViewModel` that exposes a `StateFlow`.
- The screen reads it with `collectAsStateWithLifecycle()`.
- The ViewModel uses `viewModelScope.launch { … }` for network calls.
- There are no XML layouts and no fragments — everything is Compose.
- `PlaybackHolder` is a small in-memory object that passes the resolved stream URL and
  headers from `DetailViewModel` to `PlayerScreen`.

#### Theme

`ui/theme/Theme.kt` defines the MovieBox dark scheme:

| Token | Color |
|---|---|
| `primary` | `#FF4D5A` (red) |
| `secondary` | `#22D3EE` (cyan) |
| `background` | `#0B0B0F` |
| `surface` | `#15151A` |
| `surfaceVariant` | `#1E1E26` |
| `onSurfaceVariant` | `#A8A8B3` |

The app always uses the dark scheme; the light scheme exists for completeness.

#### Settings

`SettingsDialog` edits:

- `api_base` — H5 API base;
- `site_host` — mirror used for `Origin` / `Referer` / SSR;
- `show_adult` — whether adult titles may be opened.

Saving bumps `AppSettings.revision`, which recreates the repository and the session.

---

## 6. The data layer

### 6.1 `MovieBoxApi` — the H5 web BFF

**Base URL:** `https://h5-api.aoneroom.com`  
**API prefix:** `/wefeed-h5api-bff`

#### Session

The server hands out an anonymous identity in the `x-user` **response header**:

```json
{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9…"}
```

Rules:

- Parse the JSON, then read the `token` field.
- Send it back as `Authorization: Bearer <token>`.
- While no token is held, send `X-Client-Token: "<unix seconds>,<md5(reversed seconds)>"`.
- **Never send both** `Authorization` and `X-Client-Token` — the server rejects it with
  `400 invalid token`.

`MovieBoxApi.ensureSession()` bootstraps with:

```http
POST /wefeed-h5api-bff/subject/search-suggest
Content-Type: application/json

{"keyword":"a","perPage":1}
```

#### Headers

| Header | Value | When |
|---|---|---|
| `Accept` | `application/json` | every request |
| `Content-Type` | `application/json` | POST requests |
| `X-Client-Info` | `{"timezone":"<zone>"}` | every request |
| `X-Request-Lang` | `en` | every request |
| `X-Client-Token` | `<epoch>,<md5(reversed epoch)>` | only before a session token exists |
| `Authorization` | `Bearer <token>` | after bootstrap |
| `Origin` | `https://<site-host>` | catalog/detail/play |
| `Referer` | `https://<site-host>/spa/videoPlayPage/movies/<detailPath>?id=<subjectId>&type=/movie/detail&lang=en` | detail/play |
| `X-No-High-Risk-Restrict` | `1` | **listing** calls only |

#### Host fallback

The site rotates mirrors. `MovieBoxApi.DEFAULT_SITE_HOSTS`:

```text
themoviebox.xyz
mzfi.me
netfilm.world
movieboxonline.net
moviebox.co
moviebox.ph
```

`themoviebox.xyz` is the canonical 36-section host. `mzfi.me` and `netfilm.world`
serve a reduced build (19 sections). `detail()` and `play()` try each host with a
matching `Referer`; a record is often **host-scoped** — the same slug can return `404`
on `themoviebox.xyz` and `200` on `mzfi.me`. `movie-box.co` appears in the mobile
`detailUrl`, but the H5 detail endpoint returns `404` there; `mzfi.me` is the working
mirror for the tested titles.

#### `X-No-High-Risk-Restrict` — listing only

The site's web build sends this header on every API call. It makes the server stop
hiding `webHighRisk` titles — but it behaves differently per endpoint:

| Endpoint | Without the header | With the header |
|---|---|---|
| `/subject/search` | 6 results, 0 unlisted | 10 results, 5 unlisted |
| `/home` | 384 titles, 0 unlisted | 440 titles, 56 unlisted |
| `/tab-operating` | 288 titles, 0 unlisted | 380 titles, 92 unlisted |
| `/subject/trending`, `/subject/detail-rec` | no unlisted titles | unlisted titles appear |
| **`/detail`** | resolves normally | **`404 TARGET_NOT_FOUND` / `"high risk"`** |
| `/subject/play` | resolves normally | resolves normally |

So `applyListingAuth()` sends the header, and `applyAuth()` does **not**.

#### H5 endpoints

| Method | Endpoint | Auth | Purpose |
|---|---|---|---|
| POST | `/subject/search-suggest` | client token | Bootstrap / suggestions |
| POST | `/subject/search` | Bearer | Search catalog |
| GET | `/detail?detailPath=` | Bearer + Referer | Full title record |
| GET | `/subject/play` | Bearer + Referer | **Real stream URLs** |
| GET | `/subject/caption` | Bearer + Referer | Subtitles |
| GET | `/subject/detail-rec` | Bearer | More like this |
| POST | `/subject/filter` | Bearer | Filtered grid pages |
| GET | `/subject/trending` | Bearer | Trending / Suggestions row |
| GET | `/home?host=` | Bearer + Origin | Home rows |
| GET | `/tab-operating?tabId=` | Bearer + Origin | Catalog rows |
| GET | `/ranking-list/content` | Bearer | Ranking |
| GET | `/subject/everyone-search` | Bearer | Hot searches |
| GET | `/web/...` SSR pages | browser UA | Fallback row/grid parsing |

#### `/subject/play` query

```text
subjectId=<id>
se=<season>
ep=<episode>
detailPath=<slug>
streamSignType=1
supportCodecs[hevc]=1
supportCodecs[h264]=1
```

Headers also include:

```text
x-vip-restrict: 0
x-no-high-risk-restrict: 0
x-source: moviebox
```

### 6.2 `MobileApi` — the mobile BFF

**Base URL:** `https://apig.inmoviebox.com`  
**Fallback:** `https://api.aoneroom.com`  
**API prefix:** `/wefeed-mobile-bff`

#### Bootstrap

```http
GET /wefeed-mobile-bff/tab/ranking-list
    ?tabId=0&categoryType=4516404531735022304&page=1&perPage=1
```

The token comes back in `x-user` as JSON:

```json
{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9…"}
```

#### Headers

| Header | Value |
|---|---|
| `user-agent` | `com.community.mbox.in/50020130 (Linux; U; Android 14; en_IN; Pixel 8; …)` |
| `accept` | `application/json` |
| `content-type` | `application/json` |
| `x-client-token` | `<epoch seconds>,<md5(reversed seconds)>` |
| `x-tr-signature` | `<timestamp ms>|2|<base64(HMAC-MD5(canonical request))>` |
| `x-client-info` | JSON device fingerprint |
| `x-client-status` | `0` |
| `authorization` | `Bearer <token>` after bootstrap |

`x-client-info` looks like:

```json
{
  "package_name": "com.community.mbox.in",
  "version_name": "4.0.03.0920.03",
  "version_code": 50020130,
  "os": "android",
  "os_version": "14",
  "device_id": "<random 32 hex>",
  "install_store": "official",
  "gaid": "<uuid>",
  "brand": "Google",
  "model": "Pixel 8",
  "system_language": "en",
  "net": "NETWORK_WIFI",
  "region": "IN",
  "timezone": "Asia/Calcutta",
  "sp_code": ""
}
```

`device_id` is generated once per `MobileApi` instance.

#### Mobile endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/subject-api/get?subjectId=` | Full title record + inline `dubs` |
| POST | `/subject-api/search` | Search |
| GET | `/subject-api/season-info?subjectId=` | Seasons and episode counts |
| GET | `/subject-api/dub-info?subjectId=` | Dub/subtitle list |
| GET | `/subject-api/staff-info?subjectId=` | Cast/crew |
| GET | `/subject-api/recommend?subjectId=` | Related titles |
| POST | `/subject-api/comment/list` | Comments |
| GET | `/subject-api/play-info?subjectId=&se=&ep=` | **Promo only** |
| GET | `/tab/ranking-list` | Bootstrap / ranking |

#### Mobile response quirks (important)

The mobile API is **not uniform**. `MobileApi` normalises these before the repository
sees them:

1. **Search items may be bare subjects**, not `{ "subject": {…} }`.
2. **`get` may return the subject directly under `data`**, not `data.subject`.
3. **Numeric fields can be `""`** (`duration`, `imdbRatingCount`, …).
   `LenientIntSerializer` reads them as `0`.
4. **The dub list is inline** in the direct `data` form, at the same level as `title`.
   `Subject.dubs` parses it. `dub-info` is only needed when it is absent.
5. **`play-info` returns the promo clip** on `macdn.aoneroom.com/other/…`, not a real
   stream.

### 6.3 `MobileSigner` — how the signature works

The canonical request is:

```text
{METHOD}
application/json
application/json
{BODY_LENGTH}
{TIMESTAMP_MS}
{BODY_MD5}
{CANONICAL_PATH_AND_QUERY}
```

Where:

- `METHOD` is uppercase.
- `BODY_LENGTH` is the byte length of the body, or empty when there is no body.
- `TIMESTAMP_MS` is the current time in milliseconds.
- `BODY_MD5` is the MD5 hex of the first 102,400 body bytes, or empty.
- `CANONICAL_PATH_AND_QUERY` is the path plus the **alphabetically sorted** query.
  Values are **not** URL-decoded.

The signature is:

```text
<timestamp_ms>|2|<base64(HMAC-MD5(canonical_request, key))>
```

The key is **double-base64 decoded**. The constants live in
`core/.../MobileSigner.kt`; do not copy them into other files.

The exact vectors are in `MobileSignerTest.kt`:

| Case | Result |
|---|---|
| Client token for epoch `1794634872` | `1794634872,7d35fdfc105e83cf3cf117655efbcecb` |
| GET `/subject-api/get?…` canonical string | sorted query, empty body fields |
| GET signature | `1794634872000|2|BzCBHtGHxTEznPaersNg0A==` |
| POST search signature | `1794634872000|2|BwRU8p1KAWfn/Dy+GOnJsg==` |

### 6.4 `MovieBoxRepository` — the single entry point

The UI never calls `MovieBoxApi` or `MobileApi` directly. It calls the repository.

#### Fallback table

| Operation | H5 call | Mobile fallback |
|---|---|---|
| Search | `POST /subject/search` | `mobile.searchSubjects()` |
| Search page | `POST /subject/search` | `mobile.searchPage()` |
| Detail | H5 first; if the record is `webHighRisk`/unlisted, use the mobile detail record | `mobile.detail(subjectId)` |
| Dubs | `subject.dubs` from H5 detail, **merged** with the mobile list | `mobile.dubInfo(subjectId)` (always merged) |
| Staff | `main.stars` from H5 detail | `mobile.staffInfo(subjectId)` |
| Seasons | H5 resource, **merged** with the mobile season list when the H5 count is shorter | `mobile.seasonInfo(subjectId)` |
| Related | `GET /subject/detail-rec` | `mobile.recommend(subjectId)` |
| Playback | `GET /subject/play` | **none** (mobile play is promo-only) |

#### Why merge instead of “H5 first, mobile only if empty”?

Both APIs can be **partial**:

- The H5 detail record can omit a dub (for example, the Hindi track) while the mobile
  `dub-info` endpoint lists it.
- The H5 resource for one dub can deliver a season as several entries — for example
  season 2 as `maxEp=4` and `maxEp=20` — or be shorter than the real upload. The mobile
  `season-info` endpoint reports the full `maxEp` (for example, 48).

The repository therefore:

1. merges the H5 and mobile **dub lists** (union by `subjectId`, preferring a non-empty
   `detailPath`);
2. merges the H5 and mobile **season resources** per season number, taking the larger
   episode count;
3. groups duplicate season entries by season number, so the UI shows one S2 row, not
   two;
4. uses `max(maxEp, allEp count, resolutions[].epNum)` as the episode count.

That is what fixes “S2 shows only 4 episodes” and “the Hindi dub is missing”.

The logic lives in `SeasonMath` (`seasonsOf`, `mergeResources`, `mergeResolutions`) so
it can be tested on the JVM. `SeasonMathTest` covers split seasons, duplicate seasons,
combined records, `allEp` unions, H5+mobile merges and resolution merges.

#### Main methods

| Method | What it does |
|---|---|
| `homeRows(host)` | `GET /home` + trailing Suggestions row; caches the result |
| `movieRows(host)` | `ONEROOM_MOVIE` rows + Suggestions; SSR fallback |

| `seriesGrid(host, page, filter)` | TV grid (channel 2) |
| `animatedGrid(host, page, filter)` | Animation grid (channel 1006) |
| `loadTitle(detailPath, subjectId)` | H5 record → every dub resolved → `TitleInfo` |
| `playEpisode(...)` | H5 play → `selectPlaybackSource` → `PlaybackResult` |
| `captions(...)` | Subtitles for an episode |
| `close()` | Closes both HTTP clients |

#### Caching

- `homeCache` — one `HomePage` per repository instance.
- `ssrCache` — subjects read from server-rendered pages.
- `MovieBoxApi.session` — one H5 token.
- `MobileApi.sessions` — one token per mobile host.

### 6.5 Models you will touch most

| Model | Fields that matter |
|---|---|
| `SubjectBrief` | `subjectId`, `subjectType`, `title`, `releaseDate`, `genre`, `imdbRatingValue`, `cover`, `detailPath`, `webHighRisk`, `isPornographic` |
| `Subject` | Same, plus `dubs`, `trailer`, `description` |
| `DubVariant` | `subjectId`, `lanName`, `lanCode`, `type` (0 dub / 1 subtitle), `detailPath` |
| `Season` | `se`, `maxEp`, `allEp`, `resolutions` |
| `Resource` | `seasons`, `source`, `uploadBy` |
| `TitleInfo` | `subject`, `stars`, `languages`, `referenceEpisodes`, `referenceSeasons`, `isForbidden` |
| `LanguageInfo` | `label`, `code`, `kind`, `episodes`, `seasons`, `resolutions`, `detailPath`, `subjectId` |
| `PlayData` | `streams`, `dash`, `hls`, `hasResource`, `vipLocked`, `playConfig` |
| `PlayStream` | `url`, `format`, `resolutions`, `quality`, `codec`, `signCookie`, `signHeaderKey`, `vipLocked` |

`LenientIntSerializer` is applied to numeric fields that the mobile API sometimes sends
as `""` (`subjectType`, `duration`, `imdbRatingCount`, `season`, `appointmentCnt`).

### 6.6 Full data model reference

| Model | Fields | Notes |
|---|---|---|
| `SubjectBrief` | `subjectId`, `subjectType`, `title`, `description`, `releaseDate`, `duration`, `genre`, `countryName`, `imdbRatingValue`, `imdbRatingCount`, `subtitles`, `hasResource`, `detailPath`, `cover`, `trailer`, `dubs`, `season`, `appointmentDate`, `webHighRisk`, `isPornographic` | The listing item used by rows, grids and search. |
| `Subject` | Same core fields + `dubs`, `trailer`, `description` | The detail record. |
| `DubVariant` | `subjectId`, `lanName`, `lanCode`, `original`, `type` (0 dub / 1 sub), `detailPath` | One language track. Each track is its own subject. |
| `Cover` | `url`, `width`, `height` | Poster / banner image. |
| `Trailer` | `videoAddress`, `cover` | Optional trailer. |
| `Season` | `se`, `maxEp`, `allEp`, `resolutions: List<Resolution>` | `maxEp` is the highest episode number; `allEp` is the raw list. |
| `Resolution` | `resolution`, `epNum` | Quality and how many episodes exist at it. |
| `Resource` | `seasons`, `source`, `uploadBy` | The episode structure. |
| `TitleInfo` | `subject`, `stars`, `languages`, `referenceEpisodes`, `referenceSeasons`, `isForbidden`, `resource` | Everything the detail screen renders. |
| `LanguageInfo` | `label`, `code`, `kind`, `isOriginal`, `episodes`, `seasons`, `resolutions`, `uploader`, `detailPath`, `subjectId`, `isPornographic` | One language track, resolved. |
| `SeasonInfo` | `season`, `episodes`, `resolutions` | Per-season count used by the detail screen. |
| `PlayData` | `streams`, `dash`, `hls`, `hasResource`, `vipLocked`, `limited`, `freeNum`, `playConfig`, `accessStrategy` | The H5 play response. |
| `PlayStream` | `id`, `url`, `format`, `resolutions`, `quality`, `width`, `height`, `codec`, `bitrate`, `duration`, `signCookie`, `signHeaderKey`, `vipLocked` | One rendition. |
| `BannerItem` | `id`, `title`, `image`, `url`, `subjectId`, `subjectType`, `subject` | One hero slide. |
| `OperatingSection` | `type`, `position`, `title`, `subjects`, `banner`, `opId` | One home/catalog row. |
| `HomePage` | `platformList`, `operatingList` | The home payload. |
| `Pager` | `page`, `nextPage`, `hasMore`, `totalCount` | Pagination. |
| `SearchPage` | `items`, `pager`, `counts` | Search response. |
| `FilterRequest` | `page`, `perPage`, `channelId`, `genre`, `country`, `year`, `sort`, `rate`, `classify` | Body for `POST /subject/filter`. |
| `CatalogFilter` | `genre`, `country`, `year`, `sort`, `rate`, `classify` | UI filter state. |
| `CatalogRow` | `title`, `items` | One horizontal catalog row. |
| `CatalogGrid` | `items`, `hasMore`, `nextPage` | One flat grid page. |
| `RiskFlags` | `unlisted`, `adult`, `forbidden` | The three independent risk flags. |

---

## 7. The home page

The home page is the real MovieBox home: the hero carousel, the platform strip, the
quick-browse shortcuts, and every content rail the website lists.

### 7.1 Data source

`HomeViewModel` calls:

```kotlin
repository.homeRows(host)
```

which calls:

```text
GET /wefeed-h5api-bff/home?host=<host>
GET /wefeed-h5api-bff/subject/trending?page=0
```

Both calls run in parallel and use the lightweight `X-Client-Token`, so the home path
does not wait for the session bootstrap. Measured cold load is ~2.6 s; the repository
cache makes a second open ~0 ms.

The full payload is also cached on disk (`filesDir/home_cache.json`). The next launch
renders the cached hero, platforms and every rail immediately, then refreshes in the
background and replaces the cache. No rows are dropped to make the first paint faster.

The home payload has:

| Part | Data |
|---|---|
| Hero | 15 `BANNER` slides |
| Platforms | 10 names (`Netflix`, `PrimeVideo`, …) |
| Rails | 36 operating sections |
| Trailing row | `Suggestions` from `/subject/trending?page=0` |

The website drops one row client-side (`opId=5675838686378840096`); the repository
drops it too.

### 7.2 State

```kotlin
data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val banners: List<SubjectBrief> = emptyList(),
    val platforms: List<String> = emptyList(),
    val rails: List<HomeRail> = emptyList(),
)
```

`HomeRail` is a title plus its items plus `HomeRailKind.STANDARD` or `COMING_SOON`.

`HomeViewModel` filters out:

- `BANNER` (used for the hero)
- `FILTER` (no subjects)
- `SPORT_LIVE` (no subjects)
- sections with an empty `subjects` list

### 7.3 What is on screen

```text
┌─────────────────────────────────────┐
│ [logo] MovieBox      search  star  ⚙ │
│ Movies · Series · Anime             │
├─────────────────────────────────────┤
│ [ 🔍 Search movies, series, anime… ]│
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │  FEATURED                     │   │
│ │  Hero artwork                 │   │
│ │  Title                        │   │
│ │  Movie · 2026 · ★ 7.1         │   │
│ │  [Play] [Details]             │   │
│ └───────────────────────────────┘   │
│         ● ○ ○ ○                     │
├─────────────────────────────────────┤
│ Streaming on                        │
│ [Netflix] [PrimeVideo] [Disney] …   │
├─────────────────────────────────────┤
│ Browse                              │
│ [Movies] [TV Shows] [Animation] …   │
├─────────────────────────────────────┤
│ Popular Series            See all   │
│ [poster] [poster] [poster] …        │
│ Popular Movie             See all   │
│ [poster] [poster] [poster] …        │
│ …                                   │
└─────────────────────────────────────┘
```

| Block | Composable | Behaviour |
|---|---|---|
| Top bar | `HomeTopBar` | Brand, search, ranking, settings |
| Search | `HomeSearchBar` | Taps open the Search screen |
| Hero | `HeroCarousel` | Auto-advance 5.2 s, page dots, Play/Details |
| Platforms | `PlatformStrip` | Read-only chips from `platformList` |
| Quick browse | `QuickBrowse` | Movies / TV / Animation |
| Rails | `HomeRailSection` | Title + **See all** + horizontal poster cards |
| Poster | `HomePosterCard` | 2:3 artwork, title/year/rating overlay, press scale |
| Footer | `FooterNote` | “You’re all caught up” |

### 7.4 States

| State | UI |
|---|---|
| Loading | Pulsing skeleton (search bar, hero, 3 rails) |
| Error | `ErrorBox` with **Retry** → `viewModel.refresh()` |
| Empty | “Nothing to show yet” + **Try again** |
| Ready | The full home |

### 7.5 Design notes

See [HOME_UI_REDESIGN.md](HOME_UI_REDESIGN.md) for the before/after analysis.

---

## 8. Screens and navigation

### 8.1 Bottom navigation

`MainActivity` defines:

| Route | Label | Screen |
|---|---|---|
| `home` | Home | `HomeScreen` |
| `movies` | Movies | `CatalogScreen` |
| `animation` | Animation | `CatalogScreen` |
| `tv` | TV | `CatalogScreen` |
| `search` | Search | `SearchScreen` |

The bottom bar is hidden on the detail and player routes.

### 8.2 Other routes

| Route | Screen | Notes |
|---|---|---|
| `ranking` | `RankingScreen` | Most Watched / ranking categories |
| `detail/{path}/{id}` | `DetailScreen` | `path` = detailPath, `id` = subjectId |
| `player/{kind}?title={title}&video={video}` | `PlayerScreen` | `kind` = `episode` or `trailer` |

### 8.3 Screen responsibilities

| Screen | ViewModel | What it does |
|---|---|---|
| Home | `HomeViewModel` | Hero + rails |
| Catalog | `CatalogViewModel` | Movies rows, TV/Animation grids |
| Search | `SearchViewModel` | Debounced search, suggestions, type tabs, paging |
| Detail | `DetailViewModel` | Title, dubs, seasons, episodes, risk, related, play |
| Ranking | `RankingViewModel` | Ranking categories + items |
| Player | — | Media3 playback |
| Settings | — | API host, site host, adult switch |

### 8.4 Search behaviour

- Debounce: 350 ms before search, 250 ms before suggestions.
- Minimum 2 characters.
- Type tabs come from the API `counts` (`All`, `Series`, `Movies`, `Music`).
- Infinite scroll: 28 items per page.
- Suggestions come from `POST /subject/search-suggest`.
- **Ranking:** the API returns language variants as separate records. The app ranks the
  exact title first, then the un-suffixed original, then the variants. Every record
  stays visible — the ranking only changes the order.
- The search field is focused automatically when the screen opens.
- The keyboard **Search** action and the trailing search button commit the query.
- **Recent searches** (last 8) are kept in memory and shown as chips.
- **Trending searches** and hot groups are shown as chips.
- Tapping a result opens that exact record, not a different language variant.

### 8.5 Catalog behaviour

| Kind | Content | Source |
|---|---|---|
| `MOVIES` | Rows | `repository.movieRows(host)` |
| `TV` | Flat grid + filter bar | `/web/tv-series` SSR + `POST /subject/filter` |
| `ANIMATED` | Flat grid + filter bar | `/web/animated-series` SSR + `POST /subject/filter` |

`CatalogViewModel` supports “load more” for grids only. The movie page is a
finite row set.

### 8.6 Screen-by-screen

#### Home

- Loads `repository.homeRows(host)`.
- Cached content is shown instantly on the next launch; a thin progress bar runs while
  it refreshes in the background.
- Renders the hero, platform strip, quick browse and rails.
- Hero auto-advances every 5.2 s.
- Tapping a poster opens the detail route with its `detailPath` and `subjectId`.
- **See all** on a rail opens the Movies catalog.

#### Catalog

- `CatalogViewModel` loads one of four kinds:
  - `MOVIES` — `movieRows` (rows);
  - `TV` — SSR grid + filter API;
  - `ANIMATED` — SSR grid + filter API;
- The filter bar is built from the page's own `filterItemsData`.
- Grid pages support **load more** (28 items per page).
- The top bar shows the screen title.

#### Search

- 350 ms debounce before search; 250 ms before suggestions.
- Minimum 2 characters.
- Type tabs from the API `counts` (`All`, `Series`, `Movies`, `Music`).
- 28 items per page; infinite scroll.
- Suggestions come from `POST /subject/search-suggest`.
- Tapping a result opens the detail route.

#### Detail

- Loads `repository.loadTitle(detailPath, subjectId)`.
- Before opening, `resolveBestMatch(title)` searches the mobile API and scores the
  candidates: exact title first, then prefix/substring; penalises re-edit, recap,
  compilation, summary, mini-anime, special, OVA, movie cut and “part”; prefers entries
  with a resource, a rating and `subjectType=2`. If the mobile search is blocked, the
  H5 search is used as the candidate source. If no better match is found, it opens the
  original poster.
- Shows:
  - poster, title, year, rating, genre, country;
  - synopsis;
  - **Audio language** picker (original track + English/Hindi dubs only — no subtitles);
  - **Language coverage** report: Original / English / Hindi with `FULL`, `PARTIAL`,
    `NOT AVAILABLE`, episodes/reference, missing counts and seasons;
  - hero shows the title’s cover artwork;
  - **Season** picker and episode list (split seasons are merged; the mobile season
    count is used when H5 is shorter);
  - complete / missing counts per track;
  - risk badges;
  - **More like this** — loaded from the API (`/subject/detail-rec`), not a local
    list; tapping a related poster runs the same best-match mechanism;
  - **Play** button;
  - the **Info** sheet shows the raw API data — subject fields and `resource.seasons`
    exactly as returned.
- If a title is adult and the setting is off, it shows `AdultTitleBlockedDialog`.
- Play resolves the selected track's own subject, not the parent.

Coverage example:

```text
LANGUAGE COVERAGE
Original Audio   FULL          25/25 episodes   S1:13 S2:12
Hindi dub        PARTIAL       24/25 · 1 missing
English dub      FULL          25/25 episodes   S1:13 S2:12
```

#### Ranking

- `repository.rankingPage()` loads categories and the first page.
- Selecting a category calls `repository.rankingContent(category.id)`.
- Shows a poster grid and a loading state while switching.

#### Player

- Media3/ExoPlayer.
- Plays one of:
  - a resolved episode (URL + headers from `PlaybackHolder`);
  - the title's trailer;
  - a demo clip when no trailer exists.
- The top bar has a back button; leaving releases the player.

#### Settings

- API base, site host, adult switch.
- Saving bumps the revision and recreates the repository.

#### Adult gate

- Shown once on first launch.
- **I am 18+ — continue** accepts; **Leave** exits the app.
- Per-title block can enable adult content and retry.

### 8.7 Diagnostics

Open **Settings → Run diagnostics**. The screen runs the checks below against the live
API and produces a plain-text report.

| Check | What it verifies |
|---|---|
| H5 session | bootstrap token |
| H5 home | home sections and platforms |
| H5 search | search works and the exact title ranks first |
| Mobile session | mobile token |
| Mobile search | mobile search finds a known title |
| Mobile detail | detail record loads |
| Mobile dubs | dub list is present |
| Mobile seasons | season/episode counts are present |
| Detail path | full title resolution (all languages) |
| Related | related-content API returns items |
| Playback | real MP4 vs promo |
| Home cache | cache file size |
| Image CDN | poster URL responds |

The report shows **PASS / WARN / FAIL**, duration and a short detail for each check.
Buttons: **Run again**, **Copy report**, **Share**, **Clear home cache**. The report
never includes tokens.

Live sample:

```text
PASS H5 session       1654ms  token 199 chars
PASS H5 home          4ms     36 sections, 10 platforms
PASS H5 search        1067ms  3 results, first=Black Clover S1-S2
PASS Mobile session   791ms   token 163 chars
PASS Mobile search    0ms     5 results, first=Black Clover
PASS Mobile detail    5ms     Black Clover
PASS Mobile dubs      2246ms  Original Audio, Arabic sub, … 11 tracks
PASS Mobile seasons   198ms   2 seasons
PASS Detail path      4624ms  11 languages, 180 episodes
PASS Playback         817ms   MP4 1080p
WARN Home cache       2ms     0 KB
PASS Image CDN        221ms   reachable
Summary: 11 passed, 1 warning, 0 failed
```

---

## 9. Playback

### 9.1 Flow

```text
DetailViewModel.play()
  → repository.playEpisode(detailPath, subjectId, season, episode)
    → MovieBoxApi.play(...)
      → GET /wefeed-h5api-bff/subject/play?...
    → data.lockReason()?
    → selectPlaybackSource(data, headers)
    → PlaybackHolder.set(source)
    → PlayerScreen(source.url, source.headers)
      → Media3/ExoPlayer
```

### 9.2 Renditions

| Kind | URL | Notes |
|---|---|---|
| Progressive **MP4** | `.mp4?sign=…&t=…` | Preferred; needs only a `Referer` |
| **DASH** | `.mpd` | Adaptive; needs the edge cache credential in `X-MB-Token` |
| **HLS** | `.m3u8` | Rare; treated like DASH |

`selectPlaybackSource`:

1. drops `vipLocked` renditions;
2. drops **promo** renditions;
3. picks the highest-resolution MP4;
4. if there is no MP4, picks the highest-resolution DASH/HLS;
5. adds `signCookie` under `signHeaderKey` for adaptive streams.

### 9.3 Promo detection

A real stream is a signed CDN URL:

```text
https://bcdnxw.hakunaymatata.com/resource/….mp4?sign=…&t=…
```

A promo clip is:

```text
https://macdn.aoneroom.com/other/2026/10/10/….mp4
```

`PlayStream.isPromo` recognises `macdn.aoneroom.com/other/` (or a `macdn` URL with no
`sign`). `selectPlaybackSource` skips it.

### 9.4 Headers

| Header | Value | Why |
|---|---|---|
| `Referer` | `https://<mirror>/` | CDN answers `429` without it |
| `User-Agent` | `Mozilla/5.0 (Linux; Android 12) …` | CDN/browser check |
| `X-MB-Token` | `signCookie` | DASH edge cache credential |

Media3 receives them through `DefaultHttpDataSource.Factory().setDefaultRequestProperties(...)`.

### 9.5 Errors

| Result | Meaning |
|---|---|
| `NoResource` | `hasResource=false` — episode was never uploaded |
| `Locked` | `vipLocked` or access strategy requires Premium |
| `Failed` | No playable rendition / network error |
| `Ready` | Playable source |

### 9.6 Downloads

The detail screen has a **Download** button. It resolves the selected episode through
the normal H5 play path, then hands the signed MP4 URL and headers to Android's system
`DownloadManager`.

- Open **Settings → Downloads** to see the queue.
- Each row shows progress, size, status and a play button when complete.
- Delete removes the download.
- The system notification shows progress and completion.

Limitations:

- Only **MP4** renditions are downloadable with `DownloadManager`; DASH manifests are
  not.
- Signed URLs expire (typically 2–6 hours). Large downloads must finish before expiry;
  otherwise download the episode again to get a fresh URL.
- Downloads live in the app's external files directory, so they are removed when the
  app is uninstalled or its data is cleared.

---

## 10. Risk flags and adult content

Three independent flags:

| Flag | Field | Meaning |
|---|---|---|
| `Unlisted` | `webHighRisk` | Hidden from the web UI; app-only. Not an adult flag. |
| `Adult` | `isPornographic` | Adult content. Only on the **detail** record. |
| `Forbidden` | `isForbid` | Record exists but must not be played. |

They can combine. `RiskPolicy.kt` exposes `RiskLevel` and `RiskFlags`.

### Age gate

- Shown once on first launch (`AppSettings.ageGateAccepted`).
- The catalogue mixes adult titles into ordinary rows; the flag is only known after a
  title is opened, so the app cannot filter them from a list.
- Per-title block: `AdultTitleBlockedDialog`.
- Settings switch: **Show adult content** (off by default).

### Why there is no list-level adult filter

Every listing endpoint reports `isPornographic = false`. Only the detail record carries
the flag. The app therefore gates at the point of opening.

---

## 11. Settings and host rotation

`SettingsDialog` (in `MainActivity`) exposes:

| Setting | Key | Effect |
|---|---|---|
| API base | `api_base` | Changes `MovieBoxApi` base URL |
| Site host | `site_host` | Changes `Origin`/`Referer` and SSR host |
| Show adult content | `show_adult` | Allows adult titles to open |

Changing the API base or site host bumps `AppSettings.revision`; `MainActivity` recreates
the repository and the session token.

Default hosts:

```kotlin
MovieBoxApi.DEFAULT_API_BASE = "https://h5-api.aoneroom.com"
MovieBoxApi.DEFAULT_SITE_BASE = "https://themoviebox.xyz"
MovieBoxApi.CANONICAL_HOST = "themoviebox.xyz"
MovieBoxApi.DEFAULT_SITE_HOSTS = listOf(
    "mzfi.me", "netfilm.world", "themoviebox.xyz",
    "movieboxonline.net", "moviebox.co", "moviebox.ph"
)
MobileApi.DEFAULT_BASE = "https://apig.inmoviebox.com"
MobileApi.ALT_BASE = "https://api.aoneroom.com"
```

---

## 12. Build, test and verify

### 12.1 Build

```bash
gradle :app:assembleDebug
gradle :app:assembleRelease     # unsigned release
```

### 12.2 Unit tests

```bash
gradle :core:test
```

Covers **53 unit tests**:

- mobile signer canonical string and HMAC vectors (6);
- lenient mobile JSON (4);
- playback selection, DASH credential, promo rejection (14);
- risk flags (6);
- title/language/season calculations (7);
- **search ranking** — exact title first, original before language variants (6);
- **season maths** — split seasons, duplicate seasons, combined records, `allEp` union,
  H5+mobile merge, resolution merge (10).

The season-maths tests are the regression guard for the “S2 shows only 4 episodes”
and “Hindi dub is missing” bugs. They run on the JVM, with no network.

### 12.3 Smoke tests

| Command | What it proves |
|---|---|
| `gradle :smoketest:run --args='"catalog"'` | Home rows, movies, series, animation, search |
| `gradle :smoketest:run --args='"mobile"'` | Mobile bootstrap, search, detail, dubs, seasons |
| `gradle :smoketest:run --args='"play"'` | H5 playback returns a real signed MP4 |
| `gradle :smoketest:run --args='"rank"'` | Ranking categories and items |
| `gradle :smoketest:run --args='"black torch"'` | Free-text search |
| `gradle :smoketest:run --args='"audit-catalog"'` | **Every listing source**: missing title/cover/detailPath/date/genre/rating |
| `gradle :smoketest:run --args='"sweep" "300"'` | **Random 300 titles** through the full detail path: dubs, seasons, errors |
| `gradle :smoketest:run --args='"titles"'` | Five known titles with a compact season/dub summary |
| `gradle :smoketest:run --args='"home-time"'` | Cold vs cached home load |
| `gradle :smoketest:run --args='"home-parts"'` | Session / home / trending timings |
| `gradle :smoketest:run --args='"diagnostics"'` | Full H5 + mobile + playback report |

### 12.4 Content coverage baseline

The last full run on the live catalogue:

```text
home:      458 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
movies:    398 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
tv:         36 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
animation:  36 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
ranking:    20 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
UNIQUE:   1117 records | missing title=0 cover=0 detailPath=0 date=0 genre=0 rating=0
listing records: 1304, unique subjects: 1117
```

Random detail sweep:

```text
sweep: candidates=1113 sample=300
sweep: ok=300/300 (100%) noDubs=0 zeroEpisodes=0 noSeasons=0 errors=0
```

Home timing:

```text
homeRows: cold=2578ms cached=0ms sections=37 suggestions=1
```

The cold load is the H5 home request; the persistent cache makes the next app launch
render immediately and refresh in the background.

That is the regression baseline. Run `audit-catalog` and `sweep` again after any data-layer
change and compare against these numbers.

Example live output:

```text
session: eyJhbGciOiJIUzI1NiIsInR5…
search: 10 results (HTTP 200)
detail: Black Torch dubs=0 subjectDubs=8 (HTTP 200)
  dub Original Audio (ja) = 6983322689440465144
  dub English dub (en)    = 3586957830244420632
  dub Hindi dub (hi)      = 2551537104618775712
seasons: 1
```

```text
=== play black-torch-ezMof4UGRj8 s1 e1 ===
  format      : MP4
  resolution  : 1080p
  codec       : h264
  duration    : 1425s
  url         : https://bcdnxw.hakunaymatata.com/resource/….mp4?sign=…&t=…
```

### 12.5 What to check after a change

1. `gradle :core:test` passes.
2. `gradle :app:assembleDebug` succeeds.
3. `gradle :smoketest:run --args='"catalog"'` still returns rows.
4. `gradle :smoketest:run --args='"mobile"'` still returns dubs (unless region-blocked).
5. `gradle :smoketest:run --args='"play"'` still returns a real MP4, not a promo.
6. `gradle :smoketest:run --args='"audit-catalog"'` still reports 0 missing metadata.
7. `gradle :smoketest:run --args='"sweep" "300"'` still reports 300/300 and 0 errors.

### 12.6 Automated emulator test

The diagnostics report can run without anyone touching the phone:

```bash
# one command (emulator must be running)
./scripts/emulator-check.sh

# or directly
gradle :app:connectedDebugAndroidTest
```

The instrumentation test is `app/src/androidTest/.../DiagnosticsInstrumentedTest.kt`. It:

- runs the full H5 + mobile + playback diagnostics;
- prints the report under the logcat tag `DiagnosticsTest`;
- writes it to `filesDir/diagnostics_report.txt`;
- CI copies it out with `adb shell run-as …`.

GitHub Actions is configured in `.github/workflows/android-ci.yml`. On `ubuntu-latest`
(KVM available) it:

1. builds the app and runs unit tests;
2. boots an API 36 emulator;
3. runs `connectedDebugAndroidTest`;
4. uploads `diagnostics_report.txt`, logcat and the test report as artifacts.

The workflow does not fail on region-specific mobile checks — the report is the
deliverable, and the summary shows which layer failed.

---

## 13. Troubleshooting

### 13.1 `403` / timeout on mobile

The mobile API is region-locked. Try:

- an allowed-region IP;
- `MobileApi.ALT_BASE` (`https://api.aoneroom.com`);
- H5 only (the app already falls back).

### 13.2 `407 Signature invalid`

Check, in order:

1. The canonical string includes the **complete URL**, including the sorted query.
2. Query values are not URL-decoded in the canonical string.
3. The timestamp is in **milliseconds**.
4. The body MD5 covers the exact bytes being sent.
5. The key is **double-base64 decoded**.
6. The method is uppercase.

`MobileSignerTest` is the reference.

### 13.3 `404 TARGET_NOT_FOUND` on detail

The record is host-scoped. Try another site host, and make sure the `Referer` matches:

```text
https://<host>/spa/videoPlayPage/movies/<detailPath>?id=<subjectId>&type=/movie/detail&lang=en
```

`MovieBoxApi.detail()` already tries every host.

### 13.4 Promo video instead of the episode

Check:

1. Using `h5-api.aoneroom.com` (not `apig.inmoviebox.com`);
2. Calling `/subject/play` (not mobile `play-info`);
3. The `Referer` contains the correct `detailPath` and `subjectId`;
4. The H5 token is fresh;
5. All query parameters are present;
6. `hasResource` is `true`.

`selectPlaybackSource` rejects `macdn.aoneroom.com/other/…` so the UI reports “no playable
rendition” instead of playing the promo.

### 13.5 Search returns unrelated titles

- Search is fuzzy. Verify the returned `title` before opening it.
- Use `subjectType = 2` for series, `1` for movies.
- Add `X-No-High-Risk-Restrict: 1` to see unlisted titles (H5 listing calls already do).
- The mobile search is also fuzzy; its first match is not always the title.

### 13.6 No dubs on a title

- The H5 detail record does not carry the dub array.
- The mobile `get` direct form does; `Subject.dubs` parses it.
- If `subject.dubs` is empty, the repository calls `mobile.dubInfo(subjectId)`.
- Some titles genuinely have only one track.

### 13.7 Home is empty

- `GET /home` may have failed.
- Check the site host and the API base in Settings.
- Look at `HomeUiState.error`; the UI shows a Retry button.
- `homeRows` caches an empty result; restart the app after fixing the host.

### 13.8 Build fails

| Error | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` / `ANDROID_SDK_ROOT` |
| `Unsupported class file major version` | Use JDK 17 |
| `Failed to find target with hash string 'android-36'` | Install Platform 36 |
| `Unresolved reference: …` | Sync Gradle / clean build |

### 13.9 Player shows a black screen

- Check `PlaybackHolder` has a URL; the player receives `source.url`.
- Check the stream URL has not expired (`t=` timestamp).
- Check the `Referer` header is present; the CDN returns `429` without it.
- Check the device can reach `bcdnxw.hakunaymatata.com`.
- Try another episode or quality.

### 13.10 App crashes on startup

- Check the age gate: **Leave** exits the app by design.
- Check `adb logcat -s AndroidRuntime:*` for the exception.
- A missing `ANDROID_HOME` or SDK 36 causes a build/install failure, not a runtime crash.
- An empty `AppSettings` host can make the first request fail; use **Settings** to reset
  the host to `themoviebox.xyz`.

### 13.11 Settings changes do not take effect

- Saving a host bumps `AppSettings.revision`; the repository is recreated.
- If the app was killed, the setting is read from SharedPreferences on next launch.
- A cached home page (`homeCache`) is per repository; a new repository clears it.

### 13.12 Mobile API returns 404

- You are probably using the website host (`themoviebox.xyz`).
- The mobile BFF is `apig.inmoviebox.com` (or `api.aoneroom.com`).
- The path must be `/wefeed-mobile-bff/...`.

### 13.13 Search returns nothing

- The H5 search hides `webHighRisk` titles unless the listing header is sent. The app
  sends it on listing calls.
- Search is fuzzy; try the exact title.
- Check the API base and site host in Settings.
- Try the mobile search with `gradle :smoketest:run --args='"mobile"'`.

### 13.14 Gradle daemon problems

```bash
gradle --stop
rm -rf .gradle
gradle :app:assembleDebug
```

### 13.15 A season shows too few episodes

- The API can split one season into several entries (for example S2 as 4 + 20).
- The repository groups entries by season number and takes the larger of `maxEp`,
  `allEp` and `resolutions[].epNum`.
- It also merges the H5 resource with the mobile `season-info`, so a partial H5 count
  (4) is replaced by the mobile count (24/48).
- If the mobile API is region-blocked, the H5-only count may still be low. Use an
  allowed-region IP to get the full count.

### 13.16 A dub is missing from the language list

- The H5 detail record can omit a dub (for example Hindi).
- The repository always merges the H5 `dubs` with the mobile `dub-info` list.
- If the mobile API is region-blocked, the H5 list is all the app has; the dub may stay
  missing until the mobile API is reachable.

### 13.17 A dub shows the wrong episode count

- Every dub is a separate subject with its own upload.
- The app fetches each dub's own record and season-info, not the parent's.
- If the H5 record is shorter than the parent, the mobile season-info is merged in.
- A dub that genuinely has fewer episodes (for example 24 of 48) is reported as
  missing the rest, not as a complete track.

---

## 14. API reference

### 14.1 Common response envelope

```json
{
  "code": 0,
  "message": "ok",
  "reason": "",
  "data": { }
}
```

`code == 0` means success. Errors use non-zero codes and a `message` / `reason`.

### 14.2 H5 endpoints

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/wefeed-h5api-bff/subject/search-suggest` | client token | `{keyword, perPage}` | `{items:[{word}]}` |
| POST | `/wefeed-h5api-bff/subject/search` | Bearer | `{keyword, page, perPage, subjectType}` | `{items, pager, counts}` |
| GET | `/wefeed-h5api-bff/detail?detailPath=` | Bearer + Referer | — | `{subject, stars, resource, isForbid}` |
| GET | `/wefeed-h5api-bff/subject/play` | Bearer + Referer | query (see §6.1) | `{streams, dash, hls, hasResource, vipLocked}` |
| GET | `/wefeed-h5api-bff/subject/caption` | Bearer + Referer | `id,se,ep,detailPath,lan` | `{captions}` |
| GET | `/wefeed-h5api-bff/subject/detail-rec?subjectId=` | Bearer | — | `{items}` |
| POST | `/wefeed-h5api-bff/subject/filter` | Bearer | `FilterRequest` | `{items, pager}` |
| GET | `/wefeed-h5api-bff/subject/trending` | Bearer | `tabId?,page,perPage` | `{subjectList, pager}` |
| GET | `/wefeed-h5api-bff/home?host=` | Bearer + Origin | — | `{platformList, operatingList}` |
| GET | `/wefeed-h5api-bff/tab-operating?tabId=` | Bearer + Origin | — | `{operatingList}` |
| GET | `/wefeed-h5api-bff/ranking-list/content` | Bearer | `id,page,perPage` | `{title, subjectList, pager}` |
| GET | `/wefeed-h5api-bff/subject/everyone-search` | Bearer | — | `{everyoneSearch, hot}` |

### 14.3 Mobile endpoints

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/wefeed-mobile-bff/tab/ranking-list` | `tabId,categoryType,page,perPage` | `x-user` token |
| POST | `/wefeed-mobile-bff/subject-api/search` | `{keyword,page,perPage}` | `{items,pager}` |
| GET | `/wefeed-mobile-bff/subject-api/get?subjectId=` | — | subject + `dubs` |
| GET | `/wefeed-mobile-bff/subject-api/season-info?subjectId=` | — | `{seasons}` |
| GET | `/wefeed-mobile-bff/subject-api/dub-info?subjectId=` | — | `{dubs}` |
| GET | `/wefeed-mobile-bff/subject-api/staff-info?subjectId=` | — | `{stars|items}` |
| GET | `/wefeed-mobile-bff/subject-api/recommend?subjectId=` | — | `{items}` |
| POST | `/wefeed-mobile-bff/subject-api/comment/list` | `{subjectId,page,perPage}` | `{items|list}` |
| GET | `/wefeed-mobile-bff/subject-api/play-info` | `subjectId,se,ep,isVip` | promo `PlayData` |

### 14.4 Error codes

| Code | Meaning | Fix |
|---|---|---|
| `400` | Params error / both tokens sent | Remove `X-Client-Token` when using `Authorization` |
| `401` | Token expired | Re-bootstrap |
| `403` | Region blocked | Use an allowed region / alternate host |
| `404` | Not found / wrong host / wrong slug | Try another host; check `detailPath` |
| `407` | Signature invalid | Recompute with the full URL and sorted query |
| `440` | Wrong host for this API | Use the correct BFF host |

### 14.5 Request examples

These are the shapes the clients send. Tokens are shortened; a real token is a JWT.

**H5 search**

```http
POST https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search
Authorization: Bearer <h5-token>
X-Client-Info: {"timezone":"Asia/Dhaka"}
X-Request-Lang: en
Content-Type: application/json

{"keyword":"Black Torch","page":1,"perPage":5,"subjectType":2}
```

**H5 detail**

```http
GET https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?detailPath=black-torch-ezMof4UGRj8
Authorization: Bearer <h5-token>
Referer: https://mzfi.me/spa/videoPlayPage/movies/black-torch-ezMof4UGRj8?id=6983322689440465144&type=/movie/detail&lang=en
Origin: https://mzfi.me
```

**H5 play**

```http
GET https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play
    ?subjectId=6983322689440465144
    &se=1&ep=1
    &detailPath=black-torch-ezMof4UGRj8
    &streamSignType=1
    &supportCodecs[hevc]=1
    &supportCodecs[h264]=1
Authorization: Bearer <h5-token>
Referer: https://mzfi.me/spa/videoPlayPage/movies/black-torch-ezMof4UGRj8?id=6983322689440465144&type=/movie/detail&lang=en
Origin: https://mzfi.me
x-vip-restrict: 0
x-no-high-risk-restrict: 0
x-source: moviebox
```

**Mobile search**

```http
POST https://apig.inmoviebox.com/wefeed-mobile-bff/subject-api/search
user-agent: com.community.mbox.in/50020130 (Linux; U; Android 14; en_IN; Pixel 8; …)
x-client-token: <epoch>,<md5>
x-tr-signature: <timestamp>|2|<base64(hmac-md5)>
x-client-info: {"package_name":"com.community.mbox.in", …}
x-client-status: 0
authorization: Bearer <mobile-token>
content-type: application/json

{"keyword":"Black Torch","page":1,"perPage":5}
```

**Mobile detail + dubs**

```http
GET https://apig.inmoviebox.com/wefeed-mobile-bff/subject-api/get?subjectId=6983322689440465144
GET https://apig.inmoviebox.com/wefeed-mobile-bff/subject-api/dub-info?subjectId=6983322689440465144
```

The `get` response may already contain `dubs`; if it does, `dub-info` is not needed.

---

## 15. Known limitations

| Area | Limitation |
|---|---|
| Mobile API | Region-blocked on some IPs; `recommend` and `comment/list` returned `404` live |
| Staff | `staff-info` returned an empty list for the tested title |
| Search | Fuzzy; the first match is not always the requested title |
| Ratings | `imdbRatingCount` is often missing in the mobile response |
| Home | Uses H5 `/home` only; no mobile home fallback |
| Home cache | First launch still needs the network; the cache is per install and may briefly show the previous host’s rows after a host change |
| Adult filter | Cannot be applied to a list — the flag is only on the detail record |
| Movies catalog | Now uses `movieRows` (fixed from the old series-grid bug) |
| Split seasons | Merged by season number; mobile season-info is used when H5 is shorter |
| Partial dub lists | H5 `dubs` are merged with the mobile `dub-info` list |
| Device test | No emulator was available in this environment; the UI was compiled, not launched |
| Downloads | MP4 only (DASH not supported); signed URLs expire after a few hours |
| Legal | Third-party catalogue, no public API, no license |

---

## 16. Where to change what

All paths below are relative to the app root, `moviebox-explorer/`.

| I want to… | Edit |
|---|---|
| Change the home layout | `app/.../ui/HomeScreen.kt` |
| Change home data loading | `app/.../ui/HomeViewModel.kt` |
| Change the color scheme | `app/.../ui/theme/Theme.kt` |
| Add a new screen | `MainActivity.kt` + a new `ui/*Screen.kt` + `ui/*ViewModel.kt` |
| Change the API base / mirrors | `core/.../MovieBoxApi.kt` (`DEFAULT_*`) |
| Change mobile hosts / app identity | `core/.../MobileApi.kt` (`DEFAULT_BASE`, `ALT_BASE`, constants) |
| Change signing | `core/.../MobileSigner.kt` |
| Add a mobile endpoint | `MobileApi.kt` + `MobileModels.kt` |
| Change the fallback order | `MovieBoxRepository.kt` |
| Change playback selection | `core/.../Playback.kt` |
| Change downloads | `app/.../data/Downloads.kt` + `ui/DownloadsScreen.kt` + `DetailViewModel.download()` |
| Change search result order | `core/.../SearchRank.kt` |
| Change poster best-match scoring | `core/.../SearchRank.bestMatch()` + `MovieBoxRepository.resolveBestMatch()` |
| Change risk gating | `core/.../RiskPolicy.kt` + `ui/AdultGate.kt` |
| Change diagnostics checks | `core/.../MovieBoxRepository.diagnostics()` + `ui/DiagnosticsScreen.kt` |
| Change automated emulator test | `app/src/androidTest/.../DiagnosticsInstrumentedTest.kt` + `.github/workflows/android-ci.yml` |
| Change settings | `app/.../data/AppSettings.kt` + `SettingsDialog` in `MainActivity.kt` |
| Add a unit test | `core/src/test/kotlin/.../data/` |

### 16.5 Cookbook

#### Add a new screen

1. Create `ui/MyScreen.kt` and `ui/MyViewModel.kt`.
2. Add a route in `MainActivity.kt` (`composable("my")`).
3. Add a bottom-bar destination if it is top-level.
4. Call the repository from the ViewModel; never call the API from the screen.

#### Add a new API endpoint

1. Add the method to `MovieBoxApi` or `MobileApi`.
2. Add its request/response models to `Models.kt` or `MobileModels.kt`.
3. Add a repository method that exposes it to the UI.
4. Add a unit test if it has parsing logic.

#### Change the API host

- H5: edit `MovieBoxApi.DEFAULT_API_BASE` or use the Settings dialog.
- Mobile: edit `MobileApi.DEFAULT_BASE` / `ALT_BASE` or the constructor.
- After a host change, the repository is recreated and a new session is fetched.

#### Change the home layout

- `HomeScreen.kt` is the whole home UI.
- `HomeViewModel.kt` maps `HomePage` to `HomeUiState`.
- Add a new block as an `item { … }` in the `LazyColumn`.

#### Add a new filter

1. Add the field to `CatalogFilter`.
2. Handle it in `CatalogViewModel.selectFilter`.
3. Include it in `FilterRequest` (omit it when null).
4. Add the option to the page's filter payload (SSR `filterItemsData`).

#### Debug a title that will not play

1. Open the detail page; check the track and episode.
2. Run `gradle :smoketest:run --args='"play" <detailPath> <subjectId> <season> <episode>'`.
3. If it returns a `macdn.aoneroom.com/other/...` URL, the request is getting a promo:
   check the host, Referer and token.
4. If it returns `NoResource`, the episode was never uploaded.
5. If it returns `Locked`, the title needs Premium.

#### Add a unit test

1. Put it in `core/src/test/kotlin/com/example/movieboxexplorer/data/`.
2. Use `kotlin.test` (`assertEquals`, `assertNull`, `assertTrue`).
3. Run `gradle :core:test`.

#### Change the theme

- `ui/theme/Theme.kt` has the color scheme.
- The app forces the dark scheme in `MainActivity`.

---

## 17. Legal / ethical note

This project reads **metadata and streams** from a third-party service that has no
public API and does not license its content. The sites behind the API are unauthorised
distributors of copyrighted works.

- The signing key and app identity in `MobileSigner` / `MobileApi` are reverse-engineered
  constants. Do not ship them in a public app.
- The `Referer` / `X-No-High-Risk-Restrict` behaviour that reveals unlisted titles is a
  weakness in the site's own gating. Treat it as a research finding, not a feature.
- If you intend to publish, get permission from the rights holders or use a licensed
  catalogue (TMDB / JustWatch / official APIs).

---

## 18. Glossary

| Term | Meaning |
|---|---|
| **BFF** | Backend-for-frontend — the API a client talks to |
| **H5** | The website / web-app API (`h5-api.aoneroom.com`) |
| **Mobile BFF** | The Android/iOS app API (`apig.inmoviebox.com`) |
| **SSR** | Server-side rendered HTML; the `__NUXT_DATA__` payload |
| **Dub** | An alternate audio language track |
| **Sub** | A subtitle track |
| **Manifest** | A DASH `.mpd` / HLS `.m3u8` playlist |
| **Rendition** | One playable version (quality/codec) of an episode |
| **Promo** | A short placeholder clip returned instead of a real stream |
| **High-risk / unlisted** | `webHighRisk` — hidden from the web UI |
| **Forbidden** | `isForbid` — must not be played |
| **Companion object** | Kotlin static-like container for constants/factories |

---

**End of guide.**