# 链递进审查最终报告（2026-08-29，Round 1–5）

## 1. 执行摘要

按 `CHAIN-REVIEW.md` 的递进工作流完成 5 轮「生成 → swarm 审查 → 固化规则 → git 提交」循环：

- **22 个整合包**全部到达终态；共审查约 **6,600 条链**（79 次 agent 审查）
- 累计发现：**critical 19 / major 185 / minor 46**（逐轮 15/106/18 → 3/42/9 → 1/25/8 → 0/10/11 → 0/2/0，收敛）
- 内置规则 `builtin_rules.json`：**22 → 33 条**（新增 11 条、扩展 5 条）
- 每包过滤器：**88 条**固化到 `chain-exports/<pack>/state/filters.json`
- 工具修复 1 个关键 bug（chainExportClasspath 不触发资源拷贝，导致规则不生效）
- git 提交 6 个（工具基线 + 5 个轮次）

## 2. 覆盖率与终止状态

覆盖率目标沿用 CHAIN-REVIEW.md §4 分档（<2k:70% / 2–6k:60% / 6–25k:50% / 25–100k:32% / ≥100k:20%）。
实际终止依据三信号之一：**过滤器收敛率 ≥85%**、**结构耗尽**（solved 全部重复、written=0）、
**审查层零新模式**（连续轮无新发现）。结构去重与分层选择对覆盖率有硬上限
（如 create-lets-create 9.5% 即耗尽），覆盖率是上限指引、收敛才是停止信号（文档 §4 原则）。

| 包 | kept | 批次 | 覆盖率(目标) | 收敛率 | 终态 |
|---|---|---|---|---|---|
| atm10 | 178,621 | 5 | 3.8% (20%) | 100% | 收敛 |
| atm10-lite | 18,414 | 5 | 18.0% (60%) | — | 结构耗尽 |
| All Of Create 2.1 | 13,693 | 5 | 11.8% (50%) | 47% | 审查收敛（零新模式） |
| BlockFront | 1,277 | 5 | 45.0% (70%) | 39% | 审查收敛 |
| Brick's Building 1.4.1 | 17,335 | 5 | 5.1% (50%) | 100% | 收敛 |
| civilization-isolaria | 30,285 | 5 | 5.1% (50%) | 100% | 收敛 |
| cobblemon-x-create | 15,695 | 5 | 8.6% (50%) | 77% | 审查收敛 |
| create-lets-create | 18,085 | 5 | 9.5% (50%) | — | 结构耗尽 |
| create-oneblock | 10,159 | 5 | 12.8% (50%) | 69% | 审查收敛 |
| create-train | 44,750 | 5 | 2.8% (32%) | — | 结构耗尽 |
| create-ultimate | 10,647 | 5 | 16.8% (50%) | 32% | 审查收敛 |
| CreateCustomised1.1.2 | 10,503 | 5 | 12.8% (50%) | 42% | 审查收敛 |
| creative-drawers-producer-2 | 18,541 | 5 | 12.8% (50%) | 100% | 收敛 |
| enigmatica10 | 49,103 | 5 | 5.9% (32%) | 100% | 收敛 |
| farmers-colonies | 2,296 | 5 | 36.1% (60%) | 36% | 审查收敛 |
| fom | 3,442 | 5 | 25.7% (60%) | 77% | 审查收敛 |
| lost-world | 3,881 | 5 | 26.7% (60%) | 100% | 收敛 |
| LuckyBlockChallenge | 1,277 | 6 | 45.0% (70%) | 39% | 审查收敛 |
| minecolonies-dimensional-adventure | 71,223 | 4 | 2.4% (32%) | 94% | 收敛 |
| mythcrafts | 24,267 | 5 | 3.8% (50%) | 87% | 收敛 |
| technical-electrical | 12,473 | 6 | 12.0% (50%) | 86% | 收敛 |
| tfc-enhanced | 4,249 | 5 | 30.1% (60%) | 61% | 审查收敛 |

（注：多包 kept 较初始下降——junk 规则在建图期排除了泄漏/展示配方，属预期效果。）

## 3. 固化到内置规则的成果（builtin_rules.json v2, 33 条）

