# Changelog

## 1.6.1 — 2026-10-26

挙動の変更はありません（検証とドキュメントのみ）。属性にならない Apotheosis のアフィックスと、
Iron's Spellbooks の注入呪文の**発動**を実機で確認し、どちらも追加パッチ無しで動くことを確定しました。

### 検証済み（実機、1.6.1）

| 構成 | 結果 |
|---|---|
| Apotheosis `armor/dmg_reduction/blockading`（ancient 1.0）をコスメ胸 | `mob_attack 100` が 64.66 → **50.0**（-22.7%）、`fall 40` が 30.0 → **24.0** |
| Apotheosis `armor/dmg_reduction/feathery`（ancient 1.0）をコスメ足 | `fall 40` が 30.0 → **18.0**（-40%）。同じブーツを実装備に入れても **18.0** で同一 |
| ISB `spell_container`（heal level 1）をコスメ胸 | ホイール（R）に Heal が出る。V キーで HP 140 → **145**、マナ 100 → **70** |
| 同・実装備=MekaSuit（`query=real`） | ホイールに出ず、V でも発動しない（`getItemBySlot` が実装備を返すため。意図どおり） |
| 同・実装備=ダイヤ胸当て（`query=cosmetic`） | 発動する（HP 140 → 145） |

経路: 挙動系アフィックスは `AdventureEvents#onDamage` が `entity.getAllSlots()`（手 + `getArmorSlots`）を回して適用、注入呪文は `SpellSelectionManager` が `player.getItemBySlot(...)` で装備を読むため、どちらも本 MOD の上書き経路に乗ります。

### ドキュメント

- README の互換表（Apotheosis / Iron's Spellbooks）を「実機検証済み」に更新し、検証ログの節を追加。
- `docs/plan-review.md` に §22 を追加（検証手順、二重適用の罠、実装備優先との相互作用）。
- AGENTS.md のやらかし集に、メインハンドに残したまま測る二重適用と `/data modify ... Health set value` の落とし穴を追加。

## 1.6.0 — 2026-10-26

### 修正

- **コスメ枠のバックパックが開かなかった**（Sophisticated Backpacks）。
  `PlayerInventoryProvider` の `"armor"` ハンドラが `player.getInventory().armor.get(EquipmentSlot.CHEST.getIndex())`
  を直読みするため、コスメ胸のバックパックは探索で見つからず、B キーが無反応でした。
  開くキー・背中の描画・メニュー生成の 3 経路が集まる `PlayerInventoryHandler#getStackInSlot` に注入し、
  armor ハンドラの戻り値がバックパックでないときだけコスメ胸を返すようにしました。
  実装備のバックパックがあるときは従来どおり実装備が優先です（`compat.patchSophisticatedBackpacks=false` で無効化）。

### 追加

- `/notcosmeticarmor cosmetic <slot> hand`: メインハンドのスタックを **NBT ごと**コピーします。
  アフィックス・注入呪文・アップグレードオーブ・メカスーツのモジュールは NBT に振る舞いがあるため、
  アイテム id だけの指定では落ちます。
- `/notcosmeticarmor status` のスロット行に `fromItem=`（アイテム側の属性数）と `nbt=`（NBT キー）を追加。
  属性が出ないときに「アイテム側に無い」のか「ミラーに失敗している」のかを切り分けられます。
- `/notcosmeticarmor status` に `sophisticatedBackpacks: patch=.. calls=.. hits=..` を追加。
- 設定 `compat.patchSophisticatedBackpacks`（既定 `true`）。

### 検証済み（実機、1.6.0）

| 構成 | 結果 |
|---|---|
| コスメ胸 = 革のバックパック | B キーで「革のリュック」が開く。shift+クリックで収納でき、閉じて再度 B でも中身が残る。三人称で背中に描画。`calls=1273 hits=1219` |
| 実装備胸 = ダイヤのリュック / コスメ胸 = 革のバックパック | B キーで「ダイヤモンドのリュック」が開く（実装備優先）。`calls=1358→1369` に対し `hits` は 1233 のまま |
| Apotheosis `mythic` アフィックスのダイヤ胸当て | `modifiers=6 fromItem=6`。コスメ無しと比べて 防御力 70.0 → 78.0、防具強度 42.0 → 44.0、最大体力 237.80 → 248.05、`irons_spellbooks:spell_power` 1.60 → 1.70 |
| Iron's Spellbooks の `fire_power` 2 個入りダイヤ胸当て | `irons_spellbooks:fire_spell_power` 1.0 → 1.1（0.05 × 2 MULTIPLY_BASE）、`modifiers=3 fromItem=3` |

