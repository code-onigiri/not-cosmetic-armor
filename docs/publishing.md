# 公開手順と掲載文

Modrinth / CurseForge / GitHub に出すための準備と、そのまま貼れる掲載文。

## 1. 公開前に用意するもの

| 項目 | 状態 |
|---|---|
| GitHub リポジトリ `code-onigiri/not-cosmetic-armor` | **公開済み**（<https://github.com/code-onigiri/not-cosmetic-armor>）。`mods.toml` の `issueTrackerURL` / `displayURL` もこの URL。`.github/workflows/build.yml` で push と PR ごとにビルドし、jar を artifact に上げる |
| Modrinth プロジェクト | 未作成 |
| CurseForge プロジェクト | 未作成。掲載文・入力欄ごとの値・チェックリストは [curseforge.md](curseforge.md) |
| アイコン | `src/main/resources/logo.png`（512x512、jar に同梱済み） |
| スクリーンショット | `docs/img/` の 4 枚（下の表） |
| ライセンス | MIT（[LICENSE](../LICENSE)） |

## 2. リリースビルド

```sh
nix develop --command ./gradlew clean build
sha256sum build/libs/notcosmeticarmor-1.20.1-1.0.0.jar
```

- jar 名は `notcosmeticarmor-<minecraft_version>-<mod_version>.jar`（`gradle.properties` の `mod_version`）。
- `jar` タスクが refmap の同梱を検証する。無ければビルドが失敗する（本番で全 mixin が落ちる事故を防ぐため）。
- `mods.toml` は `processResources` で展開され、`logo.png` も jar に入る。次のコマンドで確認できる:

```sh
unzip -p build/libs/notcosmeticarmor-1.20.1-1.0.0.jar META-INF/mods.toml | head -12
unzip -l build/libs/notcosmeticarmor-1.20.1-1.0.0.jar | grep -E 'logo.png|refmap'
```

**`mods.toml` の description は手動で折り返す。** ゲーム内の Mods 画面は説明文を折り返さず、
はみ出した部分を切り落とす。GUI スケール 2 で 1 行に収まるのは半角 44 文字（CJK は約 26 文字）
までなので、1 行 33 文字程度で改行しておく（現在の英文は最長 33 文字）。

タグを打って GitHub Releases に上げる:

```sh
V=1.6.1   # gradle.properties の mod_version と同じにする
git tag -a "v$V" -m "Not Cosmetic Armor $V"
git push origin main --follow-tags

gh release create "v$V" \
  "build/libs/notcosmeticarmor-1.20.1-$V.jar" \
  --title "Not Cosmetic Armor $V" \
  --notes-file "release-notes-$V.md"    # CHANGELOG の該当節を貼る
```

タグを push すると CI（`.github/workflows/build.yml`）が同じタグでビルドも走る。
リリース本文は CHANGELOG の節をそのまま貼るのが基本（利用者向けの要約 + 変更点）。

## 3. バージョンの付け方

- `mod_version` は SemVer。機能追加は minor、バグ修正は patch。
- jar 名に Minecraft のバージョンを含める（`1.20.1`）。MC を増やすときは `minecraft_version` と
  `minecraft_version_range` を見直す。
- 対応範囲は `minecraft_version_range=[1.20.1,1.20.2)`、`forge_version_range=[47,)`、
  CAR は `[1.20.1,1.20.2)`（CAR の版番号 `1.20.1-v1a` は Maven の比較で 1.20.1 より後、1.20.2 より前）。

## 4. Modrinth 掲載文

**Summary**（256 文字以内）:

```
Makes Cosmetic Armor Reworked's cosmetic slots actually work: armor attributes, enchantments, item ticks, durability mirroring and jetpack movement from the cosmetic slot.
```

**Description**（英語）:

```markdown
# Not Cosmetic Armor

Cosmetic Armor Reworked lets you wear armor for looks and armor for stats. This addon makes the
cosmetic set **actually function**, so what you see is what you get.

## What works from a cosmetic slot

- **Attributes** — armor, toughness, knockback resistance and any other attribute modifier the item
  declares are added on top of your real armor. Modifier UUIDs are regenerated per player and slot,
  so they never collide with the real armor.
- **Enchantments** — Protection, Feather Falling, Thorns, Respiration and friends apply, and they
  stack with the enchantments on the real armor (vanilla caps total EPF at 20). The query override
  covers both sides, so mods that check for worn armor on the client (water breathing, flight) see
  the cosmetic piece too.
- **Item ticks** — `inventoryTick` / Forge's `onArmorTick` run for cosmetic items, so modded armor
  that does its work on tick (energy storage, jetpacks) behaves normally.
- **Durability** — damage taken by the real armor is mirrored onto the cosmetic piece.
- **Movement** — if a cosmetic item changes the player's velocity, that change is sent back to the
  owner, so jetpacks in a cosmetic slot actually lift you.

The real armor keeps working as real armor: this mod never removes it and never rewrites the armor
inventory outside a tick. Rendering is Cosmetic Armor Reworked's job — this mod has no renderer of
its own, and steps out of the way while CAR swaps in the cosmetic set.

## Requirements

- Minecraft 1.20.1, Forge 47.x, Java 17
- [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) (required, both sides)

## Configuration

`config/notcosmeticarmor-server.toml` — everything is on by default:

| Key | Default | Meaning |
|---|---|---|
| `general.enabled` | true | master switch |
| `behavior.mirrorAttributes` | true | add cosmetic attribute modifiers |
| `behavior.overrideQueries` | true | `getItemBySlot` / `getArmorSlots` return the cosmetic item |
| `behavior.interleave` | true | copy the cosmetic piece into the armor slot for odd ticks |
| `behavior.tickCosmeticItems` | true | tick cosmetic items |
| `behavior.cosmeticDurability` | true | mirror durability damage |
| `behavior.ignoreSkinArmorFlag` | true | use slots flagged as "skin armor" by CAR |

## Commands

```
/notcosmeticarmor status    # what the mod sees and applies (permission level 2)
/notcosmeticarmor probe     # log what getItemBySlot returns inside/outside the tick window
```

## Known limits

- Enchantment damage reduction is capped by vanilla (EPF 20 = 80%).
- Jetpack thrust is one stack's worth; a real and a cosmetic jetpack do not stack (the real one wins).
- Anything that reads `getItemBySlot` / `getArmorSlots` sees the cosmetic item, including cheat-style
  mods. Do not put this on a server where that matters.
- Mods that read `Inventory#armor` directly (ProjectE's gem armor flight, for example) do not see a
  cosmetic piece except inside the tick window.

