# Song Select 静的比較に基づく修正（2026-09-29）

> 後続のユーザー指摘による[Thumbnail・mode再検査](songselect-thumbnail-mode-recheck.md)で、
> Thumbnail倍率・mode画像の欠落と描画経路を追加修正している。

対象: `b7733bd` からの変更。stable は起動できていないため、以下は Java の不具合修正と
静的根拠がある互換処理の実装結果であり、stable との実画面一致の認定ではない。
計画全体の完了ではない。設定・表示・入力・プレビューの修正を実装し、未確定の互換契約と
機能拡張は末尾の残件として区別する。

## 根拠

- ローカル `b20230727.9/osu!.exe` の SHA-256:
  `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
- 解析はメタデータと IL の読み取り。公式画像・音源の抽出や保護回避、stable の実行はしていない。
- [skin.ini](https://osu.ppy.sh/wiki/en/Skinning/skin.ini)、
  [Interface](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection)、
  [Sounds](https://osu.ppy.sh/wiki/en/Skinning/Sounds)、
  [.osu General / Timing points](https://osu.ppy.sh/wiki/en/Client/File_formats/osu_(file_format))。
- 既存の[監査](songselect-skin-remaining-audit.md)と
  [先行実装](songselect-visual-gap-implementation.md)は過去時点の記録。
  それらの「未実装」をそのまま現在の状態と解釈しない。

追加の静的追跡で意味を確認できた箇所:

| 項目 | MethodDef / FieldDef | 確認した内容 |
|---|---|---|
| selection hover | `0600136e`, `060040b9`, `060040ba` | normal は白。hover は alpha `.01 → 1`、100ms。反転時は現在値を始点にし、期間は100ms |
| transform | `060042ec`, `060042f1`, `06002b54` | transform の種類が Fade、期間が終了−開始、easing 0 は線形 |
| Back | `06003a15`, `0400284a`, `060040b1`, `06002c2b`, `06002c2c` | 同じ連番の通常層と加算層。加算層の初期 alpha .01、hover変換は1/255〜.4・250ms。加算はSRC_ALPHA/ONE |
| ordinary version branch | `06002ac8` | Version > 1.0 の分岐。別のglobal条件・名前による特例・provider分岐は同一の根拠で解決した扱いにしない |
| HD gate（未実装） | `06002340`, `06002589` | height>=800 またはbool、別boolがfalse、GPU max texture size>2048の組合せ。boolの設定名は未確定 |
| top extension（未実装） | `0600136e` IL `1b85–1c2c` | 1365列目・幅1のcropを使う追加sprite。画面座標との最終対応は未確定 |

## 実装済み

| 変更 | 内容と検証範囲 |
|---|---|
| 設定の所有者 | Song Select / Gameplay が選択skinの設定を共有。iniなしの選択skinにfallbackのVersion/Cursor設定を混ぜない。完全な同梱フォントfallbackでは同梱のoverlapを保持 |
| User・大小文字 | `User` はVersionをlatest扱いとし他の設定は保持。ini、PNG、音源、入れ子のfont prefixはWindows由来の大小文字を解決。完全一致を優先し、case-insensitive衝突は辞書順で決定 |
| 星評価 | gradeで文字領域が狭くなっても評価を消さない。必要な星数だけを配置し、さらに狭い場合は数値を残す。9枠上限、trusted rating、Version 2.2の部分星表現は継続 |
| タブ | 狭い4:3では4枚、それ以外は5枚。隠れた分類もdropdownで選べる。画像の比率を保持し、透明画像上でも文字を判別できる縁取りを追加 |
| hover | selectionの100ms線形alpha、Backの250ms加算層を実装。反転は現在値から。時間更新はupdateに置き、drawではsnapshotのみ参照 |
| 小数座標 | ILの480基準57.6/48をSDの92.16/76.8へ換算し、旧配置上端を86.4に修正。PNG推奨寸法と配置幅を分離し、Options開始位置469.76 SDを回帰検証 |
| Version境界 | 1.1〜1.9を旧配置扱いしていた `<2` を修正。通常の旧配置条件は `<=1`。thumbnailの2.2条件とは独立 |
| 描画範囲 | 行背景の影・余白はcarousel viewportまで描き、文字・thumbnailは行clipを維持。ランキングも同じbody基準で背景を配置。top/bottomの40%/30%描画制限を外す |
| リソース予算 | Backのframe zeroと他の静止asset・chromeを先に確保し、追加Back frameを後に読む。失敗時は連番を打ち切り、先頭frameを二重確保しない |
| カーソル入力 | 時刻付きの移動・左右button edgeを有界queueで渡す。frame間の短いクリックと左保持中の右押下を失わない。pollingで最終状態を補正。描画側からInput/Gameplay状態を書き換えない |
| Unicode検索 | UTF-16の高低surrogateを一つの検索変更として反映。未完成文字で絞込み・入力音を発生させない。上限80 code pointとcode point単位の削除を維持 |
| 曲プレビュー | `PreviewTime`をparse、asset解決、ライブラリ再読込で保持。ローカル音源を専用Musicで再生。同じ音源への難易度変更は再開・seekしない。音量、退出fade、hide/dispose、音源障害に対応 |
| 拍同期 | 再生位置に対する直前の非継承TimingPointとoffsetを使う。BPM変更を反映し、継承点は拍周期に使わない。中央装飾とCookieは同じ拍を参照。粒子移動・Back連番・検索caretはUI時刻を維持 |

設計上の独自方針:

- タブ4/5枚の切替を4:3に置くこと、星の狭幅時のpitch・数値優先、縁取りはJava側の可読性修正。
  stable の厳密な切替点・文字描画と同一とはしていない。
- 大きなchromeの描画を許可しても、ブラウザーの最低操作領域を確保する予約上限は維持する。
- `PreviewTime=-1` は先頭から再生。stableの未指定時の選定位置、切替待ち時間・crossfadeの推定は実装しない。
- 拍の波形・装飾量は既存Java表現。音楽に同期したこととstableの装飾再現は別。
- ファイル探索の決定性、queue 2048件、64MiPixel等の予算はローカルアプリの制約。

## 検証

各製品変更の後に `./gradlew build --offline --console=plain` を実行。最終製品状態では
**715 tests、failure/error/skipped 各0**。主な追加回帰:

- ini所有者、User、大小文字、同梱font metric。
- grade前後の評価表示幅、4:3のタブとdropdown。
- 100/250msの端点・途中反転・複数frame rate・描画snapshot。
- PreviewTimeのasset解決とライブラリ復元、同一音源継続、seek、音量、二重dispose防止、故障時の再試行抑制。
- timing offset・途中BPM・継承点、補助漢字・80文字境界。
- 1frame内の左右押下・解放、poll補正、逆順timestamp。
- 長いBack連番とcursorのロード順、先頭frame一回確保、Version1/1.1/1.5/2/2.2/2.7。

Xvfb / OpenGL の確認:

- 1024×768 configured画面（タブ4枚）: `/tmp/osujava-implemented-skin-4x3/`。
- 複合画像: `/tmp/osujava-implemented-composite/`。PNGを目視確認。
- 複数解像度・高DPI・transparent/broken/composite・navigationの回帰:
  `/tmp/osujava-implemented-regression/`、ログ `/tmp/osujava-implemented-regression.log`。
  **111 scenes / 339 PNG captures / 2,313 scripted transition frames + navigation/disposal checks passed**。
  この一式は`1d6419b`で実行。後続の小数座標修正は715件の全テストと
  1024×768の`phase5a-current-mode-pressed`を追加実行し、1 scene / 1 PNG + navigation/disposalを確認
  （`/tmp/osujava-implemented-coordinates/`）。

音声はfake MusicによるAPI/lifecycle検証であり、デバイスからの聴取試験ではない。
PNGはJava単独の描画結果であり、stableとのpixel差分ではない。
`/tmp` の成果物はこの環境でのみ参照でき、永続的なgolden画像としては扱わない。

## 残件（未完了）

| 計画上の領域 | 現状と必要な次の作業 |
|---|---|
| ロード契約 | HD自動選択、provider由来の旧/新配置、特殊skin名、normal/hover・cursor familyのfallback条件。未確定のbool・名前・provider判定を静的に追跡する |
| 精密な画像配置 | 1365列のtop延長、thumbnail115×85+9の最終scale、grade寸法、font baseline、兄弟行減光率。現行は一部独自値 |
| alphaと複合画像 | 16/160のhitbox閾値、bodyの160/35%検出、Back frame zeroのhitbox、current存在によるchrome fallback抑制、補助UIの移動閾値。描画clip修正だけでは解消しない |
| 入力・音 | カーソル倍率設定/reload、overlay・score・Cookieのcue接続、Backの二候補の音名。カーソルイベントqueueはUIボタン全体のpointer ownership再設計ではない |
| 曲・装飾 | 未指定PreviewTime、音源切替のdelay/crossfade、再生終端、背景crossfade/フォント、stableの装飾波形。preview不正位置をcodecが受理した際の挙動は実音源検証が必要 |
| assetとスコア | 全Mod画像、銀S/SSの条件、ローカルランキングの全表現・replay、original-language/thumbnailユーザー設定 |
| 独立した機能拡張 | collection、数値検索、sort/group追加、Options、矢印循環、stable型scroll/motion、通常Mods、他ruleset。表示だけ追加して実機能対応済みとはしない |

オンラインランキング・Bancho・公式APIは対象外。残件のうちコードから確定可能なものは
静的追跡で進め、実測が必要な事項は未確認のまま明示する。回帰テストの合格を根拠に
未実装機能や未知のstable互換性を完了扱いにはしない。

## 製品commit

- `4495752` — Fix skin configuration ownership and Windows filename compatibility
- `04b5546` — Preserve song ratings and skin tab readability in narrow layouts
- `e0b37f9` — Match statically verified selection and additive Back hover fades
- `e85205d` — Play local song previews and synchronize menu pulses to timing points
- `44cb500` — Preserve menu cursor input edges and atomic Unicode search input
- `eaa9566` — Preserve skin artwork bounds and reserve static assets before animation frames
- `1d6419b` — Use the verified Version greater than 1 selection anchor boundary
- `3e46558` — Retain fractional stable selection spacing and legacy anchor coordinates
