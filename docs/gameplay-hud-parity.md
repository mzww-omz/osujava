# osu!standard legacy Gameplay HUD parity

2026-09-26確認。lazer checkoutと `git ls-remote origin refs/heads/master` が一致した:
[`20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1`](https://github.com/ppy/osu/tree/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1)。

直接参照した `osu.Game/` のソース:

- `Screens/Play/HUDOverlay.cs`, `Skinning/LegacySkin.cs`
- `Skinning/LegacyScoreCounter.cs`, `LegacyAccuracyCounter.cs`, `LegacyDefaultComboCounter.cs`
- `Skinning/LegacyHealthDisplay.cs`, `LegacySongProgress.cs`, `LegacySkinExtensions.cs`, `LegacySpriteText.cs`
- `Screens/Play/HUD/GameplayScoreCounter.cs`, `GameplayAccuracyCounter.cs`, `SongProgress.cs`
- `Graphics/UserInterface/ScoreCounter.cs`, `PercentageCounter.cs`, `RollingCounter.cs`, `Utils/FormatUtils.cs`
- `Skinning/SkinProvidingContainer.cs`, `DefaultLegacySkin.cs`, `LegacyTextureLoaderStore.cs`

Font layout / easing / marginは [osu-framework](https://github.com/ppy/osu-framework) の `Text/TextBuilder.cs`,
`TextBuilderGlyph.cs`, `TexturedCharacterGlyph.cs`, `Graphics/Drawable.cs`, `Utils/Interpolation.cs` と照合。
`OsuGame.ScalingContainerTargetDrawSize` と `DrawSizePreservingFillContainer` の1024×768 / Minimumに合わせ、
HUDのunitは `min(windowWidth/1024, windowHeight/768)`。Playfield viewportやHitObject layerは変更しない。

| HUD | 配置と表示 |
| --- | --- |
| Score | TopRight / Origin TopRight、Scale 0.96、Horizontal Margin 10。osujavaの非standardised scoreはclassic表示の最低8桁を使用（計算方式をclassicへ変更するものではない）。1000ms quadratic Outで現在の表示整数からrolling、.NETと同じ偶数丸め。 |
| Accuracy | TopRight / Origin TopRight、Scale 0.6×0.96、Vertical Margin 9 / Horizontal 17。Scoreの描画下端をY基準にする。`100.00%` / `99.12%`。小数4桁でfloorしたfractionをpercent表示し、375ms OutQuadでrolling。PercentageCounterはproportional rollingを有効にしていない。 |
| Combo | BottomLeft / Origin BottomLeft、Margin 10 / Scale 1.28。可変幅の`<combo>x`。初期0は非表示。child origin Y=`0.625×height+9`、position Y=`-0.375×height+9`、大popのorigin X=3。 |
| Combo increment | 新値の大popはadditive、scale 1.56→1 / alpha 0.6→0 / 300ms linear。表示countは160ms遅延し、小pop 1→1.1 / 50ms In、1.1→1 / 50ms Out。連続incrementは古い遅延を無効化し前のbound値を表示する。 |
| Combo change/reset | 非incrementの非0変更は新値へ即時変更。resetは表示countから0まで差×20ms linearでrollingし、0に到達して100ms fade。integer丸めを使い、描画フレームに依存しない最終半stepからfadeする。resetは残っている大popを消さない。 |
| Song progress | Accuracyの左。Right=`windowWidth - accuracyDrawWidth - 18×unit`、Y=Accuracy描画中央。外径33、白border 2、中央dot 4、fill直径33×0.92。introは反転した残量 / RGB(199,255,47) alpha153、通常は白alpha153。実際の最初/最後のHitObject時刻を使用し、SliderTiming終端とSpinner終端を含む。音声durationや終了猶予を使わない。空譜面では非表示。登場時500ms OutQuint fadeもGameplay clockで評価。 |

DefaultSkinComponentsContainerのrelative配置はLoadComplete後に一度だけ適用される。
AccuracyのYとSong progressのX/Yは初期`00000000` / `100.00%`から固定し、99.xx%でprogressを追従移動させない。
Comboのcustom origin/positionも初期`0x`のheightを保持する。

MarginはDrawableのoriginに含まれ、component scaleでも拡縮される。
例えば1024×768時のScore右余白は10×0.96、Accuracy上余白は9×0.576、Combo余白は10×1.28。

## Skin font

- `[Fonts] ScorePrefix` / `ComboPrefix` は別設定で、ともに既定 `score`。
  `ScoreOverlap` / `ComboOverlap` は別設定で、ともに既定0。HitCircle設定は従来どおり。
- `LegacySpriteText`のlookup名は `<prefix>-0` … `-9`, `-dot`, `-comma`, `-percent`, `-x`。
  任意の別prefixへの自動置換はしない。HUD fontはskin.iniがなくても既定prefixを解決する。
- `@2x.png`を先に解決し、glyph width/heightはpixel size / density。
  Score/Accuracyは`5`のnative幅をdigitの固定advanceにし、各glyphをそのcell中央に置く。
  dot/percent/xは固定幅の対象外。Comboは全glyphが可変幅。縦はnative textureを上揃え。
  glyph間だけoverlapを引き、anchorはtextのadvance boundsから計算する。
- Texture解決は既存OsuSkinAssetsに集約。同じファイルをHitCircle/Score/Comboで共有し、失敗もcache。
  renderごとにロードせず、screen dispose時に各Textureを一度だけdisposeする。
- **fallbackの差**: lazerは同じasset名をSkin provider chainで解決し、LegacyGlyphStore / TextBuilderは
  解決不能なglyphを`?` lookup後に省略する。osujavaには内蔵classic画像packがないため、必要なglyph
  （固定幅の`5`を含む）が欠けたcounter全体を既存BitmapFontへfallbackする。40px cap-heightは
  RollingCounter標準font sizeに合わせた読みやすさのための代替で、classic画像のnative sizeではない。
  Skinなしの字形までlazerと同一とはしない。

## 未対応と旧HUDとの差

独自の上部左右/下部の黒panel、曲名+Difficulty、ACCURACY/COMBOラベルを削除。
noticeは独立したoperational表示、Debug Auto cursorは既存debug表示として維持。
ScoreTrackerのScore/Accuracy/Combo計算は変更せず、sessionの各score event後にHUD animationへ通知し、
immutable LegacyHudVisualだけをRendererへ渡す。表示値からGameplayへ書き戻さない。

HealthProcessor相当のhealth stateがないためHealth barは実装しない。正しいHealth gameplay modelが必要。
調査したlazerは`scorebar-bg`のproviderで`scorebar-marker`の有無を調べてstyleを選択。
`scorebar-colour[-N]`、新style marker、旧style `scorebar-ki/kidanger/kidanger2`、HPによる色/variant、
fill smoothing、marker bulge/flashがある。今回HP logicも追加しない。
osu-frameworkのshader antialiasingは未再現（Song progressはShapeRendererで描画）。
Cursor/Trail、Spinner、follow points、leaderboard、spectator、key overlay、mods、hit error meter、Judgement skinは対象外。

## 検証

`./gradlew :core:test --offline` と `./gradlew build --offline`。
既存HitCircle font testsに加え、Fonts解析、HUD @2x / density、native glyph layout、fixed width、prefix欠損、
Texture共有/破損/重複dispose、Score retarget、Accuracy rolling/floor、Combo increment/rapid increment/reset、
frame頻度非依存、session内の複数同時input、Song progress boundsを検証。

実RendererのOpenGL screenshot harness:

```sh
./gradlew :lwjgl3:hudVisualHarness --offline -PhudOutput=/tmp/osujava-hud-no-skin
./gradlew :lwjgl3:hudVisualHarness --offline -PhudOutput=/tmp/osujava-hud-skin \
  -PhudSkin=/Users/agemizu/Documents/osu/osu/osu.Game.Tests/Resources/special-skin
```

1024×768 logical / Retina 2048×1536 captureで各15枚を目視確認。
score=0 / rolling、100% / 99.xx%、combo=0/1/9/10/123、increment直後・25/160/210/260/500ms、
reset直後・100/200/300ms。lazer test skinの@2x glyphありとSkinなしを確認。
画像は`/tmp`のみでcommitしない。lazer実画面との同一Skin・同一解像度の並列撮影比較は未実施。
