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
現在の具体的範囲は後述「曲線Sliderへの拡張」とB05-T2a–cを参照。
SV丸め・source時刻順の同時刻batch・継承NaNのtick抑制を検証済み。Linear段階の記録は履歴として保持する。
計算済み空chartはSUCCESSの0星、1object chartはreferenceの非zero値を保持する。
PENDING/UNSUPPORTED/FAILEDは星なし。parserでHitObject行をskip、不正setting/timingをfallback、Spinner終端を補正したchartはFAILEDとし、部分的な星や0星を供給しない。
parserがsource timingを並べ替えたslider chartはUNSUPPORTEDとし、元順を推測しない。
object数/BPMだけの疑似星は導入しない。

独立calculatorはGL・GameplaySession・GameClock・audioを使用せず、元chartを変更しない。
float位置/CS scale/stack offset/angle、25ms strain cap、400ms section、aim/speed/rhythm、peak reduction、
最終performance結合を検証した。ゲーム用geometryのlegacy allowanceは変更していない。
数値calculatorのfloat scale契約はこの参照版に固定する。

上限は20,000objects、6時間の開始時刻span、2,000,000stack比較。
過大chartはUNSUPPORTED、非finiteなHitObject値・HitObject開始時刻の逆転等はFAILED。継承NaNのbeatLengthは合法なtick抑制設定として扱う。
interruptはcancelとして扱い、失敗ratingに変換しない。

## Reference再生成とテスト

[oracle説明](../tools/difficulty-reference/README.md)の手順で公開C#処理を一時directoryに取得し、
自作45fixtureの値を生成した。stable asset抽出なし。app/build/testのnetwork接続なし。
通常JUnitはcheck-in済み値だけを使用する。

許容差は比較前に固定した。star/aim/speedはabsolute 1e-9、object/sectionはabsolute 1e-7＋relative 1e-9、
stack高さは完全一致。empty/single/pair/three/jumps/stream/rhythm/simultaneous/stacks/spinner/gaps/
fractional settings/coordinates、12種のLinear fixtureと14種のcurve fixture、7種のtiming fixtureについて一致した。

## 残件

- Linear/Bezier/Perfect/Catmull/mixed path、SV丸め、同時刻timing、継承NaNは照合済み。次はpre-v8 tick距離とnested同時刻。
- pre-v6 stacking、範囲外settings、Mods別計算の検証。
- stable実機での数値比較、Difficulty group境界・NM/Mod適用範囲の観測。
- 1万source hashのservice/cold/warm baselineと登録allocation修復は計測済み。実library/UI分類/処理上限/cancel評価は残る。
- B05は対応subsetの45fixtureまで検証済みだが、残るtiming・実library・stable実機比較のため全体完了とはしない。

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
この段階のcalculatorはparse後modelだけを受け取り、元fileのskip履歴を知れなかった。
現在は後述「破損HitObjectの状態伝播」で修復済み。
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

## 破損HitObjectの状態伝播（開始HEAD `5c921bf`）

従来はparserが短い行・不正な数値・壊れたSlider/Spinner/Hold行をskipした後、その状態を失っていた。
残ったcircleだけへ正常な星を付けたり、全行をskipしたchartに0星を付ける問題を修復した。
これはJava内部の欠損通知の修正であり、stableの破損file許容仕様を再現したという主張はしない。

- [BeatmapDifficulty](../core/src/main/java/dev/osujava/beatmap/BeatmapDifficulty.java)に非負の`skippedHitObjectCount`を保持。
  コメント/空行は対象外。既存のprogrammatic constructorは0を補い、asset解決の両経路は件数を維持する。
- [BeatmapFileParser](../core/src/main/java/dev/osujava/beatmap/parse/BeatmapFileParser.java)は既存のskip契約を維持しつつ件数を集計。
  一部の破損でset全体を捨てない。valid row・元file digest・既存の時刻sortを保持する。
