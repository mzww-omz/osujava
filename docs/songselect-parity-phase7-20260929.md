# Song Select parity: phase 7 — Group初期配置・行の表示範囲と再進入

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)、
[Skin独立調査](skin-stable-independent-audit-20260929.md)、
[phase 6](songselect-parity-phase6-20260929.md)の継続。
今回の対象は、Groupを開いた直後の行位置と、画面外の行のsnap・再進入・描画対象としての寿命。
色や星など各foregroundの生成animationはphase 9、Skin固有の所有権・解決はphase 12に残す。

調査対象 `b20230727.9/osu!.exe` のSHA-256は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
`dnfile` / `dncil`でIL本体と参照元を読んだ。公式asset抽出・保護回避・production service接続は行っていない。

## 根拠となる契約

座標値は480高の参照単位。描画のclipとは別に、行の表示を維持する範囲が存在する。

| 箇所 | 確認内容 |
| --- | --- |
| `0600326c` `00f2–011d`, `01c3–01c7` | Group状態を切り替え、開いたときだけ`06003273`を呼ぶ。閉じる際には同じ再配置をしない。 |
| `06003273` | Groupの現在X/Yから始め、次のGroupまで全子行へ位置を配る。`06000fb6`が真、かつ`06000fc3`が真の子の後だけYを48進める。非表示の子にも位置を設定する。 |
| `06000fb6` | 自分と代表行が同じかを返す。 |
| `06000fc3` | 除外されておらず状態>0、親がある場合は親が開いていて除外されていない行が対象。JavaではBrowserの`visible()`に対応する。 |
| `06003261/3262` | 上側判定は`Y < -20`、下側は`Y > 640`。境界値自体は内部。 |
| `06003273` `0030–005a` | 配布位置＋scrollが上記範囲内なら`06000fd1(false)`を呼び、行の表示要素を準備する。 |
| `06003264/3265` | 全行indexに対する半開区間を保持する。reset時は`[0,count)`、更新前に終端をcount以下、始端を終端以下に制限する。 |
| `06003267` `000c–0041` | 再進入の検査へ渡すY成分は、符号付きscrollから残り移動量予測を引いた値。 |
| `06003263` | 区間の前後にある行を、hover込み目標Y＋上記成分で検査して取り込む。非表示行もindex走査に含む。 |
| 同 `00ab–0153`, `0201–02b1` | 区間内の先頭/末尾にある表示可能行を基準に、`現在Yの差`ではなく`論理Yの差`を加えて復帰位置を設定する。Xは基準行のstate indentを外して自分のindentを加え、`baseX + 自分のindent + 200`以下にする。 |
| 同 `0118–0145`, `0272–02a1` | 基準行がない場合は、通常X目標＋200と、符号付きscroll・予測に対応するYから始める。単純に自分の目標へsnapする分岐ではない。 |
| `06003266` `002d–00b0` | 一回限りのflagがあればX/Yを目標へsnap。それ以外はphase 4の0.95/0.875補間。 |
| 同 `00b5–0119` | 補間後の現在Y＋scrollが範囲内なら`06000fd1(true)`等を経て描画へ渡す。 |
| 同 `011a–0217` | 現在位置と目標が両方下側なら、その行以後をsnap・解放して終端を縮める。両方上側なら始端からその行まで同様に扱う。現在と目標が反対側なら同じ除外処理をしない。 |
| `06003267` `0079–0092`, `014d–014f` | 非表示行の表示要素を解放し、最後にsnap flagを解除する。 |
| `06000fd1/0fb9` | 行ごとの表示要素の生成・解放。`0fb9`はlabel texture等も解放するが、譜面行そのもののidentityや座標を削除する操作ではない。 |
| `0600142e` `0038–0090` | Song Selectの入力有効化・選択復元の経路でsnap flagを設定する。 |

`06003263`の対象判定に使うYと、`06003266`の除外判定に使うYは同じではない。
前者は残り移動量の予測を含み、後者は現在scrollを使う。この違いを一つのclip矩形へ置換しない。

## 実装とJavaへの適応

- `SongSelectCarousel`の既存の全行identity・座標モデルへ、activeなindex区間と各行の`resident`状態を追加した。
  Browserの可視projectionと区別し、非表示の子も区間走査・一括snapに含める。
- Groupが閉→開へ変わったとき、現在Group位置から子の初期X/Yを配る。
  family内の先頭を代表とする既存Browser契約に従い、表示可能な代表行だけ48進める。
  難易度が複数あっても、子ごとに48や展開gapを加える処理にはしない。
- 区間の端から行を取り込み、近隣行の変位を継承する。基準がなければ200単位のXオフセットを使う。
  現在位置と目標が同じ側の範囲外になったら、その側の行群の位置を確定して表示対象から外す。
  永続Rowは残すので、スクロール往復でidentityを作り直さない。
