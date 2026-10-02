# Song Select performance investigation (2026-10-02)

## Scope and measurement

Stable風のrow配置・展開・spring相当の指数補間・hover・wheel/drag・文字階層・thumbnail・stars・上下chromeを変更せず、実装調査と計測で確認した無駄だけを削減した。Rendererは引き続き描画専用で、状態・入力・resource準備はScreen/Carousel/Browser側にある。Java 21を維持し、外部依存は追加していない。production serviceへの接続は行っていない。

作業開始時の `git status --short` は空。変更前のproduction実装は `43d599a`、計測用probeを追加しただけのcommitは `41af07a`。変更前後とも同じ最終probe（`9375273` の入力条件・移動assertionを含む）を使用した。

環境: macOS / Apple Silicon、Microsoft OpenJDK 21.0.12.1、1280×720論理viewport、heap `-Xms512m -Xmx512m`。各setに4 difficulty、Unicode artist、thumbnail/audioなしの合成library。100 / 1,000 / 10,000 set（400 / 4,000 / 40,000 difficulty）。production `SongSelectScreen.update()` をreflectionで呼び出し、各scenario 1,200フレームwarmup + 1,200フレーム計測、固定delta 1/60秒。ポインターはwindow (640,360)に固定し、左側pointer trackingを無効にした。独立JVMを3回実行した中央値を以下に記載。各runの全数値は [CSV](songselect-performance-20261002.csv) に保存。

- idle: 中央set選択、入力なし。
- wheel: 6フレームごと±1、120フレームごと方向反転。
- fast-wheel: 毎フレーム±4、120フレームごと方向反転。
- selection: 12フレームごと中央付近20 setと4 difficultyを巡回。選択eventとupdateの両方を計測区間に含む。
- wheel/fast-wheelは実際に1 row以上移動するassertion付き。10,000 setで移動幅2,382.3 / 62,421.8 logical units、全6runで変更前後が同一。追従領域にポインターがあった初期の予備計測はCSV/表から除き、最終条件で全runを取り直した。
- stage計測: settled状態でcarousel / layoutRows / colour / stars / foreground / presentationを個別に呼び出す。total updateとは別の計測であり、stage時間を足した値がtotalに一致するとは限らない。

`System.nanoTime()`、Java 21 `ThreadMXBean.getThreadAllocatedBytes()`、`GarbageCollectorMXBean` を使用。測定配列・CSV出力・GC統計取得は計測区間外。reflection/input stubの共通overheadは含む。GCはJVM全体の計測区間内の値。fixture作成、library import、GL、font rasterization、image decode、audio、vsyncはCPU probeの対象外。実機FPSやGPU時間の改善率ではない。100 setの数値にはJIT warmup・実行順によるばらつきがあり、小差を厳密に解釈しない。

再実行:

```sh
./gradlew :core:songSelectPerformanceProbe
./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectPhase=case \
  -PsongSelectCase=greylooks-large-library -PsongSelectOutput=/tmp/osujava-songselect-perf
```

変更前を再測定する場合は `41af07a` のcheckoutへprobe条件修正commit `9375273` を適用して実行する。今回の再計測では、そのcommitのSongBrowserModel / SongSelectCarousel / SongSelectScreenを `/tmp` に取り出してJava 21で独立compileし、probeのclasspath先頭に置いた。現在の作業ツリーをrevertしていない。本番にtiming/logging instrumentationは残していない。

## Investigated processing structure

| 責務 | 主な実装・通常フレームの処理 |
|---|---|
| Screen/state | SongSelectScreen.update、SongSelectViewState。入力dispatch、pointer sample、preview、viewport layout、score revision確認、hit用row生成、pointer操作、animation、描画snapshot/resource準備 |
| Browser/selection | SongBrowserModel。library/search/sort/groupでfilter/orderを更新。選択・group開閉でrow状態とvisible projectionを更新。Screen.syncBrowserが表示選択・details・backgroundへ反映 |
| Row/entry | Browser.Rowはidentityと親/代表difficultyを持つ。Carousel.Entry/RowはUI座標・residency・animation状態。SongSelectRowとRowRenderer.Presentationは描画/input用snapshot |
| Scrolling/motion | SongSelectScroll、Carousel.advanceRows、InputController、WheelInput。既存activeStart/End、-20..640 reference-Y buffer、予測移動・target/current位置、retire/reenter、group seedを使用 |
| Layout | SongSelectLayoutとScreen.layoutRows。chrome/toolboxはviewport別snapshotを既にcache。resident rowの動的geometryは更新。hit geometryとrender geometryを同時にpublish |
| Animation | Carouselのfocus/hover/group/selection補間、RowColourAnimation、StarAnimation、ForegroundAnimation。resident sprite lifecycleと時刻による状態を保持 |
| Text | rowContentはmetadata別にcache。UiView→SmoothUiFont→UiTextFit。font、ellipsis結果（512）、rasterized label texture（256）を既にcache。動的座標は毎フレーム必要 |
| Thumbnail/background | BeatmapThumbnails。Path別LRU、通常上限18、frame pin。prepare時のmissだけfilesystem/image decode/Texture生成。resident lookupはロードしない。close/evictionでdispose |
| Skin | SongSelectSkinAssetsはshow/生成時にresolve/load。通常のgetはmemory lookup。configuration、top/body geometryは既に保持 |
| Rendering | SongSelectRenderer→RowRenderer→UiView/SpriteBatch。snapshotのみを読む。rowごとの既存sprite挿入順、begin/end、shape fallbackを保持 |

