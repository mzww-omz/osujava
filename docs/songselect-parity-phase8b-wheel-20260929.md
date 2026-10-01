# Song Select parity: phase 8b — wheel集約と通知

2026-09-29。[再クリック対応](songselect-parity-phase8b-activation-20260929.md)の続き。
今回の範囲はwheelの集約・通知と、保持key repeat／mouse gestureに対する順序。
**初回key callbackを含むdispatcher全体の一致は未完了**で、phase 8bは継続中。

専用worktreeは`/home/coder/worktrees/osujava-songselect-phase8b`、branchは
`codex/songselect-phase8b`。開始時HEADは`e85c7095334e3fc22514900ecafc108d612282ad`。
元worktree/mainへの変更・統合は行っていない。

## 根拠

対象は`b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
`dnfile` / `dncil`でILと参照元を調べた。公式asset抽出、保護回避、production service接続は行っていない。

| 箇所 | 確認した契約 |
| --- | --- |
| `0600409a` `0001–000d` | wheel callbackは`get_Delta()`を`0400282f`へ加算する。ここではsubscriberへ通知しない。 |
| `0600409e` `0001–0023` | 合計0なら何もしない。正なら`0600408f`、負なら`06004090`を1回呼び、合計を0へ戻す。絶対値分の反復や120単位の剰余保持はない。 |
| `0600408f/4090` | wheelの時刻を更新し、event 1／0を`06002b0e`へ直接通知する。mouse down/up用queueへの追加ではない。 |
| `06002af9` `011d–016e`、`01d7` | pointer状態の更新後に`0600409e`を呼ぶ。OS callback時の位置をwheelイベントへ保存する処理はない。 |
| 同 `024f–0254` | wheel通知の後でkeyboard snapshotを`06002afb`へ渡す。初回keyと保持repeatの両方がこの経路にある。 |
| 同 `046d–0655`、`077f–079c` | その後にmouse down/upをqueueへ積み、queueの通知後にscene更新を呼ぶ。wheelは行releaseやdrag sampleより前。 |
| `0600321a`、`06003245/3246` | 行コンテナはevent 1／0を購読し、keyboard追従を解除して速度へ`±0.4*(1+min(abs(v)/2,5))`を1回加算する。減衰係数は0.994。wheel量を受け取る引数はない。 |

したがって、入力更新間の`+3, -3`は通知なし、`+3, -2`は正方向の通知1回となる。
別々の更新での`+1, -1`は相殺されず、加速→制動となる。
[phase 5](songselect-parity-phase5-20260929.md)で暫定採用した「batch分割・小数比例加速」は
libGDXへの適応方針だったため、この追加根拠で置き換えた。

出力は`/tmp/osujava-phase8b-wheel-accumulate.il`、`wheel-flush.il`、
`wheel-event-xrefs.txt`、`wheel-source-xrefs.txt`、`wheel-delta-xrefs.txt`
（後の4件も同じ`/tmp/osujava-phase8b-` prefix）。global更新順は既存の`dispatch.il`で照合した。

## 実装と設計判断

- `SongSelectWheelInput`がlibGDXのwheel callbackを蓄積する。
  Screenのupdate冒頭で合計の符号を1回通知し、その後に保持key repeatとmouse gestureを処理する。
  小数値も比例加速にはせず、相殺後に非0なら方向1回分として扱う。
- 音量用processorをこのmultiplexerの内側へ入れた。これによりSong Select内では、
  音量・score・carouselを選ぶ前にwheelを集約し、通知時のpointer位置で振り分ける。
  callback後にpointerが別領域へ移った場合や、領域をまたぐ逆方向入力も扱う。
  他画面への新しい入力queue導入は不要だった。
- `SongSelectScroll`のbatch分割、小数比例、10,000回の反復上限を除き、1通知につき1回の加速にした。
  速度に依存する加速度、逆方向による制動、積分式、減衰、端のclampは維持した。
- 集約にはdoubleを使い、非有限／0のlibGDX入力は拒否する。
  これはfloat入力と極端な値に対するJava側の防御であり、native整数overflowの再現ではない。
- pause/hide/disposeで未通知wheelを捨てる。resumeで音量processorを重複登録しない。
  画面・focus境界を越えるnative global stateの継承は未調査のため、今回の取消はJava側の寿命管理と区別する。
- Import、Gameplay、Renderer、Ruleset、GameClock、Skinの契約変更は不要だった。

## 検証

`./gradlew build --offline --console=plain`成功。
**105 suites / 918 tests、failure・error・skipは0**。前回から16件追加。
ログは`/tmp/osujava-phase8b-wheel-build.log`。

- 通知前に状態を変えないこと、batchの集約、逆方向相殺、更新を分けた加速／制動。
- 微小・小数・極端な有限値、不正値／横wheelだけの入力、取消後の再入力。
- production updateでのwheel→積分、wheel→保持repeat、wheel→静止dragのrelease減衰。
- 通知前のpointer移動によるcarousel／volume routing、領域をまたぐ入力相殺。
- score／carouselの独立性、Alt／F4の既存routing、空Library／行間／chrome領域。
- pause/hide/disposeでのpending取消、resume時の音量processorの重複防止。

既存wheelテストはcallback直後の処理を前提としていたため、入力更新で通知してから検証するfixtureへ変更した。
小数入力が1回分の加速になり、短いLibraryでは到達予測がすぐ端へclampされるため、
行間でwheelを受ける検証は位置予測ではなく速度の増加で判定する。
drag releaseテストの初回失敗は、選択済み行のreleaseが正しくplay要求へ進んだ際に
fixtureのRulesetがnullだったもの。通常のRulesetを与え、release条件は変更していない。

新規`wheel-contracts`描画harnessは**12 scenes / 60 PNG / 516 scripted frames**成功。
1280×720／1920×1080／1280×720の2倍framebuffer／1024×768と、30/60/144Hzを組み合わせた。
20 callbackを送る加速・反転、相殺、保持repeatとの順序を検証し、各frameの速度を式から照合した。
出力は`/tmp/osujava-phase8b-wheel-20260929`、ログは`/tmp/osujava-phase8b-wheel-harness.log`。
1280×720・60Hzの反転開始frameを目視確認した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=wheel-contracts -PsongSelectOutput=/tmp/osujava-phase8b-wheel-20260929
```

