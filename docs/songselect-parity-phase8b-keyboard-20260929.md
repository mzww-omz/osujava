# Song Select parity: phase 8b — キーボード保持入力

2026-09-29。[phase 8a](songselect-parity-phase8a-20260929.md)の続き。
今回閉じる実装単位は、行移動のkey repeat、初回入力との区別、修飾キーと消費済み文字の扱い。
**8b全体は継続中**で、mouse dispatcher・sprite hit・context操作は残る。
ユーザーの並列作業指示を受け、途中から専用worktree
`/home/coder/worktrees/osujava-songselect-phase8b`、ブランチ`codex/songselect-phase8b`へ移した。
分岐元は`d1bf8cbbb5d42e13697c60f20c6b69d4edc97c7d`。mainへの統合は行っていない。

調査対象は同じ`b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
IL本体と参照元を`dnfile` / `dncil`で確認した。公式asset抽出・保護回避・production service接続は行っていない。

## 確認した契約

| 箇所 | 確認内容 |
| --- | --- |
| `06002af9` `01eb–0254` | 現在のkeyboard stateから押下key列を作り、`06002afb`へ渡す。この処理は同メソッド内のmouse押下・解放処理より前にある。 |
| `060026f4/26f5` | keyboard stateのwordを順に走査し、各wordのbit 0〜31を昇順に列挙する。通常keyboardのkey列はnative enum値順になる。 |
| `06002afb` `00c7–0115` | 前回のkey列から、現在保持していないkeyを除き、key-upを通知する。 |
| 同 `02ba–0331` | 新しいkeyを見つけたとき、共有repeat counter `04001b45`を−120へ戻す。これは矢印だけのtimerではなく、修飾キーなどの新しい押下でもresetされる。 |
| 同 `0425–0463` | 解放分を除いた**前回のkey列**のうち、160〜165（左右Shift/Ctrl/Alt）以外を数える。これが0ならcounterを−120へ戻す。初めて通常keyを押したframeは、後段のdelta加算へ入らない。 |
| 同 `0468–058b` | counter>=100なら100を引き、現在のkey列へ初回flag=falseの通知を行う。1更新につき1回の判定であり、経過時間分をwhileで一括発火しない。 |
| 同 `05a8–05cf` | 判定の**後**にframe deltaをcounterへ加え、現在のkey列を次回用に保存する。 |
| `060043e1/43e3` | 初回は`43e3(key,true)`。repeatの`43e3(key,false)`と初回専用subscriberへの通知を区別する。 |
| `06003247` | Up/Down/PageUp/PageDownは初回flagにかかわらず行移動。Left/Right/Enterは初回のみ動作する。Shift+Left/RightはGroup移動、ShiftかつCtrlなしのEnterは親Group操作。 |
| `06002afb` `0117–02b9` | textboxがfocusを持つ場合は別のkey選別・消費経路がある。Backspaceや文字入力のrepeatを、行移動のcounterだけから導くことはできない。 |

ローカル`gdx-backend-lwjgl3-1.14.2.jar`の`DefaultLwjgl3Input.keyCallback`も前回のbytecode調査で確認済み。
GLFW_REPEATは`keyDown`を再発行せず、`lastCharacter`があれば`keyTyped`を発行する。
そのため、以前のJavaの`keyDown`だけでは矢印を保持しても行移動が続かなかった。

## 実装と設計判断

- `SongSelectKeyRepeat`に現在/前回の保持keyと共有counterを置いた。単位はmilliseconds。
  新しい押下で−120へresetし、前回から保持している通常keyがある場合だけ判定・delta加算を行う。
  通知はPageUp→PageDown→Up→Downのnative key順。長いframeの後でも1更新で各保持keyにつき最大1回となる。
- `SongSelectInput`は初回callbackとsynthetic repeatを区別して既存のcommand処理へ渡す。
  二重のkeyDownは新たな押下として扱わず、左右キー・Enter・ショートカットが保持中に再実行されることを防ぐ。
  ショートカット専用の初回動作や、OSが通知する通常文字のrepeatは行移動のtimerへ混ぜない。
- 初回callbackは既存の即時処理を維持する。repeatはScreenのupdate先頭で、preview選択・入力用geometry・scroll積分・描画の前に処理する。
  これにより、repeatで変わったfocus/selectionを同じframeの追従とgeometryへ反映する。
- overlay・検索編集中の行移動抑制を維持する。import/outgoingと、先行するglobal Volume HUDにもrepeatを通さない。
  抑制中も発火周期は消費し、抑制解除時にcommandをqueueからまとめて再生しない。
- backendの保持状態と照合し、key-upを取り逃がしても解放済みkeyを残さない。
  pause/hide/disposeではkey状態とpointer gestureをともに破棄する。
- ImportのI、playのSpace、overlayを閉じる2は、消費済みの文字をkey-upまで抑制する。
  以前は最初の`keyTyped`だけを抑制していたため、OSによる2回目以後の文字repeatで検索が始まる余地があった。
  通常の検索中に押したIは文字として入力でき、解放・画面切替後に抑制を残さない。

counterは小さいUI入力状態として独立させた。共有GameClockやGameplayのInput APIを変更する必要はなく、
Import / Gameplay / Renderer / Ruleset / GameClockの責務と、Skinの独立作業単位を維持する。

## Javaへの適応と未完了部分

- nativeはglobalなkeyboard snapshotを処理するが、Javaは画面へ届いたcallbackで保持keyを登録し、pollingで解放を照合する。
  他画面から押し続けたkeyの引継ぎ、同一frameに複数keyが新たに押された際の初回callbackの順序、
  mouse/wheel callbackとの完全な順序一致までは今回閉じていない。
- nativeの参照clockとJavaのUI deltaの実時間比較は未実施。既存のrender delta上限2秒も維持している。
  reset値と判定順を「押下から常に220msで発火」と言い換えない。最初のframeとframe境界の影響がある。
- Backspaceなどの編集repeat、文字入力/IME/textboxの選別は既存の検索入力のまま。
  今回のsynthetic repeat対象はUp/Down/PageUp/PageDownであり、検索入力全体の互換完了ではない。
- 左右同時button、右行context、Set展開後の240ms play guard、最初のstationary drag sampleは引き続き8bに残る。

**sprite hit調査の補足:** 前回読んだ`06003256`の行走査だけで優先順位を決めてはいけない。
`06002c1c`はsprite群を検査し、managerの候補`04001bed`をhoverへ設定する。
その検査先`06002c1f`は、入力有効状態、depth `0400285b`、`060040ce`によるhit、managerのclip条件を確認して候補を更新し、
以前の候補のhoverを解除する。行の`06000fc4`が返すflagはこの処理結果を含む。
したがって8a報告の「後のeligible行が候補になる」は行走査部分の説明であり、
画面全体の「常に最後の行がhitする」という仕様には拡張できない。実際の行spriteのdepth・manager・hit geometryとの照合を続ける。

調査用ローカル出力は`/tmp/osujava-phase8b-{dispatch,order-hit,key-order}.il`、
`/tmp/osujava-phase8b-{xrefs,hover-xrefs}.txt`。前回の`/tmp/osujava-phase8-repeat.il`も使用した。

## 検証

関連Song Selectテストと、専用worktreeでの`./gradlew build --offline --console=plain`成功。
**103 suites / 861 tests、failure・error・skipは0**。phase 8aから24件追加。

- −120/100境界、初回frameの非加算、判定→delta加算順、長いframeと一回ずつの追い付き。
- 新しいkey/修飾キーによる共有reset、重複down、修飾キーのみの保持、複数保持keyの通知順、一部/全部の解放。
- 30/60/144Hzの1秒間の発火frame列を固定期待値と比較。非有限・負のdelta、解放通知欠落、取消。
- 左右・Enterの単発動作、Shift/Ctrlによるrouting、overlay/search/import/outgoing/Volume HUDの優先。
- 消費済みショートカットの文字repeat抑制と、通常の文字入力への復帰。
- production Screenでのfocus追従への同frame反映、保持Enterによる誤play防止、pause/hide時の取消。

以前のテスト/harnessには、独立したキー押下をkeyDownだけで表す箇所があった。
これらをpress→releaseへ修正し、文字を伴うものはpress→typed→releaseのbackend順へ修正した。
保持入力用fixtureはkeyDownを1回だけ送り、保持状態とframe deltaだけを進めて検証する。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=keyboard-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8b-keyboard-20260929
```

**12 scenes / 72 PNG / 1,416 scripted frames**成功。
1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768と、30/60/144Hzを組み合わせた。
各frameの移動先keyを固定した発火frame列と照合し、解放後の停止、focus確定後のEnter保持による非playを確認した。
1280×720の60Hz初回repeatと、1024×768の144Hz確定後captureを目視確認した。

既存navigation-contractsも**24 scenes / 24 PNG / navigation・disposal checks成功**。
最初の実行は途中終了し完了記録がないため、`/tmp/osujava-phase8b-navigation-rerun-20260929`へ再実行して確認した。
新規・既存を合わせて36 scenes / 96 PNG。既存navigationのscripted frame計数は0で、新規fixtureの1,416 frameと区別する。

stable Song Selectの正常な参照実行は未確保。同条件のnative pixel/audio比較は未実施で、
今回のJava側の状態・描画検証を1:1比較の合格とは扱わない。
