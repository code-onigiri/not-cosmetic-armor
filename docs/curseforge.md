# CurseForge 掲載キット

CurseForge のプロジェクト作成画面・ファイルアップロード画面に**そのまま貼る**ための原稿です。
入力欄の順に並べてあるので、上から埋めていけば完了します。

## 1. プロジェクト作成

| 入力欄 | 入れる値 |
|---|---|
| Name | Not Cosmetic Armor |
| Summary | 下の「Summary（英語）」を貼る |
| Project avatar | `src/main/resources/logo.png`（512x512）から **400x400** を書き出して使う（CurseForge の推奨サイズ） |
| Categories | Armor, Tools, and Weapons / Cosmetic / Utility & QoL |
| Game version | 1.20.1（1.20.2 以上は選ばない。`minecraft_version_range` が `[1.20.1,1.20.2)`） |
| Mod loader | Forge |
| Environment | Client and Server（**両方必須**。装備の判定はサーバー側で行う） |
| License | MIT |
| Links → Source | https://github.com/code-onigiri/not-cosmetic-armor |
| Links → Issues | https://github.com/code-onigiri/not-cosmetic-armor/issues |
| Relations | **Required dependency**: Cosmetic Armor Reworked（https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked） |
| Description | `2` の英語本文を貼る（日本語を併記したいなら `3` を末尾に足す） |

カテゴリは候補が変わることがあるので、選択画面に出ている中から近いものを選んでください。

**Summary（英語・256 文字以内）**

```
Not Cosmetic Armor makes Cosmetic Armor Reworked's cosmetic slots actually work on 1.20.1 Forge: armor attributes, enchantments, item ticks, durability mirroring and mod equip checks all see the cosmetic piece.
```

**Summary（日本語で出す場合）**

```
Cosmetic Armor Reworked のコスメ枠を、見た目だけでなく実際に着ている防具として扱います。属性・エンチャント・item tick・耐久・MOD の装着判定がコスメ枠に追従します（1.20.1 Forge）。
```

## 2. Description（英語・そのまま貼る）

CurseForge の説明欄は Markdown が使えます。``` のブロックごとコピーしてください。

```markdown
# Not Cosmetic Armor

Cosmetic Armor Reworked gives you two sets of armor: one you wear for looks, one for stats.
This addon closes the gap. **The cosmetic set actually behaves like worn armor** — what you see is what you get.

## What works from a cosmetic slot

- **Attributes** — armor, toughness, knockback resistance, movement speed and anything else the item declares
  are added on top of your real armor. Modifier UUIDs are regenerated per player and per slot, so they never
  collide with the real set.
- **Enchantments** — Protection, Feather Falling, Thorns, Respiration and the rest apply, and they stack with
  the real armor's enchantments (vanilla still caps total EPF at 20). Both sides are covered, so client-side
  checks such as water breathing and flight see the cosmetic piece too.
- **Item ticks** — `inventoryTick` and Forge's `onArmorTick` run for cosmetic items, so modded armor that does
  its work on tick (energy storage, jetpacks, water breathing upgrades) behaves normally.
- **Durability** — damage taken by the real armor is mirrored onto the cosmetic piece, Unbreaking rolls included.
- **Movement** — if a cosmetic item changes your velocity, the change is sent back to you, so a jetpack in a
  cosmetic slot actually lifts you.
- **Mod equip checks** — mods that ask `getItemBySlot` / `getArmorSlots` see the cosmetic item. Set bonuses,
  flight permissions, damage-reduction events and spell containers all follow.

The real armor keeps working as real armor: this mod never removes it, never rewrites the armor inventory
outside a tick, and has no renderer of its own. Cosmetic Armor Reworked still handles the looks.

