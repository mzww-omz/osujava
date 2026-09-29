# selection-mode upper chrome recheck (2026-09-29)

The user clarified that the selection-mode canvas includes upper chrome. Seoul
is not available in this environment; the checks below do not use that skin.

## Static evidence

Read-only inspection of the existing local b20230727.9 IL, without extracting
assets, bypassing protection or running stable:

- Song Select constructor `0600136e`, `03c8–04ec`: the title/metadata text
  sprites in the main sprite manager use depth .79.
- `0886` / `08cc`: Mode normal and hover use depths .95 / .96 respectively.
- `0bcd` / `0c13`, `0cf0` / `0d36`, `0e00` / `0e46`: the other bottom
  selection pairs also use .95 / .96. These are layers shared across buttons,
  not successive normal/hover pairs per button.
- Song Select score creation `060013b4` uses the lower score sprite layers
  documented in the prior static inspection, below .95.

## Correction

`5aa56ed` moved card backgrounds behind selection artwork but still drew
metadata and local ranking text after it. That left upper chrome incorrectly
covered by text. The renderer now draws metadata and rankings before selection
artwork, then draws **all normal selection images**, then **all hover images**.
The latter fixes a Mode hover extension being covered by Mods/Random/Options
normal artwork.

This supersedes the metadata/score-text layering policy in
`songselect-card-chrome-recheck.md`. Upper chrome is part of the authored image:
its opaque pixels can mask lower-layer text, while transparent pixels continue
to show the underlying content. There is no skin-name special case or automatic
relocation/cropping of the upper artwork.

Search/browser controls, overlays and local auxiliary UI have their existing
placement; complete stable UI depth parity is not claimed by this small fix.
Full-image origins, density normalization, image aspect ratios and bounded
button input geometry are retained.

## Regression coverage

The generated `upper-chrome` / `upper-chrome-high` fixtures contain:

- A 1150x768 logical Mode canvas with an opaque green upper region crossing the
  title/mapper area. Framebuffer sampling across that region rejects any text
  drawn over it. Equivalent SD/HD canvases are tested.
- A red Mode hover marker crossing the Mods normal canvas. After the ordinary
  hover fade has settled, its red pixels must remain visible. The marker must
  not expand Mode's input into Mods.

These are diagnostic shapes, not extracted or reproduced Seoul assets.

`JAVA_TOOL_OPTIONS=-Xmx256m MALLOC_ARENA_MAX=2 LP_NUM_THREADS=1` kept the visual
run within this environment's memory limit; tests were run sequentially.

- `./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectPhase=selection-chrome
  -PsongSelectOutput=/tmp/osujava-selection-chrome --offline --console=plain`:
  **16 scenes / 16 captures**, framebuffer and navigation/disposal checks passed.
  Includes 1280x720, 1920x1080, high framebuffer density and 1024x768, both new
  fixtures, existing composite artwork and the Mode overlay.
- `./gradlew build --offline --console=plain`: BUILD SUCCESSFUL; **723 tests**,
  no failures, errors or skips.
- Visually inspected the upper-chrome capture: green upper art masks only its
  authored rectangle; the red hover marker remains over the adjacent blue
  normal button. No stable runtime/pixel comparison was performed.
