# Stable Song Select implementation audit

Phase B, after `songselect-stable-spec.md`. Baseline: clean working tree at task
start. Historical project docs are implementation history, not independent stable
proof. References below use the specification's source IDs.

| Area | Stable | Current osujava | Difference | Evidence | Confidence | Fix |
|---|---|---|---|---|---|---|
| Logical state | Identity selection, expanded set, query/sort/group | SongBrowserModel already owns these | Good boundary; Screen retains display indices and duplicate query | SongBrowserModel / Screen | CONFIRMED code | Keep existing model, do not introduce competing selection authority |
| Layout | Height-based presentation consistent with R1 | Metrics + Chrome + Toolbox, row geometry partly computed in renderer | Multiple coordinate owners; constants claim stronger provenance than this investigation establishes | Metrics, RowRenderer | CONFIRMED code / STRONGLY INFERRED reference | Unified layout snapshot and row placement; document provisional constants |
| Row thumbnail | 9 SD inset, 115×85 SD footprint | 3.5% row-height inset, shrunken thumbnail | Depends on unrelated padding factor | S2 / RowPresentation.geometry | CONFIRMED | Use documented ratios |
| Selected card | Visible leftward emphasis, white | Selected emphasis plus arbitrary group-width fraction | Exact displacement not validated | R1 / Carousel | UNCERTAIN compatibility | Preserve provisional motion pending measurement; isolate parameters |
| Input | Left drag scrolls | Pointer only cancels clicks after slop | Drag never moves viewport | S1 / Pointer / Screen.update | CONFIRMED | Controller owns drag gesture and direct carousel motion |
| Shortcut text | Commands should not become search | One pending char reset on every keyDown; modifiers not checked in keyTyped | Ctrl/Alt characters can enter query | SongSelectInput | CONFIRMED code | Explicit modifier and command consumption |
| Renderer | Display state only | draw reduces toast timer and advances entrance; directly polls Gdx.input | Capturing twice changes animation; difficult deterministic replay | Screen.draw / UiView.fade | CONFIRMED | Move time advance to update; renderer consumes sampled input |
| Skin resolution | Versioned origin/density | Central SongSelectSkinAssets exists, supports fallback and alpha bounds | No pre-decode allocation budget; large selection art can cover scene | SkinAssets / Screen.drawSelection | CONFIRMED code | Preflight PNG size; bound decorative pass |
| Top/bottom | Repeat vs horizontal stretch | Already distinct policies | Repeat start is local approximation | S2 / Screen.drawTopSkin | CONFIRMED policy, UNCERTAIN exact position | Retain distinction; record unresolved repeat seam |
| Skin version | 2.2 thumbnails and fractional star scaling | Already implemented | No need for parallel replacement | S2/S3 / SkinAssets / RowRenderer | CONFIRMED | Maintain and test |
| Search | Difficulty criteria and original-language option | Indexed set search and fixed romanised content | Full stable filter semantics not present | S1 / SongBrowserModel | CONFIRMED | Keep query boundary; document unsupported semantics |
| Scroll | Wheel/drag/selection transitions | Exact damped viewport spring, approximate hover dynamics | Stable timing unmeasured | Carousel | UNCERTAIN | Keep elapsed-time integration; add direct drag and invariance tests |
| Performance | Large local collections | Content cached on changes; visible row snapshots; motion traverses entries | O(n) motion scan, but no full per-frame reconstruction | Carousel / Screen | CONFIRMED code | Retain caches; performance harness |
| Library | Data provider | Import service orchestrated in Screen; rendering never opens archive | Already correct persistence separation | Screen.startImport | CONFIRMED | Preserve existing persistence regressions |
| Visual harness | Reproducible comparison | Large hard-coded scene matrix and reflection | No arbitrary resolution/state/time invocation, no 4:3 matrix | SongSelectVisualHarness | CONFIRMED | Add parameterized capture entry and diagnostics manifest |
| Scores / mode / Mods | Separate toolbox and left scores | Separate models; mode/Mods partial, Options unavailable | Not full stable feature parity | S1 / Toolbox | CONFIRMED | Preserve honest unavailable states; no scope expansion into Rulesets |

## Root causes

The existing architecture is partially separated rather than entirely absent.
The remaining problem is ownership: Screen is simultaneously composition,
interaction and geometry coordinator, and row rendering still derives geometry.
Several independent approximations were labelled stable without reproducible
observations. Texture resolution is centralized, but texture allocation and
unbounded decoration do not share its defensive policy. Gesture cancellation
was mistaken for implementing drag scrolling.
