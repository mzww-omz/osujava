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

## Workerとcache

`LocalDifficultyService`をSong Select単位で所有し、disposeでinterrupt/queue破棄する。
1 daemon worker、待機32件、未公開完了64件。library内容は変更時だけcontent単位でindexし、
同内容の移動/複製は1jobを共有する。選択→visible→library順で優先し、満杯時のpromotionは末尾を
背景待機へ戻す。残り全譜面をexecutorへ一括投入しない。

UIのlibrary/prioritize/result/drainは計算・disk I/Oを行わない。workerのimmutable結果をgeneration付きで
戻し、frame側drainで現generationだけを採用する。1batchに1revision。
library交換前の結果はUIへ採用しない。同内容keyの既公開値は安全に再利用する。

Production cacheは `~/.osujava/difficulty/`、schema 1の内容別sidecar。
SHA-256/mode/正規化Mods/algorithm/preprocessingをkeyに含める。現在のserviceが要求するModsはNMのみ。
SUCCESS/UNSUPPORTED/FAILEDを永続化し、毎frame・warm restartで失敗を再計算しない。
旧計算版の別keyは新計算へ流用しない。未知schema・壊れたcacheは数値として信用せず、元fileを保持したまま
メモリ内で再計算する。64KiB読取上限を設ける。

一時fileをclose後、同directory内のhard linkで完成済みrecordをatomic公開する。
既存recordを置換しないので、競合で出現したfuture schemaも上書きしない。
hard linkを提供しないfilesystem/保存失敗では計算値を表示用メモリに保持し、storageWarningに理由を残す。
未対応filesystemで非atomic書込へ切り替えない。raw `.osu`は変更しない。

テストはcontent重複/移動/編集、cold/warm、計算版変更、Mods正規化key、terminal結果の再利用、
future/corrupt保持、保存先が通常fileの失敗、世代交換、closeのinterrupt、1万chartでのpriority/queue/completion上限を含む。
