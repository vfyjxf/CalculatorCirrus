# create-ultimate batch-001 审查报告
- pack: create-ultimate
- 批次: batch-001（written=150；类别分布：incomplete 117 / colossal 20 / giant 9 / long 3 / medium 1）
- 审查链数: 150（全量链级扫描：117 incomplete + 33 complete；其中 15 条完整精读，其余按循环签名统计 + UNRESOLVED 配对统计 + 抽样；incomplete 文件 4.4MB 用 per-chain awk 统计扫描，未整读）
- 发现: critical 0 / major 2 / minor 1

## 发现

### F-create-ultimate-1 | chain=多链（系统级） | severity=major | category=loop-or-cycle
- 位置: 各链中 `X_block ↔ X` / `X_ingot ↔ X_nugget` 互逆往返步骤（如 `minecraft:redstone_block`、`minecraft:raw_iron_block`、`minecraft:raw_copper_block`、`createpropulsion:raw_platinum_block`、`farmersdelight:beetroot_crate`、`farmersdelight:straw_bale`、`create:cardboard_block`、`tfmg:steel_nugget`↔`tfmg:steel_ingot` 等）
- 问题: 规划器在产出目标所需的中间物时，反复做"压块→解块"互逆步骤，**净产出为零**（如 redstone x9 → redstone_block x1 → redstone x9），属口径文档明确点名的"无净产出的往返环（compress↔decompress）"。极端例子：
  - CHAIN 0005 `createpropulsion:vector_thruster`：A1→A17 共 **17 步** redstone↔redstone_block 往返，最终才把 redstone x9 交给 A22 造 redstone_torch；A1 的 redstone 本可直接使用。
  - CHAIN 0068 `create_the_air_wars:machinegun`：A1→A17 **17 步** redstone 往返 + A29→A44 **16 步** raw_iron 往返，才分别交付 redstone/iron_ingot。
  - CHAIN 0047 `createpropulsion:cable_relay`：A5→A13 redstone 往返 + A15→A22 raw_platinum 往返。
  - CHAIN 0021 / 0089 / 0100 / 0144 / 0031 / 0106 等同样含 redstone/raw_iron/copper/lapis/cardboard/beetroot/straw 等往返环。
- 频数证据（非臆造，来自全文 grep 计数）：仅 20 条 colossal 中 `redstone_block x1` 出现 **384 次**、`raw_iron_block x1` **392 次**；9 条 giant 中分别为 141 / 120；per-chain 扫描显示 **全部 117 条 incomplete 均有 RB 或 RIB>0**（如 0005 RB=24、0009 RIB=24、0013 RB=24、0050 RIB=24）。即该往返环几乎覆盖本批全部 150 条链。
- 原因: 规划器声明 `loop=allowBoundedAmplification`，但该策略只豁免"有净产出的放大"；此处为纯 no-op 往返，按口径仍属问题。根因是低成本块/锭/粒互逆配方被无差别纳入锥展开，且缺乏"净产出为零的相邻互逆步骤"剪枝。
- 建议: 新增收敛规则——检测相邻"压块/解块"（及 ingot↔nugget）互逆步骤且净产出为零者，折叠为单步或直接跳过；属系统级坏路径，应在汇总阶段以 `recipeRegex`/`builtin` 模式统一收敛（见下方规则建议）。

### F-create-ultimate-2 | chain=多链（系统级） | severity=major | category=unresolved-cascade
- 位置: incomplete 链中 `[UNRESOLVED]` 标记、reason=CASCADE_EXHAUSTED 的物
- 问题: 一批**明显可由玩家配方生产**的物品因上游步骤失败而级联 unresolved，导致本批 117 条 incomplete 链整体不可用度上升。配对统计（chain×item）高频项：
  - `create:stressometer`（CASCADE_EXHAUSTED）出现在 **36 条**链（如 CHAIN 0036 `createsprings:spring_fan` 内 9 次 `[UNRESOLVED] create:stressometer x1`）。
  - `create:brass_ingot` 9 条、`create_the_air_wars:titaniumingot` 8 条、`minecraft:diamond` 5 条、`minecraft:coal` 3 条（CHAIN 0001 `createbionics:oxhauler_rear_item` 行 588：`[UNRESOLVED] minecraft:coal x9 (reason=CASCADE_EXHAUSTED)`）、`minecraft:netherite_ingot` 1 条。
- 原因: 这些均为原版/Create 标准可合成物（stressometer=黄铜外壳+精密机构等；brass_ingot=冶炼/混合；coal=烧煤矿石；diamond=合成/处理）。其 CASCADE_EXHAUSTED 表明上游依赖（常为 F-1 的往返环或锥预算截断）失败并向下级联，而非物品本身不可生产。
- 建议: 先修 F-1 的净零环与锥预算（`budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`，如 0005），再对 stressometer/brass_ingot 等回归；若仍 unresolved，则按 `unresolved-cascade` 单独收敛。

