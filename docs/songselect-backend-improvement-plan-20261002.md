# Song Selectを完成させるローカルbackend改善計画

2026-10-02、調査基準HEAD `0a1f02b`。2026-10-03時点でB01/B02/B03/B04を実装済み。B05はNM circle/spinner計算とUI接続まで実装済み。
実装範囲・検証・未完了事項は[backend実装進捗](songselect-backend-progress-20261002.md)と
[B03履歴・日時の実装記録](songselect-backend-history-20261002.md)、
[B04Collections/Optionsの実装記録](songselect-backend-collections-20261003.md)、
[B05星評価の対応範囲・検証・残件](songselect-backend-difficulty-20261003.md)を参照。
以下の現状表は計画作成時の調査記録として保持する。B05は部分実装、B06–B09は未着手。
対象は[残件台帳](songselect-remaining-work-20261002.md)のR06/R07/R08と、それらに必要な保存・更新通知。
完全ローカル、Java 21/libGDX/LWJGL3、Import / Gameplay / Ruleset / GameClock / Rendererの責務分離を維持する。

目的は、実際のローカルデータをSong Selectへ供給し、星評価・Difficulty・Recently Played・Collections・
score詳細・通常Modsを機能として接続すること。描画だけのplaceholderを完成扱いにしない。
計算・storageはGL不要とし、Rendererは確定済みsnapshotだけを受け取る。

## 現状と改善対象

古い[Results不足データ計画](results-missing-data-plan-20260929.md)は当時の履歴として扱う。
現在のコードを確認した結果は以下。旧計画の未実装記述をそのまま再採用しない。

| 領域 / 確認したコード | 既にあるもの | 現在不足しているもの |
| --- | --- | --- |
| [BeatmapFileParser](../core/src/main/java/dev/osujava/beatmap/parse/BeatmapFileParser.java)、[BeatmapPlayData](../core/src/main/java/dev/osujava/beatmap/BeatmapPlayData.java) | 元.osuのSHA-256/MD5、break、AudioLeadIn、AR等のmetadata | hashを用いたscore query/cache/collection対応。hash計算の新設は不要 |
| [BeatmapLibrary](../core/src/main/java/dev/osujava/library/BeatmapLibrary.java)、[PropertiesBeatmapLibraryStorage](../core/src/main/java/dev/osujava/library/PropertiesBeatmapLibraryStorage.java) | local import、schema 1/2読込、schema 2保存、atomic index更新、起動時parse | library revision、追加日時、backend更新のsnapshot通知 |
| [LocalScoreStore](../core/src/main/java/dev/osujava/score/LocalScoreStore.java)、[ScoreDetails](../core/src/main/java/dev/osujava/score/ScoreDetails.java) | schema 1/2、score/判定/combo/accuracy/日時、scoringVersion、hash、Geki/Katu/possibleCombo、revision、sorted cache | player/Mods/ruleset/replay参照。queryはsetId＋pathなので同pathの内容更新を区別しない |
| [OsuRuleset](../core/src/main/java/dev/osujava/ruleset/osu/OsuRuleset.java)、[OsuGameplaySession](../core/src/main/java/dev/osujava/ruleset/osu/OsuGameplaySession.java) | mode 0、ScoreV1、slider集計、時刻付き通常Input API | difficulty calculator、通常Mods、HP計算/fail。resultDetailsのpassed/healthは現在null |
| [MusicGameClock](../core/src/main/java/dev/osujava/gameplay/MusicGameClock.java)、[GameplayInput](../core/src/main/java/dev/osujava/gameplay/GameplayInput.java) | EOF後の単調時刻、lead-in、pause/resume、time/sequence/物理button mask | rate対応audio transport、replay記録/再実行。入力APIと時計の作り直しは不要 |
| [SongBrowserModel](../core/src/main/java/dev/osujava/ui/SongBrowserModel.java)、[SongBrowserQuery](../core/src/main/java/dev/osujava/ui/SongBrowserQuery.java) | 難易度単位のsearch/sort/group、identityによる選択、mode検索 | star/history/collectionのprojectionと検索。新データに応じた限定更新 |
| [SongSelectScreen](../core/src/main/java/dev/osujava/ui/SongSelectScreen.java)、[SongSelectToolboxState](../core/src/main/java/dev/osujava/ui/SongSelectToolboxState.java) | trusted rating注入点、星描画/animation、Mode/Mods overlay、既存skin | 本番rating供給。通常Modsはactive空・toggle false。未対応tab/Optionsの実処理 |

