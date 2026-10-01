# Song Select parity: phase 10 — BPM・length・drain・mode検索

2026-09-29。[検索・metadata対応](songselect-parity-phase10-search-20260929.md)の続き。
専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始HEADは `b85461d16cdd5707091456c8485335dcff16f01d`。

## 変更

難易度ごとの`bpm`／`length`／`drain`比較と、`mode`の公開名称・prefix比較を追加した。
例えば`bpm=180 length>=126 drain=115 mode=o`の全条件を満たす難易度だけを残す。
検索後の代表行、Group件数、選択修復、元のdifficulty indexは前回の難易度検索経路を共用する。

`BeatmapTimingStatistics`はImportで作る不変のLibrary用データとした。
Gameplayのslider終端やRulesetの時刻計算を呼ばず、描画frameごとの再計算・ファイル読込を避ける。
保存済み`.osu`を再parseする既存のLibrary読込経路でも復元し、schema 1／2とも再import・index書換えは不要。
両方の`withAssets`が統計を引き継ぐ。ファイルのない自作chartではobject情報から休憩0の既定値を作る。

詳細欄のLengthは最初のobjectからの差ではなく曲頭からのLibrary終端へ修正した。
BPMは終端までの最小／最大に加えて、変速時には主なBPMも括弧内へ表示する。
既存の画面書式を使い、フォント・文言配置やModsによる表示補正の完了とはしない。

## 根拠と計算契約

b20230727.9、SHA-256 `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`を静的調査した。
公式asset抽出・保護回避・production service接続は行っていない。

公開の[Beatmap search](https://github.com/ppy/osu-wiki/blob/master/wiki/Beatmap_search/en.md)でfield名、
[.osu format](https://github.com/ppy/osu-wiki/blob/master/wiki/Client/File_formats/osu_(file_format)/en.md)でBreakとholdの形式、
[Interface](https://github.com/ppy/osu-wiki/blob/master/wiki/Client/Interface/en.md#beatmap-information)でBPM範囲と主なBPMの表示を確認した。
詳細な時間計算はwikiの一般的な長さの説明へ置き換えず、以下の対象buildの契約を用いた。

| IL | 確認した契約 |
| --- | --- |
| `060013fe` → `06003c96` | BPM検索は返り値Z、すなわち主なBPMを整数へRoundした値。最大BPMではない。 |
| `06003c96` | Library終端からtiming pointを逆走。赤線のbeat lengthが使われた整数msを同じbeat lengthごとに合算し、最長のものを選ぶ。同時間なら後方で先に見つかったtempoを保持。継承点は区間を分割せず、先頭点は0msまで延長する。終端より後の赤線は対象外。最小・最大・主なBPMはties-to-evenの整数丸め。 |
| `060013ff` | `040025cf / 1000`の整数除算後に比較。lengthは小数秒を保持しない。 |
| `06001400` | `Max(040025ab,040025aa)`を比較。 |
| `06003c77` `0e8f–0f84`、`1059–10d2` | 最初のobject開始時刻、最後のobjectの開始／終端をファイル順に走査。circle／sliderでは開始時刻を両方へ、spinnerでは開始と明示的な終端、holdでは明示的終端を両方へ入れる。lengthは最後の終端そのもの。drainは開始／終端それぞれから最初の開始と休憩合計を引き、1000で整数除算した2値の最大。 |
| 同 `0dbe–0ded` | Breakは終了−開始を合算する。重複区間のunionやobject範囲へのclipは行わない。 |
| `060013aa`、`06001401`、`06003c70` | modeは名称のprefixからenum値を求め、その値を比較する。空／未知名の変換結果はNaNで、.NETのCompareToでは有限のmode値より小さい。 |
| `060013ae`、`06003c98` | 詳細欄もLibrary終端と同じBPM統計を使用する。変速時の文字列へ最小・最大・主なBPMの3値を渡す。 |

例：最初が1,000ms、最後のcircleが66,999ms、休憩10,000msなら`length=66 drain=55`。
最後がsliderの場合もLibrary lengthはその開始時刻であり、Gameplayで解決したslider tailを足さない。
統計はGameplay用objectの時刻sortより前に保存するので、ソートによって最後のファイル行が変わらない。

modeの名称は公開wikiの`osu / taiko / catch / mania`とそのprefixを実装した。
`0600136f`の辞書は8項目あり、残りのalias文字列は未確定。mode conversionも未対応のため、現在は保存modeを比較する。
壊れたhold／Breakは読込を停止せず無視する。非有限・非正のbeat length、逆転した休憩、整数overflow等の異常入力までnativeと同じ壊れ方にするものではない。

調査dump：`/tmp/osujava-phase10-{timing,parser,ordering,group-factory,details}.il`、
`/tmp/osujava-phase10-{timing,group}-xrefs.txt`。

## 検証

関連テストと`./gradlew build --offline --console=plain`成功。
**112 suites / 1,138 tests、failure・error・skip 0**（62件追加）。

- 主なBPMと最大BPMの区別、同じtempoの複数区間合算、同時間tie、継承点、終端後の赤線、整数丸め。
- circle／slider／spinner／hold終端、整数秒の境界、重複／範囲外の休憩、ファイル順とGameplay用sortの分離。
- mode名称・prefix・enum比較、空／未知名、数値検索のANDと各比較演算子。
- 自作OSZのImport → 保存 → 再読込 → 統計一致と検索。schema 1／2のload時にindex byteが変化しないこと。
- 数値条件で難易度を除外した後のGroup件数、元のindex、0件、mode切替、検索解除の選択復元。
- 詳細欄の曲頭基準のLength、BPM範囲と主なBPM。既存のGameplay／Import安全性回帰も全件通過。

`search-contracts`描画harnessは **8 scenes / 192 PNG / 2,880 scripted frames** 成功。
1280×720／1920×1080／1280×720 framebuffer 2倍／1024×768 × Groupなし／Artist Group。
従来の文字・AR/CS検索へ、BPM/length/drain/modeの複合検索、単一難易度、modeによる0件を追加した。
全frameで除外行、件数、元の難易度番号、geometryを確認。代表の単一難易度と1024×768 Group captureを目視確認した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=search-contracts -PsongSelectOutput=/tmp/osujava-phase10-timing-20260929
```

ログは`/tmp/osujava-phase10-timing-{tests,build,harness}.log`。
stableの正常Song Select参照実行は引き続き未確保。同条件のnative pixel/audio比較は未実施。

## 次の分類対応に渡す結果

今回の変更は検索・統計・詳細欄。Sort／Groupはまだ既存のSet単位近似を使っている。
単に検索の主なBPMを全用途に流用すると別の差異が生じるため、分類は次の単位で対応する。

- `06003288`のBPM Sortは`06003c95`の丸め前の最大BPMを使う。検索用の主なBPMとは別契約。
- 同Length SortはLibrary終端を1000で整数除算して比較する。
- `06003282` `037d–0465`、`060032b0`、`0600329b/329c`のLength Groupは
  `[0,1)`／`[1,2)`／`[2,3)`／`[3,4)`／`[4,5)`／`[5,10)`／`[10,∞)`分。
  既存Javaの2／4／6／10分境界とは異なる。
- Group構築は難易度に対するFindAllを使う。同じSetの難易度が異なる区間に入る場合の親Group・代表行・familyを揃える必要がある。
- BPM Groupの公開wiki記述は60刻み。既存Javaの50刻みを修正する際は対象buildのpredicateも照合する。

その他のphase 10残りは[台帳](songselect-parity-roadmap-20260929.md)を参照。
