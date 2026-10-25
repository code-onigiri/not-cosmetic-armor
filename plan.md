# Cosmetic Armor Effects — 実装計画（Mixin 前提）

コスメティック枠（Cosmetic Armor Reworked）に入れた防具の効果を、実装備の効果と**同時に**発動させるアドオンの設計・タスク分解。

- 仮称: `cosmeticarmoreffects`（ModID 候補: `cosarmoreffects`）
- 一次ターゲット: **Minecraft 1.20.1 / Forge 47.x / Java 17**
- 依存: Cosmetic Armor Reworked 4.x（`lain.mods.cos`）
- 配布形態: client + server 両必須（クライアント側の判定・HUD を読む Mod があるため）

> 1.20.5 以降・1.21.2 以降・Fabric への差分は §10 にまとめた。ターゲットが違う場合はそこを差し替える。

---

## 1. スコープ

### やること
- コスメ枠の防具を「実装備と同等」に扱い、**実枠の防具と同時に**効果を発動させる
- 対象は任意の Mod の防具（標準 API 準拠のもの）
- モード切替（無効 / 属性のみ / 時分割 / 優先固定）

### やらないこと
- コスメ枠の描画（CAR の責務。干渉しない）
- スロットの追加（CAR の 4 枠をそのまま使う）
- `EquipmentSlot` を増やす（バニラの同期・描画・属性系が総崩れするため不可）

---

## 2. 設計原則（破ってはいけない不変条件）

実装中の判断はすべてこの 3 つで決める。

**I1. 交換ウィンドウは tick 内で閉じる。**
スタックを実スロットへ差し替えたら、**同じ tick のうちに必ず復元する**。復元を跨ぐと
`ClientboundSetEquipmentPacket`（`ServerEntity#sendChanges` は entity tick の後に走る）に
偽の中身が載り、クライアントと恒久的にズレる。`try/finally` 必須。

**I2. 交換は「見せかけ」であり、装備変更として通知しない。**
`collectEquipmentChanges` を窓の中で抑止する。これをやらないと Mod の `onEquipItem` が
毎tick 発火して capability が attach/detach を繰り返し、状態が壊れる。
本物の変更（コスメ枠の中身が実際に変わった時）だけ、別経路で 1 回通知する。

**I3. 数値（属性）は交換に頼らず、常時加算で持つ。**
`Attributes.ARMOR` 等は `AttributeModifier` を UUID 分離して常設する。tick の位相に
依存させない。これが「同時に効く」の土台。

---

## 3. アーキテクチャ（4 層）

```
┌─────────────────────────────────────────────┐
│ L4 Presentation   CAR の描画に任せる（干渉なし） │
├─────────────────────────────────────────────┤
│ L3 Query          窓外の読み取りを合成/固定      │
│                   Mixin: getItemBySlot ほか     │
├─────────────────────────────────────────────┤
│ L2 Tick Window    実スロットを時分割で貸す       │
│                   Mixin: Player#tick ほか       │
├─────────────────────────────────────────────┤
│ L1 Attribute      常設 AttributeModifier        │
│                   （API のみ、Mixin 不要）       │
└─────────────────────────────────────────────┘
```

### L1: Attribute（常時・同時）
コスメ枠 4 つの `ItemStack#getAttributeModifiers(EquipmentSlot)` を集約し、
**決定的 UUID**（`UUID.nameUUIDFromBytes(playerUUID + slot + "cosarmor")`）で
`AttributeInstance#addModifier` する。差分更新は「コスメ枠の内容ハッシュが変わった時」だけ。

- 実枠の防具はバニラが別 UUID で持つので**衝突しない**
- 交換窓の中では装備変更を抑止しているので、**二重計上しない**
- 対象属性: `ARMOR` / `ARMOR_TOUGHNESS` / `KNOCKBACK_RESISTANCE` / `MOVEMENT_SPEED` /
  `ATTACK_DAMAGE` / `MAX_HEALTH` / `LUCK` など、Mod 防具が触るもの全部

