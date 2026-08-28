# CreateCustomised1.1.2 batch-004 审查报告
- pack: CreateCustomised1.1.2
- 批次: batch-004（written=43；类别分布：incomplete 43 / 0 其他；全部为 incomplete）
- 审查链数: 43（incomplete 43 / complete 0；全量审查，因总量不足 100 条按"不足则全量"执行；含 4 条 mega-wide：0009/0027/0036/0040）
- 发现: critical 0 / major 0 / minor 3

## 结论速读
本批相对 round-1..3 已固化的 `pattern/exchange`、`pattern/storage_ladder`、`intent/recycling`、`intent/generation` 标签，**无新增 major 级配方族**，包整体可视为收敛。但发现 **3 个 minor 级、此前未被任何标签覆盖的新噪声族**（均为机模/循环建模问题，非真实"假生产"）：
1. `minecraft:*_via_vanilla_stripping` —— 原版斧 stripping 兼容配方被注入图、且**无 `@ 机器` 字段**，被当作可量产步骤用于锥体放大（全文件 ~40 处，跨 ≥7 链）。
2. `incubation:chicken_egg_crate ↔ incubation:egg` —— 蛋↔板条箱无净产出往返环（链 0000）。
3. `create_salvage:diamond ⇄ diamond_nugget` 往返环 —— 已带 `intent/recycling`/`storage_ladder` 标签，但仍以 INT_MAX 规模被用来"放大"钻石（链 0040，已带标签仍失控）。
其余残留的 amount-insanity / UNRESOLVED 全部可溯源到 round-4 已知规划器级问题（32 位溢出哨兵 `x2147483647` 全文件 536 处、`|+N` id 后缀污染、BUDGET_TRUNCATED 截断 469 处、CASCADE_EXHAUSTED 原材），均非新模式。

## 发现
### F-CC-1 | chain=0002,0009,0021,0036,0039,0041(等多条) | severity=minor | category=machine-mismatch
- 位置: `minecraft:<wood>_log|_wood_via_vanilla_stripping` EXECUTE 行（示例 CHAIN 0002 `[A84] EXECUTE minecraft:stripped_birch_log_via_vanilla_stripping x131072`；CHAIN 0009 行 4577 `[A243] EXECUTE minecraft:stripped_birch_log_via_vanilla_stripping x8192`；CHAIN 0036 行 17154 `[A237] EXECUTE minecraft:stripped_birch_log_via_vanilla_stripping x65536`）
- 问题: 该配方**没有 `@ <机器>` 字段**（对比同文件其他 EXECUTE 均带 `@ create:mechanical_saw` 等）。其 in/out 形如 `in: minecraft:birch_log x131072, minecraft:iron_axe x131072 → out: minecraft:stripped_birch_log x131072`，即这是原版"手持斧右键剥皮"的玩家动作，被规划器当作可机执行的量产步骤注入锥体（dark_oak/acacia/cherry/birch 等木种 ×16/×64/×16384/×131072 各级别反复出现，同文件共 ~40 处）。同一"剥皮成 stripped log"结果还有另一条合法带机器的 `create:cutting/runtime_generated/compat/minecraft/<log>_to_stripped_<log> @ create:mechanical_saw`（链 0004 A10），形成重复族。
- 原因: 规划器把 `via_vanilla_*` 兼容配方建模为"无机器"的生产节点——既缺机器映射，又把本应消耗/不消耗斧的玩家动作当作量产链一环，驱动锥体放大。属机模层缺口，非 fake-production（动作本身玩家可执行）。
- 建议: 新增 `builtin` 候选规则，将 `minecraft:.*_via_vanilla_stripping` 标为 machine-mapping-mismatch / player-action-no-machine（或在分类器层为其指派伪机器"手持斧"并去重，使其与 mechanical_saw 剥皮配方合并）。

