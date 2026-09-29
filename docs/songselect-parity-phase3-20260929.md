# Song Select parity: phase 3 — keyboard focusと循環移動

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)と
[phase 2](songselect-parity-phase2-20260929.md)の永続行モデルを基礎に、keyboard操作を追加対応した。
対象stableのSHA-256は前回と同じ
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。

## ILで追加確認した入力契約

| 箇所 | 確認内容 |
| --- | --- |
| `06003247` `0051–00bd` | Up/Downは`06003276(±1,true,false)`、Pageは`(±10,true,false)`。左右キーはfocusがあれば`0600326a`で確定、なければ`06003276(±1,false,true)` |
| 同 `006c–0082` | Shift＋左右は`06003275`へ渡す |
| 同 `00c4–0122` | EnterはShiftかつControlなしなら`06003274`。それ以外はfocusを`0600326a`で確定し、focusがない場合に選択行のcallbackを呼ぶ |
| `06002afb` `001e–0091` | `04002a96`は左右Controlの状態（162/163）、`04002a98`は左右Shift（160/161） |
| `06003228` | traversalの起点はfocusがあればfocus、なければ選択譜面のindex |
| `06003276` `0024–0051` | 起点の行が表示候補でなく、現在Groupが開いている場合、そのGroupを起点にする |
| 同 `007b–00dc` | 全行配列を循環。visible側は除外flagと状態0を除き、Groupも数える。もう一方はGroupと選択中の代表グループを除き、非表示の難易度も数える。指定件数に届かなくても起点へ一周すると終了 |
| 同 `00de–00f1`、`06003277` | 確定側は`0600326a`。focus側は以前のfocusを解除し、選択中と同じ代表グループなら直接選択、異なる場合はfocus indexだけを保存 |
| `06003274/3275` | 現在Group（なければ選択行の親）を起点に、前者はそのGroupを開閉、後者は除外されていないGroup間を循環して開閉する。両者ともfocusを解除 |
| `0600326c` | 譜面選択ではfocusを解除する。Groupの確定は開閉処理へ入り、同じGroupにfocusを置いたまま再度操作できる |
| `06003259/324c` → `06001965` | focusは元のRGBを各1.4倍、255で飽和しbyteへ変換。alphaを保持。設定・解除で50を遷移時間へ渡す |
| `06000fd8/0fd9` → `060040c1` | focus色を背景spriteへ渡し、現在色から目標色までのtransitionを作る。終了時刻は開始時刻＋渡された時間 |

`0600326a`には、代表グループ内から現在modeと保存された難易度評価に近い譜面を選ぶ処理もある。
この選択基準は今回の実装完了範囲に含めない。後述の通り、Javaの既存fallbackを明示的に残している。

## 実装

- `SongBrowserModel.focusKey` をplayable selection、直接操作したGroup、開いているGroupから分離した。
  focus移動だけではmetadata・背景・score target・Setの展開を変更しない。
- 上下とPageを同じ全行traversalへ統合した。距離は1と10。端で循環し、少数の行では一周を上限にする。
  閉じた子・除外行を正しく飛ばし、可視Groupを数える。
- 同じSet内への移動は直接選択する。別SetまたはGroupへ移るとfocusを置く。
  Enterまたは左右でfocusを確定した一回の操作ではGameplayを開始しない。
  Set確定後はfocusを解除し、次のEnterで選択譜面を開始できる。Groupにfocusがある間はEnterで開閉する。
- 左右はfocusがなければ現在の代表グループ外を探して直接選択する。
  Shift＋左右はGroup間移動、Shift＋Enterは現在／親Groupの開閉。Control＋Shift＋Enterは通常の確定経路。
- 検索中のEnter、overlayの優先順位は維持する。Import中・画面遷移開始後はfocus確定・行／Group移動を受け付けない。
- sortと同じ譜面のlibrary refreshではfocusのidentityを保持する。削除・非表示化されたfocusは破棄し、検索・Group種別変更でfocusをリセットする。
  この修復方針はJavaの既存検索／再読込方針に接続したもので、stableの全修復経路の再現を主張しない。
