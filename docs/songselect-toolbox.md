# Song Select Phase 5A

## References and inventory

Inspected on 2026-09-28, before implementation. Reference priority is the official stable skin/interface specification, then McOsu's separation of selection controls, selector state and asset loading. No McOsu code, formulas or constants were copied; no stable binary was inspected. McOsu was read only.

- [Interface skinning](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection): independent `selection-mode`, `selection-mods`, `selection-random`, `selection-options` and `-over` variants, normal blending; v1 top-left at 87 SD pixels from bottom, v2+ bottom-left. Suggested control canvases are Mode 92 wide, others 77 wide, 87/90 high. These describe controls, not limits on decorative artwork.
- [Client interface](https://osu.ppy.sh/wiki/en/Client/Interface#beatmap-carousel): white selected difficulty, light blue expanded siblings, orange Set with at least one completed difficulty, pink unplayed Set.
- [Keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts#song-select): F1 Mods, F2 Random, Shift+F2 Previous Random, F3 Beatmap Options, Enter Play, Escape close/back.
- [skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini): version and colours belong to the selected configuration; configuration is not merged per key.

The complete SD/HD source inventory, including dimensions, logical dimensions, alpha > 0 bounds, alpha >= 160 bounds, valid/transparent/missing status and provider, is in [songselect-selection-inventory.tsv](songselect-selection-inventory.tsv). Bounds in the inventory use image top-left coordinates. Runtime bounds use bottom-left logical coordinates. No malformed selection PNG was found in these real skins; malformed recovery is exercised by test/harness fixtures.

| Local profile | Normal / hover | Density | Composite observations |
| --- | --- | --- | --- |
| Greylooks archive/current and bundled | All four pairs | SD + @2x | Mode 92x85; others 77x86; transparent top padding |
| Seoul v9 archive | All four pairs | SD + @2x | Mode 1150x540; separate 93x90 hover; other controls 78x90 |
| WhiteCat 1.0 CK imported directory | All four pairs | Mode + hover @2x; other normals SD 1x1 | Mode 1143x930 supplies full chrome and button bodies; other normals intentionally transparent |
| osu! Default Skin Template directory and imported copy | All four pairs | SD + @2x | Ordinary 88/74/77x90 canvases |
| xishu archive | Mode transparent; other three pairs | SD | Mode hover absent; resolver may supply bundled hover |
| Missing fixture | None | — | Configured fallback, then bundled; procedural only with no loadable candidate |

Current 1x beats fallback/bundled @2x. A valid transparent image wins. Each normal and hover resolves independently through current → configured fallback → bundled → procedural; malformed @2x tries same-provider SD before the next provider. Runtime provider/density/bounds reporting belongs to verification, not product UI.

## Gameplay capability audit

`OsuRuleset.supportsMode()` accepts mode 0 only. Taiko, catch and mania can be imported/browsed but have no playable ruleset. The Mode view must therefore show osu!standard as the current supported mode and the other modes disabled.

Search covered `gameplay`, `ruleset`, `GameplayScreen`, the clock, renderer and score pipeline. There is no gameplay Mods enum, selected Mods input, difficulty modifier, speed modifier, fail-mod policy or Hidden/Flashlight renderer policy.

| Capability | Classification | Evidence / Phase 5A action |
| --- | --- | --- |
| No Fail | absent | No mod-specific failure policy; ordinary lack of a fail system is not No Fail |
| Easy | absent | DifficultySettings used without mod transformation |
| Hidden | absent | No mod-driven visibility changes |
| Hard Rock | absent | No difficulty/position transform |
| Sudden Death | absent | No mod-driven instant fail |
| Double Time | absent | No selected speed/clock/audio transform |
| Half Time | absent | No selected speed/clock/audio transform |
| Relax | absent | No independent automatic tapping mod |
| Autopilot | absent | No independent automatic cursor mod |
| Flashlight | absent | No visibility mask policy |
| Spun Out | absent | No independent automatic spinner mod |
| Auto | debug-only | `GameplayRunMode.DEBUG_AUTO` / `OsuAutoPlayer`, normal session input API, F6 only, excluded from local scores |

Implemented ordinary Mods: none. Partially implemented Mods: none. Connected ordinary Mods: none. Foundation UI lists capabilities disabled, keeps an empty active selection, and supports Reset/Close without pretending to change gameplay. Ordinary gameplay remains manual. Score schema remains v1; no Mods are saved or shown in Results/Local Rankings. SSH/SH are deferred because Hidden/Flashlight are absent.

## Toolbox geometry and input

`SongSelectToolboxLayout` owns bottom chrome, shared action baseline, selection control canvas height, spacing, transparent overshoot, image/opaque/content/interaction bounds, anchors, Back, compact Import, Cookie and status/debug slots. Calculation is cached by viewport size. Chrome/selection artwork shares the existing 768-high legacy canvas; density is normalized before layout. Physical UiLayout scaling supplies 720p/1080p/Retina. No skin names are inspected.

Selection canvases are adjacent, with zero inter-control gap. V2+ artwork retains native logical width/height at a bottom-left anchor; v1 retains its top-left anchor at 87 logical pixels above baseline. A huge Mode image is drawn at native scale behind rows and Cookie, with no rectangular fit, no distortion and no control-height clamp. Its decoration may intentionally extend across adjacent controls or the screen. Normal/hover artwork keeps each image's own dimensions at the same origin; hover is layered over normal, allowing both replacement-style and outline-only hover art. A pressed tint is shared by the family; missing hover uses a quiet tint change. Options is muted/disabled, with explicit unavailable feedback on click/F3 and hover text. No fake options menu is opened.

The load-time scan inventories all opaque pixels, but interaction content only comes from pixels inside the canonical action canvas. Substantial alpha (>=160) excludes soft shadow; if none exists, visible alpha (>=16) provides a bounded translucent-body fallback. Interaction is the bounded union of normal/hover content so transparent WhiteCat normals use the real hover body. A valid empty replacement stays an empty image and never invokes a procedural button. Only a missing/unloadable normal invokes procedural rendering. Back keeps its body fit and separate hitbox. Import is a small, muted text action after the four controls, below half their height, without file-extension advertising. F6 is small muted debug text. Cookie remains an independent cropped play control; its existing hit policy is retained.

Composite artwork that spans more than three control widths and two control heights commonly contains an authored profile/status region. Its auxiliary set-count/F6 labels move to the quiet strip above the bottom artwork, with no extra card. Toasts/unavailable-mode feedback take precedence there. This is determined from logical dimensions, not skin names. Back and Import have restrained hover/pressed feedback too.

F1 and Mods click open the same wide, flat selector band. Mode click opens a capability view, with only standard current and taiko/catch/mania disabled. Selector state belongs to Song Select, independent of browser Search/Sort/Group/difficulty. Active ordinary Mods are an explicitly empty set because none has a gameplay implementation. Escape/2 closes; 1 resets Mods; F1 closes the open Mods band. While either band is open it consumes all navigation, typed search, Import, F2/F3, Play/F6, wheel and mouse input. Closing on a mouse click does not forward that click. Escape then returns to existing search/menu/back priority. F2/Shift+F2 and Shift+Random preserve filtered selection and history.

Closing with `2` also consumes its following typed event, so the close key cannot become a Search query.

## Played state and score projection

`SongSelectScoreSnapshot` caches best scores per real library difficulty and a Set-level played flag. A Set is played if at least one of its actual difficulties has a saved score; unknown/deleted difficulties and synthetic fixtures cannot mark it played. Multiple scores still produce one played flag. An individual difficulty is played only when it has its own score. Render gets flags and grades from the snapshot without constructing score identities or querying score storage per row.

Snapshot refresh is an O(1) library-reference/revision check per frame. Rebuilding is O(library difficulties) only when Library changes or a score is saved. Sort/Group/Search/Random/difficulty changes retain library identities and the score snapshot. Cached grade lookup replaces the previous per-draw repository query too. New Song Select instances after Results, and a resumed/unchanged instance on store revision, refresh immediately; disk reload at application start reconstructs the same state.

Colour priority is **selected white > sibling light blue > played orange / unplayed pink**. Hover adjusts the corresponding base tone without changing that hierarchy. Palette values are project choices, not copied stable constants. Existing row texture multiplication and skin text colours remain in effect.

The existing persistence boundary is retained: only finalized manual Gameplay saves, after `session.finish()`. Escape/aborted gameplay does not save; Debug Auto is excluded both by GameplayScreen and LocalScoreStore. No schema migration or invented Mods/grade records were introduced.

## Verification

Final `./gradlew build`: **607 tests, 78 suites, zero failures/errors/skips**; desktop and harness compilation/build passed. Selection coverage includes each current normal/hover basename, @2x, current SD over fallback HD, malformed current → fallback → bundled, procedural after exhausted candidates, transparent/sparse/composite content and density-normalized geometry. Browser tests cover no score, one/multiple scores, different difficulty/Set, reload, unknown difficulty, Debug Auto exclusion, revision-only rebuild, selected/sibling colour precedence, F1/F3 and selector input consumption including the typed close key.

Final full production OpenGL harness: **741 scenes, 1,266 PNG captures, 5,208 scripted transition frames**, all assertions and disposal checks passed. Of these, Phase 5A adds **288 scenes, 333 PNGs, 375 frames**; earlier phases retain 453 scenes and their existing transition captures. Profiles: 1280x720, 1920x1080, 1280x720 with a 2560x1440 backbuffer (density 2). Harness fixtures use real PNG decoding and actual production rendering; no screenshot substitutes or mocked texture decode.

Coverage: all four normal/hover pairs, normal-only, missing, malformed, @2x-only, composite, configured fallback, bundled; idle, four hovers/presses, disabled Random/Options, Back/Import/Cookie hover/press, Mode capability view, Mods view and empty active selection. Local compatibility profiles were Greylooks current, Seoul archive, WhiteCat imported directory and Default Template directory, each at all three display profiles. Current files, including WhiteCat's transparent 1x1 normals, won the resolver; no loaded normal entered procedural rendering. Each action emits `SELECTION PASS` with name/provider/density/procedural/image/opaque/interaction bounds. Missing/malformed skin-fixture decode warnings are expected.

Played captures compare unplayed/played collapsed Sets, sibling, selected played/unplayed, live score-save revision update, disk reload, real Gameplay Escape/dispose without score saving and 1000-Set browser states. Overlay checks click through Carousel, score list, Cookie and toolbox positions, send navigation/search/Import/Play/debug keys and wheel events, and verify no underlying selection, score scroll, search or navigation change. Existing phases exercise expansion, Unicode/ellipsis, thumbnails, trusted star fixtures, Group/Sort/Search, filtered Random/history, Local Rankings/grades, chrome, Cookie and selected-map Gameplay navigation.

Manual review used full screenshots for selectors/played priority and a 5-profile × 3-resolution bottom comparison sheet. Auxiliary labels clear WhiteCat's baked profile art. Seoul's decorative Mode extension retains native aspect and overlaps behind the independent Cookie; transparent WhiteCat normals do not get replacement procedural buttons. Normal-only hover falls back to tint. Pressed/disabled variants keep their control bounds.

The harness now uses a hidden window at an explicit position. This avoids libGDX's primary-monitor centering dereference in display-less macOS sessions, while continuing to capture actual GL framebuffers. An initial execution failed at that existing centering step before any scene; all final executions passed after the harness setup fix.

Reproduction:

```sh
./gradlew build
./gradlew lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-phase5a-final \
  '-PsongSelectCompatibilitySkins=/path/to/Seoul.osk|/path/to/WhiteCat|/path/to/Default Template'
# Focused run: add -PsongSelectPhase=5a
```

Final artifacts/logs are outside the repository: `/tmp/osujava-phase5a-final`, `/tmp/osujava-phase5a-final.log`, `/tmp/osujava-phase5a-final-build.log`. The generated `toolbox-contact-sheet.png` compares Greylooks, Seoul, WhiteCat, Default Template and procedural fallback at all three profiles. No fixtures, map data, imported skins or screenshots are committed.

## Performance: 1000 Sets

Each Phase 5A fixture contains 1000 Sets/4000 difficulties (1003 carousel entries), 500 played Sets plus 10 scores on the selected difficulty, and shared warmed artwork. Measurements are entire production Song Select render **CPU submission**, 180 samples after 60 warmups; input switch uses 100 after 40 warmups. Layout is cached; score refresh is a constant-time revision check; only visible rows draw. No per-frame score scans, score sorting, identity construction or disk reads were introduced.

| Display | Idle mean / p95 / max ms | Random hover mean / p95 / max ms | Mods band mean / p95 / max ms | Carousel scroll mean / p95 / max ms |
| --- | --- | --- | --- | --- |
| 1280x720 1x | 0.122 / 0.138 / 0.519 | 0.120 / 0.134 / 0.431 | 0.181 / 0.438 / 0.500 | 0.156 / 0.328 / 0.612 |
| 1920x1080 1x | 0.136 / 0.149 / 0.502 | 0.125 / 0.143 / 0.473 | 0.200 / 0.424 / 0.599 | 0.156 / 0.381 / 2.097 |
| 1280x720 2x | 0.127 / 0.134 / 0.618 | 0.122 / 0.140 / 0.431 | 0.185 / 0.374 / 0.548 | 0.126 / 0.131 / 0.588 |

Difficulty switch means were 0.006–0.008 ms across these states; p95 0.006–0.016 ms, max 0.047 ms. The previous Phase 4 baseline was 0.233–0.438 ms score-bearing idle and 0.013–0.017 ms switch; these final ordinary Phase 5A means did not show a regression. This is a local warmed measurement, not a GPU-completion or real-time guarantee.

The full run also repeated the original Phase 4 0/10/100/1000-score probes. Score-bearing idle means were 0.121–0.133 ms except the **2x/10-score outlier at 0.630 ms mean, 6.947 ms max**; it is retained in the log. Switch means were 0.006–0.008 ms. The focused Phase 5A earlier pass measured idle 0.124–0.157 ms and Mods 0.181–0.187 ms. The outlier and 1080p scrolling max demonstrate measurement variance; JIT/GC/scheduling/submission contention are possible causes, not established explanations. No claim of globally faster rendering is made.

## Deferred to Phase 5B or later

Real gameplay Mods/effects, compatibility matrix and activation; passing/persisting actual Mods and Results/Rankings display; Hidden/Flashlight and SSH/SH; playable non-standard modes; Beatmap Options actions; Collections; Replay browser; online rankings/management; star calculation/dynamic ratings; Difficulty/Date Added sort/group; Main Menu changes. Ordinary active Mods remain empty until a real gameplay capability exists. External/cross-process score reload remains the existing storage limitation.

osujava changes are committed in logical units. McOsu remained unchanged with a clean working tree.
