# farmers-colonies batch-002 审查报告
- pack: farmers-colonies
- 批次: batch-002（written=126，类别分布：short 42 / medium 29 / long 9 / giant 24 / colossal 10 / incomplete 12）
- 审查链数: 126（全量：incomplete 12 / colossal 10 / giant 24 / long 9 / medium 29 / short 42）。优先读 incomplete+colossal+giant+long，medium/short 抽样+全文件信号扫描（rule-tag、UNRESOLVED、EXECUTE@system、溢出哨兵、`|+N` 污染、block↔9 振荡）。
- 已知规划器级问题（按 round-2 口径仅一句话提及，不展开）：32 位计数溢出哨兵 x2147483647 **本批未出现**；`|+N` 物品 id 后缀污染（285 处，见 F2）；无净产出往返环机制本身（`loop=allowBoundedAmplification` 放行，见 F1）。上一轮已固化的 anvil/enchantment 泄漏配方本批**均已带 `[rule-tag: pattern/exchange]` 被正确排除**（见 CHAIN 0043/0050/0052 的 jei:/synthetic anvil 步、armor-trim/tool repair 步），未出现泄漏。压缩阶梯族本批未出现。
- 发现: critical 0 / major 2 / minor 1

## 发现

### F-farmers-colonies-1 | chain=0050,0052,0048,0003,0062,0092（全批 giant/colossal/incomplete 同构） | severity=major | category=loop-or-cycle
- 位置: 多处 `EXECUTE minecraft:(raw_)?<X>_block x1 @ crafting_table`（出 `<X> x9`）与反向 `EXECUTE <X> x1 @ crafting_table`（出 `<X>_block x1`）的无损 1↔9 互转段。
- 问题: 原版方块↔9 物品无损互转被 `loop=allowBoundedAmplification` 反复套娃放大，使 giant/colossal/incomplete 链体量膨胀 6–15 倍（如 raw_iron 13 次、diamond 8 次、raw_gold 7 次）。**与上一轮已带 `pattern/storage_ladder` 标签的压缩阶梯（allthecompressed/quark compressed/crate/IE 钢三态/cardboard）不同，此类原版 1↔9 互转未被任何 rule-tag 覆盖**，仍失控。全批含此类振荡的 EXECUTE 步约 452 个。
- 证据: CHAIN 0050 A7–A22（raw_iron_block↔raw_iron 13 次）→A23 才 blast 出 iron_ingot；CHAIN 0052 A5–A13（diamond_block↔diamond 8 次）；CHAIN 0048 A1–A8（raw_gold_block↔raw_gold 7 次）；CHAIN 0003 A4–A11（lapis_block↔lapis）+ A18–A26（bone_block↔bone_meal）；CHAIN 0062 A3–A11（redstone_block↔redstone）；CHAIN 0092 A1–A9（lapis）。上述均为无损 1↔9，无净产出增量。
- 原因: 规划器把无损 1↔9 互转当作放大算子；该族未被 builtin 规则覆盖（压缩阶梯已标 `pattern/storage_ladder`，原版互转漏标）。
- 建议: 把原版 `X_block`↔`X` / `raw_X_block`↔`raw_X` / `bone_block`↔`bone_meal` / `lapis`↔`lapis_block` 等无损 1↔9 互转与压缩阶梯一样标 `[rule-tag: pattern/storage_ladder]`，或在落料/合成阶段收敛，避免生成巨型放大链。

### F-farmers-colonies-2 | chain=0003,0004,0022,0067,0073,0074,0078,0080,0090（及 0051 等） | severity=major | category=data-pollution（unresolved-cascade）
- 位置: 多处物品 id 带 `|+N` 后缀。全批 285 处：oak_planks|+10 ×78、acacia_log|+3 ×70、black_wool|+14 ×68、cobblestone|+2 ×25、black_carpet|+14 ×21、sand|+1 ×11、iron_pickaxe|+… ×6、ender_chest|+… ×3、bamboo_block|+1 ×2，以及 dark_oak_log|+39、oak_door|+10、warped_stem|+…、soul_sand|+…、coal|+…、crimson_stem|+… 各 1。
- 问题: 后缀污染导致两类具体坏象：(1) 原版可合成物 `black_wool` 因污染 id `black_wool|+14` 在 **9 条 incomplete 链**中 RANK_TRUNCATED 未解析（black_wool = 任意羊毛 + 黑染料，本应可解析）；(2) 同一物品按污染后缀分裂成多种 raw_kinds，CHAIN 0051 的 `structurize:blockfluidsubstitution` raw_kinds=49 实为各 log 变体的 `|+N` 副本（包括 `dark_oak_log|+39 x4` 直接喂入 recipe），严重虚高。
- 证据: CHAIN 0003 行 219 `[UNRESOLVED] minecraft:black_wool|+14 x2 (reason=RANK_TRUNCATED)`；同构出现在 0004/0022/0067/0073/0074/0078/0080/0090（共 9 链）；CHAIN 0050 A3 `acacia_log|+3`→A4 `oak_planks|+10`；CHAIN 0051 A25 `dark_oak_log|+39 x4`。
- 原因: 规划器层 `|+N` id 后缀污染（已知规划器级问题），本批未在 id 归一化阶段收敛。
- 建议: 在 id 归一化阶段剥离 `|+N` 后缀（视作同一 canonical id），可同时消除 raw_kinds 膨胀与大量 unresolved；目标端可加 `targetRegex: .+\|[0-9]+$` 辅助识别污染目标。

