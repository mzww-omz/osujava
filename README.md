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

1. Main Menuの中央Logoをクリックしてmenuを開き、右側のPlayを選びます（`P` / `Enter` / `Space`でも直接移動できます）。
2. Song Selectの小さなImportボタン（または`I`）から譜面を選びます。
3. Beatmap SetとDifficultyを選択してPlayを押します。
4. HitCircleはタイミングに合わせてクリックします。左/右クリックまたはZ/Xキーで操作できます。Sliderは頭を押してから、押したままカーソルでボールを追います。
5. 曲が終わるとResultsを表示します。

### 音量HUD

全画面共通の円形HUDで `master` / `music` / `effect` を調整できます。`F4`で開閉し、開いた直後はmasterを選択します。表示中は `Tab` / `←` / `→` で対象を切り替え、`↑` / `↓` またはマウスホイールで5%ずつ調整します。`Escape`は表示中のHUDを閉じ、HUDが閉じている場合は従来の画面操作です。Z/X・クリック・カーソル移動はHUD表示中もGameplayへ届き、曲と判定は進行します。

Main Menu・Gameplay・ResultsではホイールだけでHUDを開いて音量を変更できます。Song Selectでは、左のLocal Rankings領域でscoreを、右のCarousel領域で譜面をホイールscrollします。どちらのBrowserにも属さない余白・ヘッダー・下部ボタンではF4なしで音量を変更できます。HUD非表示時は各Browserのホイール操作を優先します。行の上でも `F4`でHUDを開いてからホイール、または `Alt`＋ホイールで音量を変更できます。Alt＋ホイールは全画面で利用できます。

音量は0〜100%に制限し、Musicはmaster×music、hitsoundはmaster×effect×譜面sample音量を使います。Main Menuの既存の65%基準ゲインと画面遷移fadeもその上に掛けます。設定は画面間で共有し、アプリを再起動すると100%へ戻ります。120msのfade-inと軽いscale-in、1.5秒の無操作後に220msのfade-out、約90msの円弧追従、選択時250msのglow強調があります。描画は独自の円・円弧・文字で、全画面を暗くする処理はありません。

視覚確認用の `./gradlew lwjgl3:volumeHudVisualHarness` はMain Menu・Song Select・Gameplay・Resultsを1280×720、1920×1080、2560×1440、720pの2倍バックバッファ密度で撮影し、0%・50%・100%も確認します。画像は `/tmp/osujava-volume-hud` に出力し、HUD外の背景とScreenの描画行列が変わらないことも検査します。

### 開発確認用 Debug Auto

Song SelectでDifficultyを選び、`F6`を押すとDebug Auto Playを開始します。検索入力中はF6などのプレイ・navigationショートカットは動作しません（F2 / Shift+F2は使用できます）。検索入力を終了した後の`Enter`またはPlay Cookieは通常のManual Playです。Debug Autoは既存のGameplay入力・判定経路に入力を送り、カーソルには目視確認用のcrosshairを表示します。Resultsには`AUTO / DEBUG`と表示されます。これはModsではなく、通常プレイ用Local Rankingsへ保存しない開発用の実行種別です。Gameplay中の`Escape`でSong Selectへ戻れます。

終了はウィンドウの閉じるボタン、macOSのCmd+Q、Windows / LinuxのCtrl+Qで行えます。

Song Selectでは、1つの.oszに入った複数Difficultyを1つのBeatmap Setとして表示します。taiko / catch / maniaのmode情報も保持して表示しますが、Gameplay対応はosu!standardのHitCircle、Slider、Spinnerです。ModsのGameplay効果、Replay、Editor、オンライン機能は未実装です。

下部はMode / Mods / Random / Optionsの共通Toolboxで、normal / hover / `@2x`と合成skin画像に対応します。Modeはstandardのみplayableと表示し、Modsボタン／`F1`は未対応Modをdisabled表示するselector foundationを開きます。`Escape`で閉じます。Options／`F3`は未対応を明示します。Set内の難易度に保存Scoreがあればplayedの橙、なければ未プレイの桃で表示し、選択中の白とsiblingの水色が優先されます。Skin inventory、Mods audit、geometryと検証結果は[Song Select Phase 5A](docs/songselect-toolbox.md)を参照してください。

Song Select右上のGroup / SortでLibraryを分類・並べ替えできます。入力するとTitle / Artist / Creator / Difficulty名を対象に、空白区切りの全tokenで検索します。Unicode入力にも対応しています。`F2`は現在の検索結果内からRandom、`Shift+F2`は以前のRandom selectionへ戻ります。検索・Sort・Group変更時は譜面identityを維持し、検索解除時は自動fallback前の選択、または検索中に明示的に選んだ譜面を復元します。実装した分類、数値の根拠、保留項目と検証結果は[Song Browser Phase 3](docs/songselect-browser.md)を参照してください。

