# Song Select parity: phase 9 — 行の色・文字alpha

2026-09-29。ユーザーの進行方針に従い、8bの未完了項目を後段へ繰り越してphase 9へ進んだ。
今回は背景spriteの基底色・hover/focusの目標色、文字alpha、Groupの選択包含色を対応した。
**phase 9全体のanimation完了ではない。** 8bの追加実装はこの変更に含まない。

専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`を継続使用。
開始時HEADは`01981a5d04b3393b2a1e5e670ef259055e80feb2`。元worktree/mainへの変更・統合は行っていない。

## 根拠と変更

`b20230727.9/osu!.exe`（SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`）を
`dnfile` / `dncil`で調べた。公式asset抽出、保護回避、production service接続は行っていない。

| 根拠 | 確認・対応内容 |
| --- | --- |
| `06000fb1`、`06000fda` | state 4はRGBA(255,255,255,220)、state 3は(0,150,236,240)。その他はrow flagにより(233,104,0,240)/(235,73,153,240)。Java独自の近似色・alphaを置き換えた。 |
| `06000fb0/fb3` | row flag `04000866`はbeatmapの`040025b8`を反転して初期化し、`fb3`が返す。Javaでは既存のローカルscore由来のplayed projectionを使い続ける。データ上のplayed履歴を完全再現したとは扱わない。 |
| `060025e6` → `060025e4/25ec` | 開Groupは(163,240,44,255)。閉Groupは通常(35,50,143,255)、除外されていない子に現在の選択譜面がある場合(35,90,193,255)。Screenが包含flagをpresentationへ渡す。 |
| `06003256` `008a–00a0`、`06003267` `00fc–0112` → `06001966` | hover目標のRGBは`min(255, byte * 1.075 + 38.25)`の整数化、alphaは維持。旧実装の状態別hover色とalpha増加を除去した。Groupにも同じ変換を適用する。 |
| `06003259/324c` → `06001965` | focusは基底RGBを1.4倍・byte飽和しalphaを保持する。既存実装を色policyへ移し、hover目標はfocus側の色を先に反映して計算する。 |
| `06000fc7` → `0600196c` | inactive文字色のRGBを維持し、alphaを50へ置き換えた色を用意する。倍率50%という意味ではない。 |
| `06000fdf/fe2`、`06000fbf` | state 4の文字群はactive色。state 3のtitle `04000873`／byline `04000874`は上記alpha 50のinactive色、その他は通常inactive色。state 2以下も通常inactive色。title .24、byline .20/.80、detail .72というJava独自の減衰を除去した。 |

`SongSelectRowColours`へ小さい描画policyを分離した。RendererはBrowserやscore storeを参照せず、
既存のSkin active/inactive文字色とpresentationから描画色を得る。
Skinのparser/provider/asset探索、Import、Gameplay、Ruleset、GameClockの変更は不要だった。
色変換は受け取ったSkin色を変更しない。

調査出力は`/tmp/osujava-phase9-{palette,colour,colour-policy,colour-events,colour-transforms}.il`と
`/tmp/osujava-phase9-{colour,played,unplayed}-xrefs.txt`。

## 検証

関連テストと`./gradlew build --offline --console=plain`成功。
**106 suites / 937 tests、failure・error・skipは0**。前回から19件追加。
ログは`/tmp/osujava-phase9-colours-build.log`。

- 選択／同Setの別難易度／played／unplayedのRGBAと優先順位。
- 開Group、通常閉Group、選択を含む閉Groupの分岐。
- hoverのbyte計算、RGB飽和とalpha維持、focus→hover→revealの順。
- title/bylineのalpha 50、その他の文字色、選択行の不透明度、Skin色の非破壊。
- 実際のBrowser→Screen presentationでの選択包含flag。

Groupの統合テストは当初、Setだけ別artistにして難易度metadataは同じものを再利用していたため、
期待した2つのGroupを作れなかった。難易度も別artistにしたfixtureへ直し、開閉後の位置が落ち着いてから検証した。

描画harness `row-colour-contracts`は**8 scenes / 24 PNG**成功。
1280×720／1920×1080／1280×720の2倍framebuffer／1024×768それぞれで、
自作の白い行画像と画像欠損時のprocedural描画を検査した。
7状態×基底色／hover目標／focus目標の**168点のRGB pixel**を、固定RGBAと黒背景へのalpha合成から
計算した値と比較した。許容差は8-bitで2。画像・ブレンド後の色とspriteへ指定したRGBAを混同しない。
hover/focus量を固定した目標色検証であり、経過時間の検証ではない（scripted transition framesは0）。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=row-colour-contracts -PsongSelectOutput=/tmp/osujava-phase9-colours-20260929
```

1280×720の白い画像に描いた基底色captureを目視確認した。
通常Screenの`case / phase5a-state-sibling`も1024×768で**1 scene / 1 PNG**成功。
出力は`/tmp/osujava-phase9-colours-screen-20260929`。合計**9 scenes / 25 PNG**となった。

## 残件と次の対象

- 次はphase 9の色animation。nativeの状態変化は300ms、focusは50ms、hoverは
  `060040c2`による1000msのflashで、常時明るくする処理とは異なる。通常の色transition中はflashを
  追加しない条件や、再hoverで戻り先を継承する処理もある。今回の目標色修正では、既存のhover/focus量の
  時間曲線や中断・競合を一致させたとは扱わない。
- foregroundの生成／破棄／継承、星の500ms・index遅延、thumbnail/grade/modeのopacity・depth・clip。
- 同梱画像にだけ掛かる既存の明るさ補正（`fallbackWash`）は残っている。今回の白い画像fixtureには
  この補正を掛けていない。Skin既定画像との合成差はSkin独立対応と照合する。
- playedの永続化・native flagとの同値性はphase 10。既存score projectionを変えた主張はしない。
- 8bの初回key/wheel順、sprite hit、global focus境界等は後段で再確認する。phase 9の進行条件にはしない。
- stableの正常なSong Select参照実行は未確保。同条件のnative pixel/audio比較は未実施。
  上記pixel検証はJavaのRendererが確認済みの色契約を描くことの検証であり、全体の1:1合格ではない。