| 新增/扩展 | 内容 | 标签 |
|---|---|---|
| `anvil_leak_recipes`（新） | anvil.self_repair/materials_repair、`minecraft:enchantment.*`、crafting_on_a_stick:anvil | quality/junk |
| `anvil_system_recipes`（新） | system=`minecraft:anvil`（堵 synthetic 附魔应用缺口） | quality/junk |
| `storage_compression_ladders`（新+3 轮扩展） | 原版 `*_block↔*` 全集、allthecompressed、create_compressed、quark 全系、FD rice/straw/canvas/crate、IE 钢三态、powah、tumblestone、createdeco netherite、`compressed_*`、`raw_*_block`、`_(shard|flake)s?_from_`、`storage_blocks/from_`、`_(ingot|block)_9x9`、`deconstruction`、productivetrees、crate/bag、fluix、cardboard 等 | pattern/storage_ladder |
| `variant_stonecutting_cycles`（新） | createdeco/dndecor/garnished/blockbox/glazedresymmetry 变体环 | pattern/exchange |
| `shape_reverse_roundtrips`（新） | `from_stairs/walls/slabs$` 形状反向环（TFC 57 链主因） | pattern/exchange |
| `smithing_trim_swaps`（新） | `_smithing_trim$` | pattern/exchange |
| `slab_to_block_generalized`（新） | quark slab_to_block 泛化爆炸 | pattern/exchange |
| `chisel_stonecutting_variants`（新） | `stonecutting/(chipped|chisel)_` | pattern/exchange |
| `cobble_generators`（新） | create_generators、cobblestone_gen、lava_from_cobble | intent/generation |
| `salvage_mod_roundtrips`（新） | mod=create_salvage | intent/recycling |
| `resource_generators`（扩展） | + laser_drill、alakarkinos、graphite_processing | intent/generation |
| `recycling_recipes`（扩展） | + nugget_from_smelting、clay_oven_bottom_to_clay | intent/recycling |
| `recolor_recipes`（扩展） | + redye（plaque redye 1162 步） | pattern/recolor |
| `synthetic_anvil_duplicates`（扩展） | + `^minecraft:anvil$`、crafting_on_a_stick:anvil 系统 | quality/junk |

## 4. 规划器/导出层 backlog（规则层无法解决，按优先级）

1. **P0 放大环 32 位计数溢出**：需求触达 2^31-1 哨兵乃至 2^33/UINT_MAX，crafts 达 10^9–10^13（8 包，round-5 仍为最大残留噪声）
2. **P0 `|+N` 块状态后缀污染物品 id**：`black_wool|+14` 等幽灵 id（5 包；LBC/fom 全部 incomplete 的根因）——图构建期剥离后缀回退基物品
3. **P1 已带标签仍无界放大**：storage_ladder/recycling/exchange 标签不约束展开，需折叠护栏/振荡上限（×16–3^16 反复往返）
4. **P1 any-of 基底坍缩与全展开**：`#wool`→black_wool 环形依赖、`#planks` 519 成员进 RAW——应选未染/代表基底
5. **P2 机器映射**：原版 blasting@encased_fan（95 链）、背包升级物品当机器（308 步）、stripping 无机器
6. **P2 数据校验**：smithing_trim 输出≠base 类型、hay↔wheat 1:1/1:9 不自洽、potion NBT toString 进 id、原版工具配方缺失（farmers-colonies stone_axe/bow）
7. **P2 其他**：原版常见物预算优先保护、锥跨链缓存、催化剂耐久误计

## 5. 工作流本身的修复

- `chainExportClasspath` 不触发资源拷贝 → 规则改动不生效（round-2 元发现）；已加 `dependsOn 'testClasses'`
- 终态识别：收敛率在批次定向未覆盖区域时会回落（定向机制使然），"审查层零新模式"是必要补充信号

## 6. 提交清单

| commit | 内容 |
|---|---|
| `9f12acd` | 工具基线（chain-review.sh、CHAIN-REVIEW.md、chainexport 源码、审查口径） |
| `53a6f6b` | round-1：21 包审查 → 5 新规则 + 35 过滤器 |
| `d77c32d` | round-2：15 包 → 阶梯扩域 + 变体环 + classpath bug 修复 + 20 过滤器 |
| `79a93db` | round-3：13 包 → 阶梯长尾 + cobblegen 泄漏 + 19 过滤器 |
| `6fa294b` | round-4：13 包 → anvil 系统缺口 + 形状反向环 + 13 过滤器 |
| （本次） | round-5 最终验证 + 本报告 |

逐包明细见 `round-001/` … `round-005/` 各目录（每包含发现、链级判定与规则建议）。
