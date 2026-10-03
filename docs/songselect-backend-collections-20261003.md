# B04: ローカルCollectionsとBeatmap Options

2026-10-03、開始HEAD `2292fdd`。[backend改善計画](songselect-backend-improvement-plan-20261002.md)のB04を実装した。
完全ローカル、Java 21/libGDX/LWJGL3、Importer / Gameplay / Renderer / Ruleset / GameClockの責務分離を維持する。
アプリへのネットワーク接続・公式asset抽出/再配布は追加していない。

## 発見・修復・接続

- Collections tabは既存の描画/inputを持つが常にunavailableだった。内容に照合するlocal storeを作り、
  既存tabとGroup dropdownへ接続した。4:3では既存のnative tab表示条件を維持し、Groupから選べる。
- OptionsはF3/右クリック/下部skin buttonからtoastだけを出していた。
  実際のBeatmap Optionsを開き、`1 Manage Collections`から作成・rename・削除・登録解除を操作できるようにした。
- 複数collectionに同じ難易度がある場合、従来のset/pathだけのrow keyでは表示を分離できなかった。
  collection UUIDをrow/group/familyへ付け、Gameplay/scoreの難易度identityは元のset/pathと内容keyのまま維持した。
- rowのselected判定とclickがset/difficulty indexだけを見ていた。
  collectionをまたぐ同じ難易度の初回clickは、その表示rowを選択する。選択済みrowの再clickでのみプレイする。
  score対象・選択score・query cacheはcopy間でも維持する。
- 管理画面が開いている間は検索/選曲/Import/Play/score scroll/dragを停止し、開く際に保持中のrow pressを解除する。
  closeや数字shortcutが背後の検索・navigationへ漏れないことも確認した。
- 既存auditの検索入力が固定座標でCollections tabを押していた。
  共有`SongSelectLayout.search`から座標を取り、focus獲得もassertするよう修復した。
- 調査でDate Added sortがwikiの方向と逆だったことを確認。
  B03の新しい順を、既知日時の古い順へ修正した。unknownは最後、Last Playedは引き続き新しい順。

## 根拠と実装範囲