### L2: Tick Window（時分割）
`EquipmentSlot` は単一値なので、**tick を交互に割り当てる**。

- 偶数 tick: 実枠の防具が載っている
- 奇数 tick: コスメ枠の防具が載っている

判定は `level.getGameTime() & 1`（サーバー・クライアントで同じ値が取れる＝同期不要）。

なぜ「同一 tick 内で 2 周」より優れるか:

| 観点 | 同一 tick 内 2 周 | tick 交互割当 |
|---|---|---|
| Mod の `PlayerTickEvent` ハンドラ | **1 回しか呼ばれない**（呼び出し側を所有していないので再実行不能） | 各 tick で 1 回ずつ、両方に順番が回る |
| `setDeltaMovement` の競合 | 後勝ち | 各 tick で自分の値を出せる |
| エネルギー・耐久の消費 | 2 倍速 | **各半分**（合計は 1 個分と同じ） |
| 実装難度 | 低 | 中 |

> 補足: 同一 tick 内で 2 周させても、他 Mod のイベントハンドラは 1 回しか走らないので
> 「2 つ装備」にはならない。時分割はその制約を回避する唯一の汎用解。

### L3: Query（窓外の読み取り）
ダメージ処理・キー入力・GUI など、tick 窓の外で起きる読み取りは位相が読めない。
ここは **優先側を固定**（デフォルト: コスメ優先）し、必要なら OR 合成する。

- `getItemBySlot` に `@Inject(at = RETURN, cancellable = true)` を当て、
  `CAE_STATE.queryOverride` が有効な時だけコスメスタックを返す
- **グローバルに嘘をつかない**（常時書き換えると同期・描画・ドロップが壊れる）。
  コンテキストフラグで「合成評価中だけ」に限定する
- 読み取り専用が確定している判定（飛行可否・ピグリン無害化・水中呼吸の有無）は
  `OR` 合成で両立させる

---

## 4. 動作モード

| モード | 中身 | 用途 |
|---|---|---|
| `OFF` | CAR 素の動作 | 切り分け用 |
| `ATTRIBUTE` | L1 のみ | 防御力だけ両取り。最も安全 |
| `PRIORITY` | L1 + L3（コスメ優先固定） | コスメ側を完全装備として扱う |
| `INTERLEAVE` | L1 + L2 + L3 | **本命**。両方の tick ロジックが動く |
| `INTERLEAVE_STRICT` | INTERLEAVE + 交換中の全イベント抑止 + 合成通知 | 状態機械系 Mod 向け（実験的） |

コンフィグは `ForgeConfigSpec`（`common`）に置き、スロット単位・ModID 単位の除外リストを持たせる。

---

## 5. Mixin 対象一覧

`injectors.defaultRequire = 1` にして、バージョンがズレたら起動時に落とす。

| # | 対象 | 目的 | 確度 | 備考 |
|---|---|---|---|---|
| M1 | `LivingEntity#setItemSlot(EquipmentSlot, ItemStack)` | 窓内の差し替え | 確定 | Forge の `onEquipItem` 呼び出しは別 |
| M2 | `LivingEntity#collectEquipmentChanges()` (private) | **窓内で抑止**（I2） | 高 | HEAD で cancel |
| M3 | `LivingEntity#onEquipItem(...)` | 合成通知のフック点 | 高 | 本物の変更時のみ 1 回 |
| M4 | `Player#tick()` | 窓の開閉（HEAD/TAIL） | 確定 | `try/finally` |
| M5 | `LocalPlayer#tick()` (client) | クライアント側の同位相窓 | 確定 | 位相は gameTime |
| M6 | `Entity#setDeltaMovement(Vec3)` | 窓内の代入を捕獲・合成 | 確定 | 競合解決ポリシー要 |
| M7 | `LivingEntity#addEffect(MobEffectInstance)` | 効果の和集合合成 | 確定 | 省略可（バニラの merge で足りる場合あり） |
| M8 | `LivingEntity#getItemBySlot(EquipmentSlot)` | 窓外クエリの上書き | 確定 | コンテキスト限定 |
| M9 | `Player#hurtArmor(...)` | 耐久の二重計上防止・経路固定 | 高 | 窓内は自然に片方に落ちる |
| M10 | `ServerEntity#sendChanges()` | 保険（窓が閉じていない時の抑止） | 高 | 異常時のみ |
| M11 | `HumanoidArmorLayer#renderArmorPiece` (client) | 二重描画の検出 | 中 | 描画は窓外なので基本不要 |
| M12 | `Item#inventoryTick(...)` の呼び出し元 | 窓内でアイテム tick を通す | 中 | `Inventory#tick` / `Player#tick` 要確認 |
| M13 | Forge `IItemExtension#onArmorTick` の呼び出し元 | 同上 | 中 | 1.20.1 の呼び出し位置を要調査 |

