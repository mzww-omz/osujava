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

### 開発確認用 Debug Auto

Song SelectでDifficultyを選び、`F6`を押すとDebug Auto Playを開始します。検索欄が入力中の間はショートカットは動作しません。`Enter`またはPlay Cookieは通常のManual Playです。Debug Autoは既存のGameplay入力・判定経路に入力を送り、カーソルには目視確認用のcrosshairを表示します。Resultsには`AUTO / DEBUG`と表示されます。これはModsではなく、将来の通常プレイ用Local Rankingへ含めない開発用の実行種別です。Gameplay中の`Escape`でSong Selectへ戻れます。

終了はウィンドウの閉じるボタン、macOSのCmd+Q、Windows / LinuxのCtrl+Qで行えます。

Song Selectでは、1つの.oszに入った複数Difficultyを1つのBeatmap Setとして表示します。taiko / catch / maniaのmode情報も保持して表示しますが、Gameplay対応はosu!standardのHitCircle、Slider、Spinnerです。Mods、Replay、Skin、Editor、オンライン機能は未実装です。

Importしたファイルはユーザーのホームディレクトリ下の.osujava/libraryへ展開・コピーします。Library indexも同じ場所へ保存され、アプリ起動時に読み込みます。Importした譜面は再起動後もSong Selectに表示され、そのままGameplayを開始できます。同一beatmap setをもう一度Importすると、既存のローカルデータとindex entryを更新します。保存方式とset識別方法は[docs/architecture.md](docs/architecture.md)を参照してください。

## テスト

~~~sh
./gradlew test
~~~

buildは次のコマンドで実行します。

~~~sh
./gradlew build
~~~

実行時依存ライブラリを含む単一の実行可能JARは、次のコマンドで作成できます。

~~~sh
./gradlew lwjgl3:executableJar
~~~

`lwjgl3/build/libs/osujava-0.1.0-all.jar` が生成されます。Java 21で次のように起動します。

~~~sh
java -jar lwjgl3/build/libs/osujava-0.1.0-all.jar
~~~

Windowsでは `./gradlew` を `gradlew.bat` に置き換えてください。

parser、archiveのパス検証、複数DifficultyのImport、アセット関連付け、Library indexの保存・再読込・破損entryのスキップ、Rulesetの判定、Score/accuracy、Playfield座標変換をJUnit 5で確認します。

## 構成

- core: Beatmapモデル、parser、Importer、Library、Game Clock、Ruleset、Gameplay判定と画面
- lwjgl3: desktop launcherとファイル選択ダイアログ
- docs/architecture.md: データの流れと各層の責務
