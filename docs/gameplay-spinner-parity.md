# Legacy Spinner visual parity

## Source of truth (2026-09-26)

直接確認したローカル osu!lazer checkout: `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` (2026-09-25)。下記7クラスについてmasterのraw sourceも確認した。

- [DrawableSpinner](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSpinner.cs)
- [LegacySpinner](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySpinner.cs)
- [LegacyOldStyleSpinner](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyOldStyleSpinner.cs)
- [LegacyNewStyleSpinner](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyNewStyleSpinner.cs)
- [OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)
- [SpinnerRotationTracker](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Default/SpinnerRotationTracker.cs)
- [SpinnerSpmCalculator](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Default/SpinnerSpmCalculator.cs)

追加確認: `OsuPlayfieldAdjustmentContainer`, `OsuPlayfield`, `Spinner`, `OsuHitObject`, `LegacySkin.GetTexture`, `LegacySpriteText`。
osu-frameworkの[DefaultEasingFunction](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Transforms/DefaultEasingFunction.cs)で`Easing.Out = t*(2-t)`を確認。
[Interpolation.ValueAt(Color4)](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Utils/Interpolation.cs)はlinear RGBで補間後sRGBへ戻す。

## Style / assets / lifetime

`LegacySpinnerAnimation.select(background, top)`は純粋関数。

| Loaded root | Style |
| --- | --- |
| background (topの有無を問わず) | Old |
| topのみ | New |
| どちらもなし | 既存vector fallback |

全12画像は`GetTexture()`によるstatic piece: `spinner-background`, `spinner-circle`, `spinner-metre`, `spinner-approachcircle`, `spinner-glow`, `spinner-bottom`, `spinner-top`, `spinner-middle2`, `spinner-middle`, `spinner-spin`, `spinner-clear`, `spinner-rpm`。
`GetAnimation()`は使われないため、`name-0.png`等は無視する。各画像を既存resolverで`@2x`→normalの順に解決し、pixel/densityのlogical sizeを使う。`.625`とdensityは独立。
既存OsuSkinAssetsが一度ロード・cache・所有・dispose。Score fontはHUDと同じtextureを共有する。
rootが読み込めない場合はfallback。選択後のmissing sub-pieceは描画省略であり、別style・vectorへの置換なし。共通画像だけ存在してrootがない場合もvectorのみ。

Old/New全体のfade-inは`start-TimeFadeIn`からTimeFadeInのlinear fade。preempt全期間のfadeではない。
親DrawableSpinnerのresult後240ms linear fadeを、body・approach・commonへ乗算する。
Skin描画はend+240msで消える。既存Gameplay snapshotのend+320ms retentionとvectorの320ms fadeは維持。
osujavaのjudgement visualの時刻は既存のendTimeであるため、Skin transformsのHitStateUpdateTimeにもendTimeを使う。

## 640×480座標

LegacySpinnerはCentre anchor/origin、Size=(640,480)、Position=(0,-8)。
`SPINNER_TOP_OFFSET=45-16=29`, `SPINNER_Y_CENTRE=29+219=248`, `SPRITE_SCALE=.625`。
TOP_OFFSETをcentreへ再加算しない。containerの-8との合成でcircle centreはwindowのvertical centreになる。

lazer adjustmentは親windowの.8倍を4:3 Fitし、width/512でscaleする。結果として
`unit = min(windowWidth/640, windowHeight/480)`。
`LegacySpinnerCoordinates`はwindowの中央へ640×480をfitし、上方向へ8*unitずらしてY-downからY-upへ変換する。
1024×768でunit=1.6、1920×1080でunit=2.25・左右240pxのmargin。
SpriteBatch rotationは`-SpinnerVisual.rotationDegrees()`で時計回りを維持する。
HiDPIはlogical window dimensionsとprojectionを使い、実framebufferへの倍率はbackendに任せる。
通常HitObject用PlayfieldViewportは変更しない。osujavaの既存playfieldはlazerより広く、Spinnerだけwindow-spaceへ一致させる。