![Cosmetics on top of the real armor](https://raw.githubusercontent.com/code-onigiri/not-cosmetic-armor/main/docs/img/third-person.png)

## Requirements

- Minecraft 1.20.1, Forge 47.x, Java 17
- [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) — required,
  on the client **and** the server

## Install

1. Put Cosmetic Armor Reworked (1.20.1) in `mods/`.
2. Put `notcosmeticarmor-1.20.1-<version>.jar` in `mods/` on both the client and the server.
3. Run `/notcosmeticarmor status` (permission level 2). `car: detected` and `mode=INTERLEAVE` mean it is live.

## Tested with

| Mod | What works from the cosmetic slot |
|---|---|
| [Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) + Mantle | attributes and durability |
| [Iron Jetpacks](https://modrinth.com/mod/iron-jetpacks) + Cucumber | flight |
| [ManaitaMTK](https://modrinth.com/mod/manaitamtk) | zero damage, no death |
| [Advanced AE](https://modrinth.com/mod/advanced-ae) | quantum helmet water breathing |
| [Project E](https://modrinth.com/mod/projecte) | gem armor flight, step assist, saturation |
| [Mekanism](https://modrinth.com/mod/mekanism) | MekaSuit effects stay active with cosmetics worn on top |
| fantasy_ending + EndingLib | 4-piece set bonus, shield, damage nullification, revive |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | a backpack in the cosmetic chest opens with B and renders on your back |
| [Apotheosis](https://modrinth.com/mod/apotheosis) | affix attributes and damage-reduction affixes |
| [Iron's Spellbooks](https://modrinth.com/mod/irons-spellbooks) | upgrade orb attributes and inscribed spells |

Armor that uses the vanilla attribute and durability path should work the same way.

## Configuration

`config/notcosmeticarmor-server.toml`. Everything is on by default.

| Key | Meaning |
|---|---|
| `general.enabled` | master switch |
| `behavior.mirrorAttributes` | add the cosmetic item's attribute modifiers |
| `behavior.overrideQueries` | report the cosmetic item as worn armor |
| `behavior.interleave` | keep the cosmetic item visible inside the tick window |
| `behavior.tickCosmeticItems` | tick cosmetic items |
| `behavior.cosmeticDurability` | mirror durability damage |
| `behavior.ignoreSkinArmorFlag` | also use slots CAR flags as "skin armor" |
| `behavior.realPriorityNamespaces` | namespaces whose real armor wins when both slots are filled (`["mekanism"]` by default) |
| `compat.patchProjectE` | let Project E's flight and step-assist checks see cosmetics |
| `compat.patchSophisticatedBackpacks` | let the backpack search see a cosmetic chest |

## Commands

```
/notcosmeticarmor status
/notcosmeticarmor cosmetic <feet|legs|chest|head> <item id|none|hand>
/notcosmeticarmor probe
```

`status` prints the CAR state, the mode, every slot's real and cosmetic item, modifier counts, armor points and
durability. `cosmetic` swaps a cosmetic slot without leaving the world, and `hand` copies the held stack with its NBT.

## Known limits

- Enchantment damage reduction is capped by vanilla (EPF 20 = 80%).
- Jetpack thrust is one stack's worth: a real and a cosmetic jetpack do not stack, the real one wins.
- Mods that read the raw `Inventory#armor` list do not see cosmetics. Project E and Sophisticated Backpacks have
  bundled patches; more can be added through the config.
- Slots listed in `behavior.realPriorityNamespaces` show the real armor to mods when both slots are filled, which is
  what keeps the MekaSuit's abilities alive under a cosmetic set.
- Anything that reads `getItemBySlot` / `getArmorSlots` sees the cosmetic item, including cheat-style mods. Do not
  put this on a server where that matters.
- Cosmetic armor does **not** provoke piglins, because vanilla decides that by checking worn armor.

## Links

- Source and issue tracker: <https://github.com/code-onigiri/not-cosmetic-armor>
- Changelog: <https://github.com/code-onigiri/not-cosmetic-armor/blob/main/CHANGELOG.md>

Licensed under MIT.
```

> 貼ったあと、プレビューで見出し・表・画像が崩れていないかだけ確認してください。

## 3. Description に足す日本語（任意）

日本語話者向けに短い節を末尾へ。英語本文のあとに空行を 1 行入れて貼ります。

```markdown
## 日本語

[Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)（1.20.1）のアドオンです。
コスメ枠に入れた防具を、見た目だけでなく実際に着ている防具として扱います。属性・エンチャント・item tick・耐久ミラー・
MOD 側の装着判定がすべてコスメ枠に追従します。CAR はクライアントとサーバーの両方に入れてください。

`/notcosmeticarmor status` で動作状況（検出・モード・各スロットの実装備とコスメ・防御力・耐久）を確認できます。
既知の制限と詳しい説明は [README](https://github.com/code-onigiri/not-cosmetic-armor#readme) にあります。
```

## 4. ファイルアップロード（1.6.1）

| 入力欄 | 入れる値 |
|---|---|
| File | `build/libs/notcosmeticarmor-1.20.1-1.6.1.jar`（84603 B、sha256 `493b619ca5a1a9ebffe221e5ecff1304e719f9bd4e947e0f9151998227edbfca`） |
| Display name | Not Cosmetic Armor 1.6.1 |
| Release type | Release |
| Game version | 1.20.1 |
| Mod loader | Forge |
| Changelog | 下の英語版を貼る（併記するなら日本語版も） |
| Additional files | なし（`-sources` や `-api` は付けない） |

GitHub の v1.6.1 リリースと同じ jar を上げてください（同一 sha256）。

**Changelog（英語）**

```
No behaviour changes — verification and documentation only.

- Verified that Apotheosis damage-reduction affixes (blockading, feathery) reduce damage from a cosmetic slot
  exactly like from the real slot (mob_attack 100: 64.66 -> 50.0, fall 40: 30.0 -> 18.0 with feathery).
- Verified that Iron's Spellbooks spells inscribed into armor appear on the spell wheel and cast from a
  cosmetic slot (Heal I: HP 140 -> 145).
- README compatibility table updated; internal notes added to docs/plan-review.md.
```

**Changelog（日本語）**

```
挙動の変更はありません（検証とドキュメントのみ）。

- Apotheosis のダメージ軽減系アフィックス（blockading / feathery）がコスメ枠からも実装備と同じだけ効くことを実機で確認。
- Iron's Spellbooks で装備に注入した呪文が、コスメ枠からスペルホイールに出て発動することを実機で確認。
- README の互換表を更新。
```

## 5. スクリーンショット

`docs/img/` の 8 枚を、この順でアップロードします（すべて 1280x720）。
CurseForge はタイトルと説明を付けられるので、英語版をタイトルに、日本語を説明に入れると両方に届きます。

| ファイル | Title（英語） | Description（日本語） |
|---|---|---|
| `third-person.png` | Cosmetic chestplate in third person | 見えているのはコスメ枠の金の胸当て。数値は実装備のダイヤが供給しています |
| `status-in-game.png` | `status` output | スロットごとの実装備・コスメ・modifier 数・防御力を 1 画面で確認できます |
| `durability-mirror.png` | Durability mirrored | 実装備が受けた耐久ダメージが、コスメ装備にも同じだけ入ります |
| `mekanism-fantasy.png` | MekaSuit + fantasy_ending cosmetics | 実装備のメカスーツの能力を保ったまま、コスメ枠の 4 点セットも成立します |
| `presentation-layers.png` | Two layers | インベントリは実装備、モデルはコスメ。層は混ざりません |
| `sb-cosmetic-gui.png` | Backpack in a cosmetic slot | コスメ枠（胸）のバックパックも B キーで普通に開きます |
| `sb-real-wins.png` | Real backpack wins | 実装備にもバックパックがあるときは実装備が優先されます |
| `isb-spell-wheel.png` | Inscribed spells | コスメ枠の防具に注入した呪文もスペルホイールに出て発動します |

## 6. モデレーション向けの申告メモ

審査で聞かれたときのための材料です。

- ライセンスは MIT（`LICENSE`）。コードはすべて自作です。
- Cosmetic Armor Reworked のコード・アセットは**再配布していません**（`jarJar` 未使用、jar に CAR のクラスは入っていません）。
  CAR へは依存としてのみ参照し、Relations に Required で登録します。
- テレメトリや外部通信はありません。通信は Minecraft / Forge の通常の範囲だけです。
- クライアントとサーバーの両方に必要です。CAR が無い場合、Forge の依存解決で起動が止まります（実行時に落ちることはありません）。

## 7. 公開前チェックリスト

- [ ] `gradle.properties` の `mod_version` と jar 名が一致している（`1.6.1`）
- [ ] jar が GitHub Releases の v1.6.1 と同一（sha256 `493b619c…`）
- [ ] Summary を 256 文字以内で貼った
- [ ] Description を貼り、プレビューで見出し・表・画像が出ていることを確認した
- [ ] Game version = 1.20.1、Mod loader = Forge、Environment = Client and Server
- [ ] Relations に Cosmetic Armor Reworked（Required）を入れた
- [ ] スクリーンショットを 8 枚上げた（最低でも 1 枚、できれば 4 枚以上）
- [ ] File の Game version = 1.20.1、Release type = Release、Changelog を貼った
- [ ] アップロード後、`docs/publishing.md` の「CurseForge プロジェクト」を「公開済み」に更新した

## 8. 公開後にやること

1. CurseForge のプロジェクト URL を `README.md` の冒頭付近（英語ブロックの下）に足す。
2. `docs/publishing.md` `1` の表と `5` を「公開済み」に更新する。
3. Modrinth にも同じ jar・同じ本文を出す場合は `docs/publishing.md` `4` の原稿を使う（Summary は共通）。
4. 更新時は `mod_version` を上げ、GitHub Releases → CurseForge の順に同じ jar を上げる。
