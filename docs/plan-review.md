# plan.md 前提検証レポート（T-01 先行実施）

対象: plan.md（1.20.1 Forge / Cosmetic Armor Reworked Addon 計画、327行）
検証環境: 1.20.1 srg jar を Vineflower で逆コンパイル（/tmp/cae/mcsrc）、CAR tag `1.20.1-v1a` の実ソース、Forge branch `1.20.x` の patch 実物。

---

## 0. 結論（先に言うと）

1. **L2（tick 内で実枠⇔コスメ枠を差し替える）は 1.20.1 では成立しない。**
   装備差分の検出と `ClientboundSetEquipmentPacket` の送信は、`Player#tick` の**内側**で走る `LivingEntity#tick` の中にある。差し替えると毎 tick 送信と属性の add/remove が起きる。窓を開けば起き、閉じれば元通りなので「窓の中でだけ」は作れない。
2. **逆に「バニラは毎 tick 装備を読み直す」という事実は味方になる。**
   コスメ装備を本物として扱わせるのに、tick を偶数/奇数で振り分ける必要はない。読み直しは常在している。
3. したがって設計の重心は **L2（差し替え）→ L3（クエリ上書き）+ L1（属性ミラー）** に移すべき。INTERLEAVE は不要で、むしろ壊れる。実質 **PRIORITY 相当**が本線。
4. **M1 `LivingEntity#setItemSlot` は abstract。** 当てられない。主役は M8 `Player#getItemBySlot`。
5. **M2 `collectEquipmentChanges` は実在する（= `m_21319_`）。**（初版で「存在しない」と書いたのは誤記。srg 名だけを grep したため。）ただし差し替え型の設計では、これを抑止しても解決しない。属性の add/remove とパケット送信は `detectEquipmentUpdates`（= `m_21315_`）が `LivingEntity#tick` から直呼びで行う。クエリ上書き型なら、この差分処理が走っている間だけ上書きを止めればよい（差分ガード）。

---

## 1. 検証済みの実装事実（根拠つき）

| plan の前提 | 実物 | 判定 |
|---|---|---|
| 装備の正は `getItemBySlot` | `Player#getItemBySlot` (Player.java:1769-1776) は `this.inventory.armor.get(index)` を返すだけ。`Player#setItemSlot` (1783-1792) も `inventory.armor.set(...)` してから `onEquipItem` を呼ぶ | 正は **`Inventory.armor`**（NonNullList、index 0=FEET..3=HEAD） |
| M1 = `LivingEntity#setItemSlot` | LivingEntity.java:1878 で **abstract**。実装は Entity.java:2084（空）, Player.java:1783, Mob.java:848, ArmorStand.java:151 | **当てられない** |
| M2 = `collectEquipmentChanges` | `m_21319_` = `collectEquipmentChanges`。`detectEquipmentUpdates`（`m_21315_`）から毎 tick 呼ばれ、前回スナップショットとの差分があれば属性を add/remove し `ClientboundSetEquipmentPacket` を送る | **実在する**。ただし差し替え型では突破口にならない |
| 属性は `onEquipItem` で計算される | `LivingEntity#onEquipItem` (LivingEntity.java:667) はサウンド/ゲームイベントのみ | 違う |
| 属性反映は tick の外（`ServerEntity#sendChanges`） | `LivingEntity#tick` の LivingEntity.java:2253 が `this.m_21315_()` を呼ぶ。→ m_21315_(2345) → m_21319_(2350) が **前回スナップショット `f_20947_`（`m_21198_` = 2427-2428）と現在の `getItemBySlot`（`m_6844_`）を比較**し、差分があれば (a) `AttributeMap` を `m_22161_`/`m_22178_` で更新、(b) その場で `ClientboundSetEquipmentPacket` をブロードキャスト | **送信元は tick の中**。M10 の保険は効かない |
| 耐久ダメージは `hurtArmor` が `getItemBySlot` 経由 | `Inventory#hurtArmor` (Inventory.java:504) は `f_35975_`（armor 配列）を**直接**回す | storage を差し替えると**コスメが削れる**。クエリ上書きなら実装備が削れる（正しい） |
| L4 は CAR に任せて完全無干渉 | CAR の `impl/client/PlayerRenderHandler` は `RenderPlayerEvent.Pre`(HIGH) で `player.getInventory().armor` を**直接書き換え**、POST(LOW) で復元 | 配列は共有。描画に触らなければ実害は小さいが「無干渉」ではない |
| CAR の公開 API は `IModCosArmor` | `api/CosArmorAPI` の static `getCAStacks(UUID)`/`getCAStacksClient(UUID)` が返す `CAStacksBase extends ItemStackHandler`（0-3 = FEET,LEGS,CHEST,HEAD、`isSkinArmor(slot)` あり） | 形が違う |
| item tick は `Item#inventoryTick`(M12) | Forge で `ItemStack#onInventoryTick` → `IForgeItem#onInventoryTick`（idx 36..39 で `onArmorTick`） | コスメを tick させたいなら手動でこれを叩けばよい |

### srg → official 名（この文書で使う対応）

| srg | official |
|---|---|
| m_8119_ | `tick` |
| m_21315_ | `detectEquipmentUpdates` |
| m_21319_ | `collectEquipmentChanges` |
| m_21142_ | `handleEquipmentChanges`（パケット送信） |
| m_21091_ | `handleHandSwap` |
| m_21198_ | `getLastArmorItem` |
| m_6844_ | `getItemBySlot` |
| m_238392_ | `onEquipItem` |

### Forge のフック位置

- `LivingEntity.java.patch:442` → `LivingEntity#tick` の**先頭**で `ForgeEventFactory.onLivingTick`（LivingTickEvent）。`m_21315_`(2253) より**前**。
- `Player.java.patch:36` → `Player#tick` の**先頭**で `onPlayerPreTick`（PlayerTickEvent は HEAD。TAIL ではない）。
- `LivingEntity.java.patch:450` → `onLivingEquipmentChange` は `m_21319_` の差分検出直後に発火。

---

## 2. なぜ L2 が壊れるか（連鎖で見る）

```
Player#tick()                       ← ここで storage を差し替えると…
  onPlayerPreTick
  ...
  super.tick()  == LivingEntity#tick
      onLivingTick
      ...
      m_21315_()                  ← スナップショット vs getItemBySlot を毎 tick 比較
         差あり → 属性 add/remove + 装備パケット送信
  ...
  （窓を閉じる）
```