## 実装順と成果物

工数は未見積り。PR単位を小さくし、段階ごとに利用可能な機能を完成させる。
初回の実装対象はB01/B02。B05の星評価は大きいため、単なる近似式を先に公開しない。

| ID / 優先度 | 依存 | 成果物とSong Selectでの変化 | 完了判定 |
| --- | --- | --- | --- |
| B00 / P0 | なし | 対応対象・根拠・fixtureの固定。UIはstable b20230727.9、数値計算は別途versionを固定 | reference commit/algorithm version/数値許容差/未知値方針を記録 |
| B01 / P0 | B00 | 譜面内容keyとlibrary revision。更新譜面へ古いscore/cacheを誤適用しない | 同path変更、移動、再import、複製、壊れた保存データの回帰通過 |
| B02 / P0 | B01 | score schema 3と開始時play context。新規scoreのplayer・Mods・ruleset由来を保存しranking/Resultsへ表示 | schema 1/2/3読込、未知は未知、現在UIから過去scoreを補完しない |
| B03 / P1 | B01/B02 | プレイ履歴・追加日時のlocal store。Recently Playedとplayed/date検索を接続 | restart、Retry/abort/fail、DEBUG_AUTO除外、日付境界、保存失敗に対応 |
| B04 / P1 | B01 | collection CRUDとmembership。Collections tabとOptionsのManage Collectionsを接続 | 難易度/Set追加、複数collection、検索との交差、空/Unicode、restart/reimportを確認 |
| B05 / P1 | B00/B01 | 独立star calculator＋永続cache＋更新通知。星・Difficulty sort/group・stars検索を接続 | 固定reference数値一致、未知と0の区別、stale結果破棄、大量譜面でframe内計算なし |
| B06 / P1 | B00/B02 | HP/failとplay outcome。passed/healthを実収集し、正常完走と失敗を区別 | 固定入力でHP/判定/scoreがupdate頻度に依存しない。abortをfailとして保存しない |
| B07 / P1 | B02/B05/B06 | osu!standardの通常Mods。NF→HRを最初の機能単位とし、EZ/HD、rate系へ拡張 | 各Modの判定/描画/HP/score/rating/保存が同じplay contextを使用 |
| B08 / P2 | B01/B02/B07のcontext | local replayの記録・再実行・score参照。実在する記録のみranking/Resultsへ接続 | 同入力で同結果、内容不一致を拒否、再生でscore/historyを増やさない |
| B09 / P2 | B01/B03/B04 | Optionsのlocal管理操作を拡張。score削除/譜面退避を個別PRで実装 | ファイルとindexの整合、cache無効化、失敗時復元、参照の残存状態を確認 |

推奨順は **B00→B01→B02→B03→B04→B05→B06→B07→B08→B09**。
B04とB05はB01後に独立して進められるが、初期は完成しやすいlocal機能を先に接続する。
backend依存でないpixel丸め/font/chrome/manager順は別のUI比較作業として残す。

## B00/B01: 内容識別と更新の土台

- 既存のsetId＋pathによるrow selectionと`DifficultyIdentity`は位置の識別として維持し、
  計算・score照合用の内容key（raw SHA-256＋mode）と分ける。候補名`BeatmapContentKey`。
  MD5は将来の互換照合用。表示名・BeatmapID・SetIDを内容hashの代わりにしない。
