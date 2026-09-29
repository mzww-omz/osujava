# Main Menu HUD: stable reference follow-up

Research: 2026-09-28–29. This supersedes the provisional HUD spacing in the first
pass of `main-menu-composition.md`. The Cookie, mirrored Play/Exit strips, menu
motion, backgrounds, pulse, audio player and other screens are unchanged.

## Evidence and boundary

The user authorised inspection of `/home/coder/workspace/b20230727.9`.
`osu!.exe` SHA-256:
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`.

Read-only `dnfile`/`dncil` inspection reads CLR metadata and method instructions.
It does not run the client, recover encrypted strings, emit embedded resources,
extract official assets or bypass protection. No binary or IL is bundled in the
app. The implementation is independently written Java using the existing HUD
owner and normal menu music input path.

Prior offline Wine attempts for this same build failed before Main Menu and
entered the updater; see [runtime limitation](song-select-repair.md#stable-runtime-limitation).
That failure is not a Main Menu capture. This follow-up does not claim a fresh
successful runtime observation or visual parity with the supplied build.

Visual cross-check uses the public [osu! wiki Main Menu illustration](https://raw.githubusercontent.com/ppy/osu-wiki/master/wiki/Client/Interface/img/main-menu.jpg)
and [interface documentation](https://github.com/ppy/osu-wiki/blob/master/wiki/Client/Interface/en.md).
It is a reference screenshot from another date, not the missing original user
attachment and not a captured execution of b20230727.9.

## Findings and independent implementation

| Finding | Evidence | osu!java decision at 1280×720 |
| --- | --- | --- |
| HUD uses height / 480 scale | MethodDef `0x06001dcb`, IL `0008–000d`; width/height accessors `0x06001dc9` / `0x06001dca` divide by this scale | Convert observed anchors into the existing 720-high virtual UI; do not alter shared UiLayout |
| General information starts at `(210, 0)` | Main Menu init `0x06003f60`, IL `07ad–07e4`: text ctor, nominal size 14, white, stored in field `0x0400278b` | Start at x=315 instead of previous x≈387; left-aligned three rows, approximately 16px text with 21px baseline spacing based on the public screenshot. The nominal stable font argument is **not** treated as a directly interchangeable Java font size |
| Same information is updated from library count, runtime and wall clock | `0x06003f77` uses localisation IDs 417/418/419 immediately with `0x0600392d`; count, hours/minutes/seconds, `DateTime.Now`, `ToShortTimeString`, then field `0x0400278b` text setter | Keep real difficulty count, application monotonic uptime and `LocalTime.now()`; preserve HH:mm / HH:mm:ss formatting and all three lines offline |
| Top and bottom neutral black bands have height 54 | Init `0x06003f60`, IL `0c5e–0d60`: two black sprites with width from `0x06001dc9`, height 54; alpha .4 in one branch. The public screenshot places corresponding bands at the two edges | 81px top **and** bottom instead of 76/22px. Neutral black alpha .38 closed / .42 open; retain subtle fixed separators. Exact runtime opacity branches were not measured |
| Left profile block is compact and begins near the edge | Public 1280×720 screenshot: icon roughly 70px square at (5,5), adjacent text near x=80; large number within the left block | Original procedural java! emblem at (5,5), application name and local labels at x=80, actual imported difficulty count as the prominent number. No avatar/account/PP/accuracy/level/progress imitation |
| Now Playing label sits beside the title; transports sit below | Public screenshot, right edge | Small two-line label, single-line ellipsized title, 26px hit regions at 30px pitch below, actual BPM on the third line. Only existing previous / pause-resume / next actions |
| Bottom branding sits low at the edge, with broad empty chrome around it | Public screenshot | Small osu!java/version at bottom left, LOCAL bottom right, empty centre. No chat, online users, fake banner, links or service controls |

The public screenshot supports visual proportions, not uninspected internal
profile/transport constants. Colour, font choice, emblem and compact fallback
remain deliberate osujava choices. Unlike stable's complete profile area, the
left block is sparse where there is no truthful local information to show.

## Layout, interaction and visual checks

`MainMenuFrame.Layout` owns column bounds. At desktop aspect ratios the left and
centre use observed fixed height-scaled anchors, while the music block stays
right-anchored and capped at 360 baseline units. This leaves the asymmetric
negative space visible in stable. Below 1000 effective units of width, the
existing narrow-window support uses a 44-unit emblem, proportionate columns and
smaller information text. This fallback is osujava policy, not a recovered stable
rule. Text remains independently ellipsized per region. The HUD never moves with
menu open/close; only emphasis changes slightly.

Production `MainMenuScreen` is captured by the existing deterministic framebuffer
harness, with local synthetic artwork and injected metadata/audio. Checks cover
1280×720, 1366×768, 1920×1080, 2560×1440, 1024×768, 600×800 and two 2x framebuffer
cases. Long Unicode titles, five-digit count and 99:59:59 runtime are included.
The first capture showed the Now Playing caption too small relative to the
reference; its font was increased from roughly 9px to 10.5px before the final
pass. Central menu visuals remain unchanged.

Artifacts are under `/tmp/osujava-mainmenu-stable-audit` (reference and comparison)
and `/tmp/osujava-stable-hud-final` (final production captures), outside Git.
The official screenshot is used only for comparison, never as an app asset.


Final validation: `./gradlew build` passed with 692 tests, zero failures/errors/skips.
The final harness passed 480 repeated framebuffer comparisons, 80 navigation
sequences and eight music interaction sequences. At all four requested
resolutions, every pixel between the top/bottom HUD bands in the OPEN long-title
scene is identical to the preceding commit's capture (central menu, background
and pulse unchanged). Audio device output and native monitor DPI switching are
not tested by the synthetic framebuffer/Music harness.
