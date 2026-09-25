# osu!java

Java 21、libGDX、LWJGL3で作る完全ローカルのリズムゲームです。osu!stableの画面と操作感を参考にしつつ、実装は独立しています。

このアプリはBancho、osu!公式サーバー、osu! APIへ接続しません。アカウント、オンラインランキング、マルチプレイもありません。実行時のネットワーク通信は行いません。Gradleの初回buildでは依存ライブラリを取得するためにMaven Centralへアクセスします。

## 起動

必要なものはJava 21のJDKです。Gradleはwrapperを同梱しています。Gradle daemonは[`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)でJava 21を選び、各moduleのcompile/testもJava 21 toolchainを使います。システム既定のJavaが27など新しい版でも、`JAVA_HOME`を毎回切り替える必要はありません。

この設定がない状態では、Gradle 8.14.3に含まれるGroovyがJava 27のclass file version 71を解析できず、build scriptのsemantic analysisで失敗します。daemonをJava 21で動かすことで回避します。Gradleや依存ライブラリの更新は必要ありません。

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
4. HitCircleはタイミングに合わせてクリックします。左/右クリックまたはZ/Xキーで操作できます。Sliderは頭を押してから、押したままカーソルでボールを追います。
5. 曲が終わるとResultsを表示します。

終了はウィンドウの閉じるボタン、macOSのCmd+Q、Windows / LinuxのCtrl+Qで行えます。

Song Selectでは、1つの.oszに入った複数Difficultyを1つのBeatmap Setとして表示します。taiko / catch / maniaのmode情報も保持して表示しますが、Gameplay対応はosu!standardのHitCircleと基本的なSliderです。Spinner、Mods、Replay、Skin、Editor、オンライン機能は未実装です。

Importしたファイルはユーザーのホームディレクトリ下の.osujava/libraryへ展開・コピーします。Library indexも同じ場所へ保存され、アプリ起動時に読み込みます。Importした譜面は再起動後もSong Selectに表示され、そのままGameplayを開始できます。同一beatmap setをもう一度Importすると、既存のローカルデータとindex entryを更新します。保存方式とset識別方法は[docs/architecture.md](docs/architecture.md)を参照してください。

## テスト

~~~sh
./gradlew test
~~~

buildは次のコマンドで実行します。

~~~sh
./gradlew build
~~~

parser、archiveのパス検証、複数DifficultyのImport、アセット関連付け、Library indexの保存・再読込・破損entryのスキップ、Rulesetの判定、Score/accuracy、Playfield座標変換をJUnit 5で確認します。

## 構成

- core: Beatmapモデル、parser、Importer、Library、Game Clock、Ruleset、Gameplay判定と画面
- lwjgl3: desktop launcherとファイル選択ダイアログ
- docs/architecture.md: データの流れと各層の責務