- hashは既存`playData`を使う。Renderer/renderでファイルを再read/hashしない。
  同path再import時は新しい内容keyで更新。移動した同一内容はcacheを再利用できる。
- `BeatmapLibrary`に成功した更新時だけ増えるrevisionを追加し、読取snapshotを再利用する。
  UIごとに全libraryをpoll/再indexする設計にしない。新しい汎用event busは不要。
- 新規scoreは内容keyで照合する。schema 2のhashありscoreはそのhashを使用する。
  hashなしschema 1/legacyは由来不明として従来pathのlegacy欄に保持し、現在hashを後付けしない。
  同名/同path更新で不一致scoreは消さず、現譜面のbest/played判定から分離する。
- 同一hashを持つ複数Setのrowは統合しない。内容照合と画面のfamily/selection identityは別。
  `BeatmapSetIdentity`のmetadata衝突・rename問題は回帰fixtureを置き、今回全Set IDを変更しない。

検証: 同じpathで1byte変更、同内容別path、Set置換、schema 2 hash不一致、legacy hashなし、
missing chart、import失敗、同一metadata別内容。既存archiveの危険path拒否を維持する。

## B02: score schemaとplay context

新しいrecord候補`PlayContext`をGameplay開始時にfreezeする。主な値は、内容key、ruleset ID、
ruleset/scoring version、正規化Mod構成、local player ID/name、GameplayRunMode。
Retryは新play ID。終了時のUI設定を遡ってscoreへ使わない。名前は入力されたlocal profileだけを使い、
OS usernameやオンラインplayerを推定しない。profile未設定ならplayerは未知のままでよい。

| schema 3の候補値 | 保存/未知値の方針 |
| --- | --- |
| `rulesetId`, `rulesetVersion`, `scoringVersion` | 新規playで確定。旧scoreの不明ruleset/versionを推定で埋めない |
| `playerId`, `playerNameAtPlay` | optionalなlocal profile snapshot。rename後も当時の名前を保持 |
| `mods` | 新規NMは既知の空構成、旧scoreは未知。unknownとNMを同じ空listにしない |
| `replayRef` | 記録保存が成功した場合のみ。B08まで未設定 |
| `passed`, `health` | B06で収集できるまでnull。completedやgradeからpassを捏造しない |

既存Properties・playごとのUUIDファイル・atomic move・manualだけのsaveを維持する。
schema 1/2をread可能にし、load時には書換えない。未知の将来schemaは保持してPARTIALへ。
scoreとcalcのversionは別。異なるscoring versionの点数を同条件の比較と見なさず、legacy由来を表示する。
新機能のために既存scoreを一括変換・削除しない。

score projectionは保存値だけを読み、player/mods/gradeへ接続する。silver gradeは対応Modを実装し、
保存された実Modを確認できる場合のみ。現在選択しているModsを過去scoreへ反映しない。

## B03/B04: 履歴・collection・Optionsの最初の完成範囲

### 履歴

`LocalPlayHistory`候補はscoreとは別storeとし、manualで実際に開始したplayを記録する。
選曲/preview、Debug Auto、replay観覧はRecently Playedを更新しない。
開始日時と終了状態（completed/failed/aborted）を別に保持し、同play IDの終了を更新する。
Retryも新しいattempt。アプリ中断で終端不明のattemptは不明のまま扱う。
過去scoreから導けるのは保存済みplay日時だけで、欠けたabort履歴は復元しない。

初期のRecently Playedは最終開始時刻の降順。stableの日/週等のgroup境界はwikiだけでは確定しないため、
観測前は明示したlocal分類を使用し、1:1扱いにしない。時計とtimezoneを注入して境界をテストする。
`addedAt`は成功importで初回保存し、同内容再importで保持。旧libraryの正しい追加日は不明として扱う。
scoreのplayed/完走色とhistoryのattempt有無は別の値にする。B06後はfailed scoreを完走色に使わない。
現在日時への補完やファイルmtimeの代用は行わない。

### Collection

