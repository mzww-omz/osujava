# Song Select parity: phase 2 — 永続行とGroup開閉

2026-09-29。前提は [全体再調査](songselect-parity-reinvestigation-20260929.md)、
[Skin独立調査](skin-stable-independent-audit-20260929.md)、[phase 1](songselect-parity-phase1-20260929.md)。
今回の対象はBrowserの行状態とGroupカード。Song Select全体の1:1対応完了を意味しない。

## 追加確認したstableの契約

対象: `/home/coder/workspace/b20230727.9/osu!.exe`。
SHA-256: `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
ILから条件・データの関係を確認し、Java側の既存モデルに独立実装した。
公式asset抽出・保護機構回避・production serviceへの接続は行っていない。

| 箇所 | 確認した契約 |
| --- | --- |
| `06003257` `0025–0069` | Groupを境に代表行候補をリセット。除外行の代表参照をnull、識別子を−1、状態を0にする |
| 同 `006b–00c1` | 文字列キー `0400259b` が変わる先頭行を代表にし、同じ並びの後続行から代表を参照する。代表に立てた `0400086c` は後続行があると代表・後続ともfalseになる |
| `06003268` | 親が閉じている／除外された行は0、選択行は4、選択行と同じキーは3、上記flagは2、代表行は1、それ以外は0 |
| `0600326c` `00ca–018a` | Groupを操作すると以前のGroupを閉じ、対象Groupを開閉する。以前の選択譜面が対象Groupの子に存在する場合に選択indexを対応し直す。先頭の子譜面へ無条件に選択を変える処理ではない |
| `060025e2/25e5/25e8` | GroupもRowを継承し、子件数付きラベルとカードspriteを持つ。文字の水平offsetは15 |
| `06000fb1` `0013–0059`、`060025e6` | 開いたGroupの基本色はRGBA(163,240,44,255)、通常の閉じたGroupは(35,50,143,255)。開閉に応じてactive/inactive文字色を使う。別条件の閉じたGroup色(35,90,193,255)もある |
| `0600325a`（既存調査） | 表示候補のpitchは48。非GroupからGroupへの境界にgap10がある |

状態2は、**Browserの `06003257` の構築経路に限り、代表の並びが単独の行だったことを示す**と追加確認できた。
同じflagを設定する別画面の `060020f1` まで同じ意味だとは扱わない。
`0400259b` の生成元と全sort/filter経路の対応は未確定であり、Javaでは既存のローカルBeatmapSet単位に適用している。

## 実装と設計判断

- `SongBrowserModel.Row` を難易度ごとの永続オブジェクトにした。親Group、代表行、除外flag、状態0〜4を保持する。
  閉じた子や検索除外行も `rows()` に残し、描画用 `entries()` だけを可視行へ絞る。削除された譜面・Groupはpruneする。
- 複数難易度Setの折り畳み表示は、代表となる難易度行の表示形態にした。
  展開前後で別の `#set` identityへ置き換えない。単一難易度の状態2は難易度行として表示する。
- Groupは一つずつ開閉する。閉じても再生対象のselection、metadata、score targetを変更しない。
  明示的な譜面選択はその親を開く。sort・同じ内容のlibrary refreshでは閉じた状態を保つ。
- 検索・Group種別変更・選択削除時の修復は既存Javaの方針に接続した。
  ゼロ件では再生対象を公開せず、検索解除でidentityを復元する。
  **これらの修復方針までstable完全互換と確認したわけではない。**
- BrowserのキーをCarousel・hover・press/release・描画snapshotに通した。
  キーは長さ付きSet IDと難易度identityで構成し、`#`を含むパスでも混同しない。
  アニメーション継承元のSetは別fieldで渡し、キー文字列を切り分けて推定しない。
- 同じ展開済みSet内の難易度変更では可視projectionの参照を維持する。
  Carouselを再生成せず、選択移動中のviewportや行座標を保持する。
  library refresh時は、値が等しい新metadataでも新しいオブジェクトを公開する。
- 非操作の26px区切り表示を、既存の `menu-button-background` providerを使うGroupカードへ置換した。
  カードに件数と開閉時の基本色を表示し、行と共有のclip/hit geometryでクリックできる。
  連続Groupにも通常の行pitchを割り当て、非Group→Groupに10単位の余白を足した。

`groupTargetKey` は直接操作したGroupへスクロールを合わせるための状態。
`06003277` のkeyboard focusと混同しないよう別名にしている。
今回Enterの分岐は変更していない。入力eventの順序・focus/確定/開始の分岐は次段階でまとめて扱う。

Rendererには引き続き描画snapshotとresident textureだけを渡す。
Import / Gameplay / Ruleset / GameClockは変更していない。

## 検証

`./gradlew build --offline --console=plain` 成功。
JUnit: **96 suites / 755 tests、failure・error・skipはいずれも0**（phase 1から14件追加）。

追加・更新した検証:

- 代表行・子行のidentity維持、状態0〜4、単一難易度。
- Group開閉と選択維持、別Groupを開いた際の以前のGroupの閉鎖。
- 検索ゼロ件→復元、Group種別変更、難易度並べ替え、削除・library refresh。
- `#`を含むパス、明示的なSet identityによる展開位置の継承。
- Groupのclipとhitの一致、実Screenクリック後の選択・再生遷移。
- 展開済みSet内でCarouselの行リストとscroll offsetが維持される既存回帰テスト。

旧テストの「Groupはクリック不可」「全Groupの子は常時可視」「Groupは小さい区切り」という期待値は、
今回確認した契約に合わせて更新した。機能不具合として見つかったCarousel再生成は実装側で修正した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=browser-contracts \
  -PsongSelectOutput=/tmp/osujava-browser-contracts-20260929
```

visual harnessにGroup開閉・別Groupへの切替を実Screenのクリック経路で行うケースを追加。
Artist / Creator / BPM / Length、全閉鎖、別Group展開、検索ゼロ件の7ケースを、
1280×720、1920×1080、1280×720の2倍framebuffer、1024×768で検証する。
画面を動かす入力検証ではGroup開閉が誤ってGameplayを開始しないことも確認する。

最終実行は **28 scenes / 28 PNG captures**、navigation/disposalチェックを含め成功。
Groupを開いた画面と全Groupを閉じた画面の代表PNGを目視確認した。
出力先は上記一時ディレクトリ、ログは `/tmp/osujava-browser-contracts-20260929.log`。
`git diff --check` も成功。

## 残る差分・次の実装単位

1. keyboard focus、全行配列を使うPage移動、同一代表グループ内外の確定、event順序とpointer ownership。
   現在の上下/左右キーは既存Javaのselection移動を維持し、Pageは可視譜面行ベースのまま。
2. Carouselの非表示行を含む座標・補間・全gap条件。今回の永続性はBrowser側であり、
   Rendererは可視projectionを使う。隠れた行の運動履歴までstableと同じではない。
3. Groupのhover等の色分岐、300ms遷移、正確なfont・寸法・件数ラベルのlocalisation、native hitbox。
   今回のカードは既存の描画基盤を使う。閉Groupの別色条件やfocus明度を推測で追加していない。
4. 譜面単位filterと全sort/group種類、`0400259b` の生成元、検索/再読込時の選択修復の完全対応。
5. phase 1記載のSkin失敗契約・HD eligibility・Back/cursor/sound/星の時系列。

stable実機との同条件pixel比較は未実施。Java側のテスト・PNG検証の成功を、1:1一致の証明とは扱わない。
