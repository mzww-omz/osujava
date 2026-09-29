# Skin独立調査: stable b20230727.9とのSong Select互換

## 結論と範囲

Skinは、画像名ごとの置き換えだけでは1:1にならない。**読み込み元、探索条件、設定、部品の組合せ、座標・合成・操作領域**を一つの契約として扱う必要がある。
今回の対象はSkinだけ。Song Selectの検索・行モデル・Carousel・Gameplayの再設計は扱わず、製品コードも変更していない。
必要な大規模改修は許容するが、まずSkinの契約を独立して閉じ、確認済みの差を小さな実装単位へ分ける。

特に優先する差は次のとおり。

1. stableのprovider maskと結果のprovenanceを、Javaのcurrent → fallback → bundledだけでは表現できない。
2. selectionの配置はVersionだけでは決まらず、Mods画像がbuilt-in由来かどうかも参照する。
3. normal/hover、top/bottom、cursor/middleには異なる探索規則がある。Javaの独自の一括抑制・独立fallbackと一致しない。
4. skin.iniの重複・大文字小文字・boolean・色・カーソル既定値に差がある。
5. alphaから配置やhitboxを推測するJavaの規則はstableの確認済みsprite矩形契約と分離して再検証する必要がある。

## 対象と証拠

- Java基準: `bf41844c0d2b1e5286d82423cea77ba4c98b3ff5`。開始時working treeはclean。
- stable: `/home/coder/workspace/b20230727.9/osu!.exe`。
- exe SHA-256: `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
- `dnfile` / `dncil`で当該exeのメタデータ、IL、呼出先、フィールド参照を再読した。
  難読名はtokenと参照関係で対応付けた。以下のtoken/offsetはこのexe専用、offsetはdncilのmethod body表示。
- 公式画像・音源・fontの抽出、保護回避、stableの改変・実行、暗号化文字列ルーチンの実行は行っていない。
  Bancho・osu! API等のproduction serviceにも接続していない。
- **IL確認**、**Java確認**、**公開資料**、**設計判断**を区別する。ILで読んだ分岐を実機pixel比較の結果とは呼ばない。
  [以前の起動制約](song-select-repair.md#stable-runtime-limitation)は継続しており、今回stableの実画面比較はない。
- 公開資料は2026-09-29に確認した[公式skin.ini仕様](https://github.com/ppy/osu-wiki/blob/master/wiki/Skinning/skin.ini/en.md)、
  [公式Interface仕様](https://github.com/ppy/osu-wiki/blob/master/wiki/Skinning/Interface/en.md)、
  [公開lazer LegacySkin](https://github.com/ppy/osu/blob/master/osu.Game/Skinning/LegacySkin.cs)。
  現行wiki/lazerと対象stable buildが異なる場合は混同せず、対象buildの根拠と差を残す。

## 1. 画像探索と読み込み元

### ILで確認した契約

汎用画像loader `06001b71` は呼出元からmaskを受け取り、候補を次の順に処理する。

| bit / 結果の値 | 経路 | 根拠 |
| --- | --- | --- |
| 4 | 現在の譜面側resource providerからstreamを取得 | `00c1–019c`、`06004322` → `06003cbc`、成功時`04000338=4` |
| 2 | 選択Skinのディレクトリ | `01a1–02e1`、`060051a5`はSkin rootと`RawName`からpathを構成、成功時`04000338=2` |
| 1 | built-in側resource/path解決 | `02e6–0366`、`06003e6f/3e82`、成功時`04000338=1` |

`04000338`は後続の画像選択にも使われるため、単なる診断用ラベルではない。
maskごとの結果cacheとprovider別cacheがあり、null結果も保存する。
冒頭でglobal flag `04001b1a` と状態 `040016f6` によりbit 4を外す。
したがって「Song Selectがmask 7を渡す」だけでは、その画面で譜面Skinが有効とは断定できない。

ディレクトリ経路は拡張子候補配列 `04001255` を使う。`06001b6e`で2要素と確認したが、暗号化された拡張子名は今回未確定。
呼出名に`.`があれば拡張子追加を省く分岐もある。JavaのPNG限定探索と同一とはまだ扱わない。

高密度画像は無条件優先ではない。`06002340`は次の式に相当する。

```text
(displayField_04001396 >= 800 OR option_04000d19)
AND NOT option_04000d26
AND capability_04001851 > 2048
```

display値・option・capabilityの全設定元と正式名称は未確定。数値800をそのままJavaのwindow heightへ適用しない。
`06001b71` `020d–0282`は拡張子の前にsuffixを挿入し、条件成立とファイル存在を確認して読み込み、成功時density divisorを2にする。
**存在した高密度候補のdecodeがnullになっても、その呼出はそこで戻る。** 同様に存在した通常候補もnullのまま戻り得る。
`06000746`はファイル読込例外をcatchしてnullを返す。対象ファイルが存在しない場合の次候補探索と、存在するが読めない場合は別契約。
実際の壊れたPNG各種についてはdecoder `06000747`以下と実機を追加確認する。

### Javaとの差と判断

[`SkinAssetResolver`](../core/src/main/java/dev/osujava/skin/SkinAssetResolver.java)はCUSTOM → FALLBACK → BUNDLED、それぞれdensity 2 → 1。
loadability predicateがfalseならSD、別providerへ進む。画面・optionによるHD eligibilityはない。
明示fallbackDirectoryはJava独自の追加providerで、stableのbit 4に対応するものではない。

**設計判断:** 名前に加えて許可providerとHD条件を入力にし、結果にprovider identity・density・物理寸法・論理寸法・設定ownerを追跡可能にする。
missing、decode failure、予算による拒否、全透明の正常画像を区別する。
正常画像の探索互換と、不正入力からアプリを保護する制限は別に定義する。
破損時のstable互換を理由にJavaを落とす必要はないが、黙って別画像へ差し替えた状態を完全一致とは扱わない。
cacheの無効化条件・Skin切替・HD条件変更は未調査のため、stableのcache構造自体を移植する判断はしない。

## 2. selection: Version・normal/hover・操作領域

### 配置条件に画像の出所が入る

Song Select constructor `0600136e` `0803–083a`付近は、`06002ac8`がfalseのときにModsのnormal画像をmask 7で取得し、
そのproviderが1かどうかを見る。取得時の文字列ID `-1079473471`は同constructorのMods normal生成箇所と同じで、
文字列を復号せずに同一asset参照と確認できる。

```text
newAnchor = predicate_06002ac8() OR (resolved Mods normal provider == 1)
```

`06002ac8`自体も単純なVersion判定ではない。global `0400171d`がfalseならfalse、
特別RawName・Skin不在等の分岐を通り、通常SkinではVersion > 1を返す。
特別RawNameは暗号化IDで識別できるが、今回その名前をDefault/Userと断定しない。

newAnchor側はfield type 12 / origin 8 / y=0、旧側はfield type 6 / origin 0 / y=426。
xはwidescreen分岐で140、その他120、次の位置へ57.6、その後48ずつ加算する。
これはstableの座標値。Javaの768基準画像canvasへ換算してから比較する。
Java `legacySelectionAnchors()`は`Version <= 1`だけであり、Version 1でもModsがbuilt-inへ落ちる場合を表現できない。

### normal/hoverは独立した画像探索

`0600136e`のmode `0874/08ba`、mods `0bbb/0c01`、random `0cde/0d24`、options `0dee/0e34`は、
normalとhoverをそれぞれmask 7で読む。normalはdepth .95、hoverは.96、hover側にinteraction flagとcallbackが付く。
Java [`SongSelectSkinAssets`](../core/src/main/java/dev/osujava/skin/SongSelectSkinAssets.java)はcurrent normalがあればhoverもcurrentだけに限定する。
この抑制は観測した独立探索と異なる。normal-only、hover-only、normalとhoverの寸法違いを独立fixtureにする。

### hitboxはalpha推測で確定しない

sprite更新 `060040af` `0db8–0e84`は位置、origin、scale、幅・高さから`04002860`矩形を作る。
幅・高さは`04002851/2850`、各scaleの絶対値を掛ける。丸め条件もある。この生成箇所にalpha scanはない。
行の`06000fbc`が当該矩形を`06005140`へ渡すことは確認済み。
ただしselection/Backのdispatcher全体、最小操作範囲、透明時のcallback条件、重なったspriteの優先順位は未確定。
**これだけで「すべてのボタンはPNG全矩形でclick可」と断定しない。**

Javaは[`SelectionAssetBounds`](../core/src/main/java/dev/osujava/skin/SelectionAssetBounds.java)でalpha 16/160の領域を測り、
[`SongSelectToolboxLayout`](../core/src/main/java/dev/osujava/ui/SongSelectToolboxLayout.java)でnormal/hoverのcontent矩形をunionし、固定slotへclipする。
全透明置換、透明余白、複合画像で操作領域が変わる独自仕様である。
描画の画像矩形、操作矩形、表示順を別々に記録し、stableのdispatcherを閉じてから置き換える。

## 3. top/bottomと画像からの配置推測

`0600136e`のtop `1b3a`以降、bottom `1c94`付近は各画像をmask 7で解決する。
depthは.7、topは左上、bottomは左下y=480の経路。top/bottomの呼出部には「他のcurrent画像があるならfallbackしない」という条件はない。

Javaはcurrent top/bottomを先に探し、行背景・Back・selection等のどれかがcurrentなら`authoredSurface`として欠けたchromeのfallback表示を抑制する。
さらに表示しないfallback topを`topLayoutFallback`へ保存し、そのalpha分布を配置予約へ使う。
これは部品間を結び付けるJava独自規則であり、確認したstableの個別探索と対応しない。
actual built-in画像の存在・画素は今回抽出していないため、各欠損fixtureの最終見た目は実機で確認する。

stable top延長には幅1366との比較、x=1365、crop x=1365 / width=1、拡大幅=表示幅−1365の経路がある。
任意幅のcustom画像でも「その最終列を伸ばせば同値」とは未確認。crop値がtexture densityや座標変換を通る位置まで閉じる必要がある。

**Java内の再現済み不整合:** Rendererの`drawTopSkin()`はtextureの最終1列を伸ばすが、
[`SongSelectTopCoverage.depth()`](../core/src/main/java/dev/osujava/skin/SongSelectTopCoverage.java)は延長先について最後の20×density列の最大深さを使う。
100×90画像で(x=98,y=89)だけopaque、最終列x=99は全透明にすると、`depth(100,120)`は90を返す。
延長描画が透明でも配置計算には高さ90が残る。これはstableを動かさず確認できるJava内の差。

40%/30%のchrome予約上限、alphaからの余白推定、巨大複合画像で補助UIを移動する規則も互換根拠は未確定。
過去の「行背景のalpha本体へfitする」という記録は現行にそのまま適用しない。
`rowBody()`の測定は残るが、現行main codeにそのgetterの呼出はなく、既にnative寸法描画へ変更された部分を再び未修正と数えない。

## 4. skin.iniは独立した互換対象

| 項目 | stableで確認した経路 | 現行Java |
| --- | --- | --- |
| キーのcase | Section `06005152`は通常のDictionary、`06005154/5156`は正規化せず登録・検索 | 多くのsection/keyがequalsIgnoreCase。AnimationFramerateだけexact |
| 同一section内の重複キー | `06005154`はContainsKeyなら追加せず、最初の値を保持 | 行順に処理し、後の有効値で上書き |
| boolean | `06005158`はBoolean.TryParse、その後ToInt32 → ChangeType(Boolean) | `0`と`1`だけを受理 |
| 色 | `06005158`は3/4成分を受理。4成分のalphaはAllowTransparentColoursがfalseなら255 | RGBちょうど3成分だけ。4成分は不採用 |
| Colours section | `060051a7` `0238–0256`でAllowTransparentColours=falseにして色辞書を読む | SongSelect色は独自parseRgb |
| CursorTrailRotate省略 | `060051a2`に初期化なし、CLR既定false。`06005172`はキーがなければ書き換えない | Cursor.defaults/parseともtrue |
| 既存iniでVersion省略 | ctorのVersion=1を保持 | 1。基本ケースは対応 |
| iniなし | wrapper `06002ac2`で2.7を初期設定 | readSelectedConfigurationで2.7。基本ケースは対応 |
| ini読込失敗 | `06002ac2`に通知・特別Skinへの再読込分岐 | IOExceptionをwithoutIniへ置換 |

caseの区別は[公式skin.ini仕様](https://github.com/ppy/osu-wiki/blob/master/wiki/Skinning/skin.ini/en.md)とも整合する。
一方、同資料のCursorTrailRotate既定値表記は1で、今回の通常SkinOsu読込経路のfalseと食い違う。
このbuildの根拠を優先候補とし、空ini / 明示0 / 明示1を実機で確認する。wiki表記からIL結果を消したり、ILだけで全設定経路の最終結果を断定したりしない。

CursorTrailRotateは読取だけではない。`06002b02` `0265–0280`がこの値を参照し、旧trail spriteの回転を0またはcursorの回転値にする。
ただしそこで使う設定は`06002ab8`がglobal optionによって二つのSkin設定から選ぶ。
「すべての状況で選択Skinの値が効く」まで確認したわけではない。

追加調査: section名・重複section、文字encoding/BOM、コメント、Versionの異常値、special RawNameの最終Version。
`06002ac2`で2.7を初期設定する特別名があるが、その後iniをparseする経路もある。
Javaの`User`特例をこの分岐だけから完全一致と認定しない。
RGBの範囲外値はILにbyte変換があるが、壊れた入力のwrapまで再現すべきかは安全性と分けて決める。

**設計判断:** raw設定の読取と、Skin identity・画面・設定ownerを考慮した有効設定の計算を分ける。
画像がfallbackしたからといって別Skinのiniまで自動継承しない。共有parser変更時はGameplay側の既存利用を回帰確認する。

## 5. cursor / trail / middleは同じfallback規則ではない

`06002ac0`で次を確認した。

- cursorとcursortrailは各々mask 7または3で独立に解決する（global option `04000d3e`で選択）。
- cursorはlocal 3、trailは`04001b19`へ保存。どちらかがnullなら後続の構築を終える。
- middleは`01d1–01e8`で**cursorの`04000338`だけをmaskとして**取得する。
  cursorがprovider 2ならmiddleも2のみ。missing middleをbuilt-inから自由に補う経路ではない。
- centre/rotate/expandの設定を読み、cursorの回転transformには0 → 2π、時間引数10000、繰返しflagがある。
- `06002b02`はmiddle側spriteのtexture有無で軌跡生成経路が分岐し、旧側は前述のCursorTrailRotateとfade引数150を使う。
  入力密度・clock・新側の時間契約すべてを今回確定したわけではない。

Javaは3画像を同じ汎用resolverで独立に解決する。
[`SongSelectCursor`](../core/src/main/java/dev/osujava/ui/SongSelectCursor.java)から共有LegacyCursorVisualを使うため、
fallback由来middleの有無が軌跡方式にも影響する。**cursor-only Skin + fallback middle**は優先fixture。
family全体を一律に固定する修正も不適切。cursor/trailの独立性とcursor/middleの結合を別々に実装する。

## 6. menu-backと連番画像

`06001b72/1b73`はframe 0とstaticを解決し、provider優先度4 > 2 > 1で選ぶ。同providerならframe 0が優先。
後続frameは解決済みframeのprovider値をmaskにして読み、nullで停止する。別providerで穴を埋めない。
`06001b1b`は正のAnimationFramerateなら1000/fps、それ以外なら1000/frameCountをframe間隔に使う。
これらの基本規則は既存Javaの連続prefix方式と共通するが、HD条件・壊れた候補・provider集合は同じではない。
[既存lazer基準のanimation調査](skin-animation-providers.md)をそのままstableの証明として使わない。

Back constructor `06003a15`は呼出引数が許可するとmask **3**でanimationを探索する。
texture配列が取れれば、同じ配列からdepth .9 / .91の二つのanimationを作り、後者に操作とblend/transformを付ける。
配列が取れない場合は文字・icon等を組み立てる別経路へ進む。単に標準menu-back.pngへfallbackするだけの構造ではない。
[公式Interface仕様](https://github.com/ppy/osu-wiki/blob/master/wiki/Skinning/Interface/en.md)もcustom Backとnative Backを区別している。

JavaはBack最大512frame、静的画像を先に確保した後に残りframeをロードし、hit測定は先頭frameのものを保存する。
stableのframe更新時の寸法変更、両層の同期、animation開始時刻・画面再入場、native/customの音の違いは追加調査対象。
連番が表示されることだけではBack互換完了としない。

## 7. Skinの対象台帳と未確定範囲

| 部品 | Javaの現状 | Skin調査として残す契約 |
| --- | --- | --- |
| top / bottom | 独自のauthoredSurface、予約、延長 | 個別探索、crop、stretch、全depth、clip |
| selection 4組 | normal/over登録済み | Mods providerを含むanchor、独立探索、hit/hover |
| menu-button-background | 譜面行・score行で使用 | native寸法、origin、各stateのtint、layer |
| star / ranking-small | 登録済み | Version別のscale/crop、銀grade等の分岐、素材と評価表示の分離 |
| selection-tab | 登録済み | 下地色、選択色、伸縮、文字と画像の重なり |
| mode 4種×3サイズ | 登録済み、欠損時generated経路あり | 各サイズのblend、fallback、全透明画像の扱い |
| selection-mod-* | 現enumは12種 | NC/PF等を含む必要assetの網羅、表示条件。Mods機能そのものの実装は別作業 |
| star2 / 中央装飾 | 登録・独自描画あり | 個数、位置、blend、clock。Skin素材と画面演出を分ける |
| cursor / trail / middle | 共有cursor描画に接続済み | provider結合、設定owner、旧/新trail、入退場 |
| menu-back | 連番と描画経路あり | custom/native、二層、animationとhitの時間 |
| interface sounds | select-expand/difficulty、menuclick/back/hit、click-short/confirm、back-button-hover、key-press-1..4 | 別名候補の順、provider、拡張子、decode失敗、gainと同時発音 |
| skin.ini / Fonts | 共通parser、各font prefix等あり | UI文字とSkin bitmap fontを区別。Fonts設定だけで行labelのGDI系文字を再現できるとは限らない |
| import / reload / dispose | importer、予算、Texture ownershipあり | 対応形式、case衝突、Skin切替・HD変更時のcache更新と破棄 |

音はJavaがproviderごとにwav → ogg → mp3を試すことを確認したが、stableの同等な順序は未確定。
Back callbackが二つの文字列候補を音声側へ渡す既存観測も、今回候補名・優先順位を閉じていない。
音源のprovider解決をSkin側、いつ鳴らすかを画面の入力側へ分け、画像loaderの規則を音に流用しない。

既存のケース無視ファイル検索と設定所有者統一は今回のprobeでも確認した。
旧監査S02「画面間でiniが異なる」、S12「Cursor.PNGを解決できない」を現在の未修正事項へ再掲しない。
一方、INIのキーcaseとfilesystemのcaseは別問題であり、一緒にcase-insensitiveにする修正はしない。

## 8. 改修計画と着手順

### A. 比較可能なSkin fixtureと診断

自作PNG/音と短いiniでprovider・density・寸法を識別できるfixtureを作る。
同じ入力に対して「選ばれたファイル、provider、density、有効設定、draw矩形、origin、depth、blend、hit、frame時刻」を出力できるようにする。
公式assetを抽出しない。既定Skinの厳密な画素比較は、同じ素材を合法的に用意できる範囲と代替素材との差を明記する。
現行の独自bundled素材とstableのbuilt-in素材を同一と仮定しない。

### B. resolverと設定の契約修正

1. case/重複/boolean/色/省略値を、根拠付きfixtureでparserへ反映する。
2. provider identity、許可mask、HD eligibility、失敗理由を返せる最小のresolver APIへ変更する。
3. animation、selection normal/hover、cursor/middle、chrome、soundの規則を各部品側へ接続する。
4. current normalによるhover抑制、authoredSurfaceによるchrome抑制を、確定した探索契約へ置き換える。

ここは共有Skin APIの変更が必要。巨大な汎用plugin/provider frameworkや全Gameplay rendererの置換は必要と判断していない。
単なるfallback booleanの追加分岐を積み重ねるとcursor/middleやanchorの出所依存を表せないため、結果型と呼出契約の変更は許容する。

### C. Skinの描画・操作geometry

Mods画像の解決後にanchorを決定し、画像のnative寸法・origin・座標単位を明示する。
top延長と予約のJava内不整合を解消する。alpha予約・固定slot制限の撤去範囲は、stable dispatcher/clipの追加調査結果で決める。
drawとhitへ同じtransformを渡しつつ、二つの矩形を混同しない。
画像の欠損を理由に選曲側の別UIを動かす独自規則も、通常互換画面に残すか個別に判断する。

### D. 時間・音・切替

Back二層とcursorの時間契約、音の候補探索、Skin/解像度切替時の更新を順に閉じる。
Skinは素材と設定・解決結果を提供し、時間を進める責務や入力イベント発火を持たせない。
Import / Gameplay / Renderer / Ruleset / GameClockの分離は維持する。

### E. 実機による合格判定

正常起動できるb20230727.9をネットワーク遮断状態で通常起動し、同じ自作Skinと譜面を用いて比較する。
起動保護の回避で比較環境を作らない。安定して到達できるまでは「静的契約対応」と「実機1:1」を別の進捗にする。
解像度1024×768 / 1280×720 / 1920×1080、HD条件、設定、画面進入時刻、入力列を固定する。
画像差分だけでなくhover/click対象、animation frame、音の選択と発音時刻も比較する。
未確定の条件を一律許容差やmaskで隠して合格にしない。

## 9. 優先fixture / 完了条件

| fixture | 確認する契約 |
| --- | --- |
| 空ini / iniなし / Version 1, 2, 2.2, 2.7 / special名 | 設定defaultと有効Version、anchorを別々に比較 |
| Version 1 + custom Mods / Mods欠損 | built-in providerによるanchor変更 |
| normal-only / hover-only / 異なる寸法 | 独立探索、合成、hit owner |
| row-only / Back-only / top-only / 全透明top | 個別chrome fallback、見えない予約の有無 |
| SD/HDを異なる色、HDのみ、壊れたHD+正常SD | HD条件、存在とdecode失敗の区別 |
| cursor-only + 別provider middle / trail-only | cursorとmiddleのprovider結合、trail独立性 |
| CursorTrailRotate省略/0/1、CursorRotate:false | parserと旧trail描画の両方 |
| Version重複、version小文字、4成分色 | 最初のキー、case、alpha無視のRGB受理 |
| 全透明1px、alpha 15/16/159/160、空洞、巨大余白 | sprite矩形とalpha推測の差、操作の優先順位 |
| topの最終列だけ透明、幅1366前後、SD/HD | 延長cropと配置予約の一致 |
| Back static+frame0、途中欠番、途中破損、寸法変化 | provider選択、停止条件、hit更新、二層同期 |
| fps省略/-1/0/正値、画面再入場 | frame間隔とclock起点を分離して確認 |
| 同名音を異なるprovider/拡張子に配置 | 音の探索と別名候補、欠損時の挙動 |
| Skin切替、画面サイズ/HD条件変更、画面往復 | cache更新、設定owner、破棄、残像 |

pure resolver/parserテスト → geometry/時間の単体比較 → Java描画capture → stableとの同条件capture、の順で進める。
既存テストが通るだけでは互換証明にならない。独自のalphaやfamily規則を固定している期待値は根拠を更新してから変更する。
共有コード変更時はSkin/Gameplay双方の回帰テストとGradle buildを実行する。

## 10. 今回の検証結果

GL不要の一時Java probeを現行build/classesに対して実行し、次を再現した。

| 入力 | Java結果 |
| --- | --- |
| `[General]`でVersion:1、続けてVersion:2.7 | 2.7 |
| `[General] version:2.7` | 2.7 |
| CursorRotate:false | falseを受理せず既定true |
| 空General、CursorTrailRotate省略 | true |
| SongSelectActiveText:255,128,64,0 | 色を受理せず既定黒 |
| 100×90のtop、(98,89)だけopaque、`depth(100,120)` | 90 |
| HD/SDの両ファイルあり、predicateでHDを拒否 | SDを選択 |
| Cursor.PNG | cursorとして解決成功 |
| selectedフォルダーにiniなし | menu/gameともVersion 2.7 |

probeは`/tmp/skin-independent-probe/SkinProbe.java`、IL出力は`/tmp/skin-independent-*.il`へ一時保存した。
一時ファイルの永続性に依存しないよう、上記に入力・結果とmethod tokenを記録した。
fixtureの画像読込predicate試験は実PNGのdecode試験ではない。stable実行、pixel/audio比較、実Skin横断の合格率は今回の検証に含めない。

製品コード・テストコードは未変更。文書のローカルリンクに欠損なし、`git diff --check`も成功。
`./gradlew build --offline --console=plain`はBUILD SUCCESSFUL、13 taskすべてUP-TO-DATE。
既存JUnitは今回再実行しておらず、上記Java probeを新たに実行した。build成功を新しい互換テストの合格とは扱わない。
