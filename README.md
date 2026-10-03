# osu!java

Java 21、libGDX、LWJGL3で作る完全ローカルのリズムゲームです。osu!stableの画面と操作感を参考に、osu! wiki、公開lazerソース、stableの観測を根拠として独立実装しています。現在プレイできるのはosu!standardです。

Bancho、osu! API、公式WebSocketなどのproduction serviceには接続せず、実行時のネットワーク通信は行いません。アカウント、オンラインランキング、マルチプレイはありません。build時は、未取得の依存ライブラリをMaven Centralからダウンロードします。

## 起動と配布JAR

Java 21のJDKが必要です。Gradle wrapperを同梱し、[daemon設定](gradle/gradle-daemon-jvm.properties)と各モジュールのtoolchainでJava 21を指定しています。libGDXは1.14.2、JUnitは5.13.4を使います。

macOS / Linux:

```sh
./gradlew lwjgl3:run
```

Windows:

```bat
gradlew.bat lwjgl3:run
```

macOSではlauncherがGLFWの非同期起動設定を使います。以降のコマンドもWindowsでは `./gradlew` を `gradlew.bat` に置き換えてください。

依存ライブラリを含む実行可能JARを作成する場合:

```sh
./gradlew lwjgl3:executableJar
java -jar lwjgl3/build/libs/osujava-0.1.0-all.jar
```

## 遊び方

1. Main Menuの中央Logoをクリックしてメニューを開き、Playを選びます。`P` / `Enter` / `Space`でもSong Selectへ移動できます。
2. Song SelectのImportボタン、または `I` からローカルの `.osz` / `.osu` を読み込みます。
3. 曲と難易度を選び、Play Cookieまたは `Space` で開始します。`Enter` はフォーカス中のグループ・曲を展開し、プレイ対象を確定した状態では開始します。
4. HitCircleは左/右クリックまたは `Z` / `X` で叩きます。Sliderは頭を押してから入力を押したままボールを追い、Spinnerは入力を押したまま中心の周りにカーソルを回します。
5. プレイが完了するとResultsを表示します。Gameplay中の `Escape` は中断してSong Selectへ戻ります。

ウィンドウの閉じるボタン、macOSの `Cmd+Q`、Windows / Linuxの `Ctrl+Q` で終了できます。

### Song Select

1つの `.osz` に含まれる複数難易度を1つのBeatmap Setとして扱います。taiko / catch / maniaのmode情報も保持しますが、GameplayはstandardのHitCircle、Slider、Spinnerに対応しています。

| 操作 | 動作 |
| --- | --- |
| `↑` / `↓` | 一覧内の行を移動 |
| `←` / `→` | フォーカス中の行を確定、または前後の曲へ移動 |
| `Page Up` / `Page Down` | 一覧内を10行移動 |
| `Shift+←` / `Shift+→` | 前後のグループへ移動 |
| `Shift+Enter` | 親グループの開閉 |
| 文字入力 / `Backspace` | 検索 / 検索文字の削除 |
| 検索中の `Enter` / `Escape` | 検索入力を終了 |
| `F2` / `Shift+F2` | 検索結果内のRandom / 前のRandom選択へ戻る |
| `F1` / `F3` | Mods選択画面 / Beatmap Options（未対応操作は無効） |
| `F6` | 開発確認用Debug Autoを開始 |

右側の譜面一覧はホイールや行のドラッグでスクロールできます。ホイールは選択を変えず、スクロールバーは位置を示します。右上のGroup / SortではArtist、Creator、BPM、Lengthによる分類・並べ替えができ、SortにはTitleもあります。選択した譜面のローカル音声をプレビュー再生します。

通常検索はTitle、Artist、Creator、難易度名、Unicode名、Source、Tagsを対象に、空白区切りの全条件を満たす難易度を探します。正の整数はBeatmap ID / Set IDとしても照合します。条件検索の例:

```text
artist="example artist" ar>=9 bpm>180 length<120 mode=osu
```

文字列条件は `title` / `artist` / `creator` / `difficulty`、数値条件は `ar` / `cs` / `od` / `hp` / `bpm` / `length` / `drain`、mode条件は `osu` / `taiko` / `catch` / `mania` に対応します。数値比較は `=` / `==` / `!=` / `<` / `>` / `<=` / `>=`、長さとdrainは秒単位です。未対応の項目など、条件として解釈されない語は通常の文字列検索として扱います。

