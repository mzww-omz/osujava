# Song Select 1:1対応の実装計画（2026-10-02）

## 到達点と実装方針

[差分比較台帳](songselect-one-to-one-gap-ledger-20261002.md)の33項目を実装単位へ分解する。
基準はJava commit `b6981b57049877ea312ddab47a6bfceb24bb180a`と、台帳に記録したb20230727.9。
同一のローカル譜面・Skin・設定・入力列で、状態、geometry/time、pixel/audioを照合する。
静的契約の実装完了とstable実機での1:1合格は別々に記録する。

[既存roadmap](songselect-parity-roadmap-20260929.md)のphase 10の分類対応を最初の製品変更とする。
それを終えたらphase 10の全検索fieldを待たず、ビジュアルの確定差へ進む。
8bの繰越方針と、Skin専用の調査・報告・commit単位を維持する。
以下のP番号は実装順を表し、既存phaseの番号を置き換えない。

完全ローカルを維持し、未取得オンライン値を捏造しない。
公式asset抽出・保護回避を行わず、自作fixtureで差を観測する。
Wine起動検証は当面行わない。Quota制限に抵触しそうな参照実行は試行を増やさず中止する。
参照環境がなくても確定契約の改修は進め、実機待ちを明示する。
計画作成時は文書のみを追加した。その後のユーザー指示「1〜6を進めてください」を受け、
P0〜P9の確定契約の実装と未確定部分の調査を進めた。現在の対応範囲・残差・検証・commitは
[1〜6の実装進捗](songselect-parity-implementation-progress-20261002.md)を参照。全工程の完了／stableとの1:1合格はまだ認定していない。

## 現在の進行とselector残件（2026-10-03）