### F-CC-2 | chain=0000 | severity=minor | category=loop-or-cycle
- 位置: `incubation:chicken_egg_crate` / `incubation:egg` 区间（A28→A31）
- 问题: 链内形成蛋↔板条箱往返：`[A28] incubation:chicken_egg_crate (in: egg x9 → crate x1)` → `[A29] incubation:egg (in: crate x1 → egg x9)` → `[A30] chicken_egg_crate (in: egg x9 → crate x1)` → `[A31] incubation:egg (in: crate x1 → egg x1)` → A32 `ratatouille:emptying/egg_to_yolk`。
  - A28↔A29 为 9 蛋→箱→9 蛋，**净产出为零**的往返；
  - 同属 `incubation:egg` 配方却给出矛盾产出（A29 出 9 蛋、A31 出 1 蛋），脚本级数据不一致；
  - 真实蛋源 unresolved（`[UNRESOLVED] minecraft:egg x9 (reason=CASCADE_EXHAUSTED)`），此环仅是绕路占位。
- 原因: incubation 模组蛋/板条箱互转被当作可放大生产路径，实际是无净产出的往返环 + 自相矛盾产出。未被任何已固化标签覆盖（非 exchange/storage/recycling/generation）。
- 建议: 将该 `incubation:*` 蛋↔箱互转识别为 no-net-output round-trip（参考 storage_ladder 的往返环处理）或直接排除；并核查模组配方数据里 `incubation:egg` 的 9 vs 1 产出不一致。

### F-CC-3 | chain=0040 | severity=minor | category=loop-or-cycle （已带标签仍失控）
- 位置: `create_salvage:diamond_nuggets_from_diamond` ⇄ `create_salvage:diamond_from_diamond_nuggets`（如 A197 → A710/A716/... → A711）
- 问题: `diamond_nuggets_from_diamond (in: diamond → diamond_nugget) [storage_ladder,recycling]` 与 `diamond_from_diamond_nuggets (in: diamond_nugget → diamond) [recycling]` 构成 **1:1 零净产出往返**，却以 INT_MAX（`x2147483647`，全文件 536 处之一）规模重复 16 对（create_salvage 家族：diamond_nugget×40、from×16、to×16）。目标 `snout_armor_trim_smithing_template` 需钻石，规划器用此往返"膨胀"可用钻石数而非真正产出。
- 原因: 已被 `intent/recycling` + `pattern/storage_ladder` 标签覆盖，但 recycling 的往返环检测未拦住该 `create_salvage:` 命名空间变体——属"已带标签仍失控"。
- 建议: 在 recycling 往返环检测中显式纳入 `create_salvage:diamond_from_diamond_nuggets ⇄ diamond_nuggets_from_diamond`（及 `salvaging/diamond_*_superheated`）作为 no-net-output 环；无需新增独立标签。

