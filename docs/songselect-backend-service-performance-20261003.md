# B05-T3準備: 実hashのservice cold/warmと登録allocation

通常Phaseのtiming修復と並行して、実worker/cache/publicationを測るopt-in probeを追加した。
既存SongSelectPerformanceProbeはSHA-256なしの合成chartを使い、difficulty workerの対象が0件だった。
この新probeはsourceをparserへ渡し実content hashを生成する。従来UI probeは別の測定として保持する。

## 条件と範囲

- original38の検証済みfixtureを反復し、各sourceのTitleを変えて10,000 unique contentを作る。
  同じsourceを再parseした1,000コピーを加え、job数が10,000のままで同じ結果を共有することをassert。
  これは自作fixtureからの合成corpusであり、実譜面libraryの代表性は主張しない。
- 別corpusでJIT warmupし、計測corpusのlibrary登録からcoldを測る。
  一時cacheへ実保存し、serviceを閉じて別instanceでwarm再開。cold callback10,000回、warm0回をassert。
- 実wall-clock 60Hzでdrain/prioritize/result/diagnosticsを実行する。固定deltaの高速GL loopとは別条件。
  queue32/completion64上限を確認し、完了した全結果の公開時間を記録する。
  1万件には64件/frameでも最低157framesが必要なので、約2.6秒のwarmは再計算の遅さとは限らない。
- 登録時間/allocation、公開時間、selected公開latency、service frameのmean/p95/p99/max/allocation、
  callback CPU/allocation、sampled heap/GCをCSV出力。callback countersはcache IOを除外。
  frame sampleはwaitを除外し、diagnosticsなどの計測処理を含む。UI分類・carousel・GPU・audio・Importerは含まない。
- timeout、worker close/join、cache cleanupを必須にする。通常build/testはこのprobeを起動しない。

再実行:

```sh
./gradlew :core:localDifficultyPerformanceProbe --offline --console=plain
```

smokeは `-PdifficultyProbeCount=38 -PdifficultyProbeDuplicates=3 -PdifficultyProbeTimeoutSeconds=20`。
計測環境はJava21.0.12.1、Linux x86_64、JVM `-Xms512m -Xmx512m`。

## 見つけた費用と修復

BeatmapContentKeyのhash、DifficultyKeyのMods/versionがString.matchesで毎回Patternをcompileしていた。
NMでもModsのvalidation/distinct/sort streamを作っていた。
不変Patternをclass単位に一度compileし、Matcherは呼出ごとに使う。空ModsはList.ofへ正規化する。
検証内容、Modsのimmutable snapshot/整列/重複除去、cache filename、schema、numerical versionを変えない。
小文字ASCII hashの長さ、改行/Unicode、negative mode、version/Mods境界、null、mutable inputを回帰検証した。
queue/completion/数値work上限やworkerのgeneration/cancel契約は変更していない。

| 計測revision / phase | 登録ms | 登録allocation MB | 全件公開ms | service frame p95 μs | callback回数 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1633dea before / cold | 72.882 | 45.424 | 2824.587 | 1930.897 | 10000 |
| 1633dea before / warm | 30.816 | 45.424 | 2667.376 | 81.362 | 0 |
| f940f87 after / cold | 40.175 | 8.920 | 2720.830 | 148.238 | 10000 |
| f940f87 after / warm | 20.025 | 8.920 | 2652.320 | 60.304 | 0 |
| dd44805 timing修復後 / cold | 48.269 | 8.920 | 2712.608 | 136.536 | 10000 |
| dd44805 timing修復後 / warm | 20.693 | 8.920 | 2650.610 | 60.373 | 0 |

登録allocationは45,424,424→8,920,424 bytes、約80%減。
各計測は独立JVMで1回。時間・GC・heapは環境/JIT/OSの揺れを含み、普遍的なspeedupやheap改善を認定しない。
全resultはSUCCESSでcold/warm一致、コピー共有、caller threadでのcalculator0、cache警告なしを確認した。
[全CSV](songselect-backend-service-probe-20261003.csv)にsampled heap/CPU/percentile/boundsを残した。
logsは `/tmp/osujava-b05-service-{probe,optimized-probe,current-probe}.log`。

## 次のT3受入

このservice baselineをB05-T3全体の完了としない。timing T2dの受入後、以下を測る。

- 実libraryとcurve/stackの上限近傍、cold/warm全件終了時間、処理上限超過/cancelの時間とheap。
- 実Song Select updateで星sort/group/searchの再投影、wheel/rapid選曲、frame tail/allocation。
- library置換時の旧active job＋cache IO待ち、新選択first-result latency、stale破棄、import/close競合。
- GPU/音声/thumbnail IOを別に評価し、source登録やcache IOと混同しない。

既存契約のテストに加えて計測値を保存し、上限やcancel方式の変更は根拠を得てから独立commitにする。

Commit:

- `1633dea` — `test(difficulty): measure hashed library cold and warm publication`
- `f940f87` — `perf(difficulty): reuse key validators during library registration`

検証はprobeのbefore/after/current全実行成功、key境界2tests成功。
timing修復後の最終buildはcore1,457＋lwjgl3 4＝1,461tests成功。描画/数値結果は[B05記録](songselect-backend-difficulty-20261003.md)を参照。
