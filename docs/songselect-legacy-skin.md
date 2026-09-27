# SongSelect Legacy skin support

SongSelect keeps its existing Set/difficulty model, selection, navigation, row positions and interpolation. `SongSelectSkinAssets` owns UI textures; `OsuSkinAssets` remains gameplay-only. Skin images are visuals, while osujava owns layout and hitboxes.

## Assets

All entries support `.png` and `@2x.png` through `SkinAssetResolver`.

| Asset | Use |
| --- | --- |
| `menu-button-background` | Row background multiplied by selected/sibling/other/hover tint, then thumbnail and text. Selected row is composited last. |
| `songselect-top` | Stretched into the existing header, below metadata and search. |
| `songselect-bottom` | Stretched into the existing toolbar. |
| `menu-back` | Aspect-preserving fit within the existing logical Back bounds. |
| `selection-random`, `selection-random-over` | Normal image plus hover overlay in the same fitted rectangle; 140 ms fade and short F2 pulse. Click/F2 selects a matching Set, avoiding the current Set when alternatives exist. |
| `selection-mode`, `selection-mods`, `selection-options` and their `-over` images | Asset-holder support only. No buttons are shown for unimplemented actions. |
| `star` | Asset-holder support only. No star rating exists in the current BeatmapDifficulty model, so no fabricated rating or new calculator is introduced. |

Thumbnails reuse `BeatmapThumbnails` and `UiView.imageCover`: the source is cropped to cover the fixed destination without overflow. With a loaded row asset and Version >= 2.2, the Wiki's 115:85 ratio is adapted to the existing thumbnail height. Older skins (including bundled Greylooks Version 1.0) retain the original thumbnail rectangle. The carousel is clipped between header and toolbar, including at Retina density.

## Resolution and fallback

The existing priority is unchanged: custom skin, configured fallback skin, bundled Greylooks. Each provider tries `@2x` before normal PNG; decoding/initialization failure continues to its normal candidate, then subsequent providers. A valid transparent or 1x1 image still wins. Dimensions and aspect ratios cannot change logical layout, selection or hitboxes.

If no candidate loads, rows use the original quad, header/footer use TOP/BOTTOM colours, Back uses text and Random uses a labelled procedural button. Independent asset lookup supports row-only, top-only and bottom-only skins. Missing hover overlays keep the normal Random image. Empty search results disable Random visually.

The selected skin.ini is self-contained, following the shared resolver's existing configuration fallback (next provider only for absent/unreadable ini). Texture resolution and configuration parsing complete in `show()`/holder construction on the render thread, never per frame. SongSelect disposes its holder and thumbnail cache; shared texture identities are disposed once and repeated disposal is safe.

## skin.ini

`[Colours]` supports `SongSelectActiveText` and `SongSelectInactiveText`, stored in `SkinConfiguration.SongSelect`. Valid RGB triplets tint selected and inactive row text respectively. Absent settings retain osujava's existing palette. Malformed values are ignored, retaining previous valid values or the default. Existing Version parsing (including `latest` = 2.7) is unchanged; text colours apply independently of Version.

## Intentional limits

No `selection-tab`, animated `menu-back-{n}`, Mode/Mods/Options actions, star calculation, online features, pixel-perfect stable sizing, velocity effects or additional carousel curve. Existing gameplay, judgement, GameClock and ruleset implementations are unchanged.

## References and implementation provenance

- [Official Skinning / Interface](https://osu.ppy.sh/wiki/en/Skinning/Interface): element roles, row tinting, thumbnail version/ratio and hover images.
- [Official Skinning / skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini): SongSelect RGB settings and Version.
- [McOsu public OsuSkin asset inventory](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuSkin.cpp): used only to corroborate the SongSelect asset names and normal/hover pairs as conceptual UI information from public OSS. Its dimensions/constants/algorithms are not treated as stable specifications.
- Existing `docs/ui-stable-reference.md`, `docs/greylooks-assets.tsv` and bundled Greylooks.

McOsu C++ code was not copied, translated or ported. All Java composition, bounds, fallback, animation and Random selection are independently implemented using osujava's existing architecture. No osu!stable binary, decompilation, disassembly or nonpublic implementation material was used.

## Verification

Unit tests cover every asset name, normal-only/high-only skins, provider and density priority, failed-load fallback, missing/partial/broken assets, tiny/unusual dimensions, texture ownership, RGB parsing and old/latest versions. Navigation regression tests exercise preferred difficulty, backgrounds, up/down, set/page/left/right, filtered Random, search and play/debug/back transition requests. Existing SongSelectWheelTest and Volume HUD tests are retained.

Run `./gradlew build` and `./gradlew :lwjgl3:songSelectVisualHarness` (optional `-PsongSelectOutput=/tmp/...`). The production-screen harness captures 1280x720, 1920x1080 and 1280x720 at 2x backbuffer density for Greylooks (idle/hover), missing, row/top/bottom-only, normal-only, high-only, corrupt PNG, 1x1, unusual aspect ratio, old/latest Version and malformed colours. It checks keyboard/wheel navigation, search input, Random clicks and screen texture disposal. Generated screenshots/fixtures stay outside the repository.
