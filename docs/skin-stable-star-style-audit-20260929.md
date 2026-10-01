# Skin独立調査補足: Song Selectの星の描画方式

2026-09-29。[Skin独立調査](skin-stable-independent-audit-20260929.md)の補足。
今回もSkinのprovider条件を画面の時間変化とは分けて記録する。

対象は `b20230727.9/osu!.exe`、SHA-256
`bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a`。
ILのfield・呼び出し対応を調べた。公式asset抽出、保護回避、production serviceへの接続は行っていない。

## 確定した分岐

`06000fbf` `0051–0085`は選択SkinのVersionと、ある画像のprovider `04000338`から
flag `04000881`を設定する。この画像は**星ではなくmenu-button-background**である。

- `06000fc7` `004f–0060`が同じresource識別子で画像を取得して `0400087a`へ保存する。
- `06000fbf` `00b5–00ee`は `0400087a`をbackground sprite `04000872`のconstructorへ渡す。
- 星画像は別のresource識別子から `0400087b`へ保存され、`06000fd2`の20枚の星sprite生成で使用される。
- provider値1はbuilt-in、2は選択Skin、4は譜面側という対応は独立調査のprovider表で確認済み。

したがって、次の条件になる。

| Skin Version | 解決済み行背景 | `04000881` | 星の方式 |
| --- | --- | --- | --- |
| 2.2未満 | provider 1以外 | true | 横幅crop |
| 2.2未満 | provider 1 | false | scale |
| 2.2以上 | どちらでも | false | scale |

星画像そのものがcustomかbuilt-inかによってこの条件を置き換えてはいけない。
旧Versionのcustom星でも、行背景がbuilt-inならscaleになる。

## Javaへの対応と範囲

Screenが既存 `SongSelectSkinAssets` のVersionと `MENU_BUTTON_BACKGROUND` のproviderを参照し、
星animationへ方式を渡す。Skin loader/providerの探索順やparserの変更は不要だった。
Javaの独自fallback directoryからの行背景はnon-built-inとして扱う。nativeの追加providerに同定した主張はしない。
画像がない場合の既存Java fallbackはbuilt-in相当のscaleとし、公式標準assetのpixel一致は主張しない。

`06000739/073a`、`060040bb`で星spriteの寸法はdensity補正後の整数論理寸法と確認した。
SD 40pxとHD 80px、およびHDの奇数寸法81pxで同じ40整数論理pxの白画像を作り、描画harnessで寸法を検証した。
Version 2.2＋custom行背景、Version 1＋custom行背景、Version 1＋bundled行背景の3条件を
4解像度/DPIで実行した。最後の条件でも星画像はcustom HDのままである。

画像の選択・densityの回帰検証結果と画面側の実装は
[phase 9の星報告](songselect-parity-phase9-stars-20260929.md)を参照。
phase 12に残るHD eligibility、decode失敗、provider mask等を今回の条件修正で完了扱いにはしない。