- Carouselのスクロール先と選択強調を分離した。focus先へスクロールしても、その行を選択譜面として強調しない。
  focus量を描画snapshotに渡し、RGBの増加と50msの遷移を適用する。中断時は途中の値から遷移し直す。

BrowserはGL非依存、Carouselは表示状態、Rendererはsnapshotの描画を担当する。
Import / Gameplay / Ruleset / GameClockは変更していない。
公式asset抽出・保護機構回避・production serviceへの接続は行っていない。

## 検証

`./gradlew build --offline --console=plain` 成功。
JUnit: **97 suites / 774 tests、failure・error・skipは0**（phase 2から19件追加）。

- 同一Set内の直接選択と別Setへのfocus、逆方向への復帰、端の循環。
- Pageで非表示の難易度を数えず、Groupを数えること。一周で停止すること。
- Groupのfocus→Enterで開閉、非表示になった選択譜面と開Groupからの移動。
- Enter／左右で確定しても同時に開始しないこと。ShiftとControlの経路。
- 背景の選択維持、overlay/search/Import/画面遷移中の入力制御。
- library refresh・削除・検索ゼロ件でのfocus修復。
- 色のbyte飽和・alpha保持、50ms・中断・再構築後の状態維持、focusと選択強調の分離。

従来の「上下キーで他Setを即選択」「端で停止」「PageはGroupを数えない」という期待値は、
今回確認したILに合わせて更新した。全行モデルにしたことでゼロ件検索でも内部配列が空とは限らないため、
再生対象なしのtraversalがnull起点を参照しないよう回帰テストとともに修正した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=navigation-contracts \
  -PsongSelectOutput=/tmp/osujava-navigation-contracts-20260929
```

実画面のInputProcessorへキーを送り、Set focus、Set確定、Group focus、Group確定、Page移動、
既存のGroupクリック開閉を検証する。条件は1280×720、1920×1080、1280×720の2倍framebuffer、1024×768。
focus状態・選択強調・score target・誤開始がないことを描画と合わせてassertする。

最終実行は **24 scenes / 24 PNG captures**、navigation/disposalチェックを含め成功。
Setにfocusを置いた画面と、focus中のGroupをEnterで閉じた画面の代表PNGを目視確認した。
出力先は上記一時ディレクトリ、ログは `/tmp/osujava-navigation-contracts-20260929.log`。
`git diff --check` も成功。

初回のvisual実行では、共通の後処理が「スクロール先＝選択譜面」を前提としており、
Pageでfocus先へ移動した後の選択行へのwheel検証が失敗した。
focus画面をcapture・assertした後に選択譜面へ明示的に戻すようharnessを修正し、全24ケースを再実行した。

## 残る差分と次の作業

1. **確定する難易度の選び方**: stableの`0600326a`が使うmode・保存された評価への近さは未接続。
   Javaにはproduction用の信頼できるstar rating sourceと対応する保存設定がないため、Set確定は先頭難易度を使う既存fallback。
   推定評価を新規に作って互換性を装ってはいない。
2. **入力の時系列**: keyboard callbackとpointer pollingの統一、repeat、同フレーム内の押下・確定・dragの順序、
   sprite dispatcher/native hitbox、右clickの全分岐。今回Enterと直接Play（Space/Cookie）を混同せず、後者は従来の選択譜面開始を維持。
3. **運動モデル**: スクロール先と選択強調は分離したが、spring・非表示行の位置履歴・全gap条件はまだJavaの既存モデル。
   stableのfocus時スクロールの閾値・時刻まで一致したわけではない。
4. **描画・音**: focusのRGB変換と50msの遷移を接続したが、基底色・hoverやGroupの300ms色遷移との合成、
   clock選択、focus用効果音のprovider・発音時刻は引き続き対応が必要。
5. phase 2記載の検索・全sort/group種類、metadata、Skin失敗契約・HD eligibility等。

stable実機との同条件pixel/audio比較は未実施。Javaの回帰検証の成功は、Song Select全体の1:1一致の証明ではない。
