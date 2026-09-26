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

Song Selectでは、1つの.oszに入った複数Difficultyを1つのBeatmap Setとして表示します。taiko / catch / maniaのmode情報も保持して表示しますが、Gameplay対応はosu!standardのHitCircle、Slider、Spinnerです。Mods、Replay、Editor、オンライン機能は未実装です。

### HitCircleのSkin画像

任意のローカルSkinディレクトリを指定できます（相対パスは起動時のworking directory基準）。選択UIはありません。

~~~sh
./gradlew lwjgl3:run -PskinDirectory="/path/to/skin"
~~~

実行可能JARでは `java -Dosujava.skinDirectory="/path/to/skin" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar` を使います。オプションを外して起動すると従来のベクター描画へ戻ります。

`.osk`を直接指定する開発確認用オプションもあります。

~~~sh
./gradlew lwjgl3:run -PskinArchive="/path/to/skin.osk"
~~~

実行可能JARでは `java -Dosujava.skinArchive="/path/to/skin.osk" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar` を使います。起動時に一度Importし、`~/.osujava/skins/<SHA-256 ID>/`へ保存したディレクトリを既存の`OsuSkinAssets`へ渡します。IDは展開後のファイル名と内容から生成し、同名でも内容が異なるSkinは別保存、同じ内容は再利用します。subdirectoryも保持しますが、対応画像は従来どおりSkinディレクトリ直下から解決します。

ZIPのcentral directoryと各entryのサイズ・CRCを検証し、絶対パス、`..`、Windows drive path、backslash、正規化後の重複entryを拒否します。圧縮ファイル・展開後の合計はそれぞれ256 MiB、entry数は10,000までです。一時ディレクトリへ展開して成功時のみ配置し、失敗時はcleanupします。`.osz`とも安全な展開helperを共有します（`.osz`のサイズ上限は従来の1 GiB、entry数上限は10,000）。

両方のオプションがある場合は`.osk`を優先します。Import失敗はログへ出し、`skinDirectory`指定があればそこへfallbackし、なければベクター描画で起動を続けます。Gameplay中にはarchiveを読みません。Skin選択UIはありません。

対応するのはosu!standardのHitCircleおよびSlider始点・終点用 `hitcircle.png`、`hitcircleoverlay.png`、`approachcircle.png` の3画像です。画像ごとに `@2x.png` を優先し、density=2で論理サイズを求めます。128論理pixelを基準直径としてCircleSizeとPlayfieldViewportの倍率を掛け、中心に配置します。本体とApproach Circleは既存のcombo colourでtintし、overlayは元の色で重ねます。既存の出現・Approach timingは維持します。画像なし、ディレクトリなし、読み込み失敗は各画像単位で従来の描画へfallbackします。

色付けと基準サイズはosu!lazerの [LegacyMainCirclePiece](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyMainCirclePiece.cs)、[LegacyApproachCircle](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyApproachCircle.cs) を参照しています。

`OsuSkinAssets` はScreen作成時にTextureを一度読み込み、GameplayScreen終了時にdisposeします。数字Textureも同じ管理に含めます。色設定の `GameplaySkin` と画像ファイル解決の `SkinAssetResolver` は別責務です。Slider始点・終点はhitcircle画像を共有し、始点のApproach Circleも対応します。終点の既存サイズ・出現タイミングは維持します。Slider専用始点・終点画像とSlider Ballも対応します。

HitCircleとSlider始点のcombo numberは、Skin直下の `skin.ini` の `[Fonts]` から `HitCirclePrefix` と `HitCircleOverlap` を読みます。省略時はそれぞれ `default` と `-2` です（[osu!lazer LegacySkinExtensions](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkinExtensions.cs)）。`[General]` の `HitCircleOverlayAboveNumber`（typo互換 `HitCircleOverlayAboveNumer`）も対応し、既定はoverlayがnumberより上です。正規名があればtypo名より優先します。Slider Body用に `[Colours]` の `SliderBorder` と `SliderTrackOverride` も解析します。他のFonts・Colours項目、Cursor設定、hitsound設定は解析しません。

数字は `<prefix>-0` ～ `<prefix>-9` を各々 `name@2x.png` → `name.png` の順で探索し、densityで割ったnative logical width/heightを使います。桁のadvanceは `width - overlap`（正値で重なり、負値で間隔が広がる）で、数字全体をCircle中央に配置します。画像のアスペクト比を維持し、倍率は `0.8 × radius / 64 × viewport scale` です（[OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)、[DrawableHitCircle](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableHitCircle.cs)、[OsuHitObject](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/OsuHitObject.cs)）。Slider終点には数字を表示しません。

Skin未指定、skin.iniなし・読み込み失敗、数字の一部不足・読み込み失敗ではcombo number全体を既存フォント描画へfallbackします。custom prefixとdefault画像の混在は行いません。prefixはディレクトリ直下のbasename（英数字・Unicode文字・空白・`_`・`-`）に限定し、パス指定はfallbackします。

Slider Ballは `sliderb0.png` からのanimationを `sliderb.png` より優先します。各frameは `@2x.png` → `.png` の順で解決し、0から最初の欠番までの連番のみ使用します。frame 0がなければ静止画像を使い、画像なし・読み込み失敗ではBall全体を既存ベクター描画へ戻します。利用可能なSkin BallにベクターBallは重ねません。frameはScreen作成時に一度ロードし、共有Textureもidentityで管理して1回だけdisposeします。

animationは `max(0.15 / SliderTiming.velocity() × 1000/60, 1000/60)` ms/frameでloopします。共通Gameplay時刻と `startTime - preempt` を基準に直接frame番号を求め、deltaは積算しません。native pixel sizeをdensityで割り、`radius / 64 × viewport scale` を掛けます。縦横を同じ倍率で描画し、lazerと同じく384論理pixelを越える画像は各軸の中央をcropします。色は白（元画像の色）で、`[Colours] SliderBall`、`AllowSliderBallTint`、combo/accent tintは未対応です。位置・path・progress・repeat・判定・Autoは既存処理を使います。

解決・timing・サイズの根拠はosu!lazerの [OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)、[LegacySliderBall](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySliderBall.cs)、[LegacySkinExtensions](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkinExtensions.cs)、[DrawableSlider](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSlider.cs)を確認しています。`sliderb-nd`、`sliderb-spec`、`sliderfollowcircle`等は今回対象外です。

Slider Bodyは既存pathからcacheしたmeshと距離shaderで描画し、segment・round cap・round joinの重なりをGPU上で解決してから一度だけalpha合成します。`SliderBorder` は省略時white、`SliderTrackOverride` は省略時combo colourです。Track alphaはlegacyの `0.7` に固定し、shadow・border・outer/inner gradientもlazer sourceに合わせています。不正なINI色は既定値へ戻ります。設計・参照source・残る差・画像検証手順は[Slider Body parity](docs/slider-body-parity.md)を参照してください。

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

Gameplayの重なり描画はobject単位のrender queueで管理します。depth、Slider内proxy、Approach Circle / Judgement layerの参照元と検証内容は[描画順の設計記録](docs/gameplay-layering.md)を参照してください。