- 偶数 tick で実装備・奇数 tick でコスメ、と切り替えると、**毎 tick 必ず差が出る** → CRITERIA の I1 が防ぎたかったパケットが毎 tick 飛ぶ。
- try/finally で窓を閉じても、差分を起こしたのは窓の中なので手遅れ。
- さらに `Attributes` は毎 tick add/remove され、`getAttributeValue` の結果が偶数/奇数 tick で振動する。L1 と併用すると二重計上で数値が壊れる（plan §7 の「窓内は属性を読み直さない」は誤り）。

## 3. 逆に、この構造は「コスメを本物として扱う」に有利

バニラは**毎 tick** `getItemBySlot` を見て、装備が変われば属性を更新しパケットを送る。
つまり「`getItemBySlot` がコスメを返す」状態を**安定して**作れば、バニラが自分で属性を計算してくれる。
問題は「実装備の属性も効かせたい」場合だけ。そこは差分処理から実装備を見せればよい。

---

## 4. 修正案

### 案A（推奨）: L1 + L3 + 差分ガード ＝ PRIORITY 改

- **storage は絶対に触らない。** 正は `Inventory.armor`（実装備）。耐久・装備パケット・`onEquipItem` は全部バニラのまま正しく動く。
- **L1（属性ミラー）**: コスメ枠の `getAttributeModifiers` を、UUID を remap した `AttributeModifier` として常設。実装備分（バニラが計算）に**加算**される。
  - UUID は `UUID.nameUUIDFromBytes(playerUUID + ":" + slot + ":" + attributeId + ":" + n)`。**アイテム元の UUID を流用してはいけない**（armor の modifier UUID は slot 固定値なので実装備と衝突する）。
  - 適用した Multimap を保持し、変化時は「前回適用分を remove → 今回分を add」（バニラの `m_21319_` と同じ手法）。
- **L3（クエリ上書き）**: `Player#getItemBySlot` を `@Inject(at=RETURN, cancellable=true)`。ARMOR スロット限定・サーバ限定・ガード中は素通し。
- **差分ガード**: `LivingEntity#m_21315_` の HEAD でフラグ ON / RETURN で OFF。ガード中は `getItemBySlot` が実装備を返す → バニラの差分は実装備だけを見る → パケットは実装備の変更時のみ、属性は実装備分のみ。その上に L1 が乗る。
  - 例外でフラグが残った場合の壊れ方は「コスメの属性が効かなくなる」方向に倒れる（フェイルセーフ）。`Player#tick` の TAIL でも念のためクリア。
  - 代替実装: `m_21315_` を cancel して実装備版の差分を自前で書き直す（`@Shadow` で `f_20947_`/`m_21091_`/`m_21142_` を触る）。堅いが複製コードが増える。
- 見た目（他プレイヤーからの見え方）は当面**実装備のまま**。コスメの外見共有は CAR の同期に乗る別機能として後回し。

### 案B（非推奨）: 差し替え型を救う

`m_21315_` を窓内で cancel し、属性は L1 のみ、パケットは実装備変化時に自前送信。`hurtArmor` がコスメを削る問題も別途潰す必要がある。工数対効果が悪い。

### 案C（最小）: L1 のみ（plan の ATTRIBUTE モード）

`getItemBySlot` を触らない。見た目は CAR、数値は L1。Mod の「この装備を着ているか」判定は実装備を見る。最短で「コスメ装備に効果がある」体験が出せる。

---

## 5. Mixin 表の改訂

| # | plan | 改訂 |
|---|---|---|
| M1 | LivingEntity#setItemSlot | **廃止**（abstract） |
| M2 | collectEquipmentChanges | **廃止**（実在するが、差し替え型そのものを捨てる）→ 差分ガード（`detectEquipmentUpdates` の HEAD/RETURN）へ |
| M3 | onEquipItem | 不要（音のみ）。L1 更新のトリガに使うなら任意 |
| M4 | Player#tick HEAD/TAIL | **残す**（L1 更新＋フラグ防御クリア）。storage swap は廃止 |
| M5 | LocalPlayer#tick | サーバ専用なら不要 |
| M6 | Entity#setDeltaMovement | 不要（属性で自動反映） |
| M7 | LivingEntity#addEffect | 不要 |
| M8 | getItemBySlot | **主役に昇格**（@Inject RETURN, cancellable） |
| M9 | Player#hurtArmor | 不要（storage を触らないので自然に実装備へ） |
| M10 | ServerEntity#sendChanges | 不要（`m_8533_`。差分は tick 内で完結） |
| M11 | HumanoidArmorLayer#renderArmorPiece | 不要。触るなら CAR `PlayerRenderHandler` との順序だけ確認 |
| M12 | Item#inventoryTick | 任意（コスメを tick させたい場合） |
| M13 | IItemExtension#onArmorTick | 任意。`IForgeItem#onInventoryTick(stack, level, player, 36+idx, selected)` を自前で呼べば済む |

実装本数は **3 本**（M4 / M8 / 差分ガード）＋ L1 のロジック。plan の 13 本から大幅に減る。

---

## 6. 工数・マイルストーン（改訂案）

| | plan | 改訂 |
|---|---|---|
| T-01 事実確認 | 1.0d | **完了**（本レポート） |
| M0 アンカー確定 | 1d | 不要（既に確定） |
| M1 ATTRIBUTE | 5d | **2d**（L1 のみ。案C） |
| M2 INTERLEAVE PoC | 10d | **3d**（L1 + L3 + 差分ガード。案A） |
| 合計 | 19d | **8-10d**（+ 検証・調整） |

---

## 7. 決めてほしいこと

1. 対象は 1.20.1 Forge 47.x で確定か（他バージョンも視野に入れるか。入れるなら L1 の UUID 設計と `m_21315_` の存在確認をバージョンごとにやる）。
2. PoC で試す装備ペア 1 組（Mod 名まで）。「属性が乗る」ことを目視できるもの。
3. 配布範囲（個人用 / 公開）。公開なら ModID・ライセンス・CAR への依存宣言の扱いを決める。
4. 最初の到達点は 案C（L1 のみ、2d）か、案A まで一気に（3d+）か。
5. 「コスメ装備も item tick する」を要件に含めるか（含めるなら M12/M13 相当を1本追加）。
6. `isSkinArmor`（CAR のスロット単位フラグ）を尊重するか。尊重するなら L1/L3 の対象スロットはこれで絞る。

---

## 8. 実装スケッチ（案A の核）

```java
// getItemBySlot: ガード中だけ実装備を返す
@Inject(method = "getItemBySlot", at = @At("RETURN"), cancellable = true)
private void cae$cosmeticView(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
    if (slot.getType() != EquipmentSlot.Type.ARMOR) return;
    if (CosmeticState.isVanillaBookkeeping()) return;   // 差分ガード中
    ItemStack cos = CosmeticState.cosmetic(this, slot);
    if (!cos.isEmpty()) cir.setReturnValue(cos);
}

// 差分ガード: バニラの装備差分には実装備を見せる
@Inject(method = "m_21315_", at = @At("HEAD"))
private void cae$guardOn(CallbackInfo ci) { CosmeticState.enterBookkeeping(); }

@Inject(method = "m_21315_", at = @At("RETURN"))
private void cae$guardOff(CallbackInfo ci) { CosmeticState.exitBookkeeping(); }
```

