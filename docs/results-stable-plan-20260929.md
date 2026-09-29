# stableの結果画面をosu!javaへ再現する計画

[指定buildのリバースエンジニアリング結果](results-stable-research-20260929.md)に基づく計画。2026-09-29時点では未実装。
対象は**b20230727.9、osu!standard、完全ローカル条件**での結果画面。
オンライン順位・pp・送信・Bancho連携は対象にせず、同じオフライン状態での比較を合格条件とする。

現行ResultsScreenの座標変更だけでは足りない。6判定、Perfect、HP、誤差統計、Mod、replay capabilityを受け取れるデータと、演出・入力の状態が必要。
一方、アプリ全体のUI frameworkやImportを作り直す根拠はない。結果画面に限定した部品を段階的に追加する。

追加監査に基づく[不足データ・機能の実装計画](results-missing-data-plan-20260929.md)で、生成元・保存形式・依存順・テストをP0–P11に分解した。
本書の段階1はsnapshot追加だけでは完了しない。break保持、時刻付き入力、ScoreV1、slider / spinner判定、combo set、HP / failまでが実プレイ一致の前提になる。
[追加解析](results-stable-followup-20260929.md)でHP係数・graphの分母・UR採取対象・spinner判定と統計・最大可能comboを特定し、詳細計画のP4–P7、P9–P10へ反映した。

## 段階0: 観測条件と未確定分岐を閉じる

実装前の最優先。静的解析は座標引数・統計式・分岐を明らかにしたが、最終画素・音・イベント順を観測できていない。

1. 指定hashのstableを通常の方法で起動できるWindowsまたは互換環境を用意する。既存の原本は読み取り専用とし、コピー・専用設定・別のデータ保存先を使う。
2. 起動前からプロセスの外部通信を遮断する。ログインせず、更新や認証を成立させるための接続・改変・保護回避はしない。
3. 自作の短いcircle-only譜面、slider break / tail欠損、combo set、spinnerを含む譜面を使用。音・背景・skinは自作または利用許諾を確認した同一素材を両アプリへ渡す。
4. 4:3 / 16:9、skin Version 1 / 2系、SD / HD条件を記録。build hash、OS、render backend、解像度、locale、時刻帯、skin hash、譜面hash、modをmanifestへ保存する。
5. 結果遷移開始から最低8秒をframe時刻付きで撮影し、クリック位置・押下/解放・key repeatも記録する。結果画面の画面撮影は観測資料に限る。

最小の観測matrix:

| 軸 | 必須case / 判別する仕様 |
| --- | --- |
| 結果種別 | manual完走、Auto、保存結果閲覧、ローカルreplay終了、replayなし。Retry / Replay / 拡張パネルの有無 |
| 判定 | SS、S、A–D、50あり、missあり、Geki / Katuあり。6枠とgrade |
| Perfect | accuracy100%かつcombo不足、非100%のFC、slider break、tail欠損。Perfectをaccuracyから推定しない |
| 境界 | 300比率60 / 70 / 80 / 90%ちょうどと隣接値、50比率1%、0判定。Singleの丸めとCLR実測を照合 |
| skin | staticのみ、frame 0のみ、static＋frame 0、欠損、透明余白、異なるprovider、ScorePrefix / ScoreOverlap |
| 入力 | Enter / Space / Esc、演出中と完了後のRetry / Replay、F2、Back、wheel、拡張ボタン、短いclick、重なり |
| 統計 | HPとURを独立に有無切替、負のみ / 非負のみ / 0含む誤差、DT / HT、spinnerなし / あり |

**完了条件:** 各未確定項目を観測事実へ更新するか、再現対象を明示して未解決として残す。
実機条件が揃う前も段階1以降の独立fixtureは作れるが、その成功を1:1達成とは呼ばない。

## 段階1: 結果データを固定する

`ResultsScreen`がGameplayを読み直すのではなく、終了時のimmutable snapshotを受け取る。
命名案は`ResultsSnapshot`。当初から大きな汎用frameworkにせず、以下を一つの結果モデルと必要な小さいrecordで表す。

