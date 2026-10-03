# Song Select backend実装進捗 — 2026-10-02

[改善計画](songselect-backend-improvement-plan-20261002.md)の初回対象B01/B02を実装した。
開始HEADは `b9ea4f6`。完全ローカル、Java 21/libGDX、責務分離を維持する。
UIの参考対象は引き続きstable b20230727.9。今回の保存形式・内容照合はosu!java独自の契約であり、
stableの内部schemaや実装を再現したとの主張はしない。公式asset抽出・production service接続はない。

## B01: 内容照合とlibrary更新

- 新規`BeatmapContentKey`は既存parserのraw SHA-256＋modeを使用する。
  setId＋pathの`DifficultyIdentity`はrow選択・保存元の位置として維持する。
- hashが一致したscoreだけをcurrent chartのbest/played/gradeに使用する。
  同pathの1byte変更後に古いscoreを誤表示せず、同内容の移動・複製ではscoreを参照できる。
  SHA-256は元ファイルの全byteが対象なので、metadata/commentだけの変更も新内容になる。
- hashなしの旧recordは保存元のpathで閲覧可能なlegacy行として残し、順位を`—`、
  見出し・日時をcontent unverifiedとして表示する。確認済み行の後へ置き、best/playedへ含めない。
  hash不一致の旧recordは削除せず、従来location queryで参照可能。現在のchart欄には混ぜない。
- `BeatmapLibrary.all()`はimmutable snapshotを再利用する。保存成功時だけrevisionとsnapshotを更新する。
  Song Selectはsnapshotの変更時だけbrowserを同期し、preview/score対象を更新する。
  更新前に押したrowのreleaseが更新後の譜面を実行しないよう、古い入力をキャンセルする。
- score query/行のformatはrevision単位でcacheする。毎frameのファイルread/hashや全scoreのformatはない。

## B02: schema 3と開始時のplay context

新規Gameplayの作成時に内容key、mode、ruleset ID/version、scoring version、Mods、
local player ID/当時の名前、run modeをimmutable `PlayContext`として確定する。
同じ値を最終Resultsとscore保存へ渡す。Retryは従来どおり新規play ID/contextを生成する。

| 保存値 | 実装した契約 |
| --- | --- |
| schema 3 | PropertiesのUUIDファイル・atomic保存を維持。contextがない既存APIはschema 1/2を保存できる |
| 内容/採点由来 | `beatmapSha256`、`beatmapMd5`、`mode`、`rulesetId=osu`、`rulesetVersion=osu-java-standard-1`、既存ScoreV1のversion |
| Mods | 正規化済みimmutable list。現在Gameplayは既知の空構成（NM）。旧scoreのcontext nullはMods unknown |
| local player | optionalなUUID/name。当時の`playerName`を保存し、profile renameで過去scoreを変更しない |
| run mode | manualのみ保存。Debug Auto contextをmanual saveへ渡しても拒否する |
| passed / health / replay | 未収集のまま。B06/B08まで捏造しない。HP/failは未実装 |

schema 1/2/3を読み、load時はファイルを書き換えない。不正hash/context・将来schemaは元のbytesを保持し、
当該recordだけskipしてPARTIALを表示する。schema 3は既知NMの空`mods`も必須とし、欠落をNMへ補完しない。
既知modeのcontextは内容queryでもmodeを照合する。schema 2のraw hashは元.osuにModeを含んでいる。

profileは明示opt-in。起動JVMへ `-Dosujava.playerName=LocalPlayer` を渡すと
`~/.osujava/player.properties`へ保存する。同じ設定で名前を変えるとUUIDを保持してrenameする。
設定がなければ保存済みprofileを読み、新規作成・OS username推測はしない。
壊れた/将来schemaのprofileは指定名があっても上書きせず、player unknownで継続する。
profile設定画面はまだない。Gradle起動では例えば次を使用する。

```sh
JAVA_TOOL_OPTIONS='-Dosujava.playerName=LocalPlayer' ./gradlew :lwjgl3:run
```

ranking/Resultsは保存されたMods・採点由来・名前だけを表示する。長い名前でModsが隠れないよう
短い行ではMods/ScoreV1を先に置く。unknownをNMにしない。Results headerは既存のcache付き
Unicode fontを使用し、名前やmetadataの欠字を改善した。rowのhitbox/clip/pitchは維持する。
ruleset versionは保存値として保持し、画面に新たな技術詳細欄は追加しない。

## B01/B02実装時の検証

- `./gradlew build :lwjgl3:hudHarnessClasses --console=plain`成功。
  core **135 suites / 1,305 tests**、lwjgl3 **2 suites / 4 tests**、failure/error/skippedすべて0。
  今回は内容照合6、更新中入力1、context/profile/表示8の計15 testsを追加した。