L1 は `Player#tick` の TAIL で「コスメ4枠の `getAttributeModifiers` を remap して差分適用」。
---

## 9. 追記（実装後・実機検証後）: 案B は成立した

§0 の結論1「L2 は 1.20.1 では成立しない」は**取り消す**。§4 で非推奨とした案B（窓の中で
`detectEquipmentUpdates` を cancel する）を実装して実機で動かしたところ、破綻せずに動いた。

### 何が起きていたか

結論1の根拠は「差分は `Player#tick` の内側（`LivingEntity#tick`）で走るので、窓を開けば必ず差が出る」
だった。これは**差分を止めない場合**の話で、止めれば話は別だった。

- `detectEquipmentUpdates`（`m_21315_`）の HEAD に `@Inject(..., cancellable = true)` を置き、
  窓が開いている間は `ci.cancel()` する。差分が出ないので属性の add/remove も
  `ClientboundSetEquipmentPacket` も発生しない。
- 属性は L1（remap した transient modifier）だけが供給するため、窓の中でコスメ装備が
  `Inventory#armor` に入っていても**二重計上しない**。
- 窓の中では `bookkeeping` フラグでクエリ上書き（L3）も止める。止めなくても実体がコスメなので
  結果は同じだが、L1 の再計算や mod 側の `getItemBySlot` 呼び出しと競合しないようにしている。

### 実機での確認（1.20.1 Forge 47.4.28 + CAR 1.20.1-v1a）

実装装備にダイヤ胸当て（armor 8）、コスメ枠に金胸当て（armor 5）を入れた状態:

```
probe(inside window): chest seen=1 golden_chestplate storedReal=1 diamond_chestplate armorValue=13
```

- 窓の中: `getItemBySlot(CHEST)` は**コスメ**（金）を返す。
- 同時に `getArmorValue()` は **13**（8 + 5）のまま。窓の中で金の modifier が再適用されていない
  ＝ 18 にならない。
- 窓の外（ログでは `probe(outside window)`）: 実装備（ダイヤ）に戻る。
- 数分間動かして例外・装備パケットの増加なし。ワールド退出後、CAR の `.cosarmor` は
  金胸当てを保持したまま（コピーを入れているので CAR のスタックは壊れない）。

### 残っている制約

- 窓の中で付いた**耐久値の減りは捨てられる**（実装備に戻すときコピーを破棄するため）。
  コスメ装備を「耐久が減らない見た目装備」として扱う既存の方針と整合するが、鞘翅のように
  耐久を消費して動く装備をコスメ枠で使うと減らない（意図的な仕様として README に明記）。
- 窓の中の変更（`setItemSlot` 経由でない直接書き換え）は保存されない。`Inventory.armor` を
  直接触る他 MOD が、窓が開いている奇数 tick に限りコスメのコピーを見る可能性がある。
- 装備差分のスナップショット（`f_20947_`）は窓の間更新されないため、窓の外で実装備を
  付け替えた場合は次の tick で正しく差分が取られる（検証済み）。

---

## 10. 追記（L4: 描画は CAR に任せて干渉しない — 検証済み）

plan.md:22 は L4 を「コスメ枠の描画（CAR の責務。干渉しない）」、plan.md:138 は M11
（`HumanoidArmorLayer#renderArmorPiece` を止めて二重描画を防ぐ）を「描画は窓外なので基本不要」と
している。実装後に M11 の要否を判断できる材料が揃ったので、結論を書く。

### CAR の描画経路（実物を読んだ結果）

`lain/mods/cos/impl/client/PlayerRenderHandler.java`:

- `RenderPlayerEvent.Pre`（HIGHEST）で `player.getInventory().armor` の内容を退避し、
  `ModObjects.invMan.getCosArmorInventoryClient(uuid)`（CAR 自身の `InventoryCosArmor`）から
  スロットごとのコスメを `armor.set(i, stack)` で差し込む。`Post`（LOWEST）と
  レンダリングキャンセル時に元へ戻す。
- 同じ処理を `RenderHandEvent` / `RenderArmEvent` にも持つ。
- **`getItemBySlot` は一度も呼ばない。**

plan.md:225-226 の分岐でいう「自前インベントリ参照」に該当する。したがって:

- L3（クエリ上書き）とは経路が重ならないので、二重描画は起きない。
- 本 MOD の mixin はクライアントでは動かない（`PlayerMixin#getItemBySlot` は `isClientSide()`
  で早期 return、`LivingEntityMixin#detectEquipmentUpdates` は `ServerPlayer` 以外で return）。
- **M11 は実装しない**（入れても止める対象がない）。

### 実機での確認（1280x720、同一視点で連続キャプチャ）

実装装備ダイヤ胸当て・コスメ金胸当ての状態で、`/tp @p ~ ~ ~ 0 0` により視点を固定して撮影:

| 対象 | 比較 | 結果 |
|---|---|---|
| HUD の防具バー | 12 フレーム（約 2 秒）のピクセル比較 | 全て一致（差分 0 ピクセル） |
| インベントリの防具スロット | 8 フレーム | 全て一致（ダイヤのまま） |
| 三人称モデル | 10 フレームの金/ダイヤ色ピクセル数 | 金 276〜305・ダイヤ 3〜11（揺らぎは待機モーションのみ） |

防具バーが動かない＝ `GenericArmor` の同期値が 13 で安定（8 と 13 の間でチカチカしない）。
防具スロットがダイヤのまま＝ tick 窓のコピーが `ClientboundSetEquipmentPacket` で
クライアントに漏れていない。モデルが常に金＝ CAR の差し替えが 1 回だけ効いている
（実装備と二重に描かれていない）。

![presentation layers](img/presentation-layers.png)

### 残る制約（L4）

- 描画は完全に CAR 依存。CAR を外すとコスメは見えなくなる（属性だけ残る）。
- CAR 側が `Disabled` を立てた場合（GUI 内など）は実装備の見た目になる。これは CAR の仕様。
- 他プレイヤーからの見え方も CAR の同期に依存する（同じ描画経路なので本 MOD の影響はない）。

## 11. 追記（耐久のミラーを実装した）

plan.md の「何が機能しないか」には耐久値が入っていなかったが、実装後の要望でコスメ装備も
実装備と同じように傷むようにした。設計上の判断だけ残しておく。

