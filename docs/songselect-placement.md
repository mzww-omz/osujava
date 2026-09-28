# Song Select native placement audit

Audited before implementation on 2026-09-28, starting at `d89dbb4`. The working tree was clean. This follow-up fixes placement only; browser models, scores, gameplay, Mods, ratings, Sort, Group and Search are unchanged. McOsu was read only at `db2add20ea291f6f3b6d022fcd4eba100a5bd161`.

## Evidence and source limits

- [Official interface skinning](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection) specifies selection origins: v1 top-left, 87 SD pixels above the bottom; v2+ bottom-left. Back is bottom-left. Suggested selection dimensions are not a requirement to fit, crop or align visible artwork. Normal and hover have independent images.
- [Official skin.ini version history](https://osu.ppy.sh/wiki/en/Skinning/skin.ini#versions) documents the version transition and HD assets. The existing parsed version supplies the selection origin; no additional name-based configuration is needed.
- [WhiteCat author's public release](https://osu.ppy.sh/community/forums/topics/986201) and its [Song Select screenshot](https://i.ppy.sh/500ffd64d13951116571c4b65eba8baf09ae6870/68747470733a2f2f696d6775722d617263686976652e7070792e73682f715a456d3548502e6a7067) were visually inspected in a browser. The reference is 1920x1080, displayed at 1280x720 for comparison. Its panel begins around x287 and its composite ends at the right edge, unlike the old x231 panel/right edge x1226.
- McOsu `OsuSongBrowser2::updateLayout` reserves navigation origins independently of Back. Its 640x480 UI origins correspond to 224 SD pixels on our 768-high widescreen canvas and 192 at 4:3. This corroborates the public screenshot. The wiki does not publish X coordinates. This is a compatible placement inference from public artwork and McOsu, not a claim of inspecting stable internals. McOsu's fitted/centred image drawing and deliberate overscaling were **not** adopted.

## Real files and resolver audit

The complete [SD/HD inventory](songselect-placement-audit.tsv) records raw PNG dimensions, density-normalized logical dimensions, alpha > 0 bounding boxes, all four transparent margins, logical size parity, declared/effective version, resolved provider/file/dimensions/alpha/margins. Bounding boxes use top-left pixel coordinates and exclusive right/bottom edges; margins are raw pixels. Empty alpha has no bounding box or uniquely defined margins. No malformed PNG was found in this inventory.

Sources:

- WhiteCat: `/Users/agemizu/.osujava/skins/9d25c48370e65b593a03df12f3f1e15a7bbf9adadd1451c7be95fc7915625f11`, `Name: - # WhiteCat (1.0) 『CK』 #-`, author cyperdark, **Version 2.5**. The name's “1.0” is not the stable skin version. The entire `skin.ini` was inspected, including General, Colours and Fonts; it has no selection sizing/offset setting.
- Seoul: `/Users/agemizu/Downloads/-+ Seoul v9 -17-03.osk`, imported through the production importer into the harness output, **Version latest → 2.7**.
- Greylooks: current and bundled `core/src/main/resources/skins/default`, **Version 1.0**; the local Greylooks archive inventory from Phase 5A matches these selection dimensions.
- Default Template: `/Users/agemizu/.osujava/skins/7abad65780f69fd1daee98b6e236ca7bd6a47ce36a2840274e539304f76a94cf`, **Version 2.7**.

WhiteCat logical dimensions and SD alpha margins:

| Asset | Logical size | Left / top / right / bottom | Provider |
| --- | --- | --- | --- |
| selection-mode | 1143x930 | 0 / 85 / 0 / 0 | current HD |
| selection-mode-over | 82x110 | 47 / 15 / 0 / 45 | current HD |
| selection-mods | 1x1 | fully transparent | current SD |
| selection-mods-over | 61x90 | 9 / 0 / 0 / 50 | current HD |
| selection-random | 1x1 | fully transparent | current SD |
| selection-random-over | 66x90 | 13 / 0 / 0 / 50 | current HD |
| selection-options | 1x1 | fully transparent | current SD |
| selection-options-over | 67x90 | 15 / 0 / 0 / 50 | current HD |
| menu-back-0 | 272x91 | 0 / 0 / 0 / 0 | current HD |
| songselect-top | absent; resolved 1366x149 | resolved 0 / 0 / 0 / 0 | bundled HD |
| songselect-bottom | absent; resolved 1366x90 | resolved 0 / 0 / 0 / 0 | bundled HD |

All available WhiteCat SD/HD pairs have identical logical sizes. Random hover's HD alpha silhouette is inset by half a logical pixel on each side; this is a source raster difference, not a density scaling error. Back has 166 SD and 166 HD animation frames, all 272x91 logical; production retains its existing first-frame resolution. Static `menu-back.png` is absent. Missing top/bottom correctly resolve to the project's bundled Greylooks artwork; this explains the remaining striped background, distinct from the author's stable default. Back animation, stable default chrome and the existing java Cookie remain outside this placement fix.

Mode contains the normal button bodies/labels, profile chrome, shadow, bottom connection, right-side decoration, and an upper outline/search decoration. Its other transparent normals intentionally suppress replacement visuals. Seoul also uses a large Mode composite (1150x540), with independent 93x90/78x90 controls. Greylooks has 92x85 Mode and 77x86 others, including top padding. Their authored differences are retained.

Seoul's SD/HD canvases agree logically, but the raster artwork itself differs: Mode's alpha begins at SD y253 versus HD y712 (logical y356). Real-skin alpha silhouettes therefore need not be identical across densities. Parity tests use equivalent pixel fixtures and assert exact logical raw/draw/interaction geometry; the real inventory distinguishes canvas parity from authored alpha differences.

## Root cause and generalized geometry

Before this fix, selection image drawing already used raw bounds, native aspect and `height/768` scaling. It did **not** fit by common height, width or alpha. The incorrect coupling was horizontal: selection began at Back's arbitrary 154px fit slot. At 1280x720 that was x154 instead of x210, shifting all control artwork and the composite right edge left by 56px. Back was independently fitted to its substantial-alpha body and translated to remove margins, breaking its relationship with the other assets.

The final geometry separates:

1. **Raw image**: `raw/density`, uniformly scaled by viewport height/768. Normal and hover each keep their own dimensions. No alpha crop, stretch, common-height fit or width fit.
2. **Draw origin**: fixed selection X origins independent of Back width/provider/alpha. V2+ raw bottom-left uses y0; v1 raw top-left uses y87 SD. Back always uses raw bottom-left at (0,0), at the same native scale.
3. **Visible bounds**: alpha inventory mapped from the raw origin, used for diagnostics and interaction calculations only.
4. **Interaction**: substantial alpha (>=160), or bounded visible-alpha (>=16) fallback, inside the nominal control region. Normal/hover content is united. Decoration outside that region remains drawn but cannot become an action. Back now uses the same alpha metrics, restricted to its navigation region; transparent Back remains non-interactive.
5. **Common baseline**: shares the appropriate image origin, not a visible top/bottom edge. WhiteCat Mode hover may extend to 110 SD high while the others are 90. Greylooks v1 raw bottom Y is 2 SD for Mode and 1 for the others. Neither is normalized away.

At 1280x720 WhiteCat Back is now 255x85.3125 at (0,0), rather than the former 154x51.522 fit. Mode remains 1071.5625x871.875; its origin moves from x154 to x210. Its right edge is x1281.5625 and is naturally clipped by the viewport. Visible hover bottoms remain Mode 42.1875, Mods/Random/Options 46.875; the intended relative Y is unchanged.

Large composites use the existing dimension-based auxiliary layout path. Import now sits in the quiet strip above bottom artwork; status/debug start at the selection origin, clearing tall native Back artwork. Ordinary skins keep compact inline Import. The independent Cookie geometry and hit policy are unchanged. No skin name, directory hash or asset identity branches exist in production.

Normal asset presence continues to suppress procedural backgrounds/borders and additional Mode/Mods/Random/Options labels, including valid transparent replacements. Hover uses the actual hover image over normal artwork, with no generic rectangle. Label recognition/OCR is unnecessary: a resolved selection asset owns its visual, whether it contains text, icons or an intentional blank. Procedural labels only accompany an exhausted normal resolver. Existing disabled/pressed tint semantics are retained.

## Verification and captures

Before captures were generated from untouched HEAD with the production OpenGL harness: `/tmp/osujava-placement-before`, 288 scenes / 333 PNGs / 375 transition frames.

Final `./gradlew build` succeeded: **612 tests / 78 suites / zero failures, errors or skips**. The full production OpenGL harness succeeded: **750 scenes / 1275 PNG captures / 5208 scripted transition frames**, including navigation and disposal checks. The expanded toolbox subset has 297 scenes / 342 captures / 375 transition frames. Display profiles are 1280x720, 1920x1080, and 1280x720 with a 2560x1440 backbuffer. Malformed-PNG fixture warnings are expected recovery checks.

Final output: `/tmp/osujava-placement-final`; combined build/harness log: `/tmp/osujava-placement-final.log`. Every required comparison has a full 1280x720 capture under identical library, viewport and input conditions:

| Profile | Idle capture |
| --- | --- |
| WhiteCat before | `/tmp/osujava-placement-before/1280x720-1x-phase5a-profile-1-idle.png` |
| WhiteCat after | `/tmp/osujava-placement-final/1280x720-1x-phase5a-profile-1-idle.png` |
| Seoul after | `/tmp/osujava-placement-final/1280x720-1x-phase5a-profile-0-idle.png` |
| Greylooks after | `/tmp/osujava-placement-final/1280x720-1x-phase5a-current-idle.png` |
| Default Template after | `/tmp/osujava-placement-final/1280x720-1x-phase5a-profile-2-idle.png` |

Three comparison sheets were generated from actual framebuffers and visually inspected:

- `/tmp/osujava-placement-final/placement-comparison.png`: idle and Mode hover, WhiteCat before/after, Seoul/Greylooks/Default after, bundled/procedural fallback.
- `/tmp/osujava-placement-final/selection-hover-comparison.png`: all four selection hovers for each real profile, including WhiteCat before/after.
- `/tmp/osujava-placement-final/auxiliary-hover-comparison.png`: Back, Import and Cookie hovers for the same profiles.

Visual results: WhiteCat panel moves to the public reference's horizontal position, Back connects at native scale and the right decoration reaches the viewport edge. Its control heights/relative Y and transparent decoration remain intact. Seoul retains its composite extension and tall Back without auxiliary text on their artwork. Greylooks keeps the v1 top origin and unequal raw bottom margins. Default Template, bundled and procedural fallback remain usable. All loaded current selection artwork prevents procedural control drawing; hover adds no generic rectangle or duplicate label. Cookie positioning is identical before/after.

Regression coverage includes asymmetric margins, tall/thin/wide assets, raw origin preservation, alpha-only interaction, 1x/HD parity of raw/visible/interaction geometry, v1/v2 differences, fully transparent selection and Back, decoration outside interaction, fixed origin independent of Back dimensions, and all three real skin geometry profiles. Real-decoder harness fixtures add transparent and asymmetric SD/HD assets; every loaded normal asserts that no procedural control was drawn. Production harness also checks raw Back/selection origins and native scale.

The previous routing test's x200 Mode expectation was corrected to x250: x200 belongs to the reserved Back region after the verified origin correction. The old x600 gap now belongs to Import, so that sample moved to x650. Harness Random clicks now use actual interaction geometry; its chrome framebuffer sample moved from x500 (now Options) to clear x700. These correct outdated geometry assumptions without changing browser implementation or weakening the input/chrome checks.

Reproduce:

```sh
./gradlew build
./gradlew lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-placement-final \
  '-PsongSelectCompatibilitySkins=/Users/agemizu/Downloads/-+ Seoul v9 -17-03.osk|/Users/agemizu/.osujava/skins/9d25c48370e65b593a03df12f3f1e15a7bbf9adadd1451c7be95fc7915625f11|/Users/agemizu/.osujava/skins/7abad65780f69fd1daee98b6e236ca7bd6a47ce36a2840274e539304f76a94cf'
```

All generated PNGs, imported skins and temporary scripts stay outside Git.
