# stable b20230727.9: cookie と未クリック時の演出

対象: `/home/coder/workspace/b20230727.9/osu!.exe`。SHA-256 は
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
2026-09-29 の読み取り専用 CLR metadata / IL 調査と独立 Java 実装。
**指定buildを実行して1:1一致を実証したという記録ではない。**

## 境界

- 公式asset・音声・fontは抽出していない。暗号化文字列の復号、assemblyの改変、保護回避も行っていない。
- app はローカルファイルだけを使う。Bancho、osu! API、公式WebSocket、更新サービスへの接続は追加していない。
- MainMenuInput、PLAY/EXIT、P/Enter/Space/Escape、クリック取消、遷移待ち、曲送り・pause/resumeの機能を維持。stableのEdit/Options/online menuや放置によるメニュー自動閉鎖は追加しない。
- ロゴは既存のユーザー提供 `java!` asset を維持。Song Selectのcookie描画、Gameplay、Ruleset、GameClockには変更を加えない。

## 確認した処理

offset は `dncil` のmethod headerを含む。メソッド名を復元したと仮定せず、fieldと呼出関係で同定した。

| 処理 | 指定buildでの根拠 | 独立実装 |
| --- | --- | --- |
| 基準寸法 | `06003f60:0335–0354` cookie ctorに300、`06002899:000d–002a` 当たり判定は300×scale×画面scale÷2。`06003f7b:0c15–0c4d` spectrum半径は150基準 | 480高の半径150。720pで半径225。既存の縦長window対応では幅制限を残す |
| カーソル視差 | `06003f7b:004e–00ba` 中心からのpointer変位÷画面scale÷60をcontainer offsetへ。`0bbc–0c06` spectrum中心はoffsetを引く | 背景からcookieが僅かにずれる。描画とhitboxに同じ中心を渡す |
| hover/click | `06003f7b:0525–058a` hover offsetは60Hz換算frame数×.012で0〜.1へ。`06003f6e:000d–0019` clickで.08を引く | 弾性1.1倍／長押し.9倍のlazer式を置換。操作のdispatchは維持 |
| 音量の立ち上がり | `06003f7b:087a–08b0` 左右levelの和÷65536と音量、平均は旧値×.9＋新値×.1 | PCM左右のlevelを解析し、拍動へ渡す。下記のデコーダ・音量差は残る |
| 拍動 | `06003f7b:0987–0b50` 無再生時1000ms周期。音楽時は次のupdateを予測。target=`clamp(.5*(1-phase)+level-average)`、平滑化保持率`.5^frameCount`、二次Outで1.05→1 | `MenuCookieMotion` が音楽時計とUI deltaを受け取る。従来の60ms先行OutQuint／音量しきい値.4の別scaleを除去 |
| 外周の二重像 | `06003f7b:0b55–0bba` 二次Outで1.05→1.08、alphaは通常.4→0、kiai .1→0、拍で保持する強度を乗算。kiai時は加算合成 | 同じjava!画像の薄い外周を独立scale/alphaで合成 |
| 外へ広がる残像 | `06003f7a` 拍ごとにsprite生成、初期alpha `.1*intensity`、1000msでfade、二次Outでscaleを1.4倍へ | 拍の更新で生成する有限寿命のring。曲切替・巻戻しでは履歴を消し、過去の拍を一斉再生しない |
| FFT入力 | `06002b39` 1024 bins、`06002b3b:0052–008e` BASS flag `0x80000003`、通常menu gain1.6は `06003f7b:0026–0031` | 2048 sample Hann FFT、1024 bins。解析と描画の責務を分離 |
| 周囲の配置 | `06004904:0020–0067` 1024本、angle=`2π*(.4+4*i/1024)`。`06004903` 半径から位置を生成 | 4周分の細いspoke。以前の200本×5周を置換 |
| 回転・減衰 | `06004908` 10msを越すごとにindexを50ずらす、1000ms超のstallはskip。`06004909` 逆順bin対応、peak=`max(old, fft*3)*.95^(dt/16.6667)`、.01未満は0 | 再生位置のFFTを入力し、同じindex/peak更新を独立実装 |
| spectrumの濃度 | `06004909:00b8–00f2` alpha=`max(0, global*.4*min(1,(length-.04)/.08))`。`06003f7b:0c7f–0cb8` closed時通常.7、kiai1。`06003f69→0600490a` 非supporter色は `06004901` の128/128/160 | 同じalpha式と非supporter色を使用。既存のmenu展開率に応じて.3倍まで薄くする |

補足の一次資料:

- [公式wiki Interface](https://github.com/ppy/osu-wiki/blob/master/wiki/Client/Interface/en.md): cookieはBPMに同期し、周囲は音声スペクトラム。無再生時は60 BPM。
- [公式wikiの公開Main Menu画像](https://raw.githubusercontent.com/ppy/osu-wiki/master/wiki/Client/Interface/img/main-menu.jpg): 全体寸法・構成の目視参照。別の日付の画像であり、指定buildの今回の実行結果ではない。
- [BASS ChannelGetData](https://www.un4seen.com/doc/bass/BASS_ChannelGetData.html): FFT2048は1024 bins、既定はHann window、複数channelはmonoへ合成。
- [使用中のlibGDX MP3 decoder呼出](https://github.com/libgdx/libgdx/blob/1.14.2/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/audio/Mp3.java)、[OggInputStream](https://github.com/libgdx/libgdx/blob/1.14.2/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/audio/OggInputStream.java)。既存のruntime依存をAPIとして呼ぶ。新規codec/native依存や外部コマンドはappへ追加しない。

## 実装の分離

`DesktopMenuAudioAnalysis` は1つのキャンセル可能workerでMP3/OGG/PCM16 WAVを順次decodeする。
FFTは2048 samples、hopは概ね10ms。timestampはdecode経過時間でなくPCM sample index由来。
UIはMusicの再生位置に対応する標本を参照する。約2秒の先読み、最大400 frames（FFT配列約1.6MiB）に制限し、全曲PCMを保持しない。
pause中のwaveは新たな入力を止めて減衰。beatだけは60 BPMへ戻る。曲送り・hide・disposeで解析をキャンセルし、loop時はdecode履歴を作り直す。
欠損・壊れた音源、未対応形式、解析が再生に追い付かない場合は無波形。疑似FFTは生成しない。
音楽のstream/volume/queueは既存のMenuMusicPlayer / MenuAmbientAudioがそのまま所有する。

`MenuCookieMotion` はbeat/level/hoverからscaleと残像を作る。
`MenuVisualiser` はFFTの履歴だけを持つ。
`MainMenuLogo` はこれらの値と既存assetを描画する。
`MainMenuModel` のOPENING 380ms、logo縮小200ms、OPEN時.65倍、CLOSING 300ms、navigation 200msは変更しない。
ユーザーの「メニュー機能を絶対に変えない」指示を優先し、stableの横移動・多段menuを再現対象へ混ぜない。

## 実機比較が残る点

今回もコピーしたWine prefix/参照buildを `unshare -Urnp --mount-proc` でネットワーク隔離して通常起動した。
Xvfbをnamespace外で起動した最終試行の20/40/60秒captureは空の画面で、Main Menuに到達しなかった。
今回の空画面を過去のupdater到達記録に読み替えない。保護回避を行って先へ進めることもしない。
記録は `/tmp/osujava-cookie-runtime/`。

従って以下は1:1認定の対象外で、同じローカル音源・解像度・pointer履歴による実画面比較を要する。

- 公式texture内のalpha・stroke・余白。Javaのspokeは独立した長さ300×FFT scale、幅.5の幾何描画であり、抽出textureの再現ではない。残像にも既存java!画像を使う。
- BASSと既存MP3/Vorbis decoderのdelay、FFT絶対振幅、左右levelの観測window、device latency、volume補正。現在のlevelは2048 samples内のchannel平均絶対値の最大。PCM/FFTの数式テストだけでBASSとの同値は主張しない。
- 起動時のwelcome/seasonal演出、背景の切替・装飾、既存HUD/fontの完全な画素一致。今回の変更は常時のcookie/周辺演出に絞る。
- startup直後や極端な低FPSでのstable内部update順。seek時の残像抑制、portraitの幅制限、既存menuに合わせた縮小・展開はJava側の明示的な方針。

## 再調査と検証

既存の読み取り専用toolをそのまま使える。raw ILはGitに保存しない。

```sh
/tmp/osujava-stable-inspection-venv/bin/python tools/stable_results_inspect.py \
  '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 06003f60 06003f7a 06003f7b 06002899 06002b3b 06004904 06004908 06004909
./gradlew --offline build :lwjgl3:executableJar
ALSOFT_DRIVERS=null xvfb-run -a ./gradlew --offline :lwjgl3:mainMenuVisualHarness \
  -PmainMenuOutput=/tmp/osujava-cookie-final-visual
./gradlew --offline :lwjgl3:mainMenuAnalysisSmoke -PmainMenuAudio=/tmp/osujava-cookie-audio
ALSOFT_DRIVERS=null xvfb-run -a ./gradlew --offline :lwjgl3:mainMenuAudioSmoke \
  -PmainMenuAudio=/tmp/osujava-cookie-audio
```

audio fixtureは6秒、44100Hz、689.0625Hz sine（FFT bin32）、peak約.62のstereo。
WAV/MP3/OGGをテスト用ffmpegで生成した。ffmpegはappに含めず、起動時にも呼ばない。
analysis smokeは全形式のlevel、bin32、再生位置参照、巻戻しを確認。
playback smokeは全形式で再生・pause・resume・fade・二重close・再起動を確認。
OpenAL null driverを使用しており、物理音声出力を聴いた検証ではない。

build: core 894 tests、desktop 4 tests、failures/errors/skips 0。fat JAR生成も成功。
生成JARも隔離した空のuser.homeで起動し、未クリック画面をcaptureしてEscapeからexit code 0で終了した。
visual harness: 624 scenesを各2回描画してbyte一致、80 navigation sequences、8 music sequences。
1024×768 / 1280×720 / 1366×768 / 1920×1080 / 2560×1440 / 600×800、2種類の2x framebufferを含む。
追加fixtureは未クリック時の1拍6位相、closed hover、pointer両隅。これはJava内の再現性・操作回帰の検証であり、stableとのpixel差分ではない。
