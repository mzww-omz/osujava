# Song Select redevelopment: reference audit

Audit date: 2026-09-28. This is a provenance record, not a claim of exact stable parity. Read together with [architecture](architecture.md), [browser](songselect-browser.md), [carousel](songselect-carousel.md), [skin compatibility](songselect-skin-compatibility.md), [placement](songselect-placement.md), and [toolbox](songselect-toolbox.md). Earlier phase documents describe historical states: notably Mode/Mods controls, local scores and played colours now exist, although some earlier documents call them deferred.

## Supplied stable reference and investigation boundary

The supplied `~/workspace/b20230727.9` directory contains `osu!.exe`, `osu!ui.dll`, `osu!gameplay.dll`, `osu!auth.dll`, `osu!seasonal.dll`, and supporting runtime/media DLLs. Its `_staging` is an empty regular file. No source files, screenshots, recordings, beatmaps, skin configuration or design notes were found there. The nearby workspace search also found no `.cs`, `.md`, `.txt`, `.png` or `.jpg` reference material.

Thus the Song Select entry point, component types, update/draw loops, internal selection representation and precise numerical formulas **cannot be traced as source from the supplied material**. Filenames do not establish which assembly owns a particular feature. No internal type/method names or constants are inferred from them. No decompiler, disassembler, extraction or protection bypass was used. No supplied files were changed or bundled into osujava.

`wine` and `wine64` are unavailable on PATH; `xvfb-run` exists but cannot run the Windows client alone. The client was not launched, so this audit has no new executable observations of this specific build. In particular, no network-enabled launch was attempted. A future runtime comparison should use a disposable copy in an offline environment, legally supplied maps/skins and screen/input recording, leaving the reference directory untouched.

Public documentation was checked again on the audit date. It establishes outward behaviour, not the exact implementation of build b20230727.9:

