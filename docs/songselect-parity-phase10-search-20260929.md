# Song Select parity: phase 10 — 難易度単位の検索とmetadata保持

2026-09-29。phase 9の描画対応に続き、phase 10の最初の実装単位として、
Import → Library永続化 → 難易度index → 検索 → 行projectionをまとめて対応した。
専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始時HEADは `d7f7f28c27121de6aff272a1c3c0a38894f47824`。

## 問題と変更後の動作

従来はSet内の全難易度の文字を一つの検索対象へまとめていた。
そのためEasyにだけ含まれる語とHardにだけ含まれる語を同時に検索してもSet全体が一致し、
難易度別に除外・代表行再構築・件数更新できなかった。
Tags／Sourceはparserで読み捨てられ、Unicode情報も難易度には保持されていなかった。

現在は各難易度が全検索語を満たす場合だけ一致する。
例えば`difficulty=easy ar>=9`は、EasyがAR 5でHardがAR 9のSetに一致しない。
`difficulty="very hard"`は難易度名の連続した部分文字列を検索する。
残った難易度が一つならsingleton表示になり、元のdifficulty indexのままクリック・Gameplay要求へ渡す。
難易度リストを切り詰めて別のSetを作ることはせず、元の譜面・score identity・asset参照を維持する。

## 根拠

参照binaryはb20230727.9、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
公式asset抽出・保護回避・production service接続は行っていない。
検索field名は公開wikiの候補と`06003a12`のFNV-1a switch値を照合し、分岐先のILを確認した。
保護された文字列decoderを実行して判定したものではない。

