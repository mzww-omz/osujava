# osu!standard Gameplay 差分調査

比較対象は手元の `~/osu/osu` の `osu.Game.Rulesets.Osu` と、この repository の Gameplay 実装。重要度は体感への影響、難易度は現行設計での変更量を示す。`R` = `core/src/main/java/dev/osujava/ruleset/osu/`、`G` = `core/src/main/java/dev/osujava/gameplay/`、`U` = `core/src/main/java/dev/osujava/ui/`、`B` = `core/src/main/java/dev/osujava/beatmap/`。

| 項目 | osujava 現状 | lazer の実装 | 差 | 重要度 | 難易度 | 主な修正対象 |
|---|---|---|---|---|---|---|
| 1. Gameplay timing | 共通 `GameClock`、AR preempt は整数 ms、fade は固定 180 ms | `OsuHitObject` は AR から整数 preempt、`TimeFadeIn = 400 × min(1, preempt / 450)`。各 drawable は clock 上で transform | fade と object lifetime が短い | 高 | 低 | `G/ApproachTimeCalculator.java`, `G/GameplayVisualTiming.java`, `R/OsuGameplaySession.java` |
| 2. HitCircle | 単色 primitive、hit 時に即消去、number は常に同じ簡易配色 | `DrawableHitCircle` が CirclePiece と approach を別に描画。hit 時は flash 40 ms、拡大 400 ms、fade、miss は 100 ms fade | appearance と click feedback に大差 | 最高 | 中 | `U/GameplayRenderer.java`, `U/GameplayHudRenderer.java`, `G/HitCircleVisual.java` |
| 3. Slider | sampled path の太線、head/tail/repeat/ball、tick は非表示 | `DrawableSlider` は body、nested head/tail/tick/repeat、ball、follow circle を管理し、body は snaking と fade。`SliderInputManager` が tracking と tail を判定 | 視認性、tracking、event 表示と timing が違う | 最高 | 高 | `R/OsuGameplaySession.java`, `R/SliderEventGenerator.java`, `G/SliderVisual.java`, `U/GameplayRenderer.java` |
| 4. Spinner | hold 中の角度を積算し、単純な円と進捗 arc | `Spinner` が OD と長さから requirement/nested tick を生成。`DrawableSpinner` と default/Argon skin が disc、ring、SPM、bonus、completion を描画 | requirement は近いが feedback と演出が簡略 | 中 | 中 | `R/SpinnerRotationTracker.java`, `G/SpinnerVisual.java`, `U/GameplayRenderer.java`, `U/GameplayHudRenderer.java` |
| 5. Input | Z/X/LMB/RMB を一つの boolean hold に統合 | `OsuAction` の押下集合。`SliderInputManager` は head を hit した action と他の action の押下履歴を区別 | 先押しした別 key で slider を保持できる | 最高 | 中 | `G/GameplaySession.java`, `U/GameplayInputProcessor.java`, `R/OsuGameplaySession.java`, `R/DebugAutoPlayer.java` |
| 6. Hit detection / judgement | 時間差最小候補を選び、50 window 後に miss | `OsuPlayfield` の `StartTimeOrderedHitPolicy` は開始時刻順の note lock。`DrawableHitCircle` は hit window と位置を別に判定、miss window 400 ms | 重なった note の選択と早打ち判定が異なる | 高 | 中 | `R/OsuGameplaySession.java`, `G/JudgementWindows.java` |
| 7. Visual animation | circle fade 180 ms、approach 2.5→1、hit 結果は ring/text | `DrawableHitCircle` は approach 4→1、alpha 0→0.9 を `min(2×fadeIn, preempt)`、hit 時 50 ms fade。default `MainCirclePiece` は flash/explode/scale | visual timing が大きく異なる | 最高 | 中 | `G/GameplayVisualTiming.java`, `U/GameplayRenderer.java`, `U/GameplayHudRenderer.java` |
| 8. Playfield geometry | 512×384 を window 全体へ fit。radius `54.4−4.48×CS` | `OsuPlayfield.BASE_SIZE` は 512×384。`LegacyRulesetExtensions.CalculateScaleFromCircleSize(CS,true) × 64`。周囲に object radius 分の表示余地 | CS 0〜10 の半径はほぼ同じだが 1.00041 補正と余白がない | 中 | 低 | `G/OsuObjectGeometry.java`, `U/PlayfieldViewport.java` |
| 9. Stacking | なし | `OsuBeatmapProcessor` が v6 以降 reverse pass、旧譜面 forward pass。3 px 閾値、`preempt × StackLeniency`、offset `−6.4×scale×height` | 重なりと hit target がずれる | 高 | 中 | `B/DifficultySettings.java`, `B/parse/BeatmapFileParser.java`, `R/OsuStacking.java`, `R/OsuGameplaySession.java` |
| 10. Score / combo | slider nested event を全て HIT300/MISS として accuracy に算入。色を number で循環 | nested event は型別 judgement。combo index と number は別、spinner は色を進めない | score、accuracy、combo colour が違う | 高 | 中 | `G/ScoreTracker.java`, `R/OsuGameplaySession.java`, `G/GameplayVisualConfig.java` |
| 11. HUD / feedback | 汎用 panel、number、判定 ring/text | `OsuPlayfield` は judgement layer と hit object layer を分離。skin の judgement、follow points、spinner SPM など | hit の読み取りやすさと情報密度に差 | 中 | 中 | `U/GameplayHudRenderer.java`, `U/GameplayRenderer.java` |
| 12. Auto | 対象の座標へ瞬間移動、同一 input API、spinner 300 RPM | `OsuAutoGenerator` は曲線補間と easing、交互打ち、stacked position、slider path、spinner 進入方向を考慮 | 目視検証の動きがぎこちない | 中 | 中 | `R/DebugAutoPlayer.java` |
| 13. Audio / hitsound | 譜面音楽のみ。hitSound、slider node sample、spinner sample は鳴らない | HitObject の samples、slider sliding/tick/repeat/tail、spinner spin/bonus を時刻と結果に合わせて再生 | hit feeling に大きな欠落 | 高 | 高 | `B/HitObject.java`, `B/parse/BeatmapFileParser.java`, `U/GameplayScreen.java`, 新規 audio component |
| 14. Skinning | `GameplayVisualConfig` の primitive と固定色 | `OsuSkinComponents` で hitcircle、approach、slider、spinner、judgement を個別 lookup。default と Argon に別の実装 | texture/skin 差し替え不可 | 中 | 高 | `G/GameplayVisualConfig.java`, `U/GameplayRenderer.java`, 新規 skin component |

## 実装順

1. AR/CS/approach と hit circle の clock 依存 animation。
2. action ごとの入力、開始時刻順 hit policy、stacking。
3. slider の tracking・nested event・tail と表示。
4. spinner と judgement feedback、typed score の土台。
5. audio sample と texture skin の導入。ここは beatmap sample 情報と asset 管理を小分けに実装する。

参照した主な lazer source: `Objects/OsuHitObject.cs`, `Objects/Drawables/DrawableHitCircle.cs`, `Objects/Drawables/DrawableSlider.cs`, `Objects/Drawables/SliderInputManager.cs`, `Objects/Drawables/DrawableSpinner.cs`, `Beatmaps/OsuBeatmapProcessor.cs`, `UI/OsuPlayfield.cs`, `UI/StartTimeOrderedHitPolicy.cs`, `Replays/OsuAutoGenerator.cs`, `Skinning/Default/MainCirclePiece.cs`, `Skinning/Default/DefaultApproachCircle.cs`, `osu.Game/Rulesets/Objects/SliderEventGenerator.cs`, `osu.Game/Rulesets/Objects/Legacy/LegacyRulesetExtensions.cs`。