検索入力中はプレイ・移動ショートカットを抑制しますが、`F2` / `Shift+F2` は使用できます。検索・Sort・Group変更時の選択は譜面identityで管理します。

星表示には計算済みの評価を受け取る境界がありますが、通常起動では星評価の計算元がないため表示しません。実装の根拠と残る差は[Song Selectロードマップ](docs/songselect-parity-roadmap-20260929.md)、[検索](docs/songselect-parity-phase10-search-20260929.md)、[譜面時間統計](docs/songselect-parity-phase10-timing-20260929.md)を参照してください。

### Resultsとローカルスコア

通常プレイの完了時に、Score、Accuracy、最大Combo、判定数、日時などを保存します。Song Select左側のLocal Rankingsは選択難易度のスコアを降順で表示し、保存済み結果を開けます。難易度行のGradeは先頭のローカルスコアに対応します。

Resultsは6判定、Grade、Perfect、Score、Combo、AccuracyをSkin画像とともに表示します。`Enter` / `Space` で表示演出を早送りし、Retryで新しいプレイを開始、Back / `Escape` でSong Selectへ戻ります。通常プレイ直後はAccuracy領域へのhoverで入力誤差・Unstable Rateを確認できます。保存済み結果からは入力誤差を再構成しません。

現在のstandardセッションは `OsuScoreV1` によるNoModのローカルScoreV1実装を使い、Sliderの途中イベントと最終判定を分けて集計します。旧方式の保存記録も読み込みます。旧記録の未収集判定数は `-` とし、未知のPerfectやHPグラフを作りません。

Debug Autoと中断プレイは通常ランキングに保存しません。保存結果の閲覧でも再保存しません。破損した保存recordは個別にスキップし、保存失敗でもResults表示は継続します。

### 音量HUD

全画面共通のHUDで `master` / `music` / `effect` を調整します。

- `F4` で開閉し、開いた直後はmasterを選択します。
- 表示中は `Tab` / `←` / `→` で対象を選び、`↑` / `↓` またはホイールで5%ずつ変更します。
- `Escape` でHUDを閉じます。Gameplayの入力と判定はHUD表示中も進みます。
- Main Menu・Gameplay・Resultsではホイールだけでも開きます。Song Selectでは譜面一覧とLocal Rankingsのスクロールを優先し、それ以外の領域で音量を変更できます。
- `Alt+ホイール` は全画面で音量操作を優先します。

音量は0〜100%。Musicはmaster×music、hitsoundはmaster×effect×譜面sample音量で、メニューの基準ゲインやfadeも反映します。設定は画面間で共有し、再起動すると100%へ戻ります。

### 開発確認用Debug Auto

`F6` はModsではなく `DEBUG_AUTO` 実行種別です。`DebugAutoPlayer` が通常の `GameplayInput` APIへ入力を送り、手動プレイと同じ判定経路を使います。判定状態やスコアを直接書き換えません。Resultsには `AUTO / DEBUG` と表示します。

## Skin

未指定時は同梱の **Greylooks 1.4（iZaIxSP / CC BY 4.0）** を使います。osu!のローカルインストールは不要で、classpathと配布JARの両方から読み込みます。クレジットは[第三者アセット一覧](THIRD_PARTY_ASSETS.md)、統合の詳細は[Greylooks統合記録](docs/greylooks-integration.md)を参照してください。

ローカルのSkinディレクトリ、または `.osk` を起動時に指定できます。Skin選択UIはありません。相対パスは起動時のworking directoryが基準です。

```sh
./gradlew lwjgl3:run -PskinDirectory="/path/to/skin"
./gradlew lwjgl3:run -PskinArchive="/path/to/skin.osk"
```

配布JARでは `-jar` より前にsystem propertyを指定します。

```sh
java -Dosujava.skinDirectory="/path/to/skin" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar
java -Dosujava.skinArchive="/path/to/skin.osk" -jar lwjgl3/build/libs/osujava-0.1.0-all.jar
```

任意の補完SkinはGradleの `-PskinFallbackDirectory="/path/to/fallback"`、JARの `-Dosujava.skinFallbackDirectory="/path/to/fallback"` で指定します。

画像の解決順はcustom → 指定したlocal fallback → 内蔵Greylooks → コード描画です。customの通常解像度画像はfallbackの `@2x` より優先し、透明画像も有効な指定として尊重します。同じprovider内では `@2x` を優先して論理サイズを求めます。animationのframeや `skin.ini` の項目をprovider間で混ぜません。