**Mixin の作法**
- `@Redirect` は避け、`@Inject(at = @At("RETURN"), cancellable = true)` を基本にする
- `@Overwrite` は使わない
- 対象クラスが存在しない環境では `@Pseudo` + `require = 0` で縮退（ただし M1〜M4 は必須なので落とす）

---

## 6. 中核クラス設計

```
com.example.cosarmoreffects
├── CosmeticArmorEffects        … Mod エントリ、Config 登録
├── config/Config               … モード・除外リスト
├── api/CarBridge               … CAR API のラッパ（リフレクション不要、compileOnly）
├── core/SwapContext            … 窓の状態（プレイヤーごと、サーバースレッド前提）
│   ├── begin(Player, Pass)
│   ├── end(Player)             … finally で必ず呼ぶ
│   └── isActive(Player)
├── core/Pass                   … REAL / COSMETIC
├── core/SlotPair               … (EquipmentSlot, 実スタック, コスメスタック) の対応
├── core/AttributeMirror        … L1。UUID 生成・差分適用・除去
├── core/MutationCapture        … setDeltaMovement / addEffect / フラグの捕獲と合成
│   ├── capture(Predicate)
│   └── flush()                 … 合成結果を 1 回だけ適用
├── core/EquipNotifier          … 本物の変更時に 1 回だけ合成イベントを投げる
├── mixin/*                     … §5 の Mixin
└── util/Uuids                  … nameUUIDFromBytes ベースの決定的 UUID
```

### tick 内シーケンス（INTERLEAVE）

```
Player#tick HEAD
  ├─ pass = (gameTime & 1) == 0 ? REAL : COSMETIC
  ├─ if pass == COSMETIC: SwapContext.begin()  … 4 スロットをコスメに差し替え
  ├─        MutationCapture.start()
  │
  │  （ここでバニラ + 全 Mod の tick / イベントハンドラが走る）
  │
Player#tick TAIL (finally)
  ├─ captured = MutationCapture.stop()
  ├─ SwapContext.end()          … 4 スロットを復元（I1）
  ├─ MutationCapture.flush(captured)  … 合成して 1 回適用
  └─ AttributeMirror.refreshIfDirty()
```

### 競合解決ポリシー（`MutationCapture`）

| チャネル | 合成則 | 理由 |
|---|---|---|
| `setDeltaMovement` | **各パスの値をそのまま適用**（窓が排他なので競合しない） | 時分割なら自然に解決 |
| ポーション効果 | `amplifier` 最大 / `duration` 最大 | 和集合 |
| ブールフラグ（飛行可否など） | `OR` | 片方でも真なら真 |
| ダメージ倍率 | `max`（合算しない） | 無限インフレ防止 |
| クールダウン | 各 Mod の値を尊重（干渉しない） | — |

---

## 7. 落とし穴と対策

