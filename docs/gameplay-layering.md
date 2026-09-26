# osu!standard の描画順

2026-09-26確認。参照したosu!lazer checkoutは `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1`（2026-09-25）。`HitObjectContainer` comparatorとFrameworkのcomposite traversalはGitHub masterでも確認した。

## 根本原因とdepth

従来の種類別passは別objectのbase・overlay・numberを交互に描いていた。numberがHUD passに属し、Slider bodyとballもそれぞれ全Slider共通passだったため、HitObjectとしての前後関係を保持できなかった。

[HitObjectContainer.Compare](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game/Rulesets/UI/HitObjectContainer.cs)はStartTime降順。完全に同じStartTimeだけ `CompareReverseChildID` に進む。[Framework CompositeDrawable](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Containers/CompositeDrawable.cs)のこのhelperはDepth降順、ChildID降順。ChildIDはcontainerへの追加時に増加し、draw subtreeはchildrenを先頭から列挙する。よって早いStartTimeが手前、同時刻・同Depthでは先に追加されたobjectが手前。近い時刻をまとめるepsilonはない。異なるStartTime間ではDrawable.DepthよりStartTimeが優先される。

[DrawableRuleset.loadObjects](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game/Rulesets/UI/DrawableRuleset.cs)はBeatmap.HitObjectsの順で追加する。poolingの場合はdrawableがaliveになる時にcontainerへ追加される。[LifetimeEntryManager](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Performance/LifetimeEntryManager.cs)は同LifetimeStartをentry追加IDで安定化する。lazerのtie-breakの直接のキーはbeatmap indexではなくDrawableのChildIDであり、seek・Lifetime変更・再追加まで常にbeatmap indexそのものだと解釈してはいけない。

osujavaの通常プレイではpreemptが全object共通、visual identityは再利用しないため、元のbeatmap indexをimmutableに保持して同時刻の追加順を表す。session開始時にStartTime降順・index降順を確定し、snapshot作成時にvisible objectだけをその順で選ぶ。Rendererはsortしない。互換constructorによる手作りsnapshotのみmodel側で順序を構築する（index未指定の旧constructorは-1）。

## playfield layer（奥から手前）

`OsuRenderPlan`はGPUを使わないcommand model。`GameplayRenderer`はそのcommand順に描く。

1. Spinner proxy：Spinner本体・ring。lazerはSpinner全体をHitObjectContainerから[OsuPlayfield](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/UI/OsuPlayfield.cs)のspinnerProxiesへproxyする。
2. Judgement below：既存の判定body/afterimage、判定text、Spinner status text。
3. HitObject：HitCircle / Sliderの全local pieces。Slider body専用の全object共通background passは設けない。
4. Judgement above：既存の判定ring。lazerのabove-hitobjects proxy layerに対応する独立したforeground。
5. Approach proxy：CircleとSlider headのApproach Circleのみ。HitObject内部には埋め込まない。
6. Gameplay overlay / HUD：debug cursor、HUD panelsとscore等。

lazerのJudgementはlighting/bodyが本体より下、skinが明示的にproxyするforegroundが本体より上で、そのさらに上にApproach Circleが来る。osujavaの判定ring/textの見た目は従来の簡易表示を維持し、この責務分離に割り当てた。legacy lightingのadditive表現やhit animation自体の再現は今回行っていない。

Spinner / Approach proxyは普通のcontainer追加順（通常プレイでは時刻昇順・同時刻index昇順）で描く。本体containerのreverse child orderをproxyへ誤って適用しない。

## object-local pieces

HitCircleはbase → number → overlay。[LegacyMainCirclePiece](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyMainCirclePiece.cs)の `HitCircleOverlayAboveNumber` 既定trueに対応する。設定0ならbase → overlay → number。`[General]`で正規名とtypo `HitCircleOverlayAboveNumer` を読み、正規名を優先する。別objectはこれらの間に割り込めない。

[DrawableSlider](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSlider.cs)に合わせ、Sliderは次の順：

- body
- tail base + overlay（tail proxyに対応。ticks/repeatsの下）
- ticks
- repeat circles（各base + overlayを連続描画）
- head base
- reverse arrows（Slider内部OverlayElementContainer）
- head number + overlay（設定0ではoverlay + number）
- follow circle → ball

[LegacySliderHeadHitCircle](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySliderHeadHitCircle.cs)はOverlayLayerをSlider内部へ `Depth=float.MinValue` でproxyし、[LegacyReverseArrow](https://github.com/ppy/osu/blob/20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyReverseArrow.cs)は同containerの通常depthへproxyする。headのforegroundはarrowより上だがballより下。これらはplayfield全体のoverlayへは出ない。Sliderのnested objectは親SliderのStartTimeで他objectとのdepthに参加し、tail/repeat自身の時刻では再sortしない。

## blendingと描画backend

通常のSRC_ALPHA / ONE_MINUS_SRC_ALPHAを維持。textureとvectorは既存の有無判定でどちらかを描き、Skin成功後に同じpieceのfallbackを追加しない。専用slider circleのbaseがありoverlayがない場合はoverlayを空として扱う。Skin textureの半透明部分から背後が見えるのは正常な合成で、object compositeを不透明なoffscreen bitmapへ変換するものではない。

ShapeRenderer.end() / SpriteBatch.end()で先行描画をflushするため、backend切替自体はdraw orderを変更しない。texture・number共通の `sprites()` helperに切替とblend復帰をまとめた。SpriteBatch.end()でblendが無効になるため、shape再開前に再設定する。

Slider bodyのvector pathは複数rect/capで構成され、半透明時に継ぎ目のoverdrawが残る。これはSkinとfallbackの二重描画ではなく既存path描画の性質。body gradient/mesh調整のparity passで別途扱う。

## 検証

GPUなしの回帰テスト：同位置circle、異なる時刻、完全同時刻、1ms差、型をまたぐ同時刻tie、Slider head/tail/repeat位置のcircle（両方の時刻前後関係）、local command連続性、number/overlay設定0、Spinner/Approach/Judgement layer、visible objectが変わっても元indexが維持されること。

実機確認には一時的なLWJGL3 harnessで実際のGameplayRendererを固定GameClock snapshotに対して描画。stacked circle、fast stream、head上・tail上・repeat位置の次circle、ballとcircleの重なりを使用。半透明PNGと中央を横切るoverlayを持つ検証Skinで既定設定・設定0、およびvector fallbackの計18画面を出力して代表ケースを目視確認。画像・Skin・harnessは `/tmp/osujava-layer-check` の生成物としてcommit対象外。

同じSkin・譜面・時刻でのosu!lazer実機画像比較は未実施。参照元との一致はsourceによるlayer/order確認とosujava実描画確認の範囲。Ball rotation、Follow Circle animation、hit animation、reverse arrow見た目、Slider body gradientは今回変更していない。DrawableSliderBallのfollow→ball順と回転処理、LegacyMainCirclePieceのVersion依存number fade、LegacyReverseArrowの回転/scale差が次のparity passの確認対象。

検証結果：描画順回帰8 tests、SkinConfiguration 6 testsを含む全175 testsが成功（failure/error/skip 0）。`./gradlew build`、実機harness用 `lwjgl3:executableJar` が成功。