## Confirmed bottlenecks and changes

1. **全rowを繰り返し走査**: Screen.layoutRowsがvisible model row全体を1フレーム2回走査し、colour/stars/foregroundが全difficulty rowをそれぞれ走査してresidentを探していた。描画自体は既にbuffer内rowへ限定済み。Carouselのresidency変更箇所でidentity setを維持し、Screenのanimationは同じvisible residentだけを更新。layoutRowsは既存active rangeを順序通り走査する。group seed直後のresident、hidden spriteのflag、retirement、libraryから消えたidentityも扱う。
2. **選択だけで構造を再構築**: SongBrowserModel.expandが全difficultyのキー生成、親子・representative・orderの再構築、複数map/list生成を毎回行っていた。構造を変えるlibrary/search/sort/group時のみrebuildRowsを実行し、選択/group開閉は従来のstate計算とprojectionのみを更新。selectのset検索は既存visibleByIdを使用。metadata/rowのidentity再利用、filterでのrepresentative再割当、query解除、group自動展開を既存テストで維持。
3. **presentation用の一時map**: resident animationのidentity mapから毎フレームString keyのforeground/colour/stars mapを作り直していた。Carouselの既存byKeyを介して元のanimation mapを参照し、描画対象分のsnapshotだけを取得。rendererのimmutable snapshot契約は維持。
4. **projection invalidation**: structure listを選択時に再利用してもgroup焦点を矢印でconfirmした時のvisibilityが更新されるよう、Screenはvisible entriesのidentity変更も検知する。回帰テストは検知を外すとexpected 5 rows / actual 1 rowで失敗し、修正版でpass。
5. **frame共通補間係数**: Carousel内で全row共通のgroup指数補間係数をrow loop外に移動。式・float cast順・更新頻度は同じ。これは独立した改善率を主張せず、全row補助animation自体は残した。

### CPU results (3-run medians)

単位はµs/frame。selectionは選択eventを含む全フレーム平均（12フレーム中1回のevent）。

| set数 | scenario | before mean | after mean | mean削減率 |
|---:|---|---:|---:|---:|
| 100 | idle | 22.023 | 11.880 | 46.1% |
| 100 | wheel | 21.236 | 13.533 | 36.3% |
| 100 | fast-wheel | 14.431 | 8.694 | 39.8% |
| 100 | selection | 38.238 | 40.649 | -6.3% |
| 1,000 | idle | 30.316 | 12.571 | 58.5% |
| 1,000 | wheel | 28.985 | 13.007 | 55.1% |
| 1,000 | fast-wheel | 34.244 | 19.204 | 43.9% |
| 1,000 | selection | 160.468 | 88.519 | 44.8% |
| 10,000 | idle | 222.266 | 45.892 | 79.4% |
| 10,000 | wheel | 223.474 | 43.788 | 80.4% |
| 10,000 | fast-wheel | 228.650 | 64.749 | 71.7% |
| 10,000 | selection | 1,658.958 | 496.311 | 70.1% |

100 setのselection平均は38.238→40.649µs（6.3%悪化、約2.4µs差）。small-libraryの各scenarioが全て改善したという結果ではない。

10,000 setのp95: idle 233.875→47.750µs、wheel 232.375→47.584µs、fast-wheel 240.708→54.250µs、selection 16,981.042→5,331.375µs。選択時の尾部は依然存在する。

10,000 setのstage mean中央値: layoutRows 32.946→0.796µs、colour 42.464→0.290µs、stars 42.178→0.231µs、foreground 41.676→0.215µs、carousel 39.500→34.384µs、presentation 1.215→0.849µs。全row走査が支配的だったことと、削減後もCarouselに曲数依存の仕事が残ることを確認できる。

実GLの既存large-library harness（1,000 set、共有artwork、cached text、180 idle samples）の**描画込みframe CPU submission**はbefore mean 0.282ms / max 0.813ms、after mean 0.260ms / max 1.652ms（mean 7.8%削減、maxは悪化）。motion-only 600 samplesは0.020→0.016ms。これは各1runの参考値で、GPU完了時間やFPSではない。draw-onlyの時間は分離測定していない。draw順序やbatch構成を変更する根拠としては使っていない。

