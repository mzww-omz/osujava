# Song Select parity: phase 4 — 全行座標と行のX/Y補間

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)、
[Skin独立調査](skin-stable-independent-audit-20260929.md)、
[phase 3](songselect-parity-phase3-20260929.md)に続く実装。
対象は `b20230727.9/osu!.exe`、SHA-256は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。

今回完了したのはCarouselの**全行の目標座標、永続した行位置、通常時のX/Y補間**。
viewport全体の慣性、画面外のsprite生成・破棄、入力時系列まで完成した段階ではない。
公式asset抽出・保護回避・production serviceへの接続は行っていない。

## ILから確認した契約

`dnfile` / `dncil`でメソッドと参照先を静的に読み取った。数値は480高の参照座標。
実機での同条件描画・音声比較による証明とは区別する。

| 箇所 | 契約 |
| --- | --- |
| `0600325a` `0045–00d3` | 全行へ座標を割り当てる。可視行数をn、累積gapをgとして `200 + g + (n-1)*48`。非表示行はnを増やさない。最初の可視行より前の非表示行は152 |
| 同 `005b–00aa` | Groupの前が非Groupならgap＋10。譜面行の前後どちらかが開状態ならgap＋10。開いた兄弟の間にも10を加える。連続Group間は加えない |
| 同 `00fb–0156` | content travelは `g + (n-1)*48`。再構築では旧scroll fraction×旧travelを新travelで割り直して0–1へclampし、絶対移動量を保つ |
| `0600322c` | X基準は参照座標上の画面幅−340 |
| `0600325c/325d` | 曲線は `min(200, abs(y/480-.5)*75)`。GroupはX−50、開いた譜面はX−50、hover対象はさらにX−45 |
| `0600325e` | 曲線の入力は現在の行Y＋符号付きscroll＋残り移動量の予測。今回最後の予測項は未接続 |
| `0600325f` | hover行より前の全行indexにはY−10、後にはY＋10。hover行自身は移動しない |
| `06003266` `0039–00b0` | 通常更新は `next = target - (target-current)*decay^time60`。Xのdecayは0.95、Yは0.875。強制snapと画面外の分岐は別 |
| `06002316` `01e1–01f0` | `040016ac`はelapsed ms / 16.666666666666668。上記time60を秒×60へ換算できる |
| `06000fd3` `0023–0048` | 以前の状態が非表示だった非代表行は、代表行の位置を継承する |

gapの「開状態」は`06000fcd`の状態>=3。Groupは譜面を持たない別subtypeとして扱う。
「同じSetの境界だけにgap」「展開量は画面幅の5.2%」「選択行だけさらに3移動」はこの契約と異なる。

## 実装と設計判断

- ScreenからCarouselへ、可視projectionではなくBrowserの全行と可視状態を渡す。
  CarouselはkeyによるRow identityと現在のX/Yを保持し、可視行だけを描画snapshotへ投影する。
  削除されたlibrary行の状態は破棄する。
- 48のpitch、状態に応じた10のgap、先頭200、選択追従先220を接続した。
  先頭の選択行はscroll=0の制約により200にとどまる。
  同じ選択のままcontentが変化したときは、旧実装の選択行位置補償をやめ、現在の絶対scrollをclampする。
- Y目標と実際のYを分け、X/Yをそれぞれ0.95／0.875の指数補間で更新する。
  hiddenの子が再表示されるときは代表行の位置へ戻してから補間する。
  Browserの現在の代表はSet内の先頭譜面なので、Carouselも同じfamilyの先頭行を対応させる。
- Xの50／45とYの10は画面高から参照座標を変換する。
  旧実装の選択専用の位置強調、速度によるhover変位減衰、独自の微小曲線変形、画面幅割合のX制限を除去した。
  新規子の独自Xずらし・reveal fadeも除去した。stable側のsprite可視化・色transitionの全体は未対応。
