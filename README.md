# Not Cosmetic Armor

Cosmetic Armor Reworked のコスメ装備を、**見た目だけ**から**実際に機能する装備**に変える 1.20.1 Forge 用アドオンです。

コスメ枠に防具を入れると、その防具の防御力・防具強度・ノックバック耐性などの属性と、
Protection / Thorns / Frost Walker などのエンチャントが有効になります。
実装備（本物の防具枠）はそのまま「本物」として動き続けます。

## 動作環境

| 項目 | バージョン |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x（開発・検証は 47.4.28） |
| Java | 17 |
| 必須 | [Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) 4.x（1.20.1-v1a で検証） |

CAR が無い場合、この MOD は何もせず待機します（クラッシュしません）。

## 何が機能するか

- **属性**: コスメ防具の attribute modifier（防御力、防具強度、ノックバック耐性、移動速度など）を実装備の上に加算。UUID はプレイヤー・スロットごとに再生成するので、実装備の modifier と衝突しません。
- **エンチャント**: `getItemBySlot` を上書きしてコスメ装備を「着ている防具」として返すため、バニラのダメージ計算（Protection など）や他 MOD の参照にもコスメ防具が見えます。
- **tick 内の参照（インタリーブ）**: 奇数ゲーム tick の `Player#tick` の間だけ、コスメ装備を実装備枠に**コピー**して入れ、tick の終わりに元へ戻します。鞘翅の滑翔判定、カメの甲羅の水中呼吸、MOD の「この装備を着ているか」判定など、tick の内側で `getItemBySlot` を読む処理にもコスメ装備が見えます。クライアントには何も送られず、実装備は一切失われません。
- **item tick**: コスメ枠のアイテムにも `inventoryTick` / Forge の `onArmorTick` が走ります。エネルギーやチャージを持つ MOD 防具（TiC など）の動作に必要です。

## 何が機能しないか（意図的）

