# SongSelect carousel interaction

The carousel replaces selected-relative row placement with content coordinates and a scrolling viewport. Legacy skin resolution, ownership, `skin.ini`, text colours and Random/Back assets remain in `SongSelectSkinAssets`; Gameplay and GameClock are unchanged.

## Responsibilities

- `SongSelectScreen` owns Library selection, filtering, input arbitration, play/navigation and drawing. It rebuilds the logical entry list only when the expanded Set, search, Library or dimensions change. Difficulty selection within an expanded Set only changes selection and the scroll target.
- `SongSelectCarousel` assigns stable logical row centers from the start of content, downwards. Half-viewport padding at either end allows even the first/last row of a short result to center. Offset and target are always clamped to the padded content range, including empty results.
- Row visual state separates hover, hover neighbour separation, selected emphasis, selection spacing, expansion displacement/velocity, expansion X and reveal. Logical Y stays immutable. Existing Set ID + difficulty index keys retain visual state across layout rebuilds; removed entries are discarded. All logical entries stay in memory; only visible render snapshots are allocated per frame. Settled off-screen rows skip easing work; visible rows and unfinished transitions continue updating.

Rendering composes `top - logicalY + scrollOffset - expansionY - rowHeight/2 + separationY + selectionSeparationY` for the bottom of a row. X composes the existing symmetric arch from that visual Y, additive selection/hover/velocity/expansion offsets, and a right-side safety bound. Three selected/sibling/other colours remain; hover tint now follows the eased hover state instead of raw pointer intersection. The immutable visible-row snapshot supplies both drawing and click bounds, with the selected row composited and hit-tested last/first respectively. Nearly transparent emerging siblings do not intercept clicks.

## Expansion and selection

The selected Set is represented by **every** difficulty in Library order, without its parent row. Other Sets have one collapsed row. Selecting another Set collapses the previous one. The former selected-difficulty ±2 window is gone, including for large Sets.

osujava has no difficulty-strength ordering policy or star rating. Import uses deterministic `.osu` path ordering and Library persists the ordered difficulty list. This change preserves that policy and existing indices, preferred difficulty, first-difficulty defaults, page/arrow navigation and filtered Random semantics. McOsu's highest-difficulty default was considered but not adopted: the last osujava Library entry is not necessarily the hardest difficulty. Introducing a new strength comparator would also change stored selection/navigation semantics.

Clicking an unselected difficulty selects it; clicking the currently selected difficulty requests manual Play. Enter/Space and F6 Debug Auto keep their existing actions. Hit testing follows draw order, with the selected row above overlapping siblings. A Set click never plays, and a short UI-delta-based guard prevents a second click during expansion from playing the newly substituted difficulty. Subsequent intentional difficulty re-clicks work normally.

## Motion and hover

Keyboard, mouse selection and Random/F2 center the selection through the same viewport target. Wheel moves only that target, preserving the selected difficulty and expanded Set; fractional deltas are supported. Viewport scrolling uses an exact critically damped spring driven only by the existing screen delta. Its `viewportVelocity` preserves motion across target changes, giving wheel and selection snaps soft starts and natural settling. Wheel browsing moves the content without automatically selecting or expanding passing Sets. Repeating an unchanged selection does not recenter; resize/filter changes preserve the wheel target relative to the selected anchor. Gesture-specific fling APIs and right-click absolute scrolling remain deferred. The entire right-half carousel viewport reserves wheel input, including row gaps and empty results. Outside the viewport, wheel still adjusts volume. Alt and a HUD explicitly opened with F4 override the carousel; automatically displayed volume feedback does not steal its wheel input when the pointer returns.

When a Set becomes difficulties, children originate at its actual visual position and neighbouring Sets retain their previous visual positions. `expansionY` and `expansionVelocityY` reconcile old and new content coordinates; the same spring rate as the viewport makes the chosen child move toward the center without opposing expansion/snap motion. Siblings fade into the space as it opens, with a small independent `expansionX` easing back. Collapse uses the old selected difficulty as the Set representative. Interrupted expansion/collapse starts from current positions and velocities, including existing hover and selection spacing. Representatives use stable Set keys, not mutable list indices. Random follows the same path. A change of search selects the first matching Set when necessary, clamps the shorter content range, and safely supports no results and recovery.

Wheel input adds bounded signed impulses to `scrollVelocity`, independent of target error and navigation selection. Reversal releases the previous impulse; blocked input at a range boundary adds none. Exponential decay and an eased `velocityInfluence` produce a small center push/end pinch, limited to 18 logical units. A low-speed dead zone keeps slow browsing close to the old curve. Fractional deltas remain continuous; finite extreme deltas are limited before screen-level multiplication, while NaN/infinity are ignored. Visual velocity integration uses bounded small steps for consistent 30/60/120 Hz response.