[公式wikiのClient / Interface](https://osu.ppy.sh/wiki/en/Client/Interface)で次を確認した。
Collections groupingは未登録譜面を隠す。Beatmap OptionsはF3/譜面の右クリック/下部buttonから開き、
`1 Manage Collections`でcollectionの編集と、難易度またはmapsetの追加/解除を行う。
同じ文書のSort表ではDate Addedを古い順としている。

今回確認したのはこの機能と導線。native collection managerのpixel位置・hitbox・animation・名前の
重複制限・IME/editor詳細を同期観測したとは主張しない。新しいdialogはosu!javaの独立実装である。
Collectionsの名前が重複していても、local UUIDが異なれば別collectionとして扱う。

## 保存・照合の契約

`~/.osujava/collections.properties`、osu!java独自schema 1。
collection UUID/nameと、memberのraw SHA-256＋mode / 元setId＋pathを保存する。
nameはNFCへ正規化し、空・制御文字・不正surrogate・80 code points超を拒否する。
memberは同じ内容keyをcollection内で重複登録しない。別collectionへの重複登録は可能。

| 操作 / 状態 | 契約 |
| --- | --- |
| create / rename / delete / add / remove | 一時ファイルから全体をatomic置換し、成功時だけimmutable snapshotとrevisionを公開 |
| mapset一括登録 | 操作時の実在する各難易度を一回の保存で登録。後から追加された難易度は自動登録しない |
| 一部登録済みmapset | Addで不足memberを補う。全て登録済みのときだけRemoveを表示 |
| 同内容の移動・複製 | 内容keyで現在のlocal譜面へ解決。元locatorは保存記録として保持 |
| 内容更新・消失 | 旧memberをmissingとして保持。類似metadata/同pathへ付け替えない |
| Remove missing maps | 現libraryで解決できない内容だけを明示的に解除する。自動cleanupしない |
| collection削除 | 確認画面で実行。譜面・score・履歴は削除しない |
| 不正/将来schema | 元bytesを保持し、store全体を編集不可にする。部分読込した内容で上書きしない |
| 外部変更・ファイル消失 | 書込前に読込時bytesと照合。観測した変更を上書きせず、保存失敗を表示 |
| 保存失敗 | 成功したmembershipやrenameを公開せず、UIへerrorを返す。通常write失敗はfilesystem復旧後に再試行可能 |
| load | 通常読込はファイルを書き換えない。将来schema/壊れたstoreの復旧後は再起動して再読込する |

collection数10,000、member総数100,000、ファイル32 MiBのlocal上限を設ける。
multi-processの同時編集や自動mergeは対象外。外部ファイルのlive reloadも今回追加していない。

## 表示と入力

collection membershipと既存searchの交差だけを表示し、未登録難易度は隠す。
空・missingのみ・search一致なしのcollectionはcarouselにplayable rowを持たず、管理画面に件数を表示する。
collection modeで対象が0件なら、F3への導線を含むempty stateを表示する。
selectionは元の難易度identity、表示上の選択はcollectionごとのrow keyとして扱う。
有効な選択はrevision・rename・検索解除で保持し、削除後は残るmemberへ修復する。

管理画面は8件/page。wheel/Previous/Next/Up/Downで多数collectionを操作できる。
New/Renameでは入力、Backspace、Ctrl+A、Enter保存、Escキャンセルを使用する。
editor中のUTF-16 surrogate pairは1 code pointとして扱う。非editor時のN/R/Deleteも管理操作へ対応する。
resize後もdraftを維持し、描画とclickは同じlayout boundsを使う。
管理中にlibraryが更新された場合は、古い譜面への操作を避けるためdialogを閉じる。

Rendererはcached snapshotだけを受け取り、storage/CRUD/inputを実行しない。
z-orderは既存Song Select foreground → modal dim/panel/controls → transition cover → cursor。
chrome・carousel・下部buttonsはdimの背後にあり、入力もblockedになる。
新規の公式skin assetは使わず、既存Options/Tab/row assetと共通UI font/shape fallbackを維持する。

projectionはlibrary/search/sort/group/collection revisionの変更時だけ再構築する。
内容indexからmemberを解決し、collectionごとに全libraryをscanしない。
managerのrow/countはrevision単位でcacheし、Rendererは最大8行だけ描く。
idleのファイルread/write・全譜面layout再計算・texture/font生成は追加していない。
CPU/GPUの前後benchmarkは未実施。

## 検証

- `./gradlew build :lwjgl3:hudHarnessClasses --console=plain`成功。
  core **142 suites / 1,353 tests**、lwjgl3 **2 suites / 4 tests**、failure/error/skippedすべて0。
- 新規20 test methods（store 6、projection 4、manager 6、Screen 3、score対象 1）。
  Groupの既存parameterized case増加も含め、前回から21 tests増加。
- 保存：CRUD、Unicode/NFC、不正name、重複member/名前、再起動bytes保持、未知schema、保存失敗、外部変更/消失。
- projection：難易度単位のsearch、複数collectionの同譜面、移動/内容更新/missing、新difficultyの非自動登録、
  rename/delete/検索解除の選択、score対象/選択/query cacheの保持。
- input/layout：実keyboardとclickで作成・登録、背後への入力漏れ、保持中press/dragの取消、resize後の保存、
  16:9/16:10/4:3の管理button boundsに重なりがないこと。
- 実GL `collections-contracts`: **16 scenes / 100 PNG / 1,588 scripted transition frames**＋navigation/disposal。
  empty / manager / many / missing、80 code point名・CJK/補助平面文字、create/rename/register/delete、
  tab/dropdown、検索との交差、restart、idle cacheを操作した。
  1280×720、1280×800、1024×768、1280×720 framebuffer density 2。
  Greylooks、procedural fallback、HD-onlyで管理画面とcarouselを確認した。
- 既存GL `pointer-contracts`: **16 scenes / 60 PNG / 416 frames**。
- 既存GL `score-scroll-contracts`: **12 scenes / 48 PNG / 48 frames**。
- 既存GL `backend-contracts`: **16 scenes / 28 PNG / 672 frames**。
- 既存GL `history-contracts`: **16 scenes / 32 PNG / 1,536 frames**。
- 既存GL `audit`: **84 scenes / 172 PNG / 1,256 frames**。
- 合計 **160 scenes / 440報告PNG / 5,516 scripted transition frames**。
  default/fallback、ranking、long/Unicode、backgroundなし、empty/single/many、展開/折畳み、resizeとnavigationを回帰確認。

```sh
./gradlew build :lwjgl3:hudHarnessClasses --console=plain
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=collections-contracts \
  -PsongSelectOutput=/tmp/osujava-collections-reproduce
# 各runの出力先を分ける。phaseをpointer-contracts / score-scroll-contracts /
# backend-contracts / history-contracts / auditに変えて既存回帰を再現できる。
```

ログ：`/tmp/osujava-b04-final-build.log`、`/tmp/osujava-b04-gl-<phase>.log`。
最終auditは`/tmp/osujava-b04-audit-final.log`と同名output directory。
collection captureは`/tmp/osujava-b04-gl-collections-contracts`。
4:3管理画面とeditorを目視した。captureは自作fixture/既存許可skinであり、native同期比較ではない。

## 未使用のままにした機能・次の残件

- Optionsの譜面削除/score消去/Export/Edit、公式`collection.db`読込は未実装。今回有効化していない。
  譜面/scoreの削除・退避・復旧はB09で独立した保存契約とtestを作る。
- managerの正確なnative寸法・animation・連続scroll、name editorのcaret移動/範囲選択/clipboard/IME詳細は未比較。
  local managerはpage操作と末尾編集。CJKは描画できるが一部emojiは現行fontで欠字になる。保存では保持される。
- 重複nameと、同内容が複数local pathにある場合の全一致row表示はosu!javaの明示的なlocal方針。
  nativeの制限/代表path選択を観測して1:1扱いにする作業は残る。
- 外部変更のlive reload/merge、壊れたstoreの編集画面はない。元ファイルを保持し、復旧後再起動が必要。
- B03の小数日丸め/native日付group/日中predicate更新、参照が消えた旧内容の追加日時保存は継続残件。
- 次のB05はproduction star rating sourceとDifficulty tab。B00でreference commit/algorithm/version/許容差を固定し、
  独立NM calculator・worker・cacheを実装して既存星rendererへ供給する。
  HP/fail(B06)、通常Mods(B07)、replay(B08)、Results label重なりとprofile編集UIも未完了。

主な変更：`LocalCollectionStore`、`OsuJavaGame`、`SongBrowserModel/Controls`、
`SongSelectCollections/Overlay`、`SongSelectScreen/Input/Renderer`、関連testsと`SongSelectVisualHarness`。

実装commit：

- `ba96ba3` — `feat(collections): persist local content membership with atomic updates`
- `d231446` — `feat(song-select): project collections without changing playable identities`
- `4758ba5` — `feat(song-select): connect beatmap options to local collection management`
- `e56599c` — `fix(song-select): sort known import dates from oldest to newest`
- `4a77424` — `test(song-select): cover collection dialogs and use search layout bounds`

本書・改善計画・進捗・残件台帳は別のdocs commitとして記録する。
