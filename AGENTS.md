# AGENTS.md — このリポジトリで作業する AI エージェントへの申し送り

Minecraft 1.20.1 / Forge 47.4.28 の MOD（Cosmetic Armor Reworked のアドオン）。
挙動の検証は実クライアント（Xvfb + 本番 Forge）で行う。**起動して測るコストが非常に高い**ので、
以下の順序を守らないと時間の大半を待ち時間と空振りに使う。

初回（0.1.0 → 1.2.0）の実測: 作業 10.8 時間 / クライアント起動 45 回 / 1 回の起動に 140 秒
（タイトルまで）+ 20 秒（ワールド入場）/ コードは 21 ファイル 1,421 行。
つまり **「書く時間」より「確かめる時間」が 8 割**だった。

## 鉄則

### R1. 実機の前にソースを読む

対象 MOD が「何を根拠に装着を判定しているか」を jar の逆コンパイルで 1 メソッドまで特定してから
実機に触る。特定できれば修正は 20 行で終わる。当てずっぽうの実機試行は 1 回 4 分。

実例（このリポジトリで実際に特定した経路）:

- ProjectE の飛翔 / Step Assist → `PlayerHelper#checkArmorHotbarCurios` が `Inventory#armor` を直読み
- AdvancedAE の水中呼吸 → `AAELivingEntityEvents#breath` が `getItemBySlot` を読む（= こちらの経路に乗る）
- Sophisticated Backpacks のバックパック探索 → `PlayerInventoryProvider` の `"armor"` ハンドラが `player.getInventory().armor.get(...)` を直読み。ただし 3 経路（開くキー・背中の描画・メニュー生成）が `PlayerInventoryHandler#getStackInSlot` の 1 点に集約されていたので、注入は 1 箇所で済んだ
- ManaitaMTK の無敵 → ヘルメットの `inventoryTick` が `getItemBySlot` を見て capability を立てる
- バニラのダメージ軽減 → `LivingEntity` が `getArmorSlots`（`m_6168_`）のリストを回す

逆コンパイル手順は「検証ハーネス」の節を参照。

### R2. 1 起動で複数の疑問に答える

「修正 → ビルド → 起動 → 1 個測る」を繰り返さない。起動する前に**その回で答える質問を 3 つ以上**
書き出し、答えを全部ログに出す（カウンタ、レート制限ログ、`/notcosmeticarmor status` の拡張）。

実例: ProjectE で `calls/hits` の診断カウンタを足したことで「パッチは毎 tick 呼ばれ、毎回一致している」
が 1 回の起動で分かり、疑う場所を一気に絞れた。逆に、カウンタ無しで「効かない」とだけ見ていた
サイクルは 3〜4 回を空費した。

### R3. 数値はログで取る。スクショは最後の確認だけ

- 数値は `/data get entity @p ...`、状態は `/notcosmeticarmor status`。
- スクショ 1 枚は 1 ステップの往復。使うのは「見た目」の検証（描画・HUD・パケット漏れ）に限る。

### R4. 入力の自動化は作法を守る

- xdotool のキー押下 **12 ms はクライアントのサンプリングに拾われない**（ダブルタップ判定が成立しない）。
  **80 ms 押下**にする。ジャンプのダブルタップは「1 回目から 7 tick 以内・空中」が条件なので
  80 ms 押下 → 160 ms 待ち → 80 ms 押下。
- チャット送信は `Escape` ×2 → `t` → `type --delay 20-25` → `Return`。
  **コマンド送信直後の 1 秒はキーを送らない**（チャット欄が入力を吸う。`/tp` 直後にキーを送って
  1 サイクル無駄にした）。
- 理想はテスト手順をサーバー側 tick スケジューラで回し、xdotool 依存をなくすこと。

### R5. 計測手順そのものを先に疑う

- `peaceful` の自然回復は HP 差分を汚す。ダメージ測定は `hard` + `naturalRegeneration false` +
  `max_health 200` で行う。
- `/damage` の既定（`generic`）は armor を貫通し、**耐久も減らない**。装甲を見るときは
  `mob_attack`、貫通を見るときは `fall`。
- `/data get` の応答はチャット越しに遅れる。**経過時間と取り違えない**（これで「効いていない」と
  誤読したことがある）。時刻を記録して差分で語る。
- Air や泡のように `SynchedEntityData` で同期される値は、画面表示がそのままサーバー値。

### R6. 起動環境は決定版を使い回す

