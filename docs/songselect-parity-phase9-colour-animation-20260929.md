# Song Select parity: phase 9 — 背景色のanimation

2026-09-29。phase 9の背景色を、hover/focus量への追従から行ごとのRGBA transformへ置き換えた。
状態変更300ms、focus変更50ms、hover flash 1000msと、その中断・競合を対象とする。
8bの追加実装は行っていない。foreground・星のanimationは次の対象として残す。

専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始時HEADは`30fdffb10c12dec06ee3490734d0671660db6120`。
元worktree/mainへの変更・統合は行っていない。

## 根拠

前回の[基底色・文字alpha調査](songselect-parity-phase9-colours-20260929.md)に加え、同じ
`b20230727.9/osu!.exe`（SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`）のILを確認した。
公式asset抽出、保護回避、production serviceへの接続は行っていない。

| 根拠 | 契約と対応 |
| --- | --- |
| `06000fd2/fda/fb4`、`060025e6` | resident行の状態・played flag・Group色変更は300ms。生成時の基底色は即時に設定する。 |
| `06003259/324c` → `06000fd8/fd9` | focus取得／解除は基底色の40%増光／基底色へ50ms。背景spriteに渡すstored targetを更新する。 |
| `06003256/3267` → `06000fd7` | 新しいhover候補でstored targetから明滅色を計算し、1000ms flashを要求する。同じ候補の継続では再要求しない。 |
| `060040c1`、`060040d8` | 既存の色transformを置き換え、前回評価済みの色から新targetへ遷移する。hover/focus scalarの混合とは異なる。 |
| `060040c2`、`060040d9/40da` | flashは明滅色から現在色へ戻る。通常の色transformがあれば拒否する。既存flashがあれば、その終点を次の戻り先として引き継ぐ。離脱による逆向きtransformは追加しない。 |
| `060040c1/40c2` → `060042ef` | 開始時刻は`now - int(frameDeltaMs)`、終了時刻は`now + duration`。開始frameでも進捗がある。type 16、hoverは追加識別値51。 |
| `060040b0` → `06002b50/2b54` | easing 0は線形。RGBA各byteを補間・clamp後に切り捨てる。終了時刻では終点を評価し、終了時刻を過ぎた評価で期限切れtransformを除去する。 |

追加の調査出力は `/tmp/osujava-phase9-animation-{evaluation.il,xrefs.txt}`、
`/tmp/osujava-phase9-sprite-evaluation.il`、`/tmp/osujava-phase9-colour-interpolation.il`、
`/tmp/osujava-phase9-easing.il`。元の状態・focus・hover呼び出し側の証拠は前回報告を参照。

## 実装判断

`SongSelectRowColourAnimation`が基底色、stored target、表示済み色、開始／終了色・時刻を保持する。
状態変更、focus変更、hover候補の変化を処理してから当該frameの色を評価する。
通常遷移中に拒否されたhoverを後から再生するqueueは作らない。
状態変化でstored targetが基底色へ戻った際、変わっていないfocusを毎frame再適用しない。

Screenはpersistent carousel rowのidentity単位でanimationを保持する。
描画viewport外でもresidentである行の色は進め、非resident／非表示／削除済み行のanimationを破棄する。
resizeやdelta 0のgeometry更新では時間を進めず、入力更新後の1か所だけで進める。
時刻はScreenの累積UI deltaを整数msへ変換して渡す。GameplayのGameClockには変更を加えない。

Rendererへはimmutableなpacked RGBAを渡す。Renderer自身は時計やBrowser、score storeを参照せず、
このRGBAへ既存のreveal opacityを掛けて描く。既存のhover量は移動やthumbnail側に残すが、
背景色には使わない。paletteの固定目標表示は診断harness用として残した。
Skinの解決・parser/providerには変更を加えていない。

## 検証

関連テストと `./gradlew build --offline --console=plain` 成功。
**107 suites / 954 tests、failure・error・skipは0**。前回から17件追加。
ログは `/tmp/osujava-phase9-colour-animation-{tests,integration,build}.log`。

- 即時生成、300ms遷移と途中のRGBA、16ms frame補正、alphaを含むbyte切り捨て。
- 1000ms flash、hover保持中の終了、離脱、再hover時の戻り先継承、通常遷移による拒否。
- focusによるflash中断、50msの取得／解除、stored targetからの次のflash。
- 同一stateのGroup palette変更、終了時刻の境界、30/60/144Hzの終点、不均一frame／長いframe。
- production Screenで選択変更→途中色→resize→終了色、hover保持、検索で隠れた行のanimation破棄。

描画harness `row-colour-animation` は **8 scenes / 64 PNG / 64 scripted frames** 成功。
1024×768／1280×720／1920×1080／1280×720の2倍framebufferで、自作の白い行画像と
画像欠損時のprocedural描画を検証した。5種類の遷移を0/25/50/150/300/500/1000/1001msで
描画し、固定RGBAから黒背景とのalpha合成を計算した **320点のRGB pixel** と比較した（8-bit許容差2）。
これは指定時刻での描画検証で、64frameの連続実時間再生やnative画面との比較を意味しない。
1280×720の150ms画像を目視でも確認した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=row-colour-animation -PsongSelectOutput=/tmp/osujava-phase9-colour-animation-20260929
```

前回の `row-colour-contracts` も **8 scenes / 24 PNG / 168点のRGB pixel** 成功。
通常Screenの `case / phase5a-state-sibling` は1024×768で **1 scene / 1 PNG** 成功。
合計 **17 scenes / 89 PNG**。出力は `/tmp/osujava-phase9-colour-animation{-palette,-screen}-20260929`、
ログは `/tmp/osujava-phase9-colour-animation-{harness,palette,screen}.log`。

## 次の対象・比較上の制約

次はphase 9のforegroundの生成／破棄／継承、星の500ms・index遅延、thumbnail/grade/mode/textの
opacity・depth・clipを進める。背景の300/50/1000ms transformを次回再調査する必要はない。

hover候補とfocusイベントは既存の入力経路を使う。8bに繰り越したhit判定、global window focus境界、
同一frameの入力順まで一致したという主張はしない。playedのデータ同値性はphase 10、既存の
同梱画像用`fallbackWash`はSkin側の比較で追跡する。
正常なstable Song Select参照実行は未確保で、nativeとの同条件pixel/audio比較も未実施。
この変更は確認済みの色transform契約への対応であり、画面全体の1:1合格ではない。