`LocalCollectionStore`候補はUUID/name/難易度参照/revisionを保持し、create/rename/delete/add/removeを提供。
初期参照は内容key＋local locatorを保存。Set一括追加は実在する各難易度を登録し、
その後増えた難易度を暗黙に追加しない。collection間の重複membershipは許容する。
譜面内容更新で旧memberが解決できなくなればmissing扱いで保持し、類似名へ自動付替えしない。
再import後の新内容への置換は、明示的な管理操作で行う。

Collections tabはmembershipと現在search/mode filterの交差をprojectionし、各collectionに属する
同譜面の表示は別row keyでも、playable selectionは元の難易度identityを維持する。
OptionsはまずManage Collectionsだけを実処理へ接続し、未実装のDelete/Export等は有効化しない。
公式collection.db読込は後続。最初はosu!javaのlocal storeのみで完成させる。

## B05: 星評価の独立計算と供給

1. **根拠固定**: 公開lazerのcalculator/preprocessing/skillsを参考に、対応するcalculator commitと
   algorithm versionを固定。現在の公開calculatorは`Version=20260706`を持ち、stable 2023の
   数値互換の証拠にはならない。2023対象の参照値を確保するまで「stableと同値」としない。
   raw object数/BPM/CSだけの近似を本番starとして供給しない。
2. **純粋計算**: osu!standardのNMから開始。slider path/timing・stacking等の前処理を固定し、
   head/nested位置、時刻、strain/skill集計、star出力を段階的にfixtureへ落とす。
   既存geometry helperは契約が合う部分のみ利用。GameplaySessionやGLを生成して計算しない。
3. **結果モデル**: 成功値のほかpending/unsupported/failedを区別し、未知を0へ変換しない。
   `OptionalDouble`の既存UI注入点は維持し、失敗情報はbackend snapshotに持つ。
4. **Cache**: keyをSHA-256/mode/正規化Mods/calculator version/前処理versionとする。
   raw譜面は変更せずsidecarにatomic保存。失敗も同じkeyで記録し毎frame retryしない。
   内容/version変更で失効。全Mods組合せの事前計算は行わない。
5. **Scheduling**: 1 workerと有限queueから開始し、選択難易度→visible→残りを優先する。
   同keyの重複jobをまとめ、最大譜面サイズ/処理上限と終了時cancelを設ける。
   失敗/過大chartでも別chartの計算を続けられるようにする。
6. **公開**: workerはimmutable結果だけを返し、UI threadで内容key/library generationを確認して採用。
   `SongSelectScreen`のconstructor/library変更時だけのrow content cacheを、rating revisionで更新可能にする。
   完了通知はframe内にまとめ、各job完了ごとに全libraryをrebuildしない。

星値を星描画、selected info、Difficulty sort/group、stars検索で同じcacheから取得する。
sort/groupの未計算値は未知欄へ、数値検索は既知値のみmatch。検索中の計算完了後もselected identityを保持。
Mod変更で別keyを参照する。NMとMod付きのsort/group/searchの適用範囲はnative観測後に固定する。

受入: 自作circle/slider/spinner/stack/同時刻/短いchartでreference値と中間値を照合。
許容差を先に決め、丸めた表示星だけで判定しない。cold/warm restart、version変更、rapid選曲/Mod変更、
import中のjob、画面close、1万難易度でqueue上限・rebuild回数・frame内IO/計算ゼロを確認する。

## B06/B07: HP/failから通常Modsへ

B06は現在nullのpassed/healthを生成するための独立作業。HP増減、break中drain、fail境界を
stable観測・公開仕様から確定し、rulesetで計算する。時刻付きinput、scheduled判定、HP更新の順を固定。
完走/failed/abortedを分け、fail時に残りobjectを一括MISS化して最終scoreを捏造しない。
保存するhealthは時刻順・有限長とし、未収集の旧scoreはnullのまま。Resultsの既存受け口を活用する。
現`OsuGrade`にはFがないため、failedの結果表示もこの段階で接続する。
判定比率gradeとplay outcomeを分離し、passed不明のlegacyをFへ変換しない。