Mode/Mods/Options入口の割込み修復は[selector記録](songselect-selectors-20261003.md)までで区切った。
通常進行はbackend **B05のsource品質→timing oracle→work budget→B06→B07→B08→B09**へ戻す。
今回B05のsource品質通知とcache失効・warm/source修復の検証を実装した。
次の着手単位・通常Mods/Options/他mode/Editor/selectorの細部/Collection内部の解決計画は
[backend計画の依存表](songselect-backend-improvement-plan-20261002.md#通常phaseへの復帰とselector残件の実行順)を参照。
P8bの外観残件はS-VIS1/S-COL1、機能残件はS-MOD/S-OPT/S-MODEへ分割し、P10の同期比較で認定する。
後続M/E番号は別の機能Phaseで、既存P/Phase番号を置き換えない。

## 実装順・依存・完了条件

| 順 / 対応ID | 変更単位と主な対象 | 着手条件・完了条件 |
| --- | --- | --- |
| **P0: 比較fixture** / 全項目 | 既存`SongSelectVisualHarness`へ今回の自作Skinと境界sceneを追加。既存出力を調べ、不足する状態・geometry情報だけ補う | 選択path/density、frame時刻、入力列、画面/framebufferサイズを再現可能にする。PNGと状態記録を同じscene IDへ結びつける。汎用の新テスト基盤は作らない |
| **P1a: 分類値** / D01・D02 | `SongBrowserModel`のBPM最大値とLibrary終端整数秒を難易度単位で扱う。Gameplayの時間計算から独立 | 同整数秒tie、導入の長い譜面、検索で除外された難易度が分類へ混入しないことを確認。tie-breakの未確定経路を先にILで閉じる |
| **P1b: Group構築** / D01・D02・D05 | BPM60刻み、Length1/2/3/4/5/10分、同Setが複数Groupへ所属するprojectionとidentityを修正 | 親Group・代表行・family・focus・selectionを混同しない。同Set異区分、境界値、部分検索、0件、検索解除で所属と選択を比較 |
| **P2a: 重なり** / V01 | `SongSelectRenderer`のBack二層とselection normal/overの描画位置を修正 | 同main manager内のBack→selectionを保持。巨大Backで重なりを検証。Cookie・別managerの順を推測して同時変更しない |
| **P2b: 独自の描画補正** / V05・V06・V22 | `SongSelectRowRenderer`の白い補助矩形、Optionsの常時暗色化、通常画面の診断文字を個別に見直す | native通常色を確認した範囲だけ修正。未対応機能の入力は有効化しない。診断情報はharness／明示的debug表示へ移し、通常画面の余分な描画を除く |
| **P3a: 論理寸法** / V02 | `SongSelectSkinAssets.SkinTexture`と各利用箇所のdensity除算・origin・cropを揃える | Song Select内の変更を優先。185×181 selection、545×183 Back、1×1透明HD等でgeometryを比較。星／mode／gradeの既存整数化と二重丸めを起こさない |
| **P3b: 素材探索** / V10・V11・V12・V13 | HD eligibility、missing/decode failure、provider mask、INI owner、音aliasを契約ごとに分割 | 未確定option/capability/providerをILで追う。現在のローカル設定への対応を確定してから導入。任意fallback Skinをnative譜面providerへ読み替えない |
| **P4: chrome geometry** / V03・V04 | `SongSelectRenderer`、`SongSelectChrome`、top coverageとbottom layoutの配置規則を修正 | crop→UV、nativeのfield/origin変換を確定して着手。1365/1366/1367、透明top、巨大top/bottomでdraw/viewport/inputを確認。予約上限を根拠なく一括削除しない |
| **P5a: tabとmetadata** / V14・V15 | `SongBrowserControls`のtab状態、内容、幅分岐、menu配置と`SongSelectDetails`／rendererのmetadata配置を修正 | tab enumと表示カテゴリの意味、click後の状態遷移を確定。1152/1153×768・1280×900で数/geometryを比較。tabを単にGroup ordinalへ結びつけない |
| **P5b: scoreとscrollbar** / V17・V18 | `ScoreBrowserBounds`、score描画、carousel/score各scrollbarを別々に対応 | native各managerのclip・座標変換・bar更新を確定。空/1/多数、端/中間、wheel/dragでdrawとhitを一致させる。score機能依存はP8へ分離 |
| **P6a: hitと入力順** / V07・I02・I03・I04 | `SongSelectToolboxLayout`、行hit、`SongSelectInputController`、`SongSelectScreen`の処理順・状態寿命を修正 | dispatcherの候補順/alpha/clip/丸めを確定。selected優先やalpha推定範囲を根拠なく別近似へ置換しない。同frame入力、focus境界、押下中遷移を比較 |
| **P6b: spriteの寿命と時計** / V08・V09・I01 | Back連番のgeometry更新、sprite epoch、再生成/再表示を対応 | native寸法抑止flag・初回update・clock選択を確定。異寸法連番、遅延生成、再表示でdraw/crop/hit/frameを同時比較。既存行色/星/foregroundを回帰検証 |
| **P7: 文字** / V16・V15 | 字幅・baseline・影・省略・glyph fallbackを項目別に修正 | 同じ許可fontの測定結果を用意し、Latin/CJK/結合文字/長文で誤差を特定。まず既存描画経路の調整で対応し、backend変更は実測が必要性を示した場合だけ別計画化 |
| **P8a: Browser残り** / D03・D04・D05 | 未対応検索field、検索待機、selection修復、collection等のローカルデータを小単位で追加 | fieldごとの未知値/型/丸め/culture/IMEを確定。評価値はRulesetの計算結果を用い、画像都合の仮のstarsを保存しない。native検索待機と汎用debounceを混同しない |
| **P8b: selector・機能依存** / V21・V17 | Mode/Mods/Optionsの外観と状態、score/replay/collection/editorへのローカル操作を分割 | selectorの配置/animationと機能実装を別commitにする。ModsはRuleset・Gameplay側が対応したものだけ適用。Debug Autoは通常Input APIを維持。Modeの閲覧とGameplay対応を区別 |
| **P9: 音声・背景・Cookie・遷移** / V19・V20・T01・T02 | `SongSelectPreview`、音asset解決、artwork/decorations、Cookie、入退場・復帰を契約別に対応 | 同path別難易度、PreviewTime省略/0/正値、連打、ロード逆転/失敗でseek/fade/clockと表示を比較。素材探索はSkin、発音時刻は画面、Gameplay時間はGameClockの責務 |
| **P10: 総合比較** / 全項目 | 条件を揃えたstable参照との状態・geometry/time・pixel/audio比較と残差修正 | 正常なオフライン参照環境が前提。未測定領域、素材/font条件差、許容差を記録。Java capture成功だけで1:1合格にしない |

基本順はP0→P1a→P1b→P2→P3→P4→P5→P6→P7→P8→P9→P10。
P3bのIL調査はP2中から進められる。P8のデータ調査とP9のpreview調査も、配置修正と独立なら先行できる。
P5のカテゴリ/scoreがローカルデータに依存する場合は必要なP8の小単位を先に実装する。
P4以降の未確定契約が閉じない場合、該当項目を保留し、依存しない確定差を先に進める。
参照実機待ちはP10の合格を止めるが、P1–P9の確定契約の実装を一律には止めない。

## 最初の5変更単位

各単位は回帰テスト・関連capture・build・diff確認・commitまで含む。
調査で範囲が大きいと分かった単位は、実装前にさらに分割する。

| 順 | 変更と成果物 | 検証 |
| --- | --- | --- |
| 1 | P0: 自作の巨大Back/奇数HD/短いtopと分類境界fixtureを既存harnessへ追加。現状の失敗条件・PNGを保存 | 既存harness出力とscene条件を確認。現行Java画像をnative期待値としてgolden化しない |
| 2 | P1a: Sortの難易度別統計と整数秒比較を修正 | `SongBrowserModelTest`と検索integration。主なBPMと最大BPM、長い導入、同秒tieの独立期待値を確認 |
| 3 | P1b: Group分類と複数Group projectionを修正 | `SongBrowserRowsTest`、`SongBrowserFocusTest`、navigation/search。選択IDと表示行IDを比較し、Group captureを保存 |
| 4 | P2a: Backとselectionの合成順を修正 | 自作色の交差画素を検証。通常サイズ・巨大Back・透明Backで既存selection/hoverの順を回帰確認 |
| 5 | P3a: Song Selectの整数論理寸法を修正 | skin/assets/toolbox/reference geometry。SD、偶数/奇数HD、1×1透明HDのdraw/crop/hitを確認 |

この5単位の後、P2bとP3bへ進む。
Optionsやdiagnostic表示の見直しは入力・機能状態と関係するため、最初のBack描画順修正へ混ぜない。

## 追加調査を完了してから実装する契約

| 未確定部分 | 調査の出口 |
| --- | --- |
| manager間のdraw順、field/origin/crop変換 | spriteごとのowner、座標系、depth、clip、blendを表にし、入力fixtureのwindow geometryを独立計算できる |
| HD eligibilityとprovider/INI owner | 各globalの設定元・寿命・意味と、Java設定への対応表。fallback・読込失敗の結果をケース別に確定 |
| native tabのカテゴリとglobal状態 | 通常SelectPlayの5/6構成・平文enumの意味・Artist/CreatorのSort連動は実装済み。配置／animation／未対応カテゴリ機能／localized表示を追加比較。暗号化文字列は復号しない |
| sprite/dispatcherのhit | hover/down/upそれぞれの候補集合・優先順・alpha/clip/矩形境界・focus時の寿命を確定 |
| Backとsprite clock | 初回epoch、dimension更新抑止、frame番号、再生成/再表示時の継承を確定 |
| preview/背景/遷移 | 同path扱い、seek、loop、fade、clock、非同期完了と復帰の更新順を確定 |

ILで閉じられない項目は、公開wiki/lazerの根拠と対象stable観測のどちらが必要かを記録する。
lazerの実装をそのままb20230727.9の契約として扱わない。

## 設計上の境界

既存のBrowser、Skin、Screen、Renderer、Ruleset、GameClockの責務を維持する。
変更は既存クラスの小さな計算・状態・描画単位へ置き、新しい汎用scene graphや入力engineを導入しない。

- Browserは難易度統計・分類・検索・selection/focus/行IDを担当する。描画の都合でデータを変更しない。
- Skinはpath/provider/density/INI owner/素材寿命を担当する。画面の操作やGameplay状態を変更しない。
- Rendererは確認済みのgeometry・depth・clip・blendを描く。入力候補の選択順を描画順で代用しない。
- Screen/Inputはイベント消費順、hover/down/up、画面境界、UI/preview時計を担当する。
- Rulesetは評価・Mods、Gameplayは実行、GameClockはGameplay時間を担当する。

`AssetFile.logicalSize()`はGameplay/Resultsにも使用されるため、P3aではSong Selectの利用箇所を先に揃える。
共有resolverの変更が必要なP3bは独立commitにし、Gameplay/ResultsのSkin回帰も実行する。
720基準の共通`UiLayout`を一括変更せず、nativeの480/768系座標への変換を該当部品で明示する。
drawとhitが同じsprite geometryを参照する部分は、同じ計算結果を使い別のalpha推定式で再配置しない。
未対応機能を見た目のために有効化しない。外観契約と操作能力の不足を別々に報告する。

## 検証と完了管理

各バグ修正に、実装をそのまま写した期待値でない回帰テストを追加する。
モデル・計算はGL不要のテスト、素材は実PNG/INI decode、重なり・crop・filterは既存OpenGL harnessで検証する。
sceneの全組合せを毎回撮影せず、変更が影響する境界sceneと通常sceneを選ぶ。
全体マトリクスはP10で実行する。

| 領域 | 主な既存検証先 |
| --- | --- |
| 分類・検索・選択 | `SongBrowserModelTest`、`SongBrowserRowsTest`、`SongBrowserQueryTest`、`SongBrowserSearchIntegrationTest`、`SongBrowserFocusTest`、`SongSelectNavigationTest` |
| Skin・寸法・配置 | `SkinAssetResolverTest`、`SongSelectSkinAssetsTest`、`SkinConfigurationOwnershipTest`、`SongSelectToolboxLayoutTest`、`SongSelectChromeTest`、`SongSelectReferenceGeometryTest` |
| 入力・時計 | `SongSelectMouseDispatchTest`、`SongSelectPointerTest`、`SongSelectKeyboardTest`、`SongSelectWheelInputTest`、`SongSelectRowLifecycleTest`、各animation test |
| 音声・機能・共有Skin | `SongSelectPreviewTest`、`SongSelectAudioTest`、`GameplaySkinAudioTest`、`OsuSkinAssetsTest`、`ResultsSkinAssetsTest`、該当Ruleset/Gameplay test |
| 描画 | `lwjgl3/src/hudHarness/java/dev/osujava/ui/SongSelectVisualHarness.java`の既存sceneと追加自作fixture |

関連テストを実行してから原則`./gradlew build --offline --console=plain`を実行する。
描画変更では`xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain`を使い、
変更単位に応じた`-PsongSelectPhase`と`-PsongSelectOutput=/tmp/...`を指定する。
新しいphase名はharnessへ追加した時点で記録し、未実装のtask/optionを既存コマンドとして扱わない。

各変更の報告には対象ID、IL/観測根拠、変更内容、テスト/build/capture結果、残る差、commit SHA/messageを記載する。
差分を確認し、無関係な変更を含めずcommitする。自分の未コミット変更を残さない。
台帳の項目は「契約確定→実装済み→Java検証済み→stable比較済み」の段階を記録し、条件別の未判定を残す。
33項目のうち未実装・未確定・機能依存・実機未判定がどれか追えることを完了管理の基準にする。

P10の最終合格は同一入力列の状態一致、確定した座標/丸め/時間契約の一致、同条件のpixel/audio比較を必要とする。
許容差は測定前に根拠とともに決め、全面的なfont/chrome maskで差を消さない。
工期は未確定契約と機能依存が大きいため固定日数を置かず、各変更単位を閉じた時点で残量を更新する。

計画作成時の検証: 台帳33 IDの記載、参照テスト22クラスとリンク22件の存在を確認。
`git diff --check`と`./gradlew build --offline --console=plain`は成功。
文書のみの変更のためbuildの15 tasksはすべてUP-TO-DATEで、既存テストは再実行されていない。
