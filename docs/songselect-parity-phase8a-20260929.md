# Song Select parity: phase 8a — 押下・解放と右ボタンスクロール

2026-09-29。[phase 7](songselect-parity-phase7-20260929.md)と
[残phase台帳](songselect-parity-roadmap-20260929.md)の継続。
phase 8の追加調査で、Song Selectのgesture処理と共通dispatcher・spriteのhit判定が別経路と判明した。
今回はgestureを8aとして実装し、dispatcherとsprite hitの対応は8bへ分ける。
**phase 8全体や、stable実機との1:1比較を完了したという報告ではない。**

調査対象 `b20230727.9/osu!.exe` のSHA-256は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
`dnfile` / `dncil`によるIL・参照元調査。公式asset抽出、保護回避、production service接続は行っていない。
Javaのbackendはローカルにある`gdx-backend-lwjgl3-1.14.2.jar`を`javap -c -p`で確認した。

## 根拠と実装

| IL | 確認した契約 | Java側の対応 |
| --- | --- | --- |
| `06003243` | hoverを更新し、押下候補indexを保存。hover行がなく右buttonが押されていれば位置指定flagを立てる。 | 左の押下identityを保持。右は行の外で開始した場合だけ位置指定を開始する。右で行を押した場合のcontext操作は8b/13の残作業。 |
| `06003253` | 押下点と現在点の距離の二乗が`6400`を**超えた**ときに取消flagを立てる。距離判定はbutton保持中。 | 6 logical unitsのslopを廃止。80 window pixelsちょうどでは取消せず、超えた後に原点へ戻っても取消を維持する。解放だけで生じた移動では新たな距離取消をしない。 |
| `06004084/4085`, `04002830`への書込 | 距離計算はdisplayScaleで割る前のpointer値を使う。`4085`はraw値を保持し、別の参照座標をscale変換する。 | 押下点と現在点には`Gdx.input.getX/Y()`を直接渡す。UI scaleやframebuffer densityを掛けない。drag距離だけUI座標へ換算する。 |
| `06003244` → `06000fbc` | 保存した押下候補自身の、現在のbackground sprite矩形へpointerが入っているかを判定する。解放時の最前面hover行とのidentity比較ではない。 | 押下keyの現在のrow snapshotを探し、その矩形を判定する。確定処理へその行を直接渡し、もう一度最前面hitを引き直さない。行が失われた場合は確定しない。 |
| `06005140/513d` | native矩形は左・上を含み、右・下を含まない。`0fbc`は矩形判定時にalphaを参照しない。 | Yが上向きのJava座標へ境界条件を変換。解放のbounds判定をhoverのreveal条件から分離する。矩形の寸法・clip自体の一致は8bに残す。 |
| `06003253` | 左button保持中、drag flagが真、経過時間>0のときに移動をsampleする。前回Yの更新は保持の有無によらず行う。 | phase 6に残した解放フレームの最終移動加算を廃止。delta=0でもdrag移動を加算せず、次のsampleへ持ち越さない。解放時は保持中に得た速度と静止時間による既存の慣性処理を使う。 |
| `06003253` → `0600324f` | 右位置指定flagが真、左buttonが未保持なら、`clamp((pixelY−70×displayScale)/(pixelHeight−150×displayScale),0,1)`でviewportを指定し、減衰係数`.992`を使う。 | 全画面480高の参照座標へ換算した`clamp((Y−70)/330,0,1)`をscroll可能距離へ対応させる。viewport内での百分率や直接jumpにはしない。 |
| 同 | X<200参照単位でdrag中でない場合、またはkeyboard追従中は選択追従の分岐が先行する。右button解放で位置指定flagを下ろす。 | X境界とkeyboard追従の優先を維持。左button保持中は右位置指定を適用せず、右解放後は直前の慣性を継続する。 |
| `06003256/3267` | drag中・右位置指定中はhover候補の更新を止める。 | 左押下でその行をhoverに設定し、保持中は別行・空白への移動でhover identityを更新しない。右位置指定中もhoverを保持する。 |

小さいdragでも常にクリックを消す実装にはしない。移動が80px以内で、解放点が動いた押下行の現在矩形内にある場合はクリックが成立する。
選択済み難易度ならplay requestが発生することをproduction経路のテストでも確認した。

`SongSelectInputController`はwindow pixelからgestureを処理し、`SongSelectCarousel`は参照座標で位置指定を扱い、
Screenが現在のrow snapshotと結び付ける。Import / Gameplay / Renderer / Ruleset / GameClock / Skin APIの変更は不要だった。
overlay・import・outgoing・hide/disposeによるgesture取消は維持する。

## 8bへの調査引継ぎ

以下は**調査結果であり、今回すべて実装したわけではない**。

- **hover取得とsprite hit:** `06000fc4`はbackground spriteの`06003814`へ委譲し、そこは`040022e7`を返す。
  `06003256`はactive範囲を順に走査し、既存hover以外への切替には`06000fd6`（`040022df == 1`）も要求する。
  複数のeligible行がhitした場合は後のindexが候補になる。Javaのselected優先、reveal>=.05、75msのhover保持、
  chrome clip、row body寸法とnative background sprite矩形・整数pixel丸めとの対応は未解決。
  8aの解放判定はJavaが公開しているrow snapshotを使う。snapshot外になった行や装飾部までnativeと一致したとはしない。
