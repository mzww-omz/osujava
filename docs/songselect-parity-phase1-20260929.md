# Song Select互換改修 第1段階（2026-09-29）

基準commit: `aaeb55c3e640bc5caf9239d4c47b6fe003f01d9f`。
[全体再調査](songselect-parity-reinvestigation-20260929.md)と[Skin独立調査](skin-stable-independent-audit-20260929.md)を基に、
根拠が確定した設定・素材選択・静的表示から改修を開始した。
ここでの完了は下記の実装単位に限る。Song Select全体の1:1再現完了ではない。

## 実装した変更

| 対象 | 変更後の動作 | 根拠 |
| --- | --- | --- |
| skin.ini | section/keyのcaseを区別。同じsection内では最初のキーを保持し、最初が不正値でも後の重複値へ置き換えない | Skin調査§4、`06005152/5154/5156` |
| 設定の型変換 | booleanはtrue/falseと32bit整数（0=false、非0=true）を受理。区切りはコロン。Coloursは3/4成分を受理し、第4成分は無視 | `06001f80/5158/51a7` |
| CursorTrailRotate | 省略時false。明示した値は引き続き有効 | Skin調査§4、`060051a2/5172/2b02` |
| 画像の読み込み元 | `AssetFile`にCUSTOM / FALLBACK / BUNDLEDを保持。指定providerだけで探索するAPIを追加 | Skin調査§1/5 |
| cursor / middle | middleは解決済みcursorのprovider内だけで探索。cursorなしならmiddleを読まない。trailは独立解決 | `06002ac0` |
| normal / hover | current normalの有無に関わらず、hoverを独立解決 | `0600136e` |
| top / bottom | 他のcurrent画像の有無に関わらず独立解決。`authoredSurface`と非表示の`topLayoutFallback`を削除 | Skin調査§3 |
| selection配置 | Version > 1に加え、Mods normalがBUNDLEDなら新anchor。画像の解決後にgeometryを測るため、先にロードされたModeにも適用 | Skin調査§2、`0600136e` |
| topの延長域 | 配置予約も描画と同じ最後の物理1列を参照。最後の20列から見えない予約を作る不整合を解消 | Skin調査のJava再現結果 |
| 星表示 | 既知の評価では背景10枠、前景を最大10まで充填。9.25は10個目を25%表示。数値は10以上も保持。未取得の評価を0として表示しない | 全体調査§4、`06000fd2/0fbd` |

主な実装箇所:

- [SkinConfiguration](../core/src/main/java/dev/osujava/skin/SkinConfiguration.java)
- [SkinAssetResolver](../core/src/main/java/dev/osujava/skin/SkinAssetResolver.java)
- [SongSelectSkinAssets](../core/src/main/java/dev/osujava/skin/SongSelectSkinAssets.java)
- [SongSelectTopCoverage](../core/src/main/java/dev/osujava/skin/SongSelectTopCoverage.java)
- [SongSelectRowPresentation](../core/src/main/java/dev/osujava/ui/SongSelectRowPresentation.java)

## 設計判断と残る差

providerをbooleanだけで扱う構造は改めたが、現在のローカルproviderを明示する最小の変更に留めた。
FALLBACKは明示された別のローカルディレクトリであり、stableの譜面providerではない。
BUNDLEDをstableのbuilt-inに相当する分岐へ対応させたが、両者の画像素材が同一という意味ではない。
既存のboolean constructorと`fallback()`は呼出側との互換用に維持した。

選択Skinの設定と画像の読み込み元は分離している。例えばVersion 1のままModsがBUNDLEDへ落ちた場合、
anchorだけが新式になり、Version 2.2のthumbnail条件へ勝手に変わることはない。
全静的画像を解決してからgeometryを計測し、追加Back frameが静的素材のメモリ予算を先に消費しない順序も維持した。