既存`row-motion-contracts`も**32 scenes / 176 PNG / 1,520 scripted frames**成功。
高速wheel・反転、hover、16難易度の展開／collapse、Library両端を確認した。
出力は`/tmp/osujava-phase8b-wheel-motion-20260929`、ログは`/tmp/osujava-phase8b-wheel-motion-harness.log`。

score用harnessの入力fixtureもwheel通知を明示してから領域別の結果を検査するよう修正し、
`case / phase4-selected`で**1 scene / 7 PNG / 62 scripted frames**成功。
出力は`/tmp/osujava-phase8b-wheel-scores-20260929`、ログは`/tmp/osujava-phase8b-wheel-scores-harness.log`。
今回の描画検証合計は**45 scenes / 243 PNG / 2,098 scripted frames**。

## 残る条件

- **初回keyDown／keyTypedはまだbackend callbackで即時処理する。** 同じ更新区間の初回矢印・F4・
  overlay開閉・検索文字とwheelの順序はnativeと揃っていない。wheel→保持repeat／mouseの範囲だけが今回の対応。
  初回key snapshotと複数keyの順序を次の実装単位で扱う。
- nativeはglobal入力更新ごとにwheelを通知する。JavaはSong Selectのupdateに対応づけた。
  nativeの1描画内の複数polling、OS配送・thread境界、focus喪失時の蓄積／破棄は未完了。
- 音量・score・overlayのsubscriber優先度、hit領域等の完全一致は今回保証しない。
  今回は既存のJava routingを通知時に評価するよう変更した。
- sprite hitのdepth／clip／alpha／丸め、他画面とのcounter引継ぎ、tablet／keyboard mouse代替も8bに残る。
- stableのSong Selectに正常到達する参照実行は未確保。同条件のpixel/audio比較は未実施であり、
  Javaのテスト・capture成功を全体の1:1合格とは扱わない。
