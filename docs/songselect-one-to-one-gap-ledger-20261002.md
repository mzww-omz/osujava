# Song Select: stableとの1:1比較台帳（2026-10-02）

現在の実装状態・優先度は[統合残件台帳](songselect-remaining-work-20261002.md)を参照。本書はnative契約の詳細台帳。

## 目標と判定

最終目標は、b20230727.9の**ローカルSong Select**に対して、同じ譜面・Skin・設定・入力列から
同じ表示状態、配置、重なり、操作結果、時間変化を得ること。
「stable風に見える」「Javaのテストが通る」を1:1の合格条件にしない。
Bancho等への接続は対象外とし、オンライン未取得値を作らない。
他rulesetのGameplay全実装はこの調査の範囲へ自動的には追加しないが、Mode変更で生じる
Song Selectの表示・検索・操作の差は機能依存として残す。

初稿のJava基準commitは`ee065543d744cbe15fcf27923b5de0e9b5f85bff`。
ユーザー指定の1〜6を進めた現在の変更・検証・commitは[1〜6の実装進捗](songselect-parity-implementation-progress-20261002.md)を参照。
下表は今回の実装を反映し、元の画像・数値は初稿commit時点の比較として扱う。
stableは`/home/coder/workspace/b20230727.9/osu!.exe`、SHA-256は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
[IL tool](../tools/stable_results_inspect.py)、dnfile 0.18.0／dncil 1.0.2を使用。
token・offsetはこのbinary専用で、offsetはmethod headerを含む。
公式asset抽出・暗号化文字列の復号・保護回避・production接続は行わない。
Quota懸念を受け、今回もWine等の起動検証は行っていない。

| 判定 | 意味 |
| --- | --- |
| 差確定 | ILの計算・分岐・登録とJavaコードが異なる。stable実画素を見たという意味ではない |
| Java再現 | 差確定に加え、Java計算またはcaptureで発現を確認 |
| 部分対応 | 閉じた静的契約を実装済み。全経路・実機の一致は未認定 |
| 未確定 | native経路、設定元、座標変換、または実測が足りない。推測で実装しない |
| 機能依存 | 表示差を直すためにもローカルの機能・データ実装が必要 |

現在、正常なstable Song Selectの参照実行を確保できていないため、全画面のpixel/audio一致は未判定。
根拠の閉じた契約は先に実装できるが、未測定の差を0として集計しない。
単純な完了率は算出しない。各項目の条件ごとに判定を残す。

## 画面・Skinの差分と受入条件

詳しいILとJava画像は[Skin・ビジュアル調査](songselect-visual-skin-audit-20261002.md)を参照。
表の受入条件は今後の比較手順であり、今回すべて実行済みという意味ではない。