- `backend-contracts`: **16 scenes / 28 PNG / 672 scripted transition frames**。
  schema 3の再読込、既知NM/Unicode名、legacy unknown、同path内容不一致、混在行の順位/played判定、
  revision内のrow/query cache保持、実Results遷移でcontext保持、実Gameplay作成時のprofile freezeを確認。
- `score-scroll-contracts`: **12 scenes / 48 PNG / 48 scripted transition frames**。
  Greylooks、procedural fallback、HD-onlyのclip sentinel、thumb capture、通常score clickを確認。
- 既存`audit`: **84 scenes / 172 PNG / 1,256 scripted transition frames**。
  empty/single/many、difficulty展開/折畳み、long/Unicode、背景欠損、search、ranking、巨大chrome、
  resize、save/reload、実navigation/disposalを確認。
- 3 suiteとも1280×720、1280×800、1024×768、1280×720 framebuffer density 2で成功。
  合計**112 scenes / 248報告PNG / 1,976 scripted transition frames**。
  PNG総数にはharness起動時の自作fixture生成画像が別に存在するため、報告capture数で集計する。
- GL captureの目視で、長いplayer名によるModsの省略とResults Unicode欠字を発見し修復。
  Rendererのclip/hitboxの回帰も確認した。CPU/GPUの前後benchmarkは未実施。

再現コマンド（outputは検証ごとに分ける）:

```sh
./gradlew build :lwjgl3:hudHarnessClasses --console=plain
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=backend-contracts \
  -PsongSelectOutput=/tmp/osujava-backend-contracts-reproduce
# songSelectPhaseをscore-scroll-contracts / auditへ変えて既存回帰を再現する。
```

ログは `/tmp/osujava-backend-final-build.log`、
`/tmp/osujava-backend-gl-{backend-contracts,score-scroll-contracts,audit}.log`。
最終backend captureはanimation skipとcursorを避けた状態で再取得し、
`/tmp/osujava-backend-final-captures`へ保存した。同じ16 scenesの再取得は上の合計へ重複加算しない。

commit:

- `0f4bb1c` — `fix(song-select): match local scores to beatmap content and publish library revisions`
- `16262a2` — `fix(song-select): cancel captured input when the library changes`
- `3c10a1d` — `feat(score): persist frozen local play context with schema 3`
- `ffd1b8e` — `test(song-select): cover local backend provenance and content isolation`

本書・計画/残件台帳は別のdocs commitとして記録する。

## B03: 履歴・日時を接続済み

manual Gameplayの実開始と終了/abortをscoreと別に保存し、Recently Played、Last Played / Date Added sort、
`played` / `unplayed` / local `added`検索へ接続した。成功importの初回日時は内容単位で保持する。
旧データの未知日時・中断状態は捏造せず、通常loadで旧schemaを書き換えない。
音声ファイル名が空の譜面のrestart消失、将来library schemaの再import上書きも修復した。

最新の仕様・テスト・commit・残件は[B03実装記録](songselect-backend-history-20261002.md)を参照。
buildはcore 139 suites / 1,332 tests、lwjgl3 2 suites / 4 testsが成功。
新規historyと既存3 GL suiteを合わせ128 scenes / 280報告PNG / 3,512 scripted transition framesが成功。
HP/failは未実装で、COMPLETEDはsession終端だけを表す。次はB04 local Collections / Options。

## 後続・stableとの差

2026-10-03：B04も実装済み。local Collections CRUD、内容membership、Collections tab/Group、
F3/右クリック/下部OptionsからManage Collectionsを接続した。Date Addedの方向もwikiに合わせ修正した。
最新の検証・commit・残件は[B04実装記録](songselect-backend-collections-20261003.md)を参照。
core 142 suites / 1,353 tests、lwjgl3 2 suites / 4 tests、GL 160 scenes / 440報告PNGが成功。
B05はNM circle/spinner・検証済みlegacy curve Slider、worker/cache、星/情報/Difficulty tab/sort/group/searchまで接続済み。
[B05実装記録](songselect-backend-difficulty-20261003.md)に対応範囲と未検証のpath/timing等を明示した。

