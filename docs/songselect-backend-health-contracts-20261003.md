# B06: HP・失敗・結果データの契約（2026-10-03）

## 今回進めた小単位と未完了範囲

B06-T1は既存ScoreDetailsのhealth列を保存可能な契約へ整理する単位。
時刻は非減少、同時刻の複数点は許可、値はfiniteの0–1、最大100,000点。
nullは未収集、emptyは既知の空列として区別し、破損保存データを正常なgraphへ補完しない。
実装commitは`acc454c`、oversized列のcopy前拒否は`817d023`。この保存契約だけからGameplayのHPを生成することはない。

B06の独立した増減算術として、Ruleset内の小部品
[OsuHealthValues](../core/src/main/java/dev/osujava/ruleset/osu/OsuHealthValues.java)と
[回帰テスト](../core/src/test/java/dev/osujava/ruleset/osu/OsuHealthValuesTest.java)を追加した。
実際のHP H、上限なしの回復余力U、HP設定による判定増減、normal／combo setの二つの回復倍率を分ける。
既存Judgement、OsuScoreEvent、OsuComboSets.Variantを使用し、combo分類を重複計算しない。
初期HPはcallerが明示的に与える。calibration用のreset=200を実プレイ初期充填へ流用しない。

算術実装commitは`a48e743`。この部品は**まだGameplaySessionへ接続していない**。calibration、drain rate生成、runtimeの時刻・break・
spinner drain、failチェック順、outcome freeze、graph採取は次の小単位。
通常プレイのpassed／healthは引き続き未収集のnullであり、仮の固定drainや疑似graphを供給しない。
B06完了、stable実機HP比較、NF対応完了とは扱わない。

## 根拠を混同しない

