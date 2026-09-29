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
