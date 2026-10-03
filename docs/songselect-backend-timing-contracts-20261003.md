# B05-T2: Slider timingの参照契約と残件（2026-10-03）

## 今回の範囲

今回の実装単位は **v8以降のSV 0.01丸め境界とclamp**。公開frameworkの数値bindableをoracleへ接続し、
既存38fixtureのreference出力をbyte単位で保持したまま、`timing-sv-rounding`と`timing-sv-clamp`の
2fixtureを追加した。新fixtureは計20sliderケースを含む。星、skill、path、nested、lazy travel、
object/section中間値を既存の許容差で照合する。

追記: 同時刻timing（3fixture・36slider）と継承NaN（2fixture・16slider）も実装・Java検証済み。
元順変更はparserのtimingOrderChangedとして通知し、評価側はsourceが再配置されたslider chartをUNSUPPORTEDとする。
以下の契約はこの実装に使用した。pre-v8／nested tie／元順変更chartは引き続き未対応。
full decoder互換、stable実機数値比較、B05全体の完了も主張しない。
実行結果とcache変更は[B05実装記録](songselect-backend-difficulty-20261003.md)、通常Phaseの順序は
[backend改善計画](songselect-backend-improvement-plan-20261002.md)で管理する。

参照は公開lazer tag `2023.815.0`、commit `4e96853c7543f80a1b822ccd381943c7377543d7`と、
対応framework commit `3365c86f769cb0ed84a10c5c96313a73e552dc2d`に固定する。
development oracleだけが公開MIT sourceを一時directoryへ取得し、原処理を実行する。
通常Gradle/JUnit/appはnetworkへ接続せず、check-in済み自作fixtureを使う。
公式asset抽出、production API接続、stableのコードをappへ転用する処理はない。

## Raw SVとSlider SV

[LegacyDecoder.LegacyDifficultyControlPoint](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/Formats/LegacyDecoder.cs)
は独立したsource fileではなく、`LegacyDecoder.cs`内のnested classである。
constructorで`SliderVelocityBindable.Precision`を`double.Epsilon`へ変更する。
このcontrol pointは0.1–10へclampされた、**0.01に丸める前のSV**を保持する。

