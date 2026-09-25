# 開発ルール

- このプロジェクトは完全ローカルアプリです。Bancho、osu!公式サーバー、osu! APIへ接続する機能を追加しないでください。
- .osz / .osuのImportとGameplayを分離してください。ImporterがファイルをLibrary用のローカル構造へ取り込み、GameplayはBeatmap/Library modelを使います。Gameplayからarchiveを直接読み込まないでください。
- RendererとGameplay logicを分離してください。RendererはGameplayStateを描画し、Scoreや判定状態を変更しません。
- ゲーム時刻は共通のGameClockを通してください。Gameplay内で個別に実時間を読む処理を増やさないでください。
- Ruleset固有処理はRuleset内へ置いてください。新しいmodeを追加するときにosu!standardの判定を共通基盤へ混ぜないでください。
- Debug Autoは通常のGameplaySession Input APIだけを通し、Score、judgement、HitObject状態、Slider event、Spinner progressを直接変更してはいけません。
- 小さく、テスト可能な変更を優先してください。巨大な抽象化やDI frameworkは導入しないでください。
- Git commitは細かい論理単位で作成してください。大量の変更を最後の1commitにまとめないでください。機能追加と大規模なリファクタリングは別commitに分けてください。
- 原則としてcommit前にbuildと関連testを確認してください。生成物、Libraryの譜面データ、不要な一時ファイルをcommitしないでください。
- archive entryは展開前に検証してください。特に絶対パス、..、Windows形式のパスを拒否し、Zip Slipを防いでください。
- 壊れた譜面やarchiveはImportエラーとして扱い、アプリ全体を終了させないでください。