| 落とし穴 | 症状 | 対策 |
|---|---|---|
| 窓の閉じ忘れ | 装備が入れ替わったままセーブ／同期 | `try/finally` + `PlayerLoggedOutEvent` で全スロット復元 + 起動時検査 |
| 装備変更イベントの多重発火 | capability の attach/detach 地獄 | M2 で抑止、M3 で本物の変更時のみ 1 回 |
| 属性の二重計上 | 防御力が跳ね上がる | 窓内は装備変更抑止済み＝バニラは属性を読み直さない。L1 の modifier のみが効く |
| 耐久の取り合い | 片方だけ減る | 時分割で自然に半々。`hurtArmor` は窓内のスタックに落ちる |
| クライアント位相ズレ | HUD がチカチカ | HUD/描画は「常にコスメ」に固定（L4 は CAR 任せ、HUD は M8 のコンテキストを使わない） |
| 死亡・ログアウト中の窓 | アイテム増殖/消失 | tick 外では必ず復元済み。セーブは tick 境界なので理論上安全。念のため M10 |
| Mod のキャッシュ | 装備時計算の Mod が無反応 | `INTERLEAVE_STRICT` で合成通知。ダメなら該当 Mod を個別対応 |
| `keepInventory` / ドロップ | 実スタックは動かないので影響なし | — |
| 再入 | 別 Mod が tick 中にプレイヤーを tick させる | `SwapContext` を再入カウンタで管理し、内側は no-op |
| スレッド | ワールド別スレッド（MOD によっては） | `SwapContext` はプレイヤー単位 + スレッド確認アサート |

---

## 8. 実装前の事実確認（T-01、半日〜1日）

コードを書く前に jar を落として以下を確定させる。ここを飛ばすと Mixin の当て先が全部ずれる。

1. `Cosmetic Armor Reworked` の API 実体
   - `lain.mods.cos.api.CosArmorAPI` の公開メソッド
   - `IModCosArmor` の `getStackInSlot` / `setStackInSlot` / `isSkinArmor` 等のシグネチャ
   - スロット番号と `EquipmentSlot` の対応表
2. CAR の描画経路が「自前インベントリ参照」か「`getItemBySlot` 参照」か
   - 前者なら L4 は完全に無干渉（望ましい）
3. 1.20.1 の**防具属性の適用経路**
   - `collectEquipmentChanges` / `onEquipItem` / `AttributeMap` のどこで反映されるか
   - `Player` が `equipment` フィールドと `Inventory#armor` のどちらを正とするか
4. Forge が `onArmorTick` / `inventoryTick` をどこから呼ぶか（M12/M13 の当て先）
5. `ServerEntity#sendChanges` の実行タイミング（entity tick の後か）

成果物: `docs/verified-anchors.md` に「クラス#メソッド（Mojmap） / 行 / 根拠」を列挙。

---

## 9. タスク分解と工数

| ID | タスク | 内容 | 工数 |
|---|---|---|---|
| T-01 | 事実確認 | §8。アンカー確定 | 1.0d |
| T-02 | プロジェクト雛形 | ForgeGradle 6 / mixin config / refmap / CI | 1.0d |
| T-03 | CAR ブリッジ | 4 枠の読み書き、スロット対応、変更検知 | 1.0d |
| T-04 | L1 AttributeMirror | UUID 生成、差分適用、除去、コンフィグ | 1.5d |
| T-05 | SwapContext + M1/M4 | 窓の開閉、`try/finally`、復元保証 | 1.5d |
| T-06 | M2/M3 イベント抑止・合成通知 | 抑止フラグ、本物変更時の 1 回通知 | 2.0d |
| T-07 | MutationCapture + M6/M7 | 代入の捕獲、合成則、flush | 2.0d |
| T-08 | 時分割（L2） | 位相判定、クライアント同位相（M5） | 1.5d |
| T-09 | Query 層（M8） | コンテキスト限定の上書き、OR 合成 | 1.5d |
| T-10 | 耐久・死亡・ログアウト（M9/M10） | 経路固定、復元スイープ | 1.5d |
| T-11 | コンフィグ・除外リスト | スロット/ModID 単位 | 1.0d |
| T-12 | 検証（§11） | 4 分類 × 代表 Mod | 3.0d |
| T-13 | ドキュメント | 既知の制限、対応表 | 0.5d |
| | **合計** | | **19d（約 4 週間）** |

