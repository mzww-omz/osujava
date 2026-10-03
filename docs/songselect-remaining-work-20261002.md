# Song Select 残件の統合台帳 — 2026-10-02

現在の残件は本書を参照する。過去の全面監査・個別修復報告は、その時点の証拠・測定・
変更履歴として保持する。最新の修復開始HEADは `2cb934a`。
今回、下部hover spriteの入力矩形・Back連番provider・preview更新と失敗再試行を修復した。
根拠と再現手順は[解消可能な差の修復](songselect-actionable-differences-20261002.md)を参照。
stableとの完全一致は未達成。
2026-10-03：B05のNM circle/spinner星計算とDifficulty UIを接続済み。
対応範囲・数値検証・次のslider作業は[B05実装記録](songselect-backend-difficulty-20261003.md)を参照。

## 前回閉じた実装漏れ

| 問題 | 修復・設計判断 | 回帰確認 |
| --- | --- | --- |
| ローカルranking scrollbarが表示だけで操作できない | 既存thumbの描画矩形をそのままhitに使用。掴んだ位置を保持してスクロール範囲へ逆変換し、既存modelのfirst indexをclamp。score選択を維持しwheelの端数をクリア | 0/1/少数/100件、端/中間、欄外release、wheel競合、4つの画面比率/サイズ |
| scrollbar下のscoreがクリックされResultsへ遷移する恐れ | score entryより先にthumb pressを処理。releaseまで入力をcaptureし、carouselのpress/drag/hover/soundを抑止 | production Screenテストと実GL harness。欄外へ移動しても選択/Results/carousel scrollを変更しない |
| resize/譜面変更/score保存/overlay/pauseで古いdragが続く恐れ | geometryまたはscore row listの更新でdragを無効化。無効化後もreleaseまでcaptureを保持して他UIへの漏れを防ぐ | controllerの5種の中断、Screenのresize/difficulty/overlay/pause。通常score clickは既存テストで維持 |
| 同一frame内の非表示→再表示でcolour/star cacheが残る | foregroundと同じspriteGenerationを照合してcolour/starも再生成。residentの選択変更/resizeでは保持 | 実ratingを注入したScreenテストでstar再開始とcache交換、resizeで保持、時間経過で完了を確認 |

`ScoreBrowserScroll`はUI inputだけを担当し、score store/Rendererへ入力処理を混ぜない。
barは既存の幅3×scale・最小長18×scaleを維持する。不可視の大きなhit領域、track click、
新しいskin画像は追加していない。ローカルrow単位のスクロールであり、nativeの連続scroll、
barの完全な寸法・色・animation・dispatcher順と一致したとの主張はしない。

## 根拠

- stable b20230727.9、SHA-256
  `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
  [既存read-only tool](../tools/stable_results_inspect.py)で`06000fb9`を確認。
  resident解除、sprite listの解放/null化、star list Clear、rating cache -1へのリセットを観測。
  行モデルのidentityが同じでも、破棄したspriteのtransform/ratingは再使用しない仕様と判断した。
  ILの全文や内部コードはrepoへ追加していない。公式asset抽出・保護回避は行わない。
- [osu!公式Interface wiki](https://osu.ppy.sh/wiki/en/Client/Interface)のLocal Rankingが対象。
  ローカルstoreだけを使用し、online情報を取得・捏造しない。
- thumb dragとscroll offsetの対応は
  [公開osu-framework ScrollContainer](https://github.com/ppy/osu-framework/blob/master/osu.Framework/Graphics/Containers/ScrollContainer.cs)
  の公開interactionを参考に、既存Java geometry/modelから独立実装した。
  これはstableのbar寸法・時間契約を確定する証拠ではない。

再調査コマンド:

```sh
/tmp/osujava-songselect-audit-venv/bin/python tools/stable_results_inspect.py \
  '/home/coder/workspace/b20230727.9/osu!.exe' --methods 06000fb9 06000fcd
