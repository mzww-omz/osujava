# Song Select スキン残差の精密検査

検査日: 2026-09-28。対象実装: `3003bad`。実装は変更していない。
前回の追加機能も再検査し、スキンを構成する透明画像・余白・複合画像・音・入力を対象にした。

## 根拠と限界

- **確定**: 現コードと仕様／内部観測の不一致、または Java 側で再現した不具合。
- **独自方針・要比較**: Java の挙動は確認済みだが、stable の同条件での結果が未確定。
- **未実装／限定実装**: 対応経路がない、または画面だけで実機能がない。

stable `b20230727.9/osu!.exe` を今回も直接メタデータ／ILで調査した。
SHA-256: `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
今回の対象は Back コンストラクター `0x06003a15`、イベント側 `0x06003a16`、
`0x06003a19`、`0x06003a1a`、アニメーション `0x06001b08`、`0x06001b1b`、
変換オブジェクト `0x060042ec`、`0x0600382e`。公式画像・音源の抽出、
保護回避、難読文字列復元ルーチンの実行、production service 接続はしていない。

内部調査の追加確認:

1. カスタム Back 経路は同じフレーム配列から**二つのアニメーションオブジェクト**を生成し、
   両方を描画リストに登録する。二つ目にイベントと変換定義が付く。
   `0x06003a15` の `0073–019d`。変換引数には `1/255`、`0.4`、`0`、`250` がある。
   今回は型／フィールドの意味を全て解決していないため「正確に250msのhover」とは断定しない。
2. `0x06001b1b` は正の AnimationFramerate に対して `1000 / fps`、
   それ以外は `1000 / frameCount` を設定する。前回追加した基本フレーム周期には裏付けが得られた。
   起点、停止、画面を跨ぐ継続、hover中の扱いは別問題である。
3. Back のイベントから二要素の文字列配列を音声側メソッドへ渡す経路がある。
   文字列の中身・優先順位は未確定。Java の単一 cue が完全一致するとは言えない。

stable は今回起動していない。以前の Wine 起動も Song Select まで到達していない。
したがって以下の Java 再現結果を「stable実機との画像差分」と呼ばない。
WhiteCat／Seoul の記録上の実ファイルもこの環境にはなく、実スキン横断の合格率は出せない。

公開根拠:

- [osu! wiki: skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini)
- [osu! wiki: Interface](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection)
- [osu! wiki: Sounds](https://osu.ppy.sh/wiki/en/Skinning/Sounds)
- [公開lazer LegacyCursor](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Skinning/Legacy/LegacyCursor.cs)
- [既存stable内部調査](song-select-stable-reference.md)

## 優先度が高い残差

| ID / 判定 | 現在の挙動と再現条件 | 影響／根拠 | 次に必要な対応・観測 |
|---|---|---|---|
| S01 確定・可読性不具合 | `skin.ini`なし・カーソル画像だけのスキンで、選択行が黒文字＋暗い同梱行画像になる | 今回の実画面キャプチャで確認。前回の既定黒文字修正と暗いfallback画像の組合せで表面化。`SkinAssetResolver.readSelectedConfiguration`、`SongSelectRowRenderer.drawRowLabel` | 選択スキンが指定した色を尊重しながら、既定アセット一式の配色整合を設計。黒文字指定を一律白へ置換しない |
| S02 確定・画面間不整合 | 選択フォルダーにiniなし、fallbackに `Version:1 / CursorExpand:0` を置くと、選曲は `2.7 / true`、Gameplayは `1 / false` | Java probeで再現。選曲は `readSelectedConfiguration`、Gameplayは `readConfiguration`。同じ画像でも開始時にカーソルの操作感が変わる | 設定所有者を統一し、font/judgement等の既存fallback契約も回帰確認 |
| S03 確定・仕様差 | フォルダー名 `User` に `Version:1` を置くと1.0で読む | probeで再現。wikiではUserフォルダーはiniによらず最新扱い。`SkinConfiguration.read` はフォルダー名を見ない | User判定をversion解決へ反映。通常のVersion 1スキンと分離して検証 |
| S04 独自方針・要比較 | 全透明ボタンはクリック不可。alpha 15の全面画像も不可、16なら全面クリック可。160以上の領域があれば16–159の領域を除外する | `SelectionAssetBounds.detect`、`SongSelectToolboxLayout`。透明度だけで操作領域が変わることをprobeで再現。stableが同じ閾値を持つ根拠はない | 透明1pxで標準ボタンを消し、別の複合画像へラベルを描くスキンを実機観測。空洞も矩形化されるため単なるpixel-perfect hit testでもない |
| S05 独自方針・要比較 | `menu-button-background` のalpha>=160の本体を行サイズへフィットする。100×100で上下左右10px余白なら画像が行の1.25倍になる | probeでbody=.8を確認。`SongSelectBodyBounds`、`SongSelectRowRenderer.drawRow`。余白・影・額縁の変更が表示倍率と位置に作用する | native画像寸法、行間隔、クリック領域を個別に比較。35%境界を跨ぐ装飾変更でFULLへ戻る不連続も確認する |
| S06 確定・構造差 | Backは単一フレームを描き、明度を通常 .94 / hover 1 / 押下 .78へ即時変更 | `SongSelectRenderer.draw`。今回のstable内部は二層＋変換オブジェクト。連番再生対応だけではBack表現は完成していない | 二層の役割、ブレンド、hover開始／終了、押下／release、画面入退場をオフライン実機記録する |
| S07 独自方針・要比較 | `songselect-top` は上40%、`songselect-bottom` は下30%の範囲で切られる | `SongSelectChrome.topClip/bottomClip`、Renderer。前回外したのはselection-*とBackの制限だけ | 同じ大きな装飾でもファイル名で欠損が変わる。top/bottomの実際の可視範囲と重なり順を測る |
| S08 独自方針・要比較 | currentに行背景またはBackが一枚あるだけで、欠けたtop/bottomのfallback表示を抑制する。一方、見えないfallback topから配置予約は取る | `SongSelectSkinAssets.surfaceImage` と `topLayoutFallback`。一部だけ差し替えるスキンの背景／余白が連動する | row-only、back-only、top-only、全透明topを同じ条件で比較し、要素別のfallback契約を確定 |

S01のキャプチャ: `/tmp/osujava-skin-audit-missing-ini-capture/1280x720-1x-configured.png`。
黄色のテストカーソルしか持たないフォルダーを指定した。選択中の Difficulty 2 のタイトル・難易度が
暗い画像上の黒文字になっている。カーソル周辺の赤い軌跡は同梱fallback由来である。
この結果は「ini省略時の既定黒色という仕様が誤り」を意味しない。

## 画像、設定、読み込みの残差

| ID / 判定 | 精密検査の結果 | 根拠／影響 |
|---|---|---|
| S09 未実装 | ランキングの行背景に `menu-button-background` を使わず、固定色の矩形を描く | `SongSelectRenderer.drawScoreShapes`。wikiの同assetは選曲スコアボードにも使われる。譜面行だけ対応してもスキンの統一感は再現できない |
| S10 未実装 | `selection-tab`、Modeの各画像、`selection-mod-*` を選曲asset enumへ登録していない。Mode/Mods overlayも矩形＋文字 | `SongSelectSkinAssets.Image`、`SongSelectToolboxOverlay`。同梱フォルダーにはMod/tab画像が存在するが描画経路がない |
| S11 独自方針・要比較 | current normalがあればcurrent hoverだけを探索。normalがfallbackになる場合はhoverを独立解決 | 同じ見た目の欠損でも所有者によって挙動が非対称。normal-only／hover-only／壊れたnormalを比較すべき。現テストはこの独自方針を固定している |
| S12 確定・読み込み制約 | 大文字小文字を区別するLinux上で `Cursor.PNG` は `cursor` として解決されない | `SkinAssetResolver.resolveFile` とprobe。Windowsから持ち込んだスキンの互換リスク。両ケースが同居した時の優先順位も未定義 |
| S13 独自方針・要比較 | @2xを画面サイズ・ユーザー設定によらず常に優先 | `SkinAssetResolver.resolveIn`。SDとHDが異なる絵を持つスキン、HDだけあるスキンを分けて検証する必要がある。今回stableのHD選択条件は調べ切れていない |
| S14 確定・数値差 | 現行のselection配置幅は92/77 SD、既存stable ILは57.6/48の480基準、換算92.16/76.8 SD | 同じ224 SD始点からOptions始点はJava470対stable469.76 SD。720pで約0.225pxの差。旧Versionの上端87対86.4 SDも約0.5625px差。丸めや補間を含む画面上の差は未計測。wikiの推奨画像サイズを座標定数と同一視しない |
| S15 独自方針・要比較 | 3倍幅・2倍高さを超える複合画像でImport/status/debugの位置を変える | `SongSelectToolboxLayout.composite`。透過キャンバスを増やすだけでも補助UIが移動する。作者の描いたprofile/statusとの重なりは寸法閾値だけでは解決しない |
| S16 限定実装 | Backの512フレーム、全画像64MiPixel、1画像16MiPixel/8192pxの上限。末尾フレーム欠損／ロード失敗で連番を打ち切る | resolverと`SongSelectImageLimits`。既定fpsでは残った枚数で1秒周期を再計算する。大量Backフレームが先に予算を使うと、enumで後ろのカーソル類も読み込めなくなる経路がある。これはコード上の経路確認でありOOM実験はしていない |
| S17 限定実装 | 星はtrusted ratingがなければ表示されず、最大9枠・15px枠・18px間隔。文字・色・級位サイズも独自値 | `SongSelectRowRenderer`、`SongSelectRowPresentation`。Version 2.2による部分星の描き分けはあるが、評価算出や極端な星数の再現は未完成。スキン画像の読み込み成功とUI再現は別 |

S14は既存の座標IL追跡の再計算であり、今回追加したBack追跡とは区別する。
top右端の繰り返し、フォントbaseline、カーニング、選択色・兄弟行文字の減光率、
複合画像と行・スコア・Cookieの重なり順は未計測。数値を推測で「stable正解」としてテストにしない。

## カーソルと音の操作感

| ID / 判定 | 精密検査の結果 | 根拠／再現観点 |
|---|---|---|
| S18 独自方針・要比較 | cursor/trail/middleを別々のproviderから取得。fallbackにmiddleがあると軌跡モデルまで切り替わる | `SongSelectSkinAssets`、`LegacyCursorVisual.disjoint/fadeDuration`。middleなし150ms／あり500ms。画像混在だけでなく追従感が変化する。stableのfamily単位fallbackは未確定 |
| S19 確定・入力情報欠落 | カーソルはrender更新時の座標と左右ボタンのORだけを受ける。フレーム間で完結する押下・解放を拡大へ渡せない | `SongSelectScreen.update`、`SongSelectCursor.update`。左を保持したまま右を押しても新しいpressEventにならない。移動経路も最新一点だけ。既存visualの精度が高くても入力接続で情報が落ちる |
| S20 限定実装 | ユーザーカーソル倍率、メニュー用設定切替、reload、追加エフェクトは接続されていない | viewportとnative寸法から倍率が決まり、visual側にもuser scale=1のコメント。現行の回転10秒／拡大100ms・1.3倍はlazer由来で、stableのメニュー実機一致は未検証。smoke/rippleの選曲適用範囲も未確定なので単純に必須未実装とは数えない |
| S21 未実装／発火漏れ | Group/Sortのhover、overlay内hover／閉じる／reset、スコア行、Cookieに個別の音経路がない | `SongSelectScreen` はbottom actionと譜面行をhover対象にする。`SongSelectToolboxOverlay.click`と`SongSelectInput`のclose/resetは音を出さない。音ファイルが存在しても再生されない |
| S22 確定・発火不整合 | Groupを開いた状態でSortを押すと、別dropdownを開く操作なのにCONFIRMになる | `menuWasOpen`というboolだけで判定。新しいmenuのidentityを見ていない。wikiのdropdownを開く音はselect-expand |
| S23 確定・文字単位不整合 | 検索音はJava文字列長が増えた時に発火し、1→2→3→4の固定順 | `keyTyped(char)`はサロゲートペアを2回appendできるため補助平面文字で2回鳴る経路。削除・caret移動等は未対応。stableの検索欄での削除音とランダム分布は未確認で、chat用音名だけを根拠に断定しない |
| S24 独自方針・要比較 | 同じselection identityへの再選択では無音。スクロール中はポインター静止でも下のrowが変わるたびhover音対象が変わる | `SongSelectAudio.selection/hover`。Pageが一周して同じidentityに戻る場合も音を抑制。stableのイベント発火とidentity変化は同義とは限らない |
| S25 未実装／既定欠損 | Back hoverは `back-button-hover`一名だけ。今回確認した同梱defaultにはこの音がない | custom/fallbackにない時は無音。現行は例外を握って入力を継続する設計。stableの二候補を渡す経路との対応、旧menu-back用音とnative Back用音の使い分けは要確定 |
| S26 未実装 | Song Selectの選択曲プレビューがない | `SongSelectScreen`にはMusic生成／選択曲のPreviewTime接続がない。`MenuMusicPlayer`はMainMenu用、Gameplayは別にロードする。UI効果音対応だけでは選曲時の聴感は埋まらない |

音は現在画面ロード時に同期preloadされる。画像alpha走査もロード時にPixmapを再度作る。
巨大スキンで入場時間がどれだけ伸びるか、フレーム遅延／音声デバイスで聞こえる遅延は今回未計測。
音源decode失敗を黙ってfallbackするため、ユーザーが欠損を診断する表示もない。

注意: wikiのInterfaceにはtrail既定回転なしという記述、skin.iniにはCursorTrailRotate既定1という
記述があり整合しない。現実装のtrueを、それだけで不具合とは判定しなかった。
同様にlazerの実装をstableメニューの完全な仕様書として扱っていない。

## Song Select全体で依然大きい差

スキン優先の検査だが、次は画像だけでは埋まらない。

- Mode/Modsはoverlayが開くが、他rulesetと通常Mod適用は不可。Optionsも無効。
  Modアイコン対応と機能実装は別の完了条件にする。
- SortはTitle/Artist/Creator/BPM/Length、GroupはNone/Artist/Creator/BPM/Lengthに限定。
  stableにある分類・難易度順等を同等とは呼べない。
- 検索はSet単位の文字列検索。難易度単位の数値フィルタ等はない。
- `moveDifficulty`、`moveSet`は端で停止する。Pageは10件・一周制限の循環処理があるが、
  矢印は既存stable ILの循環traversalと一致しない。
- スクロール／展開はJava独自の臨界減衰spring。stable内部で確認された指数減衰velocityと
  行X/Y別の追従モデルとは違う。hoverの75ms保持・速度による強度減衰も独自。
- ローカルランキングは存在するが、全スコア表現・再生機能までstable同等ではない。
  Bancho／公式API／オンラインランキングはAGENTS.mdの禁止対象で、差分解消の実装対象にしない。

## 検証と次の順序

今回実行:

1. 現在の本体classを使う一時Java probe。出力:
   `User version=1.0`、`menu/game=2.7/1.0, expand=true/false`、
   `Cursor.PNG resolves=false`、`alpha15 empty / alpha16 92x90`、`row body=.8x.8`。
   ソースは `/tmp/osujava-skin-audit-probe/Audit.java`。リポジトリへ実装コードは追加していない。
2. Xvfb configured画面検証1シーン。S01の画像とproviderログを確認。
3. `./gradlew :core:test --tests '*Skin*' --tests '*SongSelect*' --tests '*SongBrowser*' --offline --console=plain`:
   **378 tests、failure/error/skipped各0**。既存テスト通過はstable互換の証明ではない。
   今回は製品コード変更なしの調査なので全Gradle buildは再実行していない。

次の修正順は **S01/S02の既定配色・設定所有者 → S03/S12の読み込み契約 →
S04–S08の実機計測 → S09/S10の未接続asset → S18–S25の入力／音** が適切。
S19/S22/S23はstableの詳細を待たずとも、失われる入力イベントや操作identityを整理できる。
透明hitboxやalpha-based fittingは、まず同一skin/input/resolutionでのstable比較を必要とする。

実機観測の最低ケース: 通常SD/HD、旧Version1、iniなし、User、透明1px、alpha15/16/159/160、
余白違いの同一絵、normal-only/hover-only、複合画像、サイズの違うBack連番、cursor-only、
middle-only、独自効果音、欠損・破損ファイル。各ケースで初期／hover／押下／release／
左右同時押し／低fps／画面遷移／1280x720・1920x1080・4:3・高DPIを記録する。
安易に大量のスクリーンショット件数だけを互換性の進捗指標にしない。

## 追記: 見た目と不足コンポーネントを中心とした画面別棚卸し

追記日: 2026-09-28。対象製品コードは引き続き `3003bad`。
V番号はS番号の視覚面を細分化したもので、全てを新規の独立不具合として加算しない。
「不足」は表示経路の不在、「表示差」は独自の表示・構造、「要計測」はstableでの正確な値が
未確定という意味で使う。静止画だけでなくhover、展開、画面遷移時も外観に含める。

### 比較に使った資料と誤判定の防止

- [公式の画面説明](https://osu.ppy.sh/wiki/en/Client/Interface#song-select)と
  [公式掲載の全体画像](https://osu.ppy.sh/wiki/images/Client/Interface/img/song-selection.jpg)
  を参照し、既存ローカルコピー `/tmp/osujava-stable-research/song-selection.jpg` を目視確認した。
- Java側は既存キャプチャの `1280x720-1x-greylooks-initial.png`、
  `1024x768-1x-phase5a-current-mods-view.png` を
  `/tmp/osujava-skin-implementation-foundation/` から目視確認した。
  これらは前回の装飾クリップ解除前のキャプチャなので、現在の大型装飾の証明には使わない。
  各指摘は現行Renderer／asset enum／presentationコードでも確認した。
- **同一譜面・同一スキン・同一設定の比較ではない。** 背景絵、Greylooks固有の斜線、
  `Shufl`／`Optns`など画像内の文字、サムネイル無効化を、そのまま互換不具合に数えない。
  公式画像のオンライン順位とJavaの空のローカルランキングを比べて「スコアが消える」とも判定しない。
- 公式掲載画像は配布されたstableビルドの実行結果ではない。構成要素の存在確認に用い、
  色・角度・座標の完全な正解画像とは扱わない。

### 上部: メタデータ、分類、検索

| ID / 状態 | 見た目の不足・不一致 | 現コードと確認観点 |
|---|---|---|
| V01 不足 | Group/Sortの下に並ぶ分類タブ列がない | `SongBrowserControls`は二つの文字ラベルと矩形dropdownだけ。`selection-tab`未接続（S10）。タブ列の有無は画面上部の密度・検索位置にも影響する |
| V02 表示差 | Group/Sortの枠、選択値、矢印、見出しの階層を、単一の小さい文字列へまとめている | `drawLabels`の`Group: … ▾`／`Sort: … ▾`。単にフォントサイズを変えるだけでは選択値と見出しの分離を再現できない |
| V03 不足・表示差 | メタデータ左の譜面カテゴリ表示がなく、末尾を一律`Local beatmap`にしている | `SongSelectDetails`、`drawMetadata`。ローカルに分かる状態と未知の状態の見せ方を設計する。公式ランキング状態を推測して表示しない |
| V04 表示差 | タイトル／artist／mapperの改行構成が独自。BPMはmin–maxだけで、原語優先切替もない | `SongSelectDetails.of/bpmText`。タイトルにartistを含めず次行へ置き、可変BPMの代表値もない。全角・長い難易度名での省略位置、行高、右側との衝突を比較する |
| V05 表示差・要計測 | メタデータと行文字にsystem SansSerifを使い、文字の太さ・幅・baselineが環境依存 | `SmoothUiFont`、`SongSelectRowRenderer.drawRowLabel`。日本語を表示できることとstableの文字組み一致は別。旧スキンで焼き込まれた見出しとの重なりも比較する |
| V06 表示差 | 検索は固定文字列＋末尾`\|`で、caretの点滅・選択範囲・編集位置を描く構成がない | `SongSelectRenderer.draw`、`SongSelectInput`。未入力／入力中／長文／0件の状態で、枠・案内文字・結果フィードバックを別々に評価する |

上記のstable側の構成は[画面説明のmetadata・group・search](https://osu.ppy.sh/wiki/en/Client/Interface#beatmap-information)を根拠とする。
文字寸法やフォント名の完全一致は今回未確定。

### 中央と右側: 背景、譜面カード、スクロール

| ID / 状態 | 見た目の不足・不一致 | 現コードと確認観点 |
|---|---|---|
| V07 不足 | 中央に薄く表示される現在Modeの図形とBPM連動表示がない | `mode-osu`等のロード／描画経路なし。現在は背景画像と固定dimが中心で、中央の視覚的な識別要素が欠ける |
| V08 不足 | 選曲の横方向に流れる`star2`装飾がない | `Image` enumにも粒子描画passにもない。星評価用の`star`対応とは別機能。エフェクト無効設定時の比較と混同しない |
| V09 表示差・要計測 | 背景切替は新画像一枚を0.22秒でfade-inする。旧画像を保持した二枚のcrossfadeではない | `selectBackground`、`backgroundFade`、`UiView.background`。center-cover、alpha .72と追加dimも独自値。stableの切替方式は未確定。同じ画像・同じ設定で比較する |
| V10 表示差・要計測 | 譜面カードの画像倍率、色調、文字階層、展開形状がまだ独自 | S05/S17。非選択兄弟行のtitle alpha .24、byline .20、選択以外のサムネイル明度 .78〜.90等を固定。選択カードだけでなく兄弟行の可読性、影・余白・gradeの重なりを比較する |
| V11 限定実装 | 星の帯・gradeは存在するが、星がない通常データでは行下部の情報が欠ける | trusted ratingのない場合は星非表示。gradeもSS/S/A/B/C/Dのみで銀S/SS画像の経路はない。銀gradeはMod未実装にも依存するため、単なるasset追加で完了としない |
| V12 不足 | 右端にリスト内の位置を示すスクロール表示がない | carouselにはscrollOffsetがあるが、Rendererにthumb/trackの描画がない。公式掲載画像では右端に位置表示が見える。正確な形状・出現条件は実機要計測 |

Modeとstar2の用途は[スキン仕様](https://osu.ppy.sh/wiki/en/Skinning/Interface#mode-select)で確認した。
サムネイル自体は実装済みである。旧Versionや表示設定で隠れる状態を「サムネイル未実装」としない。

### 左側: ランキングと譜面状態

| ID / 状態 | 見た目の不足・不一致 | 現コードと確認観点 |
|---|---|---|
| V13 表示差 | ランキングが固定ヘッダー＋矩形行＋grade/score/accuracy/combo/dateで、stableの行構成と異なる | `drawScoreShapes/drawRanking`。行背景のskin未適用はS09。順位、player名／avatar、Mod表示などを持つ構造ではない。ローカルで保持できる情報とオンライン専用情報を分ける |
| V14 不足・表示差 | ranking selector、空状態用パネル、Personal Best専用ブロック、吹き出しがない | 現行は`Local Rankings`と状況別の一行文字。`rank-forum`も未ロード。オンライン機能を接続せず、ローカル順位と空状態の見せ方から検討する。Web呼出ボタンの動作は本件対象外 |
| V15 表示差・要計測 | 左領域の幅・行高・grade枠・余白が独自で、狭い画面ほど密度が変わる | header幅=.35×画面、grade枠60×40、選択行に幅3のアクセント（いずれもUI座標単位）。上部のskin予約高さにも従う。公式画像のオンライン一覧との単純な行数比較ではなく、同じローカルスコア件数で確認する |

### 下部とoverlay: Mode、Mods、Back、プロフィール、Cookie

| ID / 状態 | 見た目の不足・不一致 | 現コードと確認観点 |
|---|---|---|
| V16 不足 | Modeボタン上の現在Modeアイコンと、Mode選択内の中サイズアイコンがない | `mode-*-small`／`mode-*-med`未接続。`selection-mode`の画像自体は表示できるが、動的なMode表示は別コンポーネント |
| V17 表示差・不足 | Modsは略称入り矩形タイル。スキンModアイコン、選択状態の絵、倍率等の表示がない | `SongSelectToolboxOverlay`。実機能未実装に加え、visualも代替表示。Modeも大きな文字の帯であり、元の画像を使ったselectorではない |
| V18 不足 | Beatmap Optionsは無効表示で、開いた状態のコンポーネントがない | hover時の`Beatmap Options unavailable`だけ。ボタン画像対応と、開閉パネル・選択行・確認画面の外観対応を分けて管理する |
| V19 不足・表示差 | 下部のプロフィール領域がなく、Import／local sets／DEBUG AUTOを表示する | `SongSelectToolboxLayout`、Renderer。公式掲載画像にあるavatar・名前・レベル等のまとまりと構成が違う。作者がprofile周辺を一枚絵にしているスキンでは独自ラベルが重なる（S15）。オンラインpp／順位を偽装しない |
| V20 意図的差・要計測 | Cookieは独自のjavaロゴで固定60 BPM相当のpulse | `OsuCookie`、`MainMenuLogo`。ブランド図柄の違いはasset抽出で埋めない。外径、画面外への欠け、白い縁、hover倍率、曲との同期は別に比較可能。MainMenuのvisualiser実装が選曲でも呼ばれるわけではない |
| V21 表示差・要計測 | Backの二層表現、selection hoverの移行、無効状態の減光が未整合 | S06。selectionはnormalを描いた上へhoverを即時追加し、通常 .94、押下 .78、無効 .54。作者がhover画像を透明overlay／全面差し替えのどちらとして作ったかで結果が変わる |
| V22 表示差・要計測 | 複合装飾の重なり順が固定で、同じ絵でも配置先で隠れ方が変わる | 現行順はtop/bottom→score矩形→selection→譜面行→Cookie→Back→metadata/ranking文字→dropdown→overlay→画面fade、カーソルはその後。大きいBackは行を覆える一方、selectionは行の下になる。stableの同条件の順序は未確定 |

プロフィールとランキングのネットワーク由来項目は**視覚的な差として記録するが、接続実装のTODOにはしない**。
オフラインの本人名・ローカル統計等で独立設計する場合も、公式データと見誤らせない表示にする。

### スキン部品の接続状態を確認する最小一覧

| 部品群 | 現在の状態 | 参照項目 |
|---|---|---|
| `songselect-top/bottom` | 接続済み。クリップ・予約領域・topの20 SD幅右端反復は要比較 | S07/S08、V22 |
| `menu-button-background` | 譜面カードのみ接続。スコア行には未接続 | S05/S09、V10/V13 |
| `selection-mode/mods/random/options` と `-over` | 接続済み。機能の有効性、hover合成、fallbackは別途残差 | S11、V18/V21 |
| `menu-back` と連番 | 接続済み。二層表現・遷移は未再現 | S06、V21 |
| `selection-tab` | 未接続。タブ列自体もなし | V01 |
| `mode-{osu,taiko,fruits,mania}`、`-small`、`-med` | 未接続。中央／ボタン／selectorの三用途を分けて対応する必要 | V07/V16 |
| `selection-mod-*` | 未接続。現在はテキストタイル | V17 |
| `rank-forum`、`star2` | 未接続 | V14/V08 |
| `star`、`ranking-*-small` | 部分接続。評価データ・銀grade・表示枠の不足あり | V11 |
| `cursor/cursortrail/cursormiddle` | 接続済み。provider混在・入力密度・画面間設定に残差 | S02/S18/S19 |

### 視覚面を優先する場合の順序と完了条件

1. **既存スキンの絵を壊す問題**: S01の文字コントラスト、S05の余白起因変形、
   S07の装飾切断、S08のfallback所有者を先に扱う。
2. **画面の構造的な空白**: 分類タブ、Mode三用途、ランキングの背景／空状態、Mod画像。
   禁止されるオンライン接続を前提にしないものから進める。
3. **細部と動き**: 行の文字階層、SD/HDのbaseline、右端反復、grade枠、hover合成、
   Back二層、背景切替、Cookieと装飾の同期。先に同一条件のstable観測を揃える。

部品ごとに「assetがロードされた」だけで完了とせず、**表示条件・寸法・原点・色／alpha・
重なり順・hover／押下／無効／欠損時**を確認する。比較には白一色の診断skin、透明／複合skin、
旧Version、実際の配布skinを使い、1280×720、1920×1080、4:3、高DPIで記録する。

本追記はコード閲覧・公式仕様確認・既存画像の目視検査のみ。製品コードとテストは変更せず、
新規のstable起動・画面撮影・テスト／buildは実行していない。上段の378テストは前回調査時の結果である。
