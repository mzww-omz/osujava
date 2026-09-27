# Music-first Main Menu

This replaces the previous stable-inspired, left-aligned cookie and always-visible strips. Song Select, Gameplay, gameplay audio timing and skin rendering are unchanged. The app remains fully local.

## Composition and state

`MainMenuModel` owns `CLOSED → OPENING → OPEN → CLOSING → CLOSED`. `MainMenuScreen` draws that model, and `MainMenuInput` requests actions. Transition reversal starts from the current width/scale, avoiding jumps.

CLOSED shows cover artwork, a centred java! logo and radial visualisation. Only artist/title and BPM appear quietly at bottom right. There is no top panel, footer, library count, clock or permanent dark action strip. OPEN adds EXIT on the left and PLAY on the right, growing from behind the logo. Background dim increases from .16 to .33; gradients at the top/bottom provide a light vignette. Missing/broken artwork uses a dark gradient.

The Main Menu logo uses the supplied 1400×1400 RGBA `java!` artwork, bundled unchanged as `core/src/main/resources/ui/main-menu-logo.png`. Transparent corners expose the background; mipmapped filtering keeps the logo smooth at reduced OPEN size and Retina density. The texture follows the existing animated circular bounds, so beat/hover/press/transition scales, visualiser circumference and hitbox remain aligned. The former drawn pink disk and separate text are replaced by this asset. The screen owns and disposes the texture.

Both wedge roots stay at the logo centre. The outer edge has a 20-unit diagonal. Drawing and hit testing share `MainMenuLayout.outer()`, including hover width; the circle obscuring the wedge is excluded from the button hitbox. Buttons become clickable at 80% expansion, and closing/pending navigation disables them. Press and release must target the same visible element; dragging outside cancels the click.

Logo click opens/closes the menu. P/Enter/Space navigate directly from any non-pending state without waiting for menu expansion. Escape closes OPEN/OPENING, is ignored while CLOSING, and exits from CLOSED. PLAY navigates to Song Select; EXIT exits. A single pending action prevents duplicate navigation. The 200ms outgoing fade includes button brightness, outward expansion and audio fade.

## Lazer source of truth and intentional differences

Inspected the local osu!lazer checkout at `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` and checked current upstream sources on 2026-09-27. Referenced behaviour is MIT licensed; attribution is retained in [ppy-MIT.txt](licenses/ppy-MIT.txt).