B07ではUI内部のenumをbackendのModモデルと区別し、rulesetが対応capabilityを返す。
`PlayContext`から有効difficulty/session configurationを作り、Importした元chartを変更しない。
Renderer、judgement、HP、ScoreV1、star calculation、結果保存へ同じ確定済み設定を渡す。

| 導入単位 | backend側の必須内容 | 有効化前の確認 |
| --- | --- | --- |
| NF | fail抑制・終了条件・score係数。HPは計算を継続 | HP 0、無得点終端、manual保存、NF記録、通常failとの比較 |
| HR | settings/位置変換・判定窓・HP・score係数・star key | circle/slider/spinnerの上下反転と描画/inputの一致、元chart不変 |
| EZ | settingsだけでなく追加life・回復/再開・clock/audio/入力の停止同期 | life消費、最後のfail、pause/retry、HR排他。settings半減だけで対応済みにしない |
| HD | approach/fade/visibilityのruleset値をRendererへ供給、score/rating/grade | 見え方、判定不変、保存Modsとsilver grade。純粋backendだけでは完了しない |
| DT/HT | audio rate/pitch transportとGameClockの同じ譜面時刻、effective AR/OD/BPM/length | 音とobject同期、EOF/pause/resume/preview/retry、NMとの固定入力比較 |

libGDX Musicを前提にGameClockだけを加速してDT/HTを有効化しない。
desktop側に必要なtransport capabilityを調査し、現在構成で音声速度を実現できない場合はrate Modsを未対応に保つ。
必要な依存追加はこの段階の小さな設計レビューで決める。初期計画にaudio全置換を含めない。
score係数・排他・組合せ・数値丸めはversion付きfixtureで確定。RX/AP/SO/FL/通常AUTOは別段階。
Debug Autoは通常Input API経由かつ非保存を維持し、通常ModsのAUTOと同一扱いにしない。

## B08/B09: Replayと安全なlocal管理

Replayはまずversion付きlocal形式で、`GameplayInput(timeMs, sequence, x, y, physical buttons)`と
`PlayContext`を記録する。入力を通常APIへ再投入し、judgement結果を直接注入しない。
playback clockは音声positionに結果を左右されない決定的な譜面時刻を供給する。
専用run modeでscore/historyの二重保存を防ぎ、内容/engine/ruleset/Mods不一致を検知する。
再生能力がないversionは拒否し、近いversionで成功したことにしない。

記録サイズ/イベント数/時刻順/finite座標を検証し、scoreからは専用directory内のUUID参照だけを使う。
replayを先にatomic保存し、成功後にscoreへ参照を保存。score保存失敗で残るorphanを回収できる設計にする。
圧縮・.osr import/exportは次のPR。既存内部座標/button maskをそのまま.osrへdumpしない。
同入力を30/60/144fps相当と不規則updateで再実行し、判定・score・combo・outcomeを比較する。

B09の削除はlocal score削除と譜面退避を分け、Rendererからfilesystemを触らない。
譜面はtrashへ移動→index更新→library revision→cache失効の順を回復可能な操作として設計。
失敗注入でindex/実ファイル/selectionの整合を確認する。collectionのmissing参照と履歴/過去scoreは保持。
大量VISIBLE譜面の一括削除、公式download/update、online操作は初期対象にしない。

## Backendで解決しない項目

- ranked statusは.osuのBeatmapIDやstar値から導けない。初期はunknown/local表示を維持し、
  将来、明示importされたlocal情報がある場合のみsource/date付きで取り込む。server照会はしない。
- taiko/catch/maniaは`Ruleset`境界を維持して別計画へ。browse/filterできることとplay可能は別。
  mode変換や全rulesetを本計画へ自動追加しない。
- native score bar/manager順、pixel rounding、font、chrome、CookieはUI残件。
  player/Mods供給後のrow表示回帰は行うが、backend完成を全画面stable一致と呼ばない。

