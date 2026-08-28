# enigmatica10 batch-005 审查报告（round-6 选路质量深审 / producers 索引交叉核对）

- pack: enigmatica10
- 批次: batch-005（written=9，全部 incomplete；category 分布：incomplete 9）
- 审查链数: 9（全部 9 条，因批次仅 9 链，已全量；非抽样）
- 方法: 对每条链抽非 trivial、非 rule-tag 的 EXECUTE 中间品，逐一 `grep -F '<id>\t' producers.tsv` 取生产者数，`grep '"for": "<id>"' producers.jsonl` 取替代者明细，按"输入更少/更常见、无催化剂、直连、无杂产"判选路合理性。
- 发现: critical 0 / major 0 / minor 0

## 选路交叉核对结果（逐链）

本批次 9 链全部 incomplete 且呈 mega-wide / colossal 形态。其中：
- 链 0004–0008（mcwfurnitures 玻璃桌 ×5）**100% 为 `pattern/exchange`（chipped 玻璃 stonecutting 变体）**，无任何非 rule-tag 生产者选择步骤 → 无选路问题。
- 链 0000–0003 含非平凡 EXECUTE，逐条核对如下。

### 多生产者中间品核对（链 0000 / 0001 / 0002 / 0003 共用同一批非平凡步）

| 中间品 | 生产者数 | 链所选 | 索引中替代者 | 判定 |
|---|---|---|---|---|
| `modern_industrialization:steel_ingot` | 4 | `enigmatica:blasting/steel_ingot`（in: `c:dusts/steel`→out 1 ingot） | `enigmatica:smelting/steel_ingot`（同样 `c:dusts/steel`→1 ingot，等价）；`immersiveengineering:crafting/storage_steel_to_ingot_steel`（需 steel 存储块）、`.../nugget_steel_to_ingot_steel`（需 9 钢粒，不同起点） | **ok**：dust→ingot 两配方等价（1→1，仅机器差异）；IE 两条需不同原料，非替代。所选无明显更差。机器标 `occultism:ritual_dummy/summon_marid_smelter` 为已知机器映射伪影（红线排除）。 |
| `modern_industrialization:steel_rod` | 2 | `immersiveengineering:crafting/stick_steel`（2 `c:ingots/steel`→rod） | `modern_industrialization:materials/steel/smelting/rod_magnetic_to_rod_smelting`（需 `steel_rod_magnetic`，为磁化后处理，非从原料直产） | **ok**：所选即"钢锭→钢杆"的**直接**生产者；替代者需先制磁化杆，属下游转换，非更优替代。 |
| `minecraft:string` | 2 | `supplementaries:strings`（1 `supplementaries:flax`→1 string） | `immersiveengineering:crafting/string`（3 `c:fiber_hemp`→1 string） | **ok**：链内已在产 flax（flax→flax_block→…），用 flax→string 与既有物料整合；代价 1 flax vs 3 hemp 纤维，量级相当、可辩护。 |
| `ae2:quartz_glass` | 1 | `ae2:decorative/quartz_glass` | 无 | **ok**：唯一生产者。 |
| `powah:capacitor_basic` / `capacitor_basic_large` / `capacitor_nitro` / `nitro_crystal_block` / `crystal_nitro` | 各 1 | 对应 `powah:crafting/*` | 无 | **ok**：唯一生产者。 |
| `modern_industrialization:steel_ring` / `steel_dust` / `steel_tiny_dust` / `supplementaries:flax_block` | 各 1 | 对应配方 | 无 | **ok**：唯一生产者。 |

> 注：`mffs:steel_ingot|+4`、`mekanism:dust_steel|+3`、`minecraft:glass|+68` 等链内输入带 `|+N` 后缀 —— 属生成器/导出伪影（红线排除），且其为**输入**非所选生产者，不影响选路判定。

### impure-recipe 检查
逐条核对 0000–0003 非平凡所选配方的 `outs`：
- `enigmatica:blasting/steel_ingot` → `['modern_industrialization:steel_ingot']`（单出）
- `immersiveengineering:crafting/stick_steel` → `['modern_industrialization:steel_rod']`（单出）
- `supplementaries:strings` → `['minecraft:string']`（单出）
- `ae2:decorative/quartz_glass` → `['ae2:quartz_glass']`（单出）
- `modern_industrialization:materials/steel/craft/ring` → `['modern_industrialization:steel_ring']`（单出）
- 各 `powah:crafting/*` → 单出
**结论：无杂产配方被选用，impure-recipe 不适用。**

### 目标选择怪象检查
- 0000 `ae2:portable_item_cell_64k`：真实物品，6 个生产者均为合法 AE2 升级/装配配方。链选 `ae2:upgrade/portable_item_cell_4k_to_64k`（4k→64k 升级路径），与直接合成 `ae2:tools/portable_item_cell_64k` 等价（均需 `cell_component_64k` + 他件），非怪象。
- 0001/0002 `powah:energy_cable_nitro` / `spirited`：真实物品，唯一生产者（cable 装配配方）。非怪象。
- 0003 `modern_industrialization:steel_compressor`：真实物品，唯一生产者（`steel/compressor` 升级配方）。非怪象。
- 0004–0008 mcwfurnitures 玻璃桌 ×5：真实家具物品，各有合法生产者。非怪象。

## 发现
（无。本批次 producer 选择经索引交叉核对均合理，无 worse-alternative / silly-detour / impure-recipe / 目标选择怪象。）

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete (mega-wide) | ok | 非平凡步选路合理；A67–A82 为 nitro 晶块↔晶体的无净产出往返环（loop-or-cycle，不在本轮四类，未报） |
| 0001 | incomplete (colossal) | ok | 同 0000 的非平凡选路，无更优替代 |
| 0002 | incomplete (colossal) | ok | 同 0000 的非平凡选路，无更优替代 |
| 0003 | incomplete (mega-wide) | ok | steel_ingot/steel_rod/string 选路合理，MI 钢压缩机目标唯一生产者 |
| 0004 | incomplete (colossal) | ok | 100% pattern/exchange，无生产者选择 |
| 0005 | incomplete (colossal) | ok | 100% pattern/exchange，无生产者选择 |
| 0006 | incomplete (colossal) | ok | 100% pattern/exchange，无生产者选择 |
| 0007 | incomplete (colossal) | ok | 100% pattern/exchange，无生产者选择 |
| 0008 | incomplete (colossal) | ok | 100% pattern/exchange，无生产者选择 |

## 规则建议（给汇总者）
- targetRegex: 无
- recipeRegex: 无（未发现任何 mod 配方族被系统性误选；选路在索引中均有据且可辩护）
- builtin 候选: 无
- 说明：本轮聚焦选路质量，未新发现需收敛的模式。可顺带关注（非本轮范畴）：链 0000 中 `powah` 晶块↔晶体 16 步无净产出往返环，建议作为 loop-or-cycle 在后续通用轮次用 `loop=allowBoundedAmplification` 之外的"无净产出往复"检测收敛，而非选路问题。
