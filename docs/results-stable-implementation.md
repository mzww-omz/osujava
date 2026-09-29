# stable結果画面の実装記録

対象は[解析したb20230727.9](results-stable-followup-20260929.md)のosu!standard / NoMod。[不足機能計画](results-missing-data-plan-20260929.md)を小さな変更へ分けて実装する。
実機との画面・音・入力順の比較は未実施。以下の完了項目も1:1一致の認定を意味しない。

## P1: 譜面からの計算入力

- `BeatmapPlayData`へbreak区間、AudioLeadIn、元bytesのSHA-256 / MD5を保持。既存constructorは由来不明の空digestを使い、架空hashを作らない。
- breakの数値名／文字名を読む。重複・重なり・object範囲外の区間は保持し、drainへの解釈はRulesetへ委ねる。逆転・不正数値は診断付きparse errorにする。
- assetの解決後も情報を保持する。BOM・改行を正規化する前のbytesをhash化し、同pathの内容変更を区別する。
- 構文の根拠は[公式osu形式](https://osu.ppy.sh/wiki/en/Client/File_formats/osu_(file_format))。負のAudioLeadInはこのImporterでは不正値として扱う。

検証: parser / library関連テストと`./gradlew --offline build`成功。全840テスト、失敗・error・skipは0。