| ID / 判定 | stableに対するJavaの差／不足 | 1:1の受入条件 |
| --- | --- | --- |
| V01 / 部分対応 | main manager内のBack .9/.91→selection .95/.96へ修正。自作の巨大Backで交差画素を検証済み。Cookie・別managerの全順は残る | 同一条件のnative画像で合成を比較。managerを跨ぐ順は別途確定 |
| V02 / 部分対応 | Song Selectのdensity除算を整数化し、奇数HDの末端をUV crop。185×181は92×90、545×183は272×91へ修正。共有Gameplay寸法は維持 | 全部品のorigin・crop・hitとnative実画素を照合。整数寸法の成功を最終hitの一致へ一般化しない |
| V03 / 部分対応 | display width>1366のgate、logical X1365の1列crop、UV・位置・scale・同depthの登録順を反映。短いtopの任意幅延長を除去 | 1365/1366/1367、短いtop、SD/HDのnative draw/clipを照合。予約高さはV04へ残す |
| V04 / 差確定 | Javaのalpha scan、top40%/bottom30%上限、最低予約高さが配置・viewportを変える | 透明top・高さ600top・高さ400bottomで、各spriteと入力範囲のwindow geometryを比較。nativeで裏づけのない予約規則を互換仕様にしない |
| V05 / 部分対応 | Options normal絵の常時RGB .54倍を除去し、白い自作画像の画素を検証。操作機能は未対応 | 同一素材でnative通常/hover/押下の色とalphaを照合。機能依存はV21で管理 |
| V06 / 部分対応 | bundled行に独自のalpha .86白矩形を重ねる処理を除去 | 対象条件でnativeの色・alpha合成を照合。可読性の独自補正を互換仕様へ混ぜない |
| V07 / 部分対応・未確定 | alpha/normal union/固定slot依存を除去。raw hover矩形と画面clip、selection .96→Back .91の入力優先を対応。alphaは画像診断のみ。global dispatcher/物理pixel丸めは未確定 | alpha0/15/16/159/160/255と600×350 overでhover候補、down対象、up結果を比較。透明画像の読み込み成功だけで合格にしない |
| V08 / 部分対応・未確定 | Backのframe別geometryを更新。nativeのbase更新→texture交換を保ち、新寸法は次updateでdraw/crop/hitへ反映。Back alpha推定を除去しraw矩形に対応。global dispatcher/物理pixel丸めは残る | 異寸法連番のframe・crop・hitをnativeと同時比較。画面境界の抑止flag寿命も確認 |
| V09 / 部分対応・未確定 | Back専用の初回update epochとSingle除算interval、wrap・再生成を対応。正常画面内の更新をunit/GLで検証 | native時計のfocus/minimize/復帰継続性、normal/overの全寿命を確認。UI elapsedとの差を全条件で閉じる |
| V10 / 差確定 | Java resolverは常にHD優先。nativeに800/capability/optionsによるHD eligibilityがある | HD/SDを異なる自作色にし、実際に選んだpath/densityを照合。未確定optionをJava window heightで代用しない |
| V11 / 部分対応 | Song Selectの静的画像は最初の存在ファイルのdecode失敗で探索停止。missingはfallback。Backはzero/staticを独立探索しprovider優先を比較、勝ったzeroのproviderで連番を固定。壊れたHD→SDへのretryを除去 | missing/壊れたHD/壊れたSD/連番の結果を別々に照合。Gameplay/Resultsの既存policyを誤変更しない |
| V12 / 部分対応・未確定 | 部品の独立探索は対応済み。native mask/RawName/global例外、cursor設定owner等は残る | CUSTOM/FALLBACK/BUNDLEDを別色にし、画像・INI・音それぞれのownerを比較。Javaの任意fallback Skinをnative譜面providerと同一視しない |
| V13 / 部分対応 | INIのcase/最初のキー/boolean/RGBA、cursor-middle同provider・trail独立は対応済み | 重複section、省略、読込失敗、Version切替、cursor/trail設定と音のaliasを追加照合。通常キーの成功でparser全体を完了にしない |
| V14 / 部分対応・機能依存 | 通常SelectPlayの5/6 tab、カテゴリidentity、Artist/CreatorのSort連動を対応。未対応カテゴリは案内を出し別Groupへ誤対応しない | 下記の幅境界とnative登録値を比較。tab配置・animation・localized表示、Difficulty/Recent/Collectionsの機能が残る |
| V15 / 部分対応・未確定 | titleと行title/bylineを選択・代表難易度のmetadataへ修正。公称18/12/8、depth/位置、独自5行・bold/scale/幅制限は残る | 内容とbaseline・折返し・省略・影・色を項目別比較。origin変換を閉じてから座標を変更 |
| V16 / 差確定・未確定 | native GDI系とJava AWT系の測定・描画差。Latin/CJK/結合文字等の最終pixelは未測定 | 同じ許可されたfontで字幅・baseline・glyph fallback・影・省略位置を測定。文字全領域のmaskで合格にしない |
| V17 / 機能依存・未確定 | Java score欄は現在45高/49.5pitch（native33/480高pitchから変換済み）。grade枠・情報配置とnative score containerのclip全体には差が残る | 空/1件/多数、scroll端、score種別、replay有無で内容・draw/hit/clipを比較。native score用managerをcarouselと混同しない |
| V18 / 差確定・未確定 | Java score barは幅3×scale、最小thumb18×scale。既存thumbへのdrag入力は接続済み。carousel barを含むnative形状/連続scroll/track契約は未確定 | carouselとscoreを別々に、0/少数/多数、端/中間位置でbar geometryとdrag操作を比較 |
| V19 / 差確定・未確定 | Java Cookieは生成ロゴと独自のbeat/hover/pressed半径、bottom限定hit | 自作の許可素材でorigin、動く境界、重なり、click範囲を比較。公式ロゴ抽出で差を隠さない |
| V20 / 未確定 | 背景の暗化・切替、粒子、拍同期、入退場の全native経路は閉じていない | 同一音源・背景、静止/選択変更/無音/連打でphase、fade曲線、合成順を比較。Javaの.22秒fade等は当面の値として扱う |
| V21 / 差確定・機能依存 | Java Mode/Modsは独自の帯・tile・Unavailable表示。通常Modsはactive空、toggleはfalse。Optionsは案内のみ | native selectorのgeometry/時間/キー/close/状態適用を比較。実機能と外観を別の完了項目にする |
| V22 / 部分対応 | local/debug案内をdebugUi設定に限定し、Options hoverの常設Unavailable文字を除去。Import・生成Cookie・未対応操作後の案内は残る | 通常比較画面の余分な文字・矩形とローカル機能の差を記録。診断表示を別条件として扱う |

