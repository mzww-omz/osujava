# Greylooks bundled default integration

## 調査結果と取得元

リポジトリのmodule構成、README、architecture、Import/Library/Ruleset/Gameplay/Renderer、skin package、テスト、Gradle配布設定を確認した。変更前は `GameplayScreen` → `OsuSkinAssets` → `SkinAssetResolver` で画像をロードし、custom directory → optional local fallback directory → programmatic drawing の順だった。classpath内蔵skinの概念はなかった。`SkinConfiguration` はsection別にiniを読み、不明項目を無視する。数字・Cursor・Slider・Spinner・判定画像・HUDには既存の対応があった。音声はbeatmap directory → generated clickだけだった。

取得元はユーザーが添付した `/Users/agemizu/Downloads/Greylooks [1.4].osk`。
SHA-256: `75fa4c13a5e7af2dabe96632ced2688488f9223a1e30cf048b96e997ca4014b3`。
ローカル探索では一致するインストール済みGreylooksを発見できなかった。Documents、`~/.osujava/skins`、osu!lazerの `~/Library/Application Support/osu` とそのhashed file storeを確認し、OSKのskin.iniと同じハッシュのファイルも存在しなかった。他のローカルskinを取り違えず、添付OSKを使用した。

OSKは503ファイルで、画像・WAV・skin.iniとExtrasの差し替え画像／音声からなる。TTF/OTFフォントや別のライセンス文書はない。全entryのパスを検証してから選別・コピーした。文書内のコメントは設定／出典情報として扱い、作業の指示としては扱っていない。

## 取り込みと構造

最初に現在の対応要素を中心に173ファイルを選別した。その後の「他の画像・音声要素も将来用に残す」というユーザー指示に従い、確認済み素材のうちmaniaアイコン2枚以外も保存した。

```text
core/src/main/resources/skins/default/
    skin.ini                 # 原文・作者名・未対応sectionも保持
    NOTICE.md                # JARにも付随する作者／配布元／CC BY 4.0表記
    *.png                    # 共通、standard、taiko、catch、将来用UI/効果
    *.wav                    # sample bankと将来用音声
    Extras/
        Defaults/
        No-300s/
        No-hitsound-additions/
```

501原ファイル（426 PNG、74 WAV、skin.ini）をバイト単位で無加工コピーした。リサイズ・クロップ・形式変換・リネーム・色変更はない。原skin.iniのCRLFも維持し、`.gitattributes`でcheckout時の自動改行変換を防ぐ。38 WAVは元から0 byteのplaceholderであり、将来用に保持するが現在の音声decoderには使用できない。Extrasはルートの画像や音声を上書きせず、通常の探索では自動選択しない。

コピーしなかったものは `mode-mania.png` / `mode-mania-small.png` のみ。maniaは今回対象外。`skin.ini`の空のMania sectionは原文維持のため残り、安全に無視される。

[全503ファイルの分類・使用可否・保持理由・SHA-256](greylooks-assets.tsv)を参照。現在使用する共通52、standard41、共通hitsound15、設定1、将来用taiko40、catch22、その他UI179、未対応画像110、その他音声41、除外mania2に分類した。未使用のメニュー・選曲・pause・ranking・Mods・editor素材も保存しているが、今回その画面に適用していない。

## 解決順序と設定

- 画像・数字: selected custom → optional existing local fallback → bundled default → programmatic fallback。
- `@2x`は各provider内で優先するため、customの通常画像がbundledの高解像度画像に勝つ。透明画像も有効な画像としてそのまま使う。破損画像は次の解像度／providerを試す。
- Slider始点／終点はそれぞれcustomの専用baseを先に確認する。customにhitcircle／hitcircleoverlayがあり、その専用baseがない／壊れている場合はhitcircle familyを再利用し、fallback providerの専用circleを読み込まない。baseを伴わない専用overlayはfamily選択を変更しない。専用baseがある場合は優先し、custom circle素材自体がない場合は従来どおり内蔵skinへ補完する。Slider ball・follow circle等の探索は変更しない。
- animationはframe 0またはstaticが読めたproviderを選択し、後続frameを他providerと混ぜない。
- `skin.ini`はcustomを優先し、存在しない／読めない場合のみ次のproviderを使う。項目単位のini合成はしない。部分skinでiniがない場合は内蔵iniを使用する。
- custom font prefixの不足glyphはbundled ini自身のfont prefixで補う。customの設定したoverlapは維持するため、違うfont同士が混在するとspacingが原skinと異なることがある。全10桁が揃わない／安全でないprefixの場合は既存フォント描画へ戻る。
- 音声はbeatmap sampleを最優先し、skin探索は通常の番号なしbasenameでcustom → local fallback → bundled → generated click。sample indexと音声cueを生成するrulesetは変更していない。

`SkinAssetResolver`のresource rootで任意のbundled defaultを扱う。Greylooks専用分岐はなく、中心となるrootは `skins/default`。`AssetFile`がlocal pathとclasspath resourceを区別し、libGDXのclasspath FileHandleを渡す。JARを展開したり、osu!インストール先に依存したりしない。画像・音声はScreen単位でロード／キャッシュ／disposeし、GameplayからOSKを読まない。

### skin.ini

Greylooksで実際に反映する既存項目: `Version: 1.0`, `CursorExpand: 0`, `CursorRotate: 0`, `HitCircleOverlap: 40`, `ScoreOverlap: 3`, `SliderBorder: 160,160,160`, `SliderTrackOverride: 0,0,0`。prefix省略は `default` / `score` を使う。

