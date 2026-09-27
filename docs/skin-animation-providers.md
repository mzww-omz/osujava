# Local animation provider resolution

Verified against the local osu!lazer checkout at
`20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` and current upstream sources on 2026-09-27.

`LegacySkinExtensions.GetTextures()` selects the first provider whose
`GetTexture(frame0)` or `GetTexture(static)` succeeds. All contiguous animation
frames, or the static image when frame zero is unavailable, come from that
provider. The separator is `-` for judgement images and empty for `sliderb`.
A custom static image therefore takes priority over a fallback animation, and
missing frames never get filled by another provider.

`GetTexture()` tests successful loading, not just file existence:

- Framework `TextureLoaderStore.Get()` catches image decoding errors and returns
  null; `TextureStore` also returns null for that upload.
- `LegacySkin.GetTexture()` tries `@2x` first, then the normal image in the same
  provider if loading fails.
- Broken custom frame zero with no usable custom static image allows fallback
  selection. A usable custom static image still wins when custom frame zero is
  broken.
- After a provider is selected, a missing or broken later frame terminates the
  animation and retains the preceding frames. It does not switch providers or
  replace the animation with its static image.

In osujava, the public file-only resolver uses presence checks. The runtime
animation overload receives a loadability predicate from `OsuSkinAssets`, using
its existing successful/failed texture cache. This avoids decoding twice and
preserves texture ownership. Returned `AssetFile.fallback()` identifies the
selected provider for every frame. Existing single-image diagnostics remain
unchanged.

Single-image and particle lookups retain custom-to-fallback file lookup.
Transparent custom PNGs remain valid assets. Font lookup also remains per glyph:
`LegacySpriteText.LegacyGlyphStore` calls `skin.GetTexture()` for each glyph, and
`SkinProvidingContainer.GetTexture()` traverses providers on each such call.
This applies to HUD and HitCircle fonts; animation selection does not lock an
entire font to one provider. Existing font completeness/load-failure handling is
outside this change.

Sources:

- [LegacySkinExtensions.GetTextures](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkinExtensions.cs)
- [LegacySkin.GetTexture](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkin.cs)
- [Framework TextureLoaderStore](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Textures/TextureLoaderStore.cs)
- [Framework TextureStore](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Textures/TextureStore.cs)
- [LegacySpriteText](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySpriteText.cs)
- [SkinProvidingContainer](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/SkinProvidingContainer.cs)

`AnimationProviderTest` checks provider selection across all four judgement
images and slider ball, gaps, density, load failures, transparency, particles,
single images, font glyph fallback, caching, and disposal. Existing asset tests
now expect a contiguous usable prefix after a broken later frame, matching
lazer rather than dropping the entire animation.
