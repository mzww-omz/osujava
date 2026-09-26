# osu!standard legacy animation parity

2026-09-26時点のlazer master `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` と照合。ローカルcheckoutと `git ls-remote origin refs/heads/master` の一致を確認した。

参照元は [ppy/osu](https://github.com/ppy/osu/tree/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1) の以下のファイル。

- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableHitCircle.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyMainCirclePiece.cs`
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSlider.cs`
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSliderBall.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySliderBall.cs`
- `osu.Game.Rulesets.Osu/Skinning/FollowCircle.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyFollowCircle.cs`
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSliderRepeat.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyReverseArrow.cs`
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSliderTick.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs`
- `osu.Game/Skinning/LegacySkinDecoder.cs`, `SkinConfiguration.cs`
- `osu.Game.Rulesets.Osu/Objects/Drawables/DrawableOsuJudgement.cs`, `SkinnableLighting.cs`

Easingは [osu-framework DefaultEasingFunction](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Transforms/DefaultEasingFunction.cs) に従う。`Out` = quadratic、`In` = quadratic。Tickの `OutElasticHalf` は終点補正項も含む。

| 対象 | 今回の挙動 |
| --- | --- |
| Ball | completion付近の2点を0.1 / Path.Distanceでsample。差分長0.01未満は前の角度を保持。repeatを含む方向、atan2境界の連続角度をimmutable visualへ渡す。位置・既存progress・pathは変更しない。legacy textureはSlider終了時に即消去。 |
| Follow開始 | 1→2 / min(180, remaining) / Out、alpha 0→1 / min(60, remaining) / linear。tracking判定の2.4倍は維持。 |
| Follow tick/repeat成功 | 現在scale >= 2の場合、即2.2→2 / 200ms / linear。 |
| Follow release / break | releaseはanimationなし。nested tick/repeat/tail missで4へ拡大・fade / 100ms / linear。releaseだけではbreak扱いにしない。 |
| Follow終了 | tail成功で実Slider終端にeventを予約。1.6へ / 200ms / Out、fade / 200ms / In。Slider親の240ms fadeも適用。 |
| Circle / Slider head | 元object内のbaseとoverlayを1→1.4 / 240ms / Out、fade / 240ms / linear。missは100ms / linear。 |
| Number | Version > 1はscale維持・60ms fade、それ以外はCircleと同じ。LegacySkinDecoder由来の省略時1.0、latest=2.7を最小限解析。 |
| Reverse Arrow | 現在snaked endpointとpath内側への方向。300ms idle loop 1.3→1、古いVersionはlinearと±5.625°の揺れ、新しいVersionはOut。hitは1→1.4 / min(300, span) / Out、fadeはhit Out・miss linear。`reversearrow@2x.png` → normalを既存resolverで解決。既存object-local arrow layerを維持。 |
| Tick | timing生成は変更なし。150ms fade-in、0.5→1 / 600ms / OutElasticHalf。hitは現在scaleの1.5倍へ / 150ms / Out、hit/missとも150ms fade / OutQuint。静的`sliderscorepoint`も解決。 |

Follow eventsとnested judgement時刻はGameplayがimmutable snapshotに記録する。Rendererの観測時刻でpress/releaseを推定せず、frame deltaの積算も行わない。hit済Circleをそのobjectの描画順に残してanimationさせる。`OsuRenderPlan`とそのlayer構造は変更していない。

独自の半透明circle、40ms flash、色付き拡大Judgement ringを削除。Judgement textは維持。lazerの任意の`lighting` textureは加算合成で、600ms scale / 200ms fade-in / 200ms hold / 1000ms fade-outを持つ別機能であり、この独自effectとは一致しない。

## 残る差異

- Slider Bodyの継ぎ目overdraw、gradient、shadow/border/inner colour、bodyの既存150ms fadeやsnaking-outは今回対象外。
- Reverse Arrowの曲がったpathでのframe deltaによる50ms OutQuint方向平滑化は移植していない。現在visible curveの方向を絶対時刻で決める。snaking-outも今回対象外。
- animated `sliderfollowcircle` / `sliderscorepoint` のframe列は未対応。今回は静的textureとtransformを扱う。
- Ballの`sliderb-nd` / `sliderb-spec`、skin設定のtint、lighting texture、Judgement texture/animation、kiai flash、default/Argon固有animationは未対応。
- vector fallbackの形状は既存の近似。円形Ballにrotation描画は追加していない。

## 検証

GPU非依存のangle、Follow event、Circle/number、Arrow、Tickの計算と、Gameplay snapshot・texture resolver・object-local layeringの回帰テストを追加。既存テストも実行し、Gradle buildを確認した。

/tmpの固定clock OpenGL harnessで同一legacy Skin（Version 2.7）を使用し、Circle hit前/直後/+120/+240ms、short/long/fast/slow Slider、snaking、repeat前後、tracking開始、tick直後、break、終了の34枚を取得・画像確認した。harnessと画像はcommitしない。lazer実行画面との同時画像比較は未実施で、lazerとの照合は上記sourceによる。