共有parserの修正はGameplayにも適用される。大文字小文字を誤ったキー、`=`による代入、重複キーの後勝ちに
依存したiniは従来と動作が変わる。Windows由来のファイル名のcase-insensitive探索は別の責務として維持している。
範囲外RGBをbyteへwrapする挙動は導入せず、従来の不正値拒否を維持した。
重複section、encoding、特殊RawName、ini全体の読込失敗時の扱いはまだ完全対応ではない。

次の変更は今回の完了範囲に含めない。

| 項目 | 次の作業 |
| --- | --- |
| SkinのHD・失敗契約 | display/optionの設定元を確定してHD eligibilityへ接続。存在する壊れたHDとmissingを分ける。現行のdecode失敗時fallbackは引き続き残る |
| selectionのglobal条件 | `06002ac8`のglobal flagと特別RawNameを追加対応。今回のanchor修正は通常Versionとbuilt-in画像の分岐 |
| 操作領域・合成 | sprite dispatcher、clip、透明時の操作、同depthの順序を閉じ、alpha閾値・固定slot・予約上限を置き換える |
| Back / cursor / sound | native Back、二層の時間、cursor設定ownerの全分岐、音の別名候補と時刻 |
| 星 | 状態別の出現・中断animation、時間差、正確な寸法・位置・色・数値表示。今回の10枠は静的表示の修正。狭幅時の圧縮・数値優先は残る |
| Browser / Carousel | 永続行identity・親子Group・状態0〜4・focusを導入し、可視行projectionと運動モデルを順に置換 |
| 入力 / metadata / preview / font | 全体調査で示した時系列・情報保持・文字測定の契約を閉じて実装 |

これらの残差を隠すための定数調整や、アプリ全体のrenderer置換は行っていない。
完全ローカル、通常Input API、Import / Gameplay / Renderer / Ruleset / GameClockの責務分離を維持している。
公式assetの抽出、stableの改変・保護回避・production serviceへの接続は行っていない。

## 検証

`./gradlew build --offline --console=plain`成功。
最終実装のJUnitは**95 suites / 741 tests、failure・error・skipはすべて0**。
基準時723件から18件増加。今回は実際に再実行した。

設定のcase/重複/boolean/RGBA、既定trail回転と明示回転、provider指定、cursor/middle、
通常画像とhoverの別provider、Version 1とMods provider、Texture所有権・破棄、
SD/HDの最終列予約、10枠・第10星の部分表示を検証した。
従来の独自仕様を期待していたテストとvisual harnessのassertionは根拠に合わせて更新した。

[SongSelectVisualHarness](../lwjgl3/src/hudHarness/java/dev/osujava/ui/SongSelectVisualHarness.java)に
`skin-contracts`と`star-contracts`の実行対象を追加した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=skin-contracts \
  -PsongSelectOutput=/tmp/osujava-skin-contracts-20260929

xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=star-contracts \
  -PsongSelectOutput=/tmp/osujava-star-contracts-20260929
```

- Skin: 6ケース×4条件 = **24画面 / 24 PNG**。通常画像のみ（fallbackあり/なし）、Version 1のMods有無、透明素材、同梱chrome。
- Stars: 4ケース×4条件 = **16画面 / 16 PNG**。評価未取得、0.65、9.25、12.84。
- 条件: 1280×720、1920×1080、1280×720の2倍framebuffer、1024×768。
- いずれもJavaの実PNG decode・OpenGL描画、navigation/disposalチェックを通過。代表画像も目視確認した。
- 出力は上記一時ディレクトリ、ログはそれぞれ同名の`.log`。fixture生成と検証コードはrepositoryに保存。
- `git diff --check`成功。stable実行やstableとのpixel/audio差分は未実施。
  **40画面の成功はJava側の回帰検証であり、stable実機との1:1一致を認定したものではない。**

## 実装commit

| SHA | message |
| --- | --- |
| `61f03bb` | Align skin.ini parsing and cursor defaults with stable |
| `dcf9bad` | Resolve Song Select skin parts by their stable provider contracts |
| `a1ec790` | Render ten Song Select star slots and preserve fractional ratings |
