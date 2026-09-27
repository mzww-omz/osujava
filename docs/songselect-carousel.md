# SongSelect carousel interaction

The carousel replaces selected-relative row placement with content coordinates and a scrolling viewport. Legacy skin resolution, ownership, `skin.ini`, text colours and Random/Back assets remain in `SongSelectSkinAssets`; Gameplay and GameClock are unchanged.

## Responsibilities

- `SongSelectScreen` owns Library selection, filtering, input arbitration, play/navigation and drawing. It rebuilds the logical entry list only when the expanded Set, search, Library or dimensions change. Difficulty selection within an expanded Set only changes selection and the scroll target.
- `SongSelectCarousel` assigns stable logical row centers from the start of content, downwards. Half-viewport padding at either end allows even the first/last row of a short result to center. Offset and target are always clamped to the padded content range, including empty results.
- Row visual state consists only of hover, neighbour separation and selected emphasis. It never changes logical Y. Existing Set ID + difficulty index keys retain visual state across layout rebuilds; removed entries are discarded. All logical entries stay in memory; only visible render rows are allocated per frame.

Rendering composes `top - logicalY + scrollOffset - rowHeight/2 + separationY` for the bottom of a row. X composes a smooth, symmetric, bounded arch from the row's viewport Y, selected emphasis and hover emphasis. Three selected/sibling/other colours remain, but there are no three fixed X positions.

## Expansion and selection

The selected Set is represented by **every** difficulty in Library order, without its parent row. Other Sets have one collapsed row. Selecting another Set collapses the previous one. The former selected-difficulty ±2 window is gone, including for large Sets.

osujava has no difficulty-strength ordering policy or star rating. Import uses deterministic `.osu` path ordering and Library persists the ordered difficulty list. This change preserves that policy and existing indices, preferred difficulty, first-difficulty defaults, page/arrow navigation and filtered Random semantics. McOsu's highest-difficulty default was considered but not adopted: the last osujava Library entry is not necessarily the hardest difficulty. Introducing a new strength comparator would also change stored selection/navigation semantics.

Clicking an unselected difficulty selects it; clicking the currently selected difficulty requests manual Play. Enter/Space and F6 Debug Auto keep their existing actions. Hit testing follows draw order, with the selected row above overlapping siblings. A Set click never plays, and a short UI-delta-based guard prevents a second click during expansion from playing the newly substituted difficulty. Subsequent intentional difficulty re-clicks work normally.

## Motion and hover

Keyboard, mouse selection and Random/F2 center the selection through the same viewport target. Wheel moves only that target, preserving the selected difficulty and expanded Set; fractional deltas are supported. Scrolling uses frame-rate-independent exponential interpolation driven only by the existing screen delta. Wheel browsing smoothly moves the content without automatically selecting or expanding passing Sets. Physical inertia and right-click absolute scrolling are deferred. The entire right-half carousel viewport reserves wheel input, including row gaps and empty results. Outside the viewport, wheel still adjusts volume. Alt and a HUD explicitly opened with F4 override the carousel; automatically displayed volume feedback does not steal its wheel input when the pointer returns.

When a Set becomes difficulties, its chosen child inherits the clicked Set's viewport position before moving to the center. This compensates structural insertion/removal without giving logical rows selected-relative animation state. Random follows the same path. A change of search selects the first matching Set when necessary, clamps the shorter content range, and safely supports no results and recovery.

Hover moves a row slightly left with a short ease-out. Rows above move up and rows below move down through independent visual separation offsets. Hover is retained briefly across gaps and reverses smoothly on exit. Row switches start from the current values, without resetting every row. The curve, interpolation rates, displacements, hover retention and expansion guard were designed independently for osujava.

A valid `menu-button-background` logical aspect ratio determines row height from osujava's carousel width. `@2x` uses logical dimensions. Extreme/invalid ratios use a 6:1 fallback; height is bounded to 68–110 UI units. Skin rows have a small positive gap. Missing assets retain the original 76-unit height and 72-unit pitch. Thumbnails fit inside the bounded height, keeping the existing version-dependent aspect policy.

## Conceptual comparison with public McOsu

The public OSS was inspected as an interaction reference, without translating C++ code, formulas, constants or function structure. No osu!stable binary was used. This is source-level conceptual comparison, not a side-by-side execution of the McOsu binary.

| Interaction observed in McOsu | Independent osujava implementation/check |
| --- | --- |
| Content row coordinates and a scrollable container | Logical centers separated from one clamped viewport; difficulty selection preserves row identities/Y |
| Selected row centering, compensating expansion changes | One interpolated target and screen-position anchor when replacing a Set |
| Parent/child expansion | One expanded Set, every Library difficulty retained; other parents collapsed |
| Vertical-position-dependent horizontal curve | New bounded rational arch; additive selected and hover emphasis |
| Hover left movement | UI-delta-driven short easing |
| Neighbours move above/below the hovered row | Independent signed visual Y offsets and brief gap retention |
| Skin-aware spacing and expanded-row space | Bounded logical image aspect, positive skin row gaps, hover-created space |
| Selected difficulty re-click starts play | Current selection check; Set-origin double-click protection |

References: [public browser](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuSongBrowser2.cpp), [public row interaction](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserButton.cpp), [public Set selection](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserSongButton.cpp), [public difficulty selection](https://github.com/McKay42/McOsu/blob/master/src/App/Osu/OsuUISongBrowserSongDifficultyButton.cpp). The numerical model, Java data structures, stable state lifecycle, sizing bounds, input integration and tests are osujava implementations. Deliberate differences include Library order/default selection, filtered Set Random, viewport-only wheel browsing, simpler interpolation and no right-click scrolling.

## Verification

`./gradlew build`: **403 tests passed** (383 original + 20 carousel/wheel regression tests), no failures/errors/skips. New tests cover unchanged logical rows on selection, centering/visibility, complete expansion/collapse, difficulty re-click, Set double-click protection, hover separation/retention/release, range clamp/empty recovery, resize/state retention, continuous curve, logical skin sizing, frame-rate independence, Random and filtered content changes. Wheel regression coverage also verifies whole-viewport/empty-result routing, fractional scroll, selection/expansion stability, range boundaries, and returning to the carousel while automatic volume feedback is still active. Existing Volume HUD, wheel, navigation, search, play, Debug Auto and skin tests also pass.

`./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-carousel` captures production screens at 1280×720, 1920×1080 and 1280×720 with a 2560×1440 backbuffer. **60 final scene captures plus 75 intermediate captures** cover Greylooks initial, Set selected, difficulty selected, hover, after wheel, Random, and Random-button hover, alongside missing/partial/normal/@2x/broken/tiny/unusual/version/colour fixtures. Five motion scenarios save frames 1, 4, 10, 20 and 40. Assertions check centering/range, actual input/navigation/search/Random, re-click and expansion double-click, and texture disposal. Visual review of all three resolution/density profiles and wheel transition frames confirms continuous movement and neighbour space. Captures and generated fixtures remain outside Git.
