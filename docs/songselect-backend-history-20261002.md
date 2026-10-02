# B03: ローカル履歴・追加日時・Recently Played

2026-10-02、開始HEAD `cd269c5`。[backend改善計画](songselect-backend-improvement-plan-20261002.md)のB03を実装した。
Java 21/libGDX、完全ローカル、Import / Gameplay / GameClock / Rendererの責務分離を維持する。
アプリへネットワーク接続・公式asset・新規texture/font生成は追加していない。

## 発見と修復

- Recently Playedは描画とinputの枠がある一方、capabilityが常に無効で、実履歴を持っていなかった。
  manual Gameplayの実開始をscoreとは別に保存し、既存tab・Group・Sort・検索へ接続した。
- 保存済みscoreだけではEscape/中断を含む実際の開始を扱えなかった。
  attemptの開始と終端を分離し、abortによってscoreや完走色を捏造しないようにした。
- libraryに追加日時がなかった。成功importで内容単位の初回日時を保存し、Date Added sortと日時検索へ供給した。
  旧libraryへ現在時刻・mtimeを補完しない。
- 音声ファイル名が空の譜面はindex保存できても、再起動時の必須文字列検証で消えていた。
  parserが許す空AudioFilenameを読込時にも維持し、音声なし譜面のrestart回帰を追加した。
- 将来schemaのlibrary indexを同set再importが上書きする経路を防いだ。
  履歴も外部で変更・破損したrecordの終端更新を拒否し、元bytesを保持する。
- history更新で並びが変わる場合、旧rowのpressを解除してから再同期する。
  score更新と独立にRecently Playedが更新され、選択identity・検索解除後の復元を維持する。

## 確認した検索仕様と独自部分

