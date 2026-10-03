# B05: ローカルDifficulty backend（2026-10-03）

## 数値計算の根拠と範囲

参照は公開lazer tag `2023.815.0`、commit `4e96853c7543f80a1b822ccd381943c7377543d7`、
`OsuDifficultyCalculator.Version=20220902` に固定する。
[calculator](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/OsuDifficultyCalculator.cs)、
[preprocessing](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/Preprocessing/OsuDifficultyHitObject.cs)、
[stacking](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapProcessor.cs)、
[skills/evaluators](https://github.com/ppy/osu/tree/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty)
を根拠に独立実装する。現行2026のalgorithm、またはstableの全譜面との同値は主張しない。

現在は **NM、mode 0、CS/AR/OD 0–10、StackLeniency 0–1**。
format v6以降のcircle/spinnerに加え、**v8以降の検証済みLinear/Bezier/Perfect/Catmull/mixed Slider**を含むchartへ拡張した。
未検証のpath/settings/timing・他mode・pre-v6はchart全体をUNSUPPORTEDとして数値を供給しない。
現在の具体的範囲は後述「曲線Sliderへの拡張」を参照。Linear段階の記録は履歴として保持する。
計算済み空chartはSUCCESSの0星、1object chartはreferenceの非zero値を保持する。
PENDING/UNSUPPORTED/FAILEDは星なし。object数/BPMだけの疑似星は導入しない。

独立calculatorはGL・GameplaySession・GameClock・audioを使用せず、元chartを変更しない。
float位置/CS scale/stack offset/angle、25ms strain cap、400ms section、aim/speed/rhythm、peak reduction、
最終performance結合を検証した。ゲーム用geometryのlegacy allowanceは変更していない。
数値calculatorのfloat scale契約はこの参照版に固定する。

上限は20,000objects、6時間の開始時刻span、2,000,000stack比較。
過大chartはUNSUPPORTED、非finite値・時刻の逆転等はFAILED。
interruptはcancelとして扱い、失敗ratingに変換しない。

## Reference再生成とテスト

[oracle説明](../tools/difficulty-reference/README.md)の手順で公開C#処理を一時directoryに取得し、
自作38fixtureの値を生成した。stable asset抽出なし。app/build/testのnetwork接続なし。
通常JUnitはcheck-in済み値だけを使用する。

許容差は比較前に固定した。star/aim/speedはabsolute 1e-9、object/sectionはabsolute 1e-7＋relative 1e-9、
stack高さは完全一致。empty/single/pair/three/jumps/stream/rhythm/simultaneous/stacks/spinner/gaps/
fractional settings/coordinates、12種のLinear fixtureと14種のcurve fixtureについて一致した。

## 残件

- Linear/Bezier/Perfect/Catmull/mixed pathの中間値は照合済み。次はSV丸め・NaN・同時刻timing/nested・pre-v8 tick距離を拡張する。
- pre-v6 stacking、範囲外settings、Mods別計算の検証。
- stable実機での数値比較、Difficulty group境界・NM/Mod適用範囲の観測。
- B05はcurveを含むfixtureまで対応済みだが、timing境界・stable実機・大規模cold/cache計測を残すため全体完了とはしない。

## Workerとcache

`LocalDifficultyService`をSong Select単位で所有し、disposeでinterrupt/queue破棄する。
1 daemon worker、待機32件、未公開完了64件。library内容は変更時だけcontent単位でindexし、
同内容の移動/複製は1jobを共有する。選択→visible→library順で優先し、満杯時のpromotionは末尾を
背景待機へ戻す。残り全譜面をexecutorへ一括投入しない。

UIのlibrary/prioritize/result/drainは計算・disk I/Oを行わない。workerのimmutable結果をgeneration付きで
戻し、frame側drainで現generationだけを採用する。1batchに1revision。
library交換前の結果はUIへ採用しない。同内容keyの既公開値は安全に再利用する。

Production cacheは `~/.osujava/difficulty/`、schema 1の内容別sidecar。
SHA-256/mode/正規化Mods/algorithm/preprocessingをkeyに含める。現在のserviceが要求するModsはNMのみ。
SUCCESS/UNSUPPORTED/FAILEDを永続化し、毎frame・warm restartで失敗を再計算しない。
旧計算版の別keyは新計算へ流用しない。未知schema・壊れたcacheは数値として信用せず、元fileを保持したまま
メモリ内で再計算する。64KiB読取上限を設ける。

一時fileをclose後、同directory内のhard linkで完成済みrecordをatomic公開する。
既存recordを置換しないので、競合で出現したfuture schemaも上書きしない。
hard linkを提供しないfilesystem/保存失敗では計算値を表示用メモリに保持し、storageWarningに理由を残す。
未対応filesystemで非atomic書込へ切り替えない。raw `.osu`は変更しない。

テストはcontent重複/移動/編集、cold/warm、計算版変更、Mods正規化key、terminal結果の再利用、
future/corrupt保持、保存先が通常fileの失敗、世代交換、closeのinterrupt、1万chartでのpriority/queue/completion上限を含む。

## Song Selectへの接続

以前はproductionのrating注入点が常にemptyで、用意済みstar renderer/animationを使用できなかった。
現在は同じlocal結果をrow star、selected情報、Difficulty sort/group/tab、`stars`検索に供給する。
`STAR` assetと既存procedural fallback、density処理、spriteGeneration別animationをそのまま使う。
新asset・texture/fontの毎frame生成・Gameplay側の採点変更はない。

[公式Interface wiki](https://osu.ppy.sh/wiki/en/Client/Interface)が示すDifficulty groupの整数切捨てと、
sortの易→難の方向を採用した。未知/unsupported/failedはUnknown difficultyへ置き、known 0とは分離する。
難易度単位の並び替えを使い、非隣接familyを再結合しない。
Sort/Group enumは末尾へ追加して既存ordinalを維持し、既存By Difficulty tabの明示identityへ接続する。

`stars>=5 stars<8`等は既知のfinite非negative値のみを比較する。未知は`!=`にもmatchしない。
現在のratingはNM。raw精度で比較するlocal仕様であり、nativeの小数丸め/Mod適用範囲まで同値とはしない。
検索のclear、完了後のreorder/group移動で既存playable identityを維持する。
明示的に別Groupを開いている場合、無関係な計算完了で選択Groupを強制的に開き直さない。

row内容は変更contentに属する難易度だけを再生成し、selected情報も更新する。
Sort/Group/searchがratingに依存しなければbrowserをrebuildしない。
依存する場合は完了を250msごとに集約し、全完了時は待たずに最終batchを反映する。
この分類遅延中もrow星/selected情報は直ちに更新する。
比較用ratingはrebuild時にchartごと1回取得し、sortの全比較ごとのprovider取得/boxingを避ける。
図形/文字の既存viewport reservation・row hitbox・chrome z-orderを変更していない。

## 初期circle/spinner対応時の検証と変更ファイル（履歴）

- `./gradlew build`: SUCCESS。core **145 suites / 1,386 tests**、lwjgl3 **2 suites / 4 tests**。
  failure/error/skip 0。最終log `/tmp/osujava-b05-final-build.log`。
- `difficulty-contracts`: 12 scenes / 72 PNG / 3,264操作frame。実worker、selected metadata、星renderer、
  Difficulty tabの実クリック、数値検索、known zero、unsupported slider、再オープンを確認。
  Greylooks / 全画像欠落のprocedural fallback / HD-only、1280×720・1280×800・1024×768・1280×720 density 2。
  最終log `/tmp/osujava-b05-gl-difficulty-final.log`、captures `/tmp/osujava-b05-gl-difficulty-final/`。
- 既存 `collections-contracts`: 16 scenes / 100 PNG / 1,588操作frame。
- 既存 `backend-contracts`: 16 scenes / 28 PNG / 672操作frame。
- 既存 `audit`: 84 scenes / 172 PNG / 1,256操作frame。長文/CJK、背景なし、多数difficulty、hover/hit、
  wheel、collapse/expand、score 0/1/多数、resize、巨大chrome、fallback/density、再保存/再読込を含む。
- 合計 **128 scenes / 372報告PNG / 6,780操作frame**。difficulty初回と最終再取得は重複加算しない。
  既存suiteのlog/capturesは `/tmp/osujava-b05-gl-<phase>.log` と同名directory。
- `:core:songSelectPerformanceProbe`: SUCCESS。GL/audio/画像I/Oなし・hashless fixtureの10,000sets/40,000diffsで
  idle mean **41.047μs / p95 43.802μs / 13,404.3bytes/frame**、idle中GC 0。
  `/tmp/osujava-b05-performance.log`。これはsteady updateのCPU probeであり、実大規模libraryのcold rating計算時間、
  cache I/O時間や星分類中のworst frameを測った値ではない。worker上限/priorityは別の1万chartテストで確認した。

主な変更:

- [StandardDifficultyCalculator](../core/src/main/java/dev/osujava/difficulty/StandardDifficultyCalculator.java)、
  [DifficultyResult](../core/src/main/java/dev/osujava/difficulty/DifficultyResult.java): 純粋計算・未知/成功/未対応/失敗。
- [LocalDifficultyService](../core/src/main/java/dev/osujava/difficulty/LocalDifficultyService.java)、
  [DifficultyCache](../core/src/main/java/dev/osujava/difficulty/DifficultyCache.java)、
  [DifficultyKey](../core/src/main/java/dev/osujava/difficulty/DifficultyKey.java): scheduling/世代/永続cache。
- [SongSelectScreen](../core/src/main/java/dev/osujava/ui/SongSelectScreen.java)、
  [SongBrowserModel](../core/src/main/java/dev/osujava/ui/SongBrowserModel.java)、
  [SongBrowserQuery](../core/src/main/java/dev/osujava/ui/SongBrowserQuery.java)、SongBrowserControls/OsuJavaGame: UI接続。
- `core/src/test/java/dev/osujava/difficulty/`、SongBrowserRatingsTest、既存browser/tab tests、
  `lwjgl3/src/hudHarness/java/dev/osujava/ui/SongSelectVisualHarness.java`: 数値/回帰/実描画。
- [reference oracle](../tools/difficulty-reference/README.md)、
  `core/src/test/resources/difficulty/reference-20220902/`: 再生成手順とcheck-in済み自作fixture。

commit:

- `68bb8c5` — `feat(difficulty): verify local NM circle and spinner calculation against pinned reference`
- `9c82355` — `feat(difficulty): cache versioned local results with a bounded background worker`
- `3300bc2` — `feat(song-select): connect verified local stars to difficulty browsing and search`
- `cae000a` — `test(song-select): cover bounded calculations and real worker rating visuals`

本記録とREADME/計画/残件台帳の更新は別のdocs commit。

## 次の作業に渡す未完了項目

1. **未検証のtiming/pathや処理上限を超えるchartは全体がUNSUPPORTED**。circle部分だけを計算しない。
   common legacy curve（Linear/Bezier/Perfect/Catmull/mixed）は対応・照合済み。
   次はSV丸め境界、NaNによるtick無効化、同時刻timing/nested event、pre-v8 tick距離、
   degree-specific B-spline、zero-length/極小Slider、範囲外settingsを検証する。
   曲線の多い実譜面は100,000work budgetを超える場合がある。cold実測から上限を評価する。
2. pre-v6 stacking、CS/AR/OD範囲外、StackLeniency範囲外、他mode、Mods別calculatorは未対応。
   Mod cache keyは用意したが現在の要求はNMだけ。通常Modsの効果/HP/rate audioはB06/B07で別に実装する。
3. 公開C# subsetとの一致を検証したが、stable b20230727.9での数値照合、decoder/legacy allowance差、
   native `stars`検索の丸め・Mod適用、group label/animationの同期観測は未実施。
4. 1万chart queue上限とsteady CPU probeは確認済み。実大規模libraryのcold/warm calculation/cache時間、
   分類rebuild集中時のframe p95/GC、rapid import/選曲の実描画計測は次段階で追加する。
5. sidecarの古いversion/孤立record cleanup、容量上限、外部cache変更のlive reloadは未実装。
   現行version cacheはscreen再オープンで読み、旧versionは別keyとして保持する。
   hard link不可ならsession内メモリのみで、次screen/restart時は再計算する。
6. B06 HP/fail、B07通常Mods、B08 replay、B09削除/退避/復旧は未着手のまま。
   official ranked status、online ranking、Bancho/API接続は対象外。
   B03/B04と既存Results layout/font等の具体的残件は統合残件台帳を参照する。

## Linear Sliderへの拡張（開始HEAD `834df09`、履歴）

独立した`LinearSliderPreprocessing`（現在の[SliderPreprocessing](../core/src/main/java/dev/osujava/difficulty/SliderPreprocessing.java)）を追加した。
float polyline、累積距離と期待長への短縮/延長、末尾重複時の延長抑止、repeat終点を実装する。
tick/repeatとlegacy tailを時刻順に扱い、lazy cursor/travel/timeとminimum jumpをCalculatorへ供給する。
Modern stackingのSlider終端・負のstack、aimのtravel/velocity bonus、speedのtravel距離、
rhythmのSlider境界補正も接続した。元chartを変更せず、worker内でのみ実行する。

根拠は同じpinの[Slider defaults/nested objects](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Objects/Slider.cs)、
[SliderPath](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Objects/SliderPath.cs)、
[events](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Objects/SliderEventGenerator.cs)、
[control point lookup](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/ControlPoints/ControlPointInfo.cs)。
公開path/events/stack/preprocessing/evaluator/skillはoracleで変更せず実行する。
Linear approximationはidentity、frameworkのbindable/cache等はfixture adapter。
Slider defaultsとnestedへの接続はwrapperであり、完全な公開decoderやstable実機の検証とは区別する。

観測・照合で確定した点:

- Legacy tailは`max(start + duration / 2, end - 36ms)`の時刻だが位置は実Slider終点。
  遅いtickがそれを越える場合、lazy travel timeはsorted nested listの最後を使う。
- Repeatが偶数spanなら終点はhead。headだけのstack判定ではnegative stackを再現できない。
- NMの最大follow radiusはfloatの`50 * 2.4f`で、decimal 120へ置換すると中間値がずれる。
- Nested位置は`(unstacked head + relative path) + stack offset`。
  `stacked head + relative path`とのfloat丸め差もfixtureで検出・修正した。
- 最初のred line以前でもTimingPointAtは最初のred lineを使い、red line皆無なら1000msを使う。
  SVは当該時刻以前のdifficulty pointを使うため、このfallbackと分けて扱う。

Linear段階の受入条件（現在の拡張は次節）:

- v8以降、single typed Linear segment（複数control pointのpolylineは対応）、距離>0、最大100,000px。
- SliderMultiplier 0.4–3.6、TickRate 0.5–8。finiteで時刻がstrictly increasingなtimingのみ。
  red beat length 6–60,000ms。SVは0.1–10にclamp後、0.01刻みに既に一致する値のみ。
- 同時刻のnested eventは未検証でunknown。NaN tick無効化、SVの丸め境界、同時刻red/greenは未対応。
- chart全体でcontrol point/span/tick/nested/timing処理のbudget 100,000。Slider durationも6時間以内。
  元の20,000objects / 2,000,000stack比較制限を維持。過大repeatは整数加算前に拒否し、interruptを伝播する。

この段階の計算keyはalgorithm `osu-java-nm-20220902-2` / preprocessing `linear-slider-f32-v8-1`。
旧versionのUNSUPPORTED Slider cacheを再利用せず、旧fileは保持して新versionへ計算・保存する。
旧UNSUPPORTED→新SUCCESS→warm再利用を実workerの回帰testで確認した。

追加fixtureはlinear-basic/repeat/polyline/sv/stacks/late-tick/duplicate/no-timing/rhythm/single/spinner/future-timing。
合計24fixtureで星・aim/speed・stack・object/section値、path距離/位置、nested時刻/位置、lazy end/travel、
minimum jumpを既存の許容差のまま照合した。Java出力を見て許容差を緩めていない。

今回の検証:

- `./gradlew build`: SUCCESS。core **145 suites / 1,401 tests**、lwjgl3 **2 suites / 4 tests**、failure/error/skip 0。
  log `/tmp/osujava-linear-build.log`。
- `difficulty-contracts`: **12 scenes / 96 PNG / 4,344操作frame**。実workerによるLinear表示・分類・検索、
  未対応Bezierのunknown、known zero、旧fixtureとLinearのwarm再表示を確認。
  Greylooks/fallback/HD-only × 16:9・16:10・4:3・density 2。log `/tmp/osujava-linear-gl-final.log`、
  captures `/tmp/osujava-linear-gl-final/`。画像の4:3 Greylooksと2x fallbackも目視確認。
- Renderer/input/Gameplayの変更はない。今回は星計算とdifficulty描画suiteを実行し、
  前段のcollections/backend/audit全suiteの再実行はしていない。

次の数値検証時には、既存parserが破損HitObject行をskipする情報をrating側へ渡す方法も検討する。
現在のcalculatorはparse後modelだけを受け取り、元fileのskip履歴を知れない。
壊れた行を含むraw fileと完全な正常fileの同値は本fixture suiteの検証対象外。

Linear拡張のcommit:

- `0990ff4` — `feat(difficulty): verify linear slider preprocessing and NM strains`
- `bd63944` — `test(song-select): cover linear ratings and warm cache visuals`

本記録・README・計画・残件台帳の更新は別のdocs commit。

## 曲線Sliderへの拡張（開始HEAD `363ee06`）

[SliderPathApproximator](../core/src/main/java/dev/osujava/difficulty/SliderPathApproximator.java)を独立実装し、
[SliderPreprocessing](../core/src/main/java/dev/osujava/difficulty/SliderPreprocessing.java)に接続した。
Gameplayの倍精度path・Importerのsegment model・Renderer・入力を変更せず、worker内の難易度前処理だけで変換する。
BezierのDe Casteljau適応分割、Catmull-Romのfloat多項式、floatの円弧中心/半径とsamplingを実装した。
既存のpath長補正・nested/lazy cursor・stack・strain計算を共有する。

追加の根拠は同じosu! versionが依存するframework `2023.815.0`、
commit `3365c86f769cb0ed84a10c5c96313a73e552dc2d` の
[PathApproximator](https://github.com/ppy/osu-framework/blob/3365c86f769cb0ed84a10c5c96313a73e552dc2d/osu.Framework/Utils/PathApproximator.cs)、
[CircularArcProperties](https://github.com/ppy/osu-framework/blob/3365c86f769cb0ed84a10c5c96313a73e552dc2d/osu.Framework/Utils/CircularArcProperties.cs)と、
[legacy path decoding](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Objects/Legacy/ConvertHitObjectParser.cs)。
oracleは公開frameworkの2 fileを変更せず実行する。path conversionの4 private methodも本文を変更せず
一時wrapperへ取り出して実行する。Slider defaults/timingとvector等はfixture adapterのままで、
完全な公開decoderやstable実機の再現とは主張しない。app/build/testはnetwork不要。

判明・修正した契約:

- Bezierは2階差分のfloat flatnessと0.25 toleranceで適応分割する。端点だけを足す近似では中間値が一致しない。
- Catmullは50分割、最終端の次の点は外挿する。legacy v128未満では途中の重複点をsegment区切りにしない。
- Perfectは3点以外ならBezier、collinearならLinear。minor/major arcとfloat centre/radiusを照合した。
- 曲線の最初のtyped vertexはpublic SliderPathで単独出力される（Catmullは単独subpathが空）。
  これを欠くと円弧始点の丸めを含む微小segmentが失われ、path/jumpの値がずれる。比較で発見・修正した。
- Encoded control座標はintへtruncateしてからheadを引く。fractional control fixtureで確認した。
- mixed pathの型記号後の最初のencoded pointは、前segmentの終点と次segmentの始点を共有する。
  JavaのImporter modelは異なる表現なので、難易度側だけでこの契約へ変換する。

現在の受入はv8+のLinear/Bezier/Perfect/Catmullと明示mixed、implicit duplicate区切り。
距離/setting/timingのLinear段階の制限を維持する。non-linear subcurveはcontrol 64点まで、
Bezierはdepth 32まで。既存100,000work budgetにsubdivision/flatness/出力も数え、過大curveをunknownへ戻す。
2,000,000stack比較・20,000objects・6時間制限とcancel伝播も維持する。多数curveでbudgetを超える場合もある。
未検証のpath/settings/timingを含むchart全体に星を捏造しない。

計算keyはalgorithm `osu-java-nm-20220902-3` / preprocessing `legacy-curves-f32-v8-1`。
旧Linear-only versionのBezier UNSUPPORTED cacheを保持して新versionで計算し、warm再利用を回帰testで確認した。

追加14fixtureはcurve-bezier/bezier-segments/bezier-high-degree/perfect/perfect-major/perfect-fallback/
catmull/catmull-duplicates/catmull-v128/mixed/stacks/fractional-controls/loop/perfect-reverse。
**計38fixture**で星/skill・stack・path/nested/lazy/jump・object/sectionを同じ許容差で照合した。
既存24oracle fileはbyte単位で変更なし。星だけが近いという判定にはしていない。

今回の検証:

- `./gradlew build`: SUCCESS。core **145 suites / 1,418 tests**、lwjgl3 **2 suites / 4 tests**、failure/error/skip 0。
  log `/tmp/osujava-curves-build.log`。
- 実workerで旧Linear-only cache移行→SUCCESS→warm再利用、過大control/subdivision、空typed segmentを回帰test化。
- `difficulty-contracts`: **12 scenes / 156 PNG / 7,044操作frame**。
  Bezier/Perfect/Catmull/mixedの星・selected情報、Difficulty分類/検索、Bezier warm再表示、
  未対応degree-specific B-splineのunknown、known zero、従来Linearを確認。
  Greylooks/fallback/HD-only × 16:9・16:10・4:3・density 2。log `/tmp/osujava-curves-gl.log`、
  captures `/tmp/osujava-curves-gl/`。4:3 Greylooks円弧と2x fallback mixedの画像も目視確認した。
- 今回Renderer/input/Gameplayの変更はなく、前段のcollections/backend/audit全suiteは再実行していない。

残件の優先順は、parseでskipされたHitObjectの状態をratingへ伝えること、SV/同時刻/NaN/tick境界の
reference拡張、実大規模libraryのcold/warm計測とwork budget評価、stable実機比較。
degree-specific B-spline、pre-v6、通常Mods・HP/fail・replay・管理拡張も未完了のまま。

曲線拡張のcommit:

- `67fc1e5` — `feat(difficulty): verify legacy slider curves against pinned reference`
- `b1d1c23` — `test(song-select): cover curve ratings and cached reopening`

本記録・README・計画・残件台帳の更新は別のdocs commit。
