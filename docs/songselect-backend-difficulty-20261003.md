# B05: ローカルDifficulty backend（2026-10-03）

## 数値計算の根拠と範囲

参照は公開lazer tag `2023.815.0`、commit `4e96853c7543f80a1b822ccd381943c7377543d7`、
`OsuDifficultyCalculator.Version=20220902` に固定する。
[calculator](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/OsuDifficultyCalculator.cs)、
[preprocessing](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty/Preprocessing/OsuDifficultyHitObject.cs)、
[stacking](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapProcessor.cs)、
[skills/evaluators](https://github.com/ppy/osu/tree/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Difficulty)
を根拠に独立実装する。現行2026のalgorithm、またはstableの全譜面との同値は主張しない。

初期対応は **NM、mode 0、format v6以降、circle/spinnerのみ、CS/AR/OD 0–10、StackLeniency 0–1**。
Slider・他mode・pre-v6・未検証の範囲はUNSUPPORTEDとして数値を供給しない。
計算済み空chartはSUCCESSの0星、1object chartはreferenceの非zero値を保持する。
PENDING/UNSUPPORTED/FAILEDは星なし。object数/BPMだけの疑似星は導入しない。

独立calculatorはGL・GameplaySession・GameClock・audioを使用せず、元chartを変更しない。
float位置/CS scale/stack offset/angle、25ms strain cap、400ms section、aim/speed/rhythm、peak reduction、
最終performance結合を検証した。ゲーム用geometryのlegacy allowanceは変更していない。
数値calculatorのfloat scale契約はこの参照版に固定する。

上限は20,000objects、6時間の開始時刻span、2,000,000stack比較。
過大chartはUNSUPPORTED、非finite値・時刻の逆転等はFAILED。
interruptはcancelとして扱い、失敗ratingに変換しない。

## Reference再生成とテスト

[oracle説明](../tools/difficulty-reference/README.md)の手順で公開C#処理を一時directoryに取得し、
自作12fixtureの値を生成した。stable asset抽出なし。app/build/testのnetwork接続なし。
通常JUnitはcheck-in済み値だけを使用する。

許容差は比較前に固定した。star/aim/speedはabsolute 1e-9、object/sectionはabsolute 1e-7＋relative 1e-9、
stack高さは完全一致。empty/single/pair/three/jumps/stream/rhythm/simultaneous/stacks/spinner/gaps/
fractional settings/coordinatesについて一致した。

## 残件

- Slider path、tick/repeat/tail、lazy cursor/travel/minimum jumpの公開reference中間値と照合して対応を拡張する。
- pre-v6 stacking、範囲外settings、Mods別計算の検証。
- stable実機での数値比較、Difficulty group境界・NM/Mod適用範囲の観測。
- 全B05受入はslider fixture等を含む。circle/spinnerの照合だけでB05全体完了とはしない。
