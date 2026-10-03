# B05: ローカルDifficulty backend（2026-10-03）

## 数値計算の根拠と範囲

参照は公開lazer tag `2023.815.0`、commit `4e96853c7543f80a1b822ccd381943c7377543d7`、
`OsuDifficultyCalculator.Version=20220902` に固定する。
[calculator](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/OsuDifficultyCalculator.cs)、
[preprocessing](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/Preprocessing/OsuDifficultyHitObject.cs)、
[stacking](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapProcessor.cs)、
[skills/evaluators](https://github.com/ppy/osu/tree/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty)
を根拠に独立実装する。現行2026のalgorithm、またはstableの全譜面との同値は主張しない。

初期対応は **NM、mode 0、format v6以降、circle/spinnerのみ、CS/AR/OD 0–10、StackLeniency 0–1**。
Slider・他mode・pre-v6・未検証の範囲はUNSUPPORTEDとして数値を供給しない。
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
自作12fixtureの値を生成した。stable asset抽出なし。app/build/testのnetwork接続なし。
通常JUnitはcheck-in済み値だけを使用する。

許容差は比較前に固定した。star/aim/speedはabsolute 1e-9、object/sectionはabsolute 1e-7＋relative 1e-9、
stack高さは完全一致。empty/single/pair/three/jumps/stream/rhythm/simultaneous/stacks/spinner/gaps/
fractional settings/coordinatesについて一致した。

## 残件

- Slider path、tick/repeat/tail、lazy cursor/travel/minimum jumpの公開reference中間値と照合して対応を拡張する。
- pre-v6 stacking、範囲外settings、Mods別計算の検証。
- stable実機での数値比較、Difficulty group境界・NM/Mod適用範囲の観測。
- 全B05受入はslider fixture等を含む。circle/spinnerの照合だけでB05全体完了とはしない。

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

## 最終検証と変更ファイル

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

1. **Sliderを含むchartは全体がUNSUPPORTED**。circle部分だけを計算して混合chartの星を作らない。
   oracleのSlider adapterを本物の公開path/defaults/nested/timingに拡張し、linear/Bezier/perfect/Catmull、
   SV/timing変更、repeat/tick/tail、stack、lazy end/travel/minimum jumpを中間値から照合する。
   Javaの既存SliderPath/Timing/EventGeneratorは、契約が一致する部分だけ再利用する。
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
