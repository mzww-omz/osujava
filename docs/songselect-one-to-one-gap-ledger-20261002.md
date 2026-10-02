# Song Select: stableとの1:1比較台帳（2026-10-02）

## 目標と判定

最終目標は、b20230727.9の**ローカルSong Select**に対して、同じ譜面・Skin・設定・入力列から
同じ表示状態、配置、重なり、操作結果、時間変化を得ること。
「stable風に見える」「Javaのテストが通る」を1:1の合格条件にしない。
Bancho等への接続は対象外とし、オンライン未取得値を作らない。
他rulesetのGameplay全実装はこの調査の範囲へ自動的には追加しないが、Mode変更で生じる
Song Selectの表示・検索・操作の差は機能依存として残す。

Java基準commitは`ee065543d744cbe15fcf27923b5de0e9b5f85bff`。
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
| V01 / Java再現 | main manager内でBack .9/.91→selection .95/.96の順。Javaはselection→Cookie→Backなので巨大BackがMode/Modsを覆う | 同managerのdepth順を保ち、400×150のBackと不透明selectionが交差する画素を比較。Cookieや別managerの順は別途確定 |
| V02 / Java再現 | nativeのdensity除算は整数。Javaの汎用geometryはfloatで、奇数HD selection185×181が92.5×90.5（native92×90） | 全部品の論理寸法・origin・crop・hitを追跡。星／mode／gradeの対応済みを他画像へ一般化しない |
| V03 / Java再現 | top延長はnative display width>1366でcrop X1365の1列。Javaは短いtextureの最終列を任意の画面幅で延長 | 1365/1366/1367付近と短いtop、SD/HDを比較。nativeのcrop→UV変換まで閉じてから移植 |
| V04 / 差確定 | Javaのalpha scan、top40%/bottom30%上限、最低予約高さが配置・viewportを変える | 透明top・高さ600top・高さ400bottomで、各spriteと入力範囲のwindow geometryを比較。nativeで裏づけのない予約規則を互換仕様にしない |
| V05 / 差確定 | JavaのOptions絵は機能未対応のためRGBを常時.54倍する | 同じ白い自作画像で通常/hover/押下の色とalphaを比較。機能依存と通常画像の合成差を分けて記録 |
| V06 / 差確定 | bundled行背景＋暗い選択文字等の条件でJavaがalpha .86の白い補助矩形を描く | 対象条件で余分な矩形がないことを比較。可読性の独自補正をnativeの色契約へ混ぜない |
| V07 / 差確定・未確定 | Javaのalpha>=16/160によるnormal/over範囲union、固定slotがhitを決める。native sprite寸法はalpha非依存だが最終dispatcherは未確定 | alpha0/15/16/159/160/255と600×350 overでhover候補、down対象、up結果を比較。透明画像の読み込み成功だけで合格にしない |
| V08 / 差確定・未確定 | BackはJavaで描画frameだけ更新し、初回frameのlayout/hitを保持。nativeは寸法更新経路を持つが全寿命の抑止flagは未確定 | 連番ごとに異寸法・透明度を変え、frame番号とdraw/crop/hitを同時記録。nativeの画面生存中flagも確認 |
| V09 / 差確定・未確定 | nativeはsprite初回updateを基準にするanimation経路。Javaは画面elapsedを使う | 入場後の遅延生成・再表示・Skin切替でepoch、fps、wrap、初回frameを比較。時計が違うことと実際のframe差を区別 |
| V10 / 差確定 | Java resolverは常にHD優先。nativeに800/capability/optionsによるHD eligibilityがある | HD/SDを異なる自作色にし、実際に選んだpath/densityを照合。未確定optionをJava window heightで代用しない |
| V11 / 差確定 | nativeの存在HD decode失敗は単なる不存在とは別経路。JavaはSD／別providerへfallback | missing、壊れたHD、壊れたSDを区別し、選択pathと結果を記録。安全なエラー処理も維持 |
| V12 / 部分対応・未確定 | 部品の独立探索は対応済み。native mask/RawName/global例外、cursor設定owner等は残る | CUSTOM/FALLBACK/BUNDLEDを別色にし、画像・INI・音それぞれのownerを比較。Javaの任意fallback Skinをnative譜面providerと同一視しない |
| V13 / 部分対応 | INIのcase/最初のキー/boolean/RGBA、cursor-middle同provider・trail独立は対応済み | 重複section、省略、読込失敗、Version切替、cursor/trail設定と音のaliasを追加照合。通常キーの成功でparser全体を完了にしない |
| V14 / 差確定・未確定 | tabの数、登録カテゴリ、配置、クリックによる状態遷移の比較が不足。JavaはGroup enumを直接tabにする | 下記の幅境界・native登録値を比較。正式ラベルとカテゴリの意味は暗号化文字列を推定せず追加根拠で確定 |
| V15 / 差確定・未確定 | native metadataには公称18/12/8と別depth/位置がある。Javaは独自の5行・bold/scale/幅制限 | title/mapper/時間/統計/評価文字列の内容、baseline、折返し、省略、影、色を項目別比較。native origin変換を閉じてから座標を変更 |
| V16 / 差確定・未確定 | native GDI系とJava AWT系の測定・描画差。Latin/CJK/結合文字等の最終pixelは未測定 | 同じ許可されたfontで字幅・baseline・glyph fallback・影・省略位置を測定。文字全領域のmaskで合格にしない |
| V17 / 機能依存・未確定 | Java score欄はローカル独自の64高/68pitch・grade枠・情報配置。native score containerのclipは別の矩形 | 空/1件/多数、scroll端、score種別、replay有無で内容・draw/hit/clipを比較。native score用managerをcarouselと混同しない |
| V18 / 差確定・未確定 | Java scrollbarは独自の幅5、最小thumb18等。nativeのどのbarと対応するかを分けて確定する必要 | carouselとscoreを別々に、0/少数/多数、端/中間位置でbar geometryとdrag操作を比較 |
| V19 / 差確定・未確定 | Java Cookieは生成ロゴと独自のbeat/hover/pressed半径、bottom限定hit | 自作の許可素材でorigin、動く境界、重なり、click範囲を比較。公式ロゴ抽出で差を隠さない |
| V20 / 未確定 | 背景の暗化・切替、粒子、拍同期、入退場の全native経路は閉じていない | 同一音源・背景、静止/選択変更/無音/連打でphase、fade曲線、合成順を比較。Javaの.22秒fade等は当面の値として扱う |
| V21 / 差確定・機能依存 | Java Mode/Modsは独自の帯・tile・Unavailable表示。通常Modsはactive空、toggleはfalse。Optionsは案内のみ | native selectorのgeometry/時間/キー/close/状態適用を比較。実機能と外観を別の完了項目にする |
| V22 / 差確定 | Import、local/debug案内等のJava独自表示が通常画面にもある | 通常の比較画面と診断表示を分け、比較画面の余分な文字・矩形を差として残す |

