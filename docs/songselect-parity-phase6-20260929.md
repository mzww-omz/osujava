# Song Select parity: phase 6 — drag速度推定・release慣性

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)、
[Skin独立調査](skin-stable-independent-audit-20260929.md)、
[phase 5](songselect-parity-phase5-20260929.md)の継続実装。
[残phase台帳](songselect-parity-roadmap-20260929.md)にphase 7〜14と受入条件を整理した。

対象は `b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
ILの読取りのみを行い、公式asset抽出・保護回避・production service接続は行っていない。

## 静的に確認した契約

単位は480高の参照座標とms。`dnfile` / `dncil`で本体とfield参照元を確認した。

| 箇所 | 契約 |
| --- | --- |
| `06003253` `0035–0076` | drag中、正のelapsedを蓄積。`measured=(前回Y−現在Y)*displayInverseScale/蓄積時間`、直接移動量は`measured*蓄積時間`。 |
| 同 `007b–00c6` | measuredと旧速度が逆符号、またはmeasuredの絶対値が大きい場合はbase=0.9、それ以外は0.95。重みは`base^蓄積時間`。 |
| 同 `00c7–00f9` | measuredが非0のときだけ時間をresetし、`v=旧v*重み+(1−重み)*measured`。静止サンプルでは速度を変更しない。 |
| 同 `00fe–0148` | 非0速度のdecayは`max(0.5,0.9959999918937683−0.002/abs(v))`、0なら0.5。 |
| 同 `019c–01d3`、`06002af9` `07a1–07a6` | `04001b3a`は前回button状態。左押下開始時、許可条件またはhover行があればdrag flagを立て、直接移動量と蓄積時間を0にする。旧速度は消さない。 |
| `06003254` | drag中は自由減衰・積分を行わず、入力からの直接移動量だけをviewportへ加える。非dragはphase 5の指数積分。 |
| `06003244` `001c–0074` | dragの解放時、`v *= 0.95^max(0,蓄積時間−66)`、蓄積時間・drag flagをclear。decayはこの処理では変更しない。 |
| `06003255` | 入力更新→viewport積分→行描画更新。 |

静止時間は「クリック押下からの全時間」ではなく、最後の非0の速度サンプル以降の蓄積時間。
66ms以内の静止では解放時の速度を減らさず、それを超えた分だけ減衰する。

## 実装と設計判断

- `SongSelectScroll`にdrag開始・移動サンプル・解放・中断を追加。
  InputやRendererへ依存しない計算状態のまま、直接位置と速度推定を分ける。
- `SongSelectCarousel`でlogical UIの上向き正の移動量を参照単位へ変換する。
  nativeの画面下向きYに対する「前回−現在」と同じscroll符号になる。
- 行を押すとdrag計算状態へ入り、押下開始前の慣性速度を保持する。
  旧6-unitの「スクロールを始める閾値」を外し、微小な移動も直接反映する。
  押下中は選択追従と自由積分を止め、解放後に推定速度を使う。
- 画面はgeometryを時間0で用意して入力を処理し、その後だけ時間を進める。
  押下前の旧速度や同フレームの新しいdrag速度でviewportが二重移動することを防ぐ。
  描画と次のhitに使うsnapshotも同じ位置を公開する。
- overlay/Import/画面遷移、hide/disposeによる中断ではdragと速度を破棄する。
  Input controllerがdrag ownerを保持し、通常の解放と中断を区別する。
- `dragBy(distance)`は即時位置指定を必要とする既存診断テストだけに残す。
  production pointerは時間付きのdrag経路を使う。wheel/選択/focusの係数は変更しない。
- Import / Gameplay / Renderer / Ruleset / GameClockの責務は変更しない。大規模リファクタリングは不要だった。

### Javaの適応と未完了の入力契約

- render/polling単位でサンプルを採る。押下と同時の位置不変サンプルには、押下前のframe deltaを加えない。
- releaseフレームで新しいYを受けた場合は、既存Javaの契約どおり最後の移動を適用する。
  正のdeltaなら速度推定にも使う。位置が不変のreleaseではそのframe deltaを静止時間へ加えない。
  native dispatcherのcallback時刻まで同一という主張ではなく、phase 8でevent時刻・入力順を照合する。
- delta=0でgeometryやテスト上のpointer位置を変更できるが、速度は捏造しない。
  不正deltaを0扱い、最大2秒とする既存防御を維持する。
- **クリック取消にはまだ`SongSelectPointer`の6 logical unitsを使用する。**
  native `06003253` `0239–026d`の`DistanceSquared > 6400`による取消flagと、
  `06003244`の押下sprite矩形によるrelease判定は別契約である。
  nativeの座標変換・全dispatcher条件を閉じるphase 8で置き換える。今回のドラッグ開始とは混同しない。
- nativeの強制drag許可flag、左側へのdrag-out callback、右button位置指定、全mode条件は未対応。

## 検証

`./gradlew build --offline --console=plain`成功。
**100 suites / 805 tests、failure・error・skipは0**（10件追加、既存drag期待値を更新）。

- 独立数値による加速・減速・反転、速度依存の係数と0.5下限。
- 静止中の時間蓄積、次の移動への影響、65/66/100ms後の解放、二重解放。
- 押下中の自由積分停止、押下前の速度保持、解放後の自由積分。
- 上下端のclamp、中断後の残留速度・凍結防止、delta=0と不正距離。
- 一定速度の30/60/120Hzサンプルに相当する100ms内3/6/12分割。
- production Screenの押下→30px移動→100ms静止→解放の速度と位置、描画snapshotの一致、誤選択・誤play防止。
- 既存のrelease時最終移動、navigation/search/wheel/hitの回帰テスト。

Xvfb描画検証:

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=drag-contracts \
  -PsongSelectOutput=/tmp/osujava-phase6-drag-20260929
```

**16 scenes / 96 PNG captures / 1,028 scripted transition frames**成功。
flick、100ms静止後の解放、反転、Mods overlayによる中断を、
1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768で実行した。
通常Inputの押下pollingとキーcallbackを使い、直接移動・解放速度・geometry・選択維持・disposalをassertする。
1280×720のflick解放10フレーム目と反転heldのPNGを目視確認した。
音声deviceのないXvfb環境でのALSA警告はあるが、これは音声一致を検証するharnessではない。

入力更新順の変更について既存の描画harnessも再実行し、両方成功した。

- `navigation-contracts`: **24 scenes / 24 PNG captures**。
  出力 `/tmp/osujava-phase6-navigation-20260929`。
- `row-motion-contracts`: **32 scenes / 176 PNG captures / 1,440 scripted transition frames**。
  出力 `/tmp/osujava-phase6-row-motion-20260929`。

生成物とログは`/tmp/osujava-phase6-*`。Gitには含めない。
stable実機との同条件pixel/audio比較は未実施。数値契約とJava描画検証の成功を、画面全体の1:1合格とは扱わない。
