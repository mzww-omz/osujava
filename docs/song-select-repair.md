# Song Select repair verification (2026-09-28)

This phase fixes reproduced defects in the existing classes. It does not change
screen ownership, replace the renderer/input system, or change the spring model.
The earlier [redevelopment report](song-select-redevelopment.md) describes the
starting point; its successful harness runs did not establish visual correctness.
Stable files were not modified or copied during this repair.

## Reproduction and fixes

| Observed defect | Cause | Minimal fix | Regression evidence |
| --- | --- | --- | --- |
| At 1280×720, the selected body covered the following difficulty's title. Skin changes changed the amount of overlap. | A 72-unit row pitch was combined with a skin-derived 76–88-unit body. Selected-last drawing made the overlap particularly visible. | Fit row artwork into a body equal to the logical pitch. Keep selection origin, spring and texture-body normalization. | `SongSelectLayoutTest`: settled adjacent bodies, selection center and star baseline at startup/720p/1080p/2× resolutions; rendered skin variants. |
| At the desktop's 1100×720 startup size, hovering the selected row exposed its right edge, leaving a gap to the screen edge. | A half-screen body width did not include the existing leftward hover/group displacement. | Use the existing leftmost bound to reserve enough constant body width. Drawing and clicking still share the same `SongSelectRow`. | Maximum-hover extent and right-edge hit coverage at 960/1100/1280/1920 widths; startup-size captures. |
| `I` opened Import and also entered `i` into search. Space-to-play could similarly start a query. | GLFW emits both `keyDown` and `keyTyped`; consuming one does not suppress the other. Existing tests only sent `keyDown`. | Consume the character belonging to an executed printable shortcut; keep normal typed search and release handling. | `SongSelectNavigationTest`: paired down/typed/up events, selection preservation, normal query editing, Space. Real file-dialog open/cancel. |
| 1366×1400 chrome assets painted over the whole background. | Layout reservation was bounded, but drawing still used the full image height. | Scissor only top/bottom artwork to the existing 40%/30% safety limits. Flush each batch before changing scissor; convert logical bounds to framebuffer pixels once. | `SongSelectChromeTest` plus giant SD/@2x framebuffer samples at each resolution. Normal assets keep their native dimensions and transparent placeholders. |
| A successfully imported backgroundless Set disappeared after restarting the normal application. | Storage saved an empty `backgroundFilename`, then rejected that optional value as required during load. | Accept the optional background name, retaining all stored-path validation. | `PropertiesBeatmapLibraryStorageTest`: actual import/save/new-Library round trip; normal app import and restart with two Sets. |

The first two changes supersede the previous report's skin-body sizing policy.
Wheel travel remains one body per input unit, now equal to one row pitch. It no
longer changes when a different row image is installed. No new interpolation or
time source was added.

## Normal application checks

Started the production `Lwjgl3Launcher` via `lwjgl3:run`, then reused that launcher's
runtime classpath for subsequent restart checks. This was not the harness's mock
Library or input proxy. Xvfb provided an X11 display; xdotool sent actual window
keyboard/mouse events, and window captures were visually inspected.

The host initially had only headless Java. Installing the Java 21 GUI runtime
allowed the real file chooser to run. Generated `.osz` fixtures contained local
silence, simple circles, eight difficulties and generated artwork; they were
imported through the normal file chooser. No production osu! service was used.

Observed before/after checks include:

- Empty startup, Import, one Set with eight difficulties, two Sets, restart.
- 1100×720 startup, 1280×720 resize, 1920×1080 resize.
- Hover, held press/release, drag away/cancel, difficulty click, collapsed-Set
  click/expansion, wheel and keyboard return to the selection.
- Up/Down, Page Up/Down, F2, Enter into gameplay, Escape back to Song Select,
  Escape/Back to the main menu, Mods and Mode open/close.
- Bundled skin and deliberately oversized chrome before/after; background present
  and absent; `I` followed by file-dialog cancellation without changing the query.

Window captures are under `/tmp/song-repair-*.png` on the validation host. Examples:
`1280-before`, `row-after-down`, `gameplay-escape` (pre-width-fix),
`hover-width-after`, `giant-before`, `giant-after`, `no-bg-restart`, `1080`,
`held`, `released`, `drag-cancel`, `mods`, `mode`, `back`.
These images and generated Library fixtures are not repository assets.

The temporary X11 display disconnected during some long sessions, producing a
native XIO error in the application. Those sessions were restarted, and interrupted
steps are not counted as successful checks. This was distinct from a Song Select
exception; the stack entered `_XIOError`/`XSync` after the display disappeared.

## Repeatable verification

The `repair` harness profile retains original profiles and adds actual empty and
one-difficulty Libraries, a 10 FPS transition, and the desktop startup aspect ratio.
Its matrix includes 1100×720, 1280×720, 1920×1080 and 1280×720 with a 2× framebuffer;
bundled/default and explicitly selected Greylooks; missing/transparent/tiny/@2x
assets; oversized chrome; thumbnails/stars/long text; 16-difficulty expansion;
1000 Sets; reverse scrolling and filtering/group transitions.

```sh
./gradlew test build
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  -PsongSelectPhase=repair -PsongSelectOutput=/tmp/song-repair/repair-matrix
```

The old harness's hard-coded 50–70% horizontal bound rejected a legitimate fully
hovered single row (about x=638 at width=1280). It now checks the current explicit
40–85% clamps, body coverage of the right edge and skin-independent row density.
This corrects a stale assertion; it does not move rows to satisfy the test.
The giant-chrome pixel check samples outside the separately drawn controls.

Final `test build` passed with 648 tests, zero failures/errors/skips. The final
`repair` run passed 80 scenes, 252 PNG captures and 1804 scripted transition frames,
including navigation/disposal checks. The earlier dedicated chrome run also passed
33 scenes (before the subsequent row-width correction); the final repair matrix
rechecked giant chrome after all fixes.

Captures inspected directly included the 1100×720 hover, 1280×720 empty and single
Libraries, default/Greylooks rows, procedural missing assets, stars, thumbnails,
1920×1080 giant @2x chrome and 2× framebuffer low-FPS convergence. Normal window
captures additionally establish the real input/import/restart behavior listed above.
The two initial harness failures (edge sample under a control; obsolete hover bound)
were corrected and rerun; they are not reported as successful runs.

## Remaining limits and intentional differences

- The existing critically damped spring, immediate keyboard selection, end padding,
  and local hover policy remain. No additional Stable fidelity claims are made.
- Mode/Mods still display existing unavailable choices; Beatmap Options is still
  unavailable. This phase does not implement those gameplay features.
- Thumbnail decoding remains synchronous; a first large image can stall a frame.
- System font availability still controls CJK glyph coverage. No font work was
  included in this repair.
- Oversized chrome is cropped at the existing safety reservation, so decoration
  outside that region is intentionally omitted. Normal artwork is not rescaled.
- A scene count is coverage, not proof of every skin or device. No live Stable
  side-by-side comparison or physical Retina-device check was performed.
