# Song Select 1:1再現のための再調査（2026-09-29）

## 結論と今回の範囲

目標は b20230727.9 の見た目と挙動の1:1再現。ユーザーの追加指示により、必要な大規模改修を許容する。
既存クラス、spring、独自の描画・操作方針の維持は前提にしない。今回は調査のみで、製品コードは変更しない。

調査した範囲では、Browserの行モデル、Carouselの運動、入力処理、行の状態別描画は構造的な変更が必要。
既存の座標定数を調整するだけでは対応しない。フォントと画面全体の合成にも基盤変更の候補がある。
一方、libGDX・Import・Gameplay全体の置き換えを必要とする証拠は得ていない。
改修規模はこの必要性に従って決め、画面全体の再実装も選択肢に含める。

「同じ内部クラス構造」を目標にせず、同じ入力・データ・設定・時刻で同じ状態と表示になる契約を独立実装する。
完全ローカル、通常Input APIによるDebug Auto、Import / Gameplay / Renderer / Ruleset / GameClockの責務分離は維持する。

## 対象と証拠の扱い

- Java基準: `53c52110931962cb9a2f8c283b10cd202ac71f51`。調査開始時のworking treeはclean。
- 参照ディレクトリ: `/home/coder/workspace/b20230727.9`。
- `osu!.exe` SHA-256: `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
- `osu!ui.dll` SHA-256: `a48f314c7ff381dfdd4fa16122accce45a397d0eb92afe5230aa999636358632`。
- `osu!gameplay.dll` SHA-256: `ba3467a8db908d81a0729f78fdc5c8f1d1595d3da4e5a9a34be9a16e06da9f87`。
- 今回は `dnfile` / `dncil` によるexeのメタデータ、メソッド本体、フィールド参照の読み取り。
  難読化された名前はtokenと参照関係で追跡した。保護回避、クライアントの改変・実行、公式asset抽出、暗号化文字列の実行時復号は行っていない。
- 下記tokenはこのexe専用。offsetはdncilのmethod body表示に従う。数値だけで意味を割り当てず、利用側・代入側まで確認する。
- **静的確認**は、そのメソッドの分岐・参照・計算を読んだこと。**設計判断**はJavaとの差から導く提案。
  実際の描画結果・実機時系列の一致は今回未確認。未確定の分岐を静的確認に混ぜない。
- [過去の起動試験](song-select-repair.md#stable-runtime-limitation)はSong Selectへ到達していない。
  今回は起動再試行をせず、この制約を解消した扱いにはしない。

## 1. Browserは「選択Setだけを展開したリスト」では足りない

### 今回の静的確認

| 参照 | 確認した意味 |
| --- | --- |
| Row TypeDef `02000219`、constructor `06000fb0` | 譜面参照 `04000862`、親行参照 `0400086a`、グループ識別に使う整数 `04000863`、位置 `04000880`を持つ |
| `04000882` のField signature、`06000fd4/0fd5` | 寸法ではなく値型の状態フィールド。TypeDef `020001d4` はenum（列挙値の名前は残っていない）。状態setterが旧状態を渡して描画遷移を発火する |
| `06000fc3` | 表示候補には除外flagがfalse、状態>0、親がある場合は親の状態>=3かつ親が除外されていないことが必要 |
| `06000fc5`、`06000fcd/0fce` | 前者は状態<=0の判定。後者は状態>=3を開状態として扱い、開閉操作は状態1/2と3を切り替える |
| `06003268` | 親が閉じている／除外された行を状態0にする。選択行は4、選択譜面と同じ文字列キー `0400259b` の行は3、別のflagによる行は2、代表行は1、それ以外は0にする |
| `06000fb6`、`06000fd3` | 代表行は自身と `04000869` の同一性で判定。非表示から表示へ戻る非代表行は代表行の表示位置を継承する |
| Group TypeDef `020004fa`、`060025e2/25e5/25e8` | 親Rowを継承し、子リストとラベルを持つ。件数をラベルへ反映し、カードspriteと文字を生成する |
| `0600326c` `00ca–018a` | Groupを選択すると以前のGroupを閉じる分岐、現在のGroupの開閉、子の選択index再対応、Group用callbackがある |
| `0600325a` | 全行に位置配列を作る一方、表示候補だけを48単位ずつ進める。非表示行は単純にリストから消していない |

状態2のflagの全意味、`0400259b`の文字列生成元、全Group生成経路は未確定。
状態の便宜名をstableに残っている正式名称とは扱わない。

### Javaとの差と必要な変更

`SongBrowserModel.expand()` は、非選択Setを1枚のSET、選択SetをDIFFICULTY群へ毎回投影する。
GROUP_HEADERは飾りで、`SongSelectLayout.row()`はsetIndex<0のhitboxを空にし、Rendererも区切りとして描く。
stableの開閉できるGroupカード、非表示行、代表行、keyboard focusはこのモデルに収まらない。

Browserの内部を、永続した行identity・親子関係・代表行・除外状態・行状態・選択・focusを表現できる形へ再設計する。
表示snapshotは引き続き必要な行だけでよい。内部データ構造の逐語的な移植は不要だが、可視行だけを唯一の状態にしてはいけない。
検索後の選択補正と展開状態もこの契約に含める。

## 2. 入力はfocus・選択・押下・描画の時系列まで対応が必要

- `06003277`は同一グループ識別子の候補を直接選択する分岐と、`04001f6b`へfocusを保存する分岐を持つ。
  `06003259`はfocusの色を調整し、`0600324c`は色を戻してfocusを解除する。
  `06001965`まで追い、focus色の処理はRGBを1.4倍して255に制限しalphaを保持することを確認した。
  50という遷移引数の呼出先・時計との最終対応は別途追跡する。
- `06003276`は全行を循環し、除外flag・Group型・グループ識別子・状態を条件に数える。
  最初のindexへ一周した場合にもdestination処理を呼ぶ。単純な可視行配列上の±10とは同値でない。
- `06003243/3244`は押下候補を保持し、解放時にdrag状態と候補spriteの包含判定を確認する。
  選択済み行、Group型、行状態、別入力flagによって選択／開始／別callbackへ分岐する。
- `06000fbc`は行の背景spriteが持つ矩形 `04002860` を `06005140`で判定する。
  現行Javaの「固定の行pitch矩形をviewportでclipしたhitbox」と同じとは確認できない。
  stable矩形の生成・更新経路と入力座標系を次に閉じる必要がある。

Javaではkeyboard callbackがBrowserを即時変更する一方、行・下部ボタン等は`Screen.update()`内のpollingで処理する。
`SongSelectCursorInput`のevent queueはcursor用であり、画面全体の操作event queueではない。
さらにpreview更新→layout更新→pointer操作という順番なので、keyboardとpointerでpreview反映タイミングが変わり得る。

**設計判断:** Song Selectの入力を時刻付きeventとして一か所で順に処理し、focus・選択・press候補・pointer capture・overlayの優先順位を明示する。
stableのイベント順はdispatcherと時計を追って確定する。単にJavaの順番を入れ替える修正では終えない。
既存の「短いclickがcursorへ届く」テストを、行・ボタン・score・overlayの操作まで拡張する。

## 3. Carouselは運動モデルごと置き換える

今回 `06003253/3254/3255/325a/325d/325e/325f/3260/3266` を読み直した。

| stableの契約 | 現行Javaとの差 |
| --- | --- |
| 速度の指数減衰と積分。drag中と解放後で処理が変わる | `SPRING_RATE=18`を使うviewport springと別の速度減衰 |
| 行X/Yはそれぞれ0.95/0.875を時間指数に使う補間 | 展開spring、hoverAmount、groupAmount等の独自補間 |
| dragの速度は経過時間・速度の符号と大きさで平滑化を変える。解放時にも経過時間依存の減衰がある | `dragBy`と固定slop中心の処理 |
| 初期位置200、表示候補のpitch48、条件付きgap10 | selectionAnchor始点、Group境界をrowStepの.38/.69倍で扱う |
| Group型でX−50、譜面参照ありかつ状態>=3でさらにX−50、hoverでX−45 | viewport幅×.052のgroup indent、selected indent3、独自X clamp |
| curveは表示中のY＋scroll＋予測残移動量に依存 | 静的curveに独自のvelocity influenceを加える |
| rebuild時は以前のscroll fraction×旧content travelを、新travelで正規化し直す | anchor継承による独自のscroll補正 |

`0600325a`のgap10は「非GroupからGroupへ入る場合」と、譜面参照のある行に関する開状態の境界条件。
一律のSet間隔やGroup余白には変換しない。`06003253`には距離二乗6400との比較もあるが、座標系と入力条件が閉じるまで
「drag閾値80px」と断定しない。

**設計判断:** Carouselのpublic契約も変更可能とし、行stateと運動stateを共有するシミュレーションへ置き換える。
480基準の位置・ms単位の速度・60Hz換算の補間時間を明示する。Javaの720基準viewportを残すかは境界変換の簡潔さで決める。
描画から時間を進めず、入力時刻とsnapshot時刻を指定して軌跡を比較できるようにする。

## 4. 行の見た目は状態別spriteと時間変化から組み直す

### 星表示の新たな確定差分

- `06000fb1` `000c–000e`で `04000855=10`。
- `06000fd2` `013b–0202` と `0207–02da`はこの数だけ背景層と前景層を生成する。
- `06000fbd` `0026–003f`は評価値を10で上限処理。以後各層へ値を反映する。
- 同メソッドは描画方式flag `04000881`で分岐し、scale／cropの異なる処理、500の遷移引数、
  index×80＋50の開始時刻ずらしを持つ。flagの全設定経路とeasing番号の対応は未確定。
- Java `Stars.of()`は最大9個、`drawStars()`はその時点の値を描く。独自の狭幅圧縮と数値優先もある。

従来の「9枠を維持する」は互換仕様ではない。10枠の背景・前景、部分星の形、出現順序、評価変更時の中断を含めて置き換える。
数値表示の有無・配置もstableの文字経路で確認する。

### 色・foreground・状態遷移

`06000fb1`と`06000fda`から、状態4はRGBA(255,255,255,220)、状態3は(0,150,236,240)、
その他は別predicateにより(235,73,153,240)/(233,104,0,240)の色を選ぶ経路を確認した。
これらはspriteへ渡す色であり、画像の色・後続のtransform・blend後の最終pixel色とは区別する。
JavaのSELECTED/SIBLING/PLAYED色は別値である。

`06000fd2`は状態の閾値をまたぐとforeground群の生成・削除と遷移を行う。
`06000fd3`は表示位置の継承を行い、`06000fe2`は状態とforegroundの種類ごとに文字色を分ける。
Javaの単一revealAmount、固定のsibling alpha .24/.20、selectedを最後に描く二周の描画では同値性を保証できない。

**設計判断:** 行を一枚のrectangleとして扱う現行presentationを改め、背景、thumbnail、title、byline、difficulty、grade、mode、星を
別の表示要素と時間状態として表現する。画像のnative scale修正は再利用可能だが、各要素のorigin・depth・blend・clip・hit契約は再監査する。

## 5. フォントと画面全体の合成は別の調査軸が必要

### 文字経路

Row/Groupの文字constructor `060020a6`はTypeDef `0200046b`。
その `060020c5/20c7/20ca`が `0600306d`へ到達する。
`0600306d`内にSystem.Drawing.GraphicsのMeasureString、DrawString、TextRenderingHintへの実参照がある。
`060020c7`には表示高さ/768、表示高さ/480、.625の変換もあり、文字の測定単位と画面座標を分けている。

Javaの`SmoothUiFont`はAWTの環境依存SansSerif、17×scaleの整数サイズ、2倍描画、独自ellipsis・余白を使う。
したがって、baselineを動かすだけでは字幅・weight・改行・省略・Unicode fallbackまで一致しない。
stableのfont family、サイズ、fallback、文字測定・描画flags、影・縁、DPIと丸め規則は追加調査が必要。
公式font assetは抽出しない。合法的に用意できる同一fontが必要な場合は比較条件として明記する。

**設計判断:** Song Select専用の文字metrics/rasterization adapterを候補とする。
AWT設定で十分か、別の文字描画基盤が必要かは同一文字fixtureの実測で判断する。
フォント差を全域maskして「pixel一致」と判定してはいけない。

### 合成・入力範囲・追加UI

Javaは行→chrome→metadata/score→selection→Cookie→Back→各label→overlayの固定passを持つ。
直近のselection .95/.96等の修正は有効な証拠だが、全spriteの順序を確定したことにはならない。
stableのconstructor、sprite manager、同depth時の順序、foreground移動を一つの表示要素台帳へまとめる必要がある。

Javaには次の独自方針が残る: chromeの40%/30%予約上限、固定slotへ限定したhitbox、topの「画像の最後の列」延長、
巨大複合画像で補助UIを移動する判定、常時表示のImport/Debug/local sets、独自Cookie、中央粒子とcosベースの拍波形。
これらを「安全なので維持する」とは決めない。メモリ予算・不正入力防御と、正常なskinの見た目・操作を変える方針を分ける。
追加の開発用表示は通常の比較画面から分離する設計が必要。

**設計判断:** Song Selectに限定した表示要素の合成順・transform・clip管理へ再構成する。
汎用scene graphやアプリ全体のrendererの再実装はまだ必要と判断していない。

## 6. 検索・分類はUIだけでなくデータ契約にも不足がある

`06001376`のlocalisation呼出を直接追い、No Grouping、Artist、BPM、Creator、Date Added、Difficulty、Key Count、
Length、Mode、Rank Achieved、Title、Collections、Favourites、My Maps、Ranked Status、Recently Playedの候補を確認した。
これは候補構築の証拠であり、すべてが同じdropdownや全modeに出るという意味ではない。
各候補の条件・実行処理は別途追跡する。

`060013a6`は引用符と空白を区別してqueryを分解し、`060013a7`にはRegex.Matchとcaptureの処理がある。
`060013a3`にはSearching表示、row filter、選択再設定の経路がある。
検索構文の全field、比較演算子、引用符の境界動作、検索の待ち時間・実行threadまでは未確定。

JavaのQueryは単純な空白tokenの部分一致、Sort/Groupは5種類、検索対象のIndexはSet中心。
ParserはUnicode metadataを一部保持するが、Row/Detailsはromanised Set情報中心である。
Tags/Source等の検索metadata、collection/favourite、分類に使う日付、mode別評価、scoreのMods/replay等はデータ契約の拡張対象。
「検索UIの追加だけ」「schema変更不要」という旧方針は採用しない。

Import→Library永続化→検索index→行projectionまでの情報保持を設計する。
オンライン由来の値を勝手に生成せず、ローカルで得られる値と未取得値を区別する。
評価の計算がstableと違えば星・順序・フィルタも違うため、見た目のfixtureでは同じ評価入力を固定し、
実譜面の評価互換はRuleset側への独立した依存として追跡する。

## 7. 音声・背景・メニュー遷移の未確定事項

Javaのpreviewはpath変更時に即座にdispose/newMusic/play、未指定PreviewTimeは0、全体loop。
背景は新しい画像へ切り替え、一定時間でfade。中央装飾は独自波形。これらはstable互換と確認されていない。
選択callbackからpreviewスケジューラ、音楽clock、背景ロード、拍処理への経路を次に追う。
同一audioの別難易度、選択連打、ロード完了の逆転、無音・壊れた音源、画面退出、Gameplayからの復帰を比較する。

Optionsは現行Javaでは通知のみ。通常Modsはtoggle不可。Mode、score/replay、collection操作、editorへの遷移も
表示・操作・遷移先の実機能を分けて台帳化する。Song Select全体の再現に必要な依存は別実装単位として扱い、
ボタンだけ表示して機能対応済みにはしない。他rulesetのGameplay全実装を無条件に今回の画面改修へ混ぜない。

## 8. 改修規模の判断

| 対象 | 判断 | 根拠／変更範囲 |
| --- | --- | --- |
| Browser内部・可視行projection | 再設計が必要 | 親子Group、代表行、状態0〜4、focusを現行APIが表せない |
| Carousel | 運動モデルの置き換えが必要 | 状態依存配置、指数運動、drag/release、異なるX/Y補間 |
| Screen/Input/ViewState | 制御の再構成が必要 | callbackとpolling、preview反映、押下候補の時系列が分散 |
| RowPresentation/Renderer | 状態別表示とanimationの組み直しが必要 | 10枠の星、foregroundの生成・更新・破棄、色、Groupカード |
| 画面composition/skin geometry | 再構成が有力。全depth/hit経路を追加調査 | 固定passと独自予約・slot制限が正常skinにも影響 |
| Font | adapter変更が有力。backend変更は実測判断 | GDI+系経路とAWT SansSerifの測定・描画差 |
| Library/metadata/score | 必要なschemaとindexの拡張 | 分類・検索・score表示が要求する情報不足 |
| Preview/artwork | schedulerと時間状態の変更候補 | 現在の即時切替・独自fadeでは契約不足。stable経路は未確定 |
| libGDX/Gameplay/Import全体 | 全置換の根拠なし | 問題は現状Song Select契約に限定できる。必要な依存だけ変更 |

## 9. 改修着手前に閉じる調査と検証基準

今回の再調査で構造変更の必要性は判断できたが、全分岐の仕様復元は完了していない。
次の調査は「今のJavaを直せる定数探し」ではなく、以下の契約を閉じる作業とする。

1. **状態と入力:** 状態2・代表行生成・全Group種類・親子構築・filter/selection修復、focus確定、右click・修飾キー、pointer ownership、repeat、dispatcherの順序。
2. **表示要素台帳:** 全要素のowner、data、origin、coordinate space、scale、depth、blend、clip、hit、時間、skin/provider分岐、fontを列挙。metadata/search/tab/score/Mode/Mods/Options/Back/Cookie/scrollbarも含める。
3. **描画と音の時系列:** 星・thumbnail・背景・hover・focus・展開・入退場の開始／中断／遅延、previewと拍、非同期ロード結果の適用順。
4. **実機基準:** 正常起動できるb20230727.9をネットワーク遮断環境で通常起動。同じskin・譜面・audio・font・設定・解像度・入力列を使い、時刻付きcaptureを取得。保護回避やupdater接続はしない。

比較条件は1024×768、1280×720、1920×1080、高DPI、SD/@2x、複数skin Version、透明／欠損／巨大複合skin。
0/1/少数/多数の譜面、Group開閉、混在mode、検索中の選択、Unicode・長い文字列、scoreあり／なし、連打とdrag中断を含める。
時間比較は30/60/144Hzと不均一frame timeで行う。stableにframe依存の挙動が見つかれば、それを隠して同時間一致を強制せず差分を仕様として記録する。

受入判定を次の三つに分ける。

- **状態:** 同じ入力列でselection/focus/expanded group/filter/play requestが一致。状態テストで証明する。
- **幾何・時間:** 同じ時刻で要素のbounds、色、alpha、clip、sprite状態が一致。静的計算fixtureと実機traceの両方で確認する。
- **画像・音:** 同じ条件のnative解像度captureで領域別差分を比較し、操作音・previewの開始位置とタイミングを確認する。
  font/codec/GPU差は別記する。許容誤差は測定条件から先に定め、失敗後に広げない。

公式asset抽出禁止と完全ローカルは引き続き制約。独自診断skinによる一致と公式標準画面の一致は区別する。
独自fallback画像や未取得のオンライン情報を使う領域はpixel一致と呼ばない。
実機が得られなくても静的仕様に基づく実装は進められるが、1:1再現の最終認定は未完のままとする。

## 10. 既存テスト・資料の扱い

既存テストはリソース解放、破損入力、identity、責務分離の回帰として有用。
一方、spring、9枠、非操作Group header、独自alpha、chrome予約、固定hitboxを正解として固定したテストは互換性の証拠ではない。
変更時にはstable根拠を持つ期待値へ置き換え、旧テストを通すために非互換を残さない。
既存visual harnessは同一fixtureと入力traceを受け取る方向で拡張し、製品API経由で検証する。
現行`tools/songselect_visual_compare.py`は並置画像の作成であり、pixel差分や時間一致の判定器ではない。

旧資料の「critically damped springを維持」「schema変更不要」は本調査の方針に置き換える。
旧資料が`06000fc5`を「nonpositive layout extent」と呼ぶ箇所は誤りで、正しくは**状態enumが0以下かの判定**。
過去の実装修正とテスト結果は履歴として残し、今回の仕様確定・互換合格へ読み替えない。

## 検証記録

製品コード・assetの変更なし。参照バイナリはread-onlyで調査し、ハッシュを記録した。
本調査文書と旧方針への注記だけをcommitする。

- `./gradlew build --offline --console=plain`: BUILD SUCCESSFUL、13 tasksすべてUP-TO-DATE。
  test taskもUP-TO-DATEであり、今回テストを再実行したという意味ではない。
- 既存JUnit XML: 95 suites、723 tests、failure/error/skipped各0。過去結果の確認として記録する。
- 文書のみの変更なので新規テスト・visual harnessは追加実行していない。
- `git diff --cached --check`、文書diff、追加文書のローカルリンクを確認済み。stableとの実画面・音声比較は未実施。