## 残留现象（已知规划器级问题，非新模式，不在审查层修复）
- R-1｜32 位溢出哨兵 `x2147483647`：全文件 536 处（如链 0040 A178/A183/...`diamond_from_blasting_deepslate_diamond_ore x2147483647`、A408/A554/A696 `crushing/copper_ore x2147483647`），驱动 crafts 达 5.6e11（链 0040）。属 round-4 清单"32 位溢出哨兵"。
- R-2｜`|+N` id 后缀污染：普遍进入 UNRESOLVED（如 `create:small_crimsite_brick_wall|+17`、`create:cut_andesite|+17`、`bits_n_bobs:ochrum_tiles|+17`），属 round-4 清单"`|+N` id 后缀污染"。
- R-3｜BUDGET/RANK 截断：BUDGET_TRUNCATED 469、RANK_TRUNCATED 28、CASCADE_EXHAUSTED 37；door/diamond/raw_* 等巨型需求（acacia_door|+9 ×N、diamond xN）为预算截断与 any-of 基底展开（#doors 全展开）所致，属已知规划器问题。
- R-4｜`minecraft:honeycomb_block @ create:basin` 机器映射错配：链 0005/0010/0021 中 `EXECUTE minecraft:honeycomb_block x1 @ create:basin (in: honeycomb x4)` 重复 16 次；原版 `minecraft:honeycomb_block` 系 crafting_table 配方，被映射到 `create:basin`。属 round-4 清单"机器映射错配"，非新族。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | recycling+storage+incubation | questionable | F-CC-2：incubation 蛋↔箱无净产出往返环（minor），末端 egg_yolk 合法 |
| 0002 | exchange+storage+vanilla_strip | questionable | F-CC-1：via_vanilla_stripping 无机器字段（minor）；其余 exchange/storage 已覆盖 |
| 0004 | exchange+storage+cutting(compat) | questionable | A10 `runtime_generated` saw 剥皮合法；含 via_vanilla_stripping（F-CC-1）；INT_MAX 驱动 crafts |
| 0005 | exchange+basin(honeycomb) | questionable | R-4 honeycomb@basin 机器错配；目标 waxed copper trapdoor 合法；diamond_axe 等截断（R-3） |
| 0009 | exchange+storage+vanilla_strip | questionable | F-CC-1：A243 stripped_birch_log 无机器；INT_MAX/截断（R-1/R-3） |
| 0010 | exchange+basin(honeycomb) | questionable | R-4 honeycomb@basin；waxed copper stairs 合法 |
| 0021 | exchange+basin(honeycomb)+vanilla_strip | questionable | R-4 + F-CC-1；waxed copper 合法 |
| 0022 | exchange+storage+recycling+salvage | questionable | cooking_pot 合法；rice_panicle 放大 + 门/钻截断（R-3） |
| 0027 | exchange+storage+crushing+vanilla_strip | questionable | amethyst 粉碎、birch_wood 量产合法；含 via_vanilla_stripping（F-CC-1）；rice 放大（R-3） |
| 0036 | exchange+storage+vanilla_strip | questionable | F-CC-1：A237 stripped_birch_log 无机器；INT_MAX 钻/煤放大（R-1） |
| 0039 | exchange+storage+recycling+vanilla_strip | questionable | F-CC-1；iron_axe 放大（R-3）；coal blasting 合法 |
| 0040 | exchange+storage+recycling+salvage+vanilla_strip | questionable | **F-CC-3** create_salvage 钻石往返环（已带标签仍失控）+ R-1 INT_MAX ×N + R-2/R-3 截断；目标 snout 模板本身合法 |
| 0041 | exchange+storage+vanilla_strip | questionable | F-CC-1；steak_and_potatoes 合法；rice 放大（R-3） |
| 0042 | exchange+storage+recycling | questionable | roasted_mutton 合法；beetroot/diamond/门 截断（R-3） |
| 0001,0003,0006–0008,0011–0020,0023–0026,0028–0035,0037,0038 | exchange(+storage+recycling) | ok/questionable | 均为 create/bits_n_bobs 石砌变体（exchange 已覆盖）+ storage_ladder（rice_bale/raw_*_block）+ recycling；残留 UNRESOLVED 全为 `|+N`/截断/原材（R-2/R-3），无新族 |

## 规则建议（给汇总者）
- targetRegex: 无新增。本批目标类（create/bits_n_bobs 石砌变体、farmersdelight 炊具/食物、各类门/钻/铜氧化块、snout 模板、egg_yolk_bucket 等）均落在已覆盖族或已知规划器问题内。
- recipeRegex:
  - `^minecraft:.*_via_vanilla_stripping$` → 标 machine-mapping-mismatch / player-action-no-machine，并与 `create:cutting/runtime_generated/compat/minecraft/.*_to_stripped_.* @ create:mechanical_saw` 去重合并（证据：全文件 ~40 处，跨链 0002/0004/0009/0021/0036/0039/0041 等 ≥7 条）。
  - `create_salvage:diamond_from_diamond_nuggets` ⇄ `create_salvage:diamond_nuggets_from_diamond`（及 `salvaging/diamond_*_superheated`）→ 纳入 recycling 往返环检测（已带标签仍失控，F-CC-3；无需新增独立标签，仅补回收 namespace 覆盖）。
- builtin 候选: **`minecraft:*_via_vanilla_stripping` 是跨包可能通用的新模式**——任何含 Create + 原版剥皮兼容的包都会出现"无机器字段的玩家动作配方"被注入量产图，建议标 `machine-mapping`（玩家动作/无机器）+ 与 saw 剥皮去重。证据：本批 ~40 处。
- 收敛评估: 本批相对 round-1..3 **无 major 级新配方族**，仅 3 个 minor 噪声族（已列出）；主流 pattern 已收敛。建议：在规划器/分类器层落实 F-CC-1（剥皮无机器去重）与 F-CC-3（salvage 往返环）后复测；incubation 蛋环（F-CC-2）可作 minor 排除。整体可判定为"已收敛，仅余 minor 噪声"。
