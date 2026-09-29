# Song Select parity: phase 8b — 再クリックとplay要求

2026-09-29。[mouse押下/解放対応](songselect-parity-phase8b-mouse-20260929.md)の続き。
今回の範囲はSet展開後の独自play guardの除去と、通常mouseのdouble-click分類。
**8b全体は継続中**で、sprite hit、wheel順序、画面/focus境界等は残る。

作業場所は`/home/coder/worktrees/osujava-songselect-phase8b`、branchは`codex/songselect-phase8b`。
開始時HEADは`778c808298e4ab3156427e15ecf110123d31b542`。元worktree/mainへの変更・統合は行っていない。

## 根拠

対象は`b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
`dnfile` / `dncil`でILと参照元を確認した。公式asset抽出、保護回避、production service接続は行っていない。

| 箇所 | 確認内容 |
| --- | --- |
| `06002af9` `006f–0092` | double-click counter `04001b2b`は整数。正ならframe delta（ms）を引き、0でclampして整数へ変換する。新しい押下を判定するより前に実行する。小数部はframeごとに失われる。 |
| 同 `043a–0449` | 物理down時にraw押下座標と時刻を更新する。これは通常downを通知するかどうかの判定より前にある。 |
| 同 `045f–04b6` | counterが正ならdouble-click通知をqueueへ入れてcounterを0へ戻す。それ以外は通常downをqueueへ入れ、counterを250へ設定する。button種別や行identityごとのtimerではない。 |
| 同 `04c1–05cd` | 左右の新しい押下では、まだ通常downをqueueへ入れていなければ追加する。したがって左右の2回目も行の押下候補を捕捉する。中buttonだけの2回目はこの追加経路を通らない。 |
| `06004096`、`0600321a` `00e0–012b` | double-click通知はevent 5。行コンテナはwheel 0/1、up 6、通常down 8を登録し、event 5は登録しない。 |
| `06003244` `0126–013d` | 有効な候補のreleaseで、選択済み譜面かつ前回右buttonがfalseならplay callbackを呼ぶ。Set展開後の経過時間による待機条件はない。 |
| `0600136e` `02c8–02da` → `06001381` → `06001375` | Song Selectのplay callbackは遷移開始へ接続される。選択譜面、roulette状態 `04001f5e`、画面modeの検査があるが、Setを選択してから240ms待つ条件はない。 |
| `0600326a/326c` | Setの確定は譜面選択へ進み、選択・展開状態とcallbackを更新する。ここにも240msの再クリック禁止timerはない。 |
| `06003256`、`06000fd6` | 新しいhover候補へ移る際はbackground spriteのalpha==1等を確認する。一方、既存hover indexと同じ候補ではalpha検査を繰り返さない。この条件は一律の時間guardとは異なる。 |

`04001f5e`の代入元も照合した。`06003279`でtrue、`0600327b`でfalseになり、
前回調査のroulette更新`0600327a`が参照する。展開後のplay guardと誤認しない。
調査出力は`/tmp/osujava-phase8b-double-click.il`、`double-click-xrefs.txt`、
`play-selection.il`、`play-state-xrefs.txt`（すべて同じ`/tmp/osujava-phase8b-` prefix）。
中buttonのedge `04001b3e`も`06002af9` `0327–0341`で現在/前回の物理state比較と確認した。

## 実装と設計判断

- `SongSelectScreen.setClickGuard`を削除した。Setを1回クリックしただけでは選択・展開のみだが、
  その後に選択済み譜面へ有効な左releaseを行えば、240ms待たずにplayを要求する。
  右releaseのcontext分岐、取消・release矩形の検査は従来どおり適用する。
- `SongSelectInputController`のbutton snapshotに250の整数counterを加えた。
  frame deltaを先に減算し、物理downで通常/double-clickを分類する。
  2回目でcounterを0へ戻すため、短時間の3回目は新しい組の初回となる。
- 左右のdouble-clickでも通常downは通知する。中buttonだけの2回目は通常downを通知せず、
  新しい行候補を捕捉しない。up経路は省略しない。
  通常downの有無と物理downの有無を別々に保持し、中buttonの通常downが省かれた場合でも
  距離取消の基準座標を更新する。左drag中に中buttonを追加したケースも対象にした。
- counterは行gestureのcancelで初期化しない。chromeやoverlay中の物理押下も、
  同じSong Select画面内のsnapshotで分類する。
- Screenのframe deltaをmsへ変換して使う。Renderer、Gameplay、Ruleset、GameClock、Skinへ
  共通double-clickシステムを導入する必要はなく、今回の変更をUI入力に限定した。

nativeのcounterはframeごとの整数化を含むため、「実時間で常に250ms」という仕様には置き換えていない。
例えば0.1msずつ250 frame進めると、整数counterは250減る。30/60/144Hzの境界もテストで固定した。

## 検証

関連Song Selectテストと`./gradlew build --offline --console=plain`成功。
**104 suites / 902 tests、failure・error・skipは0**。前回から19件追加。

- 左/右からのSet展開後、次の左releaseで直ちにplay要求。
- 中buttonの2回目で候補を再捕捉しないこと、3回目とcounter期限後にはplayできること。
- counterの共有、同時downの集約、保持frameで再初期化しないこと、release frameの時間も減算すること。
- 249/250ms境界、減算→押下判定順、小数msの整数化、30/60/144Hz、非有限・負deltaの防御。
- gesture取消でcounterを消さないこと、chromeの押下もcounterへ反映すること。
- 中buttonの通常downが省かれた場合のraw距離基準更新と、左drag候補の維持。

追加した中buttonテスト2件は当初、選択追従で行が移動した後も古い位置へreleaseして失敗した。
実際に押下行の現在矩形へreleaseするfixtureへ修正し、既存のrelease条件を保ったまま全件成功した。
旧`setDoubleClickCannotPlayTheDifficultyReplacingItsRow`は独自guardを期待していたため、
新たな調査根拠に基づく即時play要求のテストへ置き換えた。

新規描画harness `activation-contracts`は**12 scenes / 28 PNG / 56 scripted frames**成功。
1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768それぞれで、
左/右からの展開と再クリック、中buttonの2回目・3回目を検証した。
出力は`/tmp/osujava-phase8b-activation-20260929`。
1280×720の即時play要求後と、1024×768の中buttonの2回目のrelease後を目視確認した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=activation-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8b-activation-20260929
```

