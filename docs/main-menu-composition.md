# Main Menu composition

## Hierarchy and layout

旧画面は装飾circle、一様な紫黒のdim、同色の3本帯、disabled Options、1行に並ぶ上部情報が競合していた。今回は **Cookie → Play → Exit → 端の情報** の順に整理した。

Cookie中心は幅の33.5%、縦中央。半径は高さと幅の両方で制限し、白いring、薄い多層halo、頂点色で補間した内側gradientを使う。装飾circleと独立したsheenは置かない。60 BPM fallbackのpulseはscaleとhaloの半径・alphaにごく小さく連動する。hoverは約1.4%の拡大、pressedは約2.5%の縮小と内側の暗色化。

Play / ExitはCookie中心から始めてCookieで左側を隠す。4単位の狭い行間、横長の帯、斜めの右端を共有する。Playは淡いpink系の暗色と大きい文字、Exitはneutralと小さい文字。hover時だけ明るいpink、9単位の右端拡張、4単位の文字移動を使う。Play hoverではCookieもわずかに反応する。

左上は小さな `osu!java` とlocal difficulty数（表示名はlocal beatmaps）。右上はambient artworkのartist / titleと `LOCAL · HH:mm`。曲名は再生中という意味ではなく、背景に使う譜面の情報。音楽再生・jukebox操作は実装していない。左右で幅を分け、長い曲名は省略する。上端と下端には透明へ落ちる薄い暗色backingだけを敷く。Footerは `P / Enter — Play`。取得経路のないversionは追加しない。

## Artwork selection and future skin support

共有されるrecent selection、played history、永続選択は存在しない。Song Selectの選択はScreen内に閉じているため、その画面は変更しない。`AmbientArtworkSelection` はScreen生成時に一度だけlocal Libraryから選び、実在する背景ファイルを持つSetを優先する。difficulty固有背景を優先し、なければSet背景を使う。alphabetic minimumには依存しない。後から履歴優先policyへ差し替えられる小さな専用methodとし、大きなhistory systemは作らない。

背景画像はcoverと軽いdim、Cookie / menu近傍から左右に透明へ落ちるcontrast gradientだけを使う。背景がない／読み込めない時はdark gradientへfallbackする。画像やLibraryデータの生成・変更はしない。

現在の `SkinAssetResolver` はlocal provider / fallback / @2xを解決し、`OsuSkinAssets` はGameplay Screenが所有する。Main Menuはこれに依存せず、将来のmenu専用resolverを入れられる独立した描画経路を維持する。Song Selectでも使う従来 `OsuCookie.drawShape/drawText` はそのまま保ち、今回のCookie描画はMain Menu専用methodを使う。

## Interaction and animation

Cookie / Play click、およびP / Enter / SpaceはSong Selectへ進む。Exit click / Escapeはexitを要求する。既存の `UiNavigation` を共通のgateにして、二重要求を抑えつつ120msのoutgoing fade後に実行する。OSの既存quit shortcutも維持する。

EnteringはScreen内のUI秒だけで進む。背景は0〜180ms、Cookieは35〜255ms、Playは90〜315ms、Exitは135〜360ms。帯は左端をCookie背後に固定して右へ展開する。文字は展開済みの幅が足りる時にだけ描画する。hover補間は110ms。`UiTransition` の全画面incoming fadeを重ねず、incomingの各要素は `MainMenuMotion`、outgoingは `UiNavigation` と責務を分けた。Gameplay `GameClock` を使わない。

描画とhover hitboxは `MainMenuLayout` の同じanimated polygonを使う。右端の斜め部分とCookieに隠れる部分はstripのhitboxから除外する。

## Responsive and verification

既存 `UiLayout` のvirtual coordinatesを再利用する。縦長ではCookieを幅で制限し、帯の右端を94%に広げる。上部の2groupはそれぞれ幅の半分未満に収め、縦長では情報文字を1.3倍にする。Cookie・halo・帯と文字が画面内に収まることをlayoutテストで確認する。

Visual harness:

```sh
./gradlew :lwjgl3:mainMenuVisualHarness
# 任意: -PmainMenuOutput=/tmp/osujava-main-menu
```

1024×768、1280×720、1920×1080、600×800、1280×720@2x、600×800@2x × 背景あり／なし × initial、Cookie entering、Play entering、Exit entering、fully revealed、Play hover、Exit hover、Cookie hover、Cookie pressed = **108 captures**。production `MainMenuScreen` を描画し、pointer、UI秒、clock文字を固定する。同じ状態を2回描き、framebuffer全byteの一致をassertする。2xは論理window寸法を保った2倍解像度FBOで検証するため、native monitor移動やOSのDPI切り替え試験ではない。

7種類の操作を6種類のsize/densityでproduction Screenの入力・render経路に通し、**42 interaction checks** を行う。unit testsはkey/click routing、二重navigation抑制、斜めhitbox、Cookieの重なり、文字幅、density不変、entrance順序、ambient selectionを検証する。生成PNGとfixtureは `/tmp` 配下に置き、commitしない。

## stableからの参照と独自の判断

[公式Interface資料](https://osu.ppy.sh/wiki/en/Client/Interface) と [既存reference](ui-stable-reference.md) のCookie主役、左寄りの中心、背後から右へ伸びる帯、背景の露出、端に退いた情報、60 BPM fallbackを参照した。pixel cloneではなく、2行の実装済みaction、neutralの帯、静かなhalo、分離した2groupのmetadataでローカル専用clientとして整理した。未実装Options、online profile、chat、Bancho、偽の再生操作は表示しない。