## PR粒度と検証

推奨commit/PR: `content identity and revision` → `score schema 3 and play context` →
`local play history` → `local collection store` → `connect collection controls` →
`difficulty preprocessing` → `standard NM calculator` → `difficulty cache and browser projection` →
`health and play outcome` → `NF gameplay` → `HR gameplay` → 各追加Mod → `local replay` → 各管理操作。
計算だけ・保存だけで止めず、各機能の最後のPRで既存UIの受け口まで接続する。

| 変更領域 | 必須検証 |
| --- | --- |
| storage/identity | 既存library/score tests＋旧/新/未来schema、hash不一致、atomic保存失敗・restart。migrationはfixtureで検証 |
| history/collection/search | GLなしmodel test＋production Screen test。Unicode/長名、0/1/多数、filter/Random/selection維持 |
| calculator/cache | 固定数値reference＋failure/cancel/stale/version/重複job。cache hit時に計算しないこと |
| HP/Mods/replay | 固定clock/input列、30/60/144fpsと不規則update。NM回帰、pause/Retry、DEBUG_AUTO非保存 |
| UI接続 | harness `audit`, `score-scroll-contracts`, 機能専用phase。16:9/16:10/4:3、resize、density 1/2 |

各実装PRで関連testと原則`./gradlew build`を実行。重いGL全体testはUI接続PRの最後に実行する。
performanceは1万難易度のcold/warm、cache完了の集中、rapid選曲で、変更前後のframe時間・allocation・
queue長・rebuild数を記録。許容退行幅を実装前のbaselineから決め、数字未測定で軽量化済みと主張しない。

初回PRの受入はB01/B02の内容key照合とschema互換。全段階の完了は、実star・履歴・collection・
対応Mods・保存player/Modsがproduction UIで使われ、旧データを保持し、unsupportedを偽表示せず、
build/回帰/performance条件を満たすこと。replay/管理/rate Modsの未達は個別残件として報告する。

## 仕様の根拠と実装前の固定事項

- [公式Interface](https://osu.ppy.sh/wiki/en/Client/Interface): Difficulty/Recent/Collections、
  local ranking、Optionsの機能を確認。日付groupの厳密境界・callback順を確定する資料ではない。
- [公開OsuDifficultyCalculator](https://github.com/ppy/osu/blob/master/osu.Game.Rulesets.Osu/Difficulty/OsuDifficultyCalculator.cs):
  versionとdifficulty前処理/skill/Mod依存の確認。2026-10-02にraw sourceも確認。
  mutable masterは調査入口のみ。B00で参照commitを固定し、stable 2023互換を別に検証する。
- [NF](https://osu.ppy.sh/wiki/en/Gameplay/Game_modifier/No_Fail)、
  [HR](https://osu.ppy.sh/wiki/en/Gameplay/Game_modifier/Hard_Rock)、
  [EZ](https://osu.ppy.sh/wiki/en/Gameplay/Game_modifier/Easy)、
  [DT](https://osu.ppy.sh/wiki/en/Gameplay/Game_modifier/Double_Time):
  HP/fail、位置・settings、追加life、音声速度が必要な根拠。wikiに曖昧な境界はstable観測で補う。
- [公式.osr仕様](https://osu.ppy.sh/wiki/en/Client/File_formats/osr_%28file_format%29):
  内容MD5、player、Mods、時刻、cursor/button列を確認。local replay初版を.osr互換と呼ばない。

公式asset抽出・保護回避・production接続は行わない。stableは既存read-only toolと許可された
black-box観測で挙動を確認し、内部コードのコピーを目的にしない。

## 計画書作成時の検証

現行コードと旧計画/残件を照合し、local file linkとdiffを確認。
`./gradlew build --console=plain`成功（15 tasksすべてUP-TO-DATE）。
変更は本計画書と残件台帳の参照追加のみ。新規test・GL harnessの再実行はなし。
ログ: `/tmp/osujava-backend-plan-build.log`。