Selected emphasis retains `selectedAmount` with its own easing and a slightly stronger left offset. `selectionSeparationY` adds modest neighbour spacing without scaling or glow. Hover retains its own `hoverAmount` and `separationY`; high speed weakens both and the animated tint, then restores them naturally after scroll stops. Hover is retained briefly across gaps and reverses smoothly on exit. Row switches start from current values without resetting every row. The curve, spring model, rates, displacements, hover retention and expansion guard were designed independently for osujava.

A valid `menu-button-background` logical aspect ratio determines row height from osujava's carousel width. `@2x` uses logical dimensions. Extreme/invalid ratios use a 6:1 fallback; height is bounded to 68–110 UI units. Skin rows have a small positive gap. Missing assets retain the original 76-unit height and 72-unit pitch. Thumbnails fit inside the bounded height, keeping the existing version-dependent aspect policy.

## Conceptual comparison with public McOsu

The public OSS was inspected as an interaction reference, without translating C++ code, formulas, constants or function structure. No osu!stable binary was used. This is source-level conceptual comparison, not a side-by-side execution of the McOsu binary.

| Interaction observed in McOsu | Independent osujava implementation/check |
| --- | --- |
| Content row coordinates and a scrollable container | Logical centers separated from one clamped viewport; difficulty selection preserves row identities/Y |
| Selected row centering, compensating expansion changes | One interpolated target and screen-position anchor when replacing a Set |
| Parent/child expansion | One expanded Set, every Library difficulty retained; other parents collapsed |
| Vertical-position-dependent horizontal curve and separate center/velocity/hover animation | Existing bounded rational arch; independent selected, hover, bounded velocity push/pinch and expansion offsets |
| Hover left movement | UI-delta-driven short easing |
| Neighbours move above/below the hovered row | Independent signed visual Y offsets and brief gap retention |
| Skin-aware spacing and expanded-row space | Bounded logical image aspect, positive skin row gaps, hover-created space |
| Selected difficulty re-click starts play | Current selection check; Set-origin double-click protection |

References: [public browser](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuSongBrowser2.cpp), [public row interaction](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserButton.cpp), [public Set selection](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserSongButton.cpp), [public difficulty selection](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserSongDifficultyButton.cpp). The numerical model, Java data structures, stable state lifecycle, sizing bounds, input integration and tests are osujava implementations. Deliberate differences include Library order/default selection, filtered Set Random, viewport-only wheel browsing, an independent damped spring/impulse model and no right-click scrolling.

## Phase 1 verification

Run `./gradlew build` and `./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-motion-phase1`.

`SongSelectCarouselMotionTest` adds regression coverage for velocity clamp/decay/reversal, soft spring starts, 30/60/120 Hz timelines, tiny deltas, range-boundary impulses, unchanged-selection/resize wheel preservation, selection/hover independence, speed-dependent hover recovery, bounded deformation, first/last centering, 1/4/16-difficulty expansion/collapse, interrupted movement, monotonic expansion/snap, invalid inputs, filtering/empty recovery and 1000-Set content identity. Navigation and wheel tests additionally exercise production snapshots, selected hit priority during overlap and extreme finite screen-level wheel input. Existing tests are retained.

The production visual harness captures 90 scenes plus 234 transition frames at 1280×720, 1920×1080 and 1280×720 with a 2560×1440 backbuffer. Idle, selected, hover, slow/fast scroll, immediate reverse, first/last items, 1/16-difficulty expansion (first/last child), collapse and a 1000-Set library join the existing missing/partial/normal/@2x/broken/tiny/unusual/version/colour skin fixtures. Transition frames 1, 4, 10, 20, 40 and 60 are saved. Assertions verify actual expansion input, final Carousel/draw bounds, range/settling, existing input/navigation/search/Random, re-click/double-click guards and disposal. Captures and generated fixtures remain outside Git.

Final run: `./gradlew build :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-motion-phase1` succeeded. All **422 tests passed**, with zero failures/errors/skips; this phase adds 14 motion tests and two navigation/wheel tests. The harness passed all 90 scenes and saved 234 transition captures. Visual review covered all three resolution/density profiles, expansion/collapse/fast-scroll timelines, and missing/@2x/malformed skin fallbacks. Broken PNG fixture warnings are expected and verify programmatic fallback.

A harness timing probe measures only `Carousel.advance` over 600 warmed samples with 1003 rows. The final local run averaged 0.006 ms in each profile, with a maximum of 0.036 ms. This is a local CPU motion measurement, not a full-render or cross-hardware benchmark. Physical mouse wheels/trackpads are represented by synthetic ordinary, tiny fractional, repeated fast and reversal deltas; direct device feel and the McOsu binary have not been compared.