- [Importer](../core/src/main/java/dev/osujava/library/BeatmapArchiveImporter.java)はfileとskip件数を既存`ImportResult.warnings`へ追加。
  Song Selectのtoastは従来の「difficultyをskipした」という固定文からimport warning件数へ変更。
  行skipだけでもdifficulty自体を捨てたと誤って表示しない。詳細warningの常設viewerは追加していない。
- [Library loader](../core/src/main/java/dev/osujava/library/PropertiesBeatmapLibraryStorage.java)のmodel組み直しでも件数を維持。
  元`.osu`を再parseするためindex schema変更不要。再起動でも復元し、source修復後は新しい件数/hashを読む。
- [Calculator](../core/src/main/java/dev/osujava/difficulty/StandardDifficultyCalculator.java)は欠損chartを全体FAILEDとして早期終了。
  partial chartのstrain計算をせず、理由に件数を保持。真のempty chartは従来通りSUCCESS/0星。

algorithmは`osu-java-nm-20220902-3`のまま、preprocessingを`legacy-curves-f32-v8-2`へ更新した。
同じraw contentに保存されていた旧SUCCESSを使わず、FAILEDを新keyへ保存する。
旧cacheは上書き/削除しない。warm再表示でFAILEDを再利用し、source修復後は新content keyでSUCCESSへ戻る。
worker/cache/Renderer/Gameplayの責務とresource上限は維持する。正常38fixtureの数値は変更なし。

検証:

- 短い行、time/hitSound不正、Slider/Spinner/Hold破損、コメント/空行、両asset解決経路を回帰test化。
- `.osu`とnested `.osz`のImport warning→保存→別storage instanceで再読込→source修復を実経路で検証。
- 旧partial SUCCESS cacheの保持→新FAILED→移動/warm再利用→source修復による再計算を実workerで検証。
- 部分譜面/全行skipに星なし、実emptyに0星という判別をcalculatorと実描画の双方で検証。
- `./gradlew build`: SUCCESS。core **145 suites / 1,423 tests**、lwjgl3 **2 suites / 4 tests**、failure/error/skip 0。
  log `/tmp/osujava-parser-build.log`。関連5 test classの先行実行も成功。
- `difficulty-contracts`: **12 scenes / 192 PNG / 8,664操作frame**。Greylooks/fallback/HD-only ×
  16:9・16:10・4:3・density 2。従来38fixtureのうちUI対象の数値、分類/検索/known zero/unknown、
  partial/damaged-emptyの星なし、partial warm再表示が成功。
  log `/tmp/osujava-parser-gl.log`、captures `/tmp/osujava-parser-gl/`。
  4:3 Greylooksのdamaged-emptyと2x fallbackのpartial warmを目視確認。
  このGL runはtoast文言変更前。変更後のbuildは成功。今回はcollections/backend/audit全suiteを再実行していない。

現在の通知対象は**実際にskipされたHitObject行のみ**。TimingPointの短い行skip・不正なsetting/timingの
既存fallback、Spinner終端の補正などはこの件数に含まれない。次はこれらのsource品質通知と、
SV丸め/NaN/同時刻timing/nested/pre-v8 tick距離のreference検証を進める。
実大規模libraryのcold/warm/work budget評価、stable実機比較、pre-v6/degree-specific B-spline/Modsも残る。


状態伝播のcommit:

- `76f0554` — `fix(difficulty): reject ratings for skipped hit object rows`
- `76a658f` — `test(song-select): verify damaged charts never display stars`

本記録・README・進捗・残件台帳の更新は別のdocs commit。


## setting/timing/Spinner補正のsource品質通知

通常Phaseへ復帰した最初の変更単位B05-T1。開始HEAD `70a29a6`。
既存parserは不正なDifficulty/General値を既定値へ置換し、短いTimingPointをskip、数値/flagの不正をfallback、
Spinnerの終端が開始前なら開始へ補正していた。その由来を失うと正常なcircle chartやempty chartとして星を公開できた。
これはJavaのsource品質通知の修復であり、stableの壊れたfile許容範囲を認定したという意味ではない。

