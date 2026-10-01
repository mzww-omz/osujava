# Song Select parity: phase 9 — 描画list・重なり・clip・消去

2026-09-29。[foreground対応](songselect-parity-phase9-foreground-20260929.md)から継続。
専用worktree `/home/coder/worktrees/osujava-songselect-phase8b`、branch `codex/songselect-phase8b`。
開始時HEADは `2298b5f1ed85facaebc90825563e92a59ef31a1c`。

phase 9の静的契約に基づく実装単位を閉じる。次はphase 10のBrowserデータ・検索・分類へ進む。
これはstable実機との1:1合格を意味しない。残る領域は末尾と[台帳](songselect-parity-roadmap-20260929.md)に明記する。

## 調査結果と過去の結論の訂正

参照は同じb20230727.9のIL、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
公式asset抽出・保護回避・production service接続は行っていない。
Skin探索契約は既存の独立調査を使用し、今回はSkin実装を変更していない。

星の前回調査では、`06000fd2`が削除前に設定する300ms fadeだけから、削除後も描画されると解釈していた。
今回、managerの毎frame clearから描画までを追跡した結果、この解釈は誤りと判明した。
fadeが存在しても、そのspriteを次のframeのmanagerへ再登録しなければ描画されない。
Javaのretiring-star list、固定位置の残像描画、retire用opacityモデル・テストを削除し、
collapse直後に星を除外する回帰テストへ置き換えた。代表行に残るdetailの300ms fadeは別の契約として維持する。

| 根拠 | 確定した描画契約 |
| --- | --- |
| `06003267` `0046–0059` → `06002c17(false)` | 毎frame、行managerのsprite listをclearする。falseはdisposeを省略する引数であり、listの保持を意味しない。`1bf0`ほかのlistは無条件にclearする。 |
| `06003267` `0079–0092`、`06003266`、`06000fc8` → `06002c21` | 表示対象外なら`06000fb9`で解放して登録をskip。buffer内のresident行だけが現在の`0871` sprite listをmanagerへ登録する。 |
| `06000fd2` `0353–0415` | collapse時、detailはrow listに残すが、星20枚は`0871`からRemoveRangeする。したがって星のfadeは次のdrawへ持ち越されない。hidden／非resident行全体にも別の残像managerはない。 |
| `06003287`、`06000fbe`、`0600454a` | full browser listの順にbase depthを.6から.00003刻みで割当。各行内は必要時に.000001刻みへ更新。比較はdepth昇順。選択による最前面化はない。 |
| `06002c21`、`06002c1d` | 登録はrow listの順序を保って挿入し、managerの描画loopは先頭から実行する。行内の同depth spriteを別途選択順へsortしない。 |
| `06000fbf`、`06000fd2` | background → title → byline → thumbnail → detail → mode → grade → 背景星10枚 → 前景星10枚の順に登録する。存在しないspriteは省略する。 |
| `060025e8` | Groupはbackground → label。labelは中央左origin、公称24、local offset `(15,0)`。 |
| `0600321a` → `06002c05/2c0c`、`06002c1d` `03f8–040d`、`06003810/3813` | Song Selectの行managerはcustom clipを設定しない。各行spriteにもcustom clipがないため、共通の全display viewportを使う。行bodyや上下chrome予約でscissorしない。 |
| `0600258f/258c` | default clipはdisplay viewport全体。default矩形と同じならGL scissorを無効化する。 |
| `06002c2c` | 通常合成はSRC_ALPHA / ONE_MINUS_SRC_ALPHA。行用の加算合成設定はない。 |

主な調査dumpは `/tmp/osujava-phase9-{render-queue,queue-clear,depth-order,queue-constructor,manager-composition,clip-bounds,row-draw,row-submit}.il`。
depth setter、clip setterのxrefも確認した。表のmethod tokenと制御経路を再調査の起点とする。

## 実装

Screenはresident buffer内の全描画対象をBrowser順でsnapshotにする。
bodyが上下の操作領域から外れていても、大きな背景・badge等が画面へはみ出せるため、bodyだけではcullしない。
Rendererはその順に描き、選択行を最後へ移すpassを廃止した。
行内の描画順も上表へ合わせ、星は背景10枚を描いてから前景10枚を描く。
GL scissorは全画面の通常状態、SpriteBatchは通常alpha合成にし、呼出元のblend設定は復元する。

`RowGeometry.clip`は描画clipではなく操作領域との交差なので、`inputClip`へ改名した。
`zOrder`はBrowser indexを返す。input hitboxやdispatcherの正確な優先規則は8bに残り、
既存のselected優先body hitを今回の描画規則から推測して変更しない。
このため描画と入力の優先順位には既知の差が残る。

独自のprocedural chrome背景も行の後に合成し、画像chromeと同様に行の前に置く。
chrome自身の正確なdepth・clipはphase 11の対象であり、今回の検証は行passの契約を主対象とする。
Groupの文字は既存の任意倍率から公称24と中央左originへ修正した。字体・測定の一致はphase 11に残る。

## 検証

関連Song Selectテストと `./gradlew build --offline --console=plain` 成功。
**109 suites / 991 tests、failure・error・skipは0**。
前回990件から、誤ったretireモデルのテスト1件を除き、Screenの契約テスト2件を追加した。

- 選択変更では星を再生成せず、collapseでは次のdrawから星を除外する。detailの300ms fadeは継続する。
- 検索によるhidden化をdelta 0で反映し、直後のsnapshotと色／星／foregroundモデルから対象を除外する。
- 上下の操作領域外にあるresident buffer行もBrowser順で描画へ渡し、操作領域外ではhitしない。
- geometryのdepthはselectedを特別扱いしない。既存入力契約も回帰確認する。

新規`composition-contracts`は、自作の白い行背景・青mode・半透明緑grade・半透明赤starを使用。
1024×768、1280×720、1920×1080、1280×720 framebuffer 2倍 × crop/scaleの8条件で、
選択行に後続行／Groupが重なる順序、大きな画像のbody外描画、badgeが文字を覆う順序、
星がbadgeを覆う順序、通常alpha合成、blend設定の復元をpixel値で比較した。
SDとHD、透明画像、狭い行、大きなbadge・縦長starを含む。8-bit許容差は3。

| Harness | Scenes | PNG | Scripted frames |
| --- | ---: | ---: | ---: |
| composition-contracts | 8 | 32 | 32 |
| star-animation | 12 | 96 | 96 |
| lifecycle-contracts | 8 | 68 | 1,216 |
| foreground-contracts | 8 | 104 | 1,264 |
| 合計 | 36 | 300 | 2,608 |

`star-animation`の末尾は、旧retire-fade期待を狭幅でも星を圧縮／clipしない期待へ更新した。
出力は `/tmp/osujava-phase9-composition{,-stars,-lifecycle,-foreground}-20260929`。
ログは `/tmp/osujava-phase9-composition-{tests,build,harness,stars,lifecycle,foreground}.log`。

```sh
xvfb-run -a ./gradlew :lwjgl3:songSelectVisualHarness --offline --console=plain \
  -PsongSelectPhase=composition-contracts -PsongSelectOutput=/tmp/osujava-phase9-composition-20260929
```

## 次段階と制約

phase 10へ進む。8bのsprite hit／入力dispatch、11のfont／chrome、12のSkin独立課題、
13のthumbnail非同期cache／placeholder／音声／画面遷移、14の実機総合比較は残る。
bundled背景への既存wash、特殊表示設定も後続で判定する。
stableの正常Song Select参照実行は未確保であり、今回のJava captureとIL契約検証を
native pixel/audio一致の証明として扱わない。
