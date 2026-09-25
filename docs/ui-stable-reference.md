# osu!stable UI visual reference

調査日: 2026-09-25。対象は **osu!stable**。osu!(lazer) の画面は参照していない。

## 観察した資料

- [公式Wiki: Client / Interface](https://osu.ppy.sh/wiki/en/Client/Interface) の Main menu、Play menu、Song select、Beatmap information、Group and Sort、Search、Rankings、Beatmap carousel、Gameplay toolbox。掲載の Main menu 全景、Song select 全景、carousel 拡大画像を目視した。
- [公式Wiki: Skinning / Interface](https://osu.ppy.sh/wiki/en/Skinning/Interface) の Main menu と Song selection。特に `menu-background.jpg`、`menu-back.png`、`menu-button-background.png` の仕様を確認した。
- [公式forum: song selection panel](https://osu.ppy.sh/community/forums/topics/1066379) と [song select GUI](https://osu.ppy.sh/community/forums/topics/1133006)。stable の右側選曲パネルとスキン要素の位置について記述を照合した。

## Main Menu: 画面で確認できる特徴

- **Cookie**: 16:9 画像では画面幅の約 1/3 に近い直径。中心は画面中央より左、縦方向はほぼ中央。太い白の外周とピンクの円内に大きな `osu!`。背景上に浮き、周囲に細い放射状の動きがある。公式説明では曲の BPM に合わせて脈動し、音がない時は 60 BPM。
- **Menu button**: Cookie の右側から水平に伸びる幅広の帯。左端は Cookie に隠れ、右端は斜めに切られる。角丸の独立したカードではない。各行は狭い隙間で縦に重なり、文字は帯の中央寄り、アイコンは右寄り。
- **展開方向**: Cookie を起点に右へ展開。公式Wikiの Play menu は Play から Solo/Back 等へ分岐するが、ローカル単独プレイのみの本作では未実装の Multi/Edit を表示しない。
- **上部情報**: 細い帯にプロフィール、譜面数・経過時間・時計、右端の再生曲と jukebox が密に並ぶ。余白だけのヘッダーではない。
- **Jukebox**: 右上に現在の曲名と小さな再生操作がまとまる。公式Wikiは前曲、再生、停止、次曲などを列挙する。
- **Footer**: 画面下端には細い暗色帯。左の出典/ロゴ、中央付近の短い tip、右の chat 情報が小さく存在する。本作では存在しないオンライン要素を作らず、ローカル情報のみを載せる。
- **Background**: 画像または暗い装飾背景を全画面に敷き、Cookie と帯の背後に残す。Skinning Wiki の `menu-background.jpg` は中央基準の cover。
- **Animation / hierarchy**: 最も強いのは Cookie、次が右に伸びる帯。小情報は四隅に退き、Cookie 周囲の脈動とメニュー展開が静止画との差になる。

## Song Select: 画面で確認できる特徴

- **Carousel**: 画面の右半分前後を占有し、上の検索/整列帯から下の toolbar 直前まで続く。行は横長で画像の `menu-button-background` 推奨最小寸法が 690×85。単純な等間隔リストではなく、行ごとに左端位置がずれ、上下に近接して重なる。
- **選択 difficulty**: 明るい白系の行で、同 Set の青系 sibling より左へ張り出し、前面に見える。選択の形とコントラストが最優先。Wikiの色にはプレイ済み等の意味があるため、本作でその意味を偽装しない。
- **Sibling / other Set**: 同一 Set の別 difficulty は選択行の近傍に展開し、別 Set はより小さくまとまった行で前後に続く。拡大画像では sibling が同一 thumbnail を持ち、選択行との関連が明瞭。
- **Thumbnail**: 行の左端に小さな横長画像。Skinning Wiki は画像左端から 9 px、115×85 px と指定。画像がない場合も同じ領域の形を維持する。
- **Background**: 選択譜面の背景が画面全体に拡大され、中央左にはかなり大きく露出している。上部と左の文字の下、右行の下を局所的に暗くする。大きな不透明情報カードで覆わない。
- **Metadata**: 左上の薄い暗色領域に曲名・難易度・mapper を細かく複数行表示し、その下に Length、BPM、Objects、Circles、Sliders、Spinners、OD、HP、Stars。歌名と difficulty は一体で、difficulty は角括弧で示される。
- **Ranking area**: 左側の縦領域にランキング切替とスコア行。オンライン記録がない時は公式UIにも `No records set!` がある。本作はローカルスコアがないなら簡潔に `No local scores` とする。
- **Group / Sort / Search**: 上部右側に Group と Sort の小さい選択欄が並び、その下に細い grouping tab。Search は同じ上端の帯に収まり、キーボードから直接入力可能。今の本作にない分類機能を見かけだけ追加しない。
- **Bottom toolbar**: 下端を横断する固定の暗色帯。左にピンクの Back、続けて Mode / Mods / Random / Beatmap Options、中央寄りにユーザー情報。右下の巨大 cookie は toolbar に重なり、下端で一部が切れる。
- **Play cookie**: 右下で最も強い操作目標。Wikiによれば選択譜面を再生する。普通の四角い Play ボタンとは画面上の強さが違う。
- **重なり**: 上部 metadata と操作帯、左ランキング、右 carousel、下 toolbar が背景画像の上へ直接重なる。carousel はランキングの右へ、選択行だけ中央方向へ張り出す。cookie は行と toolbar のさらに上。
- **Selection / scroll**: Wikiによればホイール、上下キー、ドラッグ、右クリックによる絶対スクロールを備え、同じ行の再クリックまたは Enter で Play。画像では中央の選択行を基準に周辺行が前後へずれ、選択行の進出と明度変化で現在位置を示す。再設計では短い補間でこの変化を表現する。

## osujava に適用する際の判断

- オンライン ranked status、Bancho、Account、Chat、Multi、Edit を擬装しない。
- 色の役割は現在選択・同一 Set・別 Set というローカルの実データに割り当てる。
- 譜面背景がなければ暗色の抽象背景、thumbnail がなければ同寸の fallback を使う。
- 画面の silhouette、情報密度、階層、重なりと短い動きを優先し、実データがない機能のボタンは置かない。

## 旧UIとの差分と実装順

調査時点の Main Menu は Cookie 右側に独立した角丸ボタンを縦配置しており、文字の大きさと帯の重なりが stable の silhouette と異なっていた。Song Select は左の不透明な hero card と右の平坦な Set list が背景を大きく隠し、難易度の選択行も同一 Set の階層も carousel として見えなかった。

実装順は、(1) Main Menu の Cookie と右向きの斜め帯、(2) Main Menu の短い展開・脈動、(3) Song Select の右側行と Set/difficulty 展開、(4) 背景・top-left metadata・local score 領域・下端 toolbar、(5) 選択補間・thumbnail・検索、(6) GUI と build の確認とした。UI clock だけを使い、Gameplay の GameClock や判定処理には触れない。
