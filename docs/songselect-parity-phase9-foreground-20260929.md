# Song Select parity: phase 9 — foregroundの遷移・配置・thumbnail

> 更新: 本報告で残したdepth／blend／clipとhidden・非resident消去は、後続の[composition調査・実装](songselect-parity-phase9-composition-20260929.md)で対応した。hidden行に追加の200／300ms残像は描画されず、既存の即時除外が正しい。以下の残課題欄は本報告時点の記録である。

2026-09-29。[星の対応](songselect-parity-phase9-stars-20260929.md)に続き、
文字・mode／grade・thumbnailを同じ変更単位で対応した。
専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始時HEADは `44134ce658ffaa595f6fe5b98c320112b55ae5c8`。

## 根拠

同じ `b20230727.9/osu!.exe`（SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`）のILを使用した。
前回調査済みの色・星の時間曲線を再調査せず、foregroundとthumbnail loaderに必要な参照だけを追加した。
公式asset抽出・保護回避・production service接続は行っていない。
Skin方式の条件は既存の[独立調査](skin-stable-star-style-audit-20260929.md)を再利用し、
Skin parser/providerへの変更はない。

| 根拠 | 契約と実装 |
| --- | --- |
| `06000fd2` `0020–00ae` | state 0からの表示でbackground/title/byline/thumbnailのprefixを200msで0→1へ。state 2以上なら詳細にも一旦設定する。通常viewport進入時のinstant、Group生成時の通常遷移を区別する。 |
| 同 `02df–038a` | state 2をまたぐとdetail/mode/gradeを300msで表示／消去。現在のopacityを継承し、展開途中の再collapse・再展開でも0や1へ飛ばさない。 |
| 同 `041a–0508` | state 3以上への進入でthumbnailを1000msの0→1へreset、白へ300msで遷移。state 3未満へ戻る時はRGB 50へ300ms、opacityはresetしない。3↔4では再実行しない。 |
| `06003823/3824/3825` | from-current fadeとfrom-zero resetを区別。開始は`now-int(frameMs)`、終了は`now+duration`。即時resetかつdelta 0では次の正の時刻まで0に留まる。 |
| `06001be8` `0026–007b`、`04001290` | thumbnailのdraw要求間隔が200ms未満なら差分を累積し、それ以外はreset。state 3以上は100ms、未展開は500msで読込対象になる。これは単なる「毎100／500msの更新rate」ではない。 |
| `06001be8` `0225` → `06001bec` → `06000fdc` | 成功した初回読込のcallbackが400msの0→1を設定する。先行する1000ms展開fadeを置換する。callbackを一度呼んだ後に消すため、共有cacheを毎frame参照してもfadeを再開しない。読込frameは0、次の評価から進む。 |
| `06000fbf`、`06000fc2/0fe4` | 480基準の横起点はthumbnailあり75、なし5。style insetはcrop 15／scale 5。それに通常3、state 2以上かつmodeまたはgradeありなら20を加える。stateで移動するのは文字・星であり、thumbnail・mode・gradeの起点は固定。 |
| `06000fbf` `0183–024c/04c6–04f6` | title/byline/detailは中央左origin。通常の縦offsetは−16/−4/+7、crop方式はさらに−3。byline/detailの横offsetはtitle+1。公称文字サイズは16/12/12、detailのみbold。Java側も中心位置で配置できる描画入口を追加した。フォントmetricsの一致そのものはphase 11に残る。 |
| 同 `05d8–0688` | modeはvector scale .8で、左起点`column+style+1`、縦−13。gradeはscale 1、左起点`column+style−1`、modeありなら縦+14、それ以外は0。textureの論理寸法を使い、任意の24／40px枠に縮小しない。 |
| `06003285/3286` → `06003241/0fb0`、`06000fbf` `0022–004c` | Song Selectの通常行factoryはnullable modeを空で渡す。非standard譜面では譜面modeを設定する。通常osu行に独自のmode-osu badge／空き列を付けない。converted modeの表示供給はBrowserのmode対応に残る。 |
| `06000fbf` `0618–0624/068d–0699`、`06000fc1` | modeは文字色のtint対象、gradeは対象外で白のまま。grade画像が解決しなければgrade用の横幅も確保しない。行の独自grade文字fallbackは除去、score表示側は変更しない。 |

追加の証拠は `/tmp/osujava-phase9-thumbnail-loader.il`、`/tmp/osujava-phase9-state-change.il`、
`/tmp/osujava-phase9-row-mode.il`、`/tmp/osujava-phase9-mode-{construction,factory}-xrefs.txt`。
前回からの証拠は `/tmp/osujava-phase9-{foreground,foreground-transforms,star-geometry,colour}.il`。

## 実装と責務

`SongSelectForegroundAnimation`がbase/detail/thumbnailのopacity、thumbnailのbyte輝度と読込要求時刻を保持する。
Screenが既存UI時刻で一度更新し、Rendererにはimmutable snapshotを渡す。
textureの読込・所有は`BeatmapThumbnails`に残し、そこにあった共有の110ms fadeを除去した。
同じ画像を使う複数行でも、展開状態・読込時刻・明るさが相互に上書きされない。
背景画像用のcache参照によって行の読込待機が省略されることもない。
欠損／壊れた画像の負のcache、可視frameのtexture pin、evictionとdisposeの契約は維持する。

collapsed行も代表difficultyのcontentとgradeを保持する。これによりSetを閉じるとdetailを突然消したり
空文字へ交換したりせず、同じ文字のままfadeできる。state 3↔4、resize、delta 0では遷移を再開しない。
非residentになった行のモデルは破棄し、再生成時は通常進入／Group経由を区別する。

`SongSelectRowPresentation.Geometry`を480基準の各sprite起点へ変更した。
Rendererはmode/gradeの原寸とHDの整数論理寸法を使用し、gradeは白、modeはactive/inactive色で描く。
title/bylineとdetailのopacityを分け、星は独立した既存のopacityを使い続ける。
thumbnailが存在する時は下に独自の色付きplaceholderを重ねず、fade時の二重合成を除去した。
画像がない場合の既存placeholderは残っており、nativeと同一と認定したものではない。

## 検証

関連テストと `./gradlew build --offline --console=plain` 成功。
**109 suites / 990 tests、failure・error・skipは0**。前回から16件追加。
ログは `/tmp/osujava-phase9-foreground-{tests,build}.log`。
全件の初回実行では旧独自badge幅を前提にしたFoundation testが失敗したため、
調査済みの`(75+5+3/20)*height/48`を期待する契約へ更新した。

- 200/300/1000msの独立遷移、即時／delta 0境界、前frame整数ms補正。
- collapse・再展開による割込、singleton、3↔4の継続、30/60/144Hz。
- 100/500msの読込待機、200msの要求中断境界、state変更時の残時間。
- 読込完了400msへの置換、callback一度限り、同一textureを共有する別行の独立性。
- Screenでのcollapsed detail保持、割込、resize、非resident破棄。
- thumbnail有無、crop/scale、mode/grade有無、HD整数寸法と固定origin。

`foreground-contracts` harnessは **8 scenes / 104 PNG / 1,264 scripted frames** 成功。
1280×720／1920×1080／1280×720 framebuffer 2倍／1024×768 × crop/scale。
自作の白いmode/grade画像を使用し、SD・奇数寸法のHDで検証した。
0/100/200/300/500/1000msとcollapse後150/300msで、3画像の各9点のRGBを
独立した座標・時間式と比較し、title/byline/detailのglyph帯の最大alphaも比較した。
8-bit許容差は3。さらにproduction Screenで画像共有、白／RGB 50、collapseのdetail保持を検証した。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=foreground-contracts -PsongSelectOutput=/tmp/osujava-phase9-foreground-20260929
```

