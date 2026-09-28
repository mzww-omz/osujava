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

References consulted 2026-09-28:

- https://osu.ppy.sh/wiki/en/Skinning/skin.ini (missing ini = latest; missing Version
  in existing ini = 1.0; active/inactive text = black/white; animation rate = -1
  for a one-second cycle or positive frames per second).
- https://osu.ppy.sh/wiki/en/Skinning/Interface (Back animation, cursor layers/origins).
- https://osu.ppy.sh/wiki/en/Skinning/Sounds (selection, difficulty and hover events).

## Evidence limits and next work

Existing b20230727.9 IL inspection confirms the Song Select feature registration,
but does not establish pixel hitboxes, animation easing or current-version behavior.
The previous offline Wine launch did not reach Song Select. Exact alpha-based hitbox
behavior, 30% decorative clipping, alpha-based row fitting, fallback exceptions and
top-repeat seams therefore remain observation tasks. Large authored artwork is not
classified as corrupt merely because it is large. Allocation/path safeguards remain.

The recorded WhiteCat/Seoul sources are absent on this host; portable geometry tests
and synthetic captures must not be described as matched real-skin stable comparisons.
Group tabs/Mods icons depend on their separate browser/toolbox feature work. Other
rulesets, star calculation and online services are outside this skin change.