アフィックスとオーブの属性は `ItemStack#getAttributeModifiers` → `ForgeHooks.getAttributeModifiers` →
`ItemAttributeModifierEvent` の経路で供給されるため、コスメ枠でも属性ミラーにそのまま乗ります。
Sophisticated Backpacks 側の前提: パッチは `PlayerInventoryHandler` の内部に `@Pseudo` で入り、
対象クラスが無ければ mixin config 自体を読みません。
## 1.5.0 — 2026-10-25

### 修正

- **実装備のメカスーツの効果がコスメ装備に隠れていた**（Mekanism）。
  実装備にメカスーツ、コスメ枠に別の防具（例: 夢幻終焉）を入れると、Mekanism の Step Assist・
  重力変調・静水学的反発ユニット・スキューバが止まり、夢幻終焉のセット効果だけが残っていました。
  Mekanism は装着を `Player#getItemBySlot` で判定するため、コスメ優先の上書きでは
  メカスーツが見えないのが原因です（`CommonPlayerTickHandler#getStepBoost`、`#getSwimBoost`、`#isGravitationalModulationReady`、`PlayerState#updateFlightInfo`）。
  対策として **名前空間単位の「実装備優先」**（`RealPriority`）を入れました。同じスロットに
  その MOD の実装備があれば、クエリは実装備を返します。既定の対象は `mekanism` のみで、
`behavior.realPriorityNamespaces` で増減できます。tick 窓でも対象スロットは実装備を
  退避しないので、半 tick おきに能力が消えることはありません。
- **セット判定のユニオンが新しい層を見落としていた**。`ArmorSetUnion` は「見える層・
  実体・退避中の実装備」の 3 層だけを見ていたため、実装備優先を入れると 4 スロットすべてで
  見える層がメカスーツになり、コスメの夢幻終焉がどの層にも現れなくなりました
  （`set=MekaSuitMaterial is4=false`、シールド HUD 消失）。コスメ枠そのものを第 4 層として
  見るようにしました。

### 追加

- `/notcosmeticarmor status` のスロット行に `query=real|cosmetic` を追加
  （そのスロットの判定が実装備とコスメのどちらを見ているか）。
- 設定 `behavior.realPriorityNamespaces`（既定 `["mekanism"]`）。

### 検証済み（実機、Mekanism 10.4.16.80 + fantasy_ending 2.7.20）

| 構成 | 結果 |
|---|---|
| 実装備=メカスーツ 4 点（足: 油圧推進 1、脚: 静水学的反発 4、胸: 重力変調 1 + 電力）/ コスメ=夢幻終焉 4 点 | `stepHeight=1.1`（0.6 + 0.5）、`mayfly=1b` でダブルタップ飛行（Y 201.6 → 216.6 / 2 秒）、水中で `swim_speed=2.0`（無しは 1.0）、`fantasyEnding: is4=true shield[level=4936]` とシールド HUD が同時に成立、4 スロットとも `query=real` |
| 実装備=空 / コスメ=夢幻終焉 4 点 | `query=cosmetic`、`is4=true`、`mayfly=1b`、飛行 Y 230.7 → 245.7 / 2 秒（回帰なし） |
| 実装備=ダイヤ胸当て / コスメ=夢幻終焉 | そのスロットは `query=cosmetic`（従来どおりコスメ優先） |

メカスーツ側の前提: 静水学的反発ユニットは 4 個積み + `swim_boost` 有効 + 水中 + 電力が必要
（Mekanism の仕様）。重力変調は 1000 J/tick、静水学的反発は 500 J/tick を消費します。
## 1.4.0 — 2026-10-25

### 修正

