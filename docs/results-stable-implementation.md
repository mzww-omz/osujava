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
