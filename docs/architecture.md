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
    ├── click input → judgement → ScoreTracker → GameplayState
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

- beatmap: immutable records for sets, difficulties, timing points, hit objects, and difficulty settings.
- beatmap.parse: parses General, Metadata, Difficulty, Events, TimingPoints, and HitObjects. The integer mode is retained even when the mode is not playable.
- library: safely imports .osz and standalone .osu files into local beatmap storage. BeatmapLibrary exposes the in-memory repository; BeatmapLibraryStorage is its persistence boundary, implemented by PropertiesBeatmapLibraryStorage.
- gameplay: one GameClock interface, audio/elapsed implementations, judgement windows, score state, and the session contract.
- ruleset: ruleset selection boundary. OsuRuleset currently judges HitCircles and ignores Slider/Spinner gameplay.
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

## Clock and gameplay

GameClock is the only source of gameplay time. With audio, MusicGameClock samples libGDX's music position. If the audio file is absent or cannot be decoded, ElapsedGameClock provides a local fallback timeline. OsuGameplaySession reads the clock, expires misses, resolves pointer clicks, and creates a GameplayState snapshot.

The playfield uses osu!'s 512×384 coordinate space fitted into the window. Input converts screen coordinates back into that space. Rendering reads the same immutable snapshot and never changes hit status or score.

Judgement windows use Overall Difficulty; approach circle timing uses Approach Rate. Score and accuracy are calculated by ScoreTracker. Unit tests inject a controllable GameClock instead of reading wall-clock time.

## Extending rulesets

Add another Ruleset implementation that declares which imported mode it supports and creates its own GameplaySession. Keep mode parsing in the shared importer/model, and keep each ruleset's hit-object interpretation and judgement rules within that implementation. The current OsuRuleset is intentionally limited to HitCircles.
