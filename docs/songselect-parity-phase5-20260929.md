# Song Select parity: phase 5 — viewport慣性・追従・曲線予測

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)、
[Skin独立調査](skin-stable-independent-audit-20260929.md)、
[phase 4](songselect-parity-phase4-20260929.md)に続く実装。
対象 `b20230727.9/osu!.exe` のSHA-256は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。

viewportのspringを、速度の指数減衰・積分と目標位置からの速度設定へ置き換えた。
物理wheel、通常の選択／keyboard focus追従、行Xの曲線へ渡す移動量予測を接続した。
**drag中の速度推定・release慣性、Groupの初期配置、画面外の再進入は次段階**。

## 静的に確認した契約

`dnfile` / `dncil`でメソッド本体・呼出元・座標変換を読み取った。
速度の単位は480高の参照座標 / ms、elapsed timeはms。

| 箇所 | 確認内容 |
| --- | --- |
| `06003254` `0014–0059` | drag中でないとき、`vNew = vOld * decay^deltaMs`。移動量は `(vNew-vOld)/ln(decay)` |
| 同 `005e–008d` | このフレームの移動量を求めた後、`abs(vNew)<0.01`なら速度を0、係数を`0.9959999918937683`へ戻す |
| 同 `0092–00cf` | travelが正の場合、移動量をtravelで割りscroll fractionへ加え、0–1へclamp。端に到達したこと自体で速度を0にしない |
| `0600324f` | 係数0なら即座に位置を設定。それ以外は目標位置との差dから `v = -d*ln(decay)` |
| `0600325b` | 残り移動量の予測は `-v/ln(decay)`。scroll範囲ではclampしない |
| `0600325e` | 曲線入力は現在行Y＋符号付きscroll＋上記予測。予測項の符号は加算 |
| `06003245/3246` | wheelはkeyboard追従flagを解除し、`v += ±0.4*(1+min(abs(v)/2,5))`、係数は0.994。逆方向入力時も速度をresetしない |
| `0600326c` `01cc–01d6` | 選択処理の最後に係数0.99で追従を要求 |
| `06003250/3251/321e` | keyboard追従flagがありfocusがあればfocus、それ以外は選択譜面。選択行の親が閉じていれば現在Groupを使う。追従位置は行座標−220 |
| `06003277` `001f–0021` | 上下／Pageのfocus traversalはkeyboard追従flagを立てる |
| `06003243/3244`、`3245/3246` | pointer押下・有効な解放・wheel入力はkeyboard追従flagを解除 |
| `06003253` `0200–0238` | 通常のSong Selectでは、非dragでポインターX<200、またはkeyboard追従flagがある場合に係数0.992で追従し直す |
| `06004082/4085` | 上記ポインター座標はdisplay scaleで除算済み。X閾値も480高の参照座標で解釈する |
| `06003255` | 入力／追従の更新→viewport積分→行描画更新の順 |

停止閾値はフレームごとの判定なので、閾値を跨ぐフレームが違えば最終停止位置も少し異なる。
「必ず目標へ完全にsnapする」「全時間域でfpsに無関係な同一停止位置」はstableの契約ではない。
継続追従では毎フレーム速度を設定し直すため、単発の移動とは収束が異なる。

## 実装とJava側の適応

- `SongSelectScroll`を小さなGL非依存の計算クラスとして分離した。
  参照座標の位置・範囲・速度・減衰係数だけを持ち、Input、譜面、GameClock、Rendererへ依存しない。
  `SongSelectCarousel`が行と追従先、Screen/Inputがポインターやkeyboardの条件を渡す。
- 旧spring、synthetic wheel速度、速度上限、未使用のvelocity influenceを除去した。
  X/Yの行補間はphase 4の0.95／0.875を維持し、曲線へ符号付きの残り移動量を加える。
- `scrollTarget()`は独立したspring目標ではなく、現在位置と速度から求めたclamp済みの到達予測を返す。
  描画診断のvelocityは従来の表示単位（logical UI / second）へ変換する。
- wheelのproduction経路は「rowHeightを掛けた距離指定」からnotch入力へ変更した。
  複数notchを一つのeventで受けた場合は1 notchずつ加速する。
  小数のtrackpad入力は端数分の加速を適用し、既存のevent上限10000を維持する。
  **この小数・batch変換はlibGDXへの適応方針**で、stableのOS別wheel dispatcher全体を再現した主張ではない。