- **key初回とrepeat:** `060043e1`は`060043e3(key,true)`を呼ぶ。
  repeatはroulette用の`0600327a`ではなく、共通keyboard更新`06002afb`から`060043e3(key,false)`へ入る。
  `04001b45`のreset値は−120、判定閾値は100、発火時は100を引き、最後にframe deltaを加える。
  catch-upのwhileではなく1更新につき1回の判定で、修飾キーだけの場合や前回の保持key集合も条件に含まれる。
  単純な「押下から固定220msのtimer」として置換する前に、初回処理・保持key更新の順序を合わせる必要がある。
- **Song Selectのkey分岐:** `06003247`ではLeft/Right/Enterは初回flagを条件にし、Up/Down/PageUp/PageDownには同じ条件がない。
  Shift+Left/RightはGroup移動、Shift+EnterはCtrlがない場合に親Group操作になる。
  ローカルbackendの`DefaultLwjgl3Input.keyCallback`はGLFW_REPEATで`keyDown`を再発行せず、`lastCharacter`の`keyTyped`だけを発行する。
  現在のJavaの`keyDown`依存では、保持した矢印のrepeatに対応できない。
- **drag-out:** `06003253`はpointerが`baseX−200`より左へ出た条件で`04001f7a`のcallbackを呼ぶ。
  Song Selectのconstructor `0600136e`の`0308`で登録するcallbackは`06001383`で、本文は`ret`だけだった。
  この画面で新しいeditor起動や外部dragを推測で追加する必要はない。
- **buttonとcallback順:** `06003244`の選択済み行からplayする分岐は`04001b57`を参照し、右操作では別callback `04001f77`も使う。
  `04001b57`の更新は共通mouse処理にある。左右同時入力、同一frame内のpress/release、wheelとの順序、
  controls/overlayによる消費を含め、8bでcallbackとpollingを対応させる。
  8aでは既存のScreen polling経路とcontrols優先を保ち、右の行context操作は追加していない。
- Set展開後のJava独自240ms play guard、最初のstationary sampleの扱い、delta上限2秒もdispatcherとの照合対象として残る。

調査用ローカル出力は`/tmp/osujava-phase8-{input,dispatch,hit,repeat}.il`、
`/tmp/osujava-phase8-repeat-xrefs.txt`、`/tmp/osujava-phase8a-lwjgl-input.javap`。
再調査時は上記assembly hashとmethod tokenを基準にする。

## 検証

`./gradlew :core:test --offline --console=plain --tests 'dev.osujava.ui.SongSelect*'`成功。
その後の追加回帰テストを含む`./gradlew build --offline --console=plain`成功。
**101 suites / 837 tests、failure・error・skipは0**。phase 7から19件追加。

- 80px境界、斜め48/64の距離、超過後の帰還、解放だけの移動、失われた候補・行外への解放。
- 押下行が移動した場合、選択行が上へ重なった場合、矩形端とreveal条件から独立した解放。
- scale 1 / 1.5 / 1.0666667 / 2での距離とdrag換算、delta=0・解放フレームの位置/速度への非加算。
- 行外からだけ始まる右位置指定、左保持中の抑制、release/overlay取消、左側・keyboard追従の優先。
- Y=70/235/400と範囲外の位置指定、`.992`補間、dragと右位置指定中のhover identity維持。
- production経路での解像度換算、重なり、短いdragのclick/play、長いdragの非clickと描画geometry同期。

既存dragテストの「20〜60px動けば必ず取消」という前提は、新しい80px契約に合う90px以上の操作へ修正した。
逆向きfixtureも`+30,+30,−60`から`+50,+50,−100`へ変更して、一度80pxを超えてから戻る条件を維持した。
移動量・反転速度・静止後の減衰・選択不変・誤play防止のassertは残した。
Input mockが全buttonへ同じ左button値を返していた点も、左右を分離した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=pointer-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8a-pointer-20260929
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=drag-contracts \
  -PsongSelectOutput=/tmp/osujava-phase8a-drag-20260929
```

| harness | scenes | PNG | scripted frames |
| --- | ---: | ---: | ---: |
| pointer-contracts（新規） | 8 | 40 | 392 |
| drag-contracts（回帰） | 16 | 96 | 1,028 |
| 合計 | 24 | 136 | 1,420 |

1280×720 / 1920×1080 / 1280×720の2倍framebuffer / 1024×768で実行。
右位置指定には30/60/144Hzの混在deltaを使い、各frameの位置を独立計算値と比較した。
threshold fixtureは81 window pixelsで非play、80でplay requestを確認する。
1280×720の右位置指定途中と1024×768の80px解放captureを目視確認した。

これはJava側の計算・状態・描画経路の検証。stable Song Selectの正常な参照実行は未確保で、
同条件のnative pixel/audio比較は未実施。音声deviceのないharness環境なので、音声比較の合格とも扱わない。
Skinの独立作業はphase 12のまま維持する。