- 狭い画面でも行右端が画面内で途切れないよう、最大の展開・hover量から行幅を確保する。
  上部Skinの予約高だけが変わる場合は画面上のYを保ち、参照scaleが変わる場合は座標・scroll・速度を変換する。
  これはJavaのUiLayout/Skinへの適応であり、stableのwindow resize分岐を再現したとの主張ではない。
- 描画、target geometry、hit geometryのindexを全行に統一した。
  既存のfocusとplayable selectionの分離、通常Input API、ローカル動作は維持する。

Browserは譜面状態、Carouselは表示座標と運動、Rendererはsnapshotの描画を担当する。
Import / Gameplay / Ruleset / GameClockやSkin providerの責務は変更していない。

## 検証

`./gradlew build --offline --console=plain` 成功。
JUnitは **98 suites / 780 tests、failure・error・skipは0**（6テスト追加）。

追加の数値テストは、全行座標列、hiddenのpitch非消費、開いた兄弟間のgap、先頭境界、
Row identity、非代表行の再表示、削除、X/Yの1フレーム補間、30/60/120Hzでの定常targetへの収束、
固定50の展開量、chrome高変更と参照scale変更を確認する。
既存の展開・collapse・wheel・drag・click・focusテストも実行した。
旧配置・springを期待していたassertは上記ILの契約に更新した。

実描画harnessはXvfb上で次を実行し、両方成功した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=navigation-contracts \
  -PsongSelectOutput=/tmp/osujava-phase4-navigation-20260929
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=row-motion-contracts \
  -PsongSelectOutput=/tmp/osujava-phase4-row-motion-20260929
```

- navigation: **24 scenes / 24 PNG captures**。Set/Group focus、確定、Group開閉、Page移動。
- row motion: **32 scenes / 176 PNG captures / 1,440 scripted transition frames**。
  hover、高速scroll、反転、16難易度の先頭／末尾選択、collapse、library先頭／末尾。
- 各ケースは1280×720、1920×1080、1280×720の2倍framebuffer、1024×768で実行。
  描画snapshotと運動座標の一致、行の右端、選択／navigation／disposalをassertする。
- 1024×768のfocus画面、16難易度展開中の10フレーム目と収束後を目視確認した。
- ログは出力先と同名の `/tmp/osujava-phase4-*.log`。PNG等の一時生成物はGitへ含めない。

最終のAPI整理後にも全体buildを実行し、同じ780件が成功。`git diff --check`も成功。

## 次段階へ残す差分

1. **viewportの慣性と追従**。Javaの既存spring、wheel impulse、drag速度は今回維持した。
   `06003254`は `vNew=vOld*decay^deltaMs`、移動量は `(vNew-vOld)/ln(decay)`。
   `0600325b`の残り予測は `-v/ln(decay)`。
   `0600324f`は目標scrollとの差から `v=-distance*ln(decay)` を作る。
   `06003253/3250/3251`のfocus・pointer追従条件、wheelの`06003246`、drag releaseと合わせて置換する必要がある。
   したがって今回の曲線targetは予測項を省いた現在Y基準であり、スクロール中の軌跡全体は未一致。
2. **Group開閉時の初期位置**。`06003273`はGroupの現在位置を子へ配り、可視代表ごとに48進める。
   今回は永続位置と非代表行の復帰を接続した範囲。Group固有の一括再配置は次段階。
3. **画面外の運動・可視化**。`06003266`のsnap分岐、`06003263`の再進入位置と前後行の参照、
   spriteの生成・破棄をJava側へ対応させる必要がある。今回は可視候補行を更新し、描画viewportでclipする。
4. **入力・色・音**。hoverを75ms保持するJava側方針、hover/selectedの色・alphaと時間、
   入力dispatcher/native hitbox、発音時刻は未一致。focusの50ms契約は前段階の実装を維持。
5. phase 3記載の難易度評価に近い選択、検索・sort/groupの残り、metadata、Skin独立調査の未対応契約。

stable実機との同条件pixel/audio比較は未実施。Javaのbuildとharnessの成功を、Song Select全体の1:1一致とは扱わない。