- **コスメ装備が実装備の「セット判定」を隠していた**（EndingLib 経由の MOD、実例は fantasy_ending）。
  夢幻終焉 4 点を実装備していても、コスメ枠に別の防具を 4 点入れると 4 セットが成立しなくなり、
  `/kill` で死亡し、毒や弱体化も通るようになっていました。
  EndingLib の `ArmorUtils#getArmorSet` は `getItemBySlot` を 4 回読むだけで判定するため、
  コスメがあると実装備が見えません。判定を「実装備・コスメのどちらかが一致するスロットが 4 つ以上」
  に広げるパッチ（`ArmorSetUnion`）を同梱しました。属性ミラーと同じ「両方足す」方針です。
  あわせて tick 窓の中では実装備が `CosmeticState#windowReal` に退避されるため、その層も見ます
  （これが無いとシールドのレベルが半 tick おきに 0 へリセットされていました）。
- **コスメ装備の item tick がサーバーでしか走っていなかった**。
  バニラは `Inventory#tick` を両側で回すので実装備の `onArmorTick` はクライアントでも動きますが、
  コスメ枠の tick はサーバー専用でした。そのため夢幻終焉チェストプレートの
  `abilities.mayfly = true` がサーバーにしか立たず、ダブルタップしても飛べませんでした
  （HUD だけ出る、という症状）。クライアント側でも同じ tick を回すようにしました。

### 追加

- `/notcosmeticarmor cosmetic <feet|legs|chest|head> <item id|none>`: コスメ枠にアイテムを直接入れます
  （権限レベル 2）。ワールドを出ずに組み合わせを変えて検証するためのコマンドです。
- 対象 MOD が入っていない環境では、追加した mixin config は読み込まれません
  （`EndingLibMixinPlugin` が `ArmorUtils` の有無を確認）。

### 検証済み（実機、`/tmp/nca-test` = 実インスタンスのコピー + EndingLib）

| ケース | 構成 | 結果 |
|---|---|---|
| A | 実装備=夢幻終焉 4 点 / コスメ=ダイヤ 4 点 | `fantasyEnding: set=FANTASY_ENDING_MATERIAL is4=true shield[level=4936 dr=128.0]`、`/damage @p 50 minecraft:mob_attack` は無効、`/damage @p 200` は通る、`/effect give @p minecraft:poison 10 2` は拒否、`/kill @p` で死亡しない、シールド HUD のバーが表示、ダブルタップで飛行（Y 173.4 → 188.7 / 2 秒） |
| A（1.3.0 まで） | 同上 | `set=DIAMOND is4=false shield[level=0]`、`/kill @p` で死亡、HUD のバー無し |
| B | 実装備=空 / コスメ=夢幻終焉 4 点 | `is4=true shield[level=4936]`、`abilities.mayfly=1b`、Y 141.0 → 153.2（2 秒）、`/kill @p` で死亡しない |

### 仕様として明記

- 4 セット判定は**実装備とコスメ枠の合算**です。たとえば実装備に 2 点、コスメに 2 点でも 4 セットに
  なります（属性ミラーが両方を足すのと同じ考え方）。実装備だけで組んだ従来の判定は変わりません。

## 1.3.0 — 2026-10-25

### 変更

- **`/notcosmeticarmor status` に `fantasyEnding:` 行を追加**: fantasy_ending（+ EndingLib）が入っているとき、
  EndingLib の 4 セット判定（`ArmorUtils#getArmorSet`）、fantasy_ending の `FantasyEndingArmorItem#is4Armor`、
  同期済みのシールドケイパビリティ（`level` / `damageResistance`）を 1 行で出します。クラスはすべて
  リフレクションで引くので、入っていない環境では `fantasyEnding: absent` になるだけで何も起きません。

### 検証済み（追加）

fantasy_ending 2.7.20 + EndingLibraryTweaked 2.2.1fix の環境で、夢幻終焉の 4 点を**コスメ枠だけ**に
入れた状態（実装備は空）で確認しました。

