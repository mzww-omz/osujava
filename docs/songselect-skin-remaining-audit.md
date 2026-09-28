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