通常プレイが終了すると、確定したScore・Accuracy・Combo・判定数・日時を `~/.osujava/scores` へ保存します。左のLocal Rankingsに選択difficultyの結果をScore降順で表示し、clickで選択、wheelでscrollできます。difficulty rowのGradeは先頭のbest local scoreに対応し、score無しでは表示しません。Debug Autoと中断プレイは保存しません。再起動後も保持され、破損recordは個別にskipします。Grade規則、identity、保存schemaと検証結果は[Song Select Phase 4](docs/songselect-local-scores.md)を参照してください。

Song Selectのlegacy skin互換性は[互換性監査](docs/songselect-skin-compatibility.md)に整理しています。現在のskinがbrowser artworkを提供している場合、未提供のtop/bottom装飾にはアプリ側のunderlayを使います。透明1×1もcurrent assetとして尊重し、描画寸法とlayout予約領域を分離します。Greylooks・WhiteCat・Seoul・Defaultの固定metadata corpusと実画像harnessを回帰確認に使用します。

### 内蔵デフォルトSkin / カスタムSkin

未指定時は同梱の **Greylooks 1.4（iZaIxSP / CC BY 4.0）** を使います。ローカルのosu!インストールは不要で、IDE classpath・通常build・配布JARから同じリソースを読みます。アセット・制限・検証結果は[Greylooks統合記録](docs/greylooks-integration.md)、クレジットは[第三者アセット一覧](THIRD_PARTY_ASSETS.md)を参照してください。

任意のローカルSkinディレクトリを指定できます（相対パスは起動時のworking directory基準）。選択UIはありません。

~~~sh
./gradlew lwjgl3:run -PskinDirectory="/path/to/skin"
~~~

実行可能JARでは `java -Dosujava.skinDirectory="/path/to/skin" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar` を使います。オプションを外して起動すると内蔵Greylooksを使います。

欠落textureを別のローカルSkin directoryから補完する場合は、Gradleへ
`-PskinFallbackDirectory="/path/to/fallback-skin"`、JARへ
`-Dosujava.skinFallbackDirectory="/path/to/fallback-skin"`を追加します。
customの通常解像度画像もfallbackの`@2x`より優先します。透明画像は置換しません。
検索順はcustom → 明示指定したlocal fallback（任意）→ 内蔵Greylooks → コードの最低限の描画です。画像と数字は欠落部分だけ補います。animationはproviderを混ぜません。
Slider始点・終点の専用circle baseがcustomにない場合、customのhitcircle／hitcircleoverlayがあればそのcircle familyを再利用し、Greylooksの専用circleで置き換えません。専用baseがある場合は引き続き優先します。Slider ball・follow circle等の補完順は通常どおりです。
`skin.ini`はcustomを優先し、存在しない／読めない場合に次のproviderのiniを使います。設定を項目単位では合成しません。
Spinner bodyのOld/New選択はcustom providerのrootを優先し、fallback画像でstyleを変えません。

`.osk`を直接指定する開発確認用オプションもあります。

~~~sh
./gradlew lwjgl3:run -PskinArchive="/path/to/skin.osk"
~~~

実行可能JARでは `java -Dosujava.skinArchive="/path/to/skin.osk" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar` を使います。起動時に一度Importし、`~/.osujava/skins/<SHA-256 ID>/`へ保存したディレクトリを既存の`OsuSkinAssets`へ渡します。IDは展開後のファイル名と内容から生成し、同名でも内容が異なるSkinは別保存、同じ内容は再利用します。subdirectoryも保持しますが、対応画像は従来どおりSkinディレクトリ直下から解決します。

ZIPのcentral directoryと各entryのサイズ・CRCを検証し、絶対パス、`..`、Windows drive path、backslash、正規化後の重複entryを拒否します。圧縮ファイル・展開後の合計はそれぞれ256 MiB、entry数は10,000までです。一時ディレクトリへ展開して成功時のみ配置し、失敗時はcleanupします。`.osz`とも安全な展開helperを共有します（`.osz`のサイズ上限は従来の1 GiB、entry数上限は10,000）。

両方のオプションがある場合は`.osk`を優先します。Import失敗はログへ出し、`skinDirectory`指定があればそこへfallbackし、なければ内蔵Greylooksで起動を続けます。Gameplay中にはarchiveを読みません。Skin選択UIはありません。

対応するのはosu!standardのHitCircleおよびSlider始点・終点用 `hitcircle.png`、`hitcircleoverlay.png`、`approachcircle.png` の3画像です。画像ごとに `@2x.png` を優先し、density=2で論理サイズを求めます。128論理pixelを基準直径としてCircleSizeとPlayfieldViewportの倍率を掛け、中心に配置します。本体とApproach Circleはskin.iniのcombo colour（未指定時は既存色）でtintし、overlayは元の色で重ねます。既存の出現・Approach timingは維持します。画像なし、ディレクトリなし、読み込み失敗は各画像単位で従来の描画へfallbackします。

色付けと基準サイズはosu!lazerの [LegacyMainCirclePiece](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyMainCirclePiece.cs)、[LegacyApproachCircle](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyApproachCircle.cs) を参照しています。

