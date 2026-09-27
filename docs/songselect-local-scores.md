# Song Select Phase 4 — local scores and grades

Research date: 2026-09-27. Baseline: Phase 3 `fcf9e03`, `389dbfa`, `85adb2c`, `eee808b`.

## Existing flow and data audit (before implementation)

Cross-file searches covered Score, Result, Accuracy, Combo, Grade, Replay, HighScore, LocalScore, Ranking and persistence. `OsuGameplaySession` owns `ScoreTracker`; `GameplayScreen` updates the session through the common `GameClock`, then calls `session.finish()` when that clock ends. Finish resolves outstanding circle/slider-head misses, unprocessed nested slider events and spinner judgements. `session.state().score()` is passed to `ResultsScreen`. Results only formats the immutable snapshot. Escape returns directly to Song Select, without a Results screen.

| Classification | Data |
| --- | --- |
| Already exists | Total score, current/max combo, 300/100/50/miss, accuracy, MANUAL/DEBUG_AUTO mode, Set ID, difficulty `.osu` path |
| Small additions in Phase 4 | One UUID per play, completion timestamp, pure derived grade, versioned local persistence |
| Absent; not displayed/invented | Mods, player name/profile, passed/failed, replay, independently persisted slider tick/tail and spinner spin/bonus counts |
| Deferred | Mods effects/UI and silver grade variants, replay capture/player/browser, fail system, other rulesets and online features |

Slider nested events contribute score/combo without entering the 300/100/50/miss denominator; slider heads do enter it. Spinner spin/bonus points contribute score but not accuracy/combo; the spinner final judgement contributes to the normal counts. This phase preserves that existing scoring model: it does not claim full stable scoring parity or change gameplay judgements. There is no health/fail system. The existing completed state is an object-processing state, not evidence of passing a fail check. Hence no fabricated passed boolean is saved.

No existing result/score persistence was found. The library already uses per-entry Properties files and temporary-file replacement, which is the style adopted here.

## References and independent decisions

