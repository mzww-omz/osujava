# stable結果データの追加解析: HP・UR・spinner・Perfect

2026-09-29。[初回解析](results-stable-research-20260929.md)と[不足機能計画](results-missing-data-plan-20260929.md)の未確定箇所を追跡した。
対象exe SHA-256は`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`、buildはb20230727.9。
今回もCLR metadata / ILの読み取りのみ。assembly実行・暗号化文字列の復号・asset抽出・改変・production接続は行っていない。
**以下の確定は指定バイナリの静的な分岐・算術についてであり、実機の入力／画像／音との一致検証ではない。**

## 1. 今回閉じた範囲

| 項目 | 追加で確認できたこと | 残る条件 |
| --- | --- | --- |
| HP graph | 固定最大HPではなく、各objectの全成功シミュレーション後HPで正規化 | nested失敗時の参照object、実機での点列比較 |
| HP | capped値とuncapped累積値、回復係数2種、drain係数の反復探索 | 起動時のHP充填・frame内順序、特殊譜面・Mod複合 |
| fail | HPゼロ判定→NF等の除外→fail処理→pass flagへの反映 | 同時刻hitとの前後、pause／retry表示の全分岐 |
| UR | standard通常入力では成功circleと成功slider headからsigned整数誤差を追加 | 入力clock自体の丸め、特殊Mod／観戦のdispatch |
| spinner統計 | 平滑化RPMをupdateごとに整数化して採取 | 再生時も含む正確なupdate頻度と順序 |
| spinner判定 | 半回転単位の閾値、短spinner、2019年変更前のreplay分岐、bonusの条件 | 入力サンプリング全体と速度Modによる変換 |
| Perfect | standardの最大可能comboを構成する計数元 | 破損・重なり・特殊objectでの実機挙動 |
| Geki / Katu | combo set終端のvariant bit、未判定objectによる抑制、HP加算 | 時間的に重なったsetの実行順序 |

モード混同を避けるため、最初に`060021a1:0013–0077`のfactoryを確認した。
`PlayModes` enum `02000b10`の0=Osuに対応する通常の集計器はType `020007dd`（constructor `06003a7e`）、beatmap側は`06003a81`→Type `020003a4`（`06001b2c`、基底`020000ec`）。
`020005fe`はTaiko、`02000702`はCatch、`020006fe`はMania。以前列挙した`0400189e`の更新元をすべてstandardの規則として扱わない。

本書のoffsetもdncilのmethod headerを含む。obfuscated名に任意の元クラス名を割り当てず、tokenと用途で記述する。

## 2. HPには3種類の量が必要

| 量 | token | 確認した操作 |
| --- | --- | --- |
| 実際のHP `H` | `0400197b`、getter `060027dc` | 加点時は0–200へclamp。正のdrain時は下限0 |
| 回復余力を測る累積HP `U` | `0400197c`、getter `060027de` | 加点時は上限を設けず、drain時は下限0 |
| object別基準HP `B[i]` | 各objectの`04001126` | 全成功シミュレーションで、そのobjectの処理後の`H`を保存 |

`060027f3`はシミュレーションのresetで`H=U=200`を設定する。
`060027f1`はHP加算、`060027f0`は減算、`060027ef`は設定処理。後者は`H=clamp(value,0,200)`、`U=value`とする。
これは「画面に入った最初のframeからHPバーが200で描画される」という意味ではない。表示用の量・開始時充填は別経路にある。

### 2.1 graphの分母は200ではない

`06003a90:03ec–03f8`が`B[i]`を保存する。`06002202:0045–008a`は現在のHPから次を作る。

```text
point.time  = Single(gameplayClock)
point.value = Single(min(1, H / B[manager.currentJudgedObject]))
```

分母の参照はbeatmap managerの`040002f7`を経由する。分母が正のときだけ採取する。
たとえば`H=120, B=160`ならgraph値は0.75で、固定200で割った0.6とは違う。この例は独立算術確認であり実機測定ではない。
「HPを0–1へ正規化」とだけ書くと実装を誤るため、**ゲーム内HPと結果graph値を別に保持する**。

採取条件は判定codeと`0x1f000706`（十進520095494）のANDが正であること。
standardで確認したcodeに適用すると、300 / 100 / 50とvariant、負のMISS / slider breakは対象になり、tick=8、head / repeat=64、tail=128、spinner途中=4096 / 8192 / 16384はこのmaskに入らない。
したがって「HPが変化するたびに必ずgraphへ点を追加」する契約ではない。呼出経路と参照objectを含む採取fixtureが必要。