- 上下／Pageでkeyboard追従を開始し、wheel・押下で解除する。
  focusの表示状態・playable selectionはそのまま残り、wheel後にポインターを左側へ戻すと選択譜面を追従する。
  非表示になった選択譜面の代わりに現在Groupを追従するkeyはBrowserが解決する。
- Javaのoverlay／Import／画面遷移中は追従要求を止める。押下中の追従も止める。
  これらは現在のInput controllerとの接続方針であり、nativeの全button・mode・dispatcher分岐の対応は未完了。
- 参照座標の速度を保持するので、window scale変更時も慣性の強さは変わらない。
  content再構築では位置を新範囲へclampし、ゼロ範囲では残留速度を破棄する。
- 直接dragは既存の即時移動経路を維持し、今回の速度をキャンセルする。
  `scrollBy(distance)`はpreview/debug用の距離指定として残し、物理wheelには使わない。
- 積分は等価な`vOld*expm1(ln(decay)*deltaMs)/ln(decay)`で計算する。
  nativeの`vNew==vOld`時の移動量fallbackは採用せず、Javaのdelta=0では移動せず、微小deltaでも桁落ちを避ける。
  resizeとhit geometryの更新でdelta=0を使うための明示的な適応。既存の最大delta 2秒の防御も維持する。

Import / Gameplay / Renderer / Ruleset / GameClockの責務は変更していない。
完全ローカル動作を維持し、production service接続・公式asset抽出・保護回避は行っていない。

## 検証

`./gradlew build --offline --console=plain` 成功。
JUnitは **100 suites / 795 tests、failure・error・skipは0**（15件追加）。

- 1 notchの速度・100ms後の積分・残り予測を独立した数値で検証。
- 連続加速、逆入力での制動、加速量の上限と速度上限が別であること。
- 0.99／0.992の使い分け、停止直前の積分、範囲clampで速度を勝手に消さないこと。
- 閾値前の30/60/120Hz積分、ゼロ／不正／微小delta、小数・batch・極端なwheel値。
- keyboardとpointerの追従先、X=200の境界、wheel後のfocus表示と選択維持、Groupへの代替追従。
- resize後の速度・予測のscale、境界外の符号付き予測によるX曲線、従来のclick／drag／search／wheel routing。

Xvfb上の実描画harnessも成功。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=navigation-contracts \
  -PsongSelectOutput=/tmp/osujava-phase5-navigation-20260929
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=row-motion-contracts \
  -PsongSelectOutput=/tmp/osujava-phase5-row-motion-20260929
```

- navigation: **24 scenes / 24 PNG captures**。Set/Group focus、確定、Group開閉、Page移動。
- row motion: **32 scenes / 176 PNG captures / 1,440 scripted transition frames**。
  高速wheel、反転、hover、16難易度の展開／collapse、library両端。
- 各ケースは1280×720、1920×1080、1280×720の2倍framebuffer、1024×768。
  共有する描画・hit geometry、行右端、navigation、disposalを確認した。
- 1280×720のwheel反転10フレーム目／60フレーム目を目視確認した。
  PNG等の生成物は上記一時ディレクトリ、ログは同名の `.log`。Gitには含めない。

初回検証ではharness側の二つの前提が旧実装に依存していたため修正した。
Group click準備で左側へポインターを置くと、新しい選択譜面への追従により対象Groupが移動するので、
準備中は右側へ置いてからproductionのpress/releaseで操作する。
wheel反転では1秒経過時点にまだ慣性が残るため、captureの元の時刻を維持した上で、
最大180フレームの追加更新と各フレームの描画範囲assertを行い、到達予測との差が1以下になることを確認する。
停止assertを削除したり、capture前に強制snapしたりはしていない。

最終変更後の全体build、795テスト、`git diff --check`も成功。

## 次の作業

1. **dragの入力時系列**: `06003253`は移動のない時間も蓄積して速度を推定し、0.9／0.95の平滑化と
   `max(0.5,0.9959999918937683-0.002/abs(v))`で係数を設定する。
   `06003244`のrelease時は`0.95^max(0,停止時間-66)`を掛ける。
   Javaの押下候補・drag判定・解放判定と一体で置換する必要がある。
2. **Group開閉の初期配置**（`06003273`）と**画面外からの再進入・snap・sprite可視化**（`06003263/3266`）。
3. native hitbox、keyboard repeat、右buttonの位置指定、hover色・音・時計など入力／描画の残り。
4. phase 3・4とSkin独立調査に記載した難易度選択、検索・sort/group、metadata、Skinの残り。

stable実機での同条件pixel/audio比較は未実施。今回の数値契約とJava側の回帰検証の成功は、Song Select全体の1:1一致を意味しない。
