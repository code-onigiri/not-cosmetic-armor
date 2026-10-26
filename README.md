# Not Cosmetic Armor

<img src="src/main/resources/logo.png" width="128" alt="Not Cosmetic Armor">

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8527)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-f16436)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-007396)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![build](https://github.com/code-onigiri/not-cosmetic-armor/actions/workflows/build.yml/badge.svg)](https://github.com/code-onigiri/not-cosmetic-armor/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/code-onigiri/not-cosmetic-armor)](https://github.com/code-onigiri/not-cosmetic-armor/releases)

Cosmetic Armor Reworked のコスメ枠に入れた装備を、見た目だけでなく、実際に着ている装備として扱う 1.20.1 Forge 向けのアドオンです。
コスメ枠の防具の防御力が加算され、エンチャントが効き、MOD 側の「この装備を着ているか」という判定にもコスメ装備が見えるようになります。

> **English**: An addon for [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)
> that makes cosmetic armor actually work on 1.20.1 Forge: armor attributes, enchantments, item ticks,
> durability and mod equip checks. CAR is required on both client and server.
> Download the jar from [Releases](https://github.com/code-onigiri/not-cosmetic-armor/releases).

![third person](docs/img/third-person.png)

*インベントリの防具スロットは実装備（ダイヤ）、体に描かれているのはコスメ枠の金の胸当て。見た目と中身の層が分かれています。*

## 動作環境

| 項目 | バージョン |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x（開発・検証は 47.4.28） |
| Java | 17 |
| 必須 | [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) 4.x（1.20.1-v1a で検証） |

CAR は必須依存です。CAR が無い場合、Forge は「Missing or unsupported mandatory dependencies」として起動を止めます。

## 導入

1. [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)（1.20.1 版）を `mods/` に入れる。
2. 本 MOD の jar（`notcosmeticarmor-1.20.1-<version>.jar`）を `mods/` に入れる。[Releases](https://github.com/code-onigiri/not-cosmetic-armor/releases) から取得する。
3. クライアントとサーバーの両方に入れる。装備の判定はサーバー側で行い、見た目の差し替えは CAR が担当する。
4. サーバー側では `config/notcosmeticarmor-server.toml` が生成される。既定で有効。

導入できているかは `/notcosmeticarmor status`（権限レベル 2 以上）で確認できます。
`car: detected` と `mode=INTERLEAVE` が出ていれば正常です。

## できること

- コスメ防具の防御力・防具強度・ノックバック耐性・移動速度などを、実装備の属性に加算します。加算用の識別子はプレイヤーとスロットごとに分けているので、実装備の属性と重複しません。
- ダメージ軽減（Protection）、落下軽減（Feather Falling）、棘（Thorns）、水中呼吸（Respiration）など、バニラのエンチャントがコスメ防具でも有効になります。同じスロットの実装備のエンチャントとも合算されます。
- エリトラの滑翔、カメの甲羅の水中呼吸、MOD の「この装備を着ているか」という判定にも、コスメ装備が見えます。
- コスメ枠のアイテムにも通常の tick が走ります。エネルギーやチャージを持つ MOD 防具（TiC、Iron Jetpacks など）が動くのはこのためです。tick の中でプレイヤーの能力を書き換える装備（夢幻終焉チェストプレートの飛翔など）も機能します。
- jetpack のように速度を変える装備は、その動きが画面にも反映されます。
- 実装備が耐久ダメージを受けると、同じスロットのコスメ装備も同じ量だけ減ります。Unbreaking の計算や、壊れたときに消える挙動も実装備と同じです。`status` の `dmg=` で両方の耐久値を確認できます。
- fantasy_ending（EndingLib）の 4 点セット判定は、実装備とコスメ枠の合算で行います。コスメ枠に別の防具を載せても実装備の 4 点セットは消えず、実装備 2 点とコスメ 2 点でもセットが成立します。
- Mekanism のように、メカスーツを着ている前提で能力を判定する MOD では、コスメ枠の装備がメカスーツの判定を隠してしまいます。そこで既定では、`mekanism` の装備が実装備枠にあるスロットに限り、MOD から見える装備を実装備に戻します。対象は設定で増減できます。

## できないこと

- 見た目の差し替えは従来どおり CAR が担当します。本 MOD は描画に関与しません。
- コスメの下にある実装備も、これまでどおり tick されます。
- エンチャントによる軽減はバニラの規則どおり、EPF の合計 20（最大 80%）で頭打ちです。実装備とコスメに積んでも上限は変わりません。
- jetpack の推力は 1 個ぶんの速度で頭打ちになる仕様なので、実装備とコスメに 1 個ずつ入れても 2 倍にはなりません。両方に入れた場合は実装備側の速度になります。
- バニラは金の防具を着ているかでピグリンの敵対を決めるため、コスメ枠の金防具でもピグリンは敵対しません。バニラと同じ判定に乗る結果として、意図的に残しています。
- 本 MOD が差し替えるのは装備の取得経路です。インベントリの生のリストを直接読む MOD には、コスメ装備が見えません。必要な MOD には本 MOD 側で個別に対応しています（ProjectE、Sophisticated Backpacks）。

## 対応 MOD（実機で確認）

| MOD | バージョン | 結果 |
|---|---|---|
| [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) | 1.20.1-v1a | 必須。コスメ枠の保持と描画を担当 |
| [Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) + Mantle | 3.12.1.231 / 2023.11.24 | コスメ枠の属性と耐久が機能する |
| [Iron Jetpacks](https://modrinth.com/mod/iron-jetpacks) + Cucumber | 7.0.9 / 7.0.16 | コスメ枠で飛行する（速度は実装備側が優先） |
| [ManaitaMTK](https://modrinth.com/mod/manaitamtk) | 1.7.4 | コスメ枠でもダメージ 0 と「死亡しない」が成立する |
| [AdvancedAE](https://modrinth.com/mod/advanced-ae) | 1.3.6 | 量子ヘルメットの水中呼吸がコスメ枠で動く（アップグレードの導入と電力供給が前提） |
| [ProjectE](https://modrinth.com/mod/projecte) | 1.20.1 | ジェム防具の満腹度回復・ダメージ軽減・飛翔・Step Assist がコスメ枠で動く |
| [Mekanism](https://modrinth.com/mod/mekanism) | 10.4.16.80 | 実装備のメカスーツの効果（Step Assist・重力変調・静水学的反発・スキューバ）が動く。コスメ枠に夢幻終焉を着けても判定は消えない |
| fantasy_ending（EndingLib が必要） | 2.7.20 / 2.2.1fix | 4 点セットの効果（128 以下のダメージ無効化・シールド・死亡時の復帰・効果無効）が動く |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) + Sophisticated Core | 3.26.14.2226 / 1.5.15.2427 | コスメ枠（胸）のバックパックが B キーで開き、中身を出し入れでき、背中に描画される |
| [Apotheosis](https://modrinth.com/mod/apotheosis) + Placebo + Apothic Attributes | 7.4.8 / 8.6.3 / 1.3.7 | アフィックスの属性（最大体力・防御力・防具強度・呪文威力など）が加算され、ダメージ軽減系のアフィックスも効く |
| [Iron's Spellbooks](https://modrinth.com/mod/irons-spellbooks) | 3.16.3 | アップグレードオーブの属性が加算され、装備に注入した呪文もスペルホイールへの表示と発動まで動く |

バニラと同じ仕組みで属性と耐久を扱う防具なら、同様に動く見込みです。
装備の判定を独自に実装している MOD では、その MOD がどの経路で装備を読むかによって結果が変わります。

## 設定

設定ファイルは `config/notcosmeticarmor-server.toml`（サーバー側）です。

| キー | 既定 | 説明 |
|---|---|---|
| `general.enabled` | true | MOD 全体の有効・無効 |
| `behavior.mirrorAttributes` | true | コスメ防具の属性を実装備に加算する |
| `behavior.overrideQueries` | true | コスメ防具を「着ている防具」として返す（エンチャントや MOD の判定に反映する） |
| `behavior.interleave` | true | tick の内側でもコスメ装備を見せる |
| `behavior.tickCosmeticItems` | true | コスメ枠のアイテムを tick する |
| `behavior.cosmeticDurability` | true | 実装備が受けた耐久ダメージを、コスメ装備にも同じ量だけ入れる |
| `behavior.ignoreSkinArmorFlag` | true | CAR の skin armor フラグが立ったスロットも機能させる |
| `behavior.realPriorityNamespaces` | `["mekanism"]` | ここに挙げた名前空間の装備は、同じスロットに実装備があれば実装備を優先する |
| `compat.patchProjectE` | true | ProjectE の飛翔と Step Assist の判定にコスメ枠を足す |
| `compat.patchSophisticatedBackpacks` | true | Sophisticated Backpacks のバックパック探索にコスメ枠を足す |

`compat.*` の項目は、対応する MOD が入っていない場合は何もしません。

## コマンド

```
/notcosmeticarmor status   # 状態表示
/notcosmeticarmor cosmetic <feet|legs|chest|head> <item id|none|hand>   # コスメ枠に装備を入れる
/notcosmeticarmor probe    # 装備の取得結果をログに出す（デバッグ用）
```

権限レベル 2 以上が必要です。
`status` は CAR の検出状況、動作モード、設定値、各スロットの実装備とコスメ装備、防御力、耐久値を表示します。
`cosmetic` はワールドを出ずにコスメ枠の組み合わせを変えるためのコマンドです（例: `/notcosmeticarmor cosmetic chest fantasy_ending:fantasy_ending_chestplate`）。
アイテムを手に持った状態で `hand` を渡すと、NBT ごとコスメ枠にコピーします。アフィックスや注入した呪文、メカスーツのモジュールなど、振る舞いが NBT にある装備を試すときに使えます。

## ビルド

Nix を使う場合は、JDK 17 とクライアント実行用のライブラリが揃った環境でビルドできます。

```sh
nix develop
./gradlew build
# => build/libs/notcosmeticarmor-1.20.1-<version>.jar
```

Nix を使わない場合は JDK 17 を用意して `./gradlew build` を実行してください（Gradle は wrapper が取得します）。
開発用クライアントは `./gradlew runClient` です。CAR は `build.gradle` の依存に入っているので、そのまま読み込まれます。

## 謝辞

- [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)（zlainsama / original by dmillerw）: コスメ枠のインベントリと描画。
- Forge、Mixin、ForgeGradle の開発陣。

## ライセンス

MIT（[LICENSE](LICENSE)）。Cosmetic Armor Reworked 本体は MMPL 1.0.1 で、本 MOD は同 MOD を再配布せず、依存としてのみ参照します。

不具合報告や要望は [Issues](https://github.com/code-onigiri/not-cosmetic-armor/issues) へ。