| Source | Confirmed behaviour | osujava decision |
| --- | --- | --- |
| [OsuLogo](https://github.com/ppy/osu/blob/master/osu.Game/Screens/Menu/OsuLogo.cs) | Beat contraction uses `min(1, .4 + maximum)`; 60ms early Out, then two-beat OutQuint recovery. Continuous amplitude cutoff .4, scale factor .04, damping .9. Hover 1.1 over 500ms OutElastic; down .9 over 1000ms Out; release 500ms OutElastic. Click flash .4, 1500ms OutExpo. | Independent hover × press × beat × amplitude × transition layers. Same small pulse, hover/down/release durations and flash. Amplitude layer activates only for real analysis; the fallback does not pretend to measure loudness. No online UI, beat samples or decorative triangles. |
| [LogoVisualisation](https://github.com/ppy/osu/blob/master/osu.Game/Screens/Menu/LogoVisualisation.cs) | 200 bars, 5 rounds, index step 5, updates every 50ms without catch-up. Maximum length 600; decay per millisecond .0024 × (amplitude + .03); dead zone 1/600. Kiai multiplier 1, otherwise .5. Angle includes round × 360/5. Additive blending; draw colour includes .2 opacity. | Constants, peak hold, decay, rotation and kiai multiplier retained. Length scales with logo size for responsive layout. White with slight pink; base alpha .5 × draw opacity .2. First amplitude sample is immediate; long frame stalls decay once and never invent missing audio samples. |
| [MenuLogoVisualisation](https://github.com/ppy/osu/blob/master/osu.Game/Screens/Menu/MenuLogoVisualisation.cs) | Defaults to white, supporter skin may override glow. | Fixed subtle white/pink, no account/supporter dependencies. |
| [ButtonSystem](https://github.com/ppy/osu/blob/master/osu.Game/Screens/Menu/ButtonSystem.cs) | Initial → TopLevel scales logo to .5 in 200ms In; delayed button area, logo tracking, initial-state restoration. | Logo remains centred, scales to .65 in 200ms quadratic In. Larger scale balances two horizontal local actions. No toolbar, tracking or 150ms button delay. Closing takes 300ms OutExpo. |
| [MainMenuButton](https://github.com/ppy/osu/blob/master/osu.Game/Screens/Menu/MainMenuButton.cs) | 500ms OutExpo expansion, sheared geometry; hover width 1.5 over 500ms OutElastic; exploded width 2× over 200ms OutExpo with fade. | Mirrored 20-unit wedges (lazer `ButtonSystem.WEDGE_WIDTH`), 380ms OutExpo expansion. Hover adds 15% with elastic motion, slight text movement and brightness. Click adds up to 25% outward width, fading over 200ms, preserving room on narrow screens. |
| [BeatSyncedContainer](https://github.com/ppy/osu/blob/master/osu.Game/Graphics/Containers/BeatSyncedContainer.cs) | Track clock, timing/effect points, early activation and floor-based beat index before first point. | Reads imported points and Music position, including BPM changes. Phase calculation uses floor. Current playback point is selected before the 60ms pulse offset; no gameplay clock changes. |

`MainMenuMotion.beatScale` evaluates the repeating contraction/recovery directly from track time, including the preceding beat's recovery at each early activation. Beat maximum is sampled once at each early beat activation, separately from the continuous amplitude layer. It skips negative beat indices. This avoids accumulating UI-clock drift and makes fixed-time capture reproducible. Amplitude is clamped by the visualiser before rendering; audio-analysis implementations should return finite normalized maxima/bins.

## Ambient selection and lifecycle

The screen chooses one local `BeatmapSet` on construction, preferring usable artwork. It selects one difficulty with usable artwork (otherwise the first difficulty). Background, audio path and imported timing points come from that same difficulty, with set asset fallback. No per-frame reselection or new parser exists.

`MenuAmbientAudio` owns the libGDX Music instance and creates it on `show()`. It starts at zero, loops, and plays at .65 volume. Leaving via PLAY/EXIT fades volume alongside navigation for 200ms, then stops/disposes before the callback. `hide()` and `dispose()` also release safely. The reference is cleared before stop/dispose; repeated cleanup cannot touch a dead instance. Missing/open/play/position/volume failures release the stream and use silent fallback time. Reentering the same live screen creates a fresh stream at zero; returning from Song Select normally creates a new Main Menu and new ambient selection. Resume semantics are intentionally not used.

`MenuBeatTiming` ignores inherited/nonpositive/invalid beat lengths, selects the latest applicable red point, and uses the first valid point before its offset. BPM changes reset phase origin to their timing point. Missing/unopenable audio uses 1000ms / 60 BPM and delta-driven silent time. Playable audio with no valid points uses a documented 500ms / 120 BPM default; this malformed-map case does not trigger the missing-audio 60 BPM rule. Kiai comes from the latest applicable timing/effect point, including inherited points.

## Analysis boundary and PCM investigation

Production currently uses **DeterministicMenuAudioFallback, not true spectrum/FFT**. It generates 200 subdued bins from BPM phase and playback time using fixed sine/cosine spatial waves and a decaying beat envelope. No per-frame randomness exists. Same position/timing yields exactly the same generated bins. The visualiser then applies the source-derived temporal peak/decay algorithm; its live history naturally depends on previous samples. Harness captures reconstruct fixed analysis snapshots instead of relying on live history.

`MenuAudioAnalysis.sample(positionMs, beat)` sits outside drawing. UI/model consumers read only `maximumAmplitude()`, `frequencyAmplitudes()` and `available()`. `available()` means real PCM analysis; it is false for the synthetic fallback even when music playback works. The interface is injected in the harness; fixed bins, amplitude, timing points and playback position exercise the true-analysis scale layer. Resource ownership includes `close()` for a future analysis worker.

libGDX Music exposes playback position but no PCM/frequency amplitudes ([Music API source](https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/audio/Music.java)). Checked the installed 1.14.2 backend POM, source JARs and these primary upstream references:

| Candidate | Formats / packaging | Licence and maintenance evidence | Decision |
| --- | --- | --- | --- |
| Existing `com.badlogicgames.jlayer:jlayer:1.0.1-gdx` | Pure Java MP3 PCM decoder; already a desktop runtime dependency and available on [Maven Central](https://central.sonatype.com/artifact/com.badlogicgames.jlayer/jlayer/1.0.1-gdx). Fits current fat JAR, no new native dependency. | Installed POM declares LGPL 2.1; [libGDX fork](https://github.com/libgdx/jlayer-gdx) is narrowly focused on decoding, using an old 1.0.1 lineage. The current gdx backend still depends on it; that is not proof of active upstream codec maintenance. | Viable desktop analysis adapter, but needs LGPL source/notice/replacement obligations reviewed for distribution. No extra dependency or decoder code added here. |
| Existing `org.jcraft:jorbis:0.0.17` and backend `OggInputStream` | Pure Java OGG Vorbis → PCM; already on desktop classpath/fat JAR, [Maven Central](https://central.sonatype.com/artifact/org.jcraft/jorbis/0.0.17). `OggInputStream` provides channels/sample rate and PCM reads. | [JCraft](https://www.jcraft.com/jorbis/) lists 0.0.17 and LGPL for decoder; bundled example **player is GPL**, so it must not be imported. Old release/toolchain; retained by current gdx backend, not evidence of active upstream releases. [Stream wrapper](https://github.com/libgdx/libgdx/blob/1.14.2/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/audio/OggInputStream.java) has a BSD-style Slick notice in addition to decoder obligations. | Viable without adding natives, but the adapter belongs in lwjgl3, not core or Main Menu renderer. |
| Existing LWJGL STB binding | `lwjgl-stb:3.3.3` already comes with this gdx backend/native classifiers; STBVorbis supplies OGG PCM, not the MP3 half. | [LWJGL licence](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md) is BSD-style; [STB licence](https://github.com/nothings/stb/blob/master/LICENSE) offers MIT or public domain. Maintenance is through LWJGL/STB; another codec is still needed for MP3. | Possible OGG alternative, but does not solve both formats alone. |

The bounded follow-up for true analysis is a desktop adapter that decodes incrementally on a cancellable worker into mono PCM, calculates Hann-windowed FFT/RMS at sample-index timestamps (e.g. 2048 samples, 50ms hops), and stores a bounded/cached amplitude timeline. Query by the **Music playback position**, not decode elapsed time; account for sample rate, channel mix, codec delay and looping/reset. Test MP3 VBR/OGG, failures, cancellation, seeks/loop boundaries and drift against audible playback. Serve fallback while unavailable. Avoid render-thread decoding, unbounded whole-song PCM storage, shell decoders and added GPL executables. This larger codec/worker/synchronisation and distribution work is deliberately separate from this menu redesign; this commit does not claim true audio loudness/frequency response.

## Responsive and verification

The existing virtual-coordinate `UiLayout` is unchanged. Centre/radius respond to both width and height; target wedge width reserves margins even with elastic hover overshoot. Both labels stay beyond the visible logo circle. Text uses the existing smooth font. Retina uses the same logical geometry at doubled framebuffer resolution.

```sh
./gradlew :core:test --tests dev.osujava.ui.MainMenuTest
./gradlew :lwjgl3:mainMenuVisualHarness -PmainMenuOutput=/tmp/osujava-main-menu-lazer
./gradlew build
```

Harness: **228 captures**, each rendered twice with exact framebuffer byte comparison, and **60 production Screen interaction checks**. Sizes: 1024×768, 1280×720, 1920×1080, 600×800, 1280×720@2x and 600×800@2x, each with/without artwork. Scenes cover CLOSED idle/beat zero/impact/between beats/low/high amplitude/active visualiser/press; OPENING 0/100/200/190(midpoint)/380(complete) ms; OPEN idle/Play hover/Exit hover/Logo hover; CLOSING 150(midpoint)/300(complete) ms. The harness uses fixed 120 BPM, fixed frequency bins/max amplitude and fixed playback positions; it never reads user Library data. Generated PNGs/fixtures stay under `/tmp`, outside Git. Retina capture verifies rendering/geometry, not native monitor DPI switching.

Tests cover timing-point selection/BPM change/missing audio/phase, deterministic fallback identity, 200 bins/index step/50ms cadence/peak hold/decay/dead zone/round offset/kiai, explicit state transitions/reversal, keyboard and pointer navigation/Exit/Escape/duplicate suppression, animated wedge/circle hitboxes, independent amplitude/pulse/hover/press scales, and stream start/fade/stop/dispose/restart/failure handling. Screen harness also checks release outside the logo, opening/closing via Logo/Escape, and keyboard shortcuts from CLOSED.

Real playback smoke:

```sh
./gradlew :lwjgl3:mainMenuAudioSmoke -PmainMenuAudio=/tmp/osujava-main-menu-audio
./gradlew :lwjgl3:executableJar
```

Provide locally generated two-second `tone.mp3`, `tone.ogg` (Vorbis) and `tone.wav` files in that directory. The smoke harness checks actual desktop stream opening/playback-position advancement, 200ms volume fade, repeated cleanup and fresh-stream restart twice for each format. The checked fixtures were generated with a local ffmpeg installation solely for testing; ffmpeg is not a runtime dependency and is not bundled. Core lifecycle tests additionally reject every access to a disposed Music instance and exercise decoder/playback failures. All three real playback format checks passed on macOS. The test does not claim sample-accurate drift verification or true spectrum analysis.