`OsuSkinAssets` はScreen作成時にTextureを一度読み込み、GameplayScreen終了時にdisposeします。数字Textureも同じ管理に含めます。色設定の `GameplaySkin` と画像ファイル解決の `SkinAssetResolver` は別責務です。Slider始点・終点はhitcircle画像を共有し、始点のApproach Circleも対応します。終点の既存サイズ・出現タイミングは維持します。Slider専用始点・終点画像とSlider Ballも対応します。

HitCircleとSlider始点のcombo numberは、Skin直下の `skin.ini` の `[Fonts]` から `HitCirclePrefix` と `HitCircleOverlap` を読みます。省略時はそれぞれ `default` と `-2` です（[osu!lazer LegacySkinExtensions](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkinExtensions.cs)）。`[General]` の `HitCircleOverlayAboveNumber`（typo互換 `HitCircleOverlayAboveNumer`）も対応し、既定はoverlayがnumberより上です。正規名があればtypo名より優先します。Slider Body用に `[Colours]` の `SliderBorder` と `SliderTrackOverride` も解析します。Score/Combo prefix・overlap、CursorCentre/Rotate/Expand/TrailRotate、SpinnerNoBlink/Backgroundも対応します。今回`Combo1–8`と`AllowSliderBallTint`を追加しました。未対応項目は安全に無視します。

数字は `<prefix>-0` ～ `<prefix>-9` を各々 `name@2x.png` → `name.png` の順で探索し、densityで割ったnative logical width/heightを使います。桁のadvanceは `width - overlap`（正値で重なり、負値で間隔が広がる）で、数字全体をCircle中央に配置します。画像のアスペクト比を維持し、倍率は `0.8 × radius / 64 × viewport scale` です（[OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)、[DrawableHitCircle](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableHitCircle.cs)、[OsuHitObject](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/OsuHitObject.cs)）。Slider終点には数字を表示しません。

Skin未指定やskin.iniなしでは内蔵iniを使います。数字はcustom prefixのglyphを優先し、不足分だけ内蔵iniのprefixで補います。それでも全10桁をロードできない場合は既存フォント描画へfallbackします。prefixは安全なskin相対サブディレクトリにも対応し、絶対パス・`..`・Windows形式のパスは拒否します。

Slider Ballは `sliderb0.png` からのanimationを `sliderb.png` より優先します。各frameは `@2x.png` → `.png` の順で解決し、0から最初の欠番までの連番のみ使用します。frame 0がなければ静止画像を使い、画像なし・読み込み失敗ではBall全体を既存ベクター描画へ戻します。利用可能なSkin BallにベクターBallは重ねません。frameはScreen作成時に一度ロードし、共有Textureもidentityで管理して1回だけdisposeします。

animationは `max(0.15 / SliderTiming.velocity() × 1000/60, 1000/60)` ms/frameでloopします。共通Gameplay時刻と `startTime - preempt` を基準に直接frame番号を求め、deltaは積算しません。native pixel sizeをdensityで割り、`radius / 64 × viewport scale` を掛けます。縦横を同じ倍率で描画し、lazerと同じく384論理pixelを越える画像は各軸の中央をcropします。既定の色は白（元画像の色）で、`AllowSliderBallTint: 1`ではcombo/accent色でtintします。`[Colours] SliderBall`は未対応です。位置・path・progress・repeat・判定・Autoは既存処理を使います。

解決・timing・サイズの根拠はosu!lazerの [OsuLegacySkinTransformer](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/OsuLegacySkinTransformer.cs)、[LegacySliderBall](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacySliderBall.cs)、[LegacySkinExtensions](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkinExtensions.cs)、[DrawableSlider](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Objects/Drawables/DrawableSlider.cs)を確認しています。`sliderfollowcircle`は既存Rendererで描画します。`sliderb-nd`、`sliderb-spec`の追加レイヤーは未対応で、将来用に保存しています。

Slider Bodyは既存pathからcacheしたmeshと距離shaderで描画し、segment・round cap・round joinの重なりをGPU上で解決してから一度だけalpha合成します。`SliderBorder` は省略時white、`SliderTrackOverride` は省略時combo colourです。Track alphaはlegacyの `0.7` に固定し、shadow・border・outer/inner gradientもlazer sourceに合わせています。不正なINI色は既定値へ戻ります。設計・参照source・残る差・画像検証手順は[Slider Body parity](docs/slider-body-parity.md)を参照してください。

Importしたファイルはユーザーのホームディレクトリ下の.osujava/libraryへ展開・コピーします。Library indexも同じ場所へ保存され、アプリ起動時に読み込みます。Importした譜面は再起動後もSong Selectに表示され、そのままGameplayを開始できます。同一beatmap setをもう一度Importすると、既存のローカルデータとindex entryを更新します。保存方式とset識別方法は[docs/architecture.md](docs/architecture.md)を参照してください。

Gameplayの単発hitsoundは譜面sample → custom skin → 任意local fallback → 内蔵skin → 生成clickの順です。skin側では通常の番号なしsample名を使い、譜面側のsample index・判定・音声cueのタイミングは変更しません。空／壊れた音声は次のproviderを試します。

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
