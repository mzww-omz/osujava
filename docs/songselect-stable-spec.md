# Song Select: stable compatibility specification

Research date: 2026-09-28. Authority is **osu!stable**, not the previous
osujava implementation. This document was written before inspecting that implementation.
No proprietary code or extracted client assets are incorporated.

## Evidence and interpretation

- **CONFIRMED**: explicitly documented by official stable documentation, or directly
  visible in the identified reference image (only for that image).
- **STRONGLY INFERRED**: consistent with references; not a measured universal rule.
- **UNCERTAIN**: cannot yet be established. An implementation choice here is a
  provisional local policy, not a claim about stable.

Sources (public documentation retrieved on the research date):

- [S1: Interface](https://osu.ppy.sh/wiki/en/Client/Interface), particularly carousel,
  search, metadata and toolbox.
- [S2: Interface skinning](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection).
- [S3: skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini), versions and colours.
- [S4: Keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts).
- [R1: official stable screenshot](https://raw.githubusercontent.com/ppy/osu-wiki/master/wiki/Client/Interface/img/song-selection.jpg),
  1280×720, unspecified client build. Contains online UI; osujava remains offline.
- [R2: carousel detail](https://raw.githubusercontent.com/ppy/osu-wiki/master/wiki/Client/Interface/img/beatmap-cards.jpg),
  749×426 crop, not an independently sized game window.

R1/R2 were visually inspected. They are historical default-skin examples, not
proof of current version timing or custom-skin edge cases. External references
are not bundled assets. McOsu is not used as authority. Lazer is not used as a
visual/interaction specification. Existing local client binaries may be used
only through normal offline operation; updater/protection failures are not bypassed.

## Layout and coordinates

| Area | Observable contract | Confidence / evidence |
|---|---|---|
| Reference space | Height-based logical UI with horizontally extended widescreen space is a useful independent representation; exact stable internal coordinates are not established | STRONGLY INFERRED, R1 and S2 SD dimensions |
| Top chrome | Metadata occupies left; group/sort/tabs occupy right; list extends underneath upper chrome | CONFIRMED, R1 |
| Bottom chrome | Full-width bottom band; Back at left; Mode, Mods, Random, Options in sequence; play control at right | CONFIRMED, R1 |
| Left metadata | Artist/title/difficulty, creator, length/BPM/object counts and difficulty values; local scores below | CONFIRMED, S1/R1 |
| Right list | Right-anchored cards extend beyond screen edge; selected set expands into difficulties | CONFIRMED, S1/R1 |
| Search | Upper-right, immediately below tabs; typing starts search without explicit focus click | CONFIRMED, S1/R1 |
| Stars | A separate bottom line within difficulty cards | CONFIRMED, R1/R2 |
| Clipping | Cards occluded by upper/lower chrome; right screen edge clips content | CONFIRMED, R1 |
| Widescreen / 4:3 | Extra width must not double UI height or texture density; exact 4:3 placement is unmeasured | STRONGLY INFERRED / UNCERTAIN |
| HiDPI | Texture density is separate from logical placement; HD assets describe greater sampling density | CONFIRMED, S3 |

### Measurement ledger (R1, approximate screen pixels)

These are image measurements, **not** undocumented exact stable constants.
JPEG edges and overlapping shadows give about ±3 px uncertainty.

| Landmark | R1 observation | Confidence |
|---|---|---|
| Bottom band top | y≈637 (83 px visible band) | CONFIRMED for R1 |
| Mode left, width | x≈210, width≈86 | CONFIRMED for R1 |
| Other toolbox widths | ≈72 each | CONFIRMED for R1 |
| Selected card | left≈708, top≈289, bottom≈371 (82 px) | CONFIRMED for R1 |
| Adjacent difficulty | left≈720, top≈202, bottom≈282 | CONFIRMED for R1 |
| Selected thumbnail | x≈716..814, width≈98 | CONFIRMED for R1 |
| Lower inactive cards | left≈786,798,810,820; heights≈74..81, overlapping edges | CONFIRMED for R1 |
| Search baseline | near y≈94 | CONFIRMED for R1 |

A 768-high reference representation gives scale 720/768=0.9375. The documented
85-high panel and 90-high buttons then give 79.7 and 84.4 screen pixels, consistent
with R1. This is **STRONGLY INFERRED**, not confirmation of stable's internal space.
A 480-high space with a separate 1.6 asset factor is mathematically equivalent.
Do not silently mix those spaces. Record parameter units in code.

## Row geometry and text

| Property | Contract | Confidence |
|---|---|---|
| Inactive / selected | Selection is white; other expanded difficulties blue; set cards pink/orange according to play status | CONFIRMED S1/R1 |
| Horizontal curve | Rows further from the selected region move right; selected/expanded cards protrude left | CONFIRMED R1; exact curve UNCERTAIN |
| Width / height / spacing | Measure logical card bounds independently from texture size; exact scale/overlap across skins is unmeasured | UNCERTAIN |
| Hover | Must not change semantic selection merely by drawing; exact stable displacement/tint/timing unmeasured | UNCERTAIN |
| Expansion / neighbours | Expanded difficulties occupy consecutive list positions; neighbouring sets move | CONFIRMED R1; trajectories UNCERTAIN |
| Thumbnail | S2 gives 115×85 SD units, 9-unit inset; enabled from skin 2.2 with user setting | CONFIRMED S2/S3 |
| Text | Title above artist/creator, difficulty more prominent than inactive title; selected black, inactive white by default | CONFIRMED R2/S3 |
| Bounds | Clip content to card and list; hit bounds must match displayed geometry rather than raw texture dimensions | Local safety/design invariant; exact stable hitbox UNCERTAIN |
| Font / ellipsis | Exact font metrics, weight, baseline, truncation and Unicode shaping unmeasured | UNCERTAIN |
| Original metadata | Romanised by default; original-language metadata is an option | CONFIRMED S1 |
| Scores | Local-score content has its own left region; no online dependency is required | CONFIRMED S1 |

Each rendered row needs a stable identity, logical index, selected/hovered flags,
target and displayed position, dimensions, thumbnail/title/metadata/clip/hit
rectangles, alpha and drawing order. Neither textures nor renderer branches own
semantic layout. Empty/malformed textures cannot enlarge interactive bounds.

## Animation and scrolling

| Transition | Known start/end | Duration / easing | Confidence |
|---|---|---|---|
| Select | Previous difficulty to new selected card | Not established | CONFIRMED endpoints, UNCERTAIN timing |
| Expand / collapse | Set card to difficulty list / reverse | Not established | CONFIRMED endpoints, UNCERTAIN timing |
| Wheel / inertia | Scroll position changes without requiring click | Impulse, damping, edge overscroll not established | CONFIRMED input, UNCERTAIN dynamics |
| Drag | List follows pointer while held | Velocity sampling/release inertia not established | CONFIRMED S1, UNCERTAIN dynamics |
| Hover / button hover | Over artwork appears on toolbox hover | Fade/scale timing not established | CONFIRMED S2, UNCERTAIN timing |
| Search | Matching list replaces broader list | Debounce and transition not established | CONFIRMED S1, UNCERTAIN timing |
| Random | Moves to random beatmap; previous random selection recoverable | Scroll path/duration not established | CONFIRMED S1/S4, UNCERTAIN timing |

Use elapsed time, not frame counts, for local transitions. Require equivalent
results for equal elapsed time at 30/60/144 Hz. Preserve stable identity during
retargeting. Wheel during selection must not be overwritten by an old target.
These are testable engineering invariants, not measured stable easing values.

## Input

| Input | Observable result | Confidence / evidence |
|---|---|---|
| Pointer movement | Updates hover only | STRONGLY INFERRED |
| Click unselected | Select difficulty | CONFIRMED S1 |
| Click selected | Start; does not require a timed double click | CONFIRMED S1 |
| Wheel / left drag | Navigate list | CONFIRMED S1 |
| Right button | Absolute scrolling documented; context options also documented; exact gesture precedence unmeasured | CONFIRMED S1; precedence UNCERTAIN |
| Up/down | Previous/next difficulty | CONFIRMED S4 |
| Left/right | Previous/next beatmap; page keys scroll | CONFIRMED S4 |
| Enter | Start selection | CONFIRMED S1/S4 |
| F1/F2/F3 | Mods / random / beatmap options | CONFIRMED S4 |
| Shift+F2 | Return through random history | CONFIRMED S4 |
| Esc / Back | Back/cancel; exact search-clear precedence unmeasured | CONFIRMED S4; search precedence UNCERTAIN |
| Text | All search terms match; numeric filters supported by stable | CONFIRMED S1 |
| Modified shortcuts | Must not insert shortcut text into search | Local input invariant |
| Drag release | Movement exceeding threshold cancels click even after returning to original row | Local input invariant; stable threshold UNCERTAIN |
| During animation | Hit-test visible positions; avoid input/render race | Local invariant; exact stable timing UNCERTAIN |

Use an explicit gesture lifecycle (idle/hover/pressed/dragging/released), owning
button and pointer. Search text events and command key events are distinct.
Never infer a click solely from matching press/release row identity.

## Skin contract

| Asset | Origin / sizing contract | Confidence |
|---|---|---|
| menu-button-background | Bottom-left; tinted; SD minimum recommendation 690×85; layout not its raw pixel dimensions | CONFIRMED S2; exact extreme-size treatment UNCERTAIN |
| selection-mode[-over] | v1 top-left 87 SD px above bottom; v2+ bottom-left; recommended width 92 | CONFIRMED S2 |
| selection-{mods,random,options}[-over] | Same version split; recommended width 77; height 87 (v1), 90 (v2+) | CONFIRMED S2 |
| songselect-top | Top-left; right edge repeated underneath original; resolution-dependent repeat start | CONFIRMED S2; exact repeat start UNCERTAIN |
| songselect-bottom | Bottom-left; stretched across width | CONFIRMED S2 |
| menu-back | Bottom-left; optional numbered animation overrides native back | CONFIRMED S2; native geometry UNCERTAIN |
| star | Centre; last fractional star cropped through v2.1, scaled from v2.2 | CONFIRMED S2/S3 |
| mode-*-small | Centre, additive, 32×32 recommendation, over Mode | CONFIRMED S2 |
| mode-*-med | Centre, dropdown; 128×128 recommendation | CONFIRMED S2 |

SD dimensions are neither framebuffer pixels nor hitboxes. @2x doubles sampling,
not logical size. Missing ini means latest; existing ini without Version means
1.0. Active/inactive text colours default to black/white (S3).

Fallback order, HD-only handling on low-resolution displays and broken HD with
valid SD are **UNCERTAIN** stable behavior. Local policy must document its choice,
cache resolutions and preserve transparent custom images rather than replacing
them because their alpha is zero. Invalid images should fall back safely.

S2 explicitly warns a tall bottom asset can block underlying input. osujava
intentionally bounds decorative skin geometry: reproducing a malformed skin's
unbounded hitbox is not a compatibility goal. Test 1×1, extreme aspect ratios,
large images, transparent, invalid, absent, SD-only, HD-only, broken HD and unusual
versions. Apply allocation limits before decode, not after allocating textures.

## Remaining observation protocol

Run a working stable build offline with the same synthetic map names and an
independently authored measurement skin at 1280×720, 1920×1080 and 1024×768.
Record build, skin version, thumbnail setting and actual framebuffer scale.
Capture initial/hover/selection at fixed elapsed times, 1/10 wheel impulses,
reverse wheel during tracking, top/bottom, drag out-and-back, slow second click,
search clear/Esc and random history. Measure coordinates and trajectories from
video frames before upgrading UNCERTAIN values. A static screenshot cannot
validate acceleration, easing, double-click timing or frame-rate independence.

Offline runtime attempt: copied the existing b20230727.9 installation and Wine
prefix into `/tmp/osujava-stable-research`, launched under `unshare -Urn` (no
network) with Xvfb. No Song Select appeared within the bounded launch attempt;
the captured framebuffer was black. This is not a behavioral observation.
R1/R2 remain the visual reference. Existing disassembly files in `/tmp` were not
used as specifications or implementation sources for this work.