公開の[osu! wiki：Beatmap search](https://github.com/ppy/osu-wiki/blob/master/wiki/Beatmap_search/en.md)で
検索対象・field名・演算子・正の譜面ID検索を確認し、
[.osu format](https://github.com/ppy/osu-wiki/blob/master/wiki/Client/File_formats/osu_(file_format)/en.md#metadata)で
Unicode、Source、Tags、BeatmapID、BeatmapSetIDの保存契約を確認した。
wikiのwebsite／lazer専用構文をstableへ無条件に導入していない。

| 根拠 | 確認した契約 |
| --- | --- |
| `060013a3` `014d–01de` | queryをtoken化し、各tokenのpredicateを各行の譜面へ適用する。一度除外した行は以降skipするため、同じ難易度が全tokenを満たす必要がある。 |
| 同 `01df` → `06003257` | 検索後に代表行を再構築。除外行は代表参照をnull、状態をhiddenへ。残る最初の譜面が代表になり、同じfamilyの残数に応じてsingleton／collapsedを分ける。family自体をローカルSetとする既存近似は残る。 |
| `060013a6`、`060013e1`、`0600141f` | ASCII spaceで区切る。全体の引用符数を偶数へ切り下げ、未消費の引用符枠があり、直前が`=`または引用中なら引用符を除去して状態を反転する。一般的な独立`"phrase"`検索とは異なる。 |
| `060013a7` `0040–00af` | field／operator／valueを分離し、fieldとvalueを小文字化。`==`を`=`へ正規化する。 |
| `06001405–1408` | creator／artist／title／difficultyはContains。Containsの結果と`operator == '='`を比較するので、他の比較演算子も否定になる。artistはRomanised／Unicodeのどちらにも一致し、title fieldはRomanisedだけを対象とする。 |
| `060013a9`、`06003cb2` | 全文検索は行ごとの検索文字列・creator・tags・source・Unicode情報へcase-insensitiveなIndexOfを使う。今回、独立した難易度documentへ対応する情報を保持した。 |
| `06001411` | 整数tokenは譜面のID群との一致、または全文一致。今回の対応は`.osu`から得た正のBeatmapID／BeatmapSetID。未取得IDを生成しない。 |
| `060013f8–13fb` | cs／hp／od／arはsingle precisionの値をdoubleへ変換し、小数1桁へRoundして比較する。`04000a94–a97`のfield signatureは`06 0c`（float32）。AR／CSはtaikoとmaniaで常にfalseとなり、`!=`も例外ではない。 |
| `060013a8` | 数値比較は`= / != / < / > / <= / >=`。Math.Roundのties-to-evenを維持する。 |

引用の例：`title="one two"`は一つのtoken、`"one two"`は引用符を含む二つのtoken。
閉じていない`title="one two`も二つに分かれる。tabや全角spaceは区切りへ拡張しない。
数値の例：保存値9.15をfloat32へ変換して小数1桁へ丸めると9.1となり、`ar=9.1`へ一致する。

調査dumpは `/tmp/osujava-phase10-{search,predicates,field-match,row-index}.il` と
`/tmp/osujava-phase10-index-xrefs.txt`。

## 実装と責務

`BeatmapMetadata`に追加のUnicode／Source／Tags／IDを保持し、`BeatmapDifficulty.withAssets`の両経路で引き継ぐ。
parserが元の`.osu`から読み、Libraryのschema 2が難易度ごとに保存する。
既存schema 1は読込時に保存済み`.osu`から不足情報を補うため、再importは不要。
loadではindexを書き換えず、通常のsave時に既存のatomic replacementでschema 2を保存する。
schema 2のmetadataは他の既存metadataと同様にindexを優先する。未対応の将来schemaは引き続き拒否する。
欠損譜面・壊れたindex・危険パスの扱いは既存の読込境界を維持する。

`SongBrowserQuery`はI/OもGLも持たず、難易度documentとquery predicateを扱う。
全文対象をLibrary更新時に正規化し、queryは再構築時だけcompileする。
Sceneの描画frameごとに`.osu`を読み直したり、ratingを計算したりしない。

Browserは元のSetを保持したまま、一致する難易度のmapを使って除外flag、代表行、singleton、Group件数を更新する。
除外された行も永続Rowとして残し、query解除時に同じidentityへ戻す。
選択難易度が除外された場合は同Setの最初の一致難易度へ、Set全体が消えた場合は最初の一致Setへ修復する。
これは既存のJava側選択修復方針への接続であり、stableの近い星評価による選択規則まで完成したものではない。
Random／Setクリックは最初の一致難易度を選び、履歴は除外中の難易度を飛ばして保持する。
Screenの一時的なindexは元のSetのindexのままで、検索結果の0番目へ振り直さない。

## 検証

関連テスト、`./gradlew build --offline --console=plain`成功。
**111 suites / 1,076 tests、failure・error・skipは0**。前回から85件追加。

- metadataのparser → asset解決 → archive import → 永続化 → 再起動 → 検索。
- schema 1からの不足情報補完、load前後のindex byte一致、次回saveでschema 2化。
- schema 2のmetadata保持と未対応schema拒否。既存の破損／危険archive／path回帰も全件実行。
- ANDを別難易度間で満たさないこと、Source／Tags／Unicode／正のID、未知fieldのliteral fallback。
- 引用符の偶奇・開閉・隣接、ASCII spaceとtab／全角space、case／NFC。
- 文字fieldの部分一致・否定、数値演算子、float32丸め、modeによるAR／CS除外。
- 除外された先頭行の代わりに代表を選ぶ、singleton化、Group件数、行identity再利用。
- Random・履歴・検索解除・元のdifficulty index保持、Screenで検索結果をクリックした際の選択と背景。

`search-contracts`描画harnessは **8 scenes / 120 PNG / 1,800 scripted frames** 成功。
1280×720／1920×1080／1280×720 framebuffer 2倍／1024×768 × Groupなし／Artist Group。
引用検索による各Set一難易度、Artistと難易度否定の組合せ、数値比較、0件、解除の5段階を実行し、
全frameで除外行が描画されないこと、Group件数、行geometry、元の難易度番号とlabel、0件時のplayable selectionを検証した。
各段階の0／8／44frameをcaptureし、singletonとGroup表示を目視確認した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=search-contracts -PsongSelectOutput=/tmp/osujava-phase10-search-20260929
```

ログ：`/tmp/osujava-phase10-{tests,build,search-harness}.log`。

## 残りと次の対象

後続の[時間統計・mode対応](songselect-parity-phase10-timing-20260929.md)で、以下のBPM／length／drainと公開mode名称の検索を実装した。
この節は最初の実装単位の終了時点の記録であり、現在の残作業は[台帳](songselect-parity-roadmap-20260929.md)を参照。

phase 10全体は継続中。次はsort／groupの分類条件と、残る検索fieldのデータ供給を対応する。

- BPM／length／drain／stars／key／mode／status／played／unplayed／speed、日付、rank、collection／favourite、metadata／score。
- 数値suffix・指数表記等のRegex境界、IDの0／負数・native第3ID、culture-sensitive比較と特殊な文字列結合。
- mode conversion後の値、Modsと評価供給、近い難易度を選ぶ規則、同family判定の正確なキー。
- 検索の300ms待機・Searching表示・実行順、選択修復／検索解除のnativeとの厳密対応、文字編集repeat／IME。
- Unicodeの表示設定と字体・測定はphase 11。今回の保持・検索対応だけで表示切替まで完了とはしない。

現在のFIELD／NUMBER parserは確認した通常構文を実装しており、native Regexの異常入力を含む全境界一致を主張しない。
全文比較もNFC＋Locale.ROOTによる既存Java方針を使用している。
stableの正常Song Select参照実行は未確保であり、Java側captureだけでnativeのpixel/audio一致と判定しない。