### 経路

バニラの耐久ダメージは `Player#hurtArmor(DamageSource, float)`（srg `m_6472_`）から
`Inventory#hurtArmor(source, amount, Inventory.ARMOR_SLOTS)`（`m_150072_`）に入り、
スロットごとに `stack.hurtAndBreak((int)max(amount / 4, 1), player, ...)` が呼ばれる。
ここが唯一の入口なので、`Player#hurtArmor` の HEAD に注入して**同じ計算**をコスメ側にもう一度
回し、結果を CAR の `ItemStackHandler#setStackInSlot` に書き戻している
（`core/CosmeticDurability.java`）。

### 窓との干渉（ここが実装上の要点）

tick 窓（L2）が開いている間は `Inventory#armor` にコスメのコピーが入っている。そのまま
`hurtArmor` を走らせるとバニラはコピーを傷つけ、コピーは捨てられるので**実装備の耐久が
消える**。そこで `hurtArmor` の HEAD で窓を閉じて実装備を戻し、RETURN で開き直す
（`CosmeticState#reopenWindow`）。実測でも、炎上中の `in_fire` はすべて `window=true` で
入ってきており、閉じ直しが効いていること、実装備とコスメが同じ値で減ることを確認した。

### 実測（Forge 47.4.28 / CAR 1.20.1-v1a）

| ダメージ | 実装備（ダイヤ 528） | コスメ（金 112） |
|---|---|---|
| `mob_attack` 5 | 1 | 1 |
| `in_fire` 1.0 × 8 | 9 → 18 | 9 → 18 |
| さらに `mob_attack` 5（コスメ 111 から） | 19 | 破壊 → スロット空、防御力 13 → 8 |

`/damage` の既定は `minecraft:generic` で、これは `bypasses_armor` タグに入っているため
防具の耐久を削らない（バニラ仕様）。テストには `minecraft:mob_attack` か炎を使う必要がある。

### 残る制約

- 耐久ダメージの入口（`Player#hurtArmor`）に乗っているので、実装備が空のスロットでも
  コスメは傷む（`LivingEntity#getDamageAfterArmorAbsorb` は防具の有無に関係なく `hurtArmor` を呼ぶ）。
- Unbreaking の抽選はコスメ側でも独立に走るので、実装備とコスメで減少量がずれることがある
  （バニラの規則どおり）。

## 12. 追記（エンチャントは `getArmorSlots` も上書きしないと効かない）

plan.md の M8 は `Player#getItemBySlot` の上書きだけでエンチャントが効く前提だったが、
実機では **Protection IV を付けても軽減が乗らなかった**（防御力 14 の装甲分だけで、
`mob_attack 10` → 6.0。実枠に同じヘルメットを着けると 4.368 まで下がる）。

### 原因

バニラのダメージ軽減は装備を `getArmorSlots()`（srg `m_6168_`）のリストから読む:

- `LivingEntity.java:1592`: `int epf = EnchantmentHelper.getDamageProtection(this.getArmorSlots(), source);`
- `Player.java:1804-1806`: `getArmorSlots()` は `inventory.armor`（NonNullList）をそのまま返す。

つまり `getItemBySlot` を通らない。上書きの対象を `getArmorSlots` にも広げ、
実装備リストをコピーしてコスメで差し替えたリストを返すようにした
（L3 の実装は `PlayerMixin#notcosmeticarmor$getArmorSlots`）。

`getArmorSlots` の利用箇所は 3 つだけ（軽減計算・防具数カウント・`PiglinAi` の金防具判定）で、
いずれも読み取り専用なので副作用は限定的。

### 意味論（合算。実装備とコスメの両方が効く）

最初はリストの該当スロットを**置き換える**実装にしたが、「実装備のエンチャントも残したい」という
方針にしたがって、実装備リストの**末尾にコスメを追加**する形に変えた。結果として:

- ダメージ軽減は `EnchantmentHelper.getDamageProtection` がリスト全体の EPF を足し合わせるので、
  実装備とコスメの Protection / Feather Falling が**同時に**効く（属性ミラーの「両方足す」と一致）。
- バニラの上限はそのまま: `CombatRules.getDamageAfterMagicAbsorb` が EPF を 0..20 に丸めるため、
  二重に Protection IV を積んでも最大 80% で頭打ち。
- `getItemBySlot`（置き換え）と `getArmorSlots`（合算）で見え方が違う点には注意。前者は
  「そのスロットに着ているもの」、後者は「効いている防具全部」。

### 実測

| 条件 | ダメージ | 結果 |
|---|---|---|
| 上書き前: Protection IV コスメ金ヘルメット | `mob_attack` 10 | 6.0（EPF なし） |
| 置き換え実装: 同条件 | `mob_attack` 10 | 5.04（EPF 4 → ×0.84） |
| 合算実装: 実装備 FF IV 金ブーツ + コスメ FF IV 金ブーツ + コスメ Prot IV 金ヘルメット（防御力 15、強度 2） | `mob_attack` 20 | 12.096（装甲 14.4 × 0.84。mob_attack に FF は乗らない） |
| 合算実装: 同上 | `fall` 20 | 4.0（EPF 12+12+4=28 → 上限 20 → ×0.2） |

実装は `PlayerMixin#notcosmeticarmor$getArmorSlots`（`new ArrayList` に実装備を詰めてから
非空のコスメを `add`）。

### 残る制約

- ピグリンは `PiglinAi#isWearingSafeArmor` が `getArmorSlots` を見るため、コスメの金防具でも
  敵対しなくなる（バニラと同じ経路に乗る。意図的に残している）。
- `LivingEntity#getArmorCoverPercentage`（`m_21207_`）もこのリストを数えるため、被覆率が
  実装備 + コスメの比率になる（炎上中の軽減など、バニラの式にそのまま乗る）。

## 13. 追記（他 MOD の「装備判定」はどこまで通るか = まな板MTK）

