# Song Select redevelopment: reference audit

> The [2026-09-29 reinvestigation](songselect-parity-reinvestigation-20260929.md)
> supersedes the recommendations below to retain deliberate behavioral differences.
> It also corrects the row-state interpretation and identifies structural changes
> required for the user's 1:1 parity objective. Earlier observations remain historical evidence.

Audit date: 2026-09-28. This is a provenance record, not a claim of exact stable parity. Read together with [architecture](architecture.md), [browser](songselect-browser.md), [carousel](songselect-carousel.md), [skin compatibility](songselect-skin-compatibility.md), [placement](songselect-placement.md), and [toolbox](songselect-toolbox.md). Earlier phase documents describe historical states: notably Mode/Mods controls, local scores and played colours now exist, although some earlier documents call them deferred.

## Supplied stable reference and investigation boundary

The supplied `~/workspace/b20230727.9` directory contains `osu!.exe`, `osu!ui.dll`, `osu!gameplay.dll`, `osu!auth.dll`, `osu!seasonal.dll`, and supporting runtime/media DLLs. Its `_staging` is an empty regular file. No source files, screenshots, recordings, beatmaps, skin configuration or design notes were found there. The nearby workspace search also found no `.cs`, `.md`, `.txt`, `.png` or `.jpg` reference material.

Thus the Song Select entry point, component types, update/draw loops, internal selection representation and precise numerical formulas **cannot be traced as source from the supplied material**. Filenames do not establish which assembly owns a particular feature. No internal type/method names or constants are inferred from them. The initial audit used directory inspection only. A subsequent explicitly authorised assembly investigation is recorded below. No supplied files were changed or bundled into osujava.

`wine` and `wine64` are unavailable on PATH; `xvfb-run` exists but cannot run the Windows client alone. The client was not launched, so this audit has no new executable observations of this specific build. In particular, no network-enabled launch was attempted. A future runtime comparison should use a disposable copy in an offline environment, legally supplied maps/skins and screen/input recording, leaving the reference directory untouched.

Public documentation was checked again on the audit date. It establishes outward behaviour, not the exact implementation of build b20230727.9:

- [Official interface](https://osu.ppy.sh/wiki/en/Client/Interface): screen regions, selection colours, clicking, scrolling, filtering and grouping.
- [Official keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts#song-select): navigation and toolbox commands.
- [Official interface skinning](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection): asset roles, anchors and version rules.
- Existing repository measurements in [Phase 2.5](songselect-stable-alignment.md) are historical screenshot measurements and independent osujava choices, **not recovered stable equations**. The historical McOsu survey is secondary evidence, not evidence of stable internals; it was not repeated in this audit.

## Behaviour → current implementation → difference → decision

The following records the initial audit before executable inspection. **The direct CLR/IL findings later in this document supersede the unknowns for component identity, coordinates, row pitch, hover offsets and Page keys.** Remaining unknowns still need investigation; static IL findings are not runtime measurements.

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


## Authorised assembly investigation follow-up

After the initial audit, the user explicitly authorised inspection of the DLLs and executable as reference material. Existing `monodis` was used read-only for CLR type, method, field, manifest and selected user-string metadata. No protected string decoder, obfuscation remover, runtime patch, authentication component analysis or protection bypass was used. No original code was transferred to Java.

Confirmed binary facts (metadata tokens are scoped to each named assembly):

| Assembly / reference | Confirmed fact | What this does not establish |
| --- | --- | --- |
| `osu!ui.dll`, TypeDef `0x02000002`, `osu_ui.ResourcesStore` | Only a resource-wrapper type beyond the module; MethodDefs `0x06000001`–`0x06000004` are constructor, ResourceManager getter and Culture getter/setter. Manifest has `osu_ui.ResourcesStore.resources`. | This DLL does not expose a Song Select update/layout class. Resource names are not evidence of drawing behaviour. |
| `osu!gameplay.dll`, TypeDef `0x02000002`, `osu_gameplay.ResourcesStore` | Same four-method resource-wrapper structure. | Its filename does not imply it contains gameplay or Song Select logic. |
| `osu!.exe`, TypeDef `0x02000ae4`, `osu.Graphics.Skinning.Skin` | Named skin configuration type; method metadata includes generic key/value access and dictionaries. Most methods retain obfuscated names. | These signatures alone do not prove asset resolution order, density policy or Song Select geometry. |
| `osu!.exe`, TypeDef `0x02000aec`, `osu.Graphics.Sprites.Origins` | Named sprite-origin enumeration exists. | No call site associates an enum value with a Song Select asset in this investigation. |
| `osu!.exe`, `osu_common.Helpers.OsuString`, FieldDefs `0x040039ef`/`0x040039f0` | `SongSelection_Group` and `SongSelection_Sort` identifiers exist. | Localisation identifiers confirm concepts, not algorithm, grouping boundaries or ordering. |
| Same enum, FieldDefs `0x040039f2`/`0x040039f3` | `Options_SongSelect_Thumbnails` and its tooltip identifier exist. | No thumbnail coordinate or enabled-default value is implied. |
| Same enum, FieldDefs `0x04003bae`/`0x04003bb5` | `SongSelection_DifficultyFilteredWarning` and `SongSelection_NoMapsVisible` identifiers exist. | No filtering predicate or selection-repair policy is established. |

Most executable application types/methods have names of the form `#=...`. Searches for named Song Select entry points and plaintext `songselect`, `menu-button-background`, `selection-random`, `selection-mode`, `selection-mods`, `selection-options`, and `SongSelectActiveText` in CLR user strings yielded no matches. This is insufficient to identify a Song Select class or to label arbitrary numerical constants as layout/easing values. No attempt was made to recover protected strings or rename/deobfuscate the program.

A normal-mode IL disassembly attempt failed with exit 139 when Mono could not resolve `System.Runtime.Serialization, Version=4.0.0.0`. The incomplete output did not identify Song Select. This tool unexpectedly also emitted embedded-resource sidecar files into the working directory; all five generated files were immediately removed without inspecting or using their contents. None entered Git or the application. Future inspections must use metadata-only commands or a disposable directory and a reader that does not automatically emit embedded resources.

This first metadata pass narrowed the location evidence. A second, dependency-free raw CLR/IL inspection then identified the Song Select components and several behavioural calculations, as recorded below. The failed Mono output was not used for behavioural conclusions.


## Direct CLR/IL findings (supersedes initial unknowns where stated)

After the user additionally allowed ordinary obfuscation analysis, `dnfile 0.18.0` and `dncil 1.0.2` were installed into a disposable `/tmp` virtual environment. They parsed metadata and method instructions directly without loading/executing the client, resolving encrypted strings, or emitting embedded resources. Local method listings and investigation scripts remain outside Git. The inspected executable SHA-256 is `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a` (21,005 MethodDefs). Tokens below refer to that executable only.

### Identification and update ownership

Matching an arbitrary integer to a localisation enum is unreliable. Instead, candidates were confirmed by enum arguments immediately passed to the string-returning localisation function MethodDef `0x0600392d` (decimal 14637). That cross-reference identifies:

| Role | Metadata evidence |
| --- | --- |
| Song Select screen | TypeDef `0x02000297`, retained name `#=zBwZS7ysXCeyfdByozgxkLRyYAlfe1dkW4Q==`; constructor `0x0600136e` builds Group/Sort/Search/ranking labels; `0x06001376` prepares grouping/sorting choices |
| Search result display | `0x060013ad` uses singular/plural matching-count and reset identifiers |
| Selected details | `0x060013ae` uses beatmap info, secondary info and creator identifiers |
| Score presentation | `0x060013b4` uses score-list/no-record/score-tooltip identifiers |
| Options | `0x0600138d` references clear local scores, collections, delete, edit and this-beatmap labels; these are evidence of a distinct menu, not permission to add those actions to osujava |
| Carousel owner | Screen field `0x04000b00`, type `#=zckk4mG0z5wdHRXedao4MY01g25Tplv8oUYg32S3E7XSghrpVeA==`; constructor `0x0600321a`; screen update `0x0600139f` dispatches its update |
| Carousel update | `0x06003255` invokes input/movement work and `0x06003267` row placement/visible-range processing. Row positions, hover index, selected index, keyboard focus and scrolling velocity are distinct state |

These names are cross-reference handles only; Java components should keep meaningful independent names and their own structure.

### Coordinate basis and row layout

The display scale getter `0x06001dcb` divides the display-height field `0x04001396` by **480**; `0x06001dd0` returns its reciprocal. Pointer conversion in the carousel divides screen coordinates by that scale. Thus the following numbers are **480-high logical UI units**, not SD texture dimensions or physical framebuffer pixels. For a 720-high logical viewport the corresponding factor is 1.5; @2x image density must not multiply it again.

- The carousel's horizontal reference `0x0600322c` is viewport width expressed in those logical units, with a 340-unit right-side reservation. This establishes a right-relative reference rather than a fixed screen X.
- Layout rebuild `0x0600325a` advances eligible visible rows by **48 units**, beginning at logical content Y **200**. Some group/subtype boundaries add **10 units**. This calculation does not read the row PNG dimensions. Exact subtype-boundary semantics remain to be mapped; do not apply the extra gap indiscriminately.
- Selection/focus reveal `0x06003250` requests the target row at **Y 220** via `0x06003251`/`0x0600321e`. The index can be the selected or keyboard-focused row according to state. This is not always viewport midpoint, and end-range clamping can prevent exact placement.
- Horizontal curve helper `0x0600325c` is linear in distance from the **240-unit** vertical center: at Y 0 or 480 its contribution is **37.5 units**, bounded at **200** for distant rows. `0x0600325e` feeds animated row Y plus scroll and predicted remaining movement into this helper; merely using a static row center would miss its motion coupling.
- Hover index `0x04001f67` is assigned by pointer hit checks in `0x06003256`/`0x06003267`. Matching it in `0x0600325d` adds **45 units leftward**. `0x0600325f` separates rows before/after a valid hover by **10 units** each. Other 50-unit X adjustments have subtype/expanded-state predicates whose meaning was not completely established; they are not adopted as an unconditional selected offset.
- `0x06003266` eases X and Y toward row targets separately, with elapsed-time exponentiation. This differs from osujava's exact critically damped viewport spring. No animation algorithm is transplanted; retaining the tested independent spring is a conscious compatibility tradeoff.

### Navigation, click and focus

Keyboard handler `0x06003247` maps Page Up/Down to **−10/+10 eligible entries**, Up/Down to **−1/+1**, and ordinary Left/Right to a separate group/Set candidate policy. The Page keys therefore do not derive their travel from viewport height. The broad public-wiki description “page scroll” does not specify this build's actual step count.

Shared traversal `0x06003276` walks the row list cyclically, counts eligible candidates, skips excluded/collapsed candidates and stops traversal if it returns to the starting index. On that full-loop exit it still passes the starting index to the destination handler: it neither stops at the preceding entry nor returns early without dispatch. Thus fewer than ten eligible entries can make Page return to the starting entry after one circuit, with focus/selection handler effects still possible. Page and arrow navigation share the same row-eligibility flags. Predicate `0x06000fc5` tests whether the row's state enum is nonpositive; it does not test a layout extent. Further subtype and exclusion flags also participate. This interpretation was corrected by tracing the field signature and state setter in the [2026-09-29 reinvestigation](songselect-parity-reinvestigation-20260929.md).

Destination handler `0x06003277` immediately uses the selection path for a candidate within the selected Set; for a different Set it stores a **keyboard focus index** `0x04001f6b` and emphasizes that row. Enter in `0x06003247` checks that focus and activates it before the ordinary play callback. Selection and focus must therefore not be described as interchangeable. The exact subsequent dwell/auto-activation conditions are not established by this trace; osujava may retain immediate selection deliberately, but must document that difference.

Pointer-down handler `0x06003243` saves the hovered candidate. Pointer-release handler `0x06003244` verifies the candidate, rejects a drag, checks release containment and only then invokes selection/activation. This provides direct evidence for **release-to-select**, distinct from osujava's previous press-to-select flow. The exact legacy hit rectangle still depends on the row hit-test method and skin geometry, which were not fully mapped.

### Scrolling and motion model

Opposite directional input callbacks `0x06003245`/`0x06003246`, registered together by the carousel constructor, change velocity rather than a row-count target and clear selection-follow state. From rest their impulse magnitude is **0.4** in the internal velocity units; repeated input increases it with existing speed and uses damping base **0.994**. The impulse is 0.4 at rest, 0.8 at speed magnitude 2, and reaches its 2.4 cap at magnitude 10; intermediate values vary linearly with speed magnitude. Opposite-direction input subtracts that impulse from existing velocity rather than first resetting velocity to zero. Time units were subsequently traced below. Callback invocation count per physical notch remains an input-dispatch detail; these numbers specify one callback, not arbitrary device hardware.

Motion update `0x06003254` exponentially decays velocity using shared elapsed time, integrates the distance analytically, normalizes by total content travel and clamps scroll fraction to 0–1. Default damping base is approximately **0.996**; target-follow calls use other values such as **0.992**. `0x0600324f` derives starting velocity from target distance and a logarithm of the chosen damping base. This is an inertial exponential model, not the same model as osujava's second-order spring. The values are evidence of different behaviour, not suitable standalone constants to paste into Java without their units and integration contract. Shared clock getter `0x06003d05` converts `Stopwatch.ElapsedTicks / Frequency` to milliseconds. Main timing `0x06002316` stores elapsed milliseconds in field `0x040016fa`, and elapsed time divided by 16⅔ ms in field `0x040016ac`. The scrolling exponent uses the former: velocity is logical units/ms and its damping base applies per ms. The row X/Y easing in `0x06003266` uses the latter: its 0.95/0.875 factors apply per 60-Hz-equivalent time step. From rest, one 0.4-unit/ms callback with 0.994 damping has approximately 66.47 logical units of asymptotic travel (about 1.38 ordinary 48-unit row pitches), before bounds or further input. This is an analytical consequence of the observed model, not a measured device result.

### Adopted and intentionally retained behaviour

Direct binary evidence supersedes the initial Page-key assumption and permits independent layout metrics to use the confirmed 480-high coordinate basis. Row pitch must be independent from unusual texture padding/dimensions, while image placement can preserve the authored asset. Hover and selection remain distinct.

Keep tested osujava spring convergence, identity/empty-library repair, Unicode fit, local-only services and provider fallback unless a scoped implementation step changes them with regression tests. Do not claim exact stable motion, Set-focus timing, raw hitboxes, font baselines, Random distribution or asset resolver internals: those remain partially or wholly unverified. The client itself has still not been executed, so direct screen comparisons remain outstanding.

## Follow-up chrome coordinate trace (2026-09-28)

Same reference executable/hash as above, read-only. Method/field tokens below are
identifiers in this build, not portable API names. No restored code is included.
Runtime startup failed before Song Select; see `song-select-repair.md`. This table
records static observations and their limits, not live visual parity.

| Stable specification / evidence | Stable implementation locator | osujava current behaviour | Follow-up decision |
| --- | --- | --- | --- |
| Standard UI scale is screen height / 480; asset canvas scale is screen height / 768 | Display constructor `0x06001dc8` initializes reference height field `0x04001397` to 768; getters `0x06001dcb`, `0x06001dd1` | Carousel uses height/480; chrome uses height/768; UiLayout then maps once to window/framebuffer | Retain. Greylooks dimension logs show no duplicate density factor |
| Texture width/height accessors divide underlying dimensions by a texture divisor | `0x06000739`, `0x0600073a`, divisor field `0x0400033c` | Resolver normalizes SD/@2x once | Retain; the complete divisor initialization/lookup chain has not been established here |
| Bottom sprite uses BottomLeft origin at standard `(0,480)`; X scale is derived from screen width divided by image width×0.625, Y scale remains 1 | Song Select constructor `0x0600136e`, offsets `1c94–1d10`; sprite constructor `0x060040a7`, Origins enum `0x02000aec`, origin calculation `0x060040bc` | Bottom image stretches in X, uses native SD height×height/768, anchored at bottom 0 in bottom-left Java coordinates | Retain ordinary sizing. Giant-image clipping/reservation still unverified; do not label the 30% cap a Stable rule |
| Top uses TopLeft origin, standard `(0,0)`; additional right extension uses a one-column crop | `0x0600136e`, offsets `1b3a–1c2c`, crop fields `0x04002852`/`0x04002851`; sprite field type 6 scales position by height/480 in `0x060040af` | Top-left native SD artwork plus repeating edge strip | No change: physical-width threshold and texture crop interpretation require further verification |
| Legacy selection artwork uses standard top-left Y=426; newer artwork uses bottom-oriented field type 12 and BottomLeft origin; widescreen X=140 versus 120, then first step 57.6 and later steps 48 | `0x0600136e`, offsets `0803–0ef8`; `0x060040af` field-type dispatch | Legacy top uses 87 SD above bottom; modern bottom at 0; X=224/192 SD, widths 92/77 SD | Existing values are near-rounded asset-canvas equivalents, not identical constants. Do not silently claim exact parity (legacy top differs by 0.6 SD) |
| Back artwork uses BottomLeft at standard `(0,480)` | Back controller constructor `0x06003a15`, sprite origin code `0x060040bc` | Native SD bottom-left, alpha-bounded input restricted to navigation slot | Retain anchor. Full Stable hit policy/animated sizing not confirmed here |
| Row pitch=48, selection target=220, horizontal reference=screen logical width−340, hover displacement=45 and neighbour displacement=10 in 480-high coordinates | Previously traced `0x0600325a`, `0x06003250`/`3251`, `0x0600322c`, `0x0600325d`/`325f`; preceding sections document clock/input/call chain | Converted once into 720-high UI; local expansion/spring and group indentation remain | Overlay now exposes actual computed rectangles and target line; no new claim that local group offsets reproduce Stable |
| Pointer press/release candidate, wheel velocity/decay and cyclic keyboard/page traversal | Previously traced `0x06003243–3247`, `3254`, `3276–3277` | Shared animated row hitboxes, local wheel spring and immediate keyboard selection | No behaviour changes in this pass; prior intentional differences remain |
| Metadata title and detail text positions are distinct; constructor includes title at `(21,-3)`, subsequent positions `(23,12)`, `(1,24)`, `(1,36)`, `(1,48)`, `(1,56)` | `0x0600136e` beginning around offset `03d3` | Existing 720-high metadata layout and local ranking panel | Parent transforms, text origin and font scaling remain to be resolved. These literals alone do not justify moving Java labels |
| Thumbnail, ranking panel overall bounds and corner Cookie | Not established by this follow-up; old public-reference observations remain limited | Existing thumbnail version gate, local ranking panel and generated java Cookie | No speculative sizing change; retain as explicitly unresolved parity items |

The scale relation `0.625 = 480/768` explains why skin image dimensions and
standard UI coordinates cannot be used interchangeably. A 90-SD-pixel bottom image
has height `90×720/768 = 84.375` at 720p, not 90×720/480. This particular dimension
matches current osujava; it does not validate its layering or giant-asset policy.