- ScreenはBrowserで可視、active区間内、residentの行だけをsnapshot候補にする。
  その後で従来の画面chromeとのclipを適用する。描画とhitは同じsnapshotを参照する。
- show時に一回だけsnapを要求する。通常フレームでは入力準備用のgeometry再発行で行更新を繰り返さず、
  入力・scroll積分の後に行の寿命と運動を更新する。
- Library削除・並べ替えでkey列が変わった場合はactive区間を再設定する。
  同じkey列の展開/縮小では区間を保ち、nativeの前後走査で追随する。
  既存のsort時Y位置再設定、resize時の座標換算、最大delta 2秒はJavaの適応として維持する。
- resize、選択操作直後の同期、delta=0の描画fixtureでは、時間を進めず表示範囲とsnapshotを更新できる。
  native dispatcherの全callback時刻との対応はphase 8に残る。
- Javaは行ごとにnative spriteや専用label textureを持たず、snapshotと共有Rendererを使う。
  今回は描画対象の寿命を対応させ、外れた行のpresentationは次の準備で破棄される。
  共有Skin texture・font atlasや永続metadataを行の除外ごとにdisposeしない。
  星・文字・thumbnailの生成/破棄時の色・animation resetを一致させたという主張ではない。

Import / Gameplay / Renderer / Ruleset / GameClockの責務を維持し、Skin APIは変更していない。
新しい汎用sprite管理frameworkやRenderer全体の置換は不要だった。

## 回帰検証

`./gradlew build --offline --console=plain`成功。
**101 suites / 818 tests、failure・error・skipは0**（13件追加）。

- Group位置継承、可視代表だけのpitch消費、除外行、閉じる場合、再開時の現在位置。
- −20/640の境界、現在と目標が異なる側にある場合、hiddenを含む区間の一括snap。
- 上下両方の再進入、近隣行の論理差・hover indent補正・200単位cap、基準行がない復帰。
- 予測を含む追加判定と現在scrollによる除外の区別。
- 一度だけの強制snap、scale/chrome原点変換、Library削除・空状態からの復帰。
- productionのShift+EnterによるGroup閉開で、時間を進める前から描画snapshotへ初期位置が反映されること。

旧`horizontalMotionTracksTheCurveAtTheConfirmedDecayRate`は、画面外の行でも全フレーム補間すると仮定していた。
通常補間の検証対象を画面内に置き、画面外のsnap/復帰を今回の独立fixtureへ分けた。

描画harnessのX範囲assertも、通常のbase/indent/curveに加えて、
基準行がない復帰の＋200を含む上限へ修正した。描画対象のidentity、resident/区間条件、座標一致、右端、pitchのassertは維持・追加した。
Groupは同じ座標に重なることがあるため、検証側もset/difficulty座標照合から永続keyによる照合へ変更した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=lifecycle-contracts \
  -PsongSelectOutput=/tmp/osujava-phase7-lifecycle-20260929
```

**8 scenes / 68 PNG captures / 1,200 scripted transition frames**成功。
24 SetのGroup切替と、可変frame timeでのwheel往復を、
1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768で実行した。
行の除外と再進入が実際に発生したこと、identity・playable selectionの維持、誤playがないことを確認した。
1280×720のGroup開直後/10フレーム目、wheel反転中のPNGを目視確認した。

既存harnessも、同じ4種類の表示条件で再実行して成功した。

| 検証 | scenes | PNG | scripted transition frames | 出力先 |
| --- | ---: | ---: | ---: | --- |
| navigation-contracts | 24 | 24 | 0 | `/tmp/osujava-phase7-navigation-20260929` |
| row-motion-contracts | 32 | 176 | 1,440 | `/tmp/osujava-phase7-row-motion-20260929` |
| drag-contracts | 16 | 96 | 1,028 | `/tmp/osujava-phase7-drag-20260929` |

合計 **80 scenes / 364 PNG / 3,668 scripted transition frames**。
Xvfbの音声device不在によるALSA警告はあるが、これらはaudio一致を検証するharnessではない。

生成物・ログ・IL読取り結果は`/tmp/osujava-phase7-*`。Gitに公式バイナリやassetは含めない。

## 残作業

次は[残phase台帳](songselect-parity-roadmap-20260929.md)のphase 8。
hit優先順位・クリック取消距離・右button・repeat・callback/polling順などを閉じる。
phase 9のstate別foreground、phase 12のSkin独立対応は別の実装単位で維持する。

stableを正常起動した同条件pixel/audio比較は未実施。
今回の静的契約とJava検証の成功は、Song Select全体の1:1一致の合格判定ではない。
