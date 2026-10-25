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