### F-farmers-colonies-3 | chain=0010,0043,0123 | severity=minor | category=unresolved-expected-item（suspected）
- 位置: [UNRESOLVED] minecraft:splash_potion x1 + redstone ×41（CHAIN 0010，BUDGET_TRUNCATED）；minecraft:fishing_rod x1（CHAIN 0043，CASCADE_EXHAUSTED）；minecraft:bow x1（CHAIN 0123，CASCADE_EXHAUSTED）。
- 问题: 均为原版可制作/可合成物，却未解析。splash_potion 原版可由 potion+gunpowder 酿造（链内只靠 40 层 `pattern/exchange` 药水互转放大，把 splash_potion 当 RAW 叶子）；fishing_rod/bow 仅能经由 `jei:/synthetic/* @ anvil [pattern/exchange]` 的 anvil 倍增路径到达，真实合成配方未在图内，故 CASCADE_EXHAUSTED。属可疑但未坐实——可能因 brewing/工具合成配方未导出，叠加已知截断/环机制。
- 证据: CHAIN 0010 行 571–611（splash_potion/redstone 的 41 条 BUDGET_TRUNCATED）；CHAIN 0043 行 813、CHAIN 0123 行 2222。
- 原因: 疑似特定配方（brew splash_potion、合成 fishing_rod/bow）未在导出图内，或搜索预算/级联被 anvil 倍增路径耗尽；归因为已知机制叠加，非独立新 bug。
- 建议: 确认 brewing 与工具合成配方是否导出；若确实缺失则记为 known-gap，否则评估是否需在召回/优先级上补回浅层原版配方。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0003 | loop-or-cycle + data-pollution | questionable | lapis/bone 1↔9 振荡未标 + black_wool|+14 未解析 |
| 0004 | data-pollution | questionable | black_wool|+14 未解析（RANK_TRUNCATED） |
| 0010 | unresolved-expected-item(suspected) | questionable | splash_potion/redstone BUDGET_TRUNCATED，靠 exchange 放大 |
| 0022 | loop-or-cycle + data-pollution | questionable | 染料振荡 + black_wool|+14 未解析 |
| 0043 | unresolved-expected-item(suspected) | questionable | fishing_rod CASCADE_EXHAUSTED，仅 anvil 倍增可达 |
| 0067 | data-pollution | questionable | green_banner，black_wool|+14 未解析 |
| 0073 | data-pollution | questionable | magenta_banner，black_wool|+14 未解析 |
| 0074 | data-pollution | questionable | lime_banner，black_wool|+14 未解析 |
| 0078 | data-pollution | questionable | green_floating_carpet，black_wool|+14 未解析 |
| 0080 | data-pollution | questionable | light_gray_banner，black_wool|+14 未解析 |
| 0090 | data-pollution | questionable | cyan_floating_carpet，black_wool|+14 未解析 |
| 0123 | unresolved-expected-item(suspected) | questionable | bow CASCADE_EXHAUSTED，仅 anvil 倍增可达 |
| 0050 | loop-or-cycle | questionable | raw_iron 13× 振荡 + anvil tool-repair(exchange 已标) |
| 0051 | loop-or-cycle + data-pollution | questionable | raw_iron 振荡 + raw_kinds=49（dark_oak_log|+39 等污染） |
| 0052 | loop-or-cycle | questionable | diamond 8× 振荡 + armor-trim 倍增(exchange 已标) |
| 0001 | loop-or-cycle | questionable | spire_armor_trim，含振荡/倍增(exchange 已标) |
| 0005 | loop-or-cycle | questionable | 染色玻璃振荡未标 |
| 0006 | loop-or-cycle | questionable | 振荡未标 |
| 0007 | loop-or-cycle | questionable | 振荡未标 |
| 0008 | loop-or-cycle | questionable | 振荡未标 |
| 0009 | loop-or-cycle | questionable | 振荡未标 |
| 0034 | loop-or-cycle | questionable | trapped_chest，含振荡 |
| 0044 | loop-or-cycle | questionable | tnt_minecart，含振荡 |
| 0047 | loop-or-cycle | questionable | soul_lantern，含振荡 |
| 0049 | loop-or-cycle | questionable | blockhutsawmill，含振荡 |
| 0054 | loop-or-cycle | questionable | tide_armor_trim，含振荡/倍增 |
| 0058 | loop-or-cycle | questionable | purple_concrete_powder，含振荡 |
| 0069 | loop-or-cycle | questionable | chest_minecart，含振荡 |
| 0072 | loop-or-cycle | questionable | pink_candle，含振荡 |
| 0076 | loop-or-cycle | questionable | furnace_minecart，含振荡 |
| 0084 | loop-or-cycle | questionable | light_blue_candle，含振荡 |
| 0085 | loop-or-cycle | questionable | light_gray_candle，含振荡 |
| 0091 | loop-or-cycle | questionable | brick_extra，lapis 振荡未标 |
| 0093 | loop-or-cycle | questionable | brick_extra，振荡未标 |
| 0095 | loop-or-cycle | questionable | brick_extra，振荡未标 |
| 0096 | loop-or-cycle | questionable | brick_extra，振荡未标 |
| 0106 | loop-or-cycle | questionable | blockhutbarracks，含振荡 |
| 0068 | loop-or-cycle | questionable | oak_hanging_sign，含振荡 |
| 0070 | loop-or-cycle | questionable | crimson_hanging_sign，含振荡 |
| 0071 | loop-or-cycle | questionable | birch_hanging_sign，含振荡 |
| 0077 | loop-or-cycle | questionable | dark_oak_hanging_sign，含振荡 |
| 0082 | loop-or-cycle | questionable | acacia_hanging_sign，含振荡 |
| 0086 | loop-or-cycle | questionable | spruce_hanging_sign，含振荡 |
| 0110 | loop-or-cycle | questionable | blockhutwarehouse，含振荡 |
| 0112 | loop-or-cycle | questionable | blockpostbox，含振荡 |
| 0114 | loop-or-cycle | questionable | blockstash，含振荡 |
| short×42 / medium×29 / long×9 | — | ok(部分 questionable) | 常规建造/食物/铜氧化链；short 无振荡无污染路径干净；medium/long 部分含 1↔9 振荡或 `|+N` 污染但无 fake/detour/更优路径 |