L3（`getItemBySlot`）+ item tick（`CosmeticItemTicker`）が、外部 MOD の装備判定にどこまで
届くかの実例。[ManaitaMTK](https://modrinth.com/mod/manaitamtk) 1.7.4 のヘルメットは
「装備中は全てのダメージ 0・死亡しない」チート装備だが、**コスメ枠でもそのまま成立した**。

### 相手側の実装（逆コンパイルで確認）

- `HelmetManaita#inventoryTick`（`m_6883_`）: `player.getItemBySlot(EquipmentSlot.HEAD)` が
  まな板ヘルメットかを調べ、自分自身の ItemStack に `IInvincible` capability を付ける
  （`setInvincible(isEquipHelmet)`）。
- `MTKSubscribeEvent` が `LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent` /
  `LivingDeathEvent` / `LivingKnockBackEvent` などで `MTKEventHelper.cancel(event)` を呼ぶ。
  `cancel` は `MTKCapabilityHelper.execute(INVINCIBLE, player, EquipmentSlot.HEAD, ...)` →
  `player.getItemBySlot(HEAD)` の capability を見て `event.setCanceled(true)`。

### 本 MOD で成立した理由

1. `CosmeticItemTicker` がコスメ枠のアイテムに `onInventoryTick`（→ `ItemStack#inventoryTick`）
   を回すので、コスメのまな板ヘルメットの `inventoryTick` がサーバー側で走る。
2. その中で読む `getItemBySlot(HEAD)` が、本 MOD の L3 上書きでコスメを返す → capability が立つ。
3. ダメージ時に同じ `getItemBySlot(HEAD)` を読むので、同じスタックの capability が返る。

### 実測（Forge 47.4.28 / CAR 1.20.1-v1a / ManaitaMTK 1.7.4、HEAD 実装備は空・インベントリにも
まな板ヘルメット無し）

| 試験 | 結果 |
|---|---|
| `/damage @p 20 minecraft:fall`（bypasses_armor） | 0 ダメージ（Health 200 のまま） |
| `/damage @p 20 minecraft:mob_attack` | 0 |
| `/damage @p 1000000 minecraft:mob_attack` | 0 |
| `/damage @p 20 minecraft:out_of_world` | 0 |
| `/kill @p` | 生存（チャットは `Killed TestPlayer`、Health 200、死亡画面なし） |

まな板の属性（armor 165 相当）はバニラの属性上限で armor 30 に丸められるため、
「装甲で耐えた」のではなくイベントキャンセルで 0 になっている。`abilities.mayfly=1b`、
`invisibility` / `night_vision` 400 tick もコスメで有効（MOD 側のコード変更は不要）。

### 注意

これは「相手 MOD が実装備を前提に書かれている」ぶん、コスメ枠が実装備と同等に扱われることの
裏返し。チート系 MOD を入れた環境では、コスメ枠がその能力をそのまま得る（公開サーバーでは
導入しないこと）。


## 14. 追記（移動はクライアント権威 — jetpack は「動き」だけ別扱いだった）

[Iron Jetpacks](https://modrinth.com/mod/iron-jetpacks) 7.0.9 + Cucumber 7.0.16。コスメ枠の
jetpack は item tick（`CosmeticItemTicker` → `IForgeItem#onInventoryTick` → `onArmorTick`）で
`JetpackItem#fly` まで到達するのに、**最初は飛ばなかった**。

### 症状と原因

実装備ダイヤ胸当て + コスメ枠 iron jetpack、Y=95.0 からスペースを 3.6 秒保持 → Y は
95.93 → 96.31 → 96.09（普通のジャンプ程度）。退出後の `.cosarmor` の `Energy` は
**789320**（800000 − `usage` 120 × 89 tick）で、サーバー側で `onArmorTick` が毎 tick
走っていたことは確定。

原因は移動の権威。`JetpackItem#fly` は `player.setDeltaMovement(...)` を呼ぶだけで
`hurtMarked` を立てない。プレイヤーの位置はクライアント権威なので、サーバー側で書き換えた
速度は `ClientboundSetEntityMotionPacket` で本人に送らないと、クライアントの自己予測に
上書きされて消える。バニラの防具は速度を触らないため、これまで露見していなかった。

### 修正（`CosmeticItemTicker`）

1 コスメごとに `Vec3 before = player.getDeltaMovement()` を控え、`onInventoryTick` の後に
変化していれば `player.hurtMarked = true` を立てる（変化が無い tick では立てない）。

- 送信経路: `ServerEntity#sendChanges`（`m_8538_`）が `hurtMarked` を見て、トラッカー全員
  **+ 本人の connection** に `ClientboundSetEntityMotionPacket` を送る
  （`net/minecraft/server/level/ServerEntity.java:297-302`）。ChunkMap から毎 tick 呼ばれる。

### 実測（survival、スペース保持、F3 の Y を 2.3〜2.8 秒間隔で 2 点サンプル）

| 構成 | dY/dt | blocks/tick | 理論 `speedVert` |
|---|---|---|---|
| コスメ iron のみ | 7.63 b/s | 0.38 | 0.41 |
| コスメ gold のみ | 11.34 b/s | 0.57 | 0.61 |
| 実 iron + コスメ gold | 8.15 b/s | 0.41 | 実 iron の 0.41 |
| 実 gold + コスメ iron | 11.15 b/s | 0.56 | 実 gold の 0.61 |
| 実 gold + コスメ gold | 12.17 b/s | 0.61 | 0.61 |

スクリーンショット取得（`import`）に ~0.15 s かかるぶん、実測は理論値の約 0.93 倍に出る。
iron : gold の比 1.49 ≒ 0.61 / 0.41 = 1.49 で、速度差も正しく出ている。

### 2 個入れても 2 倍にはならない

jetpack の推力は `min(motionY + accel, speedVert)` を **1 スタック**（`getEquippedJetpack` →
`getItemBySlot`）から決めるので、実装備とコスメに 1 個ずつ入れても加算されない。さらに
クライアントは実装備しか見えないため、両方入れると**実装備側の速度に引っ張られる**
（上の表の 3・4 行）。サーバー側で「装備中」として動くのは常にコスメ枠の 1 個だけなので、
2 個持ちの実利は予備・エネルギー切れ対策まで。

### 注意

コスメ枠のアイテムが死亡時にドロップするのは CAR の仕様（`ModConfigs.CosArmorKeepThroughDeath`
が false のとき、`InventoryManager#handlePlayerDrops` が `.cosarmor` を空にして
`ItemEntity` を湧かせる）。本 MOD は CAR のインベントリを書き換えないため、この挙動は
バニラの防具と同じ。

## 15. 追記（クライアント側のクエリも通すことにした — AAE の水中呼吸の件）

### きっかけ

実インスタンス（NCA）で「AdvancedAE の水中呼吸が効かない」との報告。ソースを読むと
`AAELivingEntityEvents#breath` は `player.getItemBySlot(HEAD)` を見て
`LivingBreatheEvent#setCanBreathe` を立てるだけだった。経路自体は本 MOD の
`getItemBySlot` 上書きに乗るので、**サーバー側だけを見れば効くはず**だった。

### 実装前の設計（サーバー専用にしていた理由）

`PlayerMixin` は `self.level().isClientSide()` で早期 return していた。理由は 2 つ:

1. CAR が描画時に `Inventory#armor` をコスメに入れ替えるので、クライアント側で上書きすると
   二重適用になる。
2. クライアントのコスメ取得は CAR の `CosArmorAPI.getCAStacksClient` で別 API になる。

### 変更

- `CarBridge` に `getCAStacksClient` を追加し、`get(uuid, slot, boolean client)` に統一。
  `available()` は `getStackInSlot` と（`getCAStacks` か `getCAStacksClient` のどちらか）が
  揃っていれば true とし、片方だけの API jar 構成でも壊れないようにした。
- `CosmeticStacks#get(Player, int)` が `player.level().isClientSide()` で API を切り替える。
- `core/QueryGuard`（新規）: 描画中だけクエリ上書きを止めるフラグ。
- `client/ClientRenderGuard`（新規）: `RenderPlayerEvent.Pre`(LOWEST, receiveCanceled) で
  suspend、`Post`(LOWEST) で resume。CAR は Pre=HIGH / Post=LOW なので、必ず CAR の
  入れ替えの内側になる。Pre がキャンセルされたら Post が来ないので、その場で resume する。
- `getArmorSlots` はクライアントでもコスメを**追加**する（置換ではない）。

### 実測

| 見たもの | 結果 |
|---|---|
| 三人称のモデル（コスメ HEAD = 金ヘルメット / 量子ヘルメット） | 1 回だけ描画。二重描画なし。HUD の防具バーも実装備のまま |
| コスメ Respiration III 金ヘルメットで 13 秒水中 | 泡 7 個（期待どおり） |
| コスメ AdvancedAE 量子ヘルメット（`water_breathing_tag:{enabled:1b}` + 電力 200 万）で 21 秒水中 | 泡 10 個のまま減らない |

`grep Exception` は他 MOD の既存警告のみで、本 MOD 由来の例外は出ていない。

### 残る制約

- クライアント側のコスメは CAR のクライアントコピーが正であることが前提。サーバーとずれると
  1 tick だけ古い値が見えることがある（CAR の仕様）。
- `Inventory#armor` を直接読む MOD は、tick 窓の外ではコスメを見ない。上書きできるのは
  `getItemBySlot` / `getArmorSlots` まで（ProjectE のジェム飛翔は §16 の個別パッチで対応した）。



## 16. 追記（`Inventory#armor` の直読みには個別パッチ — ProjectE の飛翔と Step Assist）

### きっかけ

実インスタンス（Forge 47.4.28 / CAR 1.20.1-v1a / 41 MOD）で、コスメ枠のジェム防具が
「エフェクトだけ効いて飛べない・Step Assist が無い」という報告。逆コンパイルで経路を追うと、
ProjectE の飛翔と Step Assist は `PlayerHelper#checkArmorHotbarCurios(Player, Predicate<ItemStack>)`
が `player.getInventory().armor` を直接 stream して判定していた（`InternalAbilities#shouldPlayerFly`
→ `GemFeet#canProvideFlight`、`shouldPlayerStep` → `GemLegs`）。§15 のクライアント対応でも
§9〜§14 のどの層でも届かない場所で、**本 MOD が書き換えない実体**を読んでいる。

### 修正 1: 判定メソッドの戻り値だけを広げる

- `compat/projecte/mixin/PlayerHelperMixin`（`@Pseudo @Mixin(targets = "moze_intel.projecte.utils.PlayerHelper", remap = false)`）
  が `checkArmorHotbarCurios` の RETURN に `require = 0` で注入する。
- 元の戻り値が `true` なら何もしない。`false` のときだけ `ProjectECompat#anyWorn` を呼び、
  `EquipmentSlot.Type.ARMOR` の 4 スロットを `player.getItemBySlot(slot)` で引き直して
  `checker` に通す。**getItemBySlot 経由にするのが要点**で、`GemFeet#canProvideFlight` は
  渡されたスタックを `player.getItemBySlot(FEET)` と参照比較するため、自前のコスメスタックを
  直接渡すと同一性で落ちる。
- `ProjectEMixinPlugin`（`IMixinConfigPlugin`）が `ModList.get().isLoaded("projecte")` を見て、
  無ければ config ごと適用しない。ProjectE のクラスは一切コンパイル時参照していない。
- 設定 `compat.patchProjectE`（既定 true）で切り分け可能。`/notcosmeticarmor status` に
  `projectE: patch=... calls=... hits=...` を出し、呼ばれた回数と一致した回数を数える。

### 修正 2: `InternalAbilities` のキャッシュがゲームモード変更で固まる

パッチ 1 を入れて `calls` と `hits` が毎 tick 一致するようになっても、`abilities.mayfly` が
`0b` のまま戻らないことがあった。`moze_intel.projecte.handlers.InternalAbilities#tick` は
`hadFlightItem` をキャッシュして「前 tick と同じ状態なら何もしない」構造で、
クリエイティブ（飛翔し放題）で `hadFlightItem=true, wasFlyingGamemode=false` になったあと
サバイバルへ戻ると、どの分岐も成立せず `updateClientServerFlight` が呼ばれない。
さらにゲームモード変更パケット自体が `mayfly=false` をクライアントへ送るため、
サーバーだけ `true` という非同期も起こりうる。

`compat/projecte/mixin/InternalAbilitiesMixin`（`@Shadow @Final ServerPlayer player` /
`@Shadow boolean hadFlightItem`）で:

- HEAD: ゲームモードが変わった tick で `hadFlightItem = false` に落とし、再同期フラグを立てる。
  さらに `!mayfly && !isCreative() && !isSpectator()` なら毎 tick `hadFlightItem = false` に
  戻して、スタック状態を自然に解く。
- RETURN: フラグが立っていれば `player.onUpdateAbilities()` で能力パケットを送り直す。

### 実測（Forge 47.4.28 / CAR 1.20.1-v1a / ProjectE 1.20.1）

| 見たもの | パッチ前 | パッチ後 |
|---|---|---|
| `projectE: calls / hits` | 229 / 0 | 4106 / 4106 |
| `forge:step_height_addition` | 0.6（Step Assist 無し） | 1.0（+0.4 ADDITION） |
| `abilities.mayfly`（サバイバル、コスメ gem_boots） | 0b | 1b |
| ダブルタップ + 2 秒ホールド | 上昇しない | `Pos[1]` 95.0 → 173.4 |
| `/gamemode creative` → `/gamemode survival` 往復 | mayfly=0b に固定 | 1b のまま、飛翔も維持 |

### 入力側の知見（検証ハーネスの罠）

`LocalPlayer#aiStep` の飛翔切り替えは「1 回目の押下から 7 tick 以内・空中・2 回目の押下が
前 tick に押されていないこと」が条件。自動操作で 12 ms の `key space` を送るとサンプリングに
拾われず、2 回目が「押しっぱなし」と判定されて成立しない。**80 ms 押下 → 160 ms → 80 ms 押下**
で確実に成立した。

### 残る制約

- ProjectE 側がメソッド名やシグネチャを変えると `require = 0` により**静かに**パッチが外れる。
  `/notcosmeticarmor status` の `calls` が増えないことで気づける（`hits` だけでは分からない）。
- `GemArmorBase#hasAnyPiece` / `hasFullSet` も `Inventory#armor` の直読みだが、ProjectE 1.20.1 の
  内部に呼び出し箇所が無い（デッドコード）ため対応していない。


## 17. fantasy_ending（夢幻終焉セット）— コスメ枠でも防護は成立していた

### 現象

ユーザー報告（m04533、原文）: "じゃあ、次に、Fantasy_endingの無限終焉装備も無限終焉防護が効かなったりするのを直してください"

無限終焉（= 夢幻終焉 / fantasy_ending の `FANTASY_ENDING_MATERIAL`）の 4 点セットには、
128 以下のダメージを無効化する「シールド」がある。これをコスメ枠に入れると効かない、という報告。

### 経路の特定（ソース読解）

EndingLib（`EndingLibraryTweaked-1.20.1-2.2.1fix.jar`）と fantasy_ending
（`fantasy_ending-1.20.1-2.7.20-all.jar`）を Vineflower で展開して追った。

- `/tmp/el/src/com/mega/endinglib/util/mc/entity/armor/ArmorUtils.java:163-183`
  `getArmorSet(LivingEntity)`: HEAD の material を基準に `EquipmentSlot.Type.ARMOR` の 4 枠を回り、
  **同じ material が 4 つ揃ったときだけ** material を返す。`armorSet(living, material)` はその比較。
  1 枠 1 回の `getItemBySlot` 呼び出しで、`Inventory#armor` の直読みは無い。
- `/tmp/el/src/com/mega/endinglib/common/eventhandler/ArmorEvents.java`
  `PlayerTickEvent` / `LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent` / `LivingDeathEvent` /
  `MobEffectApplicable` のすべてが `getArmorSet` をゲートにしている。
- `/tmp/fesrc/com/mega/uom/common/items/armor/FantasyEndingArmorItem.java:68`
  `is4Armor(LivingEntity)` = `ArmorUtils.armorSet(living, ModArmorMaterials.FANTASY_ENDING_MATERIAL)`。
  同 `:76` `public static float damage_reduction = 128.0F;`。
  `onArmorSetLivingAttack`: 128 以下、または HP が半分以下で飛翔物なら `event.setCanceled(true)`。
  `when4SetTick`: 10 tick ごとに `FE_SHIELD_EC` へ `setMaxLifeTime(20481)/setPerCooldowns(20)/
  setDamageResistance(128)/setOutResistance(0.25)/setLevel((short)4936)`。
- `/tmp/fesrc/.../runic/IFeShieldCapability.java` `dealDamage`:
  `if (is4Armor(direct) && amount > 128.0F) amount -= 128.0F; context.setAmount(amount * (1.0F - getOutResistance()));`
- `FeRunicShieldCapability#onHurt`: `amount <= getDamageResistance()` でキャンセル。

**判定経路はすべて `getItemBySlot`（= 我々の L3 クエリ上書き）を通る。** ProjectE の飛翔のように
`Inventory#armor` を直読みする箇所は無い。したがってソース上は「コスメ枠でも効くはず」だった。

### 実測（Forge 47.4.28 / CAR 1.20.1-v1a / fantasy_ending 2.7.20 / EndingLib 2.2.1fix）

コスメ枠に夢幻終焉 4 点だけを入れ、実装備は空。計測は **overworld**、`/difficulty hard` +
`/gamerule naturalRegeneration false`。

| 見たもの | 4 点 | 3 点（ヘルメットを抜く） |
|---|---|---|
| `/notcosmeticarmor status` の `armor` | 70（15+18+20+17） | 53 |
| `fantasyEnding:` 行 | `set=FANTASY_ENDING_MATERIAL is4=true shield[level=4936 dr=128.0]` | `set=none is4=false shield[level=0 dr=128.0]` |
| `/damage @p 50 minecraft:mob_attack` | キャンセル（HP 547.50037 → 547.50037） | HP 569.3 → 559.5（9.8 通る） |
| `/damage @p 150 minecraft:mob_attack` | HP 547.50037 → 545.43787（2.06 通る） | — |
| `/damage @p 50 minecraft:generic` | キャンセル | — |
| `/damage @p 20 minecraft:fall` | 通る（569.3 → 531.8、37.5 減） | — |
| クライアント側のセット判定 | true（シールド HUD のバーが出る） | — |
| `/effect give @p minecraft:poison 10 2` | 付与されない（`active_effects` 空） | — |

**結論: サーバー側の防護はコスメ枠で成立している。修正不要。**
128 超が通るのは仕様（`damage_reduction = 128.0F` が上限）。`fall` が通るのは
`onArmorSetLivingAttack` が見るのが `LivingAttackEvent` の全般だが、シールド側の
ダメージ種別フィルタで落下は対象外のため。

### 「効かない」の正体（3 つ）

1. **ダメージが 128 を超えている。** 128 以下しか無効化しない。超過分は
   `amount -= 128` のあと `*(1 - 0.25)` される。
2. **4 点が同一 material で揃っていない。** 夢影・星屑など別セットを混ぜると `is4=false` になる。
3. **古い jar を使っている。** 0.1.0 の jar ではクライアント側の `getItemBySlot` が
   `ServerPlayer` 限定で早期 return するため、クライアント側の `getArmorSet` が false になり
   **シールド HUD のバーが出ない**（防護自体はサーバー側で成立する）。
   1.1.0（commit `fb18c37`「client queries: report cosmetic armor on the client as well」）で解消。

### 罠: 夢幻終焉の次元ではセット無しでも小ダメージが無効化される

`fantasy_ending:faded_void`（HUD 表記「夢幻終焉の岸」）では、**夢幻終焉の 4 点を着ていない状態でも**
`/damage @p 50 minecraft:mob_attack` がキャンセルされた。次元自体が小ダメージを無効化するため、
セット効果の検証は必ず `/execute in minecraft:overworld run tp @p 0 100 0` などで
通常世界へ出てから行うこと。

### 1.3.0 の変更

コードの変更は診断 1 行のみ。`/notcosmeticarmor status` に
`fantasyEnding: set=<material> is4=<bool> shield[level=N dr=F]` を出す
（`src/main/java/dev/codeonigiri/notcosmeticarmor/compat/endinglib/EndingLibCompat.java`、
すべてリフレクション。クラスが無ければ `absent`）。

### 残る制約

- 128 のしきい値と `outResistance 0.25` は fantasy_ending 側の実装。こちらでは調整しない。
- `ArmorUtils#getArmorSet` が `getItemBySlot` 以外の経路に変わったら追従が要る
  （`/notcosmeticarmor status` の `is4` が false になったことで気づける）。

## 18. コスメ装備が実装備の「能力」を隠していた（1.4.0）

### 現象

ユーザー報告（m04911、原文）: "う〜ん色々検証して築いたのですが、コスメにものがあると本体側が無視されていませんか？例えばコスメがダイヤ装備だったとしてもUIは出ますが飛行などの能力が出ません、kill無効化などもです。"

`/tmp/nca-test`（実インスタンスのコピー、41 mods 中 fantasy_ending と EndingLib を有効化）で
再現した。実装備に夢幻終焉 4 点を `/item replace` で入れ、コスメ枠にダイヤ 4 点を入れた状態:

| 見たもの | 1.3.0 | 1.4.0 |
|---|---|---|
| `fantasyEnding:` 行 | `set=DIAMOND is4=false shield[level=0 dr=128.0]` | `set=FANTASY_ENDING_MATERIAL is4=true shield[level=4936 dr=128.0]` |
| `/kill @p` | 死亡（"TestPlayerは殺された"） | 死亡しない（HP 7.54 → 16.2、XP 30 → 28） |
| `/damage @p 50 minecraft:mob_attack` | 通る | キャンセル（"指定のダメージタイプは対象には効果がありません"） |
| `/damage @p 200 minecraft:mob_attack` | — | 通る（HP 20.0 → 7.54 = 128 を引いて 0.75 倍） |
| `/effect give @p minecraft:poison 10 2` | 付与される | 拒否（"対象に効果を与えることができません"） |
| シールド HUD | バーが出ない | バーが出る（"夢幻終焉の岸 1824s"） |
| ダブルタップ飛行 | 不可 | 可（Y 173.38 → 188.75 / 2 秒） |

### 原因 1: セット判定が実装備を見ない

`/tmp/el/src/com/mega/endinglib/util/mc/entity/armor/ArmorUtils.java:163-183` の
`getArmorSet(LivingEntity)` は 4 枠を `living.m_6844_(slot)` で読むだけ。コスメがあるスロットでは
我々の L3 がコスメを返すので、実装備の material が数えられない。
→ 4 点セットが成立せず、`FantasyEndingArmorItem#is4Armor` が false になり、
`ArmorEvents` の全ゲート（attack / hurt / damage / death / effect）が閉じる。

修正: `compat/endinglib/ArmorSetUnion.java` + `ArmorUtilsMixin` で `getArmorSet` の HEAD を
上書きし、「実装備・コスメのどちらかが一致するスロットが 4 つ以上」なら成立とみなす。
複数候補があるときは `OptionArmorMaterial`（EndingLib のセット効果付き素材）を優先する。
対象クラスが無い環境では `EndingLibMixinPlugin` が config を読み込まない。

### 原因 2: コスメの item tick がサーバー限定だった

バニラは `Player#tick` から `Inventory#tick` を**両側で**呼ぶので、実装備の `onArmorTick` は
クライアントでも走る。一方 `CosmeticTick.onTickEnd(ServerPlayer)` はサーバー専用だった。
`/tmp/fesrc/com/mega/uom/common/items/armor/FantasyEndingArmorItem.java` の `onArmorTick` は
CHEST 分岐で `player.m_150110_().f_35936_ = true`（mayfly）を side 判定なしに立てるため、
コスメ枠だとサーバーだけ `mayfly=true`、クライアントは false のまま → ダブルタップが成立しない
（HUD はサーバー同期のシールド値で出るので「UI は出るのに飛べない」になる）。

修正: `CosmeticTick.onTickEndClient(Player)` を追加し、`PlayerMixin` の `tick` TAIL で
`ServerPlayer` 以外にも `CosmeticItemTicker.tick` を回す（速度変更の `hurtMarked` はサーバーのみ）。

### 原因 3: tick 窓の中で実装備が退避される

原因 1 を直しても、ケース A だけ `shield[level=0]` のままだった。`FeRunicShieldCapability#tick` は
`LivingTickEvent`（= `Player#tick` の内側 = **tick 窓が開いている最中**）で
`if (!is4Armor(living)) { level = 0; }` を実行する。窓の中は `TickWindow#open` が
`Inventory#armor` をコスメで置き換え、実装備を `CosmeticState#windowReal` に退避しているため、
union の 2 層（`getItemBySlot` と `Inventory#armor`）が両方コスメになり、実装備セットが消える。
`when4SetTick` は 10 tick ごとに 4936 を書き戻すが、奇数 tick のたびに 0 へ戻される。

修正: `ArmorSetUnion` に第 3 層（`state.windowHeld` のときの `state.windowReal`）を追加。
ケース B（コスメだけがセット）は窓の中もコスメなので影響を受けず、ケース A だけが壊れていた
（= 症状が「コスメに何か入れると実装備が消える」に見えた理由）。

### 検証ハーネス

`/notcosmeticarmor cosmetic <feet|legs|chest|head> <item id|none>` を追加した。
`CarBridge.set` 経由で CAR のインベントリに書くので同期・保存も通常と同じ。これまでは
`.cosarmor` を直接書き換えるためにワールドを出る必要があった（1 組み合わせ = 2 分）。
このコマンドで 1 起動のうちに A/B を切り替えられる。

### 罠

- ワールド内で死ぬと **CAR のコスメ装備が落ちて `.cosarmor` が空になる**（39 B）。テスト前に
  `mkcos.py` のバックアップを取ること。
- 同じ DISPLAY で 2 クライアントを起動するとワールドがロックされ、片方を kill すると
  `xvfb-run` が Xvfb ごと落としてもう片方も道連れになる。起動前に
  `ps -eo pid,args | grep -E 'Xvfb|BootstrapLauncher'` で残存確認。
- 空中でのダブルタップは「1 回目の押下から 7 tick 以内・空中」が条件。テレポート直後は
  チャット欄にフォーカスが残っていることがあり、1 回目が吸われると成立しない
  （1 回の失敗を「飛べない」と誤読しかけた）。

### 残る制約

- 4 セット判定は実装備とコスメの**合算**になる。実装備 2 点 + コスメ 2 点でもセット成立
  （属性ミラーが両方を足すのと同じ方針）。実装備だけで組んだ場合の挙動は変わらない。
- `ArmorUtils#getArmorSet` のシグネチャが変わったら追従が要る（`/notcosmeticarmor status` の
  `is4` が false になったことで気づける）。

