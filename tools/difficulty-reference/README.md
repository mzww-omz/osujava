# Offline difficulty reference oracle

Development tool only. Neither Gradle nor osu!java invokes this tool or performs network requests.

`generate.py` downloads **unchanged public MIT-licensed ppy/osu source files** at
`4e96853c7543f80a1b822ccd381943c7377543d7` (tag `2023.815.0`, calculator version `20220902`)
plus unchanged `PathApproximator` / `CircularArcProperties` from the matching framework dependency
`2023.815.0`, commit `3365c86f769cb0ed84a10c5c96313a73e552dc2d`, into a temporary directory
and compiles/runs them with .NET 8. It then deletes the temporary sources.
No stable binaries, extracted assets, production API, or official score/star data are involved.
Source licences: https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/LICENCE
and https://github.com/ppy/osu-framework/blob/3365c86f769cb0ed84a10c5c96313a73e552dc2d/LICENCE

From the repository root:

```sh
DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 \
  python3 tools/difficulty-reference/generate.py --dotnet /path/to/dotnet
```

Fixture `.osu` files are authored here. Generated `.properties` files are checked in, so ordinary
Java tests need no .NET installation or network access. `Stubs.cs` supplies fixture object/model,
single-precision vector, interpolation and basic unbound-bindable storage/event adapters.
The original preprocessing, stacking processor,
evaluators and skill aggregators run unchanged. The wrapper uses the pinned NM final combination formula.
It sets circle defaults to the pinned `OsuHitObject` formula, spinner Great window to zero (`HitWindows.Empty`),
and standard Great half-window to `80 - 6*OD`. It does **not** emulate the full lazer decoder or stable client.
The unchanged public `SliderPath`, `SliderEventGenerator`, framework `PathApproximator` and
`CircularArcProperties` run; curve approximation is no longer stubbed. The tool also downloads the
pinned `ConvertHitObjectParser`, extracts its four complete private path-conversion methods without
changing their bodies, and compiles them in a fixture wrapper. This exercises integer control-coordinate
conversion, implicit duplicate segments, explicit mixed boundaries, perfect-curve fallback and legacy
Catmull rules using actual public methods. The public licence headers/sources stay in the temporary
directory and are not shipped with the application. Fixture `Parsing` only handles finite values within
the declared coordinate range; these tests do not emulate malformed source parsing.

Minimal bindable/cache/vector/model adapters supply fixture contracts. Unused Lagrange helpers throw.
The wrapper wires nested objects, legacy tail offset 36ms and nested StartTime sorting. SV is now evaluated by the unchanged public
`DifficultyControlPoint`, `BindableDouble`, `BindableNumber`, and `RangeConstrainedBindable` classes.
The complete `LegacyDecoder.LegacyDifficultyControlPoint` class and the osu! `Slider` velocity
bindable/property declarations are extracted unchanged into temporary fixture wrappers. This preserves
the two stages: legacy points clamp raw SV to 0.1–10 without precision rounding, and the slider
rounds it at precision 0.01 (nearest, ties to even, after clamping). The basic unbound `Bindable<T>`
adapter stores values/events; it does not implement the numerical setters. These fixtures do not bind
instances, mutate disabled bindables, or test the framework binding graph.

Timing now runs the complete public `ControlPoint`, `ControlPointGroup`, `ControlPointInfo`,
`LegacyControlPointInfo`, timing/difficulty/effect/sample point and `TimeSignature` classes, plus
framework `SortedList`. The complete public `LegacyBeatmapDecoder.handleTimingPoint`,
`addControlPoint` and `flushPendingPoints` methods run in a fixture wrapper, with source timing
rows passed in their original order. They select first-red/last-green priority at the same timestamp;
public grouping, replacement, redundancy and binary lookup execute unchanged. Slider beat/SV lookup
uses the exact hitobject `StartTime` through `TimingPointAt` / `DifficultyPointAt`. The decoder's
1ms sample leniency is not applied to these default lookups. Its timing offset adapter is identity:
verified slider fixtures are v8+, for which the public early-version offset is zero.
The complete public `Slider.ApplyDefaultsToSelf` method supplies velocity and tick distance;
its base method adapter is empty because circle scale/preempt/windows are supplied separately above.
`TimingAdapters.cs` supplies unrelated sample metadata, type/serialization/editor dependencies and
an unbound runtime container. Audio/sample playback, framework serialization, editor snapping,
full stream parsing, hitobject conversion and nested creation are not decoder verification claims.
NaN tick-generation assignment is wired from the real legacy point to the real slider-default method,
but NaN fixture acceptance remains disabled pending a separate regression unit.

For an isolated regeneration or byte comparison, `--fixtures /path/to/fixture-copy` writes only the
chosen directory instead of the checked-in resource directory. Slider fixtures with NaN/nonfinite timing
or pre-v8 format are explicitly rejected before emitting results. The oracle accepts coincident and
out-of-order finite timing in source order using the actual public processing; Java's supported subset
is documented separately and still rejects source that was reordered by its parser.
The temporary public sources are removed after compilation/execution.

43 self-authored fixtures cover circles/spinners and Linear plus Bezier, perfect, Catmull and mixed paths.
The 14 added curve fixtures cover higher-degree Bezier, implicit duplicate segments, loops, minor/major
arcs in both directions, collinear/perfect fallback, fractional encoded coordinates, legacy/v128 Catmull duplication,
positive/negative stacks and shared mixed boundaries. The previous 24 oracle files remain byte-identical.
Output includes path positions/length, nested times/stacked positions, lazy end/travel, minimum jump and
object/section strain values. Stars/skill and intermediate tolerances are unchanged.

Two new `timing-sv-*` fixtures cover positive/zero inherited beat lengths and
clamping on both ends, division overflow from finite subnormal input, midpoint ties and encoded
values immediately below/above ties. They contain strictly increasing timing timestamps, finite
encoded beat lengths, and v14 sliders. Additional `sliderTiming.<object-index>` facts record
beat length, raw/clamped SV, rounded slider SV and tick distance for timing fixtures.
All previous 38 `.properties` files remain byte-identical after this oracle extension.

Three `timing-coincident-{rg,gr,multiple}` fixtures cover red→green, green→red and mixed
multiple-red/multiple-green batches. Twelve sliders in each fixture sample immediately before,
at and immediately after four shared timing timestamps, including the first-red fallback before
all points. All previous 40 `.properties` files remain byte-identical after connecting the actual
timing decoder/control-point lookup and slider defaults.

NaN timing, coincident nested events, pre-v8 tick-distance multipliers,
degree-specific B-spline and resource-limit cases remain outside the verified subset. Java rejects these
charts rather than substituting facts. Limits include 64 controls per non-linear subcurve, 32 Bezier
subdivision levels and 100,000 total preprocessing work units; curve-heavy charts can exceed that budget.

Acceptance tolerance, chosen before checking Java outputs: 1e-9 absolute for star/skill ratings;
1e-7 absolute + 1e-9 relative for per-object/section facts. Stack heights must agree exactly.
Java production code is an independent calculation using these formula contracts, with resource bounds
and explicit unsupported states. A passing fixture suite is evidence for this declared subset;
it is not a claim that every stable beatmap or the current 2026 calculator has equal values.