[公式osu! wikiのBeatmap search](https://osu.ppy.sh/wiki/en/Beatmap_search)のosu! (stable)節を確認した。
公開[wiki原文](https://github.com/ppy/osu-wiki/blob/master/wiki/Beatmap_search/en.md)では、
`played`は最終プレイからの経過日数、`unplayed=`は値を取らず全比較演算子を使える。
別節にあるlazerの`played=yes/no`・`lastplayed`は今回実装していない。

| 入力 / 操作 | osu!javaでの意味 |
| --- | --- |
| `played<7`、`played>=0.5` | 既知の最終プレイ時刻から経過した24時間単位の日数。未来時刻は0日へclamp |
| `unplayed=`、`unplayed!=`等 | 内容一致のmanual開始履歴も保存済みscore日時もない難易度。演算子にかかわらず同じpredicate |
| `added>=2026-10-01` | **local拡張**。成功importで記録した日時を現在timezoneのcalendar dateに変換して比較 |
| Recently Played | local calendarのToday / Yesterday / Last 7 Days / Older / Never Playedにgroup化し、最終開始日時の降順 |
| Last Played / Date Added sort | 既知日時を新しい順、unknownを最後へ並べる。既存secondary metadata順で同値を安定化 |

`played` / `added`のunknownは`!=`を含む日時比較に一致しない。不正値・未対応fieldは従来のliteral検索へ戻す。
実履歴がまだない旧データの「Never Played」は、ローカル記録がないことを意味し、過去の未記録playを否定しない。
日時は難易度単位で照合する。setの他難易度をプレイしただけでは、その難易度をplayedにしない。

native stableの厳密な小数日丸め・日/週group境界・日時filterの更新タイミングは今回観測していない。
上記は確認したfieldの意味に基づくlocal実装であり、境界やgroupを1:1再現したとは扱わない。

## 保存とlifecycle

`~/.osujava/history/<play UUID>.properties`にschema 1のattemptをatomic保存する。
play ID、内容SHA-256＋mode、元set/path、開始時刻、任意の終了時刻、outcomeを保持する。

| 状態 | 記録 |
| --- | --- |
| Gameplay constructor / preview / Song Selectの選択 | 記録しない |
| manual Gameplayの最初の`show()` | UNKNOWNの開始を保存。重複showは同attemptを増やさない |
| Escape | ABORTEDを保存してからSong Selectを構築する。scoreは保存しない |
| session終端 | COMPLETED、または実`passed=false`が供給された場合だけFAILED |
| Retry | 既存の新規Gameplay生成に従い、新しいUUID/attemptになる |
| hide / dispose / アプリ中断 | 終端を推測しない。保存済みUNKNOWNを再起動後も維持 |
| Debug Auto | 履歴・scoreとも保存しない |
| 保存失敗 | revisionと成功データを公開しない。終端保存失敗は元UNKNOWNを維持し、Song Selectで通知 |

**HP/failはB06まで未実装**。現Gameplayの自然終端はCOMPLETEDだが、passed=trueを意味しない。
FAILEDの保存契約・fixtureはテストしたが、実HP失敗のGameplay観測が済んだとは主張しない。
壁時計は保存日時専用の注入可能な`Clock`で、既存のmonotonic GameClockには影響しない。
時刻rollback時も終了を開始より前に保存しない。

内容ごとの最終開始時刻をcacheする。actual attemptがない内容についてだけ、
内容一致が確認できる保存済みscoreの最大`playedAt`を読取fallbackとして使う。
hashなし・同path内容不一致のscoreは除外する。過去scoreからattempt・abort・開始時刻を生成しない。
actual attemptがある場合は、その開始時刻を優先し、後のscore完了時刻で置き換えない。

library indexはschema 3へ拡張し、各難易度の`contentSha256` / `addedAt`を保存する。
schema 1/2/3を読み、通常loadで書き換えない。同内容再import・複製は既知の初回日を共有する。
旧schemaの既存内容は再importしてもunknownを維持し、新しい内容の成功importだけが日時を得る。
外部編集で内容が変わった場合、古い内容の日時を継承しない。
破損日時はunknownとして譜面を読込可能にし、将来index schemaは上書きしない。

## レイアウト・性能と検証

既存のcarousel、Group/Sort popup、tab geometry、row/input/scissorを使用する。
projectionはlibrary/history/score revisionまたはlocal midnightの変更時だけ再生成する。
idleはrevisionと時刻範囲の比較だけで、毎frameのファイルI/O・全譜面sort・日時変換はない。
同内容の複製はprojection内でscore lookupを共有する。timezoneとDSTの日長もテストした。

- `./gradlew build --console=plain`成功。core **139 suites / 1,332 tests**、
  lwjgl3 **2 suites / 4 tests**。failure/error/skippedすべて0。
- 新規20 test methods：history 5、lifecycle 3、import date 5、activity/query/sort 5、Screen 2。
  新規Sort/Groupに伴う既存parameterized casesを含め、前回から27 tests増加。
- `history-contracts`: **16 scenes / 32 PNG / 1,536 scripted transition frames**＋navigation/disposal。
  actual tab click、難易度単位search、検索解除後の選択復元、日時、idle cache、実Gameplay show/Escape、
  restart、Debug Auto除外を確認。検索後はanimationがsettleし、選択rowがviewport内にあることをassertする。
- 既存`score-scroll-contracts`: **12 scenes / 48 PNG / 48 frames**。
- 既存`backend-contracts`: **16 scenes / 28 PNG / 672 frames**。
- 既存`audit`: **84 scenes / 172 PNG / 1,256 frames**。
- 全suiteは1280×720、1280×800、1024×768、1280×720 framebuffer density 2を含む。
  合計 **128 scenes / 280報告PNG / 3,512 scripted transition frames**。
  既存skin・procedural fallback・HD asset、long/Unicode、背景なし、empty/single/many、resizeを回帰確認する。

```sh
./gradlew build :lwjgl3:hudHarnessClasses --console=plain
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=history-contracts \
  -PsongSelectOutput=/tmp/osujava-history-reproduce
# 出力先を変え、phaseをscore-scroll-contracts / backend-contracts / auditへ変更して既存回帰を再現。
```

ログ：`/tmp/osujava-b03-final-build.log`、`/tmp/osujava-b03-history-final.log`、
`/tmp/osujava-b03-gl-{score-scroll-contracts,backend-contracts,audit}.log`。
capture：`/tmp/osujava-b03-history-final`、`/tmp/osujava-b03-gl-<phase>`。
4:3のunplayed検索captureを目視し、settle後のselected row・上部/下部clip・empty score欄を確認した。
native同期captureやCPU/GPUの前後benchmarkは行っていない。

## 残件と次の実装

- B04：UUID/name/content membershipのlocal Collections CRUDを作り、Collections tabとOptionsへ接続する。
  Difficulty tab・Collections tab・未実装Optionsは今回有効化していない。
- HP/fail(B06)、通常Mods(B07)、replay(B08)、削除/退避と復旧(B09)は未着手。
  replay観覧機能自体がなく、新規機能を見せかけで追加していない。
- stableの経過日数の丸め・group境界はnative観測を追加し、必要ならlocal実装と比較して修正する。
  現在の時間predicateはbrowser再構築時のsnapshot。idleの日中に小数日thresholdを跨いでも、
  revision/検索変更/日付境界で再構築するまで結果は変わらない。
- 追加日時は現在のlibrary indexに保存する。内容が置換され、参照が全てなくなった旧内容については、
  restart後まで日時を保存する独立の版履歴storeはない。将来B09の退避/復旧契約と合わせて設計する。
- 過去の未記録abort/開始は復元できない。hashなしscoreは閲覧可能でも日時projectionへ使わない。
- 前回報告のResultsのGreylooks combo/accuracy label重なり、profile編集UI、古いhash不一致scoreの
  専用履歴画面、native score managerの厳密な寸法/animationは今回のB03対象外として残る。

主な変更：`LocalPlayHistory`、`GameplayAttempt`、`OsuJavaGame/GameplayScreen`、
`BeatmapLibrary/Storage`、`SongBrowserActivity/Model/Query/Controls`、`SongSelectScreen`、関連tests/harness。

実装commit：

- `0fe8cfa` — `feat(library): persist content import dates in schema 3`
- `6d95f7d` — `feat(gameplay): record local manual attempts and outcomes`
- `ddb2f7a` — `feat(song-select): enable local recently played and date search`

本書・改善計画・進捗・残件台帳は別のdocs commitとして記録する。
