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
directory and are not shipped with the application. Fixture coordinate `Parsing` only handles finite
values within the declared coordinate range. Timing parsing also permits inherited beat-length NaN
for the verified tick-disable encoding; these tests do not emulate malformed source parsing.

Minimal bindable/cache/vector/model adapters supply fixture contracts. Unused Lagrange helpers throw.
The wrapper supplies raw fixture hitobjects and legacy tail offset 36ms. SV is evaluated by the unchanged public
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
verified slider fixtures are v6+, for which the public early-version offset is zero.
The complete public `OsuBeatmapConverter.ConvertHitObject` method converts a fixture legacy slider,
including the pre-v8 `TickDistanceMultiplier` reciprocal of raw/clamped SV, before rounded slider SV
is assigned. Complete public `Slider.EndTime`, `Duration` and `SpanDuration` getters preserve their
floating-point operation order: the span is derived from `(EndTime - StartTime) / SpanCount`, rather
than substituting `Path.Distance / Velocity`. The complete public `Slider.ApplyDefaultsToSelf`,
`CreateNestedHitObjects`, `HitObject.ApplyDefaults` and `OsuHitObject.ApplyDefaultsToSelf` methods
then supply velocity/tick distance, nested positions/times, scale/preempt, and nested sorting.
The public `IBeatmapDifficultyInfo` interface/default range method also runs unchanged.
The hitwindow adapter retains the documented NM Great half-window above; sample updates and
combo bind graphs are excluded because difficulty fixture models do not use those states.
`TimingAdapters.cs` supplies unrelated sample metadata, type/serialization/editor dependencies and
an unbound runtime container. Audio/sample playback, framework serialization, editor snapping,
full stream parsing, nested scoring/audio classes and the combo binding graph are not decoder
verification claims. Nested model subclasses provide properties consumed by the unchanged methods;
the actual geometry/time generation and `List.Sort` run through the public method bodies.
`List.Sort` is executed by the .NET 8 oracle runtime. Its API specifies an unstable sort, so this
reference does not prove the tie order of osu!stable or another .NET runtime. Java keeps nested-tie
charts unsupported; the tie fixture records an explicit regression boundary.
Inherited NaN tick-generation assignment runs from the real legacy point to the real slider-default
method. It retains raw/rounded SV 1 while disabling ticks (`TickDistance=Infinity`); the public
`SliderEventGenerator` still produces head, repeats and the legacy last tick. An ordinary inherited
point or red-line reset can re-enable ticks, even when SV is unchanged, through real redundancy checks.

For an isolated regeneration or byte comparison, `--fixtures /path/to/fixture-copy` writes only the
chosen directory instead of the checked-in resource directory. Slider fixtures with nonfinite timestamps,
infinite beat lengths, red-line NaN or pre-v6 format are explicitly rejected before emitting results.
The oracle accepts coincident and
out-of-order finite timing in source order using the actual public processing; Java's supported subset
is documented separately and still rejects source that was reordered by its parser.
The temporary public sources are removed after compilation/execution.

49 self-authored fixtures cover circles/spinners and Linear plus Bezier, perfect, Catmull and mixed paths.
The 14 added curve fixtures cover higher-degree Bezier, implicit duplicate segments, loops, minor/major
arcs in both directions, collinear/perfect fallback, fractional encoded coordinates, legacy/v128 Catmull duplication,
positive/negative stacks and shared mixed boundaries. At that curve extension step, the previous
24 oracle files remained byte-identical.
Output includes path positions/length, nested times/stacked positions, lazy end/travel, minimum jump and
object/section strain values. Acceptance tolerances for stars, skills and intermediate facts are unchanged.

Two new `timing-sv-*` fixtures cover positive/zero inherited beat lengths and
clamping on both ends, division overflow from finite subnormal input, midpoint ties and encoded
values immediately below/above ties. They contain strictly increasing timing timestamps, finite
encoded beat lengths, and v14 sliders. Additional `sliderTiming.<object-index>` facts record
beat length, raw/clamped SV, rounded slider SV and tick distance for timing fixtures.
At the SV extension step, all previous 38 `.properties` files remained byte-identical.

Three `timing-coincident-{rg,gr,multiple}` fixtures cover red→green, green→red and mixed
multiple-red/multiple-green batches. Twelve sliders in each fixture sample immediately before,
at and immediately after four shared timing timestamps, including the first-red fallback before
all points. At the coincident-timing extension step, all previous 40 `.properties` files remained byte-identical
after connecting the actual timing decoder/control-point lookup and slider defaults.

Two `timing-nan-{repeat,priority}` fixtures verify inherited NaN tick suppression and recovery,
repeat survival, and NaN/finite-green priority within coincident red/green batches.
These two fixtures append a fifth `sliderTiming` fact: `GenerateTicks` as 0 or 1.
At the inherited-NaN extension step, all previous 43 `.properties` files remained byte-identical;
their existing timing facts kept four columns.

Two `timing-pre-v8-v{6,7}` fixtures verify raw versus rounded SV, both clamp ends and red resets
through the actual converter with Linear paths, followed by inherited NaN suppression and ordinary
green-line restoration. These fixtures also append `GenerateTicks` as a fifth timing fact. `timing-duration-large-time` samples 0.1px short
sliders near `Integer.MAX_VALUE` timestamps; the getter subtraction exposes measurable span-rounding
at this scale. `timing-nested-ties` covers short repeats with simultaneous repeat/legacy-tail events,
including nested list counts on both sides of the small-list sort threshold. It is reference-only;
Java continues to reject these ties.

Connecting the real duration getters and nested methods changed 25 of the previous 45 `.properties`
files at 129 numeric cells. All 45 original `stars` strings remained byte-identical. The largest absolute
intermediate delta was 7.275957614183426e-12 (`timing-sv-rounding.object.5` minimum jump time);
`aim` changed by 2.22e-16 in that fixture. Every change remains within the unchanged acceptance
tolerances below. Regenerated references retain the real operation order instead of preserving the
old fixture wrapper's arithmetic. The other 20 original files remain byte-identical.

Coincident nested events, pre-v6 slider processing, degree-specific B-spline and resource-limit
cases remain outside Java's verified subset. Pre-v8 support is limited to the checked Linear v6/v7
contracts; other combinations require separate fixtures before acceptance. Java rejects these
charts rather than substituting facts. Limits include 64 controls per non-linear subcurve, 32 Bezier
subdivision levels and 100,000 total preprocessing work units; curve-heavy charts can exceed that budget.

Acceptance tolerance, chosen before checking Java outputs: 1e-9 absolute for star/skill ratings;
1e-7 absolute + 1e-9 relative for per-object/section facts. Stack heights must agree exactly.
Java production code is an independent calculation using these formula contracts, with resource bounds
and explicit unsupported states. A passing fixture suite is evidence for this declared subset;
it is not a claim that every stable beatmap or the current 2026 calculator has equal values.