| 残件 | 次の具体的な実装 |
| --- | --- |
| B00数値reference固定 | B05の公開2023 commitと20220902 version、許容差、自作38fixtureを固定・照合済み。未検証path/timing/B06 HPのreferenceは次段階で拡張 |
| B03 history / addedAt — 実装済み | nativeの小数日丸め/group境界は追加観測が必要。現在は明示したlocal分類。詳細はB03実装記録 |
| B04 Collections / Options — 実装済み | native managerの厳密な寸法/editor/animationは未比較。local CRUDと既存UIへの接続は完了。管理操作の拡張はB09 |
| B05 star / Difficulty — 部分実装 | NM v6+ circle/spinnerとv8+検証済みlegacy curveは計算・cache・UI接続済み。HitObject skip情報は伝播済み。次はsetting/timingのsource品質、SV/NaN/同時刻timing/pre-v8 tick距離、実大規模work budget評価、pre-v6/Mods、stable実機の数値照合。未検証chartはunknown |
| B06 HP / fail | 実HP/終端を収集する。Resultsは既にpassed=falseのF表示に対応済みで、追加すべき中心はGameplayの計算・収集 |
| B07通常Mods | NF→HR、後にEZ/HD/rate系。保存用listができたこととModの効果実装を混同しない。銀gradeは実Mod対応後 |
| B08 replay | 実入力記録と再実行。保存成功した記録だけをscoreへ参照として接続する |
| B09 local管理 | score削除/譜面退避の整合・復旧を実装し、Optionsへ接続する |

現ランキングは確認済み行とlegacy行を区別したosu!javaのlocal構成。
採点方式の異なる旧scoreや未収集Modsがあるため、異なる採点方式・条件の同点比較を保証しない。
native score managerの座標・animation・連続scroll、フォントの完全一致も未完了。
最終Results captureではGreylooksのcombo/accuracy labelと数値の一部重なりも確認した。
再現は `backend-contracts` の `1024x768-1x-phase4-backend-known-results.png`。
別のResults layout修復として、同skinのlabel/numberのoriginと寸法を測定し、Results専用harnessで回帰を追加する。
古いhash不一致scoreの専用履歴画面とprofile編集UIは今後必要。ファイルは保持されるが現譜面欄では閲覧できない。
native同期captureを行ったとの主張はしない。online ranking・公式status取得は対象外のまま。

主な変更は `BeatmapContentKey`、`BeatmapLibrary`、`LocalScoreStore`、`PlayContext`、
`LocalPlayer/Profile`、`GameplayScreen`、`SongSelectScoreSnapshot/Screen/Renderer`、
`ScoreBrowserModel`、`ResultsSnapshot/Presentation/Screen`と関連tests/harness。

## B05: 検証済み範囲の星評価を接続

2026-10-03、開始HEAD `e419d84`。独立calculator、bounded worker、内容/version別の永続cache、
row星/selected情報、Difficulty tab/sort/group、`stars`検索を実装した。
公開2023版のC#中間値を12自作fixtureで照合した。B05全体の完了にはslider等の前処理検証が必要。

最終buildはcore 145 suites / 1,386 tests、lwjgl3 2 suites / 4 tests、failure/error/skip 0。
GLは新規difficulty＋既存collections/backend/auditの4 suite、128 scenes / 372 PNG / 6,780操作frameが成功。
仕様・根拠・主な変更ファイル・commit・次の作業は[B05記録](songselect-backend-difficulty-20261003.md)を参照。

2026-10-03、開始HEAD `834df09`からLinear Sliderのfloat path/nested/lazy cursor、Slider stacking、
aim/speed/rhythmを拡張した。自作24fixtureの中間値まで照合し、旧UNSUPPORTED cache移行とwarm再利用も確認。
今回buildはcore 145 suites / 1,401 tests、lwjgl3 2 suites / 4 tests、failure/error/skip 0。
今回GLはdifficulty-contracts 12 scenes / 96 PNG / 4,344操作frame。前段の128 scenesとは合算しない。
具体的範囲・次に必要なcurve/timing・parser skip情報の残件は[B05記録](songselect-backend-difficulty-20261003.md)を参照。

2026-10-03、開始HEAD `363ee06`からBezier/Perfect/Catmull/mixed Sliderに対応した。
公開framework pinと公開path-decoding methodをoracleに追加し、計38fixtureの中間値まで一致。
今回buildはcore 145 suites / 1,418 tests、lwjgl3 2 suites / 4 tests、failure/error/skip 0。
GL difficulty-contractsは12 scenes / 156 PNG / 7,044操作frame。次はparser skip情報・timing境界・
実大規模work budget評価・stable実機照合。詳細は[B05記録](songselect-backend-difficulty-20261003.md)を参照。

2026-10-03、開始HEAD `5c921bf`から破損HitObjectのskip件数をparser→asset解決→library再読込→ratingへ伝播した。
部分的な星/全行skipの0星を抑止し、Import warningとversioned cacheの移行・warm再利用・source修復を検証。
buildはcore 145 suites / 1,423 tests、lwjgl3 2 suites / 4 tests、failure/error/skip 0。
GL difficulty-contractsは12 scenes / 192 PNG / 8,664操作frame。次はsetting/timingのsource品質通知とtiming境界、
実大規模work budget評価、stable実機比較。詳細は[B05記録](songselect-backend-difficulty-20261003.md)を参照。
