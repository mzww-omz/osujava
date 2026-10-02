# 別set選択時の難易度展開 stutter 調査 (2026-10-02)

前回の通常フレーム改善に続き、「別の曲を選び難易度リストが出る瞬間」を個別に測定した。開始時の作業ツリーはclean、変更前production実装は `a0e1f18`。Java 21を維持、依存追加なし、production service接続なし。

## 根拠と変更

処理経路は `SongBrowserModel` の選択/visible projection → `SongSelectScreen.updateContent` → `SongSelectCarousel.content` の行状態更新 → 通常updateで展開補間/geometry → Renderer → `SmoothUiFont` の文字fit/rasterize/texture描画。前回のprobeは選択eventを12フレームで平均し、GL/font生成を含まなかった。今回はevent、直後の描画、展開中の各frameを分離した。

実GL harnessとJFRで次を確認した。

- 別set選択ごとにScreenが全difficultyのEntry・group索引を生成し、Carouselが全rowのmapコピー・再挿入・listコピーを行っていた。構造が変わらない選択でもライブラリサイズに比例するallocationが発生していた。
- 動くrowの幅が変わるたび、widthを含むfit cacheのmissでUnicode文字列の全幅とellipsis候補prefixをAWTで再計測していた。長いUnicodeタイトルで展開中の負荷が増えた。
- 初めて表示するlabelのrasterizeで、全pixelごとにBufferedImage.getRGBとnative Pixmap.drawPixelを呼んでいた。

対応:

1. Screenはbrowserの構造が同じならEntry一覧を再利用し、展開/visible/difficulty indexが変わる旧・新setのEntryだけ置換する。Carouselはorderが同じならallRows/byKeyを再利用し、family代表索引を構造変更時に更新する。library/search/sort/groupによる構造変更は従来どおり再構築。
2. font size/bold/textごとの測定値を上限512のLRUに保持し、全幅・grapheme境界・実際のAWT prefix+ellipsis幅を再利用する。row幅の変更時も同じ二分探索・同じ文字幅を使う。size/bold/metadata変更は別key、closeで破棄。既存fit文字列cacheも空文字を含め上限512に制限。
3. TYPE_INT_ARGB imageのpixelをRGBA ByteBufferへ一括転送する。同じAWT描画、同じalpha/RGBA、同じfilteringを維持する。bufferをposition 0に戻してGLに渡す。

row配置、展開の継承座標、spring相当の補間式/頻度、hover、scroll、stars、thumbnail、chrome、描画順、Renderer/stateの分担は変更していない。font変更は共通UIに及ぶため、厳密なfit比較とpixel比較も追加した。

## 比較条件

macOS Apple Silicon / Microsoft OpenJDK 21.0.12.1 / 実OpenGL / 1280×720 / 1,000 set×4 difficulty / 固定delta 1/60秒。共有artwork、音声なし。10回warmup後、未選択の40 setへ順に移動し、各event後24frameを描画する。通常タイトルと長いCJK/英語タイトルの2fixtureを使用。長いfixtureは既存titleに `夜空の彼方への冒険 — The Never Ending Journey Across the Constellations` を5回追加。

同じ最終harnessで旧4 production classをJava 21で独立compileしてclasspath先頭へ置き、旧/新を交互に独立JVMで3回ずつ測定した。以下は**JFRなし**の各run平均/p95/maxの中央値。System.nanoTimeとThreadMXBean allocated bytesを使用。GPU完了待ち・実機FPS・音声・曲ごとのimage decodeを測る値ではない。生データは [CSV](songselect-selection-stutter-20261002.csv)。

| metadata | stage | before mean ms | after mean ms | 平均削減率 | before p95 ms | after p95 ms |
|---|---|---:|---:|---:|---:|---:|
| 通常 | 選択処理 | 3.020 | 1.814 | 39.9% | 4.168 | 2.644 |
| 通常 | 最初の描画frame | 1.905 | 0.813 | 57.3% | 2.758 | 1.092 |
| 通常 | 選択処理＋最初の描画frame | 4.925 | 2.627 | 46.7% | 6.529 | 3.861 |
| 通常 | 続く23展開frame | 0.505 | 0.368 | 27.1% | 0.807 | 0.592 |
| 長いUnicode | 選択処理 | 2.345 | 1.369 | 41.6% | 3.421 | 1.812 |
| 長いUnicode | 最初の描画frame | 2.676 | 1.000 | 62.6% | 3.330 | 1.389 |
| 長いUnicode | 選択処理＋最初の描画frame | 5.021 | 2.319 | 53.8% | 6.733 | 2.873 |
| 長いUnicode | 続く23展開frame | 1.714 | 0.602 | 64.9% | 3.087 | 1.041 |