## Old

Backgroundとcircleは(320,248)、scale=.625。Background colourは[Colours] SpinnerBackground、default=(100,100,100)。textureの元alphaを残し、tintとwhole alphaを組み合わせる。
Circleはsnapshot rotationで回転する。

MetreはTopLeft、top margin=29。height基準は692*.625=432.5。
percent=(int)(clamp(progress)*100)、NoBlinkならbars=percent/10。
blink時はpercentを99までclampし、barsへ確率`(percent%10)/10`で1を足す。100%でも9/10段階をblinkする。
containerをfinalHeight-visibleHeight下げ、spriteを同量戻すmaskを、元textureの上部分をcropする描画で再現する。
`SpinnerNoBlink=1`でblink無効、未指定/不正値ではblink有効。

lazerのglobal RNG呼び出し順は再現しない。SplitMix64のfinalizerをabsolute GameClockの整数msとbeatmapIndexへ適用してuniform sampleを得る。
同progressのBernoulli分布・10段階semanticsを保ちつつ、同時刻・同objectのseekや再描画がframe delta/描画回数に依存しない。
10000時刻の25.7% samplingで上段確率50%±2.5%を検証する。NoBlink時は時刻やobject seedに依存しない。

## New

body順はglow → bottom → top → middle2 → fixed middle。glowのみSRC_ALPHA/ONE additiveで、直後に通常blendへ戻す。
Middle2があるとtop=rotation*.5、なければtop=rotation。middle2=full rotation、bottom=top/3、fixed middle=0。
body container scale=.625*(.8+Out(progress)*.2)。approach/commonはこのcontainerのprogress scaleに入らない。
GlowはRGB=(3,151,255)、alpha=clamped progress。明示されたbonus tickでwhiteへflashし、200ms linear RGB補間でblueへ戻る。
Sourceのresult時glow FadeOut(300)はUpdate内の`glow.Alpha=Progress`により継続的に上書きされるため、追加の300ms alphaを乗算せず、親の240ms fadeを適用する。
Fixed middleは開始前white、startからdurationでredへlinear RGB補間。

## Approach / common pieces

Old/New approachは(320,248)、開始までscale=.625*1.86、startからduration中にlinearで.625*.1まで縮小。
HitCircle approach式は使わない。Newのprovider判定は、単一custom Skinがtopを提供する場合に存在するapproachcircleを使用する、という対応にした。default providerを偽装しない。

共通containerはfloat.MinValue depthでstyle body/approachの上。順はbonus → rpm background → SPM → spin → clear。

| Piece | Position / transforms |
| --- | --- |
| spin | centre=(320,29+335)、scale=.625。start-TimeFadeIn/2からTimeFadeIn/2のlinear fade-in。end-min(400,duration)からlinear fade-out。明示されたfull-spin tickは現在alphaから300msでfade-out。frameworkのTargetGroupingTransformTracker.AddTransformに合わせ、このtickが将来の終了fadeを取消す（SPINを終了直前に再出現させない）。 |
| clear | centre=(320,29+115)。TimeCompletedへ到達時のみ表示。sequence start=min(TimeCompleted,end-400)。400ms Out fade-in。scaleは1.25→.5/240ms Out→.625/160ms linear。end-50から現在alphaを50ms linearでfade-out。 |
| rpm | TopLeft=(320-87,445+offset)、scale=.625。 |
| SPM | TopRight=(320+80,448+offset)、scale=.625*.9、整数truncate。snapshot spinsPerMinuteを使い、rendererで計算しない。 |

rpm/SPMのhide offset=50。start-TimeFadeInからTimeFadeInのOutでoffsetを50→0に動かす。
Score fontのglyph layout/overlapを既存LegacyHudLayoutから再利用。SpinnerのLegacySpriteTextはFixedWidth未指定(default false)なのでproportional layout。配置scaleはSpinner専用。
Missing glyphは省略し、BitmapFontをSpinnerへ混在させない。

