# Song Select 1:1対応 — 残phase台帳

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)と
[Skin独立調査](skin-stable-independent-audit-20260929.md)を、phase 1〜6の実装後の残作業へ整理したもの。
phase 7以降の番号は今回付与した実装単位で、当初から確定していた工数・完了予定ではない。
追加調査で依存や大きさが判明した場合は、根拠とともに分割する。

## 完了した実装範囲

- phase 1: Skin parser/providerの基礎契約、selection anchor、10枠の星。
- phase 2: 永続行、親子Group、表示行projection、Group操作。
- phase 3: keyboard focusとplayable selectionの分離、navigation/確定。
- phase 4: 行の参照座標とX/Y補間、hoverによる間隔と曲線。
- phase 5: viewportの指数慣性、wheel、選択/focus追従、曲線への予測移動量。
- [phase 6](songselect-parity-phase6-20260929.md): drag中の速度推定、直接移動、静止時間を使うrelease慣性、入力→積分→描画の更新順。

これは各phaseで閉じた静的契約とJava側検証の範囲を示す。実機を含む全画面の1:1一致が完了したという意味ではない。

## 残phase

| Phase | 対象 | 主な作業と完了条件 |
| --- | --- | --- |
| **7** | Group初期配置・画面外からの復帰 | `06003273`のGroup開閉初期配置、`06003263/3266`の再進入・snap・sprite可視化。展開/縮小/並べ替え/スクロール往復時の行位置と寿命を時系列fixtureで照合する。 |
| **8** | 入力判定・dispatcher | native hitboxと優先順位、押下候補・クリック取消距離、右button位置指定、修飾キー、repeat、wheel/callback/polling順。phase 6で残した6 logical unitsのクリックslop、release時の最終移動の扱い、drag-out callbackを閉じる。同一入力列のfocus/selection/play requestと時刻を比較する。 |
| **9** | 行の状態別描画・animation | 状態0〜4のtint/alpha、foregroundの生成・破棄・継承、星のscale/cropと500ms・index遅延、thumbnail/grade/mode/textのorigin/depth/blend/clip。各要素の台帳と中断を含む時間テストを揃える。10枠への変更済み部分は再実装しない。 |
| **10** | Browserデータ・検索・分類 | tags/source/Unicode/引用/比較検索、sort/groupの残条件、日付・mode・rank・collection/favourite、近い難易度を選ぶ規則、metadata/score。必要な情報をImport→Library永続化→index→行へ保持する。オンライン未取得値を捏造しない。評価互換はRuleset側の依存として追跡し、見た目の比較では同じ評価を固定する。 |
| **11** | フォント・画面構成 | GDI系文字測定とJavaの字幅/baseline/省略/Unicode fallback、DPI・丸め・影。chrome予約/延長、search/tab/metadata/score/Mode/Mods/Options/Back/Cookie/scrollbarのdepth・clip・hitを揃える。診断UIと通常画面を区別し、フォント差を全面maskして合格にしない。backendの大変更は実測が必要性を示した場合だけ行う。 |
| **12** | **Skin独立対応の残り** | HD eligibility、missingとdecode failure、許可mask/RawName等、部品ごとの探索、Back二層・frame/clock/hit、cursor/trailのproviderと設定owner、音のalias/provider/形式、Skin/HD切替時のcacheと破棄。自作画像・音のfixtureで選択ファイル/設定/draw/hit/frameを比較し、共有Skin変更はGameplay回帰も検証する。 |
| **13** | 音声・背景・遷移・機能依存 | previewの選択/seek/fade/loop/clock、同音源別難易度、選択連打、非同期ロード順、背景・拍・入退場、Gameplay復帰。Skinは音源解決、画面は発音時刻を担当する。Options/Mods/Mode/score/replay/collection/editorについて表示・操作・遷移先の実機能を区別し、必要なローカル依存を別変更単位で実装する。ボタン表示だけを機能完了としない。他ruleset Gameplay全実装は自動的に含めない。 |
| **14** | stable実機との総合比較 | ネットワーク遮断下で正常起動できるb20230727.9と、同じ譜面/Skin/音/font/設定/入力列を比較。状態、geometry/time、pixel/audioの三層で合格判定する。差分を根拠付きで修正し、残差と条件を明記する。公式asset抽出・保護回避は行わない。 |

phase 12はSkin専用の調査・報告・commit単位を維持する。phase 9/11/13に必要なSkin契約は先に閉じてよく、番号順に全工程を待たせる必要はない。
phase 14の参照環境・自作fixtureの準備も先行可能。Javaのcaptureだけでは実機比較の代替にならない。

## 共通の受入条件と制約

- 各phase: 追加の静的調査→独立した計算/状態テスト→関連テスト・Gradle build→必要な描画capture→diff確認→論理単位のcommit。
- 0/1/少数/多数の譜面、全Group種類、長文/Unicode、score有無、連打/drag中断を対象にする。
- 1024×768 / 1280×720 / 1920×1080 / 高DPI、SD/@2x、複数Skin Version、透明/欠損/巨大画像。
- 30/60/144Hzと不均一frame time。nativeのフレーム依存を都合よく消さず、測定前に誤差条件を決める。
- 完全ローカル。Bancho・osu! API・公式WebSocket等へ接続しない。公式asset抽出・保護回避をしない。
- Import / Gameplay / Renderer / Ruleset / GameClockの責務を維持し、必要な変更だけを行う。

**現在の比較上の制約:** この環境ではstableのSong Selectに正常到達する参照実行を確保できておらず、同条件pixel/audio比較は未実施。
これはphase 7以降の静的契約調査・Java実装を止める理由にはしないが、最終的な1:1合格の条件として残す。
