# Song Select parity: phase 9 — 星の生成・遷移・破棄

2026-09-29。[背景色animation](songselect-parity-phase9-colour-animation-20260929.md)に続き、
foregroundのうち星20 spriteの寿命と時間変化を対応した。
8bの追加実装は行っていない。phase 9全体の完了ではなく、文字・thumbnail・mode/gradeは残る。

専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始時HEADは`a55ba2a76d732744e99602065e381997dca6ecd1`。
元worktree/mainへの変更・統合は行っていない。

## 根拠と修正

同じ `b20230727.9/osu!.exe`（SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`）のILを調査した。
公式asset抽出・保護回避・production service接続は行っていない。
Skin条件は[独立補足](skin-stable-star-style-audit-20260929.md)へ分離している。

| 根拠 | 確認・実装内容 |
| --- | --- |
| `06000fd2` `00b6–0415` | state 2以上への移行で背景10枚＋前景10枚を生成。2以上同士の選択変更では作り直さない。2未満への移行で300ms fadeを付け、管理対象から外す。 |
| `06000fd1`、`06003266/3273` | 通常のviewport再進入では即時生成、Group開時に準備された行ではanimation付き生成。Carouselに生成経路のflagを持たせた。既存residentの代表行がSet展開される場合もanimationする。 |
| `06000fbd` `0026–0054` | 評価値を10で上限処理し、前回の値と同じなら何もしない。初回のcache値は−1。元のdouble値を保持し、float化で整数境界の遅延順が変わらないようにした。 |
| 同 `006b–0112` | 評価ありの背景はopacity 1へ、未取得は0へ。scale方式は600ms、crop方式は1000ms。生成時背景spriteはopacity 0。 |
| 同 `0197–024b` | scale目標は残量が正なら `0.6 * max(0.5, min(1, rating-i) + i*0.04)`、それ以外0。前景のuniform scaleへ500ms、easing 30を設定する。0.25星を単純に25%縮める旧実装を除去した。 |
| 同 `01dd–0229` | `q=floor(i-min(new,old))`。増加時はq、減少時は`floor(old-new)-q-1`を順序とし、開始を`now+order*80+50`へずらす。初回old=−1なので最初の星は130msから始まる。負の遅延も許される。 |
| 同 `016a–0192` → `060040cc` | crop目標は`int(logicalWidth*(rating-i))`。500ms、easing 7。初期幅は`-i*logicalWidth`。通常helperの開始は`now-int(frameMs)`で、scale側の手組み遅延とは異なる。 |
| `06002b54` | easing 7はOutCubic、30はOutBack（係数1.70158）。scaleの正負両方の行き過ぎを残す。途中で新評価になったら前回描画済み値から新しい遷移へ置き換える。 |
| `060040b0/40af`、`060040bb/40bc` | crop幅は整数へ切り捨て、描画時に0〜元幅へclampする。originは元画像中央のままで左側からcrop。寸法はSD/HDを補正した論理寸法。 |
| `06000fd2` 星constructor | vector scaleは0.6、scale方式の背景uniform scaleは0.35。背景は白・alpha 30、前景色はactive/inactive文字色。従来の背景alpha .24や非選択前景alpha .88を除去した。 |
| `06000fbf/fd2/fc0` の生成要素 | 行の星に独立した数値label spriteはない。Java独自の行内数値・18固定pitch・狭幅時の圧縮／数値優先を除去。評価の数値データと選択metadata側の表示は保持した。 |

追加調査は `/tmp/osujava-phase9-foreground.il`、`/tmp/osujava-phase9-foreground-transforms.il`、
`/tmp/osujava-phase9-star-{geometry,sprite,draw}.il`、`/tmp/osujava-phase9-star-crop-xrefs.txt`。
`star-draw.il`の40ad/40aeはイベント登録であり描画幅の証拠には使わない。
幅のclampの根拠は`40af` `0ef1–0f31`である。

## 設計

`SongSelectStarAnimation`は行ごとの10個のscale/cropと二層のopacityを管理する。
`SongSelectScreen`が既存のUI時刻で更新し、Rendererにはimmutable snapshotだけを渡す。
テクスチャ・評価の計算・GameplayのGameClockをこのモデルへ持ち込まない。

residentから外れた行の状態を破棄し、復帰時は生成経路に応じた初期状態を作る。
Setを閉じた時は星の遷移を止めず、300ms fade用のsnapshotを別に保持する。
この星は最後に描いた位置と色を維持し、新しいcollapsed行の移動やtintには追従しない。
再展開した新しい星と、消えつつある以前の星も別の状態となる。

Rendererは画像の論理寸法と768基準scaleからspriteサイズ・間隔を計算する。
cropの中心は元画像基準で保持し、scaleは中央から伸縮する。巨大な画像や狭幅でも星を詰めず、既存のclipを使う。
全foregroundの起点・clip/depthの一致は次の対象であり、今回だけで完成とはしない。

## 検証

関連テストと `./gradlew build --offline --console=plain` 成功。
**108 suites / 974 tests、failure・error・skipは0**。
21件追加し、独自の数値優先layoutを要求した旧テスト1件を除去したため、前回から純増20件。
ログは `/tmp/osujava-phase9-stars-{tests,unit,build}.log`（最終全件結果はbuild）。

- 500ms・80ms順序差・初回130ms、OutBackの行き過ぎと減少時の逆順。
- 評価変更の割り込み、元double値の整数境界、0／未取得／部分星／10超。
- cropの整数幅・OutCubic・frame補正、duration 0かつdelta 0の境界。
- 300ms破棄fade中もscaleが進むこと、30/60/144Hz、snapshot非破壊。
- production Screenでの選択変更時の継続、Set展開、消える星の位置固定、検索での破棄・再生成。
- Group生成と通常の再進入でinstant flagが分かれること。

途中の失敗は、80ms地点のOutBack期待値の転記誤り、float deltaを整数ms化した終了境界、
検索復帰直後のviewport位置を仮定したfixtureだった。期待値を独立計算し直し、終了後の時刻と明示的な
検索解除・選択・位置安定化を使って修正した。入力仕様や許容差を変更して通したものではない。

`star-animation` harnessは **12 scenes / 96 PNG / 96 scripted frames** 成功。
4解像度/DPI × scale/crop/旧Version＋bundled行背景を検証した。SD 40px、HD 80px／奇数81pxの自作白画像を使用。
0/130/250/380/500/630/1000msと破棄150msを描画し、最後は160幅の行へclipした。
固定の22.5-unit間隔とsnapshot値から描画矩形・白色のalpha合成を計算し、**1,282,009点のRGB pixel**を比較した。
境界の1 framebuffer pixelは事前に除外、8-bit許容差3。時間曲線そのものの独立期待値はunit testが担当する。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=star-animation -PsongSelectOutput=/tmp/osujava-phase9-stars-animation-20260929
```