### 今回追加したtabの境界比較

`0600136e` `184d–193d`はtab manager `04000ae1`へ次の値を登録する。
登録method `060042d5`は現在件数を使って`060042d6`へ追加する。

- 最初にenum `020008b0`の値12（localization ID 751）。
- flag `04000ac3`または`04000ac2`がtrueなら5、1（ID 756、752）。双方falseなら8（ID 765）。
- 続いて3、14（ID 754、767）。
- `06001dc9()>720`の場合だけ18（ID 762）を追加する。
- flagsはconstructor `00a1–00b9`でglobal `040016f6`と5/13の比較から作る。
  そのglobalの正式な状態名はここでは断定しない。

`06001dc9`は`ceil(displayWidth / (displayHeight / 480f))`。
従って双方flag=falseの経路は4/5個、いずれかtrueの経路は5/6個になる。
Java [SongBrowserControls](../core/src/main/java/dev/osujava/ui/SongBrowserControls.java)は
幅/高さ<=4/3で4、それを超えると5。内容はAll/Artist/Creator/BPM/Length。
native click handler `0600138a`は現在値`04000af2`と比較して`06001384`、`060013ac`へ渡す。
Javaは直接`browser.group()`へ渡す。カテゴリの意味の対応表はまだ未完成であり、
localization IDだけから正式ラベルを決めない。

実際のJava `tabCount()`をGL不要の一時probeで呼び、native分岐をILのfloat計算で評価した。
JavaのUiLayoutへの一様なscale変換はこの比率判定を変えない。

