# cobblemon-x-create batch-003 审查报告
- pack: cobblemon-x-create
- 批次: batch-003（written=126：incomplete 125 + medium/complete 1；coverage 1172/15695=7.5%）
- 审查链数: 126（incomplete 125 / complete 1；全量过链头+flags+crafts+rule-tags，深度细读 0041/0095/0026/0035/0097 等 ~12 条巨型/colossal 链正文，其余按头信息+抽样证据判定）
- 发现: critical 0 / major 3 / minor 1

## 读法与总体结论
- 全批 `crafts` 失控极普遍：67/126 条 crafts≥1e6，61/126 条≥1e8；多条不同目标出现**完全相同**的巨型 crafts（如 `37573774392` 同时是 0074 moon_rod 与 0036 ancient_ultra_rod；`37573774310` 同时是 0045/0076/0092/0110 四条 rod；`1982451xxx` 是 0095/0096/0098/0104/0108/0109 六个 pokefinder；`496126794` 是 0038/0044/0117 三条 rod；`296451074` 是 0026-0029+0054；`294685314` 是 0062-0065）。这是"同一巨大共享子结构 + 极小目标尾"的签名。
- 该共享子结构的根因是**已知规划器级问题**（按 round-3 增补口径，仅在链级判定一句话提及、不展开为发现）：
  1. `|+N` 物品 id 后缀污染：`create:cut_diorite_stairs|+14`、`create:crimsite_pillar|+14`、`create:small_andesite_bricks|+14`、`create:cut_tuff_wall|+14`、`cobblemon:apricorn_planks|+15`、`minecraft:flowing_water|+1`/`lava|+1`/`stick|+1` 等被当作真实输入喂入 stonecutter/exchange 树与 cobblestone_gen（如 CHAIN 0041 A1-A49 diorite 树、CHAIN 0095 A1-A34 crimsite 树、CHAIN 0026 A181）。
  2. 32 位溢出哨兵：`deeperdarker:coal_from_blasting_gloomslate_coal_ore x2147483647`（=2³¹−1）在全批反复出现，级联进 billion 级 `minecraft:coal_block` 阶梯（CHAIN 0041 A56/A61/A66/A71/A76 等），是 crafts 最大单项贡献者。
  3. 原版常见物预算截断：大量 `UNRESOLVED ... reason=BUDGET_TRUNCATED` 实为可解析物（`create:large_cogwheel`、`create:propeller`、`create:brass_sheet`、`create:encased_fan`、`create:cogwheel`、`create:brass_ingot`、`create:iron_sheet`、`minecraft:iron_ingot` 等）。
- 上述三者为 round-1/2 已确认加载的已知规划器问题，本批不再作为发现。以下为**未被任何标签覆盖**的新发现。

## 发现

### F-cobblemon-1 | chain=0002,0008,0010,0013,0047,0051,0056,0070,0094,0101 (等多条) | severity=major | category=amount-insanity
- 位置: `create:crafting/materials/raw_zinc_block` ↔ `create:raw_zinc` 压缩阶梯（例 CHAIN 0002 A94 `EXECUTE create:crafting/materials/raw_zinc_block x43046721` from `create:raw_zinc x387420489`；A95 `raw_zinc x43046721` from `raw_zinc_block x43046721` 1:1；A96 `raw_zinc_block x4782969`…），全链无任何 `[rule-tag]`。
- 问题: 数量呈 3 的幂次放大——`43046721=3¹⁶`、`387420489=3¹⁸`、`4782969=3¹⁴`、`531441=3¹²`。该阶梯未被 pattern/storage_ladder 命中（id 在 `create:crafting/materials/` 命名空间，非标准 `raw_zinc_block`），导致单步就向 crafts 注入 4.3e7，是无净产出的指数压缩往返。
- 原因: 分类器对"非标准命名空间的 9:1 矿石块↔原矿"阶梯漏标；cone 预算在其上做 3 向扇出，crafts 失控。
- 建议: 将 `create:crafting/materials/raw_zinc_block|raw_zinc` 纳入 `pattern/storage_ladder`（见规则建议）。

