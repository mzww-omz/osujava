# Architecture

## Data flow

~~~text
.osz / .osu
    │
    ▼
BeatmapArchiveImporter ── validates paths, extracts/copies local files
    │
    ▼
Local Beatmap Storage ── ~/.osujava/library/<set-id>/
    │
    ▼
BeatmapFileParser ── reads each stored .osu file
    │
    ▼
BeatmapSet / BeatmapDifficulty ── assets and .osu files resolved to local Paths
    │
    ├── PropertiesBeatmapLibraryStorage ── one index entry per set
    │
    ▼
BeatmapLibrary ── in-memory repository backed by the local index
    │
    ▼
SongSelectScreen ── selects a set and one difficulty
    │
    ▼
Ruleset (OsuRuleset)
    │
    ▼
GameplaySession ◄──── GameClock (Music position or local elapsed clock)
    │
    ├── click / cursor hold / Spinner rotation → osu! judgement → ScoreTracker → GameplayState
    ├── SliderPath + SliderTiming → tick / repeat / tail events
    │
    ▼
GameplayRenderer ── renders immutable GameplayState
    │
    ▼
ResultsScreen
~~~

Import and gameplay have no archive dependency in common. The importer owns extraction into local beatmap storage. The library index stores metadata and paths into that storage. Gameplay receives a resolved BeatmapSet and BeatmapDifficulty and does not read an archive or the index.

## Modules

### core

- beatmap: immutable records for sets, difficulties, timing points, hit objects, difficulty settings, and parsed Slider path data.
- beatmap.parse: parses General, Metadata, Difficulty, Events, TimingPoints, and HitObjects. The integer mode is retained even when the mode is not playable.
- library: safely imports .osz and standalone .osu files into local beatmap storage. BeatmapLibrary exposes the in-memory repository; BeatmapLibraryStorage is its persistence boundary, implemented by PropertiesBeatmapLibraryStorage.
- gameplay: one GameClock interface, audio/elapsed implementations, judgement windows, score state, and the session contract.
- ruleset: ruleset selection boundary. OsuRuleset judges HitCircles, basic Slider head/tracking/repeat/tail events, and Spinner rotation/completion.
- ui: screens, input mapping, playfield viewport, and renderers. GameplayRenderer consumes state snapshots and does not update score or hit state.

### lwjgl3

Starts the libGDX desktop backend and provides a native file chooser. The core flow is kept separate from desktop window startup.

## Import behavior and safety

An .osz is treated as a Beatmap Set source. Java's ZipInputStream extracts every bundled file into a unique directory under ~/.osujava/library. Entry paths are checked before writing: absolute paths, parent traversal, drive prefixes, backslashes, and duplicate normalized paths are rejected. Total expanded content is capped at 1 GiB.

Every .osu file is parsed independently. Valid difficulties are grouped into one set; a malformed difficulty is skipped with a warning if another valid difficulty remains. An archive with no .osu file or no valid difficulty returns a user-visible Import error. Audio and background paths are resolved relative to each .osu file and must remain inside the imported directory.

For a standalone .osu, the importer copies the chart and its referenced audio/background files into the same local storage structure. Missing optional assets do not prevent the chart from importing.

Storage and the index have separate responsibilities. Imported chart and asset files live under `~/.osujava/library/<set-id>/`. The index lives under `~/.osujava/library/index/`, with one versioned Java Properties file per set. Each index entry stores set metadata, each difficulty's title, artist, creator, version and mode, plus relative audio, background and `.osu` paths. The `.osu` path is used to rebuild timing points and hit objects when the app starts; Gameplay still receives the parsed model.

The importer uses a positive osu! `BeatmapSetID` as the stable set id. When a chart has no positive set id, it hashes normalized title, artist, creator and the sorted mode/version pairs. Reimporting the same id replaces the set's local files and atomically replaces its index entry, so the library has one entry for that set. With the fallback identity, two unrelated custom sets with identical metadata and mode/version pairs are treated as the same set.

`BeatmapLibrary` loads the index during app startup and saves after each imported set. Each set entry is read independently; a damaged entry or difficulty is skipped so the rest of the library remains usable. Stored paths must remain inside local beatmap storage. Missing audio/background files resolve to null and Gameplay uses its existing local-timer fallback; a missing or unparsable `.osu` file causes that difficulty to be skipped. Index writes use a temporary file and atomic replacement where supported.

On its first load, the index storage also scans unindexed UUID-named folders left by the earlier importer, rebuilds their set metadata from the stored `.osu` files, and writes index entries without moving the assets. This one-time recovery keeps beatmaps imported before persistent indexing available after upgrading.

## Clock and gameplay

GameClock is the only source of gameplay time. With audio, MusicGameClock samples libGDX's music position. If the audio file is absent or cannot be decoded, ElapsedGameClock provides a local fallback timeline. OsuGameplaySession reads the clock, expires misses, resolves pointer clicks and Spinner active intervals, and creates a GameplayState snapshot.