```sh
# ビルド（JDK 17 は flake の devShell が供給する）
export XDG_CACHE_HOME=/tmp/nix-cache-dsh
nix develop --command ./gradlew build --no-daemon -q

# クライアント（本番 Forge。userdev では CAR の SRG jar が動かない）
cd "/home/codeonigiri/source/Not Cosmetic Armor"
export XDG_CACHE_HOME=/tmp/nix-cache-dsh LIBGL_ALWAYS_SOFTWARE=1 XDG_RUNTIME_DIR=/tmp/xdgr
timeout 1800 nix develop --command xvfb-run -n 98 -s "-screen 0 1280x720x24" \
  bash /tmp/forge-test/launch-nca.sh > /tmp/nca-test/runNN.log 2>&1
```

- `--gameDir` は `/tmp/nca-test`（ユーザーの実インスタンス `~/.local/share/PrismLauncher/instances/NCA/minecraft`
  のコピー）。自前 jar は `/tmp/nca-test/mods/` にコピーしてから起動する。
- 画面遷移（DISPLAY=:98、1280x720）: シングルプレイ (640,356) → ワールド (500,264) →
  「選択したワールドで遊ぶ」(478,513)。ポーズ「ゲームに戻る」は (640,262)。
- スクショ / xdotool は **nix store のパスを直書きしない**（GC で消える。実際に消えた）。
  ヘルパー `/tmp/nca-test/act.sh` が `ls -d /nix/store/*imagemagick*/bin/import | head -1` のように glob で解決する:
  `act.sh shot out.png` / `act.sh chatt '/command'`（GUI が無い前提）/ `act.sh chat '/command'`（Escape ×2 で閉じてから）/ `act.sh key b` / `act.sh click 640 356`

### R7. 落ちたらログの探し場所を決める

- Forge のクラッシュは stdout で `... 34 more` に切れることがある。**stderr を別ファイルに**出す
  （`2> run.err`）。真因はたいていそこにしかない。
- mixin の失敗は `Critical injection failure` / `already redirected by` を grep。後者は
  他 MOD 同士の衝突で、こちらのせいではないことが多い。
- `ClassMetadataNotFoundException` は対象 MOD の依存不足（例: fantasy_ending には EndingLib が要る）。

### R7.5. 装備の「見え方」は 4 層ある

MOD が装備を判定する経路は 1 つではない。コスメ機能を入れると、同じ 1 スロットに

1. `getItemBySlot` / `getArmorSlots`（我々が上書きしている「見える層」）
2. `Inventory#armor`（実体。tick 窓の中ではコスメのコピーが入る）
3. tick 窓の外へ退避された実装備（`CosmeticState#windowReal`）
4. **コスメ枠そのもの**（`CosmeticStacks`。CAR の `CAStacksBase`）

が同時に存在する。片方だけ直すと「半分の tick だけ能力が消える」ような壊れ方をする。
`ArmorSetUnion` は 4 層すべてを見る。MOD 側の判定メソッドを 1 つ特定したら、
**そのメソッドが tick の内側で呼ばれるか**まで確認すること（`LivingTickEvent` は窓の中）。

1.5.0 で `RealPriority`（実装備優先の名前空間）を入れてから、1 層目の意味が
**「コスメ」から「実装備（対象名前空間のみ）」に変わりうる**。第 4 層が無いと、
コスメの装備がどの層にも現れずセット判定が落ちる（実測: `is4=false shield[level=0]`）。
**query の意味を変えたら、その query を読んでいる互換パッチを全部洗う**こと。

2 層目（`Inventory#armor`）を直読みする MOD は、この 4 層のどこにも乗らないので個別パッチが要る
（ProjectE の飛翔 / Step Assist、SB のバックパック）。パッチを書く前に、**その MOD の読み取りが
1 メソッドに集約されているか**を必ず確認する。SB は `PlayerInventoryHandler#getStackInSlot` に
集約されていたので 1 箇所で 3 経路ぶん直ったが、集約されていなければ差し込みが増えて保守できない
（その場合は「制約」として docs に書くほうが安い）。

### R8. 終わったら docs に残す

- 時系列の知見は `docs/plan-review.md` に § を足す（現象 → 原因 → 修正 → 実測 → 残る制約）。
- `README.md` は利用者向け（機能・互換表・検証ログ・制限）、`CHANGELOG.md` は変更点。
- バージョンは `gradle.properties` の `mod_version`。未検証の挙動を「動く」と書かない
  （書くなら「実機未検証」と明記する）。

## やらかし集（同じことをしない）

