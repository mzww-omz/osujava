# Song Select残件対応: row候補・opacity・sprite寿命

開始commit `43940b0`、開始時working treeはclean。
[前回の残件表](songselect-follow-up-20261002.md#未完了項目と次の実装単位)の
重複row入力を対象に、既存carouselとimmutable描画geometryを維持して修復した。

## 発見・修復

- 選択中rowを常に先にhitする近似を廃止。背景sprite作成時の負のdepthを
  mouse priorityとして保持し、重複時は小さい値、同値なら先の候補を採る。
  .6から.00003ずつのfloat加算はhidden rowを含むbrowser順で、順序変更時だけ計算する。
  resident rowのsort/resizeでpriorityを書き換えず、sprite再生成時に取り直す。
- 新hoverの取得を実際のbackground opacity==1へ接続。
  フェード中の候補も「hitあり」と扱い、直前のlogical hoverを維持する。
  既存hover自身はopacityが1未満でも保持可能。opacity<=.001のspriteは候補から除外する。
  候補がなくなれば即時にhoverを解除し、旧75ms猶予を削除。
  left drag/right scroll中はlogical hover更新を止める。
- 非表示difficultyの展開時に即時表示が適用され、新しいsiblingが代表rowのhoverを
  奪っていた。buffer内の復帰expanded rowを200ms base fadeで生成し、
  代表rowの既存opacityを残す。展開直後の次のclickで再生できる操作を維持した。
- 同じframe間にhide→再表示したrowで、foreground cacheが古いopacityを使い続ける
  問題を修復。sprite generationを照合し、foregroundを新しいsprite寿命へ合わせる。
- 旧`SongSelectRow.hit`を削除。新controllerは共有canvas・window/chrome境界を使い、
  press identityとcurrent boundsによるrelease判定は維持する。
  Rendererにselectionやinputの責務を追加していない。

既存foreground opacityを描画だけでなく入力条件にも接続した。
新規skin asset、network機能、偽のscore/metadataは追加していない。
前回の48画像・12音のasset分類と意図的な未使用項目は変わらない。

## stable調査の根拠

b20230727.9、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
既存`tools/stable_results_inspect.py`を使ったCLR metadata/ILの読み取りのみ。
binary起動、文字列復号、公式asset抽出、保護回避は行っていない。

| 確認箇所 | 確認した仕様 |
| --- | --- |
| `060040a7` `005c–006c`、`06000fbe` | constructorのdepth負値をmouse priorityへ保存。後の描画depth更新はmouse priorityを更新しない |
| `06003287` | .6から.00003のfloat加算で全browser rowにdepthを割り当てる |
| `06002c1f` | より小さいpriorityだけが候補を置き換える。同値は先の候補を維持 |
| `060040af` `0039–005b` | background opacity>.001とmanager opacity>0が描画条件。PNG alphaの走査ではない |
| `06003256`、`06003267`、`06000fd6` | 新logical hoverにはopacity==1が必要。fading候補はhitありとして直前hoverを保持。drag/right-scroll中は更新しない |
| `06000fd5`、`06000fd3`、`06000fcd`、`06000fd1` | 新stateを設定した後、state>=3で必要なspriteを非instant生成。状態復帰の位置継承とviewport進入時のinstant生成は別経路 |

dumpは`/tmp/osujava-hit2-{pipeline,resident,returning,state-transition}.il`、
xrefは`/tmp/osujava-hit2-returning-refs.txt`。
確認した条件をJava側のUI modelとして独立実装し、nativeコードの移植は行っていない。

## 検証

- Song Select関連419 tests成功。優先度・同値・sort後の寿命・hidden depth・fadeの
  0/途中/1・新規/既存hover・gesture・canvas edge・予約領域を検証。
  navigation fixtureはcarouselだけでなくproduction updateでopacityも進める。
  同一frame間のhide→復帰、展開直後の左/右/中ボタン、押下identityも検証する。
- `./gradlew build --offline --console=plain`成功。
  core 130 suites / 1,259 tests、lwjgl3 2 suites / 4 tests。
  failures/errors/skipsはすべて0。最終ログ`/tmp/osujava-hit2-final-build.log`。
- `row-input-contracts`: 自作700×117 SD / 1400×234 HD背景と既存bundled fallbackで
  12 scenes / 28 PNG / 56 scripted frames成功。
  1280×720、1920×1080、1024×768、1280×720のframebuffer 2倍で、
  展開→即時再生の左・右・中ボタン入力をproduction Screenで実行する。
- 既存`pointer-contracts`: 16 scenes / 60 PNG / 416 frames成功。
  `drag-contracts`: 16 scenes / 96 PNG / 1,028 frames成功。
  procedural `activation-contracts`: 12 scenes / 28 PNG / 56 frames成功。
  各runのnavigation/disposal checksも成功。
- 巨大な共用row/score背景fixtureの再実行で、Random後の選択により旧「文字中央は
  必ずselected rowをhitする」smoke前提が不安定だと確認。巨大canvasでは別rowが
  同じpixelを覆うため、harnessをcreation priorityが選ぶidentityのpress/releaseと、
  Groupの場合の展開/折り畳み・選択維持、およびSpaceによるPlay到達の検証へ修正。
  通常サイズのselected re-click検証は維持。
  `phase4-oversized-score`単独runは16:9・4:3双方で成功。
- 修正後の全体`audit`: **84 scenes / 172 PNG / 1,256 scripted frames**成功。
  16:9、16:10、4:3、framebuffer 2倍。empty/single/many difficulty、背景なし、
  long/Unicode metadata、local score 0/1/多数、hover/wheel/selection/collapse、resize、
  巨大/透明chrome、skin/fallback/density、save/reload、navigation/disposalを再確認。
  最終runは`4d6c8d3`、Gradle・Xvfb wrapperとも終了コード0。
  ログ`/tmp/osujava-hit2-final-audit-v2.log`、captureは同名directory、
  wrapper記録`/tmp/osujava-hit2-final-audit-v2-exit.json`。

入力harnessログは`/tmp/osujava-hit2-{row-input,pointer-contracts,drag-contracts,activation-contracts}.log`。
最終CPU probeログは`/tmp/osujava-hit2-final-perf-{before,after}-{1,2,3}.log`。

再実行:

```sh
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=row-input-contracts \
  -PsongSelectOutput=/tmp/osujava-row-input
```

## 性能

既存production update probe、1,200 warmup / 1,200 samples、512MiB固定heap。
`43940b0`の変更対象5 classesを`/tmp`へ独立compileしたbeforeと最終classesを、
run 1・3はafter→before、run 2はbefore→afterの順で各3回測定した。
visual harnessを同時に動かしていない。
[全180測定CSV](songselect-row-input-performance-20261002.csv)を保存した。

| Sets / 操作 | 平均の中央値 before → after (µs) | p95の中央値 before → after (µs) |
| --- | ---: | ---: |
| 100 / idle | 17.071 → 18.669 | 18.013 → 17.303 |
| 1,000 / idle | 19.209 → 19.719 | 16.561 → 18.725 |
| 10,000 / idle | 63.653 → 91.328 | 68.479 → 117.070 |
| 10,000 / wheel | 64.034 → 60.905 | 67.046 → 73.709 |
| 10,000 / fast-wheel | 72.267 → 75.700 | 88.456 → 86.633 |
| 10,000 / selection | 458.384 → 442.466 | 3,986.997 → 4,126.349 |

全条件で高速化した結果ではない。10,000 idleのp95は約49µs増、selectionは約139µs増。
最初の計測ではselectionの増加が大きかったため、一時probeでevent/updateを分離。
既存のselection eventが主なコストで、reverse順ではbefore側も遅くなり、JIT/hostの
ばらつきがあることを確認した。そのうえで、browser順が変わらないselection/resizeで
depthを全rowへ再代入する不要処理を除き、最終条件で全runを取り直した。
最終10,000 idleのallocationは12,232.9→12,206.1 bytes/frame、selectionも同程度。
計測中GCはbefore 3回/8ms、after 4回/5ms。
GPU、font rasterization、image decode、音声、実機FPSを測った結果ではない。

新controllerは既存の可視snapshotとresident foregroundだけを読み、hit評価で
新しいsnapshot/map/texture/fontを生成しない。sprite lifetime情報は既存Rowに保持し、
depthはbrowser順変更時だけ更新する。全libraryの毎frame処理は追加しない。

## 残る差異と次の作業

| 優先度 | 残件 | 次の検証・実装単位 |
| --- | --- | --- |
| P1 | global dispatcherの全frame順序・native pixel rounding | native `06003267`はrow登録途中にもlogical hoverを更新する。Javaは共有snapshotの最終候補を評価する。同じ許可skinと固定pointer列で、候補入替・callback・focus境界をpixel/frame単位に比較する |
| P1 | buffer外を含む復帰spriteの全生成順・他animationの再生成時計 | 今回の復帰fadeは既存buffer内へ適用。foreground/inputの世代を照合したが、colour/starの全寿命までnative一致を主張しない。many difficulty・sort/filter・rapid collapse/expandを固定時刻で比較する |
| P1 | HD eligibility、Back/下部buttonのalpha-based hit・provider/INI/音alias | 共通resolverの常時HD優先は維持。native option/display/GL条件と実pixel hitを確定してから変更する |
| P1 | font/metadata typography、Greylooksと公式defaultの見た目 | 同じ再配布可能skin/fontでfull-frame比較。明示色を自動改変しない |
| P1/P2 | Mods/他mode/Options/未対応tabs、rating/status source、score player/mods/replay/scrollbar drag、preview/focus/復帰 | [全体audit残件表](songselect-full-audit-20261002.md#残件と次の作業)の独立したbackend・UI単位で継続。ローカル実データのない情報を捏造しない |

安全用chrome/scissor予約領域、極端なportrait、Java Cookie・拍/粒子の差も維持する。
native同期captureや画素完全一致を達成したとの主張はしない。

## 主な変更ファイル・commit

- `SongSelectRowInput`, `SongSelectScreen`: 候補/opacity/logical hover、foreground世代。
- `SongSelectCarousel`, `SongSelectMetrics`, `SongSelectForegroundAnimation`: sprite寿命、depth、復帰fade。
- `SongSelectRow`: 旧selected優先hit削除。関連unit testsと`SongSelectVisualHarness`を更新。
- `e932b91` — `fix(song-select): use sprite priority and opacity for row input`
- `1de33e6` — `test(song-select): exercise native canvas activation across display profiles`
- `427df39` — `perf(song-select): retain row depth while browser order is unchanged`
- `6ec8b47` — `test(song-select): verify giant canvas clicks against sprite priority`
- `4d6c8d3` — `test(song-select): distinguish group activation in oversized canvas checks`