既存harnessの行クリックには、保持状態を立てず`isButtonJustPressed`相当のflagだけを使う箇所が残っていた。
これを実際のpressed snapshot→released snapshotへ修正した。Groupカードと最後のplay確認も同じhelperを使う。
Set選択のcaptureは最初の選択・展開のみを記録し、再クリックのplay要求は新規fixtureで検証する。

既存`navigation-contracts`は**24 scenes / 24 PNG / 72 scripted frames**成功。
出力は`/tmp/osujava-phase8b-activation-navigation-rerun-20260929`。
最初の実行は中断され完了記録がなかったため、別出力先へ再実行して終了を確認した。
Group開閉後のselection保持と、選択済み行からのplayもproductionの押下→解放で確認した。

既存`lifecycle-contracts`も**8 scenes / 68 PNG / 1,216 scripted frames**成功。
出力は`/tmp/osujava-phase8b-activation-lifecycle-20260929`。
Groupの初期位置配布と画面外からの再表示を確認し、今回の合計は
**44 scenes / 120 PNG / 1,344 scripted frames**となった。

## 残る条件

- sprite managerのhitbox、depth、clip、alpha、丸めは未完了。今回保証するのは、
  **押下候補が有効に確定したreleaseに余分な240ms制約を加えないこと**である。
  任意位置・任意タイミングのdouble-clickが必ずplayになるという意味ではない。
- nativeはglobal counter、JavaはSong Select画面内のcounterである。他画面、focus喪失、pauseを
  またぐstateとclockの引継ぎ、tablet/keyboardによるmouse代替、他UIのevent 5 subscriberは未対応。
- wheelとkeyboard初回callbackを含む全体順序、Options dialog等の実機能、Skin独立対応は引き続き残る。
- stable Song Selectの正常な参照実行は未確保。同条件のpixel/audio比較は未実施であり、
  Javaテスト・capture成功を全体の1:1合格とは扱わない。
