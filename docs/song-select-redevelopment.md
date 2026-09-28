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

At audit start the curve, row density and spring coefficients were osujava choices.
The later executable investigation established the coordinate and layout metrics
listed below; the independent spring remains an intentional difference. Consult
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

## Implemented navigation correction

`SongSelectInput` owns keyboard dispatch, search/modal precedence and wheel routing.
Up/Down move difficulty and Left/Right move Set. Direct executable analysis superseded
the initial wiki-only Page interpretation: Page Up/Down traverse ten eligible visible
rows, skip headers and stop after one circuit. Fewer than ten candidates therefore
return to the original row. Traversal uses one immutable entry projection, so passing
intermediate Sets does not expand them and change the counting basis.

Tests/harness steps that previously used horizontal arrows for difficulty selection
now use vertical arrows; their scenario assertions remain intact. Existing
Enter-to-close-search then Enter-to-play behavior remains. Unlike Stable's separate
keyboard focus on another Set, osujava still selects its destination immediately;
exact dwell/auto-activation rules were not established, and adding a partial focus
state would make metadata/preview/play disagree. This is a recorded compatibility
gap, not a claim of complete navigation parity.

Rows now capture a press identity and commit on release over that same identity.
A drag, release outside, modal, import or outgoing transition cancels it. The six-UI-unit
drag threshold is local policy. Existing Set double-click/play protection remains.

## Extreme skin canvas safety

Native image placement is preserved, including transparent placeholders and large
composite controls. Content reservation is now bounded independently: at most 40%
of the logical height for top chrome and 30% for bottom chrome. This guarantees a
usable positive carousel viewport even for a 4096-pixel decorative canvas; normal
skin reservations are unchanged. These are local safety limits, not claimed Stable
asset rules. The toolbox uses the same bottom reservation so auxiliary actions stay
on screen. Regression coverage includes SD/@2x from the existing suite and new
oversized/non-finite depth cases at multiple resolutions.

## Rendering and timing migration

Rows now receive immutable `SongSelectRowRenderer.Presentation` values (final bounds,
content, played/grade state and resident thumbnail). The renderer has no browser,
Library, importer or score-store dependency. Selection details are prepared in the
selection synchronization path. `render()` explicitly calls update/preparation before
drawing; composite skin artwork still sits beneath rows as required by existing skins.

Visible thumbnail paths plus the background are pinned for one frame before draw
snapshots are built. Unpinned images retain the bounded LRU policy. Hidden expansion
rows do not trigger decoding. Tests exercise more than 18 simultaneously visible
images to ensure eviction never disposes a texture still referenced by that frame.

The screen pulse previously capped every frame at 50 ms and thumbnail fading at
100 ms, stretching animation on slow frames. Both now consume elapsed UI delta
(with the carousel's existing two-second pause guard); non-finite/negative frame
input is sanitized. A 200 ms frame and 24 × 1/120 s frames both complete thumbnail
fade. This change does not alter the critically damped carousel spring.

## Executable-backed geometry

The follow-up [binary reference record](song-select-stable-reference.md#direct-clril-findings-supersedes-initial-unknowns-where-stated)
identifies the inspected hash and method tokens. Independent metrics now convert the
480-high carousel coordinates into the existing logical viewport: 48-unit pitch,
220-unit screen-down selection anchor, right-relative 340-unit origin, linear
center-distance indentation (37.5 units at the top/bottom), 45-unit hover indent and
10-unit hover neighbour separation. At 720p these become 72, 330, 510, 56.25, 67.5
and 15 logical units respectively. PNG dimensions cannot alter row pitch.

The first/last rows retain osujava's selection padding instead of the binary's initial
content Y=200 and end clamping. Expanded-group offset, selected emphasis, compact
group-header gaps, velocity deformation and critically damped spring remain local
policies: the corresponding Stable subtype/prediction/focus semantics are not fully
mapped. Wheel still targets one bounded skin-body height per input unit using the
existing spring/impulse model, rather than copying Stable's exponential integrator.
The exact build's inertial units, damping and callback travel are documented for a
future isolated motion change. Existing test expectations asserting viewport center
or the old rational curve were updated against the confirmed coordinate basis;
convergence, interrupted expansion and input/skin safety assertions are retained.

## Final validation

`./gradlew test build` passed: 642 tests, zero failures, errors or skips. The final
cross-feature harness command also ran `test build` successfully before capturing.
The following suites ran under `xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness`
with `-PsongSelectPhase=<profile>` and an output directory outside the repository:

| Profile | Scenes | PNG captures | Scripted transition frames |
| --- | ---: | ---: | ---: |
| `25` (reference geometry/motion) | 81 | 135 | 540 |
| `redevelopment` (cross-feature regression) | 111 | 339 | 2313 |
| `2` (text/thumbnail) | 81 | 93 | 24 |

All three passed navigation and disposal checks, covering 1280×720, 1920×1080 and
1280×720 with 2× framebuffer density. The new `redevelopment` profile selects
existing scenarios for search, grouping, scores, Random, modal controls, skin
fallback/transparent/oversized/@2x assets and scrolling; original profiles remain
available. Earlier interrupted full-suite attempts are not counted as passes.

Manual capture inspection included hover separation, large skin controls, Japanese
and Korean text. The validation host initially lacked CJK glyphs; installing the
host packages `fonts-noto-cjk` and `fontconfig`, then restarting the harness JVM,
resolved the missing glyphs. Japanese, Chinese and Korean coverage still depends on
installed system fonts; no font or artwork from Stable is bundled. Generated images,
fixtures and analysis outputs are not committed.

These checks validate osujava behavior, not a live side-by-side Stable session.
Stable runtime comparison, exact keyboard focus/dwell parity and its exponential
wheel inertia remain unverified or deliberately different as described above.

## Subsequent repair phase

The [repair verification record](song-select-repair.md) documents defects found by
normal application operation after these initial passes. It supersedes the earlier
skin-derived row body dimensions and unrestricted chrome drawing described above.