HitCircle、Slider、Spinner、Cursor、Gameplay HUD、Song Select、ResultsのSkin画像に対応しています。`skin.ini` のcombo色、Slider色、数字prefix / overlap、Cursor・Spinner設定などを読み、未対応項目は無視します。全てのlegacy Skin設定への対応を保証するものではありません。

`.osk` は起動時に一度Importし、ファイル名と内容から求めたSHA-256 identityで保存します。`skinArchive` と `skinDirectory` の両方を指定した場合はarchiveを優先し、失敗時はdirectory、または内蔵Skinで起動を続けます。

Gameplayの単発hitsoundは譜面sample → custom Skin → local fallback → 内蔵Skin → 生成clickの順で解決します。空・壊れた音声は次のproviderを試します。

詳しい互換仕様は[Skin animation](docs/skin-animation-providers.md)、[Slider Body](docs/slider-body-parity.md)、[Song Select Skin](docs/songselect-skin-compatibility.md)、[Results](docs/results-stable-implementation.md)に記録しています。

## ローカル保存とImport

| 場所 | 内容 |
| --- | --- |
| `~/.osujava/library/<set-id>/` | Importした譜面、音声、背景など |
| `~/.osujava/library/index/` | SetごとのProperties形式の索引 |
| `~/.osujava/scores/` | プレイUUIDごとのProperties形式のスコア |
| `~/.osujava/difficulty/` | 内容hash・Mods・計算版別のローカル星評価cache |
| `~/.osujava/skins/<SHA-256 ID>/` | ImportしたSkin |

起動時に索引と `.osu` を読み、譜面を再構築します。同一Setの再Importはローカルデータと索引を更新します。Set identityは正のBeatmapSetIDを優先し、ない場合はmetadataから生成します。スコアの難易度identityはSet IDと正規化した `.osu` 相対パスです。

`.osz` と `.osk` は共通の `SafeArchiveExtractor` を使います。絶対パス、`..`、Windows drive path、backslash、正規化後の重複entryを拒否し、ZIPのcentral directory・サイズ・CRCを検証します。圧縮ファイルと展開後の合計は `.osz` が各1 GiB、`.osk` が各256 MiB、entry数はそれぞれ10,000までです。一時ディレクトリへ展開し、成功時に配置します。

壊れた難易度は警告付きでスキップし、有効な難易度がなければImportエラーとして扱います。単独 `.osu` は譜面と参照する音声・背景をコピーします。音声・背景がなくてもImportでき、Gameplayで音声を使えない場合はローカル時計へ切り替えます。

## アーキテクチャ

Gradleは `core` と `lwjgl3` の2モジュール構成で、依存方向は `lwjgl3 → core` です。`core`にはゲームロジックに加えlibGDXの画面・描画も含まれます。

| モジュール / パッケージ | 責務 |
| --- | --- |
| `core/beatmap`・`beatmap.parse` | 譜面データモデルと `.osu` 解析 |
| `core/archive`・`library` | 安全な展開、Import、譜面一覧と索引の永続化 |
| `core/difficulty` | 独立した星評価、bounded worker、version別cache |
| `core/gameplay` | Session・Input・状態スナップショット・GameClockの契約と共通処理 |
| `core/ruleset.osu` | standardの判定、Slider経路・時間、Spinner、Stacking、ScoreV1 |
| `core/ruleset.osu.render` | osu!固有の描画順、Slider Body、legacy表示計算 |
| `core/ui`・`ui.theme` | Screen、入力変換、Renderer、レイアウト、画面演出 |
| `core/skin`・`score`・`audio` | Skin解決、結果保存、共有音量 |
| `lwjgl3` | desktop launcher、ファイル選択、メニュー音声解析 |

```text
.osz / .osu → Importer → Parser → BeatmapLibrary → SongSelectScreen
                                                       ↓
                                                GameplayScreen
                                                       ↓
手動入力 / Debug Auto → GameplayInput → OsuGameplaySession ← GameClock
                                            ↓                 ↑
                                      GameplayState      Music / Elapsed
                                            ↓
                                     GameplayRenderer

GameplayScreen → ResultsSnapshot → ResultsScreen
                        └────────→ LocalScoreStore（通常プレイ完了時）
```

