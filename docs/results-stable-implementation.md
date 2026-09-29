# stable結果画面の実装記録

対象は[解析したb20230727.9](results-stable-followup-20260929.md)のosu!standard / NoMod。[不足機能計画](results-missing-data-plan-20260929.md)を小さな変更へ分けて実装する。
実機との画面・音・入力順の比較は未実施。以下の完了項目も1:1一致の認定を意味しない。

## P1: 譜面からの計算入力

- `BeatmapPlayData`へbreak区間、AudioLeadIn、元bytesのSHA-256 / MD5を保持。既存constructorは由来不明の空digestを使い、架空hashを作らない。
- breakの数値名／文字名を読む。重複・重なり・object範囲外の区間は保持し、drainへの解釈はRulesetへ委ねる。逆転・不正数値は診断付きparse errorにする。
- assetの解決後も情報を保持する。BOM・改行を正規化する前のbytesをhash化し、同pathの内容変更を区別する。
- 構文の根拠は[公式osu形式](https://osu.ppy.sh/wiki/en/Client/File_formats/osu_(file_format))。負のAudioLeadInはこのImporterでは不正値として扱う。

検証: parser / library関連テストと`./gradlew --offline build`成功。全840テスト、失敗・error・skipは0。

## P2: 入力と終了条件の基盤

- `GameplayInput`は譜面時刻・sequence・座標・物理ボタン4種を保持。手動とDebug Autoを同じ`GameplaySession.input`へ接続した。判定中は採取済み時刻を使い、順序違反を状態変更前に拒否する。
- 同じ論理actionをマウスとキーで保持しても、片方の解放ではreleaseせず、追加pressも発生させない。cursor描画へも同じ採取時刻を渡す。
- MusicGameClockはAudioLeadIn中の負時刻、audio pause / resume、EOF後の単調進行を扱う。音源位置の後退を判定時計へ伝播しない。
- GameplayScreenは全objectの処理完了と結果用deadlineで遷移し、EOFから`finish()`を呼んで未来objectを強制MISSにしない。既存の末尾＋2500msの待機は維持した。stable固有の終了演出時間として確定した値ではない。
- 残り: slider scheduled判定と入力の時系列統合、fail / abort、replay記録。これだけでreplayのframe非依存性を達成したとは扱わない。

検証: 捕捉後にclockが進んだ入力、同時刻の物理入力、順序違反、lead-in、pause、早いEOF、時計後退、終了gateの回帰テスト。関連テストと`./gradlew --offline build`成功（全847テスト）。

## P3 / P5 / P7–P9: 判定と結果データの第一段階

- NoModのScoreV1加算を独立クラスへ分離。combo加点は加算前combo、難易度倍率はHP / OD / CS / densityからSingle精度とties-to-evenで計算する。drain長は先頭startから末尾startまでの時間からbreakを引いた整数秒。根拠は`0600264d:10f3`、`06003c7a`、`06003c77`および[公開ScoreV1仕様](https://osu.ppy.sh/wiki/en/Gameplay/Score/ScoreV1/osu!)。
- slider head / tick / repeat / tailを30 / 10 / 30 / 30点へ変更。headのtiming判定をaccuracyへ足さず、全nestedの取得率から実際の終端で1個の300 / 100 / 50 / MISSを確定する。tailの欠落は現在comboを切らない。終端はcomboを二重に増やさない。
- combo-setのjudgement時の状態からGeki / Katuを数える。可能comboとPerfectを保存し、MISS数やgradeからPerfectを推定しない。成功したcircle / slider headだけから符号付きhit errorを採取する。URは母分散の平方根×10。
- 結果遷移時に時刻・ScoreState・詳細をimmutable snapshotへ凍結。schema 2は譜面digest、scoringVersion、Geki / Katu / Perfect等を保存する。schema 1は読込可能で既存ファイルを書き換えない。未採取のHP / pass / RPMはnullのまま扱う。保存後の再閲覧用データからhit error / RPMを除き、判定合計から再構成しない。
- 現段階のhealthは未採取。保存形式には上限付きinline health欄のみ追加した。圧縮sidecarとatomicな複数file更新はまだ導入していない。
- 残り: spinnerのstable物理・点数、HP calibration / drain / fail、gradeのSingle境界、異なるscoringVersion間の順位分離、scheduled入力順。現在のversion文字列はこのローカル実装の識別子であり、stable全体の互換性保証ではない。

検証: slider全取得・head欠落・tick欠落・tail欠落・headのみ・全欠落、終端時刻、combo-set重なり、UR採取除外、旧記録保持とschema 2 round-tripを追加。関連テストと`./gradlew --offline build`成功（全863テスト）。保存時に変更不要なimmutable recordは同一instanceを維持し、既存Song Selectの参照契約も維持した。

## 結果画面: skin / 配置 / 演出 / 保存スコア閲覧

- 旧4列カードを、ranking-panel・6判定・grade・Perfect・combo・accuracy・graph frame・Retry・Backへ置換。480高座標と768単位画像を分離し、Version<=1 / >1の通常分岐を実装。画像は既存の許諾済み同梱Greylooksとユーザーskinから読む。stableのassetは抽出していない。
- 判定画像はproviderごとに静止画→frame 0。上位providerのframe 0は下位providerの静止画より優先。破損PNG・過大画像はfallback、@2x密度・ScorePrefix / ScoreOverlap・texture所有と解放を処理する。modern scoreは`06001a26:0140–014b`で上書きされるoverlap=-2を使用。
- 左から500ms刻みのscore桁確定（未確定0–8）、300ms刻みの項目、grade / 高grade加算glow、早送りを接続した。追加解析`060040b0`→`06002b51/52`→`06002b54`で、easing ID 1は`-t*(t-2)`、ID 2は`t*t`と確認。grade本体はID 2、glowはID 1。enum `0200024d`の0–5はXH / SH / X / S / A / Bであり、現NoMod表示のglow対象はSS / S / A / B。
- Enter / Spaceは早送りのみ、Esc / Backは戻る、Retryは演出中も実行。未提供のReplay / F2は表示しない。クリック可能領域は現在画像の矩形。global dispatch / hitbox / hover音 / Backアニメーションとの一致は未認定。
- URはaccuracy領域のhoverで表示。旧保存のGeki / Katuは`-`、未知Perfectは表示しない。HP未採取なら線を作らない。既知HPの描画は末尾から交互に削って100点以下とし、元の時刻範囲で正規化、累積線長で4000msのrevealを行う。1点・同時刻の線は描かない（stableの実機挙動は未確認）。
- Song Selectのscore行から保存結果を開き、元の日時を保持する。再閲覧は保存を行わず、UR / RPMを合計値から再構築しない。再閲覧からRetryすると通常の新プレイになる。

検証: provider優先・破損fallback・密度・二重dispose防止、座標分岐、桁固定とeasing、未知値、母分散、閲覧時非保存、graphの間引きと累積長を回帰テスト化。`./gradlew --offline build`成功（全877テスト、失敗・error・skip 0）。

`xvfb-run -a ./gradlew --offline :lwjgl3:resultsVisualHarness`でproductionのResultsScreenをOpenGL描画。1280×720と1920×1080各6fixture（legacy、modern＋壊れたpanel、保存済み未知値、fail、演出途中、UR hover）を実行。繰り返しEnter / Spaceでも画面を保持し、保存件数は変わらない。captureは`/tmp/osujava-results-implementation-{720,1080}`、生成fixtureは実スコアではない。これはJava側描画確認でありstableとの画像差分比較ではない。

### 残る差と次の実装

1. P4 spinner物理・加点、P6 HP calibration / drain / failをlive Rulesetへ接続する。結果HP / pass / RPMのnullはこの不足を示す。
2. 入力とscheduled判定の全体的な時系列処理、旧new-combo / spinner境界を仕上げる。異なるscoringVersion / digest間の順位分離も未完了。
3. P10 replay記録・再生・F2保存、P11 Mods、ローカル拡張結果、音、scroll containerを実装する。
4. 結果の桁formatは現在HUDの8桁score / accuracy小数2桁、count末尾xを使用しており、復号していないstable format文字列との一致は未認定。header / tooltip装飾、背景暗転、ボタンhitboxはJava側の表示方針である。
5. 新旧skin分岐のglobal / 既定skin例外は未確認。modern選択skinで旧Greylooksの複合panelへfallbackするとラベルと数値が重なることをcaptureで確認した。panel alphaを解析して座標を自動移動する挙動は解析根拠がないため導入していない。別世代の画像を混在させた場合も含め、同一skin条件のstable実機比較が必要。

## P5追補: gradeのSingle境界

`060012ec:000c–002b`の判定率はSingle除算、その後`:0072–008a / 00c8–011b`でDoubleへ昇格して0.9 / 0.8 / 0.7 / 0.6 / 0.01のDouble定数と比較する。例えば300率80%はSingleで0.8000000119…となり、MISSなしならAになる。60%もSingleで0.6000000238…となりCになる。整数比で閾値を比較する旧実装と同一ではない。

新scoringVersionの記録と結果画面にはこの分岐を適用し、旧形式の記録は従来gradeを維持した。失敗が既知なら結果画面のFが優先。HD / FLの銀gradeはMod対応時に追加する。11境界caseと旧・新recordの表示整合を検証し、`./gradlew --offline build`成功（全889テスト）。この数式の静的照合と、実機での境界譜面検証は区別する。
