# Third-party assets

## Greylooks 1.4

- Skin: **Greylooks**, original author **iZaIxSP** (`Author` in the original `skin.ini`).
- Distribution/source: [author's osu! forum release](https://osu.ppy.sh/community/forums/topics/1776224), `Greylooks [1.4].osk`.
- License: **Creative Commons Attribution 4.0 International (CC BY 4.0)**, as specified by the author and requested for this integration.
- License reference: https://creativecommons.org/licenses/by/4.0/ and [legal code](https://creativecommons.org/licenses/by/4.0/legalcode).
- Local supplied archive: `/Users/agemizu/Downloads/Greylooks [1.4].osk`. This is provenance only; runtime has no dependency on this file or any osu! installation.
- Bundled location: `core/src/main/resources/skins/default/`, also included in the core and executable JARs. A copy of this credit accompanies the assets as `NOTICE.md`.
- Included modes: osu!standard, osu!taiko and osu!catch. Common cursor, bitmap number fonts, images, sound banks and `skin.ini` are included. Taiko/catch images are retained for future mode renderers; osujava currently only plays standard.
- At the user's follow-up request, other inspected images/audio (menu, song select, ranking, pause, editor/Mods, HUD, effects, empty sound placeholders) and the `Extras/` alternative variants are also retained for future support. They are not newly applied to those screens. Alternatives are not automatically selected.
- Excluded: `mode-mania.png` and `mode-mania-small.png` (mania is outside the requested scope). The original `[Mania] //todo` section in `skin.ini` is preserved verbatim and ignored.
- Asset changes: **none**. 501 of 503 original archive files are copied byte-for-byte. No resize, crop, format conversion, filename change, recolouring, or other processing. File SHA-256 checksums, categories, current use and retention reasons appear in `docs/greylooks-assets.tsv`.
- Configuration additions in code (`Combo1–8`, `AllowSliderBallTint`) affect rendering; they do not modify source files.
- No separate TTF/OTF fonts or original license/credit document were present in the supplied OSK. The original author's credit in `skin.ini` is preserved. `default-*` and `score-*` are bitmap glyphs.

Existing code-source acknowledgements and the ppy MIT license in `docs/licenses/ppy-MIT.txt` remain unchanged.