### 通常SelectPlayのtab構成と境界（実装後）

`0600136e` `184d–193d`はmanager `04000ae1`へ値12、5、1、3、14を登録し、
`06001dc9()>720`の場合だけ18を追加する。
constructor `00a1–00b9`の`04000ac3/ac2`はglobal `040016f6`と5/13との比較。
平文enum `02000b03 osu.OsuModes`の定数からSelectPlay=5、SelectMulti=13、SelectEdit=4を確認した。
従って**通常SelectPlayは5/6個**。初稿で比較した双方flag=falseの4/5個はeditor等の別分岐である。

平文`02000b0a osu_common.Helpers.OsuString`の定数から、localization ID 751/756/752/754/767/762は
それぞれNoGrouping/ByDifficulty/ByArtist/ByCreator/RecentlyPlayed/Collectionsと確定した。
暗号化文字列は復号していない。localized表示文字列そのものの一致は未認定。
Javaはこの順の明示的Tabを使い、未実装カテゴリをBPM/Lengthへ誤対応しない。
BPM/Lengthは現ローカルGroup dropdownで利用可能。dropdown全内容はnative未対応。
click `0600138a→1384`のArtist/CreatorのSort連動を反映し、NoGroupingはSortを維持する。

`06001dc9`は`ceil(displayWidth / (displayHeight / 480f))`。

| display W×H | native論理幅 | native追加Collections | native SelectPlay / Java修正後tab数 |
| --- | --- | --- | --- |
| 1024×768 | 640 | なし | 5 / 5 |
| 1280×900 | 683 | なし | 5 / 5 |
| 1152×768 | 720 | なし | 5 / 5 |
| 1153×768 | 721 | あり | 6 / 6 |
| 1366×768 | 854 | あり | 6 / 6 |

`SongSelectVisualComponentsTest`で数・identity・クリック・Sort連動・未対応案内を回帰検証した。
native最終描画のclip・animation・カテゴリ機能は未完了であり、登録数の一致だけで合格にしない。

### depthはmanagerの境界も比較する

`0600136e`にはmain `04000ad6`に加え、`04000ad7/ad8/ad9`、score container `04000ada`がある。
Mode selectorの4枚のicon/textは`0935–0b30`付近で別manager `04000ad7`へ登録される。
一方、Backとselectionは同じmain managerなのでV01の順序を確定できる。
**異なるmanagerのspriteをdepthの数値だけで一列に並べない。**
今後は各spriteのowner manager、manager draw順、field/origin、depth、clip、blendを併記する。
phase 9の「行内composition対応済み」は、画面全体の合成完了を意味しない。

