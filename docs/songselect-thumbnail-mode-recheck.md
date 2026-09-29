# Thumbnail / modeマークの再検査と修正

2026-09-29、調査開始時 `3bc6a2e`。ユーザー指摘のThumbnailの縦横比・サイズ、
中央／下部／譜面行のmodeマーク欠落を対象とする。stableは実行せず、ローカルの
`b20230727.9/osu!.exe` のILを再調査した。前回の「読み込める」「ハーネスが通る」だけでは
透明な画像による欠落を検出できていなかった。

## 原因と根拠

- JavaのThumbnailは `rowHeight × 115/85` で、画像の高さを行間隔そのものにしていた。
  さらに画像を行の矩形でclipしていた。行の当たり判定・間隔と画像の大きさは別の量である。
- stableの行生成 `06000fbf` は画像のVectorScaleに1.425、解像度に応じてその半分を設定する
  （IL `0412–043c`）。画像のOriginはCentreLeft、相対位置のTagは `(5.2, .25)`
  （`0441–0467`）。`06000fdc` の別画像ロード経路には `min(114 / width, 85.5 / height)` がある。
  この数値から4:3の画像領域を取り、480基準の行間隔48とは分離する。
  画像ロード経路ごとの全挙動やサーバーで作られるサムネイル画像は再現していない。
- [wiki](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection)の115×85は丸められた
  スキン向け寸法であり、行の高さで正規化する根拠にはならない。
- 同梱の `mode-osu/taiko/fruits` とそれらの `-small` は実ファイルが1×1。
  `-med` とmaniaのファイルも欠けていた。PNGを読み込めても可視アイコンにはならない。
- 中央マークはさらに背景を暗くする全画面レイヤーの下へ描いていた。
- 譜面行にはmodeを保持・描画する経路がなかった。stable `06000fbf` の
  `0523–0635` はnullable modeの0〜3に応じて画像を選び、CentreLeft、倍率.8のspriteを作り、
  行のforegroundリストへ追加する。画像名の暗号化された文字列は未復元。
- [Mode selectのwiki](https://osu.ppy.sh/wiki/en/Skinning/Interface#mode-select)は、中央の
  `mode-*.png`、下部の `mode-*-small.png`、selector内の `mode-*-med.png` を別用途としている。

## 変更

1. Thumbnail領域を114×85.5 SD、4:3として配置。720基準では106.875×80.15625。
   左位置は5.2の480基準、垂直中心は行中心から画面下へ.25の480基準。
   描画はcarousel viewportでclipし、72の行間隔・当たり判定・文字clipは保持する。
2. 横長・縦長のローカル背景を中央cropする際、整数pixelへの丸めをやめてUVで切り出す。
   奇数寸法・小画像でも描画時に縦横比を変えない。これはローカル背景を使うJava側の方針で、
   stableのオンラインthumbnail生成と同一という主張ではない。
3. 欠損mode familyと**同梱**1px placeholderには、独自の幾何図形から生成する白いアイコンを用意。
   全4mode・通常/small/medをカバーする。公式assetの抽出・転用は行っていない。
   現在skinや明示fallback skinの透明画像は作者の指定として尊重する。
   生成はロード時だけ。二重生成を避け、dispose時に一度だけ解放する。
4. 中央マークは背景の減光後、前景UIより前へ描く。下部とMode selectorも生成fallbackを共有する。
5. 譜面行へmodeを渡して表示。gradeと縦に並べ、表示列の幅も確保する。
   選択中は行の文字色を使い、白い行背景に白いアイコンを重ねない。
   各難易度のmodeを使い、複数modeが混在するSet全体を誤ってosu!standardと表示しない。
   rowには既存small familyを使う独立実装であり、stableの未復元画像名の特定とは区別する。

## 検証

- `./gradlew build --offline --console=plain`: 721 tests、failure/error/skipped各0。
- 回帰テスト: 画像UVの比率・中央位置、Thumbnail寸法・行hitboxの分離、mode/grade列の倍率、
  mode伝搬、4種類の生成画像が可視かつ相異なること、custom透明画像の維持、再生成・解放。
- `:lwjgl3:songSelectVisualHarness -PsongSelectPhase=thumbnail-mode`:
  **28 scenes / 52 PNG captures / 240 scripted transition frames**、navigation/disposalチェック成功。
  1280×720、1920×1080、1024×768、高DPI、横長・縦長・欠損背景、grade、Mode selector、透明skinを含む。
- `/tmp/osujava-recheck-thumbnail-mode/` の縦長4:3画面とgrade付き画面、
  `/tmp/osujava-recheck-wide/` の横長画面を目視確認した。
  PNGはJava単独の検証であり、stable実機とのpixel比較ではない。
- 全stable互換や、前回報告にあるその他の残件の解消は主張しない。