| 根拠 | 確認できる契約 | 今回の位置づけ |
| --- | --- | --- |
| [公式Health wiki](https://osu.ppy.sh/wiki/en/Gameplay/Health) | standardは時間drain、各判定・slider parts・spinnerによる増減、katu/geki回復、breakでdrain停止 | 増減の種類の根拠。数値係数やframe内順序は確定できない |
| stable b20230727.9の既存read-only IL研究 | H/U/B、判定増減表、係数探索、HP<=0のfail入口、pass flag、graph採取 | [追加解析第2–4節](results-stable-followup-20260929.md)を独立算術の主根拠にする。runtime実測とは区別 |
| 公開lazer 2023.815.0 | generic Judgement＋DrainingHealthProcessor、Playerのhealth→score適用、fail lifecycle | lifecycleと比較材料。stable HP算術の同値oracleではない |
| 後年公開legacy processor | stableを近似する別のcalibration・判定増減 | 原処理を実行する比較oracle候補。指定stableの全契約と一致したことは意味しない |

2023参照commitは`4e96853c7543f80a1b822ccd381943c7377543d7`、frameworkは
`3365c86f769cb0ed84a10c5c96313a73e552dc2d`。B05の固定calculator versionと、将来のHP参照versionは別に記録する。
後年legacy sourceは既存研究で使用した`db635ed6bdc1c9ec65f22fc0675824d92601cba9`に固定する。
mutable masterをnumerical expectedの生成元にしない。

## 公開2023 HPをstable HPとして実装できない理由

[Ruleset.CreateHealthProcessor](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Ruleset.cs)
はgeneric [DrainingHealthProcessor](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Scoring/DrainingHealthProcessor.cs)
を生成する。[OsuJudgement](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Judgements/OsuJudgement.cs)
は最大判定をGreatにするだけで、[generic Judgement](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Judgements/Judgement.cs)
の回復値を使用する。300=.05、100=.025、50=.0025、MISS=−.1などはstableのH/200と同じ表ではない。
[OsuModClassic](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Mods/OsuModClassic.cs)
もHP processorをstableへ置換しない。

この2023 DrainingHealthProcessorはperfect-play最低HPを目標にdrain rateを探索する。
実際の目標定数はHP 0/5/10で.99/.9/.4であり、class冒頭の古い説明値を定数と混同しない。
stableはH/U、combo回復倍率C、normal回復倍率Nを使う別の探索を持つ。
また公開2023のruntime Updateは現在時刻がno-drain区間内ならそのupdate全体を省く。
外部updateがbreak境界を大きく跨ぐ場合まで、そのままframe不変の契約として使わない。

[HealthProcessor](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Scoring/HealthProcessor.cs)
は判定適用時に増減とfail条件を確認し、fail vetoを持つ。
[Precision.AlmostBigger](https://github.com/ppy/osu-framework/blob/3365c86f769cb0ed84a10c5c96313a73e552dc2d/osu.Framework/Utils/Precision.cs)
により既定の判定境界も単純なH<=0とは違う。これをstableのゼロ判定へ読み替えない。

## 後年公開legacy sourceも全体の同値oracleではない

[OsuLegacyHealthProcessor](https://github.com/ppy/osu/blob/db635ed6bdc1c9ec65f22fc0675824d92601cba9/osu.Game.Rulesets.Osu/Scoring/OsuLegacyHealthProcessor.cs)
には、H/200として300=.03、tick=.015、head/repeat/tail=.02、spinner=.0085/.01等、
static stable表と一致する部分がある。MISSのHP依存補間も照合材料になる。
ただし100=.011、50=.002はHP0のstable回復補間とは一致せず、combo set倍率Cによる追加回復もない。

[LegacyDrainingHealthProcessor](https://github.com/ppy/osu/blob/db635ed6bdc1c9ec65f22fc0675824d92601cba9/osu.Game/Rulesets/Scoring/LegacyDrainingHealthProcessor.cs)
はlegacyへ近づける意図を明記するが、pre-v8 breakで差がある。
stableのcombo set終端基準とC倍率を持たないため、最終N／drain rateが同じとは限らない。
この原処理をtemporary oracleで実行する場合は、**一致する算術部分のreference**と、
**意図的に異なるcalibration結果の比較**を分ける。
Java expectedをpubliclegacyへ合わせるためにstatic stable表を改変しない。

## 独立算術部品の受入範囲

`OsuHealthValues`はNMのeffective HP 0–10と有限の正C/Nを受け取る。Mod適用は担当しない。
positive回復はHを0–200へclampし、Uには上限を設けない。negative効果／明示drainは両方を下限0にする。
invalid値やoverflowは状態の一部を変更する前に拒否する。

| 判定 | Hへの増分（C/Nは入力された係数） |
| --- | --- |
| 300 | 6N |
| 100 | HP 0/5/10で17.6/2.2/2.2を補間しN倍 |
| 50 | HP 0/5/10で3.2/.4/.4を補間しN倍 |
| object MISS | HP 0/5/10で−6/−25/−40を補間、Nで拡大しない |
| normal set end／Katu／Geki | 基本回復へ6C／10C／14Cを加算 |
| slider tick成功／head・repeat・tail成功 | 3N／4N |
| slider parts失敗 | HP 0/5/10で−4/−15/−28を補間、tailも同じHP減少 |
| spinner半回転／通常回転／bonus | 1.7N／1.7N／2N |

この表は[既存static研究](results-stable-followup-20260929.md#31-共通集計器のhp効果)の独立実装。
NM補間のdouble演算順を再確認して保持した。set-end variantは既存ComboSetsの確定値を入力する。
MISS+Geki等の成立しない組合せをAPIで拒否する。
spinner半回転と通常回転は同じHP効果だが、score eventの値や発生頻度は別の責務。
この算術だけでHP減少の開始時刻・fail・graphを決定しない。

回帰テストはHP 0/2.5/5/7.5/10の全object／nested失敗、C/Nの独立性、combo variant、slider成功、
spinner回復、cap超過後のU、ゼロ後の純粋算術、explicit初期値、drain分割、invalid値／overflow時の
状態不変を含む。10 casesの単独JUnitは成功。
ログ: `/tmp/osujava-b06-health-values-tests.log`。最終build／統合テストはrootのB06変更記録にまとめる。

## Runtime・結果・保存の現在の受け口

- OsuGameplaySessionは時刻付き通常Input API、circle／slider／spinner判定、ScoreV1、combo variantsを持つ。
  `resultDetails()`のpassed／healthはnull。`finish()`は残りobjectをMISSへ確定する既存操作なので、
  fail／abortによる終端確定として呼んではいけない。
- GameplayStateのcompletedはallJudgedであり、passedとは別。
  GameplayCompletionはcompleted＋outro時刻を待ち、音源EOFをpass条件にしない。
  GameplayScreenはResultsへのsnapshotを一度だけ作る。manual score保存とDebug Auto非保存、
  GameplayAttemptのCOMPLETED／FAILED／ABORTED履歴が既にある。
- [ResultsPresentation](../core/src/main/java/dev/osujava/ui/ResultsPresentation.java)は明示passed=falseを既に
  Image.Fへ変換し、ResultsSkinAssetsはranking-Fをロードする。legacy nullをFへ変更しない。
  ResultsHealthGraphの間引き／revealも既存の表示受口として使う。
- Song Selectのsmall grade画像でFを使用する契約は未確定。Resultsのranking-F確認だけを根拠に
  ranking-F-smallを追加しない。failedを完走色に数えない処理と、grade assetのnative契約は別に検証する。
- [公開Player](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Screens/Play/Player.cs)
  はhealthの結果処理→scoreの結果処理の順で、fail通知より後に当該判定がscoreへ入る経路を持つ。
  fail snapshotに原因判定を含める必要はあるが、残りの未来objectを一括MISSにする根拠ではない。
  [ScoreProcessor.FailScore](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Scoring/ScoreProcessor.cs)
  はpassed=falseとFを記録する。Javaのoutcomeと判定比率gradeの責務も分離する。

実runtimeではH、U、perfect-play基準B[i]、結果graphを別に保持する。
stable graphは`min(1,H/B[参照object])`でありH/200ではない。
H=120、B=160なら.75となる例はstatic算術の例で、native画面測定値ではない。
採取の判定mask、nested失敗時の参照object、保存時の2000ms間引きも独立した未完了契約である。

## Nativeの調査手段と今回の確認範囲

対象は既存installation `/home/coder/workspace/b20230727.9/osu!.exe`。
SHA-256 `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
[tools/stable_results_inspect.py](../tools/stable_results_inspect.py)はhashを照合し、CLR metadata／ILをread-onlyで読む。
暗号化文字列を解読せず、assemblyを実行せず、embedded resourcesも取り出さない。

今回は以下を再確認した。ログはGit外に置き、nativeのコード／IL全文をrepositoryへ追加していない。

| token | 再確認した算術／分岐 |
| --- | --- |
| 060027f3 | calibration reset H=U=200 |
| 060027f0／060027f1 | 減算は両方下限0、回復はHだけ上限200、Uは上限なし |
| 0600066f | effective HPを用いるdoubleの区分補間と演算順 |
| 0600264d | object／nested／spinnerの増減定数とnormal／combo倍率 |
| 060021d2 | H<=0でfail入口、NFなどによる除外。入力との同frame順序はこのmethodだけでは未確定 |

再調査例:

```sh
/tmp/osujava-songselect-audit-venv/bin/python tools/stable_results_inspect.py \
  '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 060027f3 060027f0 060027f1 0600066f 0600264d 060021d2 \
  > /tmp/osujava-b06-health-native-new-run.log
```

今回のread-onlyログ:
`/tmp/osujava-b06-health-native-readonly.log`、
`/tmp/osujava-b06-health-arithmetic-native-readonly.log`、
`/tmp/osujava-b06-health-events-native-readonly.log`。
dnfile 0.18.0／dncil 1.0.2でhash確認済み。

[既存offline起動試験](songselect-stable-spec.md#remaining-observation-protocol)はnetwork namespaceとWine／Xvfbを使ったが、
Song Selectへ到達しなかった。この結果をHPの実機観測へ読み替えない。
今回stableを起動していない。起動制限を解消するためのprotection bypass／patch／production接続も行わない。

## 次の実装と計測の受入手順

1. **Calibrationの宣言subsetを固定する。** まずNM circle-only、HP 0/5/10、正の開始gap、
   breakなしでd/C/N/Bを出す独立純粋計算を検証する。既存static研究のset終端基準・最終基準・
   uncapped回復基準を含める。publiclegacyだけのN/drain結果をstable expectedへ代用しない。
   収束上限、cancel、空・単一・極端densityのunsupported理由を先に定める。
2. **Runtime順序を確定する。** 同時刻のdrain→入力→scheduled判定→failチェックを明示する。
   stableでH=0とhitが同時刻の場合、drop境界にcircleがある場合、長frameがbreakを跨ぐ場合を観測する。
   未確定の順序をframe単位の思いつきで実装しない。
3. **対象subsetを段階拡張する。** combo終端variant、slider head/tick/repeat/tail、長slider、
   spinnerの途中回復とdrain .25、通常break、重複break、overlap、v6/v7対v8の順に独立fixture化する。
   既存score／combo／Input結果が変わらないことも同時に検証する。
4. **Outcomeを接続する。** normal completion、HP fail、Esc abort、Retryを区別し、
   原因判定を含むscoreをfreezeする。fail時に未来MISSを増やさない。abortをfailed scoreとして保存しない。
   `passed=null`の過去scoreや未対応HP chartをfalse／trueへ補完しない。
5. **Graph採取と保存を接続する。** B[i]の参照先と判定maskを確定して採取し、finite／時刻順／上限を守る。
   live Results、保存後、再起動後で実収集列を比較する。保存圧縮と描画間引きは別にする。
6. **固定時刻列でframe不変を検証する。** 30/60/144fps相当と不規則updateを同じ通常Input API列で実行し、
   HP、fail時刻、score、combo、outcomeを照合する。pause中は譜面時刻を進めず、短音源EOF後も自然判定する。
   Debug Autoは通常API経由・score／history非保存を回帰確認する。

Native観測用chartはこのrepositoryで自作し、音源なし／許可された無音音源、独立measurement skinを使う。
観測対象の例と記録する値は以下。数値がまだないケースを「一致」と記録しない。

| 自作fixture | 記録するnative値／イベント |
| --- | --- |
| 一定間隔circle、HP 0/5/10、同じ300/100/50/MISS列 | 各入力譜面時刻、直前／直後HP、zero到達、set-end bonus。calibration d/C/N/Bは取得根拠を分ける |
| 長gap＋同時刻hit／zero境界のhit | drain開始・停止、failとhitの順序、fail原因判定を含む保存score |
| breakなし／あり、object間のbreak、二重break、v7／v8対 | break前後のHP軌跡と再開時刻。calibrationとruntimeの差を別に記録 |
| slider head／tick／repeat／tailを個別に落とす、overlap | HP増減、graph採取有無、B参照object。tailのHP減少とcombo維持の両方 |
| spinner成功／失敗／clear後継続 | .25 drain gate、途中回復、bonus、終了判定の増減 |
| fail後Retry／Esc、pause／resume、音源EOF前後 | outcome、score freeze、履歴の終端、画面遷移・audio停止 |

Build／skin／chart hash／入力列／fps／framebuffer寸法を保存し、同条件Javaへ再投入する。
HPバーのpixelsから読む値は観測のpixel誤差を明記し、double精度のreferenceと同じ許容差を主張しない。
nativeが自作playから保存するhealth graphは比較データとして扱えるが、公式assetsを抽出する作業と混同しない。
取得できない内部係数は未知のままとし、Java自身の計算結果をnative expectedへ循環させない。

Java側の描画受入には既存 `:lwjgl3:resultsVisualHarness`を利用できる。
legacy-live／modern-live／saved-unknown／failed／partial-animation／accuracy-tooltipの6自作snapshotを描画するが、
これはGameplayのHP生成やstable実機比較の証拠ではない。
実収集HPの接続単位ではこのharnessを拡張し、16:9／16:10／4:3、fallback／skin／@2x、保存前後を確認する。

原則Gradle buildと関連JUnitを各小単位で実行し、numeric契約、lifecycle、storage、GLの証拠を分けて残す。
NFはfail抑制だけでなくHP継続・score係数・終了条件・保存ModsまでB07で検証してから有効にする。


## 初期lifecycle/UI修復の検証結果

`fc2f478`は明示passed=falseのscoreを完走row色/best badgeから除外し、低scoreの完走記録を隠さない。
失敗score自体はランキングと保存Resultsで引き続き閲覧できる。
`256ce35`はlocal provenanceの先頭へFailedを表示。ratio gradeを変えず、Resultsは既存のF受口を使用する。
これはosu!javaのlocal失敗表示であり、nativeの未確認small-F assetを再現したとの主張はない。
`0188f0e`はattemptのUNKNOWN/null終端を拒否し、history保存失敗時にended flagを立てず復旧retryを可能にした。

health保存契約・破損/再起動、failed colour/badge/browser、Results F、履歴復旧を回帰した。
最終`./gradlew build --offline --console=plain`はcore1,486＋desktop4＝1,490 tests成功、failure/error/skip 0。
B05のGL12scenes/528PNGも成功。HP runtime生成のGL受入・同入力列30/60/144fps不変は次単位であり、
今回の自作snapshotによる表示テストから実HPを収集済みとは扱わない。