## Bonus / immutable events

既存SpinnerVisual.bonusScore()は通常spin tick scoreとextra bonusの合計で、lazerのCurrentBonusScoreと意味が異なる。
既存値とscore計算は維持し、full-spin境界で`SpinEvent(timeMs,legacyBonusScore,maximumBonus,bonusTick)`を発行する最小のvisual data追加を行った。
legacyBonusScoreは既存requirements/bonus score定数に基づくextra分のみ。maximumはGameplay側の既存maximumBonusSpinsから確定し、Rendererで推測しない。
snapshotはList.copyOfでimmutable。RendererはcompletedSpinsの差分や描画frameでeventを推測しない。

Bonus position=(320,29+299)、Centre origin、Score font。
通常incrementはalpha=1→0/800ms Out、scale=1.25→.8/800ms Out。
maximum時はalpha=1→0/500ms Out、scale=1.4→1.8/1000ms Out。
上限到達後の追加full-spinでもlazerのCompletedFullSpins callback同様maximum animationを再開するが、bonus tick white flashは実bonus award eventのみ。

## Gameplay差 / scope

- completionTimeMsはstart以降でprogress>=1となった最初のsession update時刻。lazer Result.TimeCompletedの意味と一致。Rendererで推定しない。
- required spins、judgement、score、rotation input、Auto、audioは変更なし。
- osujavaのrotationにはlazerのDamp(.99, elapsed)がなく、12pxのcursor dead zoneがある。既存rotationDegreesを表示する。
- osujavaのSPMは500ms windowのdegree delta、lazerは595ms record差分で、終了後もosujava側は減少する。既存SPM値をtruncateするため数値そのもののparityは未達。
- Spin history/inputのsampling差やbonus gap/score semanticsは既存Gameplayを維持。本taskはvisual eventsの追加のみ。
- Spinner sounds、frequency modulation、Cursor Ripple/particles、Health、follow points、hit error meter、key overlay、leaderboardは対象外。
- OsuRenderPlanのSpinner proxy/depth順を維持し、内部coordinateだけを分離。Judgement/HUD/Cursor順も変更なし。
- 既存SpriteBatchのtexture tint/alpha blendingを利用するため、lazerのlinear-light GPU compositingとの厳密なpixel parityは主張しない。colour transformの補間はframeworkのlinear RGB式に一致させた。

## Tests / visual harness

`./gradlew build`成功。coreの274 tests成功（Spinner pure transforms/config/density/ownership/immutable events/coordinate regressionを含む）。
`./gradlew :lwjgl3:spinnerVisualHarness`成功。
出力先は`/tmp/osujava-spinner`、107 PNGと`results.txt`。fixtureと生成画像はcommitしない。

Old: 0/25/50/90/100%、回転、NoBlink=0/1、登場、completion直前/直後、clear scale各区間、終了fade。
New: 同progress、middle2あり/なし、glow、white flash、bonus/max、completion、approach。
General: background+topでOld優先、Old/New root-only subasset不足、@2x、vector fallback、1280×720、600×800。
Retinaの実framebufferは2048×1536 (logical1024×768)、2560×1440 (1280×720)、1200×1600 (600×800)。
全107 scenarioで別時刻の描画を挟んで同時刻へ戻し、pixel arrays完全一致。
4つのinput replay scenarioは毎回新しいOsuGameplaySessionへ同じGameClock/input historyを再生し、pixel一致も確認。
blink-enabledの25%等も同じabsolute時刻で一致。主要Old/New画像を目視して中心、meter crop、rotation layer、SPMを確認した。

同一Skin/Spinner/時刻の**osu!lazer実画面比較は未実施**。sourceに基づくportとosujava実OpenGL captureによる検証であり、lazerとのpixel一致を示すものではない。