### F-create-ultimate-3 | chain=多链（系统级） | severity=minor | category=unresolved-expected-item
- 位置: incomplete 链 `[UNRESOLVED]`，reason=RANK_TRUNCATED / BUDGET_TRUNCATED 的基础/石切物品
- 问题: 一批本应极易解析的基础物品被规划器在预算内放弃：
  - `create:polished_cut_andesite_stairs` **152 次**（多为 RANK_TRUNCATED）；该物本身是 Create 石切器产物，且被下游石切 exchange 步骤作为输入（见 CHAIN 0005 A26–A33：以 `polished_cut_andesite_stairs|+14 x3` 为输入反复石切），其 unresolved 又级联污染 exchange 步骤。
  - `dndecor:cut_netherrack_bricks` / `dndecor:cut_stone` / `dndecor:small_blackstone_bricks`（RANK_TRUNCATED，石切变体，可生产）。
  - 原版基础物：`minecraft:stick`（BUDGET_TRUNCATED）、`minecraft:copper_ingot`（2 条，BUDGET_TRUNCATED）、`minecraft:beetroot`（BUDGET_TRUNCATED）——这些是最基础配方，被截断说明锥预算偏紧。
- 原因: planner 锥预算（modCap=80、cone-shaped）在深链上耗尽，对候选数多（candidates=5）的基础/石切配方执行 RANK_TRUNCATED，或根输入预算耗尽（BUDGET_TRUNCATED）截断到基岩层。属规划器预算层面的覆盖缺口，证据充分但为截断产物，故判 minor。
- 建议: 提升 cone 预算或放宽石切类候选排序预算；将 `polished_cut_andesite_stairs` 等基础石切物加入优先解析白名单。注意 `minecraft:netherite_upgrade_smithing_template`（1 条，BUDGET_TRUNCATED）属战利品专属物，**正确 unresolved，不应报告**。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0005 vector_thruster | incomplete | bad | 17 步 redstone 往返环 + polished_cut_andesite_stairs unresolved 级联 exchange |
| 0001 oxhauler_rear_item | incomplete | questionable | coal CASCADE_EXHAUSTED + 多处往返环 |
| 0021 network_filterer | long | questionable | A8–A15 redstone 往返环（净零） |
| 0026 large_switch | giant | questionable | steel ingot↔nugget 多次往返 + raw_copper 往返 |
| 0031 purple_multimeter | colossal | questionable | beetroot↔beetroot_crate 往返环 |
| 0036 spring_fan | incomplete | questionable | stressometer 9× CASCADE_EXHAUSTED + 往返环 |
| 0047 cable_relay | giant | questionable | redstone 9 步 + raw_platinum 8 步往返环 |
| 0068 machinegun | giant | bad | redstone 17 步 + raw_iron 16 步极端往返环 |
| 0089 electrolyzer | long | questionable | copper_block↔ingot↔nugget 往返环 |
| 0100 purple_cardboard_window_pane | long | questionable | lapis/cardboard/beetroot 多组往返环 |
| 0106 netherite_portable_whistle | giant | questionable | raw_gold/raw_copper/netherite 多组往返环 |
| 0144 vein_atlas | medium | questionable | straw↔straw_bale 往返环 |
| 其余 ~138 条（全部 117 incomplete + 多数 colossal/giant/long） | incomplete/complete | questionable/bad | 普遍含净零块↔物品往返环（F-1），incomplete 链另含 CASCADE/RANK/BUDGET truncated 的 unresolved（F-2/F-3） |

## 规则建议（给汇总者）
- targetRegex: `minecraft:netherite_upgrade_smithing_template|^minecraft:[a-z_]*_smithing_template$` —— 排除战利品专属物，避免其"正确 unresolved"污染 incomplete 计数。证据：1 条链（CHAIN 0351 区域），确为战利品不可合成。
- recipeRegex（收敛/标记）: `_(block|nugget)$` 与对应基物的互逆压/解配方——标记 `loop-or-cycle` 的净零往返，建议 `builtin` 候选 `pattern/netzero-roundtrip`（标签 junk/duplicate）。证据：colossal 仅 20 链 redstone_block 384×、raw_iron_block 392×；全部 117 incomplete 均 RB/RIB>0。
- builtin 候选: `pattern/netzero-roundtrip`（junk）——检测相邻"压块→解块"且净产出为零的互逆步骤并折叠；跨包通用（典型 compress↔decompress  pathology）。证据见 F-1 各链。
- 另：石切 exchange 步骤（`create:*_from_stone_types_andesite_stonecutting` 重复）已带 `pattern/exchange` 标签，按口径不再重复报告；但其输入 `polished_cut_andesite_stairs` 的 unresolved（F-3）值得单独收敛。
- 无 new fake-production / free-resource / data-pollution / machine-mismatch / amount-insanity 模式（已 grep 核验：无空 `in:`、无 reward/villager/market/jer 关键词、无 null/toString/sentinel、无 crafts>190、无机器配方误投 crafting_table）。
