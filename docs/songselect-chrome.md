# Song Select chrome integration

Scope: chrome rendering and bottom action geometry only. Song Browser, Score Browser and Gameplay models are unchanged. Cookie is retained with its existing cropped bottom/right edge position and interaction policy.

## Investigation and reference

The shared `SkinAssetResolver` already resolves current → configured fallback → bundled, trying @2x before 1x within each provider. `SongSelectSkinAssets` decodes each candidate and advances on decode failure. No new resolver or density policy was required. A valid transparent image wins too.

The previous geometry used a 960-high canvas for chrome, giving Greylooks only 111.75 top / 67.5 bottom units at 720p. This made its 149 / 90 SD artwork undersized relative to the 84-unit toolbar. Back and Random independently fitted their images to different slot widths; Random could therefore become taller. Import had a separate Y/height and strong coloured panel. The earlier Greylooks harness only exercised the bundled provider, so it did not prove current skin selection.

Reference rechecked on 2026-09-27:

- [Official Interface skinning specification](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection): top-left top, bottom-left bottom, full screen width stretch for bottom, rightmost top pixels repeating below the initial image. Selection buttons use bottom-left origins for v2+; v1 uses top-left at a fixed distance from the bottom.
- [Official forum: Looking for a Skin](https://osu.ppy.sh/community/forums/topics/1954524): moderator describes a 2732×1536 @2x top to cover the window and repeats underneath the initial artwork. This agrees with the shipped 1366-wide SD chrome's 768-high canvas.

The chrome mapping is `UiLayout.height / 768`, after shared resolver density normalization. Greylooks renders a 139.6875-unit top and 84.375-unit bottom at the 720-unit UI baseline. Physical viewport scale supplies 1080p/Retina resolution separately. There is no header/footer rectangle height clamp. Top retains aspect ratio at the top-left; bottom preserves logical height at the bottom-left and stretches horizontally. The top repeats a 20-SD-pixel right-edge strip behind the original and clips the final tile to the viewport. The wiki does not specify an exact repeat start/strip width; this implementation starts at the final source strip, varying with viewport dimensions, rather than claiming a byte-exact stable internal formula.

Greylooks' stripe artwork comes from the actual PNGs, including their curved top boundary and white bottom border. The screen's procedural chrome is a flat translucent fallback. It is entered only when all resolver/load candidates fail; it cannot run over a loaded image.

## Bottom model

`SongSelectChrome.Bottom` owns bottom skin bounds, common visible control baseline/height, intentional overlap, all three action slots and Cookie bounds. Renderer and action hit-testing consume these bounds. A narrow Back/Random slot constrains the shared height, so neither action can protrude above the other. Import uses the same slot height/baseline with quiet helper text. Local Library is muted status text; F6 is small debug text.

The existing substantial-alpha bounds scan is also performed once at load for Back, Random and its hover image. The common height/baseline refers to the visible body, so transparent padding cannot make Random taller than Back. Full-image bounds remain the safe default for fully transparent or sparse artwork. Drawing preserves aspect ratio and transparent padding/shadow; the image can intentionally extend below its visible baseline. Hover artwork also keeps its own aspect ratio instead of stretching to the normal image's rectangle. Cookie remains layered over bottom artwork.

## Verification

`./gradlew :core:test build :lwjgl3:songSelectVisualHarness -PsongSelectPhase=chrome -PsongSelectOutput=/tmp/osujava-songselect-chrome`

The focused harness adds eight cases at 720p, 1080p and 720p with a 2x framebuffer (24 captures): current Greylooks, current hover, top/bottom missing, configured fallback, bundled fallback, current normal-only, transparent current chrome, and opaque sentinel current chrome. Greylooks is selected as a current local directory using the checked-in original skin files, with a conflicting configured fallback present. The JavaExec working directory is explicitly the repository root so that this real skin directory resolves consistently.

For each case the harness asserts the branch actually used by the production renderer. Current Greylooks must resolve both @2x paths with `fallback=false`. Configured and bundled cases must use their respective providers. Current 1x and transparent 1px images must beat fallback @2x. The sentinel case checks magenta/cyan source pixels in the production framebuffer to prove that fallback drawing did not cover a present asset.

Example runtime evidence (same at all three profiles):

```text
songselect-top loaded: .../skins/default/songselect-top@2x.png provider=current density=2 logical=1366.0x149.0
songselect-bottom loaded: .../skins/default/songselect-bottom@2x.png provider=current density=2 logical=1366.0x90.0
CHROME PASS phasechrome-current TOP procedural=false
CHROME PASS phasechrome-current BOTTOM procedural=false
CHROME PASS phasechrome-missing TOP procedural=true
CHROME PASS phasechrome-missing BOTTOM procedural=true
```

Unit regressions cover top aspect/origin/no height clamp, bottom width-only stretch, density-equivalent geometry, common action baseline/height, differing transparent padding, intentional overlap, hit bounds, and top/bottom provider/load/decode priority through procedural exhaustion. Visual review covers Greylooks normal/hover at all three profiles, configured/bundled fallback and missing chrome; all keep Cookie and show no Random/Import protrusion. Captures/logs are temporary artifacts outside Git.

Final verification: all 581 unit tests passed with zero failures/errors/skips; Gradle build succeeded. The focused harness passed 24 scenes, and the full existing harness plus new chrome cases passed 450 scenes with navigation/disposal checks (`/tmp/osujava-songselect-chrome-full.log`). Final image review covered 720p/1080p/2x current Greylooks, hover, configured/bundled fallback, missing and transparent chrome. Intentional corrupt-fixture decode warnings in the full harness exercise fallback recovery.


## Follow-up: custom composite artwork and content overlap (2026-09-28)

The previous change corrected the artwork size but retained the old fixed ranking title and carousel bounds. The ranking title panel started inside Greylooks' curved bottom edge; carousel rows could similarly enter the top chrome. Larger native bottom assets also extended into the fixed browser viewport.

`SongSelectTopCoverage` now scans visible alpha once at texture load, retaining separate column depths rather than reserving the entire transparent image canvas. At viewport changes the chrome model calculates separate left/right top insets, including the top's repeated edge. Rankings start below the left boundary; the carousel clips below the right boundary. Their lower boundary respects native bottom height. Score drawing, score hit-testing/capacity, carousel drawing/hit-testing and wheel arbitration all consume these same presentation bounds. Search/Group/Sort retain their header positions. Browser identities, sorting, scores and Gameplay behavior are unchanged. The computed geometry is cached until a viewport change.

Custom archive inspection found that Seoul v9 and the local WhiteCat skin contain no native `songselect-top`/`songselect-bottom`. Their large `selection-mode` canvases carry composite decoration (Seoul 1150×540 SD, WhiteCat 1143×930 SD). Native chrome lookup correctly reaches bundled Greylooks for these missing files; silently changing resolver priority would not supply custom artwork. The existing SongSelect asset holder loaded `selection-mode` but never rendered it. This prevented those custom decorations from appearing. This legacy technique is also described in the [official skinning forum](https://osu.ppy.sh/community/forums/topics/2070261).

Full-canvas rendering of these composite images was prototyped and visually rejected: WhiteCat's baked Search/Mods/Random/Options/profile graphics duplicate the local app's controls/status and leave a second header boundary over the fallback. No such generic paste-through is included. Composite `selection-mode` chrome remains unsupported pending identification of the user's actual skin and a scoped presentation policy for its baked controls. Native top/bottom resolution retains the requested current → configured fallback → bundled order; missing native assets legitimately resolve to bundled Greylooks. This follow-up does not claim to complete arbitrary legacy composite skin reproduction.

WhiteCat also supplies `menu-back-0@2x` rather than a static `menu-back`. The shared resolver now exposes provider-local animation frame-zero lookup for static UI consumers; SongSelect uses it for Back. Current frame zero is preferred to fallback static art, with the same @2x→1x and decode-recovery semantics. Back animation playback itself is unchanged (the first frame is displayed).

The harness's current Greylooks cases now run through public production `SongSelectScreen.show()` and the app's skin-directory/fallback-directory accessors, rather than injecting loaded textures. `-PsongSelectCustomSkin=<directory or .osk>` adds production-path custom skin captures at 720p/1080p/2x; OSKs are imported safely into the temporary output directory. It asserts that any provided native top/bottom file wins, and that missing files use fallback providers. A tall native chrome case tests content separation. No custom assets or generated captures are committed.

Commands:

```sh
./gradlew :core:test build :lwjgl3:executableJar
./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectPhase=chrome -PsongSelectCustomSkin="/path/to/custom.osk" -PsongSelectOutput=/tmp/osujava-chrome-legacy
```

Follow-up verification: 585 unit tests passed with no failures, errors or skips. Gradle `build` and `:lwjgl3:executableJar` succeeded. The final full harness passed 456 captures plus navigation/disposal checks, including the local WhiteCat production-path case (`/tmp/osujava-chrome-final.log`). Image review covered current Greylooks and WhiteCat at 720p/1080p/2x, tall chrome, configured fallback, all-assets-missing procedural fallback and opaque current sentinel artwork. Greylooks current top/bottom logs report `provider=current density=2` and the renderer reports `procedural=false`. Rankings and rows stay below the visible top boundary, with rows clipped above native bottom artwork. WhiteCat uses its current animated Back's first frame and bundled native top/bottom because those two files are absent; its composite custom chrome is still unsupported as explained above. The user's specific skin has not yet been identified, so its reported custom appearance has not been reproduced or claimed fixed.
