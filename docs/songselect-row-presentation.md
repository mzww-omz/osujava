# Song Select Phase 2: row presentation

Phase 2 changes row contents, not carousel physics. `SongSelectCarousel` is unchanged: viewport/scroll velocity, velocity influence, selection spacing/spring, expansion/reveal, hover suppression and final render bounds retain Phase 1 behavior. The selected Set still becomes all its difficulties; there is no additional selected parent card. Renderer and hit testing continue using the same visible snapshots and selected-row priority.

## Content and geometry

`SongSelectRowPresentation` separates cached content, optional star presentation and pure local geometry. Collapsed Sets show a bold Title, secondary Artist / Mapper, and muted difficulty count. Expanded children show the same Title and byline, with a separate bold Difficulty name. Selected children use the active skin colour, stronger type size, full thumbnail brightness and active stars; siblings keep the blue tint and readable inactive colours. There is no glow, scale animation or rounded card.

Text width is limited by both the row and the visible right edge, including during scrolling. Title and Difficulty have different sizes; bylines/counts use smaller, quieter type. Shared `SmoothUiFont` supports cached bold labels and grapheme-safe ellipsis through `UiTextFit`. Measuring reserves raster padding and quantizes width downwards so cache reuse cannot overflow a narrower fractional width. No font shrinking is used to fit long text. System SansSerif supplies Unicode glyphs; actual coverage (especially emoji) remains dependent on installed system fonts.

Thumbnails have fixed geometry regardless of load state. Their cover crop is centered inside the row, with the existing legacy aspect policy and v2.2 115:85 policy retained. Linear filtering and oversampled type serve the existing high DPI layout. Newly resident thumbnails fade over 110 ms using the screen delta. Successful, missing and corrupt paths are cached; resident lookups avoid file-system stat calls. Fading does not request textures or change interaction bounds. The existing bounded 18-entry synchronous texture cache remains; first decoding of a large uncached file can still stall. This phase does not introduce an asynchronous image pipeline.

The last text line shares a bounded trailing accessory region with stars. That region can later host a grade badge by reducing the text budget; no grade data or empty badge column is introduced.

## Rating availability and assets

Neither BeatmapDifficulty, the parser, Library storage nor the local importer provides a trusted calculated Star Rating. OD/AR/CS are settings, not ratings. Production therefore supplies `OptionalDouble.empty()` and shows **no stars or numeric placeholder**, leaving the whole Difficulty line for text. No model/storage format, calculator, Gameplay or online API changes were made.

The screen's optional-rating constructor is a UI seam for already-calculated, trusted local values. It is called once per difficulty on content-cache rebuild, never in rendering. The harness supplies explicitly synthetic fixtures to exercise present ratings; these values are not written to a Library or used in production.

Present finite non-negative ratings use literal full/partial icons up to seven stars and a two-decimal numeric label. Partial icons fill horizontally over a quiet whole-star silhouette. Above seven, one icon plus the numeric value keeps width bounded; huge finite values use scientific notation. Invalid/negative values are treated as absent; a known zero has an unfilled icon and `0.00`. Selected/inactive icon tint follows SongSelectActiveText / SongSelectInactiveText.

`SongSelectSkinAssets` resolves `star` using its existing current skin → configured fallback → bundled Greylooks priority, @2x before normal within each provider. Decode failures try the next candidate. Only when all candidates fail does the screen prepare a small procedural star, once at show; it is owned/disposed with the skin textures. Greylooks has no special rendering branch. Other existing assets and self-contained skin.ini interpretation are unchanged. Explicit text colours always win; absent active colour uses light text on skin-backed rows and dark text on the light procedural selection.

## Reference and independent implementation

The public McOsu row sources cached during Phase 1 were inspected conceptually: separate title/subtitle/difficulty roles, stronger difficulty type, selected/inactive skin text, thumbnail integration and a trailing grade/star accessory. This phase does not copy C++ code, layout equations, pixel measurements, constants, star calculation or animation formulas. All geometry, capped icon policy, caching, ellipsis and fade behavior were independently designed for osujava. No McOsu binary comparison was performed.

## Verification

Run:

```sh
./gradlew build :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-songselect-phase2
```

New unit coverage includes absent/invalid/zero/low/full/partial/high/huge ratings, collapsed/child content, one/16 difficulty Sets, bounded geometry and legacy/v2.2 ratios, missing thumbnails, fade progression, long English/Japanese/Chinese/Korean/symbol/combining/emoji fitting, and missing/corrupt/@2x/provider-priority star resolution. Existing 422 tests remain, including motion, wheel, navigation, final-bounds hit priority and skin compatibility.

The production harness retains all 90 Phase 1 scenes and 234 motion frames, and adds Phase 2 idle/hover, long collapsed title/title/mapper/difficulty, Japanese/Chinese/Korean/symbols, absent/low/5/fractional/high ratings, one/many children, missing/corrupt/portrait/wide thumbnails, missing/corrupt/@2x stars, default fallback and v2.2 cases. All run at 1280×720, 1920×1080 and 1280×720 with a 2560×1440 framebuffer. Thumbnail fade captures use a deliberately cold cache after entrance at frames 0/2/4/8, asserting unchanged bounds. Rating availability, procedural fallback, @2x selection and disposal are also asserted. Captures/fixtures remain outside Git.

Performance probes separately report Carousel.advance and warmed idle production render CPU submission with 1000 Sets. They use shared fixture artwork and do not measure GPU completion, worst-case image decoding, physical input devices or other hardware.

Final verification: `./gradlew build :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-songselect-phase2` succeeded. All **453 tests** passed (422 retained + 31 added), with zero failures/errors/skips. The harness passed **171 scenes** and saved **246 transition frames** (234 retained motion frames + 12 fade frames). Manual screenshot review covered idle entry/selected/expanded/hover states, Greylooks and default/programmatic fallbacks, long text and all requested languages, literal/partial/high stars, normal/missing/corrupt/portrait/wide thumbnails and all three resolution/density profiles. No motion or final-bounds regression was observed. Corrupt fixture decode warnings are expected.

The final 1003-row motion probe averaged 0.006–0.007 ms (maximum 0.043 ms). Warmed idle render CPU submission averaged 0.218–0.229 ms (maximum 1.400 ms) across the three profiles. These local shared-artwork results do not remove the known synchronous first-decode limitation or establish a GPU/cross-hardware guarantee. Production ratings remain unavailable and system-font coverage remains OS-dependent.
