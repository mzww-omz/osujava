# Song Select 1:1対応 — 残phase台帳

2026-09-29。[全体再調査](songselect-parity-reinvestigation-20260929.md)と
[Skin独立調査](skin-stable-independent-audit-20260929.md)を、phase 9の背景色・星のanimation対応までの実装を反映して整理したもの。
phase 7以降の番号はphase 6時点で付与した実装単位で、当初から確定していた工数・完了予定ではない。
追加調査で依存や大きさが判明した場合は、根拠とともに分割する。

**現在はphase 9を進める。** ユーザーの進行方針に従い、8bの未完了項目は後段へ繰り越す。
8bを完了扱いにはしないが、そこに留まって追加調査を続けることをphase 9以降の前提にはしない。

## 完了した実装範囲

- phase 1: Skin parser/providerの基礎契約、selection anchor、10枠の星。
- phase 2: 永続行、親子Group、表示行projection、Group操作。
- phase 3: keyboard focusとplayable selectionの分離、navigation/確定。
- phase 4: 行の参照座標とX/Y補間、hoverによる間隔と曲線。
- phase 5: viewportの指数慣性、wheel、選択/focus追従、曲線への予測移動量。
- [phase 6](songselect-parity-phase6-20260929.md): drag中の速度推定、直接移動、静止時間を使うrelease慣性、入力→積分→描画の更新順。
- [phase 7](songselect-parity-phase7-20260929.md): Group開時の初期位置配布、画面外の行群snap・再進入、active範囲と行の表示状態。
- [phase 8a](songselect-parity-phase8a-20260929.md): 80 window pixelsのクリック取消、押下行自身の現在矩形への解放、保持中だけのdrag sample、右button位置指定、gesture中のhover保持。
- [phase 8bのキーボード部分](songselect-parity-phase8b-keyboard-20260929.md): 共有counterによるUp/Down/Page保持repeat、初回入力との区別、修飾キーによるreset、overlay等の優先、消費済み文字のrepeat抑制。8b全体は継続中。
- [phase 8bのmouse押下/解放部分](songselect-parity-phase8b-mouse-20260929.md): button snapshotの共通down/up、左右併用・交換、前回右stateによるcontext/play分岐、右行選択、候補とdragの寿命分離。Options dialogは未実装で既存の案内へ接続。8b全体は継続中。
- [phase 8bの再クリック部分](songselect-parity-phase8b-activation-20260929.md): Set展開後の独自240ms play guardを除去。Song Select内の250整数counter、左右/中buttonの通知差、物理down時の距離基準更新を実装。画面/focus境界等は継続中。
- [phase 8bのwheel部分](songselect-parity-phase8b-wheel-20260929.md): 更新間のwheel合計を方向1回分に集約し、保持key repeat・mouseより先に通知。通知時のpointer位置でvolume/score/carouselへrouting。初回key callbackとの順序、global polling/focus境界は継続中。
- [phase 9の色・文字alpha部分](songselect-parity-phase9-colours-20260929.md): state別の背景RGBA、hover/focus目標色、title/bylineのalpha 50、選択を含む閉Groupの別色を対応。
- [phase 9の背景色animation](songselect-parity-phase9-colour-animation-20260929.md): 300ms状態色遷移、50ms focus、1000ms hover flashと中断・競合、byte補間、resident行の色保持・破棄を対応。954 tests / Gradle build、17 scenes / 89 PNG成功。
- [phase 9の星](songselect-parity-phase9-stars-20260929.md): 500msのscale/crop、評価差による80ms順序、OutBack/OutCubic、生成経路による即時表示、300ms破棄fadeを対応。画像寸法による間隔とopacityを修正し、独自の行内数値表示・狭幅圧縮を除去。Skin条件は[独立補足](skin-stable-star-style-audit-20260929.md)。974 tests / Gradle build成功。次は文字・thumbnail・mode/grade。

これは各phaseで閉じた静的契約とJava側検証の範囲を示す。実機を含む全画面の1:1一致が完了したという意味ではない。

## 残phase