| 見たもの | 結果 |
|---|---|
| `/notcosmeticarmor status` | `armor=70` / `fantasyEnding: set=FANTASY_ENDING_MATERIAL is4=true shield[level=4936 dr=128.0]` |
| `/damage @p 50 minecraft:mob_attack` | 無効（HP 547.5 → 547.5） |
| `/damage @p 150 minecraft:mob_attack` | 通る（HP 547.5 → 545.4） |
| ヘルメットを 1 枚抜いて 3 点にした対照 | `is4=false` / `shield[level=0]` / `armor=53`、同じ 50 ダメージが 9.8 通る |
| クライアント側の 4 セット判定 | true（シールド HUD のバーが表示され、残り時間 `1024s` も動く） |
| `/effect give @p minecraft:poison 10 2` | 付与されない（4 セットのバフ無効化が効いている） |

**サーバー側の防護は 1.2.0 でも 0.1.0 でも成立していました。** 1.3.0 の変更はその状態を
その場で読めるようにする診断だけで、防護そのものの修正はありません。

### 既知の制限（更新）

- fantasy_ending の防護は仕様上 **128 以下のダメージしか無効化しません**
  （`FantasyEndingArmorItem#damage_reduction = 128.0F`）。128 を超えるダメージは
  `IFeShieldCapability#dealDamage` で「128 を引いてから `outResistance` 0.25 を掛ける」扱いになります。
- 4 セット判定は **同じ material の 4 点**が前提です。1 枚でも別素材（夢影・星屑など）だと発動しません。
- 0.1.0 の jar はクライアント側でコスメ装備を返さないため、**シールド HUD のバーが出ません**
  （防護自体はサーバー側で成立します）。1.1.0 以降で解消しています。

## 1.2.0 — 2026-10-25

### 変更

- **ProjectE の互換パッチを追加（任意・既定で有効）**: ProjectE のジェム防具は
  `PlayerHelper#checkArmorHotbarCurios` が `Inventory#armor`（実装備リスト）を直接読むため、
  コスメ枠では飛翔と Step Assist が効きませんでした。このメソッドの戻り値を、実装備で
  一致しなかったときだけ `Player#getItemBySlot` 経由でコスメ防具にも問い合わせるよう広げました。
  ProjectE が入っていない環境では mixin config 自体が適用されない（`IMixinConfigPlugin` で判定）ので、
  他 MOD への影響はありません。
- **ProjectE の飛翔フラグがゲームモード変更後に固まる問題を修正**: `InternalAbilities#tick` は
  `hadFlightItem` をキャッシュしているため、クリエイティブ（飛翔し放題）からサバイバルへ戻ると
  「飛翔アイテムを持っている」という古い状態のまま再評価されず、`abilities.mayfly` が
  `false` に固定されることがありました。ゲームモードが変わった tick でキャッシュを捨て、
  `Player#onUpdateAbilities` で能力パケットを送り直します。
- **設定に `compat.patchProjectE`（既定 true）を追加**: パッチが原因と疑われるときに
  切り分けられるようにしました。`/notcosmeticarmor status` に `projectE: patch=... calls=... hits=...`
  と `stepHeight=...` を追加し、パッチが呼ばれているか・一致しているかをその場で確認できます。

### 検証済み（追加）

- ProjectE 1.20.1 のジェムブーツをコスメ枠に入れて `abilities.mayfly=1b`、
  ジャンプ 2 回で飛翔開始 → 2 秒のホールドで Y が上昇すること。
- 同ジェムブーツで Step Assist（`forge:step_height_addition` = 1.0 = 基準 0.6 + 0.4）。
- クリエイティブ → サバイバルの往復後も `mayfly` が維持されること。

### 既知の制限（更新）

- ProjectE のジェム防具の飛翔 / Step Assist は **1.2.0 でコスメ枠でも動きます**（上記）。
  ジェムの攻撃能力（攻撃時の爆発・ヘルメットの電撃）は `GemArmorBase#hasAnyPiece` にも
  同じ形で注入していますが、**実機での確認はまだ**です。

## 1.1.0 — 2026-10-25

### 変更

