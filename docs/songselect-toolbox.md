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