| Phase | 対象 | 主な作業と完了条件 |
| --- | --- | --- |
| **8b（後段へ繰越）** | 入力判定・dispatcherの残り | native background spriteのhitbox/丸め/clip・depthとhover候補の優先・alpha条件、初回key callbackとwheelの順序・同時key snapshot、global polling/画面/focus境界での入力状態継承・mouse代替等。保持repeat、左右併用・右行context要求、画面内のdouble-click分類、wheel集約と保持repeat/mouseより先の通知は実装済み。独自play guardは除去済み。初回keyと修飾キー/overlayの同時更新順は未完了。Options等のdialog実機能はphase 13、検索編集repeat/IMEはphase 10と照合する。8aのpointer契約も実装済み。Song Selectのdrag-out先は空callbackと確認済み。同一入力列のfocus/selection/play requestと時刻を比較する。 |
| **9（進行中）** | 行の状態別描画・animation | 背景RGBA・文字alphaと色遷移、星のscale/crop・500ms・index遅延・生成／破棄は対応済み。残りはdetail/mode/gradeの300ms fade、表示復帰の200ms fade、thumbnailのstate別色・1000ms表示・ロード設定、全foregroundの横起点・origin/depth/blend/clip。消える星の全spriteとのdepth比較も含む。対応済みの色・星の時間曲線を再調査しない。 |
| **10** | Browserデータ・検索・分類 | tags/source/Unicode/引用/比較検索、sort/groupの残条件、日付・mode・rank・collection/favourite、近い難易度を選ぶ規則、metadata/score。必要な情報をImport→Library永続化→index→行へ保持する。オンライン未取得値を捏造しない。評価互換はRuleset側の依存として追跡し、見た目の比較では同じ評価を固定する。 |
| **11** | フォント・画面構成 | GDI系文字測定とJavaの字幅/baseline/省略/Unicode fallback、DPI・丸め・影。chrome予約/延長、search/tab/metadata/score/Mode/Mods/Options/Back/Cookie/scrollbarのdepth・clip・hitを揃える。診断UIと通常画面を区別し、フォント差を全面maskして合格にしない。backendの大変更は実測が必要性を示した場合だけ行う。 |
| **12** | **Skin独立対応の残り** | HD eligibility、missingとdecode failure、許可mask/RawName等、部品ごとの探索、Back二層・frame/clock/hit、cursor/trailのproviderと設定owner、音のalias/provider/形式、Skin/HD切替時のcacheと破棄。自作画像・音のfixtureで選択ファイル/設定/draw/hit/frameを比較し、共有Skin変更はGameplay回帰も検証する。 |
| **13** | 音声・背景・遷移・機能依存 | previewの選択/seek/fade/loop/clock、同音源別難易度、選択連打、非同期ロード順、背景・拍・入退場、Gameplay復帰。Skinは音源解決、画面は発音時刻を担当する。Options/Mods/Mode/score/replay/collection/editorについて表示・操作・遷移先の実機能を区別し、必要なローカル依存を別変更単位で実装する。ボタン表示だけを機能完了としない。他ruleset Gameplay全実装は自動的に含めない。 |
| **14** | stable実機との総合比較 | ネットワーク遮断下で正常起動できるb20230727.9と、同じ譜面/Skin/音/font/設定/入力列を比較。状態、geometry/time、pixel/audioの三層で合格判定する。差分を根拠付きで修正し、残差と条件を明記する。公式asset抽出・保護回避は行わない。 |

phase 12はSkin専用の調査・報告・commit単位を維持する。phase 9/11/13に必要なSkin契約は先に閉じてよく、番号順に全工程を待たせる必要はない。
phase 8はgestureと共通dispatcher/spriteの別経路を確認したため8a/8bへ分割した。8bの静的調査結果はphase 8a報告に引き継いでいる。
phase 14の参照環境・自作fixtureの準備も先行可能。Javaのcaptureだけでは実機比較の代替にならない。

## 共通の受入条件と制約

- 各phase: 追加の静的調査→独立した計算/状態テスト→関連テスト・Gradle build→必要な描画capture→diff確認→論理単位のcommit。
- 0/1/少数/多数の譜面、全Group種類、長文/Unicode、score有無、連打/drag中断を対象にする。
- 1024×768 / 1280×720 / 1920×1080 / 高DPI、SD/@2x、複数Skin Version、透明/欠損/巨大画像。
- 30/60/144Hzと不均一frame time。nativeのフレーム依存を都合よく消さず、測定前に誤差条件を決める。
- 完全ローカル。Bancho・osu! API・公式WebSocket等へ接続しない。公式asset抽出・保護回避をしない。
- Import / Gameplay / Renderer / Ruleset / GameClockの責務を維持し、必要な変更だけを行う。

**現在の比較上の制約:** この環境ではstableのSong Selectに正常到達する参照実行を確保できておらず、同条件pixel/audio比較は未実施。
これはphase 8以降の静的契約調査・Java実装を止める理由にはしないが、最終的な1:1合格の条件として残す。
