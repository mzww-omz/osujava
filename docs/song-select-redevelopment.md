# Song Select redevelopment audit (2026-09-28)

## Current architecture and ownership

The entry point is `OsuJavaGame` → `SongSelectScreen`. The Library exposes immutable
`BeatmapSet` / `BeatmapDifficulty` records with resolved local paths. Import stays in
`BeatmapArchiveImporter`; neither the browser nor renderer opens archives.

| Responsibility | Current owner | Decision |
| --- | --- | --- |
| Selection, stable identity, filtering, sorting, grouping, Random history | `SongBrowserModel` | Retain; move screen-side relative navigation here |
| Scroll target, critically damped spring, interrupted expansion, hover | `SongSelectCarousel` | Retain independent UI delta, document constants; do not assert unverified Stable timing |
| Visible geometry and hit priority | `SongSelectScreen.layoutRows/hitRow` | Extract shared immutable row geometry and hit testing |
| Row width, skin body size, pitch | Screen + carousel | Centralize Song Select metrics with explicit units |
| Input | Anonymous InputAdapter + render-time click handling | Separate input dispatch and update from drawing; retain existing shortcut priorities |
| Row drawing and label styling | Screen | Extract renderer consuming immutable visible presentations, no browser reference |
| Selected metadata | Computed lazily inside drawMetadata | Extract pure details snapshot, prepare on selection changes |
| Skin resolution | `SongSelectSkinAssets`, `SkinAssetResolver` | Retain provider ordering, SD/@2x, authored transparency and procedural fallback |
| Chrome | `SongSelectChrome`, `SongSelectToolboxLayout` | Retain distinct image/content/interaction bounds |
| Thumbnails/background | `BeatmapThumbnails`, screen fade | Retain bounded cache and cover crop; move prefetch out of row drawing |
| Scores | `ScoreBrowserModel`, `SongSelectScoreSnapshot` | Retain local ranking, grade badges, storage revision cache |
| Text | `SmoothUiFont`, `UiTextFit`, row content cache | Retain Unicode/grapheme fitting and bounded raster cache |

The screen currently has 939 lines and owns both model coordination and drawing.
Selection changes are already routed through `syncBrowser` and hover never selects.
The render callback currently advances motion, polls clicks, aggregates metadata,
loads thumbnail textures and draws. This ordering makes draw-only behavior difficult
to test and creates a risk of stale geometry following input changes.

## Existing behavior to preserve

* A selected set is replaced by all its difficulty rows; other sets remain collapsed.
* Mouse wheel moves the viewport, without changing the selected beatmap.
* Selected difficulty re-click plays, with a 240 ms guard after opening a set.
* Selected overlapping rows draw last and win hit tests. Group headings do not play.
* Arrow navigation, Enter, Escape, F2/Shift-F2, F1/F3/F6, import and score selection.
* Empty queries and empty libraries have no playable selection; filtering repairs
  selection and clearing the query can restore the prior identity.
* Mode/Mods show local, nonfunctional selectors and Options reports unavailable.
  Adding gameplay modes/mods or destructive file operations is outside this UI change.
* No fabricated difficulty rating: stars require trusted supplied ratings.
* Existing spring preserves momentum across interrupted selection/expansion.
* Existing skin corpus policies must not be replaced by PNG-sized hitboxes.

## Regression risks and verification

High risks are overlap click priority, selected identity after sorting/filtering,
expansion continuity, temporary screen-to-Library indices, transparent custom artwork,
large composite controls, score wheel arbitration, texture ownership and retina
scissor coordinates. Existing navigation, carousel motion, browser, skin corpus,
Unicode fitting and score tests cover these. The existing production visual harness
covers 720p, 1080p and doubled framebuffer density; use it after structural changes.
Baseline `./gradlew test` passed before edits.

The current curve, row density and spring coefficients are osujava choices, not
verified constants from the supplied Stable build. Consult
[specific reference evidence and gaps](song-select-stable-reference.md) before
making a compatibility claim. Earlier Phase reports are historical and sometimes
describe obsolete geometry (for example seven-star versus current nine-star bands).

## Incremental migration

1. Record audits and source confidence before implementation.
2. Move relative selection navigation into the model; centralize metrics and shared
   visible geometry. Preserve existing ordering and motion.
3. Extract immutable row/detail presentation and row renderer; keep selection and
   Library operations unavailable to it. Make update/input occur before drawing.
4. Verify selection/filter/empty/random, frame-rate convergence, hit geometry,
   resolution and fallback coverage, then run the production visual harness.

Each implementation step receives its own reviewed commit after relevant tests.
No reference executable, extracted assets, generated captures or imported maps are
added to this repository.
