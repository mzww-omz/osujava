# osu!java

Java 21、libGDX、LWJGL3で作る完全ローカルのリズムゲームです。osu!stableの画面と操作感を参考にしつつ、実装は独立しています。

このアプリはBancho、osu!公式サーバー、osu! APIへ接続しません。アカウント、オンラインランキング、マルチプレイもありません。実行時のネットワーク通信は行いません。Gradleの初回buildでは依存ライブラリを取得するためにMaven Centralへアクセスします。

## 起動

必要なものはJava 21のJDKです。Gradleはwrapperを同梱しています。

macOS / Linux:

~~~sh
./gradlew lwjgl3:run
~~~

Windows:

~~~bat
gradlew.bat lwjgl3:run
~~~

macOSではlauncherがGLFWの非同期起動設定を使います。

## 遊び方

1. Main MenuでPlayを選びます。
2. Song SelectのImport .osz / .osuから譜面を選びます。
3. Beatmap SetとDifficultyを選択してPlayを押します。
4. HitCircleの位置を曲に合わせてクリックします。
5. 曲が終わるとResultsを表示します。

Song Selectでは、1つの.oszに入った複数Difficultyを1つのBeatmap Setとして表示します。taiko / catch / maniaのmode情報も保持して表示しますが、Gameplay対応はosu!standardのHitCircleだけです。Slider、Spinner、Mods、Replay、Skin、Editor、オンライン機能は未実装です。

Importしたファイルはユーザーのホームディレクトリ下の.osujava/libraryへ展開・コピーします。現時点でLibraryの一覧はメモリ上にあり、アプリ再起動後は譜面をもう一度Importしてください。

## テスト

~~~sh
./gradlew test
~~~

parser、archiveのパス検証、複数DifficultyのImport、アセット関連付け、Rulesetの判定、Score/accuracy、Playfield座標変換をJUnit 5で確認します。

## 構成

- core: Beatmapモデル、parser、Importer、Library、Game Clock、Ruleset、Gameplay判定と画面
- lwjgl3: desktop launcherとファイル選択ダイアログ
- docs/architecture.md: データの流れと各層の責務
