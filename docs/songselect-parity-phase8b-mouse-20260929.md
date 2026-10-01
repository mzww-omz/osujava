# Song Select parity: phase 8b — mouseの押下・解放

2026-09-29。[キーボード対応](songselect-parity-phase8b-keyboard-20260929.md)の続き。
今回は通常mouseのbutton snapshotから行の押下・解放を通知する経路を実装した。
**8b全体は継続中**。wheelの通知順、sprite hit、global dispatcherの残条件は後述のとおり残る。

作業場所は`/home/coder/worktrees/osujava-songselect-phase8b`、branchは`codex/songselect-phase8b`。
開始時HEADは`02cba32a8ed4f33956ecd51daa3225f37cb2d019`。並列作業中の元worktree/mainへは変更・統合していない。
Skin、Gameplay、Import、Ruleset、GameClockは今回変更していない。

## 調査根拠

対象は`b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
前回のIL調査と同じく`dnfile` / `dncil`でmethod本体・参照元を確認した。
公式asset抽出、保護回避、production service接続は行っていない。

| 箇所 | 確認した契約 |
| --- | --- |
| `06002af9` `0346–03ae` | 物理mouseの左右・中buttonについて前回stateと比較する。新しいdownがあれば共通down経路、いずれかのupがあれば共通up経路へ進む。別buttonが保持中でもupを検出する。 |
| 同 `0433–05cd` | 共通downはdouble-clickの判別を通る。左右の新規押下では、先に共通downをqueueへ入れていなければ追加する。左右同時押下を左右別々の行クリックとして通知しない。 |
| 同 `05e6–0655`、`077f–079c` | 共通upをdownの後にqueueへ追加する。queueをdrainしてからsceneの更新を呼ぶ。同一snapshotで左右を交換した場合もdown→up→scene更新の順となる。 |
| 同 `07a1–07ca` | scene更新の後で左右の前回stateを保存する。行のrelease中に読む`04001b57`は**前回frameの右button保持状態**であり、現在の右buttonや最後に押したbuttonではない。 |
| `06004093/4095` | 共通down/upをdispatcherへ通知する。行の`06003243/3244`へ到達する経路を前回のsubscriber調査と照合した。 |
| `06003243` | hoverを確認し、その行を押下候補へ保存してkeyboard追従を解除する。hoverがなく右button保持中なら位置指定scrollを開始する。ここでは左dragを開始しない。 |
| `06003244` `000c–0074` | 取消flagを保存する。左buttonがupならdrag releaseの減衰を行い、stationary時間・取消flag・drag状態を解除する。左buttonが保持中ならこれらを維持する。 |
| 同 `0083–0166` | 候補行自身の現在矩形と保存した取消flagを確認する。未選択行/Groupは選択・展開処理へ進む。選択済み譜面のplay callbackは前回右buttonがfalseの場合だけ。trueならcontext callbackへ進む。成功時に候補を−1へ戻すが、範囲外/取消等の早期returnでは候補を残す。 |
| `06003253` `000c–01ec` | 左保持中の既存dragを計測してから、新しい左押下ならdragを開始する。初回frameの時間を速度推定へ加算しない。前回Yはその後に保存する。 |
| 同 `0200–026e` | 左端領域の非drag時、keyboard追従時などは選択追従へreturnする。それ以外で左右のいずれかを保持中なら、共通down時のraw座標からの距離が80 window pixelsを超えると取消する。 |
| `0600136e` `02b1–02da`、`06001380/138d` | Song Selectのcontext callbackは、譜面行ではBeatmap Optionsのdialog経路へ進む。通常Groupでは開かず、分類値18のGroupには別のdialog経路がある。 |

新規調査出力は`/tmp/osujava-phase8b-mouse.il`、`mouse-xrefs.txt`、`mouse-events.il`、`mouse-routing.il`
（後ろ3つも同じ`/tmp/osujava-phase8b-` prefix）。前回の`dispatch.il`等も使用した。
`04001b31`は移動通知の状態であり、wheelの識別子とみなしてはいけない。

## 実装と設計判断

- `SongSelectInputController`で左右・中buttonの前回snapshotを保持し、共通down/upと前回右button値を作る。
  Screenはdown→up→保持中の動作計測→viewport積分の順に処理する。
  行クリックは`isButtonJustPressed`だけから作らず、保持stateの変化を必要とする。
  frame間に完了し、down状態が一度もsnapshotに現れない行クリックは生成しない。
- 行の押下候補と左dragの寿命を分離した。右押下も行を捕捉するが左dragを開始しない。
  左drag中の追加button押下は保持中のhoverを使い、速度を消したりdragの計測時間を初期化したりしない。
  右buttonを先に離して候補を消費しても、左buttonが保持中ならdrag自体は続く。
- 取消は共通downで無条件に初期化しない。左保持中の別buttonのdown/upを挟んでも取消を維持する。
  upで左が離れている場合だけ取消を解除する。失敗したreleaseで候補が残るnativeの早期returnも反映した。
- 右releaseは未選択のSet/難易度を選択し、Options actionを要求する。選択済み譜面をplayしない。
  通常Groupは展開/閉鎖のみ。左右交換時は前回右button値を使うので、現在右がdownでも左由来のplayになる場合がある。
- overlay・import・outgoing等ではgestureを取消すが、button snapshotは進める。
  overlayを閉じた後に、押し続けたbuttonから新しい行クリックを作らない。
- 既存の左buttonによるchrome操作、wheel routing、描画用hit geometryは維持した。
  小さいUI入力状態の変更で対応できるため、global Input APIやRendererの再構成は行っていない。

## 未完了範囲

- **Options dialog自体は未実装**。contextの接続先は既存の「Beatmap Options unavailable」の案内である。
  Options/collectionの実機能とmodal表示後の入力抑制はphase 10/13へ残す。
  Javaの併用button検証で案内後もdragを続けられることを、nativeのOptions dialog表示中の挙動と同一視しない。
- nativeの250ms double-click通知、特に中buttonだけの連続押下、tablet/keyboardによるmouse代替、
  他画面やfocus喪失をまたぐglobal stateの継承は、今回のsnapshotモデルでは完全再現していない。
  通常の左右押下に必要な共通経路と、中button単発の共通経路を対象とした。
- wheel/callback/polling全体の順序、同一frameの初回key順、Sprite managerのdepth/clip/alphaとbackground hitbox・丸めは未完了。
  group/selected優先を含むJavaのhit policyはまだnativeとの完全一致を保証しない。
- Set展開後の既存240ms play guardは維持した。release側に同じtimerは見当たらないが、
  playの遷移先・double-click・sprite有効化まで含めた確認を別単位で行う。

## 検証

関連Song Selectテストと`./gradlew build --offline --console=plain`成功。
**104 suites / 883 tests、failure・error・skipは0**。前回から22件追加。

- 同時down/upの集約、左右交換時の前回右state、片方だけの解放、候補の一度だけの消費。
- 右releaseでのSet/難易度選択、既選択譜面の非play、通常Groupの操作、範囲外release・80px超の取消。
- 左drag中の追加downでhover/速度を維持、取消を維持、releaseを新しいmotion sampleより先に判定。
- 初回drag frameの非加算、解放移動/zero delta、高DPIの80px境界、右位置指定scrollの既存契約。
- overlay中断、held状態からの誤再開防止、中button単発、snapshotに現れない短いクリック。

最初の全buildで、新規Groupテストのクリック点が上部clip外となり1件失敗した。
fixtureを現在行とviewportの交差部分へ修正し、再buildで全件成功した。
既存のpointer/foundationテストは、productionと同じ共通down/up→motionの呼出順へ更新した。

描画harnessの`pointer-contracts`は、右行選択と左右併用のfixtureを追加し、
**16 scenes / 60 PNG / 416 scripted frames**成功。
1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768を対象とし、
右位置指定scrollは30/60/144Hzの混合deltaで検証した。
出力は`/tmp/osujava-phase8b-mouse-pointer-20260929`。
1280×720の右選択後と、1024×768の右解放後に左dragを続けたcaptureを目視確認した。

既存`drag-contracts`も**16 scenes / 96 PNG / 1,028 scripted frames**成功。
flick、停止後release、反転、overlay取消を同じ4種類の画面条件で再検証した。
出力は`/tmp/osujava-phase8b-mouse-drag-20260929`。
合計**32 scenes / 156 PNG / 1,444 scripted frames**で、navigation/disposal checksも成功した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=pointer-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8b-mouse-pointer-20260929
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=drag-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8b-mouse-drag-20260929
```

stableのSong Selectへ正常到達する参照実行は未確保。同条件のnative pixel/audio比較は未実施であり、
今回の静的契約・Javaテスト・capture成功を画面全体の1:1合格とは扱わない。
