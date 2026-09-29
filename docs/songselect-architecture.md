# Song Select architecture decision

> Historical decision. The user's 1:1 parity objective now permits necessary large
> changes. [The 2026-09-29 reinvestigation](songselect-parity-reinvestigation-20260929.md)
> supersedes the requirements to retain the spring, existing row model and schema.
> Responsibility separation remains required; implementation structure is not fixed.

Phase C, before implementation. Follow the stable specification and confidence
ledger; do not relabel inherited constants as newly measured stable behavior.

## Ownership

- **SongBrowserModel** is the existing SongSelectModel responsibility: cached
  Library projection, stable selection, expanded set, query, sorting, grouping
  and random history. Keep the name to avoid a facade and duplicate state.
- **SongSelectLayout** owns whole-screen and row layout snapshots. Existing
  Metrics/Chrome/Toolbox helpers remain internal geometry policies. Immutable
  row geometry is computed before drawing and shared with hit testing.
- **SongSelectViewState** owns sampled pointer and elapsed display time; the
  existing SongSelectCarousel remains its motion engine. No Library mutation.
- **SongSelectInputController** extends the existing keyboard router with a
  pointer gesture state machine, slop and direct drag scroll. Screen orchestrates
  commands and services; rendering cannot interpret input.
- **SongSelectRenderer** owns draw passes, receives prepared screen state and
  resident assets, and does not advance time or change selection/search/scores.
  It has no Importer or Library service dependency.
- **SongSelectSkinAssets** remains the sole skin resolution owner. Preflight
  dimensions before PNG decode. Transparent images are valid presence. Clip
  oversized decoration without growing interaction rectangles.

## Coordinate contract

UiLayout maps window coordinates to a 720-high logical viewport. Framebuffer
scale only enters GL scissor/capture, not layout. Legacy SD art is 768-high;
existing 480-high motion constants must carry explicit conversion. Row layout
is independent of image width/height. Thumbnail ratios derive from 9/85 and
115/85, not a renderer-specific padding percentage.

Each row exposes identity/index, target/displayed position, selected/hovered,
body, thumbnail, title/metadata, clip, hit bounds, alpha and z-order. Frame
snapshots are proportional to visible rows; cached text and asset ownership are
not duplicated each frame. Retain the already-tested critically damped motion;
its rate remains provisional until stable video measurement is available.

## Extension boundaries

Sorting comparators and grouping buckets belong in SongBrowserModel. Extract a
SortProvider/GroupProvider only when a second consumer warrants it. Query parsing
and indexed matching are the SearchMatcher/filter boundary. Mode filtering belongs
before row projection, never in rendering. Local score snapshots supply ranking
and played/grade data; future ScoreProvider stays behind that boundary. No new
persistence schema, network service, gameplay clock or Ruleset change is needed.

## Validation

Keep existing navigation/skin/Library tests. Add targeted gesture, modifier,
geometry, malformed PNG and renderer ownership regressions after visual review.
Extend the existing production-screen harness with explicit resolution, density,
selected row, wheel distance, query, hover, skin and animation time. Compare with
R1/R2; record exact visual/timing gaps rather than claiming unobserved equivalence.
