# Home page UI — analysis and redesign

## 1. What the Android app's home page was

The home screen was a catalog browser, not a home page:

- a plain `TopAppBar` (logo square, "MovieBox", a star action and a text "Settings");
- a search bar;
- a three-tab segmented control: **Movies / TV Shows / Animation**;
- a filter-chip rail for the TV and animation tabs;
- a `HorizontalPager` that rendered either the movie page's rows or a two-column poster grid.

What it did **not** have:

- no hero / featured banner, even though the API returns 15 banner slides;
- no content rails — the home payload carries 36 sections (`Popular Series`,
  `Popular Movie`, `Nollywood Movie`, `Adult Animation`, `Epic Fantasy`, `Comedy`,
  `Teen Romance`, `Superhero Series`, `K-Drama`, `C-Drama`, `Anime[English Dubbed]`,
  `Coming Soon`, `Suggestions`, …);
- no platform strip, even though the payload lists 10 platforms;
- no visual hierarchy: everything was the same weight, with hard-coded colours and
  a generic loading spinner;
- no empty state, skeleton, or press feedback.

## 2. What the real home page contains

Read live from `GET /wefeed-h5api-bff/home` (canonical host):

| Part | Data |
|---|---|
| Hero | 15 `BANNER` slides (`image`, `title`, `subject`, `subjectId`, `subjectType`) |
| Platforms | 10 (`Netflix`, `PrimeVideo`, `Disney`, `AppleTV`, `Viu`, `Hulu`, `Zee5`, `Vivamax`, `Hoichoi`, `Showmax`) |
| Rails | 36 operating sections + the trailing `Suggestions` row from `/subject/trending?page=0` |
| Hidden row | the website drops `opId=5675838686378840096` client-side; `MovieBoxRepository.homeRows` drops it too |

Empty duplicate rows (for example the empty `Popular Movie` / `K-Drama` entries) are
removed before rendering.

## 3. The redesigned home page

A modern Material 3 take on the real home, in `moviebox-explorer`:

1. **Brand top bar** — gradient play mark, "MovieBox" wordmark, "Movies · Series · Anime"
   tagline, and search / Top 10 / settings actions. Transparent, so the ambient brand
   glow shows through.
2. **Search pill** — 56 dp, 28 dp corners, on a `surfaceVariant`.
3. **Hero carousel** — full-bleed-ish pager with 20 dp side padding, 12 dp page spacing,
   26 dp rounded corners, a bottom gradient scrim, `FEATURED` pill, `UNLISTED` badge,
   title, type/year/rating meta, **Play** and **Details** buttons, animated page dots,
   and auto-advance every 5.2 s (paused while the user is scrolling).
4. **Streaming on** — the platform names from the payload as a horizontal chip rail.
5. **Browse** — four gradient shortcut cards: Movies, TV Shows, Animation, Midnight.
6. **Content rails** — every non-empty home section in the website's order, each with
   the section title, a **See all** action, and 140 dp poster cards. Cards use 18 dp
   corners, a bottom gradient with the title / year / rating, an `UNLISTED` badge when
   applicable, and a press-scale interaction.
7. **States** — pulsing skeleton while loading, a retryable empty state, and the shared
   error box.

### Files

- `app/src/main/java/com/example/movieboxexplorer/ui/HomeViewModel.kt` — loads
  `MovieBoxRepository.homeRows(host)` and maps it to `HomeUiState`
  (`banners`, `platforms`, `rails`, loading/error).
- `app/src/main/java/com/example/movieboxexplorer/ui/HomeScreen.kt` — the new UI
  (top bar, search, hero, platform strip, quick browse, rails, cards, skeleton).
- `app/src/main/java/com/example/movieboxexplorer/MainActivity.kt` — passes the
  `onBrowse` route callback used by the quick-browse cards and "See all".

## 4. Verification

```bash
gradle :app:assembleDebug   # BUILD SUCCESSFUL
gradle :core:test           # data-layer tests pass
```

The emulator could not be started in this environment (`/dev/kvm` is absent and the
software-rendered system image needs more free disk than is available), so the final
visual check should be done on a device or an emulator with hardware acceleration.