Licensed MIT. Sources and issues on GitHub.
```

**Description**（日本語）:

```markdown
# Not Cosmetic Armor

Cosmetic Armor Reworked のコスメ装備を、見た目だけでなく**実際に機能**させます。

## コスメ枠でできること

- **属性** — 防御力・防具強度・ノックバック耐性など、アイテムが宣言する attribute modifier を実装備に加算。UUID はプレイヤーとスロットごとに作り直すので実装備と衝突しません。
- **エンチャント** — Protection / Feather Falling / Thorns などが有効。実装備のエンチャントとも合算されます（バニラの EPF 上限 20 まで）。
- **item tick** — コスメ枠のアイテムにも `inventoryTick` / Forge の `onArmorTick` が走ります。エネルギーやチャージを tick で処理する MOD 防具がそのまま動きます。
- **耐久** — 実装備が受けた耐久ダメージがコスメ装備にも入ります。
- **移動** — コスメ装備が速度を変えた tick は、その変化が本人に同期されます（jetpack がコスメ枠で飛びます）。

実装備は実装備のまま動き続けます。tick の外でインベントリを書き換えることはなく、描画にも触りません（見た目は CAR が担当）。

## 必要なもの

- Minecraft 1.20.1 / Forge 47.x / Java 17
- [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)（必須・クライアントとサーバーの両方）

## 設定とコマンド

`config/notcosmeticarmor-server.toml` の 7 項目（すべて既定で有効）と、
`/notcosmeticarmor status` / `/notcosmeticarmor probe` の 2 コマンド。

## 制限

- ダメージ軽減はバニラの上限（EPF 20 = 最大 80%）で頭打ち。
- jetpack の推力は 1 個ぶん。実装備とコスメに 1 個ずつ入れても加算されません（実装備側が優先）。
- `getItemBySlot` / `getArmorSlots` を読むものにはコスメ装備が見えるため、チート系 MOD の能力もコスメ枠で有効になります。

MIT ライセンス。
```

## 5. CurseForge 掲載文

**入力欄ごとの値をまとめた掲載キットは [curseforge.md](curseforge.md)。** プロジェクト名・Summary・
カテゴリ・Environment・Relations・Description（英語／日本語）・File メタデータ・Changelog・
スクリーンショットのキャプション・公開前チェックリストを、そのまま貼れる形で置いてある。

要点だけ:

- Summary は Modrinth と同じでよい（英語 256 文字以内）。
- Description は CurseForge も Markdown が使えるので、[curseforge.md](curseforge.md) §2 の英語本文を貼る。
- File 名は `notcosmeticarmor-1.20.1-<version>.jar`、Game version `1.20.1`、Mod loader `Forge`、Release type `Release`。
- Environment は **Client and Server**（両方必須）。
- Relations で **Cosmetic Armor Reworked を Required dependency** に設定する。

カテゴリ・タグ（両サイト共通）: `Armor` / `Cosmetic` / `Utility` / `Forge`。

## 6. スクリーンショット

| ファイル | 説明文（そのまま使える） |
|---|---|
| `docs/img/status-in-game.png` | `/notcosmeticarmor status` — armor 13 = real 8 + cosmetic 5, the tick window stays closed |
| `docs/img/third-person.png` | Third person: the cosmetic chestplate is what you see, the real one still provides stats |
| `docs/img/durability-mirror.png` | Durability damage mirrored onto the cosmetic piece |
| `docs/img/presentation-layers.png` | Inventory shows the real armor, the model shows the cosmetic one — the layers stay separate |

## 7. 公開後の更新フロー

1. `gradle.properties` の `mod_version` を上げる。
2. `CHANGELOG.md` に追記する。
3. `README.md` の検証ログ・互換性表を更新する（新しい MOD で試したとき）。
4. `./gradlew clean build` → コミット & push → `git tag v<version>` → `gh release create` で jar を添付（上のコマンド例）。
5. Modrinth / CurseForge には同じ jar を手動でアップロードする。

Modrinth への自動アップロードをしたくなったら、`minotaur` Gradle プラグイン
（`com.modrinth.minotaur`）を入れて `MODRINTH_TOKEN` を環境変数で渡す形にできる。
今は手動アップロード前提で、トークンや CI の設定は置いていない。

## 8. ライセンスと再配布

- 本 MOD は MIT。`jarJar` は使っておらず、CAR の jar を同梱していない（CAR は MMPL 1.0.1）。
- 掲載文に CAR へのリンクを置き、依存として参照する形にする。