既存の`lifecycle-contracts`は **8 scenes / 68 PNG / 1,216 frames**、
`star-animation`は **12 scenes / 96 PNG / 96 frames**、
`star-contracts`は **16 scenes / 16 PNG / 32 frames** 成功。
**合計44 scenes / 284 PNG / 2,608 scripted frames**。
出力は `/tmp/osujava-phase9-foreground{-lifecycle,-stars,-screen}-20260929`、対応する`.log`を参照。
通常Screenの高評価行とcollapse途中のcaptureも目視確認した。

## 残る対象

- 全spriteのdepth／blend／clip、消える星との重なり、Groupを含む描画順。
- hiddenへ移行してresidentを解放する行の残存sprite。今回のstate 0 fadeはモデル単体で検証したが、
  Screenは従来どおり非residentを破棄する。行全体が消える時の200/300ms描画を完成扱いにしない。
- GDI文字測定・Unicode・省略、特殊な大きい文字／背景表示設定、実画面でのfont高さ・shadow。
- thumbnailのnative cache生成・非同期decode・placeholder・再読込順。今回のlocal texture cacheは
  従来の同期読込であり、ネットワーク取得やnative cacheの削除動作は持ち込まない。
- mode conversionや評価値の供給はphase 10／Ruleset側の依存。

phase 9の主要な行内遷移・配置は対応したが、上記の横断描画契約が残るため全体完了とはしない。
stableの正常Song Select参照実行は未確保であり、Java側captureをnative pixel/audio一致の証明には使わない。
