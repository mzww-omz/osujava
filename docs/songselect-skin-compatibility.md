# Song Select legacy skin compatibility audit

Audit started on 2026-09-28 at osujava `6586ab5`, clean working tree. McOsu is a read-only reference at `db2add20ea291f6f3b6d022fcd4eba100a5bd161`. The initial evidence and recommendations below were written **before production code was changed**. No C++ code was copied or translated. No stable binary was inspected. This is a compatibility audit, not a claim of pixel-identical stable emulation.

## Evidence and four-way comparison

Primary public references: [official Interface specification](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection), [skin.ini version history](https://osu.ppy.sh/wiki/en/Skinning/skin.ini#versions), and the [WhiteCat release](https://osu.ppy.sh/community/forums/topics/986201) / public screenshot linked in [the placement audit](songselect-placement.md). Suggested dimensions describe controls, not maximum artwork bounds. Exact stable pixel ordering, X offsets, and omission/fallback intent are not fully specified publicly; those remain inferences where identified.

| Family | Stable public semantics | McOsu at inspected revision | osujava before this audit | Real examples / recommendation |
| --- | --- | --- | --- | --- |
| selection-mode/mods/random/options | Normal blending; v1 top-left at 87 SD above bottom; v2 bottom-left. Mode 92x87/90, others 77x87/90 suggested | Base Mode 90x90 normal, 88x90 hover, others 74x90, osuSize 38. UI bounds independent of raw PNG, but each whole PNG is contained and centred in its bounds | Native density-normalized artwork at height/768, fixed adjacent slots, v1/v2 origins, bounded alpha-derived input | WhiteCat/Seoul Mode composites must not be contained. Keep existing raw origins/scale; classification need not drive rendering |
| menu-back | Bottom-left, suggested 200x214; animated menu-back-{n} supported | Base 225x87, osuSize 54; raw art retained through OsuSkinImage, height clamped to 1.5 base heights; SongBrowser limits Back focus to base width | Native bottom-left; first animation frame; visible interaction clipped to reserved navigation region | WhiteCat 272x91, 166 SD + 166 HD frames. Keep independent reservation/input; full animation deferred |
| menu-button-background | Multiplicative, bottom-left; suggested minimum 690x85; reused for songs and scores | Plain texture plus OsuSkinImage base 699x103, osuSize 64. SongButton normalizes to minimum reference before UI scaling; inner vertical margins compensate raw-height delta | Bounded row aspect, load-time body bounds, separate row artwork/body/text/input; score cards use same asset | Greylooks 700x117, WhiteCat 700x106, Seoul/Default 699x103. No giant row canvas found; no row redesign justified |
| songselect-top | Top-left; repeated right pixels underneath original | Fit to screen then enforce minimum info height (heuristic); no stable edge repetition; raw image controls topbar geometry | Native uniform scale and edge repetition; visible depth for layout | Greylooks has authored top; all other primary profiles omit it. Foreign Greylooks top is visible in before capture |
| songselect-bottom | Bottom-left, stretch horizontally | Bottom bar proportional to viewport; scale modifier 0.8, black underlay and quad filling bar | Native logical height, X stretch; content minimum height separate, but toolbox chrome Rect also supplies layout metrics | Keep drawing presence separate from reservation. Tiny images must not shrink layout; missing current decoration should not import another theme |
| star | Multiplicative centre; suggested 50x50; partial-star treatment changes at 2.2 | Star sized from row height/raw height; partial scale minimum .5; growing full stars, regardless of skin version | Fits bounded 16-unit glyph; partial clipping for all versions; no trusted production rating source | WhiteCat absent, xishu transparent 1x1, Greylooks padded 45x45, Seoul 52x50. Modern partial-star mismatch recorded, no rating work in scope |
| grades / ranking / score | Grade assets and ranking screen artwork have distinct roles; the [ranked-map grade-set guideline](https://osu.ppy.sh/wiki/en/Ranking_criteria/Skin_set_list#grade-indicator-set) suggests 34x40 small indicators (not a limit on whole user-skin PNGs) | Small grades base A 34x38, B 33x38, C 30x38, D 33x38, S/SH 31x38, X 34x40, XH 34x41; osuSize 128. Song/score grade placement scales from nominal bases while raw art may overshoot. Score background uses nominal 699x103 fill | Small grades fitted into bounded row/score destinations; no ranking screen or score-font skin renderer in Song Select | Small grades ordinary in corpus; Greylooks half-pixel HD differences are authored. WhiteCat *large* grades are 1108x600 composite ranking art, not Song Select assets. Do not route them into small-grade slots |
| thumbnails | Version >=2.2, 115:85; image is beatmap background | Version <2.2 skips drawing and thumbnail text reservation; cover + 1.05 overscale and bounded clip; asynchronous residency fade | Same version gate, cover in independent bounds and residency fade; explicit harness-only old preview | Portrait/wide/missing/corrupt fixtures already exist. Backgrounds are library assets, not skin filenames; no new thumbnail change required |

Source traversal covered OsuSkin.cpp/.h, OsuSkinImage.cpp/.h, OsuUISelectionButton.cpp/.h, OsuSongBrowser2.cpp, OsuUISongBrowserSongButton.cpp, OsuUISongBrowserButton.cpp, OsuUISongBrowserSongDifficultyButton.cpp, OsuUISongBrowserScoreButton.cpp, OsuUIBackButton.cpp, OsuScreenBackable.cpp, OsuRankingScreen.cpp and OsuUIRankingScreenRankingPanel.cpp. Relevant symbols are `load`, `loadImage`, `getSizeBaseRaw`, `drawRaw`, `onResized`, `updateLayout`, `drawMenuButtonBackground`, `drawBeatmapBackgroundThumbnail`, `drawGrade`, `getGradeImage`, `onJustBeforeReady`.

## McOsu assumptions and workarounds

These are observations, not values to import into Java:

- `OsuSkinImage` normalizes density separately from its nominal base size. `draw` applies resolution/density scale; `drawRaw` leaves scaling to its caller. Animation searches user frame zero/static before default; stops at first missing frame (limit 512). This avoids default animated images overriding a user's static image. HD is optional via `osu_skin_hd`; Java currently prefers HD whenever loadable.
- Mode normal uses a deliberately wider base than hover, and SelectionButton adds .025 to base aspect. Comments explicitly say this improves connection to the bottom blue line for most skins. It is empirical seam correction, not stable specification; no Java equivalent added.
- SelectionButton contains **each normal and hover independently**. This prevents tiny normal files magnifying hover, but shrinks a composite canvas into one control. WhiteCat could therefore differ markedly between McOsu and stable. Java already avoids the shared-scale failure and the containment failure.
- SongBrowser navigation uses fixed offsets (including small gaps), with a remaining previous-button-width dependency after the third control. Comment cites the real skin `kyu` as overflowing the previous image-width-driven layout. Java's independent adjacent nominal slots avoid that dependency.
- Back draw order and focus are separately constrained to stop wide decoration stealing other buttons. Java already separates artwork and input rather than imposing an artwork width limit.
- Tiny top/bottom textures are substituted with McOsu default textures **after async loads finish**. Top checks width OR height <3; bottom currently checks height twice (a likely typo, documented rather than imitated). This protects layout division/geometry but discards intentional transparent replacements. Java must retain the image and use owned layout minima.
- Row-height padding compensation assumes a nominal 103-high canvas; score backgrounds and grades likewise use nominal metric sizes even with unusual raw art. Results panel culling cannot safely use its PNG alone. Java's body/content/reservation split is the transferable principle.
- McOsu's plain-image loader and OsuSkinImage have different fallback implementations; duplicate `<element>2` loads are marked temporary fixes. A single universal fallback model is not a proven compatibility rule.

## Historical fixes

Inspected local git log **and relevant diffs**, plus OsuChangelog.cpp (alpha317, alpha314, alpha2897). Public links identify commits without treating commit messages alone as proof of exact stable behaviour.

| Evidence | Failure / reason | Java implication |
| --- | --- | --- |
| [030b535, 2025-04-13](https://github.com/McKay42/McOsu/commit/030b535) | 1x1 top/bottom broke layout; adds post-ready default substitution and force-default loading | Separate render presence from metrics; do not reject valid transparent assets |
| [3c32df1, 2022-06-26](https://github.com/McKay42/McOsu/commit/3c32df1) | Very wide Back hid/stole selection clicks; changes draw order and bounds focus | Keep reserved Back input region independent of raw art |
| [e363ffb / a99bdd4, 2021-10-18](https://github.com/McKay42/McOsu/commit/e363ffb) | Fixed selection area, independent fitted over-image, OsuSkinImage migration | Tiny-normal/large-hover regression belongs in tests; do not adopt containment |
| [ee606bb / 420a2f3, 2021-11-06](https://github.com/McKay42/McOsu/commit/ee606bb) | Small ranking-panel caused scrollview culling; first container reservation, then max(nominal 622x505 scaled, raw artwork scaled) in follow-up | Texture size is not content extent or culling reservation |
| [16be69d, 2021-11-06](https://github.com/McKay42/McOsu/commit/16be69d) | Score/Combo prefix, overlap @2x and missing-texture behaviour | Density and font overlap require independent metrics; no Song Select font patch |
| [07f7588, 2017-09-01](https://github.com/McKay42/McOsu/commit/07f7588) | Version <2.2 incorrectly displayed thumbnails and reserved their width | Existing Java version gate retained |
| [7a01e55, 2018-12-16](https://github.com/McKay42/McOsu/commit/7a01e55) | Cursortrail @2x scaling | Density belongs to asset, not global skin flag |
| [93113bc, 2020-02-16](https://github.com/McKay42/McOsu/commit/93113bc) | Legacy scorebar-ki default behaviour (historical commit has non-OsuSkin file changes) | Fallback is element/capability dependent, not universally interchangeable |
| alpha2897 changelog | Empty transparent images draw optimization; tiny selection normals enlarged hover | Visibility optimization must preserve provider identity and input semantics |
| alpha314 changelog | Weird long ranking grade overflow | Large ranking art is real; record outside Song Select scope |

No separate recent `menu-button-background` compatibility commit was found in the inspected log. Existing nominal sizing and padding correction are the relevant current workarounds; this is not evidence that all row skins work.

## Skin Version cross-renderer audit

McOsu defaults to 1, parses `latest` as **2.5**, and has skin-version branches in: thumbnail draw/text reservation (<2.2); slider/circle hitburst numbers (>1); static miss animation (>1); old spinner selection (<2 or explicit old asset); ranking score scale, hit-result offsets, perfect/grade placement (>1). `OsuSkinImage` does not gate HD on version. SelectionButton itself does **not** implement the v1/v2 anchor switch. Beatmap format-version branches are unrelated and excluded.

Java parses latest as **2.7**, already switches selection anchor at 2 and thumbnails at 2.2. These semantics remain. Missing/unreadable ini currently follows configuration provider fallback, unlike the wiki's missing-ini latest default; recorded as an existing global semantic difference, not changed in this Song Select patch. Star fraction and ranking/version changes outside current production capability remain deferred.

## Corpus and unusual assets

Inventory includes Greylooks current/bundled (1.0), WhiteCat CK (2.5), Seoul (latest ->2.7), Default Template (2.7), the additional Downloads Default copy and xishu (2.2). Directories and archive contents are read only; inactive subdirectory alternatives are marked separately. No skin art or Library data is redistributed in Git. The fixed inventory records filenames, SHA256, version, raw/logical size, density, alpha >0 bounds/coverage/mass, four transparent margins, animation frame, normal/hover pairing, source provider and diagnostic flags. Bounds use top-left coordinates and exclusive right/bottom edges.

Initial scan: Greylooks 101, WhiteCat 418, Seoul 98, Default 96, Default-local 96, xishu 68 Song Select/ranking/score-related PNGs. Includes all 332 WhiteCat Back frames. Flags are **review hints**, never invalidity: giant (>1024 logical or >4M raw pixels), 1x1, fully transparent, extreme aspect (>12), >35% empty alpha bounding-box margin, >3x/<.25x suggested size, and SD/HD logical mismatch.

Examples: Greylooks small S/SH width 36 versus HD logical 35.5, X/XH height 50 versus 49.5; Seoul score digits SD 33x49 versus HD logical 21.5x39.5, several SD/HD transparent placeholders both raw 1x1 (logical 1 versus .5). Seoul Mode canvases agree but alpha begins at SD y253 versus HD logical y356. WhiteCat large ranking-panel 1400x1500 and grades 1108x600, padded/transparent ranking graph; xishu transparent Mode and star. No giant Song Select row background or small grade was found; giant ranking assets are not used here.

Diagnostic selection classification: ordinary canonical control = button-like; oversized/margin-heavy = decorated-button; >3 nominal widths and >2 nominal heights = composite-canvas; alpha-empty = transparent-replacement. Alpha coverage is recorded but cannot establish semantic ownership: a sparse canvas can contain distant chrome, while dense large art can be intentional. This classification is deliberately **not required for artwork rendering or fallback selection**.

## WhiteCat eight-image pixel audit

Local original `/Users/agemizu/ckzip` and imported CK directory agree. Actual Mode HD PNG is **2286x1860**, logical **1143x930**, not the initially reported 2048x1666. Alpha bounds are (0,170)-(2286,1860), so the reported dimensions do not match the alpha extent either. The user suggested the number may simply describe the image size; its measurement source remains unverified. Findings apply to the actual bytes; no undiscovered different edition is claimed.

| Pair | Normal raw | Hover raw (HD preferred) | Equal pixels after bottom-left 2x normalization/padding | Changed raw bounds in common 2x raster |
| --- | --- | --- | --- | --- |
| Mode | 2286x1860 | 164x220 | 77.5944% | (0,170)-(2286,1860) |
| Mods | 1x1 | 122x180 | 62.8643% | (18,0)-(122,80) |
| Random | 1x1 | 132x180 | 65.6902% | (27,0)-(131,80) |
| Options | 1x1 | 134x180 | 66.2023% | (30,0)-(134,80) |

All 28 inter-image pairs are measured, with pixel counts and changed bounds. Three transparent normal files are 100% equal. Unequal dimensions mean unpadded raw pixel equality is undefined; the inventory explicitly labels the comparison coordinate system rather than calling padding a same-size image. Intrinsic differences compare images at a common local origin; rendered-state differences must place controls at their distinct native origins.

**Conclusion: these are not eight shared giant canvases with local state edits.** Only Mode normal carries the top outline/search, toolbox, profile/status, bottom connections and right decoration. Three other normals suppress redundant bodies; four hover assets are small local overlays. Diff captures and the eight-image sheet are generated outside Git under `/tmp/osujava-compatibility-audit/whitecat-diff`. The current native artwork policy already supports this architecture.

## Reproduced issues and recommended patch (pre-implementation)

Before production captures: `/tmp/osujava-compatibility-before`, 297 scenes, 342 full PNGs, 375 scripted frames, all assertions passed. Existing assertions only prove presence/native geometry; they did not prevent foreign chrome.

1. **Foreign decorative fallback**: WhiteCat, Seoul and Default omit top/bottom; the holder imports Greylooks stripe/chrome. Missing does not prove author intent, but another theme is demonstrably mixed. Recommended narrow policy: first load current browser artwork/chrome; when current browser artwork exists, missing/unloadable decorative top/bottom use owned procedural underlay. When no current browser surface loads, keep existing fallback/bundled behaviour. Do not change gameplay-critical resolution, row/grade/Back/selection priority or configuration merge semantics. Current transparent images remain successful current replacements. This chooses an authored *surface provider*, not a guessed per-pixel semantic map.
2. **Tiny bottom layout coupling**: toolbox's chrome Rect uses logical bottom height for drawing and auxiliary layout. Content already has a minimum, but tiny current bottom gives inconsistent reservation. Recommended explicit artwork rectangle versus reservation rectangle; placeholder dimensions never shrink reserved area. Top already derives alpha depths and layout minima; retain it. Do not scale a 1x1 replacement into a visible default or turn it into a hit blocker.

Ownership needs only layer order and source policy here: current dedicated chrome draws below selection artwork; native composite keeps its authored pixels; neutral owned UI fills absent regions. Inferring a per-pixel top/profile/bottom semantic mask, OCR, skin-name rules, or suppressing all chrome merely because one PNG is large would be fragile. Missing decoration cannot reliably distinguish intentional omission from incomplete skin; transparent successful assets can be distinguished. Owned underlay is an explicit project policy, **not a proven stable fallback specification**.

## Geometry contracts

Keep raw texture (physical pixels), logical resolution (raw/density), native artwork origin (version/role), alpha extent (diagnostics/content), interaction (canonical slot restricted visible normal/hover content), and layout reservation (owned minimum/capability) separate. Empty alpha is still rendering presence. A giant raw artwork never expands input. A tiny raw artwork never reduces layout minima. Hover never shares normal-image scale. Current row-body/thumbnail/grade bounds remain separate; their real data does not justify a refactor.

## Implementation and final verification

The final implementation adds authored-current surface ownership for decorative chrome, current-normal ownership for selection hover, and independent bottom artwork/reservation bounds. Current normal artwork, including transparent replacements, is still drawn at its native density-normalized origin. No new rendering classifier, skin-name branch or dimension correction was added.

The first no-foreign-chrome prototype also discarded top layout metrics. Fullscreen review reproduced WhiteCat's top outline crossing the ranking header and first row. The final holder retains a separate `topLayoutFallback` and alpha coverage for **content reservation only** when current top is absent; `get(TOP)` remains null and rendering uses the owned underlay. `topLayoutProvider()` reports metric provenance separately from visual `provider(TOP)`. This reuses established reservation rather than guessing semantic regions in composite art. The metric texture remains in normal ownership/disposal (tested exactly once), so it costs one loaded texture but never draws foreign pixels. A successfully loaded current transparent top does not invoke this metric fallback.

Bottom has separate `bottomImage` (native logical height, stretched X only) and `chrome` (owned minimum reservation). Transparent 1x1 bottom stays current, renders no replacement, retains native tiny draw bounds and does not reduce navigation/input reservation. Top and content already have independent owned minima. Raw art never becomes a fullscreen hitbox.

Tests retain measured Greylooks/WhiteCat/Seoul/Default geometry in `core/src/test/resources/songselect-skin-geometry.json`. Actual art stays local; `-PsongSelectCorpusManifest=docs/songselect-skin-corpus.json` verifies pinned original PNG SHA256 values before production captures. The Python tool also verifies complete inventory/configuration equality for all six sources. The Downloads Default copy has identical filenames, pixels/hashes and metadata to Default Template; it needs no duplicate visual profile. xishu is additionally imported/captured as an exploratory profile.

Synthetic regression coverage: normal button, giant composite, asymmetric alpha margins, fully transparent 1x1, oversized independent hover, mismatched SD/HD dimensions, normal-only with bundled fallback, missing top/bottom, malformed files, current/fallback/bundled priority, old/latest Version, layout-only texture disposal, thumbnails (portrait/wide/corrupt/missing) and all bounds/input routes. A suppressed transparent Mode with no current hover must remain unclickable; the previous harness expectation that every profile could click Mode was corrected to explicitly assert no phantom target.

Scope excludes gameplay, Mods effects, star calculation, Beatmap Options and mode implementation.

### Final results and visual review

- `./gradlew build`: **PASS**, 625 tests in 79 suites, zero failures/errors/skips. The subsequent full harness used the final production code and corrected suppressed-control expectation, without recompiling its JAR during the run.
- Python synthetic scanner tests: **4 PASS**. Read-only pinned inventory verification: **all 877 entries in six sources PASS**.
- Full production harness: **819 scenes, 1344 PNG captures, 5208 scripted transition frames**, plus navigation/disposal checks: **PASS**. Output: `/tmp/osujava-compatibility-done`; log: `/tmp/osujava-compatibility-done.log`.
- Each of Greylooks, WhiteCat, Seoul and Default, plus exploratory xishu, has fullscreen idle, hover/pressed and capability-view captures at 1280x720 (1x), 1920x1080 (1x), and 1280x720 with a 2560x1440 framebuffer (2x). Synthetic fixtures and score/row/thumbnail scenes run at the same profiles. Captures are local generated artifacts, not committed skin artwork.
- Manually inspected fullscreen corpus and all four hover contact sheets, then xishu idle/Mode-hover and tiny-chrome sheets at every profile. No introduced foreign decorative chrome, double chrome, giant-art shrink, unexpected clipping, or art-driven hitbox expansion was observed. WhiteCat's line no longer intersects the ranking header/first row. Native viewport clipping and the existing app-owned Cookie are recorded limitations, not claimed stable parity. xishu's explicitly transparent Mode remains invisible and noninteractive; its missing functional Back still falls back by policy.
- Final sheets: `/tmp/osujava-compatibility-audit/fullscreen-corpus.png`, `fullscreen-hover.png`, `placeholder-regression.png`. Original WhiteCat eight-image sheet/28 pair diffs: `/tmp/osujava-compatibility-audit/whitecat-diff`. Twelve rendered-state heatmaps and full numeric results: `/tmp/osujava-compatibility-audit/whitecat-rendered-diffs.json` and `whitecat-rendered-*.png`.

After verification, final captures, fixtures, analysis sheets/diffs and log were moved out of `/tmp` into the local artifact directory `/Users/agemizu/.codex/visualizations/2026/09/28/01a0e6b5-4428-7623-bb65-75f8bea937dd/songselect-skin-compatibility/` (`captures/`, `analysis/`, `harness.log`). The `/tmp` paths above identify the original run/reproduction; retained artifacts are in this directory and remain outside Git. Before/intermediate captures retain their original `/tmp` locations.

Rendered WhiteCat idle → hover measurements use the full framebuffer, top-left/exclusive bounds. These show the small hover overlay at its actual control origin, rather than replacing/shrinking the giant normal canvas. Options also activates an app-owned tooltip; its changed region therefore includes that feedback. Pixel equality is approximately 99.83–99.84% for Mode/Mods/Random and 99.72% for Options at all profiles.

| Control | 720p changed pixels / bounds | 1080p changed pixels / bounds | 2x changed pixels / bounds |
| --- | --- | --- | --- |
| Mode | 1505 / (254,631)-(287,678) | 3371 / (381,946)-(430,1017) | 6071 / (508,1261)-(574,1356) |
| Mods | 1448 / (304,636)-(353,673) | 3315 / (457,953)-(530,1010) | 5989 / (609,1271)-(707,1347) |
| Random | 1556 / (381,636)-(430,673) | 3571 / (571,953)-(645,1010) | 6329 / (762,1271)-(860,1347) |
| Options | 2590 / (442,613)-(589,673) | 5877 / (663,920)-(884,1010) | 10257 / (884,1227)-(1179,1347) |

### Additional reproduced hover-family issue (recorded before its patch)

The exploratory xishu archive supplies transparent 1x1 `selection-mode.png` and no `selection-mode-over`. Production capture `/tmp/osujava-compatibility-complete/1280x720-1x-phase5a-profile-3-mode-hover.png` shows a Greylooks Mode hover on top of the current blank. Diagnostics measure an unexpected 86.25x72.1875 interaction at (210,0), created entirely by the foreign hover. This is distinct from a missing functional Back (which may legitimately use the existing fallback).

Recommended narrow extension: a successful **current normal selection image** establishes that control's authored visual family. Probe only current hover; if absent/unloadable, keep normal (existing tint), and derive interaction only from available current pixels. If normal itself falls back, retain existing provider priority. This respects transparent intentional replacements, ordinary normal-only skins and all current normal/hover density differences. It is an explicit project family-ownership policy; the wiki does not fully specify per-image missing-hover resolution.

## Known limits and deliberate non-changes

- McOsu contain/overscale/default-replacement workarounds were documented, not copied. Native composite rendering was already correct in Java; only ownership/metrics couplings were patched.
- No semantic per-pixel chrome classifier, alpha-based rejection, skin-name rules, OCR or forced SD/HD size repair. Authored half-pixel and large logical differences remain source data. Sparse art does not prove absence of chrome.
- Back is still first-frame-only in Song Select. Full animation and the project's Cookie/layer differences from stable remain existing limits. Large artwork's viewport clipping is intentional; no claim that the entire raw canvas should fit on screen.
- Star partial rendering >=2.2 differs from the public specification; production has no trusted rating source, so that region is absent. No star calculator or unsupported rating feature was introduced during this audit.
- Ranking-screen composite grades/panels, version-specific Results layout and score font semantics are recorded but not implemented in Song Select. Small grades, row art and thumbnails had no newly reproduced geometry failure in primary corpus; no speculative refactor was added.
- Missing ini/version defaults remain a global configuration difference. This patch does not change gameplay/configuration provider semantics.
- Missing functional assets (including xishu's unprovided Back) still use the configured fallback/bundled/procedural chain. Current normal controls now own their hover family, and decorative chrome has different semantics. An absent functional control is not equivalent to an explicitly transparent authored control.
- If the current normal itself is missing/unloadable, a current hover may still accompany a fallback normal under the existing priority. Author intent cannot safely be inferred from such an incomplete family; no broader provider lock was added.
- Local macOS captures verify the inspected corpus, not every skin, platform or window aspect. Stable exact pixel ordering/default omission rules are not fully specified publicly. No binary reverse engineering or online gameplay connection was used.

## Reproduction

Run from the repository root. Python audit requires Pillow; no image or archive is written back to its source.

```sh
python3 -B -m unittest discover -s tools -p 'test_*.py'
python3 -B tools/songselect_skin_audit.py --verify docs/songselect-skin-corpus.json
./gradlew build
./gradlew :lwjgl3:songSelectVisualHarness \
  -PsongSelectCorpusManifest=docs/songselect-skin-corpus.json \
  '-PsongSelectCompatibilitySkins=/Users/agemizu/Downloads/_• xishu.osk' \
  -PsongSelectOutput=/tmp/osujava-compatibility-done
```

To inventory new editions, use repeated `--skin ID=directory-or-osk`, `--output /tmp/inventory.json`, optional `--fixtures /tmp/geometry.json`, and `--diff WhiteCat --captures /tmp/diff`. Labels select reports/test fixtures only, never production behaviour. Keep original SHA256/configuration measurements instead of silently rebasing the fixed corpus after a source changes. Real skin locations are machine-local in the manifest; missing sources fail the opt-in harness explicitly, while the portable measured geometry corpus always runs in unit tests.