- **クライアント側でもコスメ装備を返すようにした**: `Player#getItemBySlot` / `Player#getArmorSlots` の
  上書きはサーバー専用でしたが、クライアントでも効くようになりました。CAR の
  `CosArmorAPI.getCAStacksClient` からコスメを読みます。クライアント側で装備判定をする MOD
  （水中呼吸・飛翔など）や、クライアントで完結する表示にコスメ装備が届きます。
- **描画中の二重適用を防止**: CAR は `RenderPlayerEvent.Pre`(HIGH) で `Inventory#armor` を
  コスメに入れ替え、`Post`(LOW) で戻します。本 MOD は `Pre`/`Post`(LOWEST) の間だけ
  クエリ上書きを止める `QueryGuard` / `ClientRenderGuard` を追加しました。三人称のモデルに
  コスメ防具が 1 回だけ描画され、HUD やインベントリの実装備表示は変わらないことを実機で確認しています。

### 検証済み（追加）

- コスメ枠の Respiration III（金ヘルメット）で水中の泡が減らないこと（13 秒で泡 7 個 = 期待どおり）。
- AdvancedAE 1.3.6 の量子ヘルメット（水中呼吸アップグレード + 電力）をコスメ枠に入れて、
  21 秒間水中で泡が減らないこと。
- 三人称の描画でコスメ防具が二重に描かれないこと。

### 既知の制限（追加）

- ProjectE のジェムアーマーの飛翔 / Step Assist はコスメ枠では効かない（MOD 側が
  `Inventory#armor` を直接読むため）。

## 1.0.0 — 2026-10-25

初回公開リリース。

### 機能

- **属性ミラー**: コスメ枠の防具の attribute modifier（防御力・防具強度・ノックバック耐性・移動速度など）を実装備に加算。UUID はプレイヤー・スロット・modifier ごとに作り直すので実装備と衝突しない。
- **クエリ上書き**: `Player#getItemBySlot` と `Player#getArmorSlots` がコスメ装備を返す。Protection / Feather Falling / Thorns などのバニラの軽減計算、および他 MOD の装備判定にコスメ装備が見える。
- **tick 窓（interleave）**: 奇数ゲーム tick の `Player#tick` の間だけコスメ装備のコピーを実装備枠に入れ、tick の終わりに戻す。鞘翅・カメの甲羅・MOD の装備判定など tick 内参照にも届く。差分ガードで装備パケットの誤送信を防ぐ。
- **item tick**: コスメ枠のアイテムにも `inventoryTick` / Forge の `onArmorTick` を回す（エネルギーやチャージを持つ MOD 防具のため）。
- **耐久ミラー**: 実装備が受けた耐久ダメージと同じ量をコスメ装備にも入れる（4 分の 1 丸め・最低 1・Unbreaking・破壊までバニラと同じ）。
- **移動の同期**: コスメ装備の tick が `setDeltaMovement` を変えた tick は `ClientboundSetEntityMotionPacket` で本人にも送る（jetpack がコスメ枠で飛ぶ）。
- **コマンド**: `/notcosmeticarmor status`（状態表示）、`/notcosmeticarmor probe`（tick 窓の内外のデバッグログ）。
- **設定**: `enabled` / `mirrorAttributes` / `overrideQueries` / `interleave` / `tickCosmeticItems` / `cosmeticDurability` / `ignoreSkinArmorFlag`。

### 検証済み

- Minecraft 1.20.1 / Forge 47.4.28 / Java 17 / Cosmetic Armor Reworked 1.20.1-v1a。
- Tinkers' Construct 3.12.1.231（属性・耐久ミラー）、Iron Jetpacks 7.0.9（コスメ枠で飛行）、ManaitaMTK 1.7.4（コスメ枠で無敵）。
- 描画は CAR に任せ、二重描画なし（HUD・インベントリ・三人称モデルをピクセル比較）。

### 既知の制限

- ダメージ軽減エンチャントはバニラの EPF 上限 20 で頭打ち。
- jetpack などの推力は 1 スタックぶんが上限で、実装備とコスメに 1 個ずつ入れても加算されない（実装備側の速度が優先）。
- コスメ枠が実装備と同等に扱われるため、チート系 MOD（まな板MTK など）の能力もコスメ枠で有効になる。