実画面の `lifecycle-contracts` は **8 scenes / 68 PNG / 1,216 scripted frames**、
`star-contracts` は **16 scenes / 16 PNG / 32 scripted frames** 成功。
Group開閉・scroll再進入と、未取得／低評価／第10星の部分表示／高評価を確認した。
合計 **36 scenes / 180 PNG / 1,344 scripted frames**。
出力は `/tmp/osujava-phase9-stars-{animation,lifecycle,screen}-20260929`、
ログは `/tmp/osujava-phase9-stars-{harness,lifecycle,screen}.log`。
白画像の380ms captureと1280×720の高評価Screenを目視確認した。

## phase 9の残り

- detail/mode/gradeのstate 2境界での300ms fade、title/bylineを含む表示復帰の200ms fade。
- thumbnailのstate 3境界の白／RGB 50への300ms遷移、1000ms表示、ロード時刻・rate設定。
- foregroundの横起点（`04000865`等）、文字・mode/gradeのorigin、全要素のdepthとclip。
  今回の消える星は既存row passの後で描く。native全spriteとのdepth順の検証は未完了。
- 比較用に与えた評価値での表示契約は対応したが、productionの評価供給は引き続き別依存。
  存在しない評価を推測して表示する処理は加えていない。
- 通常ScreenのSkin既定画像補正、8bに残した入力順／window focus境界、実機pixel/audio比較は従来どおり残る。

stableの正常なSong Select参照実行は未確保。Java側の数値・描画検証を全体の1:1合格とは扱わない。