## 表示結果を変えるデータ・入力・時間の差

見た目だけを修正しても、違う行が並ぶ／違う行を選ぶ状態では1:1にならない。

| ID / 判定 | 比較結果と残る差 | 受入条件 |
| --- | --- | --- |
| D01 / 部分対応 | 難易度単位のSort/Group、最大BPM、Library終端整数秒、文字metadataへ修正。検索に合うchartからSetをprojection。culture/tie-breakとfamily identityは残る | 同Set異BPM/Length/metadata、部分検索、代表行、family、選択修復をnativeと比較 |
| D02 / 部分対応 | BPM60刻み、Length1/2/3/4/5/10分境界へ修正。native predicateのちょうど300 BPMの未所属、負Lengthの除外も保持 | 各境界の直前/一致/直後で所属・件数・selectionを照合。Java unitだけでnative合格にしない |
| D03 / 部分対応・機能依存 | 難易度AND検索、metadata、AR/CS/OD/HP/BPM/length/drain等は対応済み。stars/key/status/played/date/rank/collection等は残る | 各fieldの変換・丸め・culture・未知値・AND/引用を比較。未取得オンライン値を0で捏造しない |
| D04 / 差確定・未確定 | Javaは即時rebuild。nativeに300ms待機経路。Regex境界・IME/culture・alias/conversionは未完了 | 編集時刻ごとの表示件数とselection、クリア・0件・再入力、IME確定を入力列で照合 |
| D05 / 部分対応・未確定 | 同Set複数Group、連続familyの区切り、クリックした代表難易度のactivateを修正。family identityはlocal Set近似、近い難易度の全選択規則は未完了 | 同名/別Set/同Set複数Group・部分検索で行ID、focus ID、playable selection IDを別々に比較 |
| I01 / 部分対応・未確定 | 行の参照座標/指数慣性/色/星/foregroundは各phaseで対応済み | nativeの同一入力列で全frameの位置・色・scale/cropを確認。Java snapshotだけで総合合格にしない |
| I02 / 部分対応・未確定 | 行背景の生成時priorityと同priority時の先着、fading候補/hover状態の分離は修復済み。下部sprite間のpriorityも修復。managerを跨ぐ候補更新/clip/物理pixel丸めは残る | 巨大行・透明行・重なり・画面端でdraw順とhit対象を別記録し、選択/開始/context結果を照合 |
| I03 / 部分対応・未確定 | wheel集約/保持repeat/mouse snapshotは対応。初回key callbackはnativeと同じ更新順に閉じていない | 同frameのwheel+key+mouse、overlay開閉、修飾key、focus喪失/復帰で消費順と発火時刻を比較 |
| I04 / 部分対応・未確定 | drag/80px取消/250counter等の画面内契約は対応。画面境界のglobal入力状態継承は残る | drag-out、左右交換、押下中の遷移、復帰直後、低fpsで入力stateの寿命を比較 |
| T01 / 未確定 | Java previewの同frame選曲/音声更新と失敗後の新選択時1回retryを修復。健康な同pathは継続、新pathでdispose/play/seek、未指定0、全曲loop。nativeのseek/loop/focus/復帰規則は未調査 | 同音源別難易度、異音源連打、PreviewTime省略/0/正値で選択・seek・fade・loop・clockを比較 |
| T02 / 未確定・機能依存 | 背景・thumbnail・音の非同期順、入退場/Gameplay復帰の状態保持が未完了 | 遅延/失敗/順序逆転と短時間往復で古い選択の画像・音が混入しないこと、nativeと同じ復帰状態を確認 |