```

## 未完了の作業一覧

優先度はSong Selectの違和感・入力影響順。確認できない仕様は計測を先に行う。
backend依存の項目をUIだけ有効にしない。他mode Gameplay等の全実装へ作業範囲を拡大しない。
R06/R07/R08の実装順・保存互換・受入条件は
[ローカルbackend改善計画](songselect-backend-improvement-plan-20261002.md)を参照。
B01/B02（内容照合・更新通知・score schema 3・play context）と
B03（manual履歴・追加日時・Recently Played・日時検索）、
B04（local Collections CRUD・membership・Collections tab/Group・Manage Collections）は実装済み。
検証と後続の具体的作業は[backend実装進捗](songselect-backend-progress-20261002.md)と
[B03実装記録](songselect-backend-history-20261002.md)、[B04実装記録](songselect-backend-collections-20261003.md)を参照。

| ID / 優先度 / 分類 | 現在の差異・根拠 | 次の具体的作業と終了判定 |
| --- | --- | --- |
| R01 / P1 / 追加観測 | global input dispatcherとnative pixel rounding。行と下部spriteの生成時priority、下部raw矩形は修復済みだがnative `06003267`の登録途中の候補更新とJava最終snapshot評価には差がある | 同一許可skin、重複canvas、固定pointer/press/release列でcallback順と境界pixelを比較。frame単位の候補・選択・click結果をfixture化してから変更 |
| R02 / P1 / 追加観測 | buffer外を含むsprite再生成順と全animation clock。今回colour/starの寿命漏れを修復したがnative全体との同期比較は未実施 | many difficultiesでrapid collapse/expand、sort/filter、viewport入退場を固定時刻で比較。生成・色・星・thumbnailのsnapshotと時計の差を別々に閉じる |
| R03 / P1 / 追加観測 | HD eligibilityはJavaの共通resolverで常時HD優先。nativeはdisplay height>=800またはoption、別option、GL max texture条件を持つ | native `06002340`のoptionの意味・display対window・reload契機を測定。SD/HD混在fixtureとresizeでprovider/density期待値を確定し、共有resolverを壊さず実装 |
| R04 / P1 / 追加観測 | Back/下部buttonのraw hover矩形、透明部分のhit、静的/連番provider競合、壊れたHD時の探索停止を修復済み。INI owner/mask、音alias、focus時の連番時計は未確定 | nativeと同期した異寸法連番・focus/復帰の比較でframe/crop/hit/時計を確認。INI・音のownerを独立に確定。旧台帳V08–V13参照 |
| R05 / P1 / 追加観測 | metadata typography/font metrics、全frame合成。AWTとnative GDIの差、独自5行構成、Greylooks fallbackと公式defaultの差 | 再配布可能な同一font/skinでLatin/CJK/結合文字・長文を720/800/768高で比較。baseline・省略・影・originを測定して調整。公式素材の抽出で埋めない |
| R06 / P1 / backend依存 | B05でNM v6+ circle/spinner星を独立計算・永続cacheから供給済み。Slider/他mode/pre-v6/Mods/範囲外設定は未検証でunknown。本当のranked statusはlocal sourceなし | 次は公開referenceのslider path/nested/lazy cursor中間値をfixture化して対応拡張。stable実機の数値照合も未実施。ranked statusは実metadataがある場合のみ接続 |
| R07 / P1 / backend依存 | Recently Playedと日時検索はB03、Collections tab/GroupとOptionsのcollection管理はB04で接続済み。B05のDifficulty tab/sort/group/stars検索も接続済み。通常Mods/他mode、譜面/score削除等は未実装 | 次はB05 slider等の数値範囲拡張。管理拡張はB09。Recently Played境界とnative collection manager/editorの寸法・animationは追加観測 |
| R08 / P2 / 観測・backend依存 | 新規scoreのplayer/Mods/rulesetをschema 3へ保存しranking/Resultsへ接続済み。現GameplayはNMのみ、旧scoreの未収集情報はunknown。replay、score native座標/grade配置/managerの全比較、barのnative形状・連続scroll・track操作は未完了 | score専用native managerを0/1/多数/選択/hoverで比較。通常ModsはB07、実replayはB08で収集後に接続。carousel barとscore barを分けて寸法/hit/scrollを検証。旧V17/V18 |
| R09 / P2 / 追加観測 | 同frameの選曲/音声更新と、失敗音源の選択変更時のみ1回再試行を修復済み。同一audio pathの健康なstreamは継続。別PreviewTime、未指定seek=0、loop/focus/Gameplay復帰のnative契約は未確定 | 同一音源で別PreviewTime、失敗後再選択、focus/minimize、Gameplay短時間往復を観測。位置・再生/停止・fade・再試行をfake Music testへ落とす。旧T01/T02 |
| R10 / P2 / layout方針 | chromeの安全上限とrow scissorはnativeとの差。極端なportraitではscore幅0で非表示になる | 正常skinのnative clipと巨大/異常skinの安全方針を区別。portraitを対応対象にする場合は専用レイアウト方針とresize回帰を追加 |
| R11 / P2 / 素材・追加観測 | CookieはJava素材。拍/粒子・hover/入退場clockは近似 | 独自素材を維持し、音声position/frame固定の自作fixtureで位相・半径・fade・layer順を比較。公式素材の再配布は行わない |
| X01 / 対象外 | Bancho/online ranking/chat/profile/公式server接続 | 完全ローカル要件のため実装しない |

R04の透明部分のhitと連番providerは今回対応済み。次はR01の物理pixel境界とglobal callback順、
R04のfocus/復帰時計・INI/音ownerを、nativeの参照実行が可能な環境で測定する。
R03は共通resolverへの影響が広いため、option/display/reloadの未確認状態で変更しない。

## Asset / dead code 状況

[全面棚卸し](songselect-full-audit-20261002.md#skin-asset棚卸し)の48画像・12音声の分類を継続。
新たな画像loadや削除はない。ranking barはproceduralの既存要素を入力へ接続した。
online/replay/silver gradeは実データ・機能がないため意図的に未接続。
新規scoreの明示local playerと既知NMはB02で文字表示へ接続。通常Modsの効果・iconはB07まで未接続。
`star2`の透明fallback、独自mode glyph、missing grade/star/chrome fallbackを維持。
旧body scan等のobsolete削除は前回対応済み。今回、Backのalpha map/getter/連番ごとのPixmap再decodeを削除。
Selectionのalpha範囲は画像診断に使用するがinputには使用しない。画像・音の使用分類は維持する。

## 最新の検証・commit

[backend実装進捗](songselect-backend-progress-20261002.md)と
[B03履歴・日時の実装記録](songselect-backend-history-20261002.md)、
[B04Collections/Optionsの実装記録](songselect-backend-collections-20261003.md)に最新build・GL harness・commitを記録。
[解消可能な差の修復](songselect-actionable-differences-20261002.md)はその直前のUI修復記録。
下記は前回修復の検証履歴であり、現在の総test数ではない。

## 前回の検証・変更

- `./gradlew build --console=plain`成功。core 131 suites / **1,277 tests**、
  lwjgl3 2 suites / **4 tests**。failure/error/skippedすべて0。
  今回の追加はsprite寿命1、drag controller12、Screen入力5の計18 tests。
- `score-scroll-contracts`: **12 scenes / 48 PNG / 48 scripted transition frames**とnavigation/disposal成功。
  1280×720、1280×800、1024×768、1280×720 framebuffer density 2。
  Greylooks、procedural fallback、HD-onlyでscore clipの画素sentinelも確認。
- 既存`audit`: **84 scenes / 172 PNG / 1,256 scripted transition frames**とnavigation/disposal成功。
  empty/single/many、long/Unicode、背景/thumbnail欠損、search、ranking、巨大chrome、resize、
  保存・再読込、density 1/2を含む。今回のdrag suiteと合わせ96 scenes / 220報告PNG。
- [4:3のdrag中capture](songselect-residual-captures-20261002/score-drag-4x3.png)。
  自作fixture/既存許可skinで、nativeとの同期captureではない。
- idle drag controllerはboolean判定のみ。drag中は保持中geometryとrow listを使用しO(1)。
  全beatmapの毎frame scan、texture/font生成、score再formatは追加していない。
  今回CPU/GPUの前後benchmarkは再実施していない。

主な変更: `SongSelectScreen`, `SongSelectRowColourAnimation`, 新規`ScoreBrowserScroll`,
`ScoreBrowserModel`, navigation/drag tests、`SongSelectVisualHarness`。
Renderer/Importer/Gameplay/score保存schema、Java 21/libGDX構成は維持。

- `66de2ad` — `fix(song-select): reset colour and stars with recreated row sprites`
- `34d55e4` — `fix(song-select): connect local ranking thumb drag and capture input`

ログ: `/tmp/osujava-residual-final-build.log`, `/tmp/osujava-score-scroll-final.log`,
`/tmp/osujava-residual-final-audit.log`。
IL確認: `/tmp/osujava-residual-sprite-lifetime.il`。一時資料消失時は上記tool/harnessで再現可能。

```sh
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=score-scroll-contracts \
  -PsongSelectOutput=/tmp/osujava-score-scroll
```
