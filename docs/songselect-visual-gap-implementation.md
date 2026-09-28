# Song Select 視覚差分の実装計画

対象: `songselect-skin-remaining-audit.md` のV項目。2026-09-28。
完全ローカルのまま、既存スキンと描画責務を維持する。

1. 未接続asset（selection-tab、Modeの三用途、Modアイコン、star2）を既存resolverへ接続。
   正規のprovider、SD/HD、透明画像、破損fallback、disposeの契約を引き継ぐ。
2. Group/Sortの見出しと選択値を分離し、利用できる分類をタブから操作可能にする。
   検索・dropdown・carouselの領域を重ねず、描画とhitboxを共有する。
3. 中央Mode、下部Mode、Mode/Mods overlayの画像、星装飾、スクロール位置表示を追加。
   未実装ruleset/Modを有効化したようには見せない。
4. ランキング行にもスキン背景を使い、ローカルの順位／自己ベスト／空状態を表示。
   黒文字＋暗い同梱fallback行の可読性を、作者の色指定を変更せず改善する。
5. 関連回帰テスト、Gradle build、Xvfb画面検証と画像目視、diff確認、論理単位commit。

根拠: 既存内部調査、osu! wikiの[画面構成](https://osu.ppy.sh/wiki/en/Client/Interface#song-select)、
[部品仕様](https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection)。
中央ModeのBPM同期はローカル譜面のtimingを使用する視覚表現とし、プレビュー音源の実装と区別する。
分類タブは現在利用可能な分類を表示する。Collections等の永続化機能を見せかけだけ追加しない。
ネットワーク由来profile/ランキング、通常Modsと他ruleset、正確なstableフォント、
未計測のalpha hitbox・二層Back・fallback例外・スクロール物理は別の実装単位に残す。
公式assetの抽出は行わず、既存同梱およびユーザースキンのみを使う。

## 実装結果

- `selection-tab`で、All/Artist/Creator/BPM/Lengthの実際に使える分類を操作できる。
  Group/Sortの見出しと選択値を分離。dropdownはタブより入力を優先し、hover表示／音と
  dropdown間移動時のexpand音を接続。検索欄を下へ移し、一覧の上端も同時に調整した。
- 中央Mode、下部Mode、Mode selector、12種のMod画像、`star2`を接続。
  Mode中サイズ画像が欠ける場合は通常Mode画像を利用する。透明な画像は欠損とみなさない。
  中央とsmall Mode、粒子は加算合成、Modとmedium Modeは通常合成を使う。
- 右端のスクロール位置表示を追加。既存carouselのoffset/rangeを描画するだけで、
  別のスクロール状態は持たない。ドラッグ可能なscrollbarではない。
- ランキング行に`menu-button-background`、順位、自己ベスト要約、空状態パネルを追加。
  ローカル保存スコアだけを表示し、ユーザーやオンライン記録は生成しない。
- 暗い同梱行画像＋暗い選択文字色の組合せでは、選択行の背景を白く薄める。
  選択skinの色指定を変更せず、current/configured fallbackの作者画像には適用しない。
- メタデータはartist - title [difficulty] / Mapped by creatorに整理。
  検索caretは点滅する。Unicode原語切替と代表BPMの算出は未対応。

表示の原点・大きさ・読み込みと操作可能性を区別した。Mode/Mods画像が表示されても、
他rulesetと通常Modは引き続き無効である。全てのstable機能を再現したものではない。

中央Modeのpulseは最初の有効なuninherited timingの周期とメニュー経過時間を使用する。
実音源の再生位置・PreviewTime・変速区間とは同期していない。粒子の速度・分布・alphaも
独立した近似であり、stableの数値を計測したものではない。
分類タブの内容、淡色fallback処理、scroll thumb寸法もローカル設計として明示する。

## 検証

- Gradle buildと全690テストを実行し成功。追加した回帰はタブと検索／一覧の非重複、
  dropdown優先順位、scroll端点、fallback可読性の適用範囲、追加assetのSD/HD・所有権。
- 配置変更に伴い、画面テストの一覧上端を658から636へ更新。
  選択の画面上の基準点は390のまま維持し、判定自体を緩めていない。
- Xvfb個別検証: custom、custom Mods、custom Mode（4:3/2x）、
  スコア選択、複合画像（2x）、透明画像（4:3）。出力は
  `/tmp/osujava-visual-gaps-{custom,custom-mods,custom-mode,scores,composite,transparent}`。
  custom画像は単色の診断用に生成し、公式assetを抽出していない。
- スコア選択検証には60遷移フレームと7枚のキャプチャを含む。
  custom通常／Mods、スコア一覧を目視し、画像のproviderと文字コントラストを確認した。
- foundation一括検証は途中でexit 143になった。完走結果はなく、全76シーン成功とは扱わない。
  上記個別シーンとunit/buildの成功を今回の検証範囲とする。

## 残る差分

V01/V02/V07/V08/V12/V16に描画経路を追加したが、タブ内容と効果の精密互換は未完。
V04/V06/V13/V14/V17は部分対応。V03、原語とフォント（V04/V05）、
背景切替（V09）、行画像フィット／星評価／銀grade（V10/V11）、
Beatmap Options（V18）、プロフィール（V19）、Cookie同期（V20）、
Back二層・hover／重なり順（V21/V22）は残る。
S02/S03/S12等の設定・読み込み契約も、この表示実装では変更していない。
残差監査の過去の「未接続」記述は実装前時点の記録として保持する。