### F-cobblemon-2 | chain=0026,0027,0028,0029,0035,0046,0053,0054,0099 等（≥7 条） | severity=major | category=amount-insanity
- 位置: `createdeco:netherite_ingot` ↔ `createdeco:netherite_nugget` 阶梯。例 CHAIN 0026 A194 `EXECUTE createdeco:netherite_ingot x43046721`（=3¹⁶）from `createdeco:netherite_nugget x387420489`，**未带标签**；其反向 A195 `createdeco:netherite_nugget_from_netherite_ingot` 已带 `[rule-tag: pattern/storage_ladder]`。全批该族 452 个 EXECUTE 步未带标签、仅 132 个带标签。
- 问题: 阶梯整体是 storage_ladder（反向已标），但正向（ingot 由 nugget 合成）漏标，仍造成 3¹⁶ 级放大且无净产出——属"已带标签仍失控"。同样出现 `4782969=3¹⁴`、`531441=3¹²`、`59049=3¹⁰` 等幂次。
- 原因: netherite nugget/ingot 配对仅反向配方被规则识别，正向合成步漏标，导致阶梯在 cone 中被重复展开。
- 建议: 将 `createdeco:netherite_(ingot|nugget)`（含 `createdeco:netherite_nugget_from_netherite_ingot`）整组纳入 `pattern/storage_ladder`，消除半标导致的内爆。

### F-cobblemon-3 | chain=0097,0102,0107 | severity=major | category=amount-insanity
- 位置: `cobblemon:tumblestone_block` ↔ `cobblemon:tumblestone` 压缩阶梯。例 CHAIN 0097 A153 `EXECUTE cobblemon:tumblestone_block x65536`（=2¹⁶）from `cobblemon:tumblestone x262144`（=2¹⁸）；A154 `tumblestone x65536` from `tumblestone_block x65536` 1:1；A155 `tumblestone_block x16384`（=2¹⁴）… 全链无 `[rule-tag]`。
- 问题: 4:1 石料块↔碎石存储阶梯未带标签，数量按 2 的幂次放大（65536/262144/16384），单步注入 6.5e4 且为无净产出往返。
- 原因: 分类器未覆盖 cobblemon 的 `tumblestone`/`tumblestone_block`（非原版 `*_block` 命名）。
- 建议: 将 `cobblemon:tumblestone(_block)?` 纳入 `pattern/storage_ladder`。