D01/D02の根拠は`06003288`、`06003c95`、`060013ff`、`06003282`、`060032b0`、
`0600329b/329c`、`060032b2`。
[時間統計・分類の残差](songselect-parity-phase10-timing-20260929.md)と現在の
[SongBrowserModel](../core/src/main/java/dev/osujava/ui/SongBrowserModel.java)を参照。
分類の違いはGroupカードの数、星、行の開閉位置にも波及するため視覚差として扱う。
過去の[全体再調査](songselect-parity-reinvestigation-20260929.md)のJava現状説明は旧commit時点。
修正済みのバネ移動や選択最前面化を新しい未実装項目へ再計上しない。

## 比較シーンと記録形式

| 軸 | 必須ケース |
| --- | --- |
| データ | 0/1/少数/多数、同Setの異BPM/異Length、長い導入、境界値、Unicode/長文、scoreなし/多数 |
| Skin | Version 1/2/2.2/2.7、SD/HD併存、奇数HD、欠損/壊れた画像、透明/巨大画像、normalとover別寸法、異寸法連番Back、provider混在 |
| 画面 | 1024×768、1280×900、1152/1153×768、1365/1366/1367×768、1280×720、1920×1080、framebuffer density 1/2 |
| 操作 | hover境界、down/up、wheel、同frame key+wheel+mouse、長押し、左右交換、drag中断、検索、tab/selector開閉、Skin切替、画面復帰 |
| 時間 | 30/60/144Hz、不均一delta、初回frame、50/300/500/1000ms等の境界、遅延ロード、連打 |

各sceneで入力譜面/Skinのhash、設定、画面/framebufferサイズ、font、locale、時刻基準を固定する。
各イベントとframeに次を残す。

1. 状態: query/sort/group/tab、表示行ID、focus、selection、hover/down/up対象、機能request。
2. geometry: 論理座標とwindow座標、origin、論理/物理寸法、crop、manager/depth、clip、blend、RGBA、animation epoch/frame。
3. 出力: 全画面と領域別pixel差分、音の開始/seek/停止時刻。Javaのcaptureとnativeのcaptureを区別する。

状態・素材選択・整数演算は同値を要求する。
float geometryはnativeの変換と丸め地点を先に固定して比較し、任意の1px許容で差を消さない。
時間は時計の基準とnativeのframe量子化を測定してから許容範囲を決める。
pixelは同一の許可素材・font・設定が揃う領域で比較し、fontや素材が違う領域は未判定として残す。
領域の除外理由と面積を記録し、文字全体・chrome全体の除外で合格にしない。

## 対応順と今回の検証

ビジュアル優先ではV01→V02/V10–13→V03/V04→V14/V15/V17/V18→V07–09→V16/V19–22。
D01/D02は表示行を決める依存として並行して扱える。phase番号の既存の進行方針は
[roadmap](songselect-parity-roadmap-20260929.md)を維持する。
ユーザーの1〜6の実装指示を受け、今回の確定契約を製品へ反映した。詳細は[1〜6の実装進捗](songselect-parity-implementation-progress-20261002.md)を参照。
同じspriteの配置・hit・時間を別々の近似で直さず、確定した契約ごとに小さく実装する。

以下は台帳初稿追加時の検証記録。実装後の最新結果は[1〜6の実装進捗](songselect-parity-implementation-progress-20261002.md)を参照。
初稿の変更はこの比較台帳とroadmap参照の追加のみ。製品コード、既存テスト、binaryは変更していない。
新規IL調査は`0600136e/138a/13ae/13a3/1376/13b4`、`0600321a`、`060042d5`、`06001dc9`。
tab probeは一時ファイルで初稿Java実装の4/5値を確認した。現在の5/6値は回帰testで確認した。
前回のJava自作Skin captureは[画像つき調査](songselect-visual-skin-audit-20261002.md)に保存済み。
docsのリンク27件は参照先が存在し、`git diff --check`も成功。
`./gradlew build --offline --console=plain`は成功（15 tasksすべてUP-TO-DATE）。
製品コードの変更がないため、既存テストは再実行されていない。
一時tab probeは初回にGDXの実行classpathが不足したが、依存jarを追加した再実行で成功した。