### Allocation / GC

| 10,000 set scenario | before bytes/frame | after bytes/frame | 削減率 | GC回数中央値 | GC時間中央値 |
|---|---:|---:|---:|---|---|
| idle | 15,248.0 | 11,936.3 | 21.7% | 0→0 | 0→0ms |
| wheel | 14,202.3 | 10,815.1 | 23.8% | 0→0 | 0→0ms |
| fast-wheel | 16,079.1 | 12,669.4 | 21.2% | 0→2 | 0→18ms |
| selection | 2,515,693.0 | 673,049.6 | 73.2% | 9→3 | 16→4ms |

presentation単体は9,376→7,078 bytes/call。colour/stars/foregroundのresident一時setを除去。selectionの構造再利用がallocation削減の大半を占める。選択scenarioの値は全フレームで平均した値であり、1選択eventのbytesではない。selection計測区間ではGCが減った一方、fast-wheelはbefore 0 / after 2 collectionsとなった。scenarioは同じ順序で実行するが、その時点の累積heap量によりGC発生区間がずれるため、区間別GC数から全体のstutter改善を断定できない。fast-wheelのafter meanには約18msのGC時間も含まれ、p95との差がある。live gameplayでの周期的stutter解消までは主張しない。

### Complexity

N=全difficulty row数、V=browser visible row数、A=既存active range内のrow数（hiddenも含む）、R=visible resident数。

- colour/stars/foregroundのresident探索はO(N)→O(R)、一時set生成不要。
- layoutRowsの候補走査はO(V)→O(A)、出力順序は同じ。通常viewport付近ではAは小さいが、nativeと同じhidden row rangeや大量移動ではAが大きくなる可能性は残る。
- selectionではO(N)の構造再構築とidentity/string/collection生成を除去。ただしstate/projection更新とScreen/Carouselのcontent再投影はO(N)のまま。
- Carouselの補助amount更新はO(V)のまま。**Song Select全体がO(1)になったという変更ではない。**

## Suspected paths that did not justify changes

- 画面外rowすべてを描画: 既にnative buffer/residentによるcullingがある。新たなviewport clippingやoverscan値変更は不要。
- 全rowの座標spring更新: advanceRowsは既にactive rangeに限定。全visible rowの補助amount更新は別に存在するが、挙動をlazy/sleepへ変更する実装は見送った。
- metadata文字列、Unicode fallback、ellipsisの毎回再計算: static row contentとSmoothUiFontのfit/raster cacheがある。cache hitでもString keyを作るallocationは存在するが、今回そこが支配的という実測はない。
- chromeのlayout毎回再生成: viewport別snapshotが既にある。row動的geometryの生成は残るがN比例ではない。
- thumbnailの同一Path重複Texture生成: 既存Path cacheとframe pinで共用される。missing/corrupt結果もcacheされる。Texture disposal契約を変更する必要なし。
- 毎フレームsorting/filtering: rebuildは変更event時のみ。今回問題だったのはselection時にも構造を作るexpand。
- skin filesystem lookup in draw: SongSelectSkinAssets.getはin-memory lookup。
- 毎フレームのscore全library再集計: revisionとlibrary identityでskip済み。

## Compatibility and verification

追加回帰テスト:

- SongSelectResidentIndexTest: 1,000 group / 600操作frameでfull scanとindexのmembershipを比較。seed直後、hidden children、scroll方向反転、selection、focus、resize、reorder、library縮小・空化を検証。active range走査と旧full scanのpresentation順序が同じことを確認。10,000 groupでsettled bufferとreadonly viewを確認。
- SongSelectNavigationTest追加case: 閉じたparent groupにUpでfocusし、Rightでconfirmして開く操作。playable selectionを変えず、carouselに子rowが再表示されることを確認。projection検知なしのnegative controlではfail、修正後pass。
- SongBrowserRowsTest追加case: selectionとgroup開閉でstructure listを再利用し、library/search/sort/group変更時にrebuildすることと、parent/除外状態を確認。
- 既存SongSelectNavigationTest等: snapshot colour、stars、foregroundのfade時刻、resident破棄、展開、同一set difficulty移動、別set移動、hover、wheel/drag、Random/Back、検索、viewport、score、Unicode/長文、skinとthumbnail lifecycleを継続検証。

実行結果:

- `./gradlew :core:test --tests 'dev.osujava.ui.SongSelect*'`: pass。
- `./gradlew test`: core 1,199 + lwjgl3 4 = **1,203 tests、失敗/skip 0**。
- `./gradlew build`: pass（hudHarnessClassesもcompile）。
- 100 / 1,000 / 10,000 set performance probe before/after各3run: pass。
- lifecycle-contracts before/after: 各8 scenes / 68 captures / 1,216 scripted transition frames、navigation/disposal含めpass。68 captureがPNG bytesまで一致。
- greylooks-large-library before/after: 各1 scene / 7 captures / 62 transition frames、navigation/disposal含めpass。7 captureがPNG bytesまで一致。
- wheel / drag / keyboard / foreground contracts before/after: 12 / 16 / 12 / 8 scenes、60 / 96 / 72 / 104 captures、516 / 1,028 / 1,416 / 1,264 scripted frames。全てpass、合計332 captureがPNG bytesまで一致。projection検知追加後にkeyboardを再実行しpass。
- foundation before/after: 既存の `Giant chrome still covers the browser background` assertionで両方fail。最終版との比較で、それまでの114 captureのうちRandom以外93枚がPNG bytesまで一致。Random 21枚は乱数seed非固定の選曲差であり、pixel同一比較から除外。Randomの操作・選択契約は既存testsとharnessで検証。

全test初回には、macOSの `/var`→`/private/var` 表記差でstorage resave test、case-insensitive filesystemで `Cursor.PNG` / `cursor.png` 衝突fixtureが失敗した。productionやtestを変更・除外せず `/private/tmp` 内の使い捨てcase-sensitive APFS volumeで再実行し全件pass。実行時のみ `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp/osujava-test-volume` を指定。volumeは終了後unmountした。

目視確認は1280×720の初期表示のbefore/after画像。操作互換性の確認は上記のscripted harnessとunit/integration testsであり、人が実アプリを操作した確認とは区別する。Import/再起動後のmetadata・選択・score storageはintegration test範囲で確認し、実ユーザーlibraryをimportし直していない。thumbnail有無、Unicode/長文、skin有り/fallbackは既存testsと取得済みのvisual fixture範囲。skin hot reloadの新機能やcache invalidationは追加していない。

## Remaining work and future candidates

- 全visible rowのfocus/hover/group/selection amount更新（10,000 setでCarousel約34µs）が次のCPU候補。offscreenの経過時間・hover履歴・再入場をexactに維持する検証が必要。
- selectionのstate/projection/content O(N)処理が残る。実測p95約5.3ms、allocation約0.67MB/frame（event平均）。旧/新familyとgroupに限定するincremental更新は、独立の互換性検証が必要なので今回は行わない。
- 初見thumbnail/backgroundはupdate内で同期decode/upload、text cache missはdraw内でAWT rasterize/Texture生成。多数の**異なる**高解像度artwork、長文Unicode、256 label/512 fit cacheのthrashは今回のCPU probeでは未測定。次はcold-cache/unique-artworkのJFR・GL frame profileを取ってからasync decodeやcache設計を判断する。
- row geometry/snapshot/星glyphの一時objectとrow単位begin/endは残る。描画順・sprite layeringへの影響があるbatch再編は今回見送った。
- foundationの巨大chrome既存failureはこの性能修正の対象外として残る。

## Changed files and commits

| File | 理由 |
|---|---|
| core/build.gradle | opt-in Java 21 probe task |
| core/src/test/java/dev/osujava/ui/SongSelectPerformanceProbe.java | 同条件CPU/allocation/GC計測・追従領域外pointerと実移動assertion |
| core/src/main/java/dev/osujava/ui/SongSelectCarousel.java | resident索引・既存range公開・frame共通係数・identity lookup |
| core/src/main/java/dev/osujava/ui/SongSelectScreen.java | range/residentだけを走査、一時presentation map除去 |
| core/src/main/java/dev/osujava/ui/SongBrowserModel.java | 構造更新とselection state更新を分離、既存set index使用 |
| core/src/test/java/dev/osujava/ui/SongSelectResidentIndexTest.java | buffer/lifecycle/index/full-scan parity回帰 |
| core/src/test/java/dev/osujava/ui/SongBrowserRowsTest.java | structure再利用とinvalidate回帰 |
| core/src/test/java/dev/osujava/ui/SongSelectNavigationTest.java | group焦点の矢印confirmとprojection更新回帰 |
| docs/songselect-performance-20261002.md | 調査・設計・互換性・結果・制約 |
| docs/songselect-performance-20261002.csv | 全6runのraw測定値 |

- `41af07a` — `test(song-select): add repeatable CPU and allocation probe`
- `e0255e7` — `perf(song-select): index resident rows without changing motion`
- `0112863` — `perf(song-select): reuse row structure on selection changes`
- `a032186` — `perf(song-select): remove temporary presentation maps`
- `f27c290` — `fix(song-select): invalidate content on projection changes`
- `9375273` — `test(song-select): verify wheel movement outside pointer tracking`
- この報告とCSVは別のdocumentation commitで保存（SHAはGit履歴と最終報告に記載）。
