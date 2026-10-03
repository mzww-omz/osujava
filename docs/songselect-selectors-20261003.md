# Mode / Mods / Beatmap Options のstable画面修復 — 2026-10-03

変更開始HEAD: `0917bb0`。旧Modeの横並びtile、Modsのflat band、Optionsの独自小panelを、
stableの左下flyout・全画面Mods dialog・6項目Options dialogへ変更した。
本書は入口の画面構成・入力・animationの記録。通常Mods等のGameplay backendの実装完了を意味しない。

## 根拠と観測範囲

- [公式Interface wiki](https://osu.ppy.sh/wiki/en/Client/Interface)のGame mode selector / Game modifiers / Beatmap options。
  公開スクリーンショットでModeの縦順、Modsの3段とReset/Close、Optionsの6項目・配色・全画面暗転を確認。
- [公式Keyboard shortcuts](https://osu.ppy.sh/wiki/en/Client/Keyboard_shortcuts)と
  [Game modifier](https://osu.ppy.sh/wiki/en/Gameplay/Game_modifier)でF1/F3、番号、Modsの並び・shortcutを確認。
- stable `b20230727.9`、SHA-256
  `bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
  [既存read-only tool](../tools/stable_results_inspect.py)、dnfile 0.18.0 / dncil 1.0.2で
  dialog構築・座標・変形の数値を確認。実行・asset抽出・暗号化文字列復号・保護回避はしていない。
  内部コード/IL全文、公式スクリーンショット/公式assetはrepoへ追加していない。
  同一入力列によるstable実機との同期captureは未実施。

| 確認対象 | binary専用token | 読み取った契約 |
| --- | --- | --- |
| Options構築 | `0600138d` | Manage / Delete / Remove from Unplayed / Clear local scores / Edit / Cancelの6項目、Green/OrangeRed/Orchid/Grey系の配色 |
| Mods構築・icon配置 | `06002efe`, `06002f00`, `06002189` | 3段、group pitch 60、icon centre X=240+66×column、ScoreV2を含む |
| generic dialog・button | `06001166`, `06001172`, `060043f2` | 480高基準、button幅460/高40/pitch50、font指定14×高さ/18 |
| dialog入場 | `06001172`, `06002b54` | index×60ms遅延、交互±40水平offset、800ms OutBounce（easing 33）、alpha OutQuad（easing 1） |
| dialog close | `06001174` | 120ms fade処理 |
| 背景暗転 | `06001166` | black alpha 240/255 |

再調査例:

```sh
/tmp/osujava-songselect-audit-venv/bin/python tools/stable_results_inspect.py \
  '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 0600138d 06002efe 06002f00 06001166 06001172 06001174 060043f2 06002b54
```

Modeの幅230/row高80/bottom60、dialogの絶対top位置、font補正、hoverの色、opening fade 200msは
Modeの背景dim 0.35も含め、公開画像を参考にしたJava側の配置・可読性方針であり、native実機の厳密な測定値ではない。
Modeとは別のranking display dropdownをMode寸法の根拠に使わない。

## 修復内容

| 画面 | 修復 | 入力と機能の境界 |
| --- | --- | --- |
| Mode | 左下に下からosu! / taiko / catch / mania。既存large mode画像を使用。横並びtileと常時のUnavailable文言を除去 | osu!選択・外側クリック・Escで閉じる。外側クリックを下のSong Selectへ流さない。Ctrl+1も閉じる。未対応modeは無効、hover時に説明。Mods用の2で閉じない |
| Mods | full-screen dim、上部説明、中央Score Multiplier、緑/赤/白の3カテゴリ、native順のicon、中央下の縦2ボタン | F1 / bottom Modsで開く。1 Reset、2 / Esc / F1 Close。通常Modsは未実装のため無効。倍率は実状態の1.00x。AutoはF6 debug専用と明示し、Modとして選択しない |
| Options | full-screen dim、左上の対象譜面と質問、6色付き番号付きbutton | F3 / bottom Options / 既存右クリック経路。1は実local Collection managerへ接続、6 / Escで閉じる。2–5は無効、hoverで未対応表示。削除・編集等を実行したふりはしない |

`SongSelectSelectorLayout`で画面比率ごとのgeometryを共有し、buttonの水平animation offsetも
描画・hover・clickで共有する。入場前の透明buttonはmouse commandを受けない。
論理close後は直前の表示snapshotを120ms描画し、再openで前のentranceをresetする。
別selectorを開く際は前selectorのclosing描画を終了し、二重暗転を避ける。
入力capture、score dragのcancel、search/navigation/import/play/debug/wheelのmodal優先を維持する。

Optionsから進む既存Collection managerのCRUD、Unicode名、membership、Group/tabへの反映と保存は維持。
manager内部の独自panelは今回のnative Options dialogと分けて保持した。
Rendererにはstorage/Gameplayの処理を追加していない。

## Skinと描画

- 既存mode画像と13個のMods画像を使用。以前未接続だった`selection-mod-scorev2`をloader/selectorへ接続。
  bundledはSD 103×66、@2x 206×132（logical 103×66）。GameplayのScoreV2は未実装。
- current → configured fallback → bundled、provider別density、@2xのlogical寸法・crop処理を維持。
  透明画像の読み込み成功をmissingと見なさない。画像がないときは既存mode glyph / Mods acronymと名前。
- dimはSong Select全体のforegroundより上、selectorの文字・icon/buttonはdimより上、画面遷移coverは最後。
  dialog buttonは自作shape。公式素材の抽出・追加・再配布はない。
- AWT SansSerifの幅とnative fontは異なるため、dialog fontを0.85倍、カテゴリを18 logical unitsへ補正し、
  カテゴリ名を省略せずiconと重ならない幅に収める。buttonは横・縦の両方向を中央揃え。
  長い対象譜面は既存text fit/cacheで省略する。
- scaleはheight/480を基本にwidth/640を上限として狭いwindowでも画面内に収める。
  通常16:9 / 16:10 / 4:3ではheight基準。portraitは安全縮小でありnative仕様の完全再現ではない。
- frameごとのtexture/font生成経路は増やさない。既存のbounded text/texture cacheを使用。
  selector内の少数のgeometry計算のみ。library全件layout/scanを追加していない。CPU/GPUの前後benchmarkは未実施。

## 主な変更ファイル

- `SongSelectSelectorLayout`, `SongSelectMenuAnimation`: geometryとframe-driven animation。
- `SongSelectToolboxState`, `SongSelectToolboxOverlay`, `SongSelectSkinAssets`: Mode/Mods表示とasset接続。
- `SongSelectCollections`, `SongSelectCollectionsOverlay`: Options表示、capability guard、closing presentation。
- `SongSelectInput`, `SongSelectScreen`, `SongSelectRenderer`: modal routing、状態遷移、frame接続。
- `SmoothUiFont`, `UiView`: 既存cacheを使う横/縦中央揃え描画。
- layout / navigation / collections / skin test、`SongSelectVisualHarness`: geometry・入力・密度・描画回帰。

## 検証

- `./gradlew build --offline --console=plain`成功。core 146 suites / **1,429 tests**、lwjgl3 2 suites / **4 tests**。failure/error/skippedすべて0。今回追加は6 tests。
- `selector-contracts`: **12 scenes / 108 PNG / 3,960 scripted transition frames**、navigation/disposal成功。
  1280×720・1280×800・1024×768・1280×720 framebuffer density 2、Greylooks/fallback/@2x-only。
  Mode外側click・未対応click、Mods Reset/Close/hover/enter/close、Options disabled/Manage/Cancel、modal入力抑止を確認。
- 既存`collections-contracts`: **16 scenes / 100 PNG / 1,588 scripted transition frames**成功。
  Unicode CRUD、membership、複数Collectionに同一difficultyがあるケース、空/多数/missing、Group/tab、保存/再読込を確認。
- 既存`audit`: **84 scenes / 172 PNG / 1,256 scripted transition frames**成功。
  empty/single/many、long/Unicode、missing background/thumbnail、search、score、巨大chrome、resize、保存/再読込等。
- 旧`phase5a-current-mode-view`も1 scene成功。Mode外側clickの変更が旧harnessのmodal前提を壊さないよう、outside-dismissは新selector suiteで検証する。
- unit testでlive resize中のselector状態・選択保持と変更後のhit、portrait安全範囲、入場delay/alpha/easing、closing/reopen、
  invisible buttonのclick禁止、@2x ScoreV2のproviderとlogical寸法、Ctrl+1のtyped抑止と解除を確認。
- 最初のCollection GL再実行は、前回中断した出力directoryの永続fixtureを再使用してmember数の期待値が不一致になった。
  fresh directoryで全16 scenesを再実行して成功。productionのCollection処理は変更していない。

以下のcaptureは自作fixtureと既存許可skinのosu!java画面。公式stableの画面ではない。

- [Mode](songselect-selector-captures-20261003/mode.png)
- [Mods hover](songselect-selector-captures-20261003/mods.png)
- [Options](songselect-selector-captures-20261003/options.png)

ログ: `/tmp/osujava-selectors-final-build.log`, `/tmp/osujava-selectors-committed-selector-contracts.log`,
`/tmp/osujava-selectors-final-collections-contracts.log`, `/tmp/osujava-selectors-final-audit.log`。

再現コマンド（保存fixtureの影響を避けるため、未使用の出力directoryを指定する）:

```sh
./gradlew build --offline --console=plain
JAVA_TOOL_OPTIONS=-Xmx256m xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness \
  --offline --console=plain -PsongSelectPhase=selector-contracts \
  -PsongSelectOutput=/tmp/osujava-selector-new-run
# songSelectPhaseをcollections-contracts / auditへ変更し、それぞれ別のfresh directoryで確認。
```

Commit:

- `9f319b0` — `fix(song-select): match stable selector layouts and transitions`
- `ba8d854` — `test(song-select): cover selector interaction and visual states`
- 本記録・capture・asset分類・残件台帳の更新は後続のdocs commit。

## stableとの差として残す作業

| 残件 | 次の作業と完了判定 |
| --- | --- |
| Modeの絶対寸法・hover/開閉animation | 同一許可skinのstable実行でleft/bottom anchorと開始/中間/終了時刻を測定。現在の公開画像由来の寸法・200ms opacityを置換し、resizeと入力列を照合 |
| dialogのfont・影・gradient・hover/press/音の細部 | 同じ再配布可能font/skinでnativeとJavaの全button/text pixels、baseline、hover/down/upを比較。現在はSansSerif補正とshapeによる近似 |
| Modsの選択・互換・倍率・Gameplay適用 | backend計画B07で実Ruleset/Gameplay効果・組合せ制限を実装したものだけ有効化。Nightcore/Perfect等の派生選択やmod選択animationもその時点で接続 |
| taiko/catch/mania選択後のSong Select/Gameplay | 実mode model/filter・Ruleset対応を追加してから有効化。現在のdisabled項目を画面だけで切り替えない |
| Options 2–5 | B09でlocal譜面削除、Unplayed操作、score削除をrollback/確認付きで実装。Editorは実backendが必要。未実装buttonを有効化しない |
| Collection manager内部 | CRUD/backendは動作済み。native manager/editorの内部配置・paging・animationは未照合。今回はOptions入口までの修復 |
| 完全1:1認定 | wiki画像とread-only数値比較、Java unit/GL検証まで。stable実機との同一入力・同一skinでの同期計測を行い、未確認値を別々に閉じる |

通常Modsはemptyのまま、Bancho/API/公式production service接続は追加していない。
