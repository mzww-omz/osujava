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
