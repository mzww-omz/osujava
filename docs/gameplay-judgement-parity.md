# osu!standard legacy Judgement parity

2026-09-26のlazer master `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` を直接参照。
ローカルcheckoutのHEADと `git ls-remote https://github.com/ppy/osu.git refs/heads/master` の一致を今回確認した。

参照元（[固定commit](https://github.com/ppy/osu/tree/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1)）:

- `osu.Game/Skinning/LegacySkin.cs`: component選択、result mapping、particle、density。
- `osu.Game/Skinning/LegacySkinExtensions.cs`: `GetAnimation` / `GetTextures` / frame duration。
- `osu.Game/Skinning/LegacyJudgementPieceOld.cs`, `LegacyJudgementPieceNew.cs`: 全transformとproxy。
- `osu.Game/Graphics/ParticleExplosion.cs`: particle生成・position・alpha。
- `osu.Game/Rulesets/Judgements/DrawableJudgement.cs`: animation開始時刻・lifetime・proxy。
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableOsuJudgement.cs`: object位置・scale。
- `osu.Game.Rulesets.Osu/Objects/OsuHitObject.cs`: `OBJECT_RADIUS = 64`。
- `osu.Game.Rulesets.Osu/UI/OsuPlayfield.cs`: below / HitObjects / above / approach。
- `osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs`, `LegacyJudgementPieceSliderTickHit.cs`: tail専用画像とVersion制約。
- [osu-framework Animation](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Animations/Animation.cs): non-looping末尾保持。
- [DefaultEasingFunction](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Transforms/DefaultEasingFunction.cs): `In` = quadratic、`Out` = quadratic、無指定 = linear。

## Assetとstyle

| osujava event | lazer result | main | particle |
| --- | --- | --- | --- |
| 通常 `HIT300` | Great | `hit300` | `particle300` |
| 通常 `HIT100` | Ok | `hit100` | `particle100` |
| 通常 `HIT50` | Meh | `hit50` | `particle50` |
| 通常 `MISS` | Miss | `hit0` | なし |
| 明示的 `SLIDER_TAIL` miss | IgnoreMiss | `sliderendmiss` | なし |
| 明示的 `SLIDER_TAIL` success | SliderTailHit | Version < 2のみ `sliderpoint10`（static） | なし |

通常resultはusable main画像が存在し、対応するusable particle画像もある場合だけnew style。
particleがない・壊れている場合はold style。Missはparticle切替しない。
main画像が欠ける・壊れる場合は、そのresultのJudgementは表示しない。2026-09-26の追加指示に従い、従来のBitmapFont判定表示を削除した。
存在する別resultのSkin画像は引き続き表示する。font Judgementの描画経路自体を削除し、Skinなし・partial Skin・破損PNG・nested eventでも従来表示を出さない。

`SLIDER_TAIL`は既にGameplayが位置・時刻・結果をemitするため、そのeventだけを専用mappingへ接続する。
headの通常結果は通常mappingのまま。Rendererから追加の結果を生成しない。

## Animation解決

`hit300-0.png`からゼロ始まりの連番を読み、最初の欠番で終了。frame 0が存在すればanimationをstaticより優先する。
frame 0がなければstaticへfallbackし、frame 1以降だけをanimationにしない。
1frameだけならlazerのSpriteと同様にstatic transformsを使う。2frame以上だけがtransform省略対象。

全frameとparticleは既存`SkinAssetResolver.resolve()`の `@2x.png` → `.png` 優先順を共用し、frameごとにdensityを持つ。
logical native size = texture pixel size / density。その後 `radius / 64` とviewport scaleを掛ける。
画像pixelを直接playfield長として使わず、画像の縦横比・paddingを維持する。
Missのposition offsetもJudgement親の `radius / 64` を通す。

Judgementは `GetAnimation(name, true, false)` なのでframe lengthは `1000 / 60` ms固定。
`applyConfigFrameRate`はfalseで、`AnimationFramerate`やframe数による変更はない。
frame = floor(age / frame length)、0～末尾へclamp。loopしない。終了後は最後のframeをfade lifetimeまで保持する。

壊れたframe列はresult全体を非表示にする（欠損frameを飛ばさない）。
これは破損入力の安全方針であり、lazerのTextureStoreがnullを返して連番探索を止める処理を厳密に模倣するものではない。
Skin source stackやdefault skinへのresource探索は既存osujavaにないため追加していない。

## Old style

全result: alpha 0→1 / 120ms / linear、500msから1→0 / 600ms / linear、通常終端1100ms。

static通常hit:

| 時刻 | scale | easing |
| --- | --- | --- |
| 0ms | 0.6 | 即時 |
| 0～96ms | 0.6→1.1 | linear |
| 96～120ms | 1.1 | hold |
| 120～144ms | 1.1→0.9 | linear |
| 144ms | 0.95 | sourceの補正による即時set |
| 144～168ms | 0.95→1 | linear |
| 168ms以降 | 1 | hold |

static通常Miss:

- scale 1.6→1 / 100ms / In。
- Version > 1.0のみY=-5から80下方向offset / 1100ms / In（最終Y=75）。Version 1.0は移動なし。既存parserの省略時1.0 / latest=2.7を利用。
- target rotationを[-8.6°, 8.6°)で固定。0→target / 120ms / linear、その後target→target×2 / 980ms / In。
- `JudgementVisual.effectSeed`はevent生成時に確定するimmutable値。描画側がそのseedから同一targetを得る。
  score・判定logicへ乱数を入れない。rotationそのものはsource通り時刻で補間するが、targetはframeごとに変わらない。

2frame以上のold styleはfadeのみ。hit scale、Miss scale・move・rotationを全て省略する。

static `sliderendmiss`は1.2→1 / 100ms / In、fade開始250ms・長さ600ms。
通常Missのmove・rotationは使わない。animatedなら通常old animatedと同じfadeのみ。
Version < 2の`sliderpoint10`はY=-10まで300ms / Out、その後60ms fade。専用Spriteなのでhit popは使わない。

## New styleとparticle

親fadeはoldと同じ120 / 500 / 600ms。
mainはstaticの場合0.9→1.05 / 1100ms / linear。animated mainはscale 1でframe再生する。

temporary old styleは同じmain frame列を独立に使い、必ずhit scale transformsを適用（animatedでもforceTransforms）。
最終scaleだけ1.05。alphaは-16msから0→0.5 / 56ms / Out、40msから0.5→0 / 300ms / linear。
実alphaは親fadeを乗算する。additive blendingでabove layerへ描画する。

particleはShapeRendererで代用せず、cache済み`particle50/100/300` PNGを150個描く。
sourceと同じdistributionで各particleのdistance [0,0.5)、duration [1600/3,1600)、direction [0,2π)を一度決定。
seed付きJava Randomで再現性を持たせるため、lazerのglobal RNGと同じ乱数列ではない。

- explosion開始はJudgementより100ms前、寿命1600ms。
- progress = clamp((age+100)/particle duration)。移動はlinear。
- positionはsourceの140×140矩形中央を原点に、distance×progress×140×(sin(direction),cos(direction))。
- particle alpha=(1-progress)×(1-clamp((age+100)/1600))×親fade。
- textureはnative logical size、追加scale変化・rotationなし。親のobject scaleとviewport scaleのみ。
- SRC_ALPHA / ONEでadditive。blendとbatch colourは描画後に復元する。
- Visual event保持時間は最大1500ms（1600-100）へ延長。親fadeが1100msで0になるためそれ以降は見えないが、sourceのlifetimeも覆う。

## Layeringとclock

既存`OsuRenderPlan`の順序を維持し、component種類別passへ戻していない。

| layer | 内容 |
| --- | --- |
| Judgement below | new style particles（本体より奥）とmain、asset不足時は表示なし |
| HitObjects | 既存object-local順序 |
| Judgement above | old style全体、new style temporary old、legacy slider point |
| Approach | 既存approach proxy |

old styleをbelowでも重複描画しない。new style main / particleをaboveへ移動しない。
追加した独自ring / circle explosionはない。任意`lighting`は今回追加していない。

位置は`JudgementVisual`の既存judged object位置を利用。Slider tail専用eventは既存tail位置を利用。
全frame / scale / alpha / move / rotation / particle位置は `GameplayState.currentTimeMs - event.timeMs` から計算し、deltaを積算しない。
particle parametersはimmutable eventをkeyとしてcacheし、消えたeventのcacheは除去する。
同一snapshotをseekで再描画してもseedと絶対時刻から同じpixelになる。

## Texture lifecycle

`OsuSkinAssets`へ統合。起動時にframe列とparticleを読み、既存AssetFile→SkinTexture cacheを共有する。
同じファイルをHUD glyphとJudgementが参照してもloadは一回。partial失敗で共有frameをdisposeしない。
failed lookupもcacheする。成功frame・particleはidentity ownership setでdisposeを一回だけ行い、二度目のdisposeは安全。

## 未対応nested結果と差異

- Tick / repeatは判定状態を持つが、専用Judgement visual eventはない。`slidertickmiss` / `sliderpoint30`の結果表示はemitしていない。
- Slider全体のlazer最終Great/Ok/Meh判定は、現在のhead / nested score構造と異なる。今回scoreや判定計算は変更していない。
- tail successで`sliderpoint10`がない場合（Version >= 2を含む）は表示しない。missing `sliderendmiss`も表示しない。
- randomの範囲・分布・時刻関数を再現し、lazer global RNGのsequence一致は目標にしていない。
- 同一Skin・同一時刻のlazer側GPU capture比較は実施していない。sourceとの照合とosujava固定clock captureで検証した。

## 検証

Unit test: 通常mapping、explicit tail mapping、@2x、static / animated priority・順序・欠番、non-looping末尾、
fadeと全scale境界、Miss Version差 / rotation target固定、animated transforms省略、new main / temporary、
particle範囲・再現性、resultごとのold/new/非表示、破損PNG、HUDとのTexture共有・dispose、Gameplayのvisual lifetime。

`./gradlew :core:test --offline`と `./gradlew build --offline` を実行。241 tests成功、失敗0・error0。Gradle build成功。
固定clock harness:

```
./gradlew :lwjgl3:judgementVisualHarness -PjudgementOutput=/tmp/osujava-judgements
```

17 scenario × 9時刻（0 / 60 / 120 / 144 / 168 / 500 / 800 / 1100 / 1500ms）= 153 PNG。
300/100/50/Miss static、Version1 Miss、animated300/Miss、particle300、animated particle300、@2x、Skinなし、
tail miss/old tail point/Version2 tail非表示、partial300/100、壊れた50をcapture。全captureに非重複例とHitObject重複例を併置する。
各scenarioの1500ms後に60msへseekし、最初の60msとframebuffer全pixelの一致もassertする。17 scenarioすべてで一致を確認した。
Skinなし・missing100・破損50・Version2 tail hitは、Judgement eventがないsnapshotとframebuffer全pixelが一致することもassertする。
fixtureはframe番号入り画像、`AnimationFramerate: 2`を設定して無視されることを確認できる。
生成PNG・Skin fixtureは指定output以下だけに置き、Gitへ含めない。
