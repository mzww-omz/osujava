# Song Select — 解消可能な差の修復（2026-10-02）

開始HEAD `2cb934a`。残件R04の下部input/Back画像探索、およびR09のJava内のpreview不整合を修復した。
全画面のstable一致は未認定。[統合残件台帳](songselect-remaining-work-20261002.md)と
[詳細比較台帳](songselect-one-to-one-gap-ledger-20261002.md)も更新した。

## 発見・修復・設計判断

| 問題 | 修復 | 確認範囲 |
| --- | --- | --- |
| 下部inputがnormal/hoverのalpha領域unionと固定slotで縮み、透明marginを押せない | input ownerをraw hover sprite矩形へ変更。Backもraw sprite矩形。画像サイズ・origin・density・drawは維持 | alpha 0/15/16/159/160/255、透明canvas、非対称margin、normal/hover異寸法、density 1/2、720/800/768高 |
| Backを先にhitすると、重なったselectionより優先される | selectionの生成depth .96はBack .91より優先。同depthではMODE→MODS→RANDOM→OPTIONSの生成順 | 巨大Backと複数overの交差、画面端、local Importの優先、実Screenからoverlay/Back遷移 |
| Backのstatic/zero競合で下位providerのanimationを採用し得る | staticとzeroを独立探索し、上位providerを優先。同providerならzero優先。勝ったzeroのproviderで後続frameを探索 | custom static対fallback animation、custom zero対fallback static、frame欠損、texture ownership/dispose |
| 壊れたHD frameをSDで置換し、nativeにない連番を作る | 最初に存在する候補のdecode失敗でその画像探索を停止。zero失敗時は別static familyを試し、後続frame失敗時は連番終了 | 壊れたHD zero/追加frame、SD siblingが存在してもretryなし。Gameplay/Resultsのresolver APIは維持 |
| Back連番全frameをPixmapで再decodeしalpha mapを作るが、修復後は用途がない | Back alpha map/getter/scanを削除 | Back描画・連番・寸法寿命・所有権は既存test/GL harnessで確認。selection alpha診断、top coverageは維持 |
| mouse release frameに新譜面metadataと旧previewが混在 | 選択を確定するinput後にpreviewを更新 | production Screen testで同frameの選択とロードpathが一致 |
| load/backend失敗後、同音源の別難易度へ移っても復旧しない | 選択epochを渡し、失敗streamは新しい選択で1回だけretry。健康な共有streamは継続 | 60 frameで追加loadなし、選択変更で復旧、旧streamのgain解除/dispose、closeでpositionリセット |

inputはviewport内に制限する。hoverが実際にない場合はnormal、双方ない場合はprocedural slotへ
fallbackするローカル方針を維持する。local Importも独自操作なので優先を維持する。
大きな**normal**装飾は独立hoverのhitを広げない。作者が大きな**hover**画像を指定した場合は
その矩形がinputを持つ。これはalphaや固定slotを根拠なく互換仕様にしないための修正。

previewの失敗retryと同frame整合はJava内の修復であり、nativeのretry時刻を再現したとの主張はしない。
健康な同音源でPreviewTimeを再適用するかは未確認なので、既存streamを継続する。

## stableから確認した契約

read-onlyの[既存tool](../tools/stable_results_inspect.py)でb20230727.9を確認。
SHA-256 `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`、
dnfile 0.18.0／dncil 1.0.2。IL全文・内部実装・公式assetはrepoへ追加していない。
native実行による同期captureは今回も未実施。

- `06002c1f`はdrawn/visible/handle-inputとstrict priorityを調べ、pointerとmargin 0を
  `060040ce→060040cf`へ渡す。PNG alpha判定は通らない。margin 0ではraw sprite rectangleを使う。
- `0600136e`ではselection normal .95は装飾、hover .96へinput callbackを接続。
  `06003a15`のBack interactive spriteは.91。mouse priorityは負の生成depthで、同priorityは先着。
- `060040af`のraw矩形はlogical texture寸法とtransformから作る。条件付きpixel丸めがあるため、
  Javaのfloat矩形だけで物理pixel境界の完全一致とはしない。nativeの矩形containは半開区間だが、
  座標反転と丸めを含む最終境界はR01に残す。
- `06001b71`は最初に存在するeligible候補でdecodeを試す。失敗後のSD sibling探索はしない。
  `06001b72/06001b73`はzeroとstaticのownerを比較し、同ownerならzero、
  後続frameはzeroのownerに固定して最初のmissing/decode失敗で止める。
- nativeのbeatmap image providerとJavaの任意fallback skin directoryは別の仕組み。
  provider内のHD/SD選択と連番契約を対応させ、Javaのfallback優先順自体は維持する。

再確認:

```sh
/tmp/osujava-songselect-audit-venv/bin/python tools/stable_results_inspect.py \
  '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 06002c1f 060040ce 060040cf 060040af 06005142 0600513d \
  06001b71 06001b72 06001b73 0600136e 06003a15
```

一時資料: `/tmp/osujava-actionable-button-contracts.il`,
`/tmp/osujava-actionable-hit-and-loader.il`。消失時は上記toolで再取得できる。

## Asset・内部構造・性能