| やったこと | 何が起きたか | 正解 |
|---|---|---|
| 開発ランタイム（`runClient`）に CAR の本番 jar を置いた | `NoSuchMethodError: Component.m_237113_`。jar は SRG 名でコンパイルされており userdev では解決できない | 本番 Forge クライアントで検証する |
| PrismLauncher 11 でオフライン起動 | Microsoft アカウント必須のダイアログで停止 | 起動コマンドを自前生成（`/tmp/forge-test/gen.py`） |
| classpath にバニラ jar と Forge client jar を両方入れた | `module.ResolutionException`（`net.minecraft` の二重） | どちらか一方にする |
| `pkill -f prismlauncher` を自分のコマンドと同じ行で実行 | 自分のコマンドラインに一致して自滅 | `pkill -f 'prismlaunch[e]r'` かジョブ管理 API を使う |
| TS のテンプレートリテラルに `${...}` をそのまま書いた | 補間されて `ReferenceError` | `${...}` は `\${...}` とエスケープする |
| 12 ms のキー押下でダブルタップ | サンプリングに拾われず飛ばない（原因究明に 2 サイクル） | 80 ms 押下 |
| 効果が無いと判断して修正を重ねた | 実際は計測側の問題（peaceful の回復、`/damage` の既定値、チャット遅延） | 計測手順を先に検証する |
| コスメが無い状態だけで「動く」と確認して終えた | コスメを 1 枚載せた瞬間に実装備のセット能力が消える不具合を見逃した | コスメあり/なし × 実装備あり/なしの 2×2 を測る |
| `/data get` の `abilities.mayfly` が 1b だけで「飛べる」と判断 | サーバーだけ true でクライアントは false。実際には飛べなかった | 能力はサーバー値と実挙動（Y の変化）を両方見る |
| テレポート直後にダブルタップを送った | チャット欄が 1 回目の押下を吸い、飛ばないと誤読 | 送信後 1 秒待ち、クリックでフォーカスを戻してから押す |
| GUI を開いたまま `Escape` ×2 のチャット送信ヘルパーを使った | 1 回目で GUI が閉じ、2 回目でポーズメニューが開き、最後の Return が「セーブしてタイトルへ戻る」を押してワールドから退出した | GUI を閉じるときは Escape 1 回。コマンドは GUI が無い状態で送る（`t` だけのヘルパーを分ける） |
| `/data modify entity @p SelectedItem.tag...` で手に持った装備の NBT を書いた | 「変更しました」と出るのに反映されない（`SelectedItem` は読み取り専用） | `Inventory[{Slot:0b}]`（ホットバー 1）に対して書く |
| `/item replace ... with <id>{NBT}` で複雑な NBT を渡した | チャットに「「}」が必要です」。パースに失敗する | 素のアイテムを入れてから `/data modify` で NBT を足す |
| `RealPriority` で query の意味を変えたのに、それを読む互換パッチを数えなかった | `ArmorSetUnion` が 3 層前提で、実装備メカスーツが勝った瞬間にコスメの夢幻終焉がどの層にも出ずセットが消えた | 層を増やす/意味を変えるときは、その query の読み手を全部 grep する |

## 検証ハーネス

### 逆コンパイル

```sh
java -jar /tmp/cae/vineflower.jar -dgs=1 mod.jar /tmp/out    # 対象 MOD のソース
# 1.20.1 のバニラ（official 名 + srg メンバ名）
#   ~/.gradle/caches/forge_gradle/mcp_repo/.../joined/rename/output.jar
```

### CAR のコスメ枠を直接書き換える（テスト用の装備投入）

```sh
python3 /tmp/cae/mkcos.py '{"0":{"id":"minecraft:golden_boots","enchant":[[2,4]]}}' \
  "<world>/playerdata/<uuid>.cosarmor" 11
```

`.cosarmor` は非圧縮 NBT。`Size:int` / `Hidden:string` / `Items:List<Compound>`（`Slot:int` + ItemStack）。
エンチャント ID は 1.20.1 の registry 順（protection=0, feather_falling=2, respiration=5, ...）。
**ワールドを閉じてから書き換える**こと（開いている間の変更は上書きされる）。

### コスメ枠にアイテムを入れる（ワールドを出ずに切り替える）

```
/notcosmeticarmor cosmetic chest fantasy_ending:fantasy_ending_chestplate
/notcosmeticarmor cosmetic chest none
```

CAR のインベントリ経由で書くので同期・保存も通常の編集と同じ。`.cosarmor` を直接書き換える方法は
**ワールドを閉じているときしか使えない**（開いている間の変更は退出時に上書きされる）。

### NBT 付きの装備をコスメ枠に入れる

`/notcosmeticarmor cosmetic <slot> hand` はメインハンドのスタックを NBT ごとコピーする。
アフィックス・注入呪文・アップグレードオーブ・モジュール付き装備はこれで入れる（アイテム id 指定では NBT が落ちる）。

### 状態確認

```
/notcosmeticarmor status
```

CAR 検出、モード、各スロットの実装備/コスメ、modifier 数、防御力、`projectE: patch/calls/hits`、
`stepHeight` を 1 画面で出す。まずこれを読む。
各スロットの `query=` は、そのスロットで `getItemBySlot` が返すのが実装備（`real`）か
コスメ（`cosmetic`）か。`realPriorityNamespaces` の MOD を実装備に入れると `real` になる。
