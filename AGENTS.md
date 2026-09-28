1. osujavaは完全ローカルアプリとし、Bancho・osu! API・公式WebSocket等のproduction serviceへ接続しないこと。  
2. Import / Gameplay / Renderer / Ruleset / GameClockの責務を分離し、Debug Autoも通常Input APIのみを使うこと。  
3. osu!互換実装では推測に頼らず、osu! wiki、公開lazerソース、stableのブラックボックス観測を根拠に独立実装すること。  
4. stableの観測・計測・比較・内部調査は許可するが、公式asset抽出、保護機構の回避は禁止する。  
5. 変更は要求範囲に限定し、小さくテスト可能に実装する。不要な大規模リファクタリングや巨大な抽象化を行わないこと。  
6. バグ修正では可能な限り回帰テストを追加し、関連テストと原則Gradle buildを実行して失敗を解消すること。  
7. archive展開では絶対パス・`..`・Windows形式の危険パスを拒否し、壊れた譜面はアプリ終了ではなくImportエラーとして扱うこと。  
8. ファイルを変更した場合はdiff確認後、無関係な既存変更を含めず論理単位でGit commitし、未コミットの自分の変更を残さないこと。  
9. 最終報告には変更内容・設計判断・テスト/build結果・commit SHA・commit messageを記載すること。変更なしの調査はcommit不要。  
