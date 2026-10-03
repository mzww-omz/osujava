# Offline difficulty reference oracle

Development tool only. Neither Gradle nor osu!java invokes this tool or performs network requests.

`generate.py` downloads **unchanged public MIT-licensed ppy/osu source files** at
`4e96853c7543f80a1b822ccd381943c7377543d7` (tag `2023.815.0`, calculator version `20220902`)
into a temporary directory and compiles/runs them with .NET 8. It then deletes the temporary sources.
No stable binaries, extracted assets, production API, or official score/star data are involved.
Source licence: https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/LICENCE

From the repository root:

```sh
DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 \
  python3 tools/difficulty-reference/generate.py --dotnet /path/to/dotnet
```

Fixture `.osu` files are authored here. Generated `.properties` files are checked in, so ordinary
Java tests need no .NET installation or network access. `Stubs.cs` only supplies fixture object/model,
single-precision vector and interpolation adapters. The original preprocessing, stacking processor,
evaluators and skill aggregators run unchanged. The wrapper uses the pinned NM final combination formula.
It sets circle defaults to the pinned `OsuHitObject` formula, spinner Great window to zero (`HitWindows.Empty`),
and standard Great half-window to `80 - 6*OD`. It does **not** emulate the full lazer decoder or stable client.
The unchanged public `SliderPath` and `SliderEventGenerator` also run. Linear approximation is the
identity in the framework adapter; all Bezier/perfect/Catmull paths throw. Minimal bindable/cache adapters
supply only these fixture contracts. The wrapper independently wires the public Slider default/nested
object contracts: relative float control points, first-red-line timing fallback (1000ms without red lines),
SV precision 0.01, legacy tail offset 36ms and nested StartTime sorting. It is not a full decoder oracle.

24 self-authored fixtures cover the original circle/spinner cases plus Linear paths: single slider,
first slider, spinner transitions, clipped/extended polylines, repeated terminal control points,
odd/even repeats, positive/negative stacks, ticks later than the early legacy tail, SV/red-line changes,
missing timing and timing before the first red line. Output includes actual path positions and length,
nested event times/stacked positions, lazy end/travel and minimum jump distance/time.
Arbitrary SV rounding boundaries, NaN/duplicate timing, coincident nested events and pre-v8 tick-distance
multipliers remain outside the verified subset. Java rejects these charts rather than substituting facts.

Acceptance tolerance, chosen before checking Java outputs: 1e-9 absolute for star/skill ratings;
1e-7 absolute + 1e-9 relative for per-object/section facts. Stack heights must agree exactly.
Java production code is an independent calculation using these formula contracts, with resource bounds
and explicit unsupported states. A passing fixture suite is evidence for this declared subset;
it is not a claim that every stable beatmap or the current 2026 calculator has equal values.
