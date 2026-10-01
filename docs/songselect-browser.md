# Song Select Phase 3 — local Song Browser

Current difficulty-level filtering and metadata persistence are documented in [parity phase 10](songselect-parity-phase10-search-20260929.md).

Historical Phase 3 report. Current toolbox actions, F1/F3 routing and played-state projection are documented in [Phase 5A](songselect-toolbox.md).

## References and scope

Research: 2026-09-27. Phase 2.5 baselines: `a4b0ebe`, `e16bf55`. Primary: [stable interface](https://osu.ppy.sh/wiki/en/Client/Interface), [official illustration](https://osu.ppy.sh/wiki/images/Client/Interface/img/song-selection.jpg), [shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts). Secondary: read-only McOsu `db2add2`, `OsuSongBrowser2.cpp` (sort comparators, group creation, search update, visible candidate random/history, separate top controls) and the Phase 2.5 cross-file survey. No stable binaries were examined. No McOsu code, formulas or constants were copied. Everything remains local; gameplay, main menu, scores, mods, collections and online features are unchanged.

Stable publicly documents first-character Artist/Creator groups, ranged BPM/Length groups, lowest-to-highest numeric sorts and all-word metadata search. Its Difficulty order/groups use stars. McOsu demonstrates state-driven rebuilding and visible candidate/history selection; it has implementation-specific difficulty approximations and some incomplete groups. Those approximations are not suitable inputs here.

## Architecture

`SongBrowserModel` has no GL/Gdx dependencies. It owns an immutable library snapshot, pre-normalized search fields, trustworthy numeric keys, query tokens, Sort/Group enums, identity selection, search bookmark, bounded Random history and immutable typed entries (`GROUP_HEADER`, `SET`, `DIFFICULTY`).

Pipeline: Library snapshot/index → token filter → deterministic Sort → Group order/partition → browser entries → selected Set expansion → carousel projection. Rebuilds occur on query, sort, group or library changes; expansion changes only when a different Set is selected. Selecting another difficulty only changes identity. Idle getters never rebuild. Token parsing/matching is separate from indexed metadata, so later field predicates can be added without embedding parsing in drawing.

The Screen handles input, transitions, graphics and mapping entries to existing carousel coordinates. Its integer indices are temporary projections into the snapshot for legacy row presentation/test seams, never selection authority. The carousel retains spring, row/body geometry, curve, hover and scroll responsibilities. Row presentation still owns thumbnails, labels and optional supplied stars. `SongBrowserControls` owns compact selector/menu bounds and rendering. `SongSelectAction` routes only implemented keyboard/bottom actions; F1/F3 remain unimplemented.

Selection uses Set `id` plus normalized `.osu` path. Synthetic/in-memory difficulties lacking a path use a length-delimited identity of mode, version, creator and audio filename. This cannot distinguish two synthetic difficulties with identical identity metadata; real imported maps have paths. It never uses list indices. Library refresh preserves identity even if difficulty order changes, falls back to the first difficulty if removed, and retains query/sort/group. Import keeps existing auto-select behavior only if the imported Set is visible.

A visible selection survives Sort, Group and Search. If filtered out, the first result becomes the temporary selection; zero results expose no playable Set/difficulty and generate no entries. Search activation records the pre-search identity and clearing restores it if still present, otherwise falls back. An explicit navigation or Random choice inside the query updates that bookmark, so clearing keeps the user’s new choice instead of undoing it. Escape/Enter leave typing focus without clearing the query; Backspace remains available to clear it. Current Set expansion survives browser changes. Group dividers are always visible within their partition, not collapsible; no tree/accordion is introduced.

Sort/Group reorderings retain the selected anchor, discard unrelated old row travel, and center selection through the existing spring. Query changes retain existing expansion/anchor motion. Wheel browsing remains independent. Input updates refresh draw snapshots before drawing, so rows, metadata, background and Cookie target agree in the same frame. Empty search keeps the last backdrop for visual continuity but has no gameplay target.

## Sort and data provenance

Implemented: Title, Artist, Creator, BPM, Length, ascending. Secondary ordering for every primary key is normalized Title → Artist → Creator → stable Set ID. Unicode NFC and `Locale.ROOT` lowercasing preserve original display strings and avoid dependence on the process locale. Alphabetical ordering is deterministic Unicode string order, not language-specific collation.

BPM key: highest finite positive BPM across all uninherited timing points in all Set difficulties. This is an actual observed maximum, stable's documented changing-BPM policy extended to keep Sets together; it is independent of current selection. Inherited SV points do not count. Missing BPM sorts last and groups as Unknown BPM.

Length key: maximum existing `HitObject.endTimeMs - firstObject.timeMs` span across difficulties that contain objects. Spinner ends are available; slider tails are not calculated by this model. It is the same approximate map span available in Phase 2.5, not audio duration or drain time. Empty object data is unknown, sorts last and has an Unknown length group. No parser changes were introduced solely for Sort.

Deferred: Difficulty Sort/Group. OD/AR/CS/HP are trustworthy independent settings, but none alone orders overall playing difficulty; multiplying them or combining BPM would invent a score. Production has no trusted star ratings. Date Added is deferred because the library/storage schema has no trustworthy import timestamp; filesystem mtime is not substituted. There is also no persistent played/score store, so the existing pink/blue/white row palette does not claim played state and no orange played state is fabricated.

## Group

Implemented: No Grouping, Artist, Creator, BPM, Length. Artist/Creator use the first normalized Unicode code point of uppercase metadata; digits share `0–9`, letters retain their actual initial (including Japanese/non-BMP letters), symbols share Symbols, blanks use Unknown. Empty partitions are omitted. Group order is deterministic; current Sort orders Sets inside each partition.

BPM uses independently designed 50 BPM bands below 300 and one 300+ band. Length uses independently designed under-2, 2–<4, 4–<6, 6–<10 and 10+ minute bands. Unknowns are last. These are osujava ranges, not McOsu boundary constants; broad buckets follow stable's ranged-group concept without creating one group per distinct value. Sets remain intact even when their difficulties have different BPM/length.

Headers are compact 26-unit dark dividers with a label, no beatmap image/thumbnail, selection, hover hitbox or play action. Carousel header-adjacent center spacing accounts for their shorter body; unchanged beatmap-to-beatmap and child-to-child pitches remain 0.96/1.02 of body height. Group names and motion coexist with selected child composition.

## Search and Random

Search fields: Set and difficulty Title, Artist, Creator, plus difficulty version/name. NFC + case-insensitive matching; split on Unicode whitespace; all tokens must match some field anywhere in the Set metadata. Tokens may match different fields or different difficulties; matching Sets retain all difficulties. This Set-oriented scope is intentional and differs from stable's ability to independently filter difficulties. No field syntax or numeric query language is implemented yet. A numeric token can match a difficulty name even if it is absent from the title; the old harness's single-phrase expectation was updated accordingly.

Typing activates the existing browser overlay. The query is limited to 80 Unicode code points, surrogate pairs are accepted together, and Backspace removes a whole code point. Inactive search remains borderless; active/retained queries use the existing subdued layer. Zero matches show No matching beatmaps; a truly empty library shows the import prompt. No per-frame searches or debounce lag are introduced.

F2 selects a random visible Set and its first difficulty, excluding the current Set whenever alternatives exist. A single current result/empty results is a no-op. The RNG is injectable for tests. Before a successful random change, the outgoing identity (including difficulty) is pushed once; this records the path to the chosen selections and allows the first Previous Random to return to the starting selection. At most 64 identities are retained; adjacent duplicate entries are suppressed. Shift+F2 walks backward and consumes the eligible history entry. Hidden entries are retained until their query becomes visible again; empty results do not consume history. Sort/Group and library mutations cannot make dangling object references. Removed difficulties safely resolve to the Set's first difficulty. No forward-history command is introduced.

Public stable shortcuts establish Shift+F2, but do not fully specify filtered-history edge cases. The visible-results policy preserves osujava's prior Random behavior and follows the inspected McOsu visible-candidate concept. It never clears the query or changes browser modes to reach a hidden history entry.

## UI and compatibility

Top-right Group/Sort are separate borderless text selectors, showing current mode and opening a small square, dark, selected-strip list. They use the reserved Phase 2.5 chrome, without a modern form/dropdown theme. Mouse menus do not acquire keyboard focus; typing dismisses them, Escape dismisses the menu before leaving search/navigation. Search typing and F2/Shift+F2 work together.

Back, Import, Random and Cookie retain Phase 2.5 geometry/assets. The bottom action model reserves no fake Mode/Mods/Options buttons. Cookie always uses the model's selected difficulty and is absent for zero results. Metadata caches both Set and difficulty references. Legacy skin resolution, Greylooks thumbnail semantics, procedural fallback, @2x artwork, row composition, optional supplied stars and prior motion behavior remain intact.

## Verification

`./gradlew build :lwjgl3:songSelectVisualHarness --offline -PsongSelectOutput=/tmp/osujava-songselect-phase3-verified --console=plain` succeeded (2m 41s). **535 tests**, zero failures/errors/skips: 466 baseline + 69 added cases. **333 full scenes + 372 transition frames**: 252 Phase 1/2/2.5 scenes + 81 browser scenes, all 300 prior transition frames + 72 browser frames. Profiles: 1280×720, 1920×1080, and 1280×720 with a 2560×1440 framebuffer.

GL-free coverage includes every supported Sort/Group, deterministic ties, timing/object provenance and unknowns, Unicode/canonical normalization, field-spanning tokens, blank/empty results, identity preservation and fallback, search bookmark/explicit-choice semantics, reordered difficulty paths/library refresh, injectable Random and bounded history, selector/bottom hitboxes, unsupported F1/F3, typing/Escape/Backspace, F2/Shift+F2, and same-frame draw snapshot synchronization. Real production UI capture checks menu clicks preserve identity, headers coexist with motion, all animated bounds agree with snapshots, and Cookie navigates to Gameplay with exactly the selected Set/difficulty after Group/Sort/difficulty changes. Navigation/disposal and prior spring/selection/hover/geometry assertions passed.

Manual visual review covered Group/Sort lists, grouped/expanded and first/last groups, Unicode/long/no-result search, Greylooks, modern thumbnails, procedural fallback, 1080p and @2x. Phase 2.5 reference rows/chrome/stars and browser transition frames were also reviewed. Shared artwork assets and supplied harness ratings remain fixtures, not production ratings. Broken-fixture decode warnings are intentional. One intermediate execution was discarded after an overlapping Gradle rebuild replaced its runtime JAR; the final sequential execution passed.

Captures and fixture images remain outside Git under `/tmp/osujava-songselect-phase3-verified`; raw build/benchmark log: `/tmp/phase3-verified.log`.

### 1000 Set measurements

Values below are mean / p95 / max milliseconds for model rebuilds (100 samples after 40 warmups), mean / max for idle production render CPU submission (180 after 60 warmups). A separate bridge probe includes carousel/content/snapshot refresh (60 after 40 warmups); it is measured in a later, independently warmed pass, so it should not be subtracted from model timings.

| Profile | Browser idle mean / max | Search model mean / p95 / max | Sort model mean / p95 / max | Group model mean / p95 / max |
| --- | --- | --- | --- | --- |
| 1280×720 1x | 0.165 / 0.647 | 0.510 / 0.695 / 2.546 | 0.517 / 0.615 / 0.804 | 3.295 / 8.537 / 9.318 |
| 1920×1080 1x | 0.164 / 0.705 | 0.508 / 0.694 / 2.598 | 0.425 / 0.475 / 2.197 | 1.124 / 2.184 / 3.781 |
| 1280×720 2x | 0.196 / 3.917 | 0.524 / 0.991 / 1.339 | 0.435 / 0.528 / 0.649 | 1.007 / 1.667 / 2.913 |

| Profile | Search bridge mean / p95 / max | Sort bridge mean / p95 / max | Group bridge mean / p95 / max | Carousel scroll mean / max (600 samples) |
| --- | --- | --- | --- | --- |
| 1280×720 1x | 0.669 / 0.870 / 1.957 | 0.869 / 1.206 / 1.579 | 2.454 / 4.075 / 5.123 | 0.007 / 0.007 |
| 1920×1080 1x | 0.489 / 0.528 / 0.594 | 0.672 / 0.776 / 2.069 | 1.282 / 1.974 / 3.611 | 0.007 / 0.036 |
| 1280×720 2x | 0.484 / 0.518 / 0.545 | 0.637 / 0.720 / 0.830 | 1.321 / 2.116 / 3.521 | 0.007 / 0.023 |

Browser idle used Greylooks with no rating, 1000 Sets/1003 entries and shared warmed artwork. The original Phase 2.5 modern/no-rating probe was rerun as well (table below). Browser rebuilds never run per frame. Local measurements show practical input update times at 1000 Sets; JIT/GC/scheduling variance remains visible, especially the first grouping pass, so these are not strict real-time guarantees.

## Phase 4 gaps

Difficulty/Date Added ordering and grouping need real rating/import metadata. Persistent played state, Score Browser, grades, Mods, Mode, Beatmap Options and Collections remain out of scope. Field/numeric search syntax, independent difficulty filtering, collapsed browser partitions, language-specific collation and async thumbnail decoding are possible future work. Slider-tail-accurate length needs a trustworthy shared metadata source. Timings below measure warmed local CPU submission with shared artwork, not cold disk/image decoding or GPU completion.


### Phase 2.5 modern idle regression probe

| Profile | Mean / max ms |
| --- | --- |
| 1280x720-1x | 0.479 / 1.454 |
| 1920x1080-1x | 0.216 / 0.783 |
| 1280x720-2x | 0.224 / 0.693 |

These retain the Phase 2.5 thumbnail/no-rating setup with the new browser controls. The Phase 2.5 baseline was 0.204–0.220 ms mean. The final 720p modern probe was higher (0.479 ms), while 1080p/2x were 0.216/0.224 ms. The previous complete run measured 0.219/0.232/0.220 ms for this probe; subsequent production changes only affect explicit selection/bookmark handling, not idle rebuilding. This variation suggests JIT/GC/scheduling effects, but does not establish a strict performance guarantee. Even the higher final mean remains below 0.5 ms CPU submission; raw results are retained rather than selecting the best run. McOsu stayed at `db2add2` with a clean working tree.