- identity、譜面表示名、作成者、ローカルプレイヤー表示名、実際の日時、ruleset、score方式、mods。
- 6判定数、最大コンボ、total score、accuracy、grade、passとPerfect。ただし未収集・旧記録の値はunknownを表せるようにする。
- HP点列、任意のhit error / spinner統計、ローカルreplay参照。空・未収集・保存時に失われた状態を0で埋めない。
- entry context（プレイ終了 / 保存結果閲覧 / replay終了 / Debug Auto）と、実データに基づく可能操作。

RulesetがGeki / Katu、slider全体判定、combo、Perfect、passを決める。Gameplayが必要な時系列を集める。
Rendererはsnapshotを描くだけにし、判定数からHPやURを捏造しない。
既存`ScoreTracker`のscore計算とslider countにはstableとの差があるため、**表示fixtureの一致と実プレイの結果値の一致を別に検証**する。

詳細計画のP1–P7で生成元を整え、P8で終了理由とsnapshotを一度だけ確定する。
音源EOF・全object判定済み・pass・abortは区別する。現在の`clock.finished()`→`finish()`による強制MISS確定を通常の完了判定として流用しない。

`LocalScoreStore`は詳細計画P9のschema 2と旧レコードの読込互換を備えて拡張する。未知の値は利用不可として扱い、既存の値とgrade算出の根拠・日時を保持する。
旧方式のscoreをScoreV1として再計算せず、譜面内容hash・計算方式・採取できた情報を識別する。
保存は今と同じくGameplay終了処理で一度だけ。保存結果を開く・Retry・画面を再生成することで重複保存しない。
Debug Autoは通常Input API経由を維持し、ローカルランキングへの保存除外も維持する。

検証: 6判定とaccuracy分母、FCとaccuracyの独立性、old schema、unknown、二重保存、Auto除外。
統計の採取条件は段階0で確定したcaseをRuleset回帰テストにする。

## 段階2: 静止状態のRendererとSkin

結果画面だけに480高の座標変換を設け、左・右anchorとsprite originを明示する。
既存SkinAssetResolver / SkinConfiguration / 数字描画の利用可能部分を使い、必要なら`ResultsSkinAssets`と`ResultsLayout`を小さく追加する。
Song Selectの並列変更を前提に共有クラスを無条件に置き換えない。

実装順はheader / panel→scoreと6判定→combo / accuracy→grade / Perfect→mods / buttons。
ScorePrefix / ScoreOverlap、SD / HD論理寸法、static対frame 0、provider、旧 / modern分岐を画面固有の契約として扱う。
`ranking-*`画像の欠損をRendererの都合で無条件なUiTheme panelに置換しない。

検証: 同一custom skinで旧 / modern、4:3 / 16:9、透明余白、極端な桁数、長い譜面名、non-ASCII、異なるlocaleを撮影。
観測と異なるassetを使った箇所は画素一致の対象外と明記する。公式built-inを抽出して補完しない。

## 段階3: 時計・演出・入力

clockの責務はGameClock境界に置く。Gameplay終了後にも進められる結果表示用の時刻を、描画frame数に依存せず読み取る。
テスト時は固定clockを注入し、score桁確定、項目ごとの遅延、grade、mods、HP graph、拡張panelの時刻を検証する。
`UiTransition`一つで全要素をfadeさせる構成から、結果画面内の状態計算へ移す。

入力は時刻付きの通常イベントとして処理する。snapshot表示と同じ時刻基準でskipを適用し、操作ごとの違いを保持する。
特にRetryはskipの戻り値に関係なく遷移する経路、Replayは演出中の初回がskipだけになる経路を検証する。
Enter / Spaceの画面handlerとglobal shortcutの関係を観測で確定し、現行の「即Song Selectへ戻る」を流用しない。