### F-cobblemon-4 | chain=0026,0027,0028,0029,0062,0063,0064,0065 | severity=minor | category=other
- 位置: `sophisticatedstorage:limited_netherite_barrel_1..4`（0026-0029，crafts 均=296451074）与 `limited_iron_barrel_1..4`（0062-0065，crafts 均=294685314）。
- 问题: 同一"limited barrel"的四个编号变体各自展开出**逐字节相同**的 ~2.95e8 crafts 巨型链（共享子结构签名）。目标是 pack 真实导出的四个槽位变体，本身合法，但对批量审查是近重复目标，把预算/链路规模放大 4 倍而无新信息。
- 原因: 目标选择未对"编号后缀变体"去重；规划器对每条都重算同一棵共享锥。
- 建议: 对 `sophisticatedstorage:limited_.*_barrel_[1-4]`（同类还有 `*_barrel_1..4`、`*_shulker_box`、`netherite_chest/gold_chest` 等）做目标级去重/归并后再跑批（见规则建议）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0002 | amount-insanity | bad | raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0005 | amount-insanity | questionable | brass_exoskeleton：recolor×105+exchange×150 已标但仍 1.9e8，根因含 INT_MAX 哨兵 |
| 0008 | amount-insanity | bad | raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0010 | amount-insanity | bad | raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0013 | amount-insanity | bad | raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0026 | amount-insanity | bad | createdeco netherite 3¹⁶ 半标（F2）+ limited barrel 重复目标（F4）+ `|+1` 污染 cobblestone_gen |
| 0027-0029 | other | questionable | 同 0026 的 limited barrel 编号变体重复（F4） |
| 0035 | amount-insanity | questionable | heal_rod：共享子结构（|+14 污染 + INT_MAX 哨兵）主导 2.68e10 |
| 0036 | amount-insanity | questionable | ancient_ultra_rod：crafts=37573774392 与 0074 完全相同（共享子结构） |
| 0039 | amount-insanity | questionable | ancient_gigaton_rod：3.1e10，INT_MAX 哨兵 + 未标 ladder 叠加 |
| 0041 | amount-insanity | questionable | mechanical_roller：A1-A49 diorite `|+14` 污染 stonecutter 树 + A56+ INT_MAX 煤哨兵（已知问题） |
| 0046 | amount-insanity | bad | netherite_shulker_box：createdeco netherite 3¹⁶ 未标阶梯（F2） |
| 0047 | amount-insanity | bad | precision_mechanism：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0051 | amount-insanity | bad | mechanical_mixer：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0053-0055 | other | questionable | sophisticatedstorage 箱/桶变体，共享子结构 + 编号目标冗余 |
| 0054 | amount-insanity | bad | netherite_barrel：createdeco netherite 3¹⁶ 半标（F2） |
| 0056 | amount-insanity | bad | encased_fan：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0058 | other | questionable | simpletms:machine_tm：raw_kinds=1371，候选爆炸，非链级问题 |
| 0062-0065 | other | questionable | limited_iron_barrel 1-4 近重复目标（F4） |
| 0070 | amount-insanity | bad | rolling_mill：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0074 | amount-insanity | questionable | moon_rod：crafts=37573774392 与 0036 完全相同（共享子结构） |
| 0075 | loop-or-cycle | questionable | eevium_z（完整链）：leaf/water/fire/thunder_stone 各做 block→from_block→block→from_block 双重往返，无存储需求却冗余反转（minor，已标 exchange 不报，仅记） |
| 0086 | amount-insanity | questionable | net_rod：crafts=37584536021 与 0049 同（共享子结构） |
| 0091 | other | questionable | gold_shulker_box：共享子结构 + 编号目标冗余 |
| 0094 | amount-insanity | bad | brass_mixer：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0095 | amount-insanity | questionable | pokefinder_white：A1+ crimsite `|+14` 污染 129 步 exchange 树主导 1.98e9 |
| 0096 | amount-insanity | questionable | advanced_display_small：共享子结构（1.78e9） |
| 0097 | amount-insanity | bad | pokefinder_black：tumblestone 2¹⁶ 未标阶梯（F3） |
| 0098 | amount-insanity | questionable | pokefinder_pink：crimsite `|+14` 树 + 共享子结构（1.98e9） |
| 0099 | amount-insanity | bad | gold_chest：createdeco netherite 3¹⁶ 未标阶梯（F2） |
| 0101 | amount-insanity | bad | railway_mixer：raw_zinc 3¹⁶ 未标阶梯（F1） |
| 0102 | amount-insanity | bad | ancient_citrine_rod：tumblestone 2¹⁶ 未标阶梯（F3） |
| 0104 | amount-insanity | questionable | pokefinder_blue：`|+14` 树 + 共享子结构（1.98e9） |
| 0107 | amount-insanity | bad | ancient_azure_rod：tumblestone 2¹⁶ 未标阶梯（F3） |
| 0108-0109 | amount-insanity | questionable | pokefinder black/yellow：`|+14` 树 + 共享子结构（1.98e9） |
| 0110 | amount-insanity | questionable | roseate_rod：crafts=37573774310 与 0045/0076/0092 同（共享子结构） |
| 0119 | amount-insanity | questionable | nest_rod：2.68e10，INT_MAX 哨兵 + 未标 ladder 叠加 |
| 其余 0077-0093、0111-0125 等 colossal 链 | other | questionable | 同型：巨型共享子结构（|+14 污染、INT_MAX 煤哨兵、未标 storage ladder）主导，目标尾极小；无新发现族 |

## 规则建议（给汇总者）
- targetRegex: `sophisticatedstorage:limited_.*_barrel_[1-4]`（及可类推的 `*_shulker_box`/`netherite_chest`/`gold_chest` 编号变体）—— 去重近重复目标，证据 8 链（0026-0029、0062-0065）crafts 逐字节相同。
- recipeRegex / builtin `pattern/storage_ladder` 增补：
  - `create:crafting/materials/raw_zinc_block|raw_zinc` —— 9:1 矿石块阶梯漏标，证据 10 链（0002/0008/0010/0013/0047/0051/0056/0070/0094/0101），幂次 3¹²~3¹⁸。
  - `createdeco:netherite_(ingot|nugget)`（含 `createdeco:netherite_nugget_from_netherite_ingot`）—— 半标导致 3¹⁶ 内爆，证据 452 未标步 / ≥7 链。
  - `cobblemon:tumblestone(_block)?` —— 4:1 石料块阶梯漏标，证据 3 链（0097/0102/0107），幂次 2¹⁴~2¹⁸。
- 已知规划器级问题（已在链级判定一句话提及，本批不新增规则，建议数据层排除）：`|+N` 物品 id 后缀污染（corrupted id 喂入 exchange/stonecutter/cobblestone_gen）、32 位溢出哨兵 `deeperdarker:coal_from_blasting_gloomslate_coal_ore x2147483647`、原版常见物 `BUDGET_TRUNCATED` 截断。
- 无新模式（除上三条 storage_ladder 增补外，未发现新型 fake-production / 绕路 / tag 展开错误族）。