stageごとの中央値は独立に取るため加算一致しない。最悪値がすべて改善したわけではない。通常タイトルの展開frame max中央値は1.767→4.699ms、選択処理maxは4.981→5.656ms。通常after run 2にはselection-frame平均4.531ms/max10.844msのばらつきがあり、生データに含めている。JFRを付けた予備runの数値は改善率に使用していない。

| event＋24frame allocation | before bytes | after bytes | 削減率 |
|---|---:|---:|---:|
| 通常 | 2,131,529 | 1,181,187 | 44.6% |
| 長いUnicode | 10,860,966 | 6,033,005 | 44.5% |

大量libraryのheadless補助probe（各1run、選択eventは12frameごと）のallocationも、1,000 setで78,911→23,896 bytes/frame、10,000 setで673,082→121,306 bytes/frameに減った。GCはそれぞれ1回/5ms→0、3回/7ms→0。この補助runのCPU平均は1,000 setで374.664→642.303µsと悪化、10,000 setで2,124.975→1,906.281µsであり、単発runのCPU時間を安定した改善率として扱わない。GCの発生区間もheap状態に依存し、実アプリのstutter解消を断定しない。

計算量: 選択時の全Entry生成・map再構築・allRowsコピーを除去したが、全row状態の比較/座標投影やbrowser projectionは依然O(N)。変更Entry生成は旧/新setのdifficulty数に比例する。通常フレームのresident cullingは前回のまま。文字測定cacheは上限512、既存label texture cache上限256を維持しており、無制限GPU cacheは追加していない。

## 検証

- `./gradlew test`: core 1,207 + lwjgl3 4 = **1,211 tests、failure/error/skip 0**。
- `./gradlew build`: **成功**。
- SongSelect関連とui.theme関連のtargeted tests: 成功。
- 追加回帰: 旧/新family以外のEntry identity・allRows identity再利用、resize/search/group、font size/bold/metadata別cache、空fitを含むcache上限、close、CJK/ZWJ/combining/kerningの200種類の幅で旧fit算法との厳密一致、全256alphaのRGBA転送/buffer位置。
- visual harnessのlifecycle 8scene/1,216frames、keyboard 12scene/1,416frames、foreground 8scene/1,264framesを旧/新で実行。選択、group展開、再入場、keyboard等のassertionが成功。profileの6対も含め、**286枚すべてPNG byte一致**。長いUnicodeのcaptureを画像として目視確認した。
- macOSの既存テストが要求するcase-sensitive filesystemとcanonical temp pathのため、一時case-sensitive APFS volumeを `/private/tmp` に作り、JAVA_TOOL_OPTIONSのjava.io.tmpdirをそこへ設定した。テスト変更/skipなし。実行後detachし、一時volumeを削除した。

曲ごとの実背景decode、音声preview、実マウスによる全操作、Import/再起動の実アプリ通し確認は今回の計測対象外。共有背景のharnessでTexture重複やbackground decodeを今回の主因とは確認していないため変更しなかった。culling・spring・draw順・batchをさらに変更する根拠もなかった。

## 変更ファイルとcommit

- `lwjgl3/build.gradle`, `lwjgl3/src/hudHarness/java/dev/osujava/ui/SongSelectVisualHarness.java`: opt-inで選択event/展開frameの計測を追加。本番のdebug出力なし。
- `core/src/main/java/dev/osujava/ui/SongSelectScreen.java`, `SongSelectCarousel.java`, `core/src/test/java/dev/osujava/ui/SongSelectNavigationTest.java`: 同じbrowser構造の選択でprojectionを再利用。
- `core/src/main/java/dev/osujava/ui/theme/SmoothUiFont.java`, `UiTextFit.java`, 対応する3 test file: 同じ文字fitを保持して測定再利用/pixel転送。
- 本報告とCSV: 条件・結果・限界の記録。

実装commit:

- `061e508 test(song-select): profile cold set activation and expansion frames`
- `1db1d36 perf(song-select): reuse carousel projections on set activation`
- `bae9fe2 perf(ui): cache Unicode prefix widths and bulk upload label pixels`

残る候補は選択時のO(N) state/projectionと、実library固有のcold image decode/texture生成/音声切替。後者は共有artwork fixtureでは確認できないため、実曲で再現した場合に選択eventのJFRとresource timingを追加して原因を分離する必要がある。今回の結果は描画込みの選択/展開処理を改善する根拠であり、どの実libraryでも大きな停止が完全に消えたという保証ではない。

再計測:

```sh
./gradlew :lwjgl3:songSelectVisualHarness -PsongSelectPhase=case \
  -PsongSelectCase=greylooks-large-library -PselectionProfile=true \
  -PselectionLong=true -PsongSelectOutput=/tmp/osujava-selection-profile
```

通常タイトルは `-PselectionLong=false`。変更前は `a0e1f18` に計測のみの `061e508` を適用して同条件で実行できる。
