# Song Select skin compatibility work

## Plan and authority

This work follows the Song Select audit, with skins treated as authored compositions,
including transparent replacements, asymmetric padding and composite artwork.
The application stays offline. No client artwork or proprietary implementation is copied.

Implement in small verified changes:

1. Fix selected-skin configuration ownership, missing-ini version and default text colours.
2. Play the complete `menu-back` animation using `AnimationFramerate`; preserve provider,
   density, origin, deterministic timing, allocation limits and disposal.
3. Add missing selection-screen cursor and interface sounds using existing visual/audio
   boundaries; no gameplay session or GameClock is created for menu presentation.
4. Verify production captures, regressions and the full Gradle build. Record unresolved
   compatibility instead of turning unmeasured guesses into expected test results.
5. Preserve oversized selection/Back decoration across the viewport while retaining
   bounded controls; add a framebuffer regression above the old 30% clipping line.

References consulted 2026-09-28:

- https://osu.ppy.sh/wiki/en/Skinning/skin.ini (missing ini = latest; missing Version
  in existing ini = 1.0; active/inactive text = black/white; animation rate = -1
  for a one-second cycle or positive frames per second).
- https://osu.ppy.sh/wiki/en/Skinning/Interface (Back animation, cursor layers/origins).
- https://osu.ppy.sh/wiki/en/Skinning/Sounds (selection, difficulty and hover events).

## Evidence limits and next work

Read-only b20230727.9 metadata/IL inspection was performed for this investigation
(`osu!.exe` SHA-256 `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`).
Song Select constructor token `0x0600136e`, sort enum `0x020008c4`, group method
`0x06001376` and options method `0x0600138d` were examined. No assets were extracted
and no protection was bypassed. See also [the reference audit](song-select-stable-reference.md).
This inspection confirms the Song Select feature registration,
but does not establish pixel hitboxes, animation easing or current-version behavior.
The previous offline Wine launch did not reach Song Select. Exact alpha-based hitbox
behavior, chrome clipping, alpha-based row fitting, fallback exceptions and
top-repeat seams therefore remain observation tasks. Large authored artwork is not
classified as corrupt merely because it is large. Allocation/path safeguards remain.

The recorded WhiteCat/Seoul sources are absent on this host; portable geometry tests
and synthetic captures must not be described as matched real-skin stable comparisons.
Group tabs/Mods icons depend on their separate browser/toolbox feature work. Other
rulesets, star calculation and online services are outside this skin change.

## Implemented and verified

- Selected skin settings no longer inherit another skin's ini when the selected
  directory lacks one. Existing ini without Version remains legacy 1; absent ini
  uses latest supported 2.7. Song Select text defaults are black/white. Gameplay's
  existing resolver configuration policy is deliberately unchanged.
- Back frames retain density and provider resolution, use the configured frame rate
  (or one full cycle per second), and are disposed with the screen. Input uses the
  first frame's existing bounds so frame changes do not move the control. The local
  safety budget is 512 frames and 64 Mi decoded pixels across Song Select images;
  these limits are not claimed as stable behavior.
- The menu now displays cursor/trail/middle and cursor ini settings using the existing
  visual component and menu elapsed time. Native cursor visibility is restored when
  leaving. Menu presentation does not instantiate a gameplay session or GameClock.
- Interface samples are preloaded with provider/format fallback and master/effect
  gain. Set/difficulty selection, row/control hover, Back, play, dropdown opening,
  confirmation and typed search characters emit events. Numbered key sample names
  are preserved. Drawing does not load or play sounds; repeated hover is suppressed.
- Selection and Back artwork retain native dimensions and may extend above the bottom
  reservation. Their input regions remain bounded. Top/bottom background clipping,
  artwork layering and alpha-based control policy still need stable runtime comparison.

Validation on 2026-09-28:

- `./gradlew build --offline --console=plain`: passed, 686 tests, no failures/errors/skips.
- Xvfb foundation visual harness: 76 scenes, 172 PNG captures, 960 scripted transition
  frames plus navigation/disposal checks; output `/tmp/osujava-skin-implementation-foundation`.
- Composite framebuffer regression passed at 1x and 2x framebuffer density: decoration
  above the former clipping line is visible and does not become clickable. Outputs
  `/tmp/osujava-skin-composite` and `/tmp/osujava-skin-composite-2x`.
- Synthetic tall two-frame Back and custom cursor captures at 0.1s/0.6s were inspected:
  red/cyan frames alternate, artwork extends above 30%, and the custom cursor appears.
  Outputs `/tmp/osujava-skin-frame0` and `/tmp/osujava-skin-frame1`. These fixtures are
  generated test graphics, not extracted official assets or matched stable captures.
- Audio uses mock playback assertions for event choice, gain, fallback, caching and
  disposal. Audible device output and exact stable timing have not been compared.

Remaining precision work: transparent controls currently have no alpha hit region;
row alpha cropping can reshape authored layouts; current cursor layers can mix with
fallback layers; key samples cycle deterministically rather than reproducing an
observed stable distribution; overlay/group/sort hover and all keyboard confirmation
sounds are not complete. Stable Back hover motion, animation reset points, pointer
expansion/trail timing, skin User-folder overrides and default/fallback exceptions
also need measured comparison. Re-run the same input recordings against actual
WhiteCat/Seoul and minimal/transparent/composite skins before declaring parity.