保存文字列への2000ms超・先頭・末尾の選択と、描画時の100点間引きは[初回解析の追記](results-stable-research-20260929.md#追加調査-hp列の採取と保存は別処理)の通り。

## 3. HP係数の算出と判定別増減

以下はまずNoModで定義する。`R(h;a,b,c)`はHP設定hの0 / 5 / 10にa / b / cを対応させる区分線形補間。
`0600066f`で補間式、同メソッドから呼ぶ`0600066c`でEZの半減・HRの倍率と上限の適用を確認した。
元HP値とMod適用済みHPを二重に変換しない。

`C=040018a4`をcombo set回復倍率、`N=040018a5`を通常回復倍率と呼ぶ。`06002617`では両方1で初期化する。
これらはHP用で、score倍率の`040018a6`とは別。

### 3.1 共通集計器のHP効果

`0600264d`のstandardで使う分岐。数値codeは表示点数そのものではない。

| code / 意味 | HP増分 | 主なoffset |
| --- | --- | --- |
| 1024 / 300 | `6*N` | `088f–089f` |
| 512 / 100 | `R(h;17.6,2.2,2.2)*N` | `07f5–082d` |
| 256 / 50 | `R(h;3.2,0.4,0.4)*N` | `074f–0787` |
| 514 / 100 Katu | 100の回復＋`10*C` | `0a1d–0a66` |
| 1026 / 300 Katu | `6*N+10*C` | `0aaf–0ad0` |
| 1028 / 300 Geki | `6*N+14*C` | `0b19–0b39` |
| 257 / 513 / 1025 / set終端の通常variant | 対応する基本判定の回復＋`6*C` | `08d1–09eb` |
| 8 / slider tick | `3*N` | `0585–0598` |
| 64 / slider head・repeat系、128 / tail系 | `4*N` | `0655–0668`、`0725–0738` |
| 4096 / spinner半回転、8192 / 通常加点回転 | `1.7*N` | `0b6f–0b9c` |
| 16384 / spinner bonus | `2*N` | `0ba4–0bba` |
| −131072 / object MISS | `R(h;−6,−25,−40)` | `0469–049a` |
| −262144 / slider head・途中失敗、−524288 / tail欠損 | `R(h;−4,−15,−28)` | `03f9–0463` |

正なら`060027f1`、それ以外は符号を反転して`060027f0`へ渡す（`12eb–130d`）。
判定variantでは基本countとGeki / Katuの両方を更新するが、accuracyのobjectを二つに増やす意味ではない。
slider側`060017ab:03a2–03e1`は失敗点数を増やし、成功点数＋失敗点数がnested列長に達した場合に−524288、それ以前は−262144を返す。
共通集計器の−524288分岐だけはcombo変更flagをfalseにする（`0600264d:042b–042c`）。したがってtail欠損にはHP減少があっても現在comboのresetはない。最大可能comboへの未到達と区別する。
一般的な回復・減少の種類は[公式Health説明](https://osu.ppy.sh/wiki/en/Gameplay/Health)と整合する。上表の係数は同ページからの推測ではなく指定exeのILによる。

### 3.2 全成功シミュレーション

standardの`06003a90`はdrain係数`d=0.05`から始め、条件を満たすまで譜面を繰り返し評価する。
各iterationでHPと集計をresetし、`C,N,d`の調整は次のiterationへ持ち越す。

| 基準 | 式 | 算出offset |
| --- | --- | --- |
| object前の最低HP | `R(h;195,160,60)` | `0023–004b` |
| combo set終端のHP | `R(h;198,170,80)` | `004d–0075` |
| 最終HP | `R(h;198,180,80)` | `0077–009f` |
| objectあたりの余剰回復 | `R(h;8,4,0)` | `00a1–00c9` |

各objectについて、前object終端から今回の開始までのdrain、object継続時間のdrain、成功nested判定、最後に300またはGekiを適用する。
slider成功数は既存event列から、spinnerは必要半回転数ぶんcode8192を使ってシミュレーションする。実プレイのspinner加点頻度と同じloopとは限らない。
開始の基準時刻は最初のobject開始−preempt。音源長はこの計算の基準ではない。

調整規則:

1. object前HPが最低基準以下なら`d*=0.96`してやり直す（`0208–0225`）。
2. 長objectについて、先に引いたdrainの超過分を、nested回復後のHPからも考慮する。補正後が最低基準以下なら同じく`d*=0.96`（`022a–0262,0321–034e`）。
3. set終端HPが基準未満になる3回目に`C*=1.07, N*=1.03`でやり直す（`0389–03d0`）。counterは高いHPのsetを挟んでもこのloop内ではresetされない。
4. 最終HPが基準未満なら`d*=0.94, C*=1.01, N*=1.01`（`0412–0459`）。
5. `(U−200)/objectCount`が余剰回復基準未満なら`d*=0.96, C*=1.02, N*=1.01`（`045e–04c0`）。

breakの扱いにもversion分岐がある（`0164–01c8`）。前object終端以後に開始し、次object開始までに終了するbreakに対し、format>=8ではbreak終端−前object終端、旧formatではbreakの長さをdrain時間から差し引く。
複数breakが同じobject間にあるcaseや、時間的に重なるobjectの負のgapについては、parserの整列・正規化も含めた追加確認を残す。

`06003a82`は得られたdをHP部品の`04001979`へ保存し、HT bitがある場合には0.75を乗じる。
実プレイの`06003a86`はdrain有効flagに従い、譜面時刻差×dを減算する。現在のobjectがspinnerであるflagではさらに0.25倍する。
flagの由来は`060021db:0067–007e`、drain範囲のgateは`060021d1:0e9a–0f2c`と`060021d5`。break中だけでなくbreak前後・preempt・再生状態も関係する。

**実装判断:** calibrationは描画・音・入力を動かさない純粋な計算にする。stable内部のscoreやHPを一時的に書き換える実装構造を模倣する必要はない。
出力はd、C、N、object別B、最大可能combo。極端な譜面での収束と終了条件は実装前のfixtureにし、無限loopを持ち込まない。

## 4. 失敗とpass

`060021d2`はHP部品が存在して`H<=0`の場合、NF bit、RX / AP、replay等の状態による除外を調べて、`060021e3`→`06002646(false)`へ進む。
RX / APのflagは`06002632:000c–0049`のmod bit 128 / 8192から確認した。
通常のfail処理は`040015b4=true`とその時刻を記録する（`06002646:0066–0076`）。
結果確定`06002648:0018–0026`は、このfail flagがfalseかどうかをpassへ入れる。全object処理済みや最終accuracyからpassを推測しない。

EZはSDなしの場合に回復回数2を設定する（`06002632:0085–00bc`）。fail入口で回数が残っていれば`06002647`へ進み、HP回復・pause側処理・回数減少を行う。
SDのcombo breakはHPを0へ設定する経路がある（`0600264d:12a0–12cf`）。PFはMISSへの置換とretry分岐を別に持つ。
これらの表示、同時刻hitとfail checkの順序、pause解除は今回の完全確定範囲に含めない。

## 5. URに入る入力

`060021f9`はnullableの明示誤差があればそれを、なければ整数の`040014b1−object.040027f8`を`04000a55`へ追加する。
standardの通常クリックhandler `060021fb`の2箇所では明示誤差はnull。結果のURは成功判定を同じsigned整数標本から集計する。

| 通常経路 | 採取 | 根拠 |
| --- | --- | --- |
| 成功circle | する | circle判定の`0600068d`が正を返した後、`0172–017e`で追加 |
| 成功slider head | する | head未判定を確認し、`060017a0()>0`なら`01d2–01de`で追加 |
| headのMISS・早押し失敗 | しない | 非正のhead判定は`0210–021c`のbreak処理へ分岐 |
| circle MISS | しない | `0600068d`の戻り値が正でないと採取へ進まない |
| 空打ち | しない | candidate=nullで採取経路へ入らない |
| notelockで拒否 | しない | `06002654`の結果0 / 1は採取箇所を通らない |
| slider途中のtick / repeat / tail、spinner途中／終端 | この通常経路ではしない | 別の更新・判定経路であり、同採取methodのstandard呼出元に追加箇所はない |

`0600179f`はslider headの判定済みflagを返すため、すでにMISSとなったheadの後続trackingを「遅い成功入力」としてここでURへ追加しない。
他の`060021f9`参照元`0600251d`はManiaのbeatmap処理、`0600358a`はCatchの集計器。standardへの移植根拠にしない。

score constructor `060012e2`はhit errorとspinner標本の空Listをそれぞれ作る。保存形式にこれらの生標本があることを意味しない。
母分散、負／非負平均、UR=標準偏差×10、空列処理は初回解析の通り。
譜面時刻基準という公開仕様とも整合する。[公式UR説明](https://osu.ppy.sh/wiki/en/Gameplay/Unstable_rate)
入力clockの丸め、offset、RX / APの自動入力、replay入力がこのhandlerへ届く細部は別fixtureで検証する。

## 6. spinner結果統計は平滑化したRPM標本

`06000838:003d–009b`は次の更新を行い、`:00cd–00de`で`060021fa`へ渡す。

```text
a   = pow(0.9, deltaMs / (1000/60))
rpm = a * previousRpm + (1-a) * abs(angularVelocity) * 1000 / (2*pi) * 60
sample = truncateToInt(rpm)
```

`angularVelocity=040003db`はrad/msに対応する状態値、rpmは`040003cd`。
`deltaMs=04001b62`は`06002af9:0173–018e`で譜面clockから前回値を引いて更新する。
採取はspinnerが未判定かつ開始時刻以後のupdate経路。`060021fa`はmode 0以外を除外する。
平滑化と採取は同method内の加速度追従より前にあるため、更新後の速度を代入して同じ式を呼ぶだけでも位相が変わり得る。

例: 前回rpm=0、速度0.03 rad/ms、delta=16msならrpm約27.5589885、保存値27。
これは算術例であり、stableの実機frameを測定した値ではない。
画面側はこの整数列の平均・最大・母標準偏差×2を使用する。0標本を勝手に捨てたり、時間重み付き平均へ変更しない。

カーソルの角度差をそのまま回転数に積む実装とは異なる。`06000839`には角度wrap、入力間隔の平滑化、無移動時の減速、約17.333msの分岐がある。
`06000838`には速度追従、±0.05 rad/msのclamp、frameあたり最大1半回転ぶんの進行加算がある。
このため、**spinnerの判定が合っても、異なるupdate標本列から作った結果統計は一致しない**。録画・replayの時刻／update順の契約を先に用意する。

## 7. spinnerの必要回転・判定・bonus

`06000678:003c–006a`の必要速度は`R(OD;3,5,7.5)`で、半回転/秒の単位。
`06000837:01c4–01e0`はdurationをSingleで1000除算した後にDoubleへ変換して速度を乗じ、intへ切り捨てて必要半回転値`Q=040003cc`を作る。
蓄積半回転数は`040003ce`（以下K）。Singleを経る位置も境界fixtureに含める。

### 7.1 2019年変更後の判定

`0600083d:0083–00e0`を分岐順のまま表すと以下になる。

```text
Q == 0                  -> 300
K < max(0, Q / 4)        -> MISS       // Q/4は整数除算
K > Q                   -> 300
K > Q - 2               -> 100
otherwise               -> 50
```

Q=5ならK=0でMISS、1–3で50、4–5で100、6以上で300。
Q=1ではK=0でも100、Q=2ではK=0でも50へ到達する。これはIL上の境界であり、実機の短spinner観測caseとして残す。
clear側の閾値はQ、300はQ超なので、同一条件ではない。

`0600083a`は保存scoreの日時が2019-05-10より前、**または**保存versionが20190510未満なら旧判定を選ぶ。
旧判定はK<QでMISS、K>Q+1で300、K>Qで100、それ以外50。新判定のQ=0特例をこの旧経路へ追加しない。
公開wikiの2019年変更・半回転単位・clearとの差を、指定buildの分岐と照合した。[公式判定仕様](https://osu.ppy.sh/wiki/en/Gameplay/Judgement/osu!)

### 7.2 bonus

`06000839:02e0–04cb`でKを増やした後、次の優先順でeventを返す。

1. `K>Q+3`かつ`(K−Q−3)%2==0`ならcode16384。
2. それ以外でK>1の偶数なら8192。
3. それ以外でK>1なら4096。
4. その他は0。

`0600264d`でこれらは1100 / 100 / 0点へ対応する。通常回転100点とbonus1100点は排他的なeventで、1100へさらに100を足さない。
HP効果は前掲の通り。Q=5なら最初のbonusはK=10。この境界とclear・300判定を同じ閾値にしない。

## 8. combo setとPerfect

### 8.1 Geki / Katu

`0600068d:0215–0246`は結果50 / MISSで失敗側counter、100で非300側counterを増やす。
standardで有効な`06000675()`の下、最後のobjectまたは次objectのnew-combo bitでset終端を判定する（`024b–028d`）。
さらに現在setを後方へ辿り、未判定objectが残るか調べる（`0295–02dc`、spinnerはこの走査を省く）。
正の結果について、counter・未判定状態に応じて以下をORする。

| 条件 | variant bit | 集計上の効果 |
| --- | --- | --- |
| 非300なし、失敗なし、未判定なし | 4 | 300 Geki |
| 失敗なし、未判定なし | 2 | 100 / 300 Katu |
| その他 | 1 | 通常のset終端回復。Geki / Katu countは増やさない |

その後counterをresetする。後続setの見た目の色や、最終的に揃った判定数だけからvariantを再生成してはいけない。
sliderが後続objectと重なる場合、この「判定時点で未判定が残る」条件を時系列fixtureにする。

### 8.2 最大可能comboの生成元

`06003a90`の各iterationで`0400189e=0`へresetする。
circleとspinnerは各1。sliderでは`A=count(04000e35)+2`、`T=count(04000e36)−count(04000e35)−1`として、途中で`A+T−1`、共通終端で1を加える（`0278–02b4,03de–03e7`）。
従ってsliderの寄与は**`count(04000e36)+1`**。同じ列＋headの1はslider全体精度の分母にも使われる（`060017a1:0019–004b`）。
Perfectは結果確定時に`!(maximumPossibleCombo−score.maxCombo>0)`を保存し、表示側はそのbooleanを読む。

**実装判断:** sliderの採点点列と最大可能comboに別々のtick生成器を作らない。geometry / timingから確定した点列を共有する。
ScoreV1ではheadのタイミング判定をslider全体accuracyに直接加算しない（`060017a1:00ce–0109`）。取得率1なら300、そうでなく0.5以上なら100、そうでなく正なら50、0ならMISSに対応する。
ScoreV2の分岐はhead判定も参照するため、同じ計算式へ押し込まない。

## 9. 実装計画への反映と未確認事項

NoModについては、前回未確定だった通常入力のUR採取、spinner判定境界、HP回復式・calibrationの主要分岐、最大可能comboを、独立実装用の契約へ進められる。
P6には`H / U / B[i]`と`d / C / N`を追加する。P7は単なるraw RPM収集をやめ、spinnerのupdate順を含む標本契約を使う。
P4 / P5のテストにはQ=0 / 1 / 2、Q−2 / Q−1 / Q / Q+1、K=Q+5、未判定sliderを残したset終端を追加する。
P9 / P10ではspinnerの旧挙動選択にscore日時も使われるため、versionだけで互換経路を選ばない。

未解決のままにする項目:

- 指定buildの実機入力・画面・音の記録。今回のIL確認を実測済みとはしない。
- HP calibrationと実プレイ開始時の充填／reset順、重複break・負のobject間隔・空譜面の処理。
- HPゼロとhitが同じframeに来た場合の順序、fail演出とRetry、NF / EZ / SD / PF複合。
- spinnerの物理入力・update dispatch・速度Modの変換と結果標本列の完全一致。
- HP採取時のmanager参照objectと、slider途中失敗・overlap時の点列。
- Geki / Katuのset跨ぎを含む動的順序、gradeのSingle境界、font / easing / global入力dispatch。

### 再読手順

[読み取り専用tool](../tools/stable_results_inspect.py)で、対象hashを確認しながら再読できる。使用環境はdnfile 0.18.0 / dncil 1.0.2。

```sh
python tools/stable_results_inspect.py '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 060021a1 06003a90 06003a82 06003a86 060027f0 060027f1
python tools/stable_results_inspect.py '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 060021fb 060021f9 06000837 06000838 06000839 0600083a 0600083d
python tools/stable_results_inspect.py '/home/coder/workspace/b20230727.9/osu!.exe' \
  --methods 0600068d 060017a1 060017ab 06002648 0600264d 06002202
python tools/stable_results_inspect.py '/home/coder/workspace/b20230727.9/osu!.exe' \
  --refs 0400189e 04001126 060021f9 060021fa
```

生ILは`/tmp/osujava-results-research-20260929/followup-*.il`へ保存し、Gitには独立に記述した分析だけを含める。
mask、spinner境界、正規化例、RPMの算術を別計算で照合した。これはCLR実行テストではなく、解析中の転記・分岐解釈を確認するための補助検証である。

本追加調査の検証: 指定hashのIL再読・全method参照走査に成功し、parse失敗は0。文書4件のローカル参照44件（anchorを含む）とdiffを確認した。
`./gradlew --offline build`成功（13 tasksすべてUP-TO-DATE、既存test結果を再利用）。文書だけの変更のため製品コード・テストは追加していない。stableとの実機比較は未実施。