Primary references: [stable interface and rankings](https://osu.ppy.sh/wiki/en/Client/Interface), its [full screen illustration](https://osu.ppy.sh/wiki/images/Client/Interface/img/song-selection.jpg), and [official grade rules](https://osu.ppy.sh/wiki/en/Gameplay/Grade). The existing locally downloaded official illustration was visually inspected. It shows rankings below metadata, independent dark/translucent rows, large grade anchors and subordinate score/combo, with accuracy/mods at the trailing edge. Approximate screenshot observations: ranking width around 0.28 viewport, row pitch around 0.07 viewport height, grade around half a row. These are visual observations, not stable internals or exact pixel specifications. osujava uses a somewhat wider 0.35 viewport column for score/accuracy/date without fictional usernames/avatars, 64/720 row height and 68/720 pitch, with 60×40 grade bounds. The column stops before the carousel and bottom controls. The selected treatment is an independently chosen blue row and thin accent strip; hover uses a stronger veil.

Secondary, read-only McOsu `db2add2`: `OsuUISongBrowserScoreButton.cpp` (grade, numbers, metadata, highlight/click); `OsuSongBrowser2.cpp` (`updateScoreBrowserLayout`, `rebuildScoreButtons`, difficulty-selection rebuild and `onScoreClicked`); `OsuDatabase.cpp` (difficulty-scoped local source/sorting); `OsuUISongBrowserSongDifficultyButton.cpp` (`updateGrade`). The latter obtains the grade from the first sorted score, rather than independently maximizing grade. McOsu score click opens its ranking/details screen. Here click selects/highlights the record only, retaining a future details seam, with no replay action. No McOsu code, equations or constants were copied. No stable binary was inspected. McOsu is unmodified.

## Model, identity and persistence

`SongBrowserModel` retains only beatmap filter/sort/group/selection. `LocalScoreStore` owns persistence and cached queries; `LocalScore` wraps the exact shared `ScoreState`, while `ScoreBrowserModel` owns target, formatted rows, selected play ID and scroll position. The screen coordinates them.

Real identity is Set ID plus normalized difficulty `.osu` path, matching Phase 3 real-map selection. It never uses difficulty index or file order. Pathless synthetic maps are not persisted. Import uses the existing library identity/path model; neither importing another Set nor reordering difficulties rewrites scores. Relocating the whole library or editing a `.osu` in place is not a content-hash migration feature of this schema.

Storage: `~/.osujava/scores/<play UUID>.properties`, UTF-8, `schemaVersion=1`. Fields: playId, setId, osuPath, playedAt (epoch milliseconds), score, accuracy, current combo, maxCombo, count300, count100, count50, misses. Grade is derived from counts, avoiding redundant contradictory grade data. Date is recorded at finalization; gameplay timing still exclusively uses GameClock. No database/network is introduced. Future schema versions can add mods/replay IDs; unsupported versions are preserved and skipped, not rewritten.

Records load once during application creation; valid scores are indexed into immutable sorted lists by difficulty. A successful save writes a temporary file, atomically moves it where supported, then updates memory. No score file is read during rendering/query/scrolling. Damaged individual records are logged/skipped with PARTIAL status; directory/read/write failures expose UNAVAILABLE status. Valid cached records remain usable. Unknown files and damaged records are preserved. One application instance owns writes; cross-process coordination and external file hot reload are not supported.

Finalization occurs after `session.finish()` and before Results navigation. A per-screen guard and UUID store idempotency prevent duplicate saves; constructing/showing Results does not save. Retry creates a new play ID. Escape/interrupted sessions never finalize scores. DEBUG_AUTO is excluded before capture and again by the store API, without manipulating gameplay input/judgement state. Saving failure does not stop Results; returning to Song Select exposes the storage warning.

## Grade and best-score definition

`ruleset.osu.OsuGrade` is a pure helper. Official current standard rules: SS for all 300; S for >90% 300, <=1% 50 and zero misses; A for >80% 300 with zero misses or >90% 300; B for >70% 300 with zero misses or >80% 300; C for >60% 300; D otherwise. Boundaries use integer cross-products with long totals to avoid floating ambiguity and int overflow. Empty/negative counts defensively return D, never a false perfect grade. Available: SS/S/A/B/C/D. Silver variants are deferred until real visibility Mods exist.

Ranking order is Score descending, Accuracy descending, timestamp descending, UUID lexical ascending. It is independent of filesystem/insertion order. The difficulty grade is the grade of the first record in this same order, even if a lower-score play has a higher grade. No record means no grade. This follows the inspected McOsu first-sorted-score concept and keeps the row badge consistent with the displayed best score. There is one score sort mode; extra sort controls are unnecessary for this phase.

## Presentation and interaction

Local Rankings heading and local Score-descending context sit under metadata. Each row presents grade, separated score, accuracy to two decimals, combo with `x`, then a muted `yyyy-MM-dd HH:mm` date. Numeric formatting uses Locale.ROOT; dates use the system timezone. Existing Snapshot accuracy is reused without a second formula. No Mods badge or fake player/avatar appears; judgement counts are retained in storage but not crowded into the row.

Only whole visible rows are rendered within the left viewport. Wheel advances rows, accumulates fractional trackpad deltas and clamps top/bottom; an index-range counter indicates offscreen records. Selection uses play UUID and survives unchanged-target beatmap Sort/Group operations. Changing difficulty resets score selection/scroll immediately. Search, clear, Random, Previous Random and Import all pass through the same screen synchronization. Cookie continues to read the authoritative browser selection.

Wheel ownership: left ScoreBrowserBounds, right carousel viewport, neither metadata nor bottom chrome. The gap between columns belongs to neither browser. The global volume controller reserves both browser regions; existing explicit Alt/F4 volume behavior is preserved. Left wheel cannot change right motion; right wheel cannot change left offset. Score click does not navigate/play and does not acquire search typing focus.

Empty library, no matching/selected difficulty, ordinary no local scores, and unavailable storage have distinct text. PARTIAL warning additionally identifies skipped damaged records while displaying valid data. Empty state is compact at the list top, not a restored full-height panel.

Grade images resolve `ranking-X-small`, `ranking-S-small`, `ranking-A/B/C/D-small` through the existing current skin → fallback skin → bundled resolver with its @2x precedence and corrupt-candidate fallback. SS uses stable's X asset name. No skin-specific name or path is hardcoded. Missing artwork uses grade text. Difficulty badges occupy the space between thumbnail and labels; labels/ellipsis/stars use the remaining bounded width. Grade artwork is not tinted with selected dark text colours; the text fallback follows the row text colour to remain readable on white selected rows.

## Verification

Final command:

```sh
./gradlew build :lwjgl3:songSelectVisualHarness --offline \
  -PsongSelectOutput=/tmp/osujava-songselect-phase4-final --console=plain
```

Succeeded in 2m 57s. **575 tests**, zero failures/errors/skips (535 baseline + 40 added cases). **426 full scenes + 480 transition frames**: all 333 previous scenes/372 frames, plus 93 score scenes/108 frames. Profiles: 1280×720, 1920×1080, 1280×720 with a 2560×1440 framebuffer. Focused development validation is available with `-PsongSelectPhase=4`; its final 93-scene pass also completed navigation/disposal checks before the final fallback text-contrast adjustment, which is covered by the final full run.

Grade tests cover every rank, strict 300 boundaries, inclusive 1% 50 boundary, misses, empty/negative counts and overflow. Store tests cover empty/save/reload, Unicode paths, multiple plays, separate difficulties/Sets, duplicate UUID after restart, malformed/missing/future schema and invalid numbers, unavailable storage, debug exclusion, deterministic ties and a highest-score/lower-grade case. Browser tests cover every Sort × Group, search/clear/empty results, Random/Previous Random, imported/reordered library, target refresh, selection/cache/revision changes and scroll clamping. Production Screen input tests exercise independent score/carousel offsets, immediate difficulty refresh and search typing. Existing Cookie/Back/bottom/volume/search/navigation tests all pass; the old left-column-as-volume expectations were updated because that region now belongs to scores.

The actual production-screen harness covers empty, single, three and 16-score rows; all grades; score 50 through 9,876,571,410; 1x through 10100x combo; 100%, fractional and low accuracy; timestamps; sibling grade and no-score difficulty; Group/Search; long/Unicode titles; top/middle/bottom wheel/hover/selection; simultaneous right carousel; difficulty transition frames; bundled Greylooks, missing/text fallback, bundled fallback and @2x-only fixtures. No Mods fixture is presented because the model intentionally has no implemented Mods. Assertions check left wheel leaves the carousel target unchanged, right wheel leaves score offset unchanged, target identity refresh and correct Cookie gameplay target. Visual review included all these composition categories across 720p/1080p/2x; the fallback selected-row grade contrast was corrected after inspection. Prior spring/curve/expansion/hit-testing/typography/thumbnail/fade/stars/chrome/browser captures and checks remain in the full run.

Captures: `/tmp/osujava-songselect-phase4-final`; raw final log: `/tmp/phase4-final.log`; earlier focused benchmark: `/tmp/phase4-focused.log`. Fixtures and generated assets remain outside Git, never enter production score storage. Deliberately broken PNG candidate warnings are expected and demonstrate fallback recovery; the final run has no assertion failure. Early development runs found harness preconditions (spring settling and exiting search focus), both fixed before this final run.

## Performance — 1000 Sets, shared warmed artwork

Phase 3 reported normal idle 0.164–0.196 ms and old modern/no-rating 720p 0.479 ms. This final run remeasured the original no-score browser and modern fixtures:

| Profile | Original browser idle mean / max ms | Modern/no-rating idle mean / max ms |
| --- | --- | --- |
| 1280x720-1x | 0.172 / 0.510 | 0.229 / 0.645 |
| 1920x1080-1x | 0.164 / 0.512 | 0.317 / 1.627 |
| 1280x720-2x | 0.170 / 0.512 | 0.248 / 1.083 |

Phase 4 fixtures have 1000 Sets/1003 carousel rows and the indicated score count on the selected difficulty. Idle measures CPU render submission, 180 samples after 60 warmups. Input-switch uses the actual screen Left/Right processor and includes score synchronization, carousel snapshot refresh and background selection; it alternates the scored difficulty and an empty sibling. Query, sort, scroll, cached target switch, input switch and first-format samples each use 100 after 40 warmups. Cold-format uses a new ScoreBrowserModel each sample, so the cost is not hidden by the cache. Sort copies and sorts the queried list. Queries and cached scrolling are below 0.001 ms at the displayed precision (not literally zero cost).

| Profile | Scores | Idle mean / max ms | Input switch mean / p95 / max ms | First format mean / p95 / max ms | Score sort mean / p95 / max ms | Right motion mean / max ms |
| --- | --- | --- | --- | --- | --- | --- |
| 1280x720-1x | 0 | 0.229 / 0.760 | 0.016 / 0.022 / 0.063 | 0.001 / 0.002 / 0.009 | 0.000 / 0.000 / 0.001 | 0.008 / 0.029 |
| 1280x720-1x | 10 | 0.257 / 0.637 | 0.014 / 0.017 / 0.024 | 0.061 / 0.122 / 0.154 | 0.001 / 0.001 / 0.002 | 0.007 / 0.017 |
| 1280x720-1x | 100 | 0.438 / 6.259 | 0.013 / 0.013 / 0.013 | 0.182 / 0.217 / 0.357 | 0.006 / 0.010 / 0.046 | 0.007 / 0.019 |
| 1280x720-1x | 1000 | 0.236 / 0.526 | 0.013 / 0.013 / 0.014 | 1.113 / 1.200 / 2.382 | 0.016 / 0.018 / 0.088 | 0.007 / 0.064 |
| 1920x1080-1x | 0 | 0.193 / 0.689 | 0.017 / 0.026 / 0.076 | 0.002 / 0.002 / 0.092 | 0.000 / 0.000 / 0.000 | 0.007 / 0.042 |
| 1920x1080-1x | 10 | 0.233 / 0.750 | 0.013 / 0.013 / 0.021 | 0.011 / 0.012 / 0.026 | 0.000 / 0.000 / 0.001 | 0.007 / 0.009 |
| 1920x1080-1x | 100 | 0.404 / 1.537 | 0.014 / 0.017 / 0.020 | 0.200 / 0.217 / 0.352 | 0.004 / 0.006 / 0.009 | 0.008 / 0.025 |
| 1920x1080-1x | 1000 | 0.266 / 2.823 | 0.013 / 0.014 / 0.014 | 1.139 / 1.199 / 2.628 | 0.015 / 0.015 / 0.021 | 0.007 / 0.020 |
| 1280x720-2x | 0 | 0.191 / 0.833 | 0.014 / 0.019 / 0.029 | 0.000 / 0.000 / 0.003 | 0.001 / 0.000 / 0.037 | 0.007 / 0.059 |
| 1280x720-2x | 10 | 0.296 / 1.287 | 0.014 / 0.018 / 0.019 | 0.022 / 0.028 / 0.065 | 0.001 / 0.001 / 0.008 | 0.007 / 0.035 |
| 1280x720-2x | 100 | 0.255 / 0.852 | 0.016 / 0.029 / 0.070 | 0.188 / 0.226 / 0.749 | 0.004 / 0.007 / 0.033 | 0.007 / 0.035 |
| 1280x720-2x | 1000 | 0.241 / 0.710 | 0.013 / 0.013 / 0.015 | 1.218 / 1.395 / 3.227 | 0.015 / 0.016 / 0.022 | 0.007 / 0.073 |

Left score scrolling and cached score-target switching have mean/p95 below 0.001 ms in all final fixtures. Memory query mean/p95 is also below 0.001 ms; the largest observed query max was 0.011 ms. No per-frame disk reads or list sorts occur. Render draws only five visible score rows at these aspect ratios, so list size itself does not increase row drawing work. First formatting scales with record count; the 1000-score stress cost is paid on first visit or store revision, and subsequent visits reuse the formatted rows.

The final no-score idle stays close to Phase 3. Displaying scores adds submission work, with observed means up to 0.438 ms in the final score fixtures. Measurement variance remains material: the earlier focused 2x/1000-score run measured 0.893 / 4.965 ms idle mean/max; the final corresponding result is 0.241 / 0.710 ms. The final unrelated modern-rated 2x fixture also showed 1.109 / 12.990 ms and a 720p browser search bridge showed 4.855 / 16.038 / 26.126 ms mean/p95/max. These outliers are preserved in logs rather than discarded. JIT, allocations from repeated cold-format stress, GC, scheduling and CPU/GPU submission contention can affect these local measurements; they are not GPU completion, cold image decode, disk-save benchmarks or strict real-time guarantees. No additional performance-oriented gameplay changes were made.

## Known limitations / Phase 5

Mods UI/gameplay effects and silver grades, Mode switching, Beatmap Options, Collections, online rankings/profiles/submission, replay capture/playback/browser, dynamic star calculation, Difficulty/Date Added sort/group, played/unplayed row colours and fail semantics remain outside this phase. Score click currently only selects; score deletion/details and external storage reload are not implemented. Library relocation/content-hash migration and cross-process writes require a future identity/storage policy. Whole-row wheel scrolling is deliberate; there is no smooth scroll animation, drag scrollbar or score-sort selector.