[全面棚卸し](songselect-full-audit-20261002.md#skin-asset棚卸し)の48画像・12音声の分類を継続。
新しいasset/renderer/backendは追加しない。既存hoverとBack画像のinput接続、
既存Back連番の探索修復が対象。意図的未接続のonline/player/replay/score Mods/silver gradeは、
実データ・対応機能がないので維持。alpha診断はselectionのみ、Backの不要な解析は削除した。

Renderer/logic、Importer/Gameplay、Java 21/libGDX/LWJGL3、完全ローカル要件を維持。
共通resolverの従来APIは変更せず、Song Select Back専用APIを追加した。
良いproviderを先にdecodeするので、捨てるfallback animationが64 MiPixel予算を消費しない。
Backの二重decode/scanを除去。毎frame texture/font生成、全beatmap scan、text再測定は追加しない。
preview epochの照合は参照比較のみ。CPU/GPUの前後benchmarkは今回未実施。

## 主な変更ファイル

- [SkinAssetResolver](../core/src/main/java/dev/osujava/skin/SkinAssetResolver.java)、
  [SongSelectSkinAssets](../core/src/main/java/dev/osujava/skin/SongSelectSkinAssets.java)
- [SongSelectToolboxLayout](../core/src/main/java/dev/osujava/ui/SongSelectToolboxLayout.java)、
  [SongSelectAction](../core/src/main/java/dev/osujava/ui/SongSelectAction.java)
- [SongSelectScreen](../core/src/main/java/dev/osujava/ui/SongSelectScreen.java)、
  [SongSelectPreview](../core/src/main/java/dev/osujava/ui/SongSelectPreview.java)
- skin resolver/assets、toolbox/corpus、navigation/preview regression tests、
  [SongSelectVisualHarness](../lwjgl3/src/hudHarness/java/dev/osujava/ui/SongSelectVisualHarness.java)

## 検証

- `./gradlew build --console=plain`: core **131 suites / 1,290 tests**、lwjgl3 **2 suites / 4 tests**。
  新規13 tests（resolver/assets 3、toolbox 7、preview/navigation 3）。failure/error/skipped 0。
- `button-hit-contracts`: **24 scenes / 48 PNG / 384 scripted transition frames**、navigation/disposal成功。
  透明marginから実Mode overlayを開き、Backの透明marginから退場要求を確認。
- `skin-contracts`: **24 scenes / 24 PNG / 8 scripted transition frames**、navigation/disposal成功。
- `parity-visual`: **30 scenes / 30 PNG**、navigation/disposal成功。
  幅1365/1366/1367、4:3境界、odd HD、透明Back、異寸法Back連番を含む。
- `audit`: **84 scenes / 172 PNG / 1,256 scripted transition frames**、navigation/disposal成功。
  empty/single/many、展開/折畳み/scroll、long/Unicode、背景/thumbnail欠損、search、
  score 0/1/多数/hover/wheel、巨大chrome、resize、保存/再読込を含む。
  1280×720、1280×800、1024×768、1280×720 framebuffer density 2。

上記PNG数はharness報告値。補助captureを含むdirectoryの実ファイル数とは異なる。
unit/Java captureによる回帰検証であり、nativeとの同期比較ではない。
最初のauditは完了前に終了signal（exit 143、assertionの報告なし）で中断した。
同じphaseを独立runnerで再実行し、全84 scenesの完了とexit 0を確認した。
古い「nominal slot以下」「透明ならinputなし」というJava testの期待値は、確認したnative契約へ更新した。
1px透明canvas中央を物理pixelへ丸める新規fixtureは4:3で矩形外へ出たため、
GLクリックfixtureを通常寸法の完全透明canvasへ修正。1px矩形のunit検証は維持し、丸めはR01に残す。

ログ: `/tmp/osujava-actionable-final-build.log`, `/tmp/osujava-actionable-buttons-v2.log`,
`/tmp/osujava-actionable-skin.log`, `/tmp/osujava-actionable-parity.log`,
`/tmp/osujava-actionable-audit-v2.log`。
PNG: `/tmp/osujava-actionable-buttons-v2`, `/tmp/osujava-actionable-parity`,
`/tmp/osujava-actionable-audit-v2`。4:3のscore多数と透明marginからのMode overlayを目視確認。

新規GL phaseは下記で再現できる。既存phase `skin-contracts`, `parity-visual`, `audit`も使用。

```sh
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=button-hit-contracts \
  -PsongSelectOutput=/tmp/osujava-button-hit-contracts
```

## Commit

- `d1150d1` — `fix(song-select): resolve Back animation without hidden density retries`
- `754eed8` — `fix(song-select): synchronize preview with selection and retry failed audio`
- `bf53f1a` — `fix(song-select): use native hover sprite bounds for bottom actions`

## 残る差と次の作業

1. R01/R02/R04: global callback/frame順、物理pixel丸め、focus時のBack・行sprite時計。
   同一自作skin・固定入力/時刻でnativeのcallbackとsnapshotを比較してから修正する。
2. R03: native HD eligibilityのoption名・display/window区別・reload契機。
   `06002340`の条件だけでwindow heightを代入せず、SD/HD混在fixtureで採用pathを測定する。
3. R05/R10/R11: font/GDI対AWT、metadataの5行階層、全frame合成、chrome安全上限/clip、
   portraitのscore非表示、独自Cookie/拍/粒子。許可font/skinによる同期画素比較と設計判断が必要。
4. R06/R07/R08: 本番star計算/ranked source、通常Mods/他mode/Options/Collections/Recent、
   player/mods/replay保存、score bar連続scroll/track/native配置。backendとnative観測が必要。
5. R09: 同音源別PreviewTime、loop/focus/Gameplay復帰のnative音声契約。
   healthy stream継続・未指定0は現Java方針のまま。音源positionとload/seek/fadeを比較する。
6. 詳細D04: searchのnative300ms待機経路は確認済みだが、force/IME/callbackの適用条件が未確定。
   現在の即時検索を維持し、編集/clear/再入力/IME列で更新時刻を比較してから対応する。

Bancho/online等は完全ローカル要件により対象外。未確認差を解消済みとして数えない。
