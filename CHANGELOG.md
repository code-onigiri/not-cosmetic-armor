# Changelog

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
