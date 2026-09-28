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