| display W×H | Java tab数 | native論理幅 | native追加18 | native tab数（双方flag=false） |
| --- | --- | --- | --- | --- |
| 1024×768 | 4 | 640 | なし | 4 |
| 1280×900 | 5 | 683 | なし | 4 |
| 1152×768 | 5 | 720 | なし | 4 |
| 1153×768 | 5 | 721 | あり | 5 |
| 1366×768 | 5 | 854 | あり | 5 |

これは登録数の静的比較。native最終描画時のclip・animation・表示状態や正式ラベルは未観測。
16:9で数が一致するだけでは互換と判定できない。

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
| D01 / 差確定 | Java Sort/GroupはSet集約。nativeは難易度側を分類。BPM Sortは検索用の主なBPMと別の最大値、Lengthは曲頭基準終端の整数秒 | 同Setの各難易度でBPM・長さを変え、検索後の順序、Group所属、代表行、family、選択修復を比較 |
| D02 / 差確定 | Java BPM Groupは50刻み、Length Groupは2/4/6/10分。対象ILのBPM predicateは60刻み、Lengthは1/2/3/4/5/10分の経路 | 各境界の直前/一致/直後で所属と件数を照合。長い導入と同整数秒tieも含む |
| D03 / 部分対応・機能依存 | 難易度AND検索、metadata、AR/CS/OD/HP/BPM/length/drain等は対応済み。stars/key/status/played/date/rank/collection等は残る | 各fieldの変換・丸め・culture・未知値・AND/引用を比較。未取得オンライン値を0で捏造しない |
| D04 / 差確定・未確定 | Javaは即時rebuild。nativeに300ms待機経路。Regex境界・IME/culture・alias/conversionは未完了 | 編集時刻ごとの表示件数とselection、クリア・0件・再入力、IME確定を入力列で照合 |
| D05 / 部分対応・未確定 | 永続行/状態/focus/代表行は対応済み。familyはlocal Set近似で、検索後に近い難易度を選ぶ全規則は未完了 | 同名/別Set/同Set複数Group・部分検索で行ID、focus ID、playable selection IDを別々に比較 |
| I01 / 部分対応・未確定 | 行の参照座標/指数慣性/色/星/foregroundは各phaseで対応済み | nativeの同一入力列で全frameの位置・色・scale/cropを確認。Java snapshotだけで総合合格にしない |
| I02 / 差確定・未確定 | Javaの行hitはselected優先と逆順。他のspriteと重なる場合のnative候補優先、alpha/clip/丸めは残る | 巨大行・透明行・重なり・画面端でdraw順とhit対象を別記録し、選択/開始/context結果を照合 |
| I03 / 部分対応・未確定 | wheel集約/保持repeat/mouse snapshotは対応。初回key callbackはnativeと同じ更新順に閉じていない | 同frameのwheel+key+mouse、overlay開閉、修飾key、focus喪失/復帰で消費順と発火時刻を比較 |
| I04 / 部分対応・未確定 | drag/80px取消/250counter等の画面内契約は対応。画面境界のglobal入力状態継承は残る | drag-out、左右交換、押下中の遷移、復帰直後、低fpsで入力stateの寿命を比較 |
| T01 / 未確定 | Java previewは同path変更を無視、新pathでdispose/play/seek、未指定は0、全曲loop。nativeの全規則は未調査 | 同音源別難易度、異音源連打、PreviewTime省略/0/正値で選択・seek・fade・loop・clockを比較 |
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
[roadmap](songselect-parity-roadmap-20260929.md)を維持し、この調査で勝手に製品実装へ進まない。
同じspriteの配置・hit・時間を別々の近似で直さず、確定した契約ごとに小さく実装する。

今回の変更はこの比較台帳とroadmap参照の追加のみ。製品コード、既存テスト、binaryは変更していない。
新規IL調査は`0600136e/138a/13ae/13a3/1376/13b4`、`0600321a`、`060042d5`、`06001dc9`。
tab probeは一時ファイルで実行し、上表のJava値を確認した。
前回のJava自作Skin captureは[画像つき調査](songselect-visual-skin-audit-20261002.md)に保存済み。
docsのリンク27件は参照先が存在し、`git diff --check`も成功。
`./gradlew build --offline --console=plain`は成功（15 tasksすべてUP-TO-DATE）。
製品コードの変更がないため、既存テストは再実行されていない。
一時tab probeは初回にGDXの実行classpathが不足したが、依存jarを追加した再実行で成功した。
