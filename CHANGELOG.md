# Changelog

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