- **耐久値**: ダメージは実装備（本物の防具）にのみ入ります。コスメ装備は減りません。見た目だけの装備に耐久の概念を持ち込むと、修理・破壊・複製の扱いが破綻するためです。
- **描画**: 見た目の差し替えは従来どおり CAR が担当します。本 MOD は描画コードに触りません（[描画（L4）](#描画l4)）。
- **実装備の tick**: コスメの下に隠れている実装備も、これまで通り tick されます（バニラの挙動を変えない方針）。

## 設定

`config/notcosmeticarmor-server.toml`（サーバー側）:

| キー | 既定 | 説明 |
|---|---|---|
| `general.enabled` | true | MOD 全体の有効/無効 |
| `behavior.mirrorAttributes` | true | コスメ防具の属性を実装備に加算する |
| `behavior.overrideQueries` | true | `getItemBySlot` でコスメ防具を返す（エンチャント有効化） |
| `behavior.interleave` | true | 奇数 tick の間だけコスメ装備を実装備枠にコピーする（tick 内参照の有効化） |
| `behavior.tickCosmeticItems` | true | コスメ枠のアイテムを tick する |
| `behavior.ignoreSkinArmorFlag` | true | CAR の「skin armor」フラグが立ったスロットも機能させる |

## コマンド

```
/notcosmeticarmor status   # 状態表示
/notcosmeticarmor probe    # tick 窓の内外で getItemBySlot が何を返すかをログに出す（デバッグ）
```

権限レベル 2 以上。`status` は CAR の検出状況、実効モード（OFF / ATTRIBUTE / PRIORITY / INTERLEAVE）、
設定値、各スロットの実装備/コスメ装備、適用中の modifier 数、現在の防御力、差分ガードと tick 窓の
状態を表示します。`probe` を有効にすると、数秒おきに次のようなログが出ます:

```
probe(inside window): chest seen=1 golden_chestplate storedReal=1 diamond_chestplate armorValue=13
probe(outside window): chest seen=1 diamond_chestplate armorValue=13
```

## ビルド

Nix を使う場合（JDK 17 とクライアント実行用ライブラリが揃います）:

```sh
nix develop
./gradlew build
# => build/libs/notcosmeticarmor-1.20.1-<version>.jar
```

Nix を使わない場合は JDK 17 を用意して `./gradlew build` を実行してください
（Gradle 8.8 は wrapper が取得します）。

開発用クライアントは `run/mods/` に CAR の jar を置いてから:

```sh
./gradlew runClient
```

## 仕組み

1. **属性ミラー** — 毎 tick、コスメ枠の `getAttributeModifiers` を読み、UUID を再生成した
   transient modifier として `AttributeMap` に追加/削除する。クエリ上書きとは独立に動く。
2. **クエリ上書き** — `Player#getItemBySlot` の RETURN で、防具スロットかつサーバー側のときだけ
   コスメスタックを返す。実体（`Inventory#armor`）は一切書き換えない。
3. **差分ガード** — バニラは `LivingEntity#tick` の中で毎 tick 装備の差分を取り、
   変化があれば属性を付け替えて `ClientboundSetEquipmentPacket` を送る。その差分処理の間だけ
   上書きを止め、バニラには常に実装備を見せる。これがないと毎 tick パケットが飛び、属性が壊れる。
4. **tick 窓（インタリーブ）** — 奇数 tick の `Player#tick` の間だけ、コスメの**コピー**を
   `Inventory#armor` に入れて実装備を退避する。窓の中ではバニラの装備差分処理自体を
   `detectEquipmentUpdates` の HEAD でキャンセルするので、属性の二重計上もパケット送信も起きない。
   窓は tick の終わり（例外時は次 tick の頭）に必ず閉じ、実装備を書き戻す。
   コピーを入れているので、窓の中で付いた耐久値は捨てられ、CAR が持つスタックは変化しない。

モードは設定の組み合わせで決まります: `enabled=false` が OFF、`mirrorAttributes` のみで ATTRIBUTE、
`+overrideQueries` で PRIORITY、`+interleave` で INTERLEAVE（既定）。

設計検討の詳細は [docs/plan-review.md](docs/plan-review.md) を参照。

## 描画（L4）

見た目の差し替えは CAR の担当です。本 MOD は描画コードを持たず、mixin はクライアント側で
何も変更しません（`PlayerMixin` は `isClientSide()` で早期 return、`LivingEntityMixin` は
`ServerPlayer` のみ対象）。

CAR は `RenderPlayerEvent.Pre` でクライアントの `Inventory#armor` をコスメに入れ替え、
`Post` で元に戻します。入れ替え元は CAR 自身のインベントリ（`InventoryCosArmor`）で、
`getItemBySlot` は経由しません。したがってこの MOD のクエリ上書きとはぶつかりません。
二重描画を止めるための `HumanoidArmorLayer#renderArmorPiece` の mixin（計画書の M11）は
不要と判断し、入れていません。

実機での確認（同じ視点で連続キャプチャし、ピクセル比較）:

| 見る場所 | 結果 |
|---|---|
| HUD の防具バー | 12 フレームすべてピクセル一致（8 と 13 の間でチカチカしない） |
| インベントリの防具スロット | 8 フレームすべてピクセル一致（ダイヤのまま。コスメが漏れていない） |
| 三人称のモデル | 常に金胸当て（実装備のダイヤと二重に描かれない） |

![presentation layers](docs/img/presentation-layers.png)

インベントリの防具スロットは実装備（ダイヤ）、中央のモデルはコスメ（金）。層が分かれているのが
そのまま見えます。防具バーの 13 は実装備 8 + コスメ 5 です。

## 検証ログ（実機）

1.20.1 Forge 47.4.28 + CAR 1.20.1-v1a、実装備にダイヤ胸当て・コスメ枠に金胸当て:

![status](docs/img/status-in-game.png)

![third person](docs/img/third-person.png)

armor は 8 + 5 = 13、窓の内外で `getItemBySlot` が入れ替わり、防御力は 13 のまま（二重計上なし）。

## ライセンス

MIT