The playfield uses osu!'s 512×384 coordinate space fitted into the window. `PlayfieldViewport` is the shared transform for input, object positions, and logical lengths, so circle radii and Slider geometry scale with the same aspect-preserving factor. Rendering reads the same immutable snapshot and never changes hit status or score. SliderPath calculates the curve and its distance-adjusted position independently of the renderer. SliderTiming derives velocity from the active redline and inherited timing point, then maps GameClock time to alternating span progress. Slider head, tick, repeat, and tail states are judged in OsuGameplaySession and contribute to ScoreTracker.

Gameplay visuals derive approach, fade, judgement, and follow-circle animation values from the snapshot's GameClock time and object timing. `GameplayState` also carries short-lived judgement visuals emitted beside their existing judgement events; these do not affect ScoreTracker. `GameplayRenderer` draws slider bodies from the existing sampled SliderPath and caches primitive path coordinates while a Slider is visible. Colors and default visual dimensions live in `GameplayVisualConfig`, separate from ruleset logic. `GameplayHudRenderer` draws combo numbers, 300/100/50/Miss feedback, and the score, accuracy, and combo HUD.

Judgement windows use Overall Difficulty; approach circle timing uses Approach Rate. Score and accuracy are calculated by ScoreTracker. Slider nested events currently use an equal-weight hit/miss contribution in this local score model. Unit tests inject a controllable GameClock instead of reading wall-clock time.

### Slider compatibility and scoring limits

SliderTiming follows the lazer osu! formula for base scoring distance, slider multiplier, active redline, inherited velocity (clamped to 0.1–10x), and tick spacing. Repeat progress reverses on alternating spans. Tick generation omits points within 10 ms of a span end, and the visible tail judgement keeps lazer's 36 ms leniency. The tail waits for earlier generated ticks and repeats to be processed before it is scored. The legacy last tick is not generated because lazer retains it for internal compatibility uses rather than regular osu! gameplay scoring.

SliderPath is intentionally a sampled piecewise-linear approximation. Bezier curves use adaptive subdivision with 0.35 px flatness; Catmull and B-spline paths use 50 samples per segment/span; perfect curves use roughly 2 px spacing along the arc. Lazer's PathApproximator uses different tessellation and legacy-version edge cases, so tight curves and unusual duplicate control points can differ slightly. Current tests cover each curve type and expected distance, but do not assert point-for-point equality with lazer.

Scoring is also deliberately simpler than lazer. The slider head uses the ordinary timing judgement, while each tick, repeat, and tail is recorded as either one `HIT300` or one `MISS`. ScoreTracker therefore gives every nested event the same maximum score and accuracy weight. Lazer keeps object-specific judgement results (for example, slider ticks and end nodes use large-tick results) and applies ruleset scoring, combo, and health rules to those results. The local score, accuracy, and combo are not expected to match lazer. A future scoring change belongs in the osu! session's event-to-judgement mapping and ScoreTracker's typed/weighted judgement model; it should not add state changes to the renderer.

Input routes left click, right click, and Z/X keyboard presses through the same session click path. Holding any one of them enables Slider tracking and Spinner rotation; tracking stops when all are released. Lazer can restrict which key sustains a slider immediately after its head is hit; osujava does not yet model that key-specific transition. Spinner movement is sampled from cursor-position events while the GameClock time is inside the Spinner interval.

### Spinner compatibility and scoring limits

The parser stores Spinner end time in `SpinnerData`; Gameplay uses the model start/end interval and never re-reads `.osu` text. Legacy Spinner position is normalised to the playfield centre, matching lazer. The cursor angle is measured around that centre and each delta is wrapped to the shortest range of −180° to +180°. A 12 osu-unit centre dead zone resets the angle baseline so movement through the singular centre cannot add a large or noisy turn; lazer's current drawable tracker does not use this distance threshold.

Required spins use lazer's OD-interpolated clear RPM values of 90/150/225 at OD 0/5/10, multiplied by Spinner duration and floored with its 0.0001 precision allowance. Full-spin progress is unidirectional: reversing first undoes progress in the current turn, which prevents rapid direction changes from manufacturing spins. Two normal spin ticks follow the clear requirement before large bonus ticks begin. Spinner result thresholds follow lazer: full progress is HIT300, above 90% is HIT100, above 75% is HIT50, and lower progress is MISS. A zero-spin requirement completes implicitly.

The local ScoreTracker records one Spinner result for accuracy and combo. Completed normal ticks add 10 score each and completed bonus ticks add 50 score each, without changing accuracy or combo, matching lazer's base tick values. The full lazer scoring processor, score multipliers, health, samples, and tick miss results are not modeled. GameplayState carries rotation and progress values for the renderer; rendering does not change Spinner state or judgement.

## Extending rulesets

Add another Ruleset implementation that declares which imported mode it supports and creates its own GameplaySession. Keep mode parsing in the shared importer/model, and keep each ruleset's hit-object interpretation and judgement rules within that implementation. OsuRuleset owns Slider timing, path traversal, and judgement alongside HitCircle judgement.
