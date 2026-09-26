# osu!standard legacy Slider Body

## 確認したsource（2026-09-26）

ローカルosu!lazer checkout: `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1`。

- [LegacySliderBody / ColourAt](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySliderBody.cs)
- [DrawableSliderPath](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Default/DrawableSliderPath.cs)
- [PlaySliderBody](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Default/PlaySliderBody.cs)
- [OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)
- [LegacyUtils.InterpolateNonLinear](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game/Utils/LegacyUtils.cs)

osu!framework公式masterの確認時点: `71509833c97c50555b170e15339b3372605e8aa2`。lazer csprojのframework dependencyは `2026.921.0`。

- [SmoothPath](https://github.com/ppy/osu-framework/blob/71509833c97c50555b170e15339b3372605e8aa2/osu.Framework/Graphics/Lines/SmoothPath.cs)
- [Path.DrawNode](https://github.com/ppy/osu-framework/blob/71509833c97c50555b170e15339b3372605e8aa2/osu.Framework/Graphics/Lines/Path.DrawNode.cs)
- [PathPrepass shader](https://github.com/ppy/osu-framework/blob/71509833c97c50555b170e15339b3372605e8aa2/osu.Framework/Resources/Shaders/sh_PathPrepass.fs)
- [Path colour shader](https://github.com/ppy/osu-framework/blob/71509833c97c50555b170e15339b3372605e8aa2/osu.Framework/Resources/Shaders/sh_Path.fs)
- [Color4Extensions.Darken](https://github.com/ppy/osu-framework/blob/71509833c97c50555b170e15339b3372605e8aa2/osu.Framework/Extensions/Color4Extensions/Color4Extensions.cs)

## Legacy colour profile

`p = 1 - distanceToCentreline / radius`（外縁0、中心1）。radiusは既存の `SliderVisual.radius()` をそのまま使用。lazerでも `PathRadius = OBJECT_RADIUS * Scale` であり、shadowを加えるためradiusをさらに拡大する処理はない。

|範囲|RGBA|
|---|---|
|`0 <= p <= 5/64`|transparent black → black alpha `0.25`|
|`5/64 < p <= 0.1875`|SliderBorder、既定white alpha `1`|
|`0.1875 < p <= 1`|outer → inner、alpha `0.7`|

legacy circle radiusは `64 - 5 = 59`。shadowとborderの境界は中心から `59/64 * radius`、borderとtrackの境界は `0.8125 * radius`。

Trackは `SliderTrackOverride` があればそのRGB、なければHitObjectのaccent/combo RGB。source alphaによらず `0.7`。outer RGBは `track / 1.1`（frameworkの `Darken(0.1)`）、inner RGBは `min(1, track * 1.125 + 0.25)`（legacy独自lighten）。

`LegacyUtils.InterpolateNonLinear` はencoded sRGB成分を直接補間する。linear-light空間への変換、gamma補正や独自easingは加えない。libGDXの通常RGB値もencoded sRGBとして同じ式を適用する。shadowにも同じ補間を使用。さらにSmoothPathに合わせ、外縁alphaに `min(p / 0.02, 1)` を乗算する。

`SkinConfiguration.Colours` だけを小さく追加し、`[Colours]` の `SliderBorder` / `SliderTrackOverride` を解析。0〜255の整数RGB3成分以外はwhite border / accent trackへfallbackする。既存Fonts・General・Versionの解析と既存constructorは維持する。

## 継ぎ目を作らないgeometry / rendering

旧rendererはborder・combo・innerを3回描き、それぞれのpathも半透明rectLineとcircleに分割していた。segment/capが重なるpixelは繰り返しblendされ、継ぎ目が濃くなっていた。

新rendererは以下の構成。

1. `SliderBodyGeometry` に既存pathのsegment端点、cumulative distance、boundsをcache。連続する完全同一点だけを省略する。曲線のsampling・path geometry・ゲーム判定は変更しない。
2. 各segmentにつき2 trianglesのstatic meshをGPUへ送る。shaderが既存radiusからcapsule boundsを作り、fragmentで有限線分への距離を計算する。
3. Slider単体の共有RGBA8888+depth framebufferで、`gl_FragDepth = distance / radius` と `GL_LESS` により最小距離のfragmentだけを残す。距離を解決するこのpassではblendせず、完成画像を通常の `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` で一度だけ合成する。Track alphaやobject fade alphaを強制的に変更せず、premultiplied alphaにも変更しない。
4. capは線分の終点への円距離、joinは隣接capsuleのunionとなる。円polygonやmiterを作らないため、急角度や往復でもcapとbodyの間にalpha二重描画、crack、miter spikeが発生しない。自己交差も最小距離で解決する。
5. `OsuRenderPlan.SLIDER_BODY` の位置でShapeRendererをflushし、Bodyを合成してShapeRendererへ戻る。親Sliderのdepthを維持する。別Slider同士の重なりは通常のobject compositingを維持する。

frameworkもquad内で有限線分への距離を計算し、R16Float framebufferのMAX blendで `1 - distance/radius` のunionを作る。こちらのdepth最小値方式は同じ距離unionを選ぶ。

## Snaking / cache / lifecycle

既存 `sliderSnakeProgress()` のvisual timing（`startTime - preempt` から `preempt/3`）を使用。累積距離からbinary searchで可視prefixのvertex数を決め、最後のsegmentをshaderで途中まで縮める。その終点も同じround cap式を使用する。progress 0はBodyなし、0.5は全長の半分、1は全path。毎frame meshをtessellateしない。

`SliderRenderData` のpath-list identity keyと5秒未使用pruneを維持。cacheはradius・colour・viewportに依存せず、これらをuniformで更新する。meshはpruneとGameplayScreen.disposeで解放。共有framebufferはviewportサイズ変更時のみ再生成し、clear・合成はcache済みboundsにscissor/cropする。HiDPIではprojectionからframebuffer pixel単位へ変換。GPU距離passは可視Bodyごとに再描画される。大きなCPU texture生成・readbackは通常描画で行わない。

## Fallback / 残る差

旧Body rendererは削除し、新旧同時描画はない。通常のdesktop OpenGL fragment depthとdepth attachmentを使用し、shader作成失敗はログ付きの例外。旧primitive描画へのfallbackは設けていない。GLESへ移植する場合は `GL_EXT_frag_depth` とfragment highpが必要。

lazerのR16Float距離buffer・小さな1D colour texture・texture filteringに対して、今回はdepth bufferの距離選択とanalytic colour shader、RGBA8888中間bufferを使用する。profileの値・計算は同じだが、境界のsampling、quantisation、edge AAのpixel値は完全一致を保証しない。frameworkのsegment merge / cap bounds縮小最適化も未移植で、重複領域のGPU計算自体は残る。最終画像のalpha overdrawはない。

lazer実行画面との同一Skin・譜面・時刻の直接画像比較は未実施。今回のlazer照合は上記sourceによる。HUD、Spinner、Cursor、follow points、lighting、animation assets、hitsoundsは対象外。

## 検証

全206 unit tests成功（failure/error/skip 0）、`./gradlew build` 成功。

GPU非依存の追加テストはstraight、直角、acute/backtracking、Bezier、very short、repeated point、progress 0/0.5/1、round cap距離、finite vertices/endpoints、colour profileのshadow/border/outer/inner、source alpha固定、sRGB補間、INI解析と既存設定の同時解析を検証。

`tools/SliderBodyCheck.java` は実際のOpenGLを使う再実行可能なfixed-clock harness。1024×768でSkinなし・設定なし・Border指定・Track指定、straight/curved/sharp/short/long/combo、AR5のsnaking中間0msと完成990msをcapture。direct BodyとHiDPI 2048×1536を含め59画像を生成。segment分割前後、往復重複、途中snakeと切り詰めた直線、progress 0と空pathの4組を全pixel比較し、全てmax RGB差分0。`GL_NO_ERROR`。代表画像とcontact sheetを目視確認し、segment境界・capの濃い継ぎ目がないことを確認。

生成画像・Skinは `/tmp/osujava-slider-body-check` に出力し、commitしない。

```sh
./gradlew core:test build lwjgl3:executableJar
mkdir -p /tmp/osujava-slider-body-check
javac -d /tmp/osujava-slider-body-check -cp lwjgl3/build/libs/osujava-0.1.0-all.jar tools/SliderBodyCheck.java
java --enable-native-access=ALL-UNNAMED -cp lwjgl3/build/libs/osujava-0.1.0-all.jar:/tmp/osujava-slider-body-check SliderBodyCheck
```

macOS harnessは `useGlfwAsync()` を使用。既存layering固定clock harnessの18ケース（stacked/stream/head/tail/repeat/ball × skin設定2種/vector）でもbackend切替とobject compositingを再確認。