- [Official interface](https://osu.ppy.sh/wiki/en/Client/Interface): screen regions, selection colours, clicking, scrolling, filtering and grouping.
- [Official keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts#song-select): navigation and toolbox commands.
- [Official interface skinning](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection): asset roles, anchors and version rules.
- Existing repository measurements in [Phase 2.5](songselect-stable-alignment.md) are historical screenshot measurements and independent osujava choices, **not recovered stable equations**. The historical McOsu survey is secondary evidence, not evidence of stable internals; it was not repeated in this audit.

## Behaviour → current implementation → difference → decision

The following describes the implementation at audit start. A statement marked **unknown** needs external measurement before it can justify a compatibility change.

| Topic | Stable evidence | osujava at audit start | Difference and redevelopment decision |
| --- | --- | --- | --- |
| Screen structure | Public interface shows metadata/rankings left, carousel right, browser controls above and toolbox below | `SongSelectScreen` orchestrates these; browser, carousel, toolbox geometry, skin holder and presentation already have separate classes | Preserve those owners; extract screen drawing/update responsibilities rather than replace working models |
| Row reference coordinates | Exact stable origin/base resolution **unknown** | Downward content coordinates in `SongSelectCarousel`, converted into bottom-left UI coordinates; first center starts at half viewport height | Keep explicit content-to-viewport conversion; do not label current coordinates stable constants |
| Row pitch | Exact body/pitch relationship **unknown** | Body-scaled set/child pitch; headers have separate spacing | Centralize metrics; retain established density until a timed reference proves a difference |
| Selected expansion | Public carousel distinguishes expanded siblings and selected difficulty | Selected Set becomes difficulty entries; parent disappears, other Sets stay collapsed | Preserve identity and expansion model; no new duplicate selected parent |
| Neighbour movement | Exact displacement **unknown** | Selected and hovered rows add independent signed neighbour displacement | Preserve tested transient offsets and interrupted-motion continuity |
| Horizontal curve | Curved appearance historically measured from screenshot; equation **unknown** | Bounded rational curve based on distance from viewport center; expanded group, selection, hover and speed add offsets | Retain independent formula; centralize its parameters |
| Scroll target | Exact stable anchoring **unknown** | Selection targets viewport center, padded limits let first/last center; wheel does not alter selection | Keep selection/wheel separation and boundary tests |
| Velocity/damping | Exact stable wheel distance, inertia and easing **unknown** | Wheel unit moves one row height; critically damped viewport spring; separate decaying decorative velocity | Keep analytic time-based spring; never claim sampled stable timing |
| Selection/hover transition | Exact times and hover retention **unknown** | Eased independent hover/selection/group/reveal fields; hover alone does not select | Preserve hover/selection separation; measure stable before retuning |
| Click | Official interface: click selects; another click or Enter plays | Final visible bounds are hit-tested; selected row has priority; expansion double-click guard prevents accidental start | Keep render/hit geometry shared; guard is a documented independent policy |
| Wheel/drag | Official interface describes wheel, left drag and right-button absolute scroll | Wheel supported; drag/absolute scroll absent | Minimum requested inputs remain; drag/absolute scrolling requires dedicated later interaction design |
| Keyboard | Official shortcuts: Up/Down difficulty, Left/Right beatmap, Page Up/Down page, Enter activate | Difficulty/Set navigation exists; Page keys historically act as Set navigation | Page semantics are a confirmed outward difference, not a physics issue; address independently if changed |
| Escape/Back | Public interface returns to menu | Modal/menu/search priority before Back; skin artwork and interaction bounds separate | Preserve priority and bounded Back hitbox; exact stable hitbox **unknown** |
| Random | F2 random; Shift+F2 previous | Filtered Set candidates, avoid current if alternatives; bounded identity history | Exact stable candidate distribution/history on filter changes **unknown**; retain deterministic testable local policy |
| Group/Sort | Public interface supports criteria and expandable groups | `SongBrowserModel`: title/artist/creator/BPM/length; immutable grouped entries; headers not collapsible | Keep trustworthy local metadata. BPM buckets currently use 50, while wiki specifies 60; this is an intentional existing difference |
| Difficulty ordering | Stable public difficulty sort uses rating and may split Sets | All difficulties retained in Library order inside a Set; no production trusted star calculator | Do not invent ratings or reorder using OD as a substitute |
| Filtering | Public interface searches all words and supports numeric filters per difficulty | NFC/case-normalized all-token metadata filter at Set scope, query bookmark repairs selection | Preserve safe empty/filter selection; per-difficulty/numeric syntax is a distinct future feature |
| Title/artist/mapper/difficulty | Public screenshot establishes hierarchy; exact font sizes, baselines and truncation algorithm **unknown** | Cached title/byline/difficulty content; active/inactive skin text; stronger child difficulty; Unicode ellipsis | Extract rendering intact; keep grapheme-safe fit and acknowledge system font coverage |
| Thumbnail | Skin specification: version 2.2+, 115×85 SD thumbnail, 9 SD pixels from image left | Version gating, fixed cover bounds, resident fade and bounded cache | SD values are skin canvas units, not framebuffer pixels; retain density-normalized independent geometry |
| Stars | Skin specification: `star` tinted; older partial crop versus 2.2+ scale | Optional trusted supplied ratings; absent production ratings hide stars; bounded nine-slot policy | Preserve honest absent state; exact extreme-rating presentation intentionally differs |
| Row image | Skin specification: multiplicative, bottom-left, suggested minimum 690×85 SD | `menu-button-background` tinted; substantial-alpha body estimate; body bounds separate from image | Suggested image size is not a universal interaction rectangle; preserve placeholder safety |
| Top/bottom | Top-left top artwork with repeated right edge beneath; bottom-left bottom stretches across width | Dedicated chrome/coverage policy, native-scale artwork and fallback surfaces | Exact repeat origin is resolution-dependent and not fully specified; retain tested local policy |
| Back/controls | Separate Back and Mode/Mods/Random/Options assets, version-dependent control anchors | `SongSelectToolboxLayout` supports native composite art and independent bounded control hitboxes | Preserve oversized artwork/padding/transparent normal with hover; never fit the whole image to the button |
| Mode/Mods/Options | Public toolbox exposes all three | Capability-aware Mode/Mods, no implemented ordinary gameplay Mods; Options unavailable feedback | Do not fabricate gameplay support or enable server/editor/destructive actions merely to mimic UI |
| SD/@2x/fallback | Skin format is density-aware; exact binary resolver chain **unknown** | Current → configured fallback → bundled; @2x then SD within provider; malformed tries next; valid transparent wins | Keep existing tested chain and self-contained skin.ini configuration |
| Background/dim | Background visible behind UI; exact stable dim/timing/decode pipeline **unknown** | Selected difficulty path then Set fallback; fade and dim; cached synchronous textures | Preserve local Library paths; move IO out of drawing where possible; async decoding is a separate lifecycle change |
| Virtualization | Exact stable visible-range implementation **unknown** | Logical entries cached; only visible snapshots rendered; settled off-screen easing skipped | Preserve idle model caching and bounded texture reuse; no claim of stable internals |

## Numerical provenance and coordinate rules

These values were read from **osujava**, not from the stable binaries, at audit start:

- Carousel spring rate `18` is reciprocal seconds in the analytic critically damped update. Decorative velocity decay `5` is an exponential rate; speed limit `36 × rowHeight` is logical units per second. They are not per-frame factors.
- Selected centering subtracts half the carousel viewport height from the logical row center. Expansion preserves the previous on-screen representative position across entry-list changes.
- Curve uses viewport width and normalized distance from viewport center; group displacement uses `0.052 × width`; selected/hover offsets use logical UI units. Neighbour spacing uses fractions of row height.
- Screen background fade is `0.22 s`; existing thumbnail fade is documented as `0.11 s`. Neither is measured stable timing.
- Toolbox asset placement uses the established 768-high legacy skin canvas; UI rendering/framebuffer density is a separate transform. Public SD asset sizes must not be multiplied by @2x density twice.

Metrics consolidation should name the basis (viewport fraction, logical UI units, SD canvas units, body fraction, seconds or reciprocal seconds) next to each parameter. No stable constant should be claimed without a source or a reproducible measurement.

## Drawing and ownership

At audit start the screen's `render` method also polls clicks, advances animation, refreshes score state and constructs visible rows before drawing. Although row drawing itself does not select a beatmap, this combined lifecycle obscures the renderer/state boundary and is the main structural issue.

Current composition deliberately places large composite selection artwork **behind** rows and the independent Cookie. A simplistic change that moves every bottom image above every row would break skins whose Mode image contains broad decorative chrome. Preserve this exception while making passes explicit:

1. Update input/model, transient animation, layout and resource residency before drawing.
2. Background and dim; decorative chrome surfaces and composite selection artwork.
3. Clipped rows, with selected row composited last within the row layer.
4. Cookie, metadata/rankings, Back and browser/control labels.
5. Menus, toolbox modal, feedback and global cursor/overlay ownership.

This is osujava's documented composition policy, not a recovered stable draw loop. The audit does not establish stable background decoding threads, culling structures, exact depth values or font implementation.

## Preserve, move, rewrite, add

- **Preserve:** Library/Importer separation, browser identity model and repair, filtered Random history, spring and interruption handling, immutable visible hit geometry, skin resolver/provider rules, composite art, Unicode fit, score snapshot and local capability honesty.
- **Move:** screen row drawing into a dedicated renderer; viewport/row metrics into one model; per-frame state updates and residency preparation out of drawing; selected detail preparation into a cache owner where useful.
- **Rewrite only with evidence:** duplicated layout calculations or mixed ownership; do not replace existing physics, ordering or skin policies just for structural uniformity.
- **Add:** explicit update/draw boundary checks, metric/resolution tests and regression coverage for extracted rendering; document unresolved stable measurements rather than filling gaps with guesses.

## Remaining reference work

Exact stable scroll/selection/hover timing, one-notch wheel travel, selected-row expansion, curve coordinates, click-down/up timing, native Back bounds, font baselines and background fade remain unmeasured for b20230727.9. A future offline experiment should record identical maps at 720p/1080p and SD/@2x, timestamp input plus frame captures, then compare normalized positions over time. Record FPS, viewport, skin version/dimensions, input units and selection identity. Tests should target those outward measurements; recovered proprietary code is unnecessary.