今回追加: `Combo1–8`（Greylooksは紫／橙／緑／青の4色）、`AllowSliderBallTint: 1`。色はrenderer専用 `ConfiguredGameplaySkin` で適用し、判定・score・hit windows・input・clock・parserは変更しない。判断根拠はローカルosu!lazer checkout `20e82fb18cd5ec2068f4551bb1fe0b1defc61cd1` の `SkinConfiguration.ComboColours` と `LegacySliderBall.LoadComplete`。

Greylooksで使用されるがini項目として未対応: `AnimationFramerate`, `LayeredHitSounds`, `SpinnerFadePlayfield`, `InputOverlayText`, `SongSelectActiveText`, `SongSelectInactiveText`, `StarBreakAdditive`, CatchTheBeatの`HyperDash`, `HyperDashFruit`, `HyperDashAfterImage`。不明項目は安全に無視する。現在の判定animationは既存の60 fps扱いで、Greylooksの主要画像は静止画像。音声cueは既存のlayeredな列挙を維持するが、iniで切り替えはできない。`SpinnerFadePlayfield: 0`は未反映のためSpinner中の背景・playfieldの見え方には差が残る。

## モード別確認と未対応機能

| Mode | 確認 | 残る制限 |
| --- | --- | --- |
| standard | 実際のGameplayScreen + Debug Autoでhit/approach circle、Slider body、cursor/trail、判定300、combo number、follow circle、ball、reverse arrow、old Spinner、score/accuracy/comboを目視確認。15種類のskin hitsoundを実decoderでロード。 | `sliderb-nd` / `sliderb-spec`追加レイヤー、followpoint、lighting、scorebar、warning、k/g追加判定等は未対応で保存のみ。Greylooksのlighting画像自体も1px透明。 |
| taiko | `taikohitcircle` / overlay、big circle / overlay、判定、drum、bar、glow、roll、Slider類の40画像を同梱。JAR画像デコードと代表画像のlibGDX Pixmapロードを確認。 | Ruleset/Gameplay/Rendererが未実装。Don/Kat色付け、big notes、判定、drum、score/comboをゲーム内表示する確認は実行不可。モード追加は今回のscope外。 |
| catch | fruit本体／overlay、banana、dropの22画像を同梱。JAR画像デコードと代表画像のlibGDX Pixmapロードを確認。 | Ruleset/Gameplay/Rendererが未実装。fruits/droplets/burst等のゲーム内表示確認は実行不可。catcherと専用burstの画像は添付OSKにないため、将来Renderer側のfallbackが必要。 |

ライセンス／クレジットは[THIRD_PARTY_ASSETS.md](../THIRD_PARTY_ASSETS.md)と配布内のNOTICE.mdに記載した。原作者のskin.ini表記と既存ppyクレジットは保持。

## 検証

- `./gradlew :core:test build :lwjgl3:executableJar`: 全330テスト成功、build／実行可能JAR生成成功。
- 新規テスト: default検出／ini、custom優先／partial skin、optional local fallback、nested font prefix、破損画像、animation provider維持、texture所有権、beatmap/custom/bundled音声優先とcache、isolated core JARからのini/PNG/WAVロード。
- JARテストはplatform classloaderをparentにしたisolated loaderを使い、source resourcesにアクセスできない状態で読み込む。全426 PNGのデコード、NOTICE同梱、maniaアイコン除外を確認。
- IDEと同じ展開済みclasses/resources: `./gradlew :lwjgl3:bundledSkinVisualHarness -PskinHarnessOutput=/tmp/osujava-greylooks-ide`。resource URLが `file:.../core/build/resources/main/skins/default/skin.ini` であることを確認。
- build後のcore dependency JARでも同じharnessを起動済み。resource URLが `jar:file:.../core-0.1.0.jar!/skins/default/skin.ini`。
- 配布JAR: `./gradlew :lwjgl3:bundledSkinVisualHarness -PskinHarnessJar=<absolute path to lwjgl3/build/libs/osujava-0.1.0-all.jar> -PskinHarnessOutput=/tmp/osujava-greylooks-jar`。harnessクラス以外のruntimeとassetsを配布JARだけから読み、resource URLが `jar:file:.../osujava-0.1.0-all.jar!/skins/default/skin.ini` であることを確認。各起動は独立した一時HOMEを使い、ローカルskinを要求しない。
- Java 21で通常の `java -Duser.home=/tmp/osujava-greylooks-launcher -jar <配布JAR>` 起動も確認。working directoryを `/tmp` にして起動し、起動エラーなくMain Menuを開始した後に検証用プロセスを終了した。
- opt-in harnessは通常buildではコンパイルのみ。GameplaySessionの通常Input APIを使う既存Debug Autoを起動し、GameClock・状態・scoreを直接変更しない。スクリーンショットと生成譜面／Libraryは `/tmp` 配下で、commitしない。

## Slider circle補完の追加修正

customのhitcircle系を持つskinで、欠落したSlider始点／終点をGreylooksの専用circleに置き換えていた問題を修正した。ローカルlazerの`LegacyMainCirclePiece`のprovider内でprefixを選ぶ考え方を参照し、同じcustomのcircle familyを優先する。描画時は既存hitcircle Textureを共有する。skin関連126テスト、全338テストとbuild／配布JAR生成が成功。専用素材の優先・片側だけの専用素材・破損base・孤立overlay・custom circleなしの場合を回帰テストに含めた。