検証時刻: 基準直前、300ms周期の前後、499 / 500 / 501ms、4000ms、4400ms前後、4999 / 5000ms、skip直後。
乱数のseedが外から揃えられない場合、未確定桁領域は画素完全一致の判定から明示的に除外し、文字集合・確定順・時刻を検証する。
確定後の数字はmaskしない。音名・開始時刻・重なりは観測後に対応し、音素材も既存の許諾済みskinから解決する。

## 段階4: Graph・詳細統計・保存スコア閲覧

HP graphは収集されたHP列から作り、thinning、境界色、累積長による4000msの描画を再現する。
点の値は`min(1, 実際のHP / object別の全成功基準HP)`。Ruleset側で採取した値を渡し、Rendererで固定200による再正規化をしない。
URは負側/非負側平均、母標準偏差、譜面時刻基準を守る。graphの画像・線・tooltip hitboxを分ける。
URの表示は統計の有無とentry contextの両方で判断する。単なる保存結果閲覧で表示せず、直後またはreplayによって採取できた場合の条件を再現する。

Song Selectのlocal score行から、同じ結果画面へ「保存結果閲覧」のcontextで入る経路を追加する。
元の選択譜面・browser状態へ戻せるようにし、保存済み結果の表示で新しいプレイを保存しない。
Replayを表示するにはローカル記録・読込・通常Input APIによる再生の実装が必要。
詳細計画P10で、入力記録→決定的再生→`.osr`読込・書出しを別単位にする。F2 / Save Replayを含む一致には書出しの検証も必要。
未実装のボタンを成功したように振る舞わせず、圧縮replayの解析は明示的なImport境界に置く。

ローカル拡張領域は観測されたguest名 / local ranking / replay保存等の条件で構成する。
online情報・通信を要求する操作は実装しない。オフライン表示にない独自ボタンを追加して1:1と呼ばない。

## 段階5: 統合と合格基準

| 検証層 | 合格条件 |
| --- | --- |
| データ | 同じ譜面・通常Input列から6判定、score、combo、accuracy、grade、Perfectが一致。未採取の項目を架空値で埋めない |
| 配置 | 同一skin / resolution / locale / 内容で、各anchor、寸法、桁間隔、clipが一致 |
| 画素 | 安定表示時の差分画像と数値を保存。背景・font・SD/HDを揃える前の画像から合格を出さない |
| 時系列 | 同じ入力・時刻でskip、項目表示、graph、grade、buttonが一致。実録frame時刻の誤差範囲も記録 |
| 操作 | 初回・連続押下、演出中、閲覧中、replayなし、保存失敗、resizeで遷移とhitboxが一致 |
| 分離 | Renderによるscore更新なし。GameClockと通常Inputを経由。production接続なし |
| 回帰 | 関連unit / integration tests、`./gradlew build`、既存Song Select / Gameplayの必要なharnessが成功 |

画素誤差や許容時間を先に都合よく設定しない。まずstableの同条件再撮影どうしの再現性を測り、renderer差と非決定的部分を記録する。
数値・入力の一致と画像の一致を別々に報告する。課題を残した段階は「部分再現」と表記する。

## Gitでの実装単位

各単位を専用worktreeで実装・diff確認・テスト・commitする。mainへのmergeはこの調査に含めない。

1. 観測manifest・比較fixture・確定仕様の追記。
2. 結果snapshotの契約と、詳細計画P1–P8の生成元。Ruleset・clock・統計は機能ごとに分割し、回帰テストを付ける。
3. 結果skin解決と静止Renderer。
4. 時刻駆動の演出・skip・button入力。
5. HP graph・tooltip統計。
6. 詳細計画P9–P10の保存互換・結果閲覧・ローカルreplay・osr・拡張領域をそれぞれ独立commit。
7. 同条件stable比較で見つかった差の小さい修正。

各実装報告には、変更内容、根拠、テスト/build、未解決差、commit SHA / messageを残す。
現時点の最大の前提不足は**指定buildの結果画面を実行観測できる環境**と、**現行Gameplayにまだない結果統計**である。
最初の到達点はNoModの結果一致。Modを含む実プレイは詳細計画P11の機能群ごとに広げ、未対応条件を残したまま全条件の1:1達成とはしない。