一方、[osu! Slider.SliderVelocityBindable](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Objects/Slider.cs)
のprecisionは0.01、範囲は0.1–10。legacy control pointの値をSliderへ設定した段階で丸める。
[framework BindableNumber.setValue](https://github.com/ppy/osu-framework/blob/3365c86f769cb0ed84a10c5c96313a73e552dc2d/osu.Framework/Bindables/BindableNumber.cs)
はclamp、precisionで除算、既定の`Math.Round`、precisionで乗算の順に処理する。
halfwayは偶数丸め。Javaの独立実装では同じbinary double演算順と`Math.rint`を用いる。
decimal文字列で「ちょうど中間」と見えても、beatLengthの逆数や0.01除算により実際のbinary値は
境界の片側になることがあるため、入力値と実際のSVを区別する。

SV oracleはhalfwayの前後、偶数側／奇数側、両clamp端、範囲外を対象とする。
raw SVとrounded SVの二重状態はpre-v8検証の際に必要になる。今回のv8+計算へ不要なmodel追加は行わない。

## 同時刻timingと元file順

[LegacyBeatmapDecoder](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/Formats/LegacyBeatmapDecoder.cs)
の`handleTimingPoint`、`addControlPoint`、`flushPendingPoints`を一体の契約として扱う。
decoderは元file順に読み、timeが変わった時とparse終了時にpending batchをflushする。
同じ時刻の連続batchでは、green由来のdifficulty pointがred由来より優先される。
複数redは最初、複数greenは最後が各typeの採用候補になる。

| 元fileの連続batch | 有効red beatLength | 有効difficulty SV |
| --- | --- | --- |
| red A → green B | A | B |
| green B → red A | A | B |
| red A → red C | A | A由来 |
| green B → green D | 以前のred | D |

この規則を「全rowsを時刻sortし、各時刻を一括処理」と一般化してはいけない。
異なる時刻rowを挟んで同じ時刻が再登場した場合は別batchとなり、
[ControlPointGroup.Add](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/ControlPoints/ControlPointGroup.cs)
は同typeの既存pointを置換する。redundancy判定も含めて観測する必要がある。

現在Java parserはTimingPointsを安定時刻sortするため、非連続同時刻の元順情報を保持していない。
今回は元順で時刻逆転したchartをparser診断へ保持し、slider評価を明示UNSUPPORTEDとする選択をした。
sort済みlistだけから元batchを推測しない。Import／Gameplayの現在値を変更する場合は、その影響を別に検証する。

[ControlPointInfo.TimingPointAt](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/ControlPoints/ControlPointInfo.cs)
は対象時刻以前のredがなければ最初のredを使い、redなしなら1000msを使う。
[LegacyControlPointInfo.DifficultyPointAt](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Beatmaps/Legacy/LegacyControlPointInfo.cs)
は最初のgreenの前にはdefault SV=1を使う。future redのfallbackとfuture greenの扱いを混同しない。
red beatLengthのpublic bindable clamp範囲は6–60000msだが、現在Javaが範囲外を受理したことは意味しない。

## Inherited NaN

Legacy decoderはbeatLengthのNaNを読み取れるが、redではerror、greenでは合法な特殊値として扱う。
NaNとの比較はfalseなのでraw SV=1になり、LegacyDifficultyControlPointは`GenerateTicks=false`を保持する。
NaNは全timing情報が欠損していることを意味しない。

Sliderの`ApplyDefaultsToSelf`は通常のvelocityを計算し、tick生成なしの場合だけTickDistanceをInfinityにする。
[SliderEventGenerator.Generate](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Objects/SliderEventGenerator.cs)
はこれをpath lengthへclampするので通常tickは出ないが、head、repeat、legacy tailは残る。
「GenerateTicks=falseならnested全体を省略」は誤り。

NaN→通常greenでtick生成が復帰すること、SV=1同士でもGenerateTicks差がredundancy判定で消えないことを検証する。
同時刻NaNと通常greenはbatch規則に従う。source品質通知はgreen NaNを破損扱いせず、
B05-T2cでは継承NaNを正当なtick抑制設定として評価する。赤NaNは引き続きsource破損としてFAILEDとする。

## Pre-v8と古いversionのoffset

[OsuBeatmapConverter.ConvertHitObject](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapConverter.cs)
はv8未満でTickDistanceMultiplierにlegacy difficulty pointの**未丸めSVの逆数**を設定する。
tick距離は、rounded Slider SVを含むscoring distance／tick rateに、このmultiplierを掛ける。
例えばraw SV=1.234ならSlider SV=1.23、multiplier=1/1.234。1/1.23で代用すると異なるtick位置／時刻になる。
SVが0.01精度に一致する例だけでは、この差を検出できない。

まずv6/v7対v8を同一入力で比較する。v6/v7の時間offsetは0。
LegacyBeatmapDecoderはv4以下について+24msをTimingPoint、hit object、Break、previewへ適用するが、
現在pre-v6 stackingは未検証として拒否しているため、v6/v7 sliderを検証する単位で古いoffsetを実装する必要はない。
v4対応はstacking／parser／時刻modelを含む別単位にする。

## Nested sortと演算順

Public Sliderはevent generatorからnestedを作り、legacy tailにはeventのpath progressではなく実際の
EndPositionを設定する。[HitObject.ApplyDefaults](https://github.com/ppy/osu/blob/4e96853c7543f80a1b822ccd381943c7377543d7/osu.Game/Rulesets/Objects/HitObject.cs)
はそのnestedを`List.Sort`でStartTime順に並べる。C#のこのsortはtieの安定性を保証しない。
Javaの安定sortと同値と決めつけない。nested数が小さい例だけで大きい例まで受理しない。

短い2-span sliderではrepeatとlegacy tailが同時刻になり、両者の位置／repeat属性が異なる。
lazy cursorの移動閾値と最終nestedの扱いに影響するため、tieがあっても時刻だけ照合すればよいとはいえない。
生成順、sort後の属性／位置、lazy end／distance／time、次objectのminimum jumpまで比較する。

またpublic Sliderのrepeat時刻は`StartTime + (SpanIndex + 1) * SpanDuration`を再計算する。
event descriptorの`spanStartTime + SpanDuration`と数学的には同じでも、浮動小数点の演算順は違う。
legacy tailも最終span開始時刻とspan durationを足してから36msを引く。
現在のfixture wrapperを将来tie／大きなtimestampへ拡張する時は、public Sliderのmaterialisationも実行対象にする。
演算順が違うadapterを「原処理実行」と表示しない。

## 次の小単位と受入条件

通常PhaseのB05-T2を、以下の順で小さく進める。各単位でcacheのpreprocessing version、unknownの保持、
失敗後の次job、cold／warm result、Song Select反映を確認する。未検証subsetの制限は検証が揃うまで解除しない。

| 単位 | Oracleへ接続するpublic処理 | 自作fixtureと回帰テスト | 受入条件 |
| --- | --- | --- | --- |
| SV 0.01／clamp（今回） | pinned framework数値bindable | 2fixture、20sliderケース。halfway前後・偶数／奇数・clamp端 | 旧38reference byte保持、新2fixtureの全中間値一致。v8+丸め境界だけ制限解除 |
| 同時刻timing（実装・Java検証済み） | decoderのtiming parse／pending flush、control pointの検索／置換／redundancy | red→green／green→red、red複数、green複数、混在、sliderが直前／同時刻／直後、future red／green、非連続同時刻 | 有効beat／raw SV／GenerateTicksを直接出力し、nestedと最終ratingも一致。元順喪失を解消、または該当chartを明示unsupported |
| Green NaN（実装・Java検証済み） | LegacyDifficultyControlPointとSlider defaults／nested生成 | NaN単独、NaN→通常green、repeat、同時刻NaNの両順序、red NaN | 通常tickだけ省略、repeat／legacy tail維持。red NaNは破損、green NaNは正当な設定として区別 |
| v6/v7 tick距離 | converterのmultiplier設定とSlider defaults | SV=.5／2／1.234、rounded境界、v6／v7／v8対、repeat、future red | raw／rounded SVを分離したtick距離・nested・lazy・rating一致。pre-v6は引き続きunsupported |
| Nested tie | public Slider materialisationとHitObjectのsort | 短い2-span、tickとtail同時刻、16個以下／超のnested、長いtimestamp、repeat多数 | sort後属性／位置とlazy／次objectまで一致。再現できないtieはunsupportedを維持し、勝手なtie-breakを追加しない |

数値許容差は既存と同じくstar／aim／speed absolute 1e-9、object／section absolute 1e-7＋relative 1e-9、
stack高さとnested数は完全一致。既存fixtureが変化した場合はadapter変更とpublic処理接続の影響を調べ、
Javaに合わせてexpectedを変更しない。JUnitは固定referenceだけでoffline実行できる状態を維持する。

Stableとの実機比較はB05-T4／P10の残件。公開2023 oracleへの一致だけを根拠に、
stable全譜面・現行2026 algorithm・Gameplay timingとの一致を主張しない。
