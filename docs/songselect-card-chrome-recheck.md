# Song Select card / ranking / chrome recheck (2026-09-29)

Reported skin: Seoul v9. The actual skin is not present in this Linux workspace;
its path has been requested. The older corpus records a 699x103 SD card and a
1150x540 selection-mode composite, but these records are not a substitute for
rendering the user's files. No stable runtime comparison is possible here.

## Evidence and correction

Read-only IL inspection of local b20230727.9 (same binary hash as the earlier
static audit), with no asset extraction or execution of protected routines:

- `06000fbf`, `00b5–010b`: card sprite uses Field 6, CentreLeft origin and the
  row position. Its canvas is not resized using the nontransparent bounding box.
- `060040a7` initializes the sprite's scale to 1; `060040af` applies the skin
  canvas conversion independently of the 480-high position coordinates.
- `060013b4`, `08d5–0917` and `0af2–0af8`: the Song Select score background uses
  the same texture, CentreLeft, and scalar .55. Its colour is black; the hover
  alpha endpoints at `0a66–0a7e` are .3 and .6.
- `0600136e`, `1b85–1c2c`: the top extension uses a one-column source, rather
  than repeating a 20px band. Stable's standard-width path addresses column
  1365. Java extends the actual texture's last column to also support arbitrary
  custom canvases; that generalization is a local policy.

Java changes:

1. Card artwork keeps native SD dimensions at height/768, anchored CentreLeft.
   Transparent padding no longer changes its scale or translation. Row pitch,
   labels and input retain their own geometry. The earlier thumbnail extended
   beyond a 72-unit *hit rectangle* while the card background was squeezed into
   that rectangle; a Seoul-size card is now 655.3125x96.5625 at 720 UI height.
2. Ranking uses the independent .55 native scale and black tint. Its local
   column is capped by a 700 SD reference width at that scale, so local score
   labels are not laid out to the former much wider stretched surface. Header,
   labels, row pitch and selected stripe remain Java's local-score presentation;
   this is not full stable scoreboard parity or hover-animation parity.
3. Cards and score backgrounds precede top/bottom chrome and selection artwork.
   This prevents row backgrounds/labels erasing the composite decoration. This
   is a composition correction; complete stable sprite-depth parity has not
   been established. Foreground metadata, controls and local-score text remain
   above chrome. Input bounds do not grow with decoration.
4. Top extension samples the last column uniformly, avoiding tiled edge motifs.

## Verification

Native SD/HD card and score scale regression tests include the recorded Seoul
canvas sizes, unusual aspect ratios, and transparent 1px replacement sizes.
The composite visual fixture has an extra green marker crossing the carousel;
a framebuffer assertion verifies it survives card drawing without expanding
button hit bounds. The fixture is generated geometric artwork, not Seoul art.

Final build and visual results are recorded after execution below. These tests
verify Java geometry/composition, not visual equality with stable or Seoul v9.

- Final `./gradlew build --offline --console=plain`: **723 tests**, no failures,
  errors or skipped tests; BUILD SUCCESSFUL.
- `songSelectPhase=thumbnail-mode`: **28 scenes / 52 captures / 240 scripted
  transition frames**, navigation/disposal checks passed. Includes rankings,
  4:3, widescreen, high density, portrait/wide thumbnails and chrome.
- `songSelectPhase=case`, `songSelectCase=phase5a-assets-composite`: **1 scene**,
  framebuffer overlap and navigation/disposal assertions passed.
- Manually reviewed the ranking capture, 4:3 thumbnail capture, and the composite
  marker capture. Artifacts: `/tmp/osujava-card-chrome-targeted/` and
  `/tmp/osujava-card-chrome-overlap-final/`.
- The first broad visual run exited 137; the concurrently started case run
  crashed in native code. The cgroup reported OOM kills. A later broad run was
  stopped when memory again approached the 4 GiB limit. Those broad runs are
  **not passes**. Final focused runs were sequential with
  `JAVA_TOOL_OPTIONS=-Xmx256m MALLOC_ARENA_MAX=2 LP_NUM_THREADS=1` (the isolated
  composite case also passed with a 384 MiB heap and two rendering threads).
