# Song Select verification and remaining differences

2026-09-28. Read with [specification](songselect-stable-spec.md),
[audit](songselect-stable-audit.md) and [architecture](songselect-architecture.md).
This is a tested architecture foundation, **not a claim of complete stable parity**.

## Reproduce

All captures use the production screen and renderer. Assets and outputs stay
outside Git. Linux without a display can use `xvfb-run -a`.

```sh
./gradlew build
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  -PsongSelectPhase=foundation -PsongSelectOutput=/tmp/songselect-foundation

# Select any existing named scene, optionally overriding resolution/density.
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  -PsongSelectPhase=case -PsongSelectCase=greylooks-large-library \
  -PsongSelectOutput=/tmp/songselect-performance

# Explicit, reproducible state; time is seconds after the requested operation.
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  -PsongSelectPhase=configured -PsongSelectWidth=1024 -PsongSelectHeight=768 \
  -PsongSelectDensity=2 -PsongSelectSelectedSet=3 -PsongSelectSelectedDifficulty=1 \
  -PsongSelectScroll=250 -PsongSelectSearch=Local -PsongSelectHover=850,370 \
  -PsongSelectTime=0.08 -PsongSelectAction=selection \
  -PsongSelectOutput=/tmp/songselect-state
```

`Hover` uses logical, bottom-left coordinates. Width/Height are window pixels;
Density controls the actual FBO dimensions independently. `Scroll` uses logical
units. `Action` accepts none, selection, random, back, mode, mods, options.
`CustomSkin` accepts a local directory or .osk. Generated maps/backgrounds and
score fixtures are synthetic. No runtime requests to osu! services occur.

Configured capture emits a PNG and text inventory: row target/display rectangles,
logical indices, clip/hit bounds, alpha/z-order, skin version, asset provider,
path and density. Interactive geometry overlay remains available through
`-Dosujava.songSelectGeometry=true` in the application JVM. Random outcomes are
not seeded in the existing matrix; deterministic comparisons should specify a
selected set/difficulty rather than rely on a particular F2 result.

The configured scene first prepares resources with a zero-time render, applies
scroll/action, then advances in 1/120-second steps. Back times >=0.12s leave the
screen, so use a smaller time to capture the outgoing fade. The older named
scenes intentionally include entrance settling and additional input assertions.

For a side-by-side local sheet (Pillow required):

```sh
python3 tools/songselect_visual_compare.py --reference /path/to/stable.png \
  --capture /tmp/songselect-state/1024x768-2x-configured.png \
  --output /tmp/songselect-comparison.png
```

The accompanying JSON records source sizes and SHA-256 values. This is an
inspection aid, not an automated pixel-parity metric for different skins/maps.
Official screenshots are referenced by URL in the specification, not committed.

## Visual observations

The foundation matrix covers 1280×720, 1920×1080, a 1280×720 logical window with
2560×1440 framebuffer, and 1024×768 (4:3). It covers initial/normal/selected/hover,
selection and scroll transitions, first/last item, Unicode search, Random, Back
hover, Mode/Mods overlays, Options hover, legacy, fallback, HD-only, 1×1, invalid,
extreme-wide and oversized chrome. Separate configured runs captured Back fade,
scroll at 80ms, selection at 80ms in 4:3, modern thumbnails and 2× framebuffer.

R1 was displayed alongside actual captures; R2 was separately inspected. Findings:

| Region | Result and remaining difference |
|---|---|
| Bottom band/toolbox | 84-ish logical pixel band and widescreen x≈210 Mode origin agree with R1 landmarks. Artwork, labels and native Back differ. |
| Selected row | x≈705 in local 1280 capture versus R1≈708; this alone is not a universal compatibility measurement. Local body is 72 px versus approximately 80–82 in R1. Do not tune against one screenshot without controlling skin/version. |
| Expanded neighbours | Left emphasis and blue siblings exist; curve, spacing and overlap remain provisional. |
| Thumbnail | Geometry now uses documented 9/85 inset and 115/85 aspect instead of 3.5% padding and 93% height. Skin 1.x intentionally omits thumbnails. |
| Search/top | Local search is higher than R1. Stable's grouping shortcut tabs are absent. Metadata hierarchy exists, but font/baselines are not identical. |
| 4:3 / HiDPI | Layout remains usable and bounded, with independent framebuffer scissor conversion. No matched stable 4:3/Retina screenshot was available to assert parity. |
| Malformed skin | Oversized chrome and row texture decoration stay clipped; invalid images fall back. Tiny/transparent authored artwork may intentionally hide controls but does not enlarge hitboxes. |
| Motion | Captures exercise intermediate states and interruption. No stable video timing was obtained, so visual motion equivalence is unverified. |

The first visual pass exposed accidental text changes introduced during renderer
extraction; these were corrected before final verification. A later capture run
failed with a ZipFile invalid-header error when another build replaced its core
JAR. The harness now uses exploded core resources/classes; final verification is
run sequentially. Neither failure is hidden as a successful parity check.

## Automated checks

- 29 new parameterized test cases: 12 layout/gesture/frame-rate cases, 14 PNG
  preflight cases, 3 production navigation/shortcut/drag cases.
- Updated thumbnail expectation with the official SD-dimension rationale.
- Full suite: **677 tests, 0 failures, 0 errors, 0 skipped**.
- Existing Library persistence/import/duplicate and missing-background tests are
  retained and included in the full suite; persistence formats were not changed.
- Gradle build succeeds; desktop harness sources compile as part of check.
- Existing skin tests retain SD/HD/fallback/transparency/version coverage; visual
  fixtures exercise real PNG decode, not only mock textures.

Performance scene: 1,000 sets, 4,000 difficulties, 1,003 projected carousel rows.
Measured locally with shared synthetic artwork: motion mean 0.031 ms / max
0.196 ms (600 samples), idle render CPU submission mean 0.792 ms / max 3.903 ms
(180 samples). These are host-specific CPU timings, not GPU latency or a real
library's I/O benchmark. Motion still scans projected rows; contents/textures are
not rebuilt or loaded on every frame. Visible row geometry allocates bounded
snapshots proportional to visible rows.

## Deliberate policies and unresolved compatibility

- PNG limits: 8192 per dimension, 16 MiPixels, 64 MiB encoded file; validated
  before native allocation. CRC/pixel validity remains the decoder's job.
- Decorative selection/Back/chrome art is confined to bounded bands. Very large
  composite skins may lose intended decoration; this intentionally prioritizes
  the requested malformed-skin safety over stable's documented unbounded bottom
  blocking behavior.
- Direct drag cancels wheel/selection momentum and has no release inertia. Stable
  release inertia, slop and exact wheel dynamics require working-client video.
- Exact row height, curve, expansion/reveal durations, hover displacement,
  selection-click guard, paging distance and top-repeat seam remain provisional.
- Right-button absolute scrolling/context precedence is not implemented here.
- Mode-specific small/medium icons and animated menu-back beyond its first frame
  remain unsupported. Mode/Mods/Options retain existing honest capability limits.
- Numeric difficulty search, metadata-language preference and stable's complete
  grouping tabs are not added. Model/query/score boundaries allow them without
  renderer or persistence rewrites.
- Existing configuration fallback can inherit another provider's skin.ini when
  the selected directory lacks one; official missing-ini/latest semantics still
  need a scoped resolver decision that does not break other screens.

A working offline stable installation and matched measurement skins are needed
to close these evidence gaps. The current offline Wine attempt did not reach
Song Select; no decompilation or protection bypass was used to fill that gap.

Final visual run after the resource-path fix: **76 scenes, 172 PNG captures,
960 scripted transition frames**, navigation/bounds/disposal assertions passed.
The existing Python skin-audit suite also passed (4 tests). Local evidence is in
`/tmp/osujava-stable-research/verified`, `verified.log`,
`verified-comparison.png` and its input-hash manifest. These temporary artifacts
are intentionally not committed; the commands above regenerate the evidence.
