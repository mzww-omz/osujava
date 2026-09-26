# Gameplay Cursor / Trail parity

## Source of truth (2026-09-26)

ローカル osu!lazer checkout `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` (2026-09-25) を直接確認した。

- `osu.Game.Rulesets.Osu/UI/Cursor/{OsuCursorContainer,OsuCursor,CursorTrail}.cs`
- `osu.Game.Rulesets.Osu/Skinning/Legacy/{LegacyCursor,LegacyCursorTrail,OsuLegacySkinTransformer}.cs`
- `osu.Game.Rulesets.Osu/Skinning/{NonPlayfieldSprite,OsuSkinConfiguration}.cs`
- `osu.Game.Rulesets.Osu/UI/OsuPlayfieldAdjustmentContainer.cs`
- `osu.Game/Skinning/LegacySkin.cs` (`STABLE_MAGIC_SCALE_FACTOR=1.6`)
- osu-framework [Texture](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Textures/Texture.cs),
  [DefaultEasingFunction](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Transforms/DefaultEasingFunction.cs),
  [InputResampler](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Input/InputResampler.cs)
- osu-resources [sh_CursorTrail.vs](https://github.com/ppy/osu-resources/blob/master/osu.Game.Resources/Shaders/sh_CursorTrail.vs)

上記7つのCursor/Skinクラスが基準。generic trailのfade exponentをlegacyへ適用しない。

## Asset / Cursor

| 項目 | 実装 |
| --- | --- |
| assets | `cursor`, `cursormiddle`, `cursortrail`をそれぞれ`@2x.png`→`.png`。既存OsuSkinAssetsがcache・共有・一度だけdisposeする。破損したpieceは独立してfallbackする。 |
| native size | `LegacyCursor.Size=50`はコンテナの大きさ。spriteにはSize/RelativeSizeAxes指定がなく、50へstretchしない。Texture.DisplaySizeはpixel size / ScaleAdjust。NonPlayfieldSpriteとLegacyCursorTrailはScaleAdjustに1.6を掛けるため、osujavaではpixel/density/1.6を現在のplayfield viewportで描画する。 |
| CursorCentre | default true。cursorとmiddleのoriginはtrue=Centre、false=TopLeft。GLのY-upへ変換してもanchorはpointer位置。 |
| CursorRotate | default true。cursor.pngだけclockwise、360度/10000ms。absolute GameClock時刻のmodulo。0/2500/5000/10000ms=0/90/180/0度。 |
| CursorExpand | default true。各pressは1→1.3 / 100ms quadratic Out。全action release時、現在scale→1 / 100ms quadratic Out。無効時は常に1。 |
| cursormiddle | cursor.pngのExpandTargetと別child。上に重ねるが回転・expandなし。native sizeも独立。cursorがない場合は孤立middleを使わずvector cursor。 |
| cursor size settings | Gameplay Cursor Size / Auto Cursor Sizeは現プロジェクトにない。内部user scale=1 / mod scale=1、CircleSizeによる自動scaleなし。新Settings UIなし。 |

lazerのplayfieldは画面の0.8倍の領域で、1024×768の基準画面ではplayfield scale=1.6となる。
osujavaの既存viewportは512×384を画面へfitするため同画面でscale=2。
今回viewportを変更せず、NonPlayfieldSpriteの**playfield内でのサイズ**を合わせる。
従って同じwindow dimensionsに対する絶対pixel sizeはlazerより1.25倍になる既存playfield差を含む。
ユーザーCursor Size、texture density、expandのscaleを混同しない。

## Input / layer / OS cursor

- manualはGameplayInputProcessorがSessionへ送った変換済みpointer座標をそのまま使用。
  Screen show/resize時にも現在の実mouse位置を同じ入力経路へ流す。
- AutoはDebugAutoPlayerからSessionへ送る位置を使用。入力前の初期位置だけAutoのgetterを読む。
  既存Auto path、Score、判定、HitObject、Slider event、Spinner progressを変更しない。
- OsuGameplaySession.pointerState()が既存cursorX/YとpressedActionsのread-only snapshotを返す。
  CursorTrackingSessionは全入力を元Sessionへ転送した**後**にvisualへ通知。
  visualに別のkey/button集合を作らず、Z/X/左右mouseの既存releaseIfIdleが維持する状態を読む。
  pressの瞬間も観測するので入力が一frame内で完了しても取りこぼさない。
- Debug Autoの旧debug円/十字をGameplayRendererから削除。manual/Autoとも同じGameplayCursorRenderer。
- HitObjects/Judgement/HUD/noticeの後で独立Cursor layerを描画。OsuRenderPlanのqueue/orderには追加しない。
  Screenのentrance fade overlayはその後に重なる。Gameplayにmenu overlayは現在存在しない。
- OS cursorはlibGDX newCursor/setCursorの透明16×16 Pixmapで隠す。pointer capture/warpはしない。
  showで適用、hideまたはactive disposeでSystemCursor.Arrowへ戻す。Pixmapは作成直後dispose。
  native Cursorはscreen dispose時にdispose。hide済みscreenのdisposeで次Screenのcursorを上書きしない。

## Trail

| 項目 | Disjoint | Connected |
| --- | --- | --- |
| 選択 | cursor providerがない、またはcursor providerにmiddleがない | cursorとmiddleが両方ロード成功 |
| movement | 補間なし、最後に受けたpointer位置 | InputResamplerでraw/HD入力は維持、integer入力はcornerへ減らし、距離補間 |
| part間隔 | 1000/60ms | Texture.DisplayWidth×CursorScale.X/2.5×IntervalMultiplier。内部user scale=1なのでlogical width/1.6/2.5 |
| IntervalMultiplier | legacy override `1/max(GameplayCursorSize,1)`、現在は1 | 同左 |
| fade | 150ms、exponent=1 | 500ms、exponent=1 |
| blend | 通常SRC_ALPHA / ONE_MINUS_SRC_ALPHA | additive SRC_ALPHA / ONE |
| origin | CursorCentreへ従う | 常にCentre |
| cursor近傍 | 除外なし | endpoint手前1 intervalを除外し、d < distance-intervalで生成 |

- 単一Skinなのでprovider chainはない。`cursorあり/middleなし`はdisjointになる。
  `cursorなし/middleだけあり`でもcursor providerがないのでdisjoint。
- CursorTrailRotateのdefaultはtrue。**rotationは生成時の値を保存しない**。
  lazer TrailDrawNode.ApplyStateと同じく、全既存partを現在のcursor角度で描画する。falseは0度。
- NewPartScaleは生成時のCurrentExpandedScaleを各partへ保存。過去partは後からexpandしない。
- shaderの`pow(clamp(part.Time-fadeClock,0,1), FadeExponent)`はlegacy exponent=1の場合
  `clamp(1-age/FadeDuration,0,1)`と同じ。SpriteBatch vertex alphaで再現し、shader frameworkは追加しない。
- 最大2048part（lazerのmax_spritesと同じ）。期限切れは先頭からpruneし、上限で古いpartを落とす。
  極端な大移動でも最新2048partだけ計算。Disjointの長い時間jumpでもfade内のslotだけを計算する。
- SpriteBatchのcolorとblend functionを描画後復帰し、Textureをrenderごとに作らない。

## Fallback / clock / 残差

- cursor欠損/破損はvector fallback。OsuCursor.DefaultCursorのSize=28、白ring、半透明inner ring、
  blue centre dotを参考にし、ringのみexpand、dotは別piece。Glow/Shadow/fragment antialiasingは省略。
  fallback animationもLegacyCursorの1.3倍/100msを使う。lazer DefaultCursorの基底SkinnableCursorは
  1.2倍/400ms OutElasticHalf、contractは400ms OutQuadであり、この点は代替visualの差として残す。
- OsuLegacySkinTransformerはcursortrail画像がなければnullを返し、lazer側SkinnableDrawableは
  DefaultCursorTrail (`Cursor/cursortrail`内蔵画像、generic 300ms/exponent=1.7)へfallbackする。
  osujavaにはこのbuilt-in image packがないため**trail欠損時はtrailを省略**する。
  Skin cursor / vector cursorどちらとの組合せもCursor layerを維持し、OS cursorにfallbackしない。
- lazer CursorTrailは専用FramedClock（実時間）を持ち、Cursor spinはload開始からのdrawable clock。
  osujavaはrotation/expand/fade/emissionすべてGameClock。停止中はanimationも止まり、spinのphaseは
  gameplay時刻0が基準になる。後方seekでTrail/input animation履歴をclear。再現には入力historyを再供給する。
- lazer disjointはframe Updateで間隔以上の時だけ1part追加し、遅いframeでは間引かれる。
  osujavaは同じ1000/60msをinteger slotで処理し、event/render頻度に依存しない密度を優先する。
  新しい位置のeventまでのslotは最後の既知位置で生成し、disjointへ位置補間は加えない。
- Auto位置は既存absolute slider/spinner pathを読む。renderer側でAutoを進めたり未来位置を推定しない。
  5ms/33ms/16msの入力samplingで共通1600msのSlider位置一致をassert、captureで曲線を目視確認。
  Trailは実際に届くnode間の直線補間なので低sampling時の細かなcurve/fade/part数差は残る。
  Autoのtarget切替時刻も既存frame updateに依存する。今回はGameplay logicを変更しない。
- Auto circleの既存click→releaseは同じ時刻で即時に完了するため、expandの保持時間は追加しない。
- lazerを起動した同一Skin/同一windowの並列capture比較は未実施。
- Cursor Ripple / particles、Spinner renderer、Health、follow points、hit error meter、key overlay、
  leaderboard、hitsoundsは変更しない。

## 検証

```sh
./gradlew :core:test --offline
./gradlew :lwjgl3:cursorVisualHarness --offline -PcursorOutput=/tmp/osujava-cursor
./gradlew build
```

テスト: Cursor3assetの@2x優先/density/cache/identity共有/dispose/欠損/破損、4設定/default/malformed、
0/2500/5000/10000ms回転、press50/100ms/release50/100ms、途中release、重複press、Z/X/左右mouse重複、
無効flags、middle transform分離、native size、60Hz生成、150/500ms線形fade、connected間隔/補間/clear gap、
resampler、live trail rotation、birth scale、2048上限/大移動/長時間、seek、fallback、Auto入力転送時の
Score不変・position一致、OS cursor show/hide/dispose/次Screen保護。

実GameplayRenderer + GameplayCursorRendererで29枚のPNGを出力。
各sceneでclock/input historyを最初から2回再生しframebuffer byte一致をassert。
Skin static、0/2500/5000/10000ms回転、CursorCentre=0、CursorExpand=0、press前/直後/+50/+100ms、
release+50/+100ms、middleあり/なし、connected straight/curve、disjoint movement、fast/slow、
TrailRotate=0/1、@2x（1xには故意に異なる赤画像）、Skinなし、cursorのみ、trailのみ、Debug Autoと30/60Hz。
1024×768 logical / Retina 2048×1536 capture。主要sceneとAuto sampling比較を目視確認した。
画像/fixtureは`/tmp`だけに置きcommitしない。結果は全pixel replay checks成功、258 core tests（failure/error=0）と`./gradlew build`成功。