- 新規`BeatmapParseIssues`はsetting数・問題のあるTimingPoint行数・補正したobject数のimmutable snapshot。
  `BeatmapDifficulty`に保持し、両asset解決経路とlibrary再構築で維持。既存constructorはNONEを補い、互換性を保つ。
- 対象settingはHP/CS/OD/AR/SliderMultiplier/SliderTickRate、GeneralのStackLeniency/Mode。
  明示した空値・数値不正・非有限値を通知する。省略した既定値とOD→ARのlegacy fallbackは問題として数えない。
  設定の有限な範囲外は従来の未検証範囲判定で扱い、入力を新たにclampしない。
- TimingPointは短い行、time/beatLengthの不正、存在するmeter/sample/index/volume/effectsの整数不正、
  uninheritedの0/1以外を行単位で通知。省略されたoptional列は有効。従来のparse値・sort・skipを維持する。
- 継承pointのbeatLength NaNは意図的なtick無効化のencodingとして認識し、破損件数へ入れない。
  ただし現在のSlider前処理では未検証なのでUNSUPPORTEDのまま。赤pointのNaN、非有限time、Infinityは通知する。
- Spinnerは実際に採用したrowの終端補正だけを数える。同じrowが不正HitSound等でskipされた場合は二重計上しない。
  開始前の終端を補正したchartは保守的に星なしとする。現在のGameplay/Importの補正動作を変えたわけではない。
- Importerは既存warningへ件数とsource fileを追加。parse後にLibrary保存→新storage instanceで再読込→元file修復した場合も通知を再計算。
  library schemaの変更・問題のあるSet全体の破棄・Rendererでのsource再readはしない。
- Calculatorはsource問題ありを早期FAILEDにし、星・中間strainを作らない。known zeroと異なるunknownを維持。
  algorithmは`osu-java-nm-20220902-3`のまま、preprocessingは`legacy-curves-f32-v8-3`。
  旧v8-2 SUCCESSを使わず、新keyでFAILEDを保存。旧cacheは保持する。正常chartも新keyで一度再計算する。

根拠:

- [公式.osu形式](https://osu.ppy.sh/wiki/en/Client/File_formats/osu_(file_format))のDifficulty/TimingPoints/Spinner欄。
- 固定済み公開commit `4e96853c7543f80a1b822ccd381943c7377543d7` の
  [LegacyBeatmapDecoder](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/Formats/LegacyBeatmapDecoder.cs)。
  継承NaNを許容してtick生成を止め、赤pointのNaNを拒否する区別を確認した。
  既存Java parserのfallbackを独立に追跡した実装であり、公開decoderやstable binary内部コードはコピーしていない。

検証:

- 関連5 test class: **105 tests**成功。設定の空/不正/NaN/Infinity/overflow、省略AR/optional timing、
  red/green NaN、採用/skip Spinner、Import `.osu`/nested `.osz`→保存→再起動→source修復を確認。
- 旧v8-2 SUCCESS保持→新FAILED→移動/warm再利用→source修復→SUCCESSを実workerで検証。
- `./gradlew build --offline --console=plain`: 成功。core **146 suites / 1,442 tests**、lwjgl3 **2 suites / 4 tests**。
  failure/error/skipped 0。追加13 test cases。log `/tmp/osujava-b05-source-build.log`。
- `difficulty-contracts`: **12 scenes / 288 PNG / 12,984 scripted transition frames**成功。
  source-setting/source-timing/source-spinner/source-empty-settingのrow・情報・Unknown分類・星検索除外とwarm再表示を追加。
  Greylooks/fallback/HD-only ×16:9/16:10/4:3/density 2。log `/tmp/osujava-b05-source-gl.log`、captures `/tmp/osujava-b05-source-gl/`。
  4:3 Greylooksのempty-settingと2x fallbackのSpinner warm画面を目視確認。
  正常38数値fixtureは既存の星/skill/stack/path/nested/strain許容差で全件成功。
- Renderer/input/Gameplayを変更していない。今回はdifficulty suiteを実行し、selector/collections/audit全suiteの再実行はしていない。

次の通常単位は[計画B05-T2](songselect-backend-improvement-plan-20261002.md#通常phaseへの復帰とselector残件の実行順)。
SV丸め/同時刻control point/継承NaNの実tick動作/nested同時刻/pre-v8距離は引き続き未検証。
source品質通知は数値互換を拡張しない。raw parserの重複key/非標準数値記法/全source構文の厳密互換も未認定。
B05-T3の実大規模計測、stable実機比較、B06以降も未完了。B05全体を完了とはしない。

Commit:

- `7232a51` — `fix(difficulty): retain source issues before publishing ratings`
- `6b61d25` — `test(song-select): cover source quality failures and warm ratings`
- 本記録・計画・進捗・残件台帳の更新は後続のdocs commit。


## B05-T2a: SV丸め境界とclamp

通常Phaseの次の小単位を実装した。v8+、finiteなtiming、既存の検証済みcurve subsetで、
raw SVが0.01に一致しないという理由だけのUNSUPPORTEDを解除した。
同時刻timing、継承NaN、pre-v8 tick multiplier、nested tieは引き続き未対応。

- development oracleで公開DifficultyControlPoint／BindableDouble／BindableNumber／RangeConstrainedBindableを変更せず実行。
  LegacyDifficultyControlPoint classとSlider速度bindable/propertyは全本文を一時wrapperへ抽出し、raw/clampedとroundedを分離。
  sourceはappへ取り込まず一時directoryで破棄。原38referenceはbyte単位で不変。
- 自作timing-sv-rounding／timing-sv-clampの2fixture・20sliderケースを追加。中点の前後、偶数／奇数、0.1／10境界、
  finite subnormalからの除算overflow、正／0の継承beatLengthを検証。binary double演算順も維持する。
  星／aim／speed／stack／path／nested／lazy／object／sectionを従来の許容差で全件照合した。
- JavaのSV計算はclamp後にMath.rintでnearest ties-to-evenへ丸める既存式を維持し、未検証guardだけ解除。
  algorithm versionは同じ、preprocessingはlegacy-curves-f32-v8-4。旧v8-3 UNSUPPORTEDは保持し新keyでSUCCESSへ再計算。
  次のservice instanceではcache SUCCESSを使い、calculator callbackは再実行しない。
- 公開decoder/control pointの同時刻batch、full defaults/nested materialisationはまだoracle未接続。
  oracleも未検証timingの期待値を生成しないようNaN／同時刻・逆順／pre-v8を明示拒否する。

検証:

- Calculator／LocalDifficultyServiceの関連63 tests成功。固定referenceは40fixtureになった。
- SV修復時のbuildはcore 146 suites / 1,445 tests、lwjgl3 2 suites / 4 tests、failure/error/skipped 0。
- difficulty-contractsは12 scenes / 336 PNG / 15,144 scripted transition framesとnavigation/disposal確認に成功。
  新2fixtureのworker評価、row／情報／星group／search、cold／warm表示を追加。
  Greylooks／fallback／HD-only、16:9／16:10／4:3／density 2。4:3 Greylooksのrounding warmと2x fallbackのclampを目視確認。
- logs: /tmp/osujava-b05-sv-oracle.log、/tmp/osujava-b05-sv-tests.log、/tmp/osujava-b05-sv-build.log、/tmp/osujava-b05-sv-gl.log。
  captures: /tmp/osujava-b05-sv-gl/。一時logs/captureは開発環境の実行記録で永続配布assetではない。

根拠と次の独立実装契約は[timing監査記録](songselect-backend-timing-contracts-20261003.md)を参照。
特にpre-v8はraw SVの逆数を使い、同時刻は元fileの連続batchを参照する。
安定sort済みlistから元順を推測しない。Java fixture一致とstable実機比較を区別する。

Commit:

- 67173a0 — test(difficulty): execute pinned velocity bindables at rounding boundaries
- 9e327ec — fix(difficulty): accept verified slider velocity rounding
- 9a82bd2 — test(song-select): verify rounded velocity ratings through warm reopen


## B05-T2b/c: 同時刻timingと継承NaN

SV境界修復後も承認待ちを挟まず通常Phaseを進行し、同時刻とNaNを独立単位で実装した。
公開decoderのhandleTimingPoint/addControlPoint/flushPendingPoints、ControlPoint/Group/Info/LegacyInfo、
framework SortedList、Slider.ApplyDefaultsToSelfを一時wrapperで原処理のまま実行するoracleへ拡張。
lookupは正確なStartTime、sample用1ms leniencyをdefaultsへ足さない。公式素材やstableコードをappへ移植しない。

- Javaは時刻順sourceの連続同時刻batchでfirst redをBPM、last greenをSVへ採用する。
  greenはredの前後どちらでも優先する。3fixture・36sliderで直前/同時刻/直後を照合した。
  非連続batchを時刻sortだけで推測しないため、parserがtimingOrderChangedを保持する。
  source逆順のsliderはUNSUPPORTED。orderingだけではsource破損やImport warningを捏造しない。
  既存parse値/Gameplay用sort/asset解決は維持。Import→保存→再parse→順序修復後SUCCESSを検証。
- 継承NaNはSV=1、GenerateTicks=false。通常tickだけ省略し、head/repeat/legacy tailは維持する。
  通常greenへ戻るとtickを復帰させ、同時刻NaN/通常greenにも同じbatch規則を適用。
  2fixture・16sliderで元43referenceをbyte不変に保持しながら全中間値/星を照合した。
  red NaNはsource破損のFAILED。実public oracleもInvalidDataExceptionで拒否し、期待値を生成しない。
- preprocessorは同時刻単位v8-5、NaN単位v8-6へ更新。旧UNSUPPORTEDを保持し、新keyでSUCCESSを計算する。
  実workerの再表示cacheとNaNのImport/保存/再起動後reference星を検証した。
- B05専用前処理だけを変更し、Gameplayのtick生成/採点/input/audioは変更していない。
  sourceが並べ替えられたtiming、pre-v8 tick距離、nested tie、未検証path/設定/処理上限は引き続き星なし。

最終検証:

- 公開oracleは45fixture成功。段階ごとの旧38→40→43 referenceは全件byte不変。
- 最終build成功: core147 suites / 1,457 tests、lwjgl3 2 suites / 4 tests、failure/error/skipped 0。
  開始時1,446から合計15cases追加。log /tmp/osujava-b05-timing-final-build.log。
- 最終difficulty-contracts: 12 scenes / 480 PNG / 21,624 scripted transition frames＋navigation/disposal成功。
  同時刻/NaNの星、元順変更の星なし、group/search、cold/warmを実screen/workerで確認した。
  Greylooks/fallback/HD-only ×16:9/16:10/4:3/density2。4:3 Greylooks NaN repeat warm、2x fallback元順変更warmを目視確認。
  log /tmp/osujava-b05-timing-final-gl.log、capture /tmp/osujava-b05-timing-final-gl/。
- oracle logs: /tmp/osujava-b05-coincident-oracle.log、/tmp/osujava-b05-nan-oracle.log、/tmp/osujava-b05-nan-red-reject.log。
- 別単位で[1万sourceのservice/cold/warm計測と登録allocation修復](songselect-backend-service-performance-20261003.md)を実施。
  実library/UI分類/全体の実機比較/HP/Modsは未完了。B05全体やstable 1:1を完了認定しない。

Commit:

- 865b108 — test(difficulty): execute pinned timing decoder and control point lookup
- 43e98d9 — fix(difficulty): respect coincident timing priority and source order
- b9cae56 — test(song-select): cover coincident and reordered timing ratings
- 28267cd — test(difficulty): verify inherited NaN tick suppression against decoder
- a171304 — fix(difficulty): honor inherited NaN without dropping slider repeats
- dd44805 — test(song-select): include inherited NaN cold and warm ratings