## 规则建议（给汇总者）
- targetRegex: `.*\|[0-9]+$` —— 识别并排除 `|+N` 污染目标，证据 285 处 / 9 条 incomplete 因 black_wool|+14 未解析 / CHAIN 0051 raw_kinds=49 虚高。
- recipeRegex:
  - `minecraft:(raw_)?[a-z_]+_block x1 @ minecraft:crafting_table`（出 `... x9`）及其反向 `minecraft:[a-z_]+ x1 @ minecraft:crafting_table`（出 `..._block x1`）—— 标 `[rule-tag: pattern/storage_ladder]`，证据约 452 个此类振荡 EXECUTE（CHAIN 0050 A7–A22、0052 A5–A13、0048 A1–A8、0003 A4–A11/A18–A26、0062 A3–A11、0092 A1–A9 等）。
  - `minecraft:lapis_lazuli x1 @ minecraft:crafting_table`（出 `lapis_block`）/ 反向，及 `bone_block↔bone_meal` 互转——同上并入 storage_ladder 收敛。
- builtin 候选:
  - 原版无损 1↔9 互转族（`*_block`↔`*`、`raw_*_block`↔`raw_*`、`bone_block`↔`bone_meal`、`lapis`↔`lapis_block`、`redstone_block`↔`redstone`）→ 建议与压缩阶梯同标 `pattern/storage_ladder`（跨包通用；本轮 ATM10 同类的 productivetrees wood↔log 振荡也属此族，建议统一 no-op/无损互转标签）。
  - `|+N` id 后缀 → 建议 id 归一化层剥离（非分类标签，属数据清洗），跨包通用。
- 无新模式时写"无"：否，已列出 2 条新模式（原版 1↔9 无损互转未标 storage_ladder、|+N 污染致 black_wool 未解析）。
