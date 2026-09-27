# Song Select Phase 2.5 — public reference and independent implementation

Research date: 2026-09-27. Target baseline: `36a9bd0`; read-only McOsu baseline: `db2add20`. Priority: stable public visuals, then McOsu implementation concepts, then osujava constraints. No stable executable, disassembly, private code or reverse engineering was used. McOsu code, constants and equations are not transferred.

## Public sources

- [Client / Interface](https://osu.ppy.sh/wiki/en/Client/Interface): selected metadata, Group/Sort and tabs, typing search, rankings and score rows, carousel colours, toolbox, user panel and Cookie activation.
- [Official full-screen illustration](https://osu.ppy.sh/wiki/images/Client/Interface/img/song-selection.jpg): directly inspected; annotated wiki example, not a claim that every current skin has identical geometry.
- [Skinning / Interface](https://osu.ppy.sh/wiki/en/Skinning/Interface): menu-button-background is multiplicatively tinted, bottom-left anchored; recommended minimum SD 690×85. Thumbnail support starts at 2.2, with reference 115×85 and left image inset 9. selection-mode SD reference 92×87 (1.0) / 92×90 (2.0+); mods/random/options 77×87 / 77×90, including -over variants. Legacy origins are top-left at a bottom offset; newer origins bottom-left. menu-back uses bottom-left and can animate. Bottom stretches horizontally; top uses top-left and repeats its right edge underneath the original. star is centred, tinted; older partial stars crop, newer ones scale. star2 is decorative rather than the rating glyph.
- [skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini): 2.0 introduced @2x and selection anchor changes; 2.2 introduced optional thumbnails and changed partial stars; SongSelectActiveText/InactiveText remain explicit skin colours.
- [Keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts): arrows/wheel browse; Enter activates; F1 Mods, F2 Random, Shift+F2 previous Random, F3 options; typed search. osujava retains its existing arrow/page semantics, Space play and F6 Debug Auto. Missing commands are deferred.

## Observations and measurement method

Measure the official 1280×720 illustration using viewport/body ratios, excluding shadows from bodies. Approximate visual ranges, not prescribed constants: collapsed left edges around 0.61–0.64 viewport width; expanded bodies around 0.55–0.57; collapsed pitch roughly one body; difficulty pitch roughly one body; thumbnail near one body high; bottom around 0.11–0.12 viewport height; Cookie diameter around 0.25–0.28 height, partly outside right/bottom, overlapping toolbox and carousel. Curve is a vertically stretched shallow bend. The image does not establish stable's internal animation equations or exact hitboxes.

Metadata occupies top-left with title/difficulty, mapper, duration/BPM/object counts and difficulty settings; status icon precedes it. Browser controls are top-right with selectors above tabs. Search is a type-to-search layer at the browser top, not an ordinary permanently boxed form input. Rankings selector sits below metadata; individual score rows and empty-state notices occupy their own areas without a full-height solid panel. Cookie remains a prominent play control. Hover/pulse intensity cannot be measured from a still; retain modest existing animation rather than claiming exact stable timing.

## McOsu cross-file survey

Inspected `src/App/Osu/` in the local clone:

- `OsuSongBrowser2`: separate left/right top containers, browser/search, score view, bottom navigation and user panel; score view has background/frame disabled. Its additional grouping, recalculation and UI controls are implementation-specific.
- `OsuUISongBrowserButton`: image rectangle and actual body rectangle are separate; hit testing uses actual geometry; visible-only animation work. Velocity push is configurable and is not evidence of stable default behaviour.
- `OsuUISongBrowserSongButton` / `SongDifficultyButton`: parent hides on expansion, children share group displacement; normal title, Artist // Mapper subtitle, dim sibling song metadata, bold difficulty and stars below it; grade precedes text. Thumbnail cover/clipping/fade is version-gated.
- `OsuUISongBrowserInfoLabel` / `ScoreButton`: independent metadata and score content responsibilities, skin-backed score rows; dynamic rating/pp is out of scope.
- `OsuUISelectionButton` / `BackButton` / `SongBrowserUserButton`: independent navigation elements, normal/over artwork, Back anchoring and a separate user region.
- `OsuUISearchOverlay`: search drawn against browser coordinates, query/status layer instead of a generic textbox.
- `OsuSkin` / `OsuSkinImage`: user/default candidate resolution, density-aware logical size and version; animation frames are separate from size metadata.
- `OsuBackgroundImageHandler`: asynchronous delayed requests, eviction and caching. Keep osujava's existing bounded cache/fade here; async decoding is a remaining gap.

## Three-way comparison and implementation plan (before edits)

| Element | osu!stable public visual | McOsu concept | osujava baseline | Direction / provenance |
| --- | --- | --- | --- | --- |
| Top metadata | left, title/difficulty/mapper/counts/settings/status | independent label | four compressed full-header lines | taller left metadata, honest local status; stable/common |
| Group / Sort | top-right selectors and tabs | separate right container | LOCAL SETS · TITLE label | reserve separate selector/tab geometry; no fake controls; stable |
| Search | typing layer at browser top | search overlay | permanent small boxed field | wider borderless query layer; keep input, add typing start; common |
| Rankings | selector then individual rows/notices | unfilled score view | full-height dark 32% panel | compact local heading/notice; stable/common |
| Carousel start | right half under controls | separate viewport | under uniform header | right viewport independent of metadata; common |
| Row width | extends past right edge | image can overflow body | viewport-proportional PNG width | retain overflow, explicitly distinguish image/body; common |
| Row image bounds | shadow overlaps | separate outer rectangle | equals body/hitbox | detect substantial alpha once at load; osujava implementation |
| Visible row body | contiguous | actual bounds | transparent margins count as spacing | body-based sizing/hit testing; common |
| Row pitch | close to body height | independent layout | image height + gap | body-based collapsed/child pitches; common |
| Curve | shallow tall bend | animated/configurable | deep bounded arch | independently chosen shallower excursion; stable |
| Expanded group X | all children left of collapsed | parent/child offsets | selected alone left | whole child group left, tiny selected emphasis; common |
| Selected row X | slight within-group displacement | selectable style | large selected emphasis | selection chiefly colour/type; stable |
| Thumbnail | near body height, optional/version gate | version gate, cover clip/fade | always, inset image | default version >=2.2; legacy A/B fixture; common |
| Title | main, regular type | regular, dim siblings | always bold | regular, strongly dim siblings; common |
| Artist / Mapper | Artist // Mapper | same | Artist / Mapper | // and dim sibling byline; common |
| Difficulty | prominent child line | bold | small final line | strongest child line; common |
| Stars | below difficulty, readable silhouettes | partial/background band | tiny right accessory + number | lower band, bounded high ratings, version partial style; common |
| Colours | white selected, blue sibling, pink unplayed, orange played | state-specific | white/blue/pink | keep state palette, do not invent played history; stable |
| Bottom chrome | substantial toolbox | independent bar | 38-unit toolbar | viewport-relative substantial navigation region; stable/common |
| Back | independent left navigation | independent asset | narrow toolbar cell | anchored standalone artwork/fallback; common |
| Mode | separate selection asset | normal/over | asset resolver only | preserve support/reserve future space; common |
| Mods | separate selection asset | normal/over | resolver only | preserve support, no false working button; common |
| Random | selection asset + F2 | normal/over | squeezed asset cell | bottom-anchored asset and hover; common |
| Options | separate selection asset | normal/over | resolver only | reserve future navigation; common |
| User area | separate profile | separate local stats | local set count | distinct honest local-library region; osujava |
| Cookie | right-bottom play control | no equivalent established in surveyed browser | small heavily clipped circle | keep, enlarge and integrate with toolbox; stable |

Independent ratios will be tested; reference pixels will not be asserted. Keep spring, viewport/velocity, selection/expansion/reveal, compositing order, Unicode/ellipsis, resolver chain, @2x, thumbnail cache/fade, optional supplied ratings, visual and unit infrastructure. Implement geometry first, composition second, then chrome/Cookie; run all motion scenes and the 1000-Set probe.

## Verification and remaining gaps

### Implemented geometry and composition

`SongSelectBodyBounds` scans substantial alpha once when the resolved row asset loads. Sparse/fully transparent art uses the full-image fallback. The image is drawn around the body, preserving its padding/shadow; the immutable draw snapshot/hitbox is the body. Greylooks' detected body is about 0.726 of image height and 0.966 of width, identically at @2x. The viewport-scaled image-derived body is bounded to 76–88 UI units. Collapsed pitch/body is 0.96; adjacent children within a Set use 1.02. These are osujava choices, not sampled equations. Pitch also drives padded content limits and stable logical centers; selection/hover neighbour separation is smaller.

Curve excursion from center to viewport edge is 0.0325 viewport width (formerly 0.075). All children ease toward a group displacement of 0.052 viewport width; selected emphasis is only a small additive offset. Group emphasis survives expansion/collapse interruption and child selection alongside the existing spring/reveal state. Velocity X deformation is bounded to five UI units instead of eighteen. Spring rate/integration, wheel semantics and selected compositing priority remain.

Collapsed rows contain regular Title and Artist // Mapper only. Children use regular Title, byline, bold Difficulty and a separate star band. Sibling title/byline alpha multipliers are 0.24/0.20 while Difficulty/stars retain readable inactive skin text. Text fit remains Unicode-aware and bounded by the visible screen edge. Grade space can be inserted between thumbnail and text without changing artwork geometry; no grade is invented.

Supplied ratings use nine readable silhouette slots with full/partial foreground and a secondary exact label; >=9 saturates the band while retaining the actual numeric/scientific label. Absent/invalid ratings show nothing. Legacy partial icons crop; 2.2+ shrink. This bounded nine-slot policy differs from unrestricted stable/McOsu high-rating presentation. Production still has no supplied ratings/calculator.

Thumbnail A (default) respects version >=2.2; B exists only through a package-local visual-harness preview seam. Greylooks is not named in production policy. Thumbnail height/body is 0.93, cover-cropped in fixed bounds, with the existing cache and fade. Skin configuration remains one resolved configuration, without merging keys across providers. Current → fallback → bundled and @2x priority remain unchanged.

### Implemented chrome and interaction

Left metadata extends lower than right browser controls. Group/Sort describe the existing fixed Set/title organisation as text, with space for future selectors/tabs; they are not clickable fabricated controls. Search is a borderless browser-top prompt, with a subtle query background when active, and typing can start it. Existing explicit focus, filtering, Enter/Escape and navigation remain. The local ranking heading and compact empty notice replace the full-height panel. The user region honestly identifies Local Library rather than inventing profile scores.

Toolbox height/viewport is 84/720 (about 11.7%). Back is an independent left artwork/fallback, Import remains a working osujava action, Random keeps its normal/over assets, and the shared resolver already supports all Mode/Mods/Options variants. Future controls have open navigation space rather than fake functional buttons. Top uses its logical aspect and top-left anchor plus a repeated right edge underneath; bottom stretches horizontally at a logical height from its bottom-left origin. Arbitrary oversized assets are bounded/cropped. Public documentation does not specify stable's exact repetition start, so the independently chosen osujava skin-canvas scaling/one-column extension is an approximation.

Cookie diameter/viewport height is 0.27. Its center leaves right and bottom edge overlap, and artwork extends above the toolbox into the browser. Hover/pulse are modest radius-proportional changes. Click in its toolbox region plays; upper overlap still passes row input through, preserving Phase 1 behaviour. Enter/Space, selected difficulty re-click and expansion double-click guard remain. Mouse-wheel arbitration still reserves the browser above the enlarged toolbox. Full-circle stable Cookie interaction and exact music-synchronised pulse remain differences.

### Verification

Captures and downloaded references stay outside Git. Final measured results are recorded below.

### Remaining differences

- Group/Sort selectors/tabs, score rows/ranking selector, played history/orange state, Grade, Mode/Mods/Options and previous-Random history remain deferred as requested. The local ranking notice does not imply an implemented score store.
- Local status cannot claim ranked/submitted status; metadata Length uses the model's first/last object span, not a newly calculated slider-tail/audio duration. Online rankings and web actions are absent.
- Cookie uses existing osu!java artwork, toolbox-only click bounds and a 60 BPM UI pulse. Stable arrow/page mappings and drag/absolute scrolling are not adopted; existing osujava navigation and Import shortcut remain.
- Nine-slot extreme ratings, system font coverage, skin top-edge repetition and arbitrary transparent decorative body inference are independent approximations, not exact stable internals.
- Thumbnail decoding remains synchronous on the first uncached load, unlike McOsu's async delayed background handler. Benchmarks measure warmed CPU submission with shared artwork, not cold decoding/GPU completion or a cross-hardware guarantee.


### Final verification results

`./gradlew build :lwjgl3:songSelectVisualHarness -PsongSelectOutput=/tmp/osujava-songselect-phase25-release --console=plain` succeeded. **466 tests**, zero failures/errors/skips (453 baseline + 13 new cases). **252 full scenes and 300 transition captures**: all 234 original motion frames, 12 thumbnail fade frames and 54 additional reference-state frames. Profiles: 1280×720, 1920×1080 and 1280×720 at a 2560×1440 framebuffer. Motion/bounds/navigation/disposal assertions passed, including spring settling, selection snap, expansion, hover suppression, hit priority, first/last item, wheel and filtered Random. Newly added typing-start/Enter behaviour has a unit regression test.

Manual rendered review covered the official stable illustration against Greylooks A/B, modern and procedural fallback, normal/hover/no-motion selected/siblings/collapsed rows, long/Unicode labels, absent/3.x/5.x/7.x/high ratings, missing/wide/tall thumbnails, full/top/left/bottom chrome and edge-overlapping Cookie. Expansion/reversal transition sheets and 1080p/2x full captures were also reviewed. Broken-fixture decode logs are intentional; they do not cause a test failure. Captures remain under `/tmp/osujava-songselect-phase25-release`.

1000 Sets produce 1003 logical rows. Warmed production render CPU submission, 180 measured frames after 60 warmup frames (shared artwork):

| Profile | Greylooks legacy A, mean / max ms | Modern thumbnail, no rating, mean / max ms | Modern supplied rating fixture, mean / max ms |
| --- | --- | --- | --- |
| 1280x720-1x | 0.168 / 0.846 | 0.215 / 0.730 | 0.237 / 0.902 |
| 1920x1080-1x | 0.163 / 0.759 | 0.220 / 0.818 | 0.232 / 0.606 |
| 1280x720-2x | 0.177 / 0.964 | 0.204 / 0.662 | 0.226 / 0.527 |

Carousel motion advance averaged 0.007 ms (600 measured samples), with maximum 0.053 ms over all final profiles. Normal modern render means 0.204–0.220 ms are comparable to or below the Phase 2 0.218–0.229 ms baseline; rated fixtures add the visible star band and measure 0.226–0.237 ms. Earlier runs showed scheduling/driver variance; these are local warmed submission measurements, not strict real-time ceilings.

To avoid a density-induced cost increase, each skin-backed row now submits body, thumbnail placeholder/cover and text within one SpriteBatch section. The one-pixel fill texture is created once and disposed with the screen; no geometry scan, rating formatting or metadata string allocation is added to idle drawing. McOsu remained at `db2add20` with a clean working tree.

Implementation commit: `a4b0ebe` — `Align Song Select geometry and composition with stable references`.