短縮案: `ATTRIBUTE` モードのみなら **T-01〜T-04 + T-11 = 5d**。ここで一度リリースして価値を確認するのが妥当。

---

## 9.5 マイルストーン

- **M0（1d）**: アンカー確定。ここで「無理」なら撤退判断
- **M1（5d）**: `ATTRIBUTE` モード動作。防御力の両取りが成立
- **M2（10d）**: `INTERLEAVE` で 1 Mod の防具が両方効く（PoC）
- **M3（15d）**: 4 分類の代表 Mod で検証通過
- **M4（19d）**: ドキュメント・既知制限の明文化

---

## 10. バージョン差分

| 環境 | 差分 |
|---|---|
| 1.20.1 Forge | 本書の前提。`equipment` ベース |
| 1.20.5+ | 属性収集が `collectEquipmentAttributes` に集約。M2 の代わりにここをフックできる可能性 |
| 1.21.2+ | `AttributeModifier` が `ResourceLocation` ID 化。UUID 生成を差し替え。装備スロット体系も変更 |
| 1.21.5+ | `AttributeModifier` の ID が `Identifier` 必須。`Item#inventoryTick` 廃止系あり |
| Fabric | `LivingEquipmentChangeEvent` 相当が無いので M3 の代替が必要。`ServerPlayNetworkHandler` の同期も別経路 |

**方針: 1 バージョンに固定して完成させる。** 追従は各 +1 週間。

---

## 11. 検証計画

### テスト用 Mod の 4 分類（各 1 つ以上）

| 分類 | 例 | 確認点 |
|---|---|---|
| 属性型 | Apotheosis 系の防具 | 防御力が加算される／二重計上しない |
| capability 型 | MekaSuit 系 | エネルギーが破綻しない／attach の thrash が無い |
| tick 型 | ジェットパック・水中呼吸系 | 両方の推力が出る／効果が切れない |
| イベントキャッシュ型 | セットボーナス系 | 装備変更が伝わる／外した時に解除される |

### チェックリスト
- [ ] 実枠のみ／コスメ枠のみ／両方の 3 パターンで数値が期待通り
- [ ] 窓の開閉ミスが無い（1000 tick 回して装備が元に戻る）
- [ ] 死亡 → リスポーン → ログアウト → 再ログインで増殖・消失なし
- [ ] ディメンション移動で状態が残らない
- [ ] クライアントとサーバーで `equipment` が一致
- [ ] 他プレイヤーから見た見た目が常にコスメ
- [ ] `INTERLEAVE` で HUD がチカチカしない
- [ ] 属性が 2 重に乗っていない（`/attribute` で実測）

---

## 12. リスクと撤退条件

| リスク | 影響 | 撤退条件 |
|---|---|---|
| M2 の抑止が効かない（Mod が独自経路で装備変更を検知） | 状態破壊 | M0 で確認。効かなければ `ATTRIBUTE` モードに縮退 |
| capability 型 Mod が毎tick 再初期化 | 使用不能 | 該当 Mod を除外リストへ |
| クライアント位相ズレが解消不能 | HUD 不具合 | 描画/HUD を完全に固定して回避 |
| CAR の描画が `getItemBySlot` 依存 | 二重描画 | M11 で描画を止める |
| バージョン追従コスト | 保守不能 | 単一バージョン専用と明記して配布 |

**撤退ラインは M0（アンカー確定）と M2（PoC）の 2 点。** ここで無理なら `ATTRIBUTE` モード単体で出す。

---

## 13. 未確定事項（要回答）

1. 対象 MC バージョン・ローダー（本書は 1.20.1 Forge 前提）
2. PoC で試す装備ペア 1 組（Mod 名まで）
3. 配布範囲（個人用 / 公開）
4. `ATTRIBUTE` モードのみで一度出すか、`INTERLEAVE` まで一気に作るか