`OsuJavaGame` が共有サービスと描画リソースを組み立て、画面遷移と破棄を管理します。Importがarchive処理を完結し、Gameplayへ解析済み譜面とローカルPathを渡します。判定・スコアはSession、プレイ時刻はGameClock、描画は読み取り専用のGameplayStateを受け取るRendererの責務です。音声cueもSessionが発行し、AudioPlayerが再生します。

Song Selectは `SongBrowserModel` が検索・分類・選択、Input関連クラスが操作、Carousel / ViewStateが動き、Layout / RowPresentationが表示情報、Rendererが描画を担当します。Local Rankingsは別の `ScoreBrowserModel` が管理します。`LocalDifficultyService` が別workerで検証済みの星を計算し、星表示・Difficulty分類・検索へ供給します。画面破棄でworkerをcancelします。UIの表示時刻とGameplayの時計は分離しています。

`Ruleset` / `GameplaySession` はインターフェース化されていますが、現在のGameplay画面とRendererはstandard専用です。他モードの追加にはRulesetと表示側の対応が必要です。[設計資料](docs/architecture.md)には旧スコア方式の記述も残っているため、現行のスコア経路は `OsuGameplaySession → OsuScoreV1` と[Results実装記録](docs/results-stable-implementation.md)を参照してください。

## 対応範囲と残る課題

- standardのHitCircle・Slider・Spinner、ローカルImport、Skin、通常プレイ結果の保存・再閲覧に対応しています。
- NoModのScoreV1、Geki / Katu、入力誤差統計を実装していますが、stableとの全体的な1:1互換は未達です。Spinner物理・加点、判定の時系列処理などに残る差があります。
- HP drain・fail判定は未実装で、実プレイのHP・pass・RPM記録は未収集です。HPグラフは実データがある場合だけ描画する構造です。
- 星評価はNM standardのv6以降のcircle/spinnerと、v8以降の検証済みLinear/Bezier/Perfect/Catmull/mixed Sliderに対応し、Difficulty tab/sort/groupと `stars` 検索へ接続しています。HitObject行のskipがあるchart、未検証path/timing・処理上限超過・Mods等のchartは星なしです。[対応範囲と検証](docs/songselect-backend-difficulty-20261003.md)を参照してください。
- Modeは左下の縦型メニュー、Modsは3段の全画面ダイアログ、Optionsは6項目の番号付きダイアログを表示します。開閉・hover・アニメーションと入力領域を共有し、未対応項目は無効です。[stable調査・検証記録](docs/songselect-selectors-20261003.md)を参照してください。
- ModsのGameplay効果、Replay記録・再生と `.osr` Import / Export、Editor、taiko / catch / maniaのGameplayは未実装です。Optionsはlocal Collection管理に対応し、譜面/score削除等は未対応です。
- 現譜面の順位は内容hashが一致したローカルscoreを対象とし、hashなしの旧scoreは未検証legacy行として扱います。異なる採点方式・条件の順位分離は未完了です。

互換性の調査と実装の残りは[Gameplay差分](docs/gameplay-lazer-gap.md)、[Resultsの不足データ計画](docs/results-missing-data-plan-20260929.md)、[Results実装記録](docs/results-stable-implementation.md)を参照してください。これらには過去の仕様や未実装の計画も含まれます。

## テストと描画確認

```sh
./gradlew test
./gradlew build
```

依存取得済みの環境では `./gradlew --offline build` も利用できます。JUnitでParser、安全な展開、Import、索引・スコア保存、時計、入力、判定、ScoreV1、Skin解決、UIモデル・レイアウトを確認します。制御可能なGameClockを使う判定テストは実時間に依存しません。

実際の画面を撮影するHarnessは任意実行です。通常のbuildではHarnessをコンパイルしますが起動しません。描画にはデスクトップのOpenGL環境が必要です。

```sh
./gradlew lwjgl3:mainMenuVisualHarness
./gradlew lwjgl3:songSelectVisualHarness
./gradlew lwjgl3:resultsVisualHarness
./gradlew lwjgl3:volumeHudVisualHarness
```

既定の画像出力先はそれぞれ `/tmp/osujava-main-menu`、`/tmp/osujava-songselect`、`/tmp/osujava-results`、`/tmp/osujava-volume-hud` です。Gameplayには `hudVisualHarness`、`judgementVisualHarness`、`cursorVisualHarness`、`spinnerVisualHarness`、`bundledSkinVisualHarness` もあります。利用可能なオプションは [lwjgl3/build.gradle](lwjgl3/build.gradle) を参照してください。
