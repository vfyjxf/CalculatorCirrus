# cobblemon-x-create batch-005 审查报告

- pack: cobblemon-x-create
- 批次: batch-005（written=34，类别分布：incomplete 34 / 0；solved=150, structuralDupes=116, coverage 1342/15695=8.6%）
- 审查链数: 34（incomplete 34 / complete 0；本批仅 34 条链，已全量审查；含 8 条 deep-read + 全量 UNRESOLVED/未打标 EXECUTE/系统/机器扫描）
- 发现: critical 0 / major 0 / minor 0

## 审查方法与覆盖

- 全读 `report.txt` / `index.tsv` / `manifest.txt` / `targets.tsv`。
- `chains-incomplete-001.txt` 单文件 17560 行 / 34 链，平均约 50KB/链（<100KB 阈值，未触发抽样下限），故全量过链。
- 定向核对：`reason=BUDGET_TRUNCATED|RANK_TRUNCATED` 共 464 处；全量提取 34 链的 `UNRESOLVED` 行（约 360 条）与未带 `[rule-tag:]` 的 EXECUTE 步骤；枚举 `system:` 与 `@ machine` 去重。
- 抽样深读：0000/0001/0002/0005/0008/0020/0031/0032 等代表链全文。

## 发现

### 无新模式

本批 34 条链全部由已固化的规则族主导（`pattern/exchange`、`pattern/recolor`、`pattern/storage_ladder`、`intent/recycling`、`intent/generation` 已在每条链头 `builtin-rule-tags` 标注），未出现任何未被既有规则覆盖的新可疑配方族或目标类。所有未解析叶子与异常数值均可归因为 round-5 口径已列明的规划器级已知问题：

- **`|+N` id 后缀污染**：`create:cut_scorchia_brick_wall|+14`、`cobblemon:green_plaque|+15`、`cobblemon:apricorn_planks|+15`、`create:crimsite_pillar|+14`、`minecraft:black_wool|+14`、`minecraft:gray_wool|+15`、`minecraft:lava|+1`、`minecraft:trapped_chest|+1` 等——属已知 id 污染，非真实配方问题。
- **原版常见物预算/排序截断**：`minecraft:brick`、`minecraft:redstone`、`minecraft:coal`、`minecraft:iron_ingot`、`minecraft:raw_iron`、`minecraft:copper_ingot`、`minecraft:cobblestone`、`minecraft:wheat`、`minecraft:stick`、`minecraft:cherry_planks` 等，均为 `reason=BUDGET_TRUNCATED/RANK_TRUNCATED/CASCADE_EXHAUSTED` 的截断叶子。
- **哨兵/溢出数值**：`crafts=37573774120` 等 crafts 列的 32 位溢出哨兵；`deeperdarker:coal_from_blasting_gloomslate_coal_ore x2147483647`（MAX_INT 哨兵，且 `@ create:encased_fan` 即已知的 `blasting@encased_fan` 机器映射错配）；`create:raw_zinc x387420489`、`cobblemon:relic_coin x43046721`、`minecraft:raw_iron x43046721` 等为 `3^n`/`9^n` 量级放大。
- **无界放大（已带标签仍放大）**：`dndesires:crafting/coal_piece_from_coal` ↔ `dndesires:crafting/coal_from_coal_piece`（coal↔coal_piece 8:1 阶梯），属 `pattern/storage_ladder` 的 piece↔item 族放大，已知规划器级问题，非新族。
- **fake-production 排查**：本批出现的 `system:` 仅 `minecraft:crafting`、`minecraft:stonecutting`、`minecraft:smithing`、`jei:category/create/sequenced_assembly`、`jei:category/create_enchantment_industry/printing` 五种，均为玩家可执行的真实生产系统；无 quest/trade/JER/奖励展示页。`jei:category/*` 为合法机器配方系统，未误判。
- **free-resource 排查**：全量扫描 `in :` 为空输入的 EXECUTE 步骤，0 条——无零输入产出。
- **机器映射**：`@ machine` 去重后均为真实机器（stonecutter、industrial_fan、crafting_upgrade、mechanical_crafter、shadow_steel_press、brewing_stand、basin、encased_fan、crushing_wheel、mechanical_saw、blast_furnace、railway_deployer、smithing_table、furnace、millstone、printer、mechanical_grindstone）；其中 `encased_fan` 承载 blasting/coke、`stripped_*_log @ mechanical_saw` 等即已知 stripping/blasting 机器错配，已在既有清单内。

**结论：审查层收敛，本批无新模式。**

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 createdeco:dusk_brick_wall | incomplete | ok | 纯 exchange 石切变体；未解析为 `|+14` id 污染 + `minecraft:brick` 预算截断 |
| 0001 create:schematic | incomplete | ok | recolor/exchange/storage_ladder 爆炸 + crafts 溢出哨兵 + MAX_INT + blasting@encased_fan + coal_piece 放大 |
| 0002 cobblemon:pasture | incomplete | ok | recycling/exchange/storage_ladder；原版常见物截断，无新族 |
| 0003 createdeco:corner_dusk_brick_slab | incomplete | ok | 纯 exchange 石切变体 |
| 0004 sophisticatedstorage:iron_barrel | incomplete | ok | generation/recycling/exchange/storage_ladder；已知规划器级问题 |
| 0005 create:experience_nugget | incomplete | ok | storage_ladder 经验块阶梯；`super_experience_nugget` CASCADE_EXHAUSTED 叶子 |
| 0006 sophisticatedbackpacks:advanced_tool_swapper_upgrade | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0007 createdeco:blue_brick_stairs | incomplete | ok | 纯 exchange 石切变体 |
| 0008 createcasing:industrial_iron_mixer | incomplete | ok | exchange/recolor/storage_ladder 对称爆炸，无新族 |
| 0009 cobblenav:pokefinder_item_red | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0010 createdeco:long_pearl_brick_slab | incomplete | ok | 纯 exchange 石切变体 |
| 0011 sophisticatedbackpacks:advanced_restock_upgrade | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0012 createdeco:corner_red_brick_stairs | incomplete | ok | generation/recycling/exchange/storage_ladder |
| 0013 create:adjustable_chain_gearshift | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0014 createdeco:short_pearl_brick_stairs | incomplete | ok | 纯 exchange 石切变体 |
| 0015 createcasing:weathered_iron_adjustable_chain_gearshift | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0016 sophisticatedbackpacks:everlasting_upgrade | incomplete | ok | exchange/recolor/storage_ladder；无新族 |
| 0017 createcasing:refined_radiance_mixer | incomplete | ok | exchange/recolor/storage_ladder 对称爆炸 |
| 0018 createcasing:shadow_steel_mixer | incomplete | ok | exchange/recolor/storage_ladder 对称爆炸 |
| 0019 storagedrawers:keybutton_concealment | incomplete | ok | mega-wide；recycling/exchange/recolor/storage_ladder，无新族 |
| 0020 mega_showdown:olivias_z_ring | incomplete | ok | exchange/recolor（cobblemon plaque redye + `|+15` 污染）/storage_ladder |
| 0021 sophisticatedstorage:advanced_filter_upgrade | incomplete | ok | exchange/recolor/storage_ladder；无新族 |
| 0022 create_sa:fan_component | incomplete | ok | exchange/recolor/storage_ladder 对称爆炸 |
| 0023 create:content_observer | incomplete | ok | generation/recycling/exchange/storage_ladder |
| 0024 dndesires:large_cog_crank | incomplete | ok | exchange/recolor/storage_ladder；无新族 |
| 0025 rctmod:trainer_spawner | incomplete | ok | mega-wide；generation/recycling/exchange/storage_ladder |
| 0026 exposure:developed_black_and_white_film | incomplete | ok | sequenced_assembly 真实系统 + recycling/exchange/recolor/storage_ladder |
| 0027 create:sticky_mechanical_piston | incomplete | ok | exchange/recolor/storage_ladder；无新族 |
| 0028 createcasing:industrial_iron_encased_fan | incomplete | ok | recycling/exchange/storage_ladder；无新族 |
| 0029 dndesires:cog_crank | incomplete | ok | recycling/exchange/recolor/storage_ladder |
| 0030 createcasing:refined_radiance_encased_fan | incomplete | ok | exchange/recolor/storage_ladder 对称爆炸 |
| 0031 cobblemon:timer_rod | incomplete | ok | smithing 真实系统 + recycling/exchange/recolor/storage_ladder |
| 0032 create_enchantment_industry:printer | incomplete | ok | storage_ladder（raw_copper/dried_kelp 阶梯）+ blasting@encased_fan + stripping 错配 |
| 0033 createcasing:brass_chain_conveyor | incomplete | ok | exchange/recolor/storage_ladder；无新族 |

## 规则建议（给汇总者）

- 无新模式时写"无"。
- 跨包通用候选（已在 round-5 口径固化，本批再次印证，无需新增）：`pattern/storage_ladder` 已覆盖 `dndesires:coal_piece↔coal` 这类 mod 自定义 piece↔item 8:1 阶梯；`pattern/exchange` 已覆盖 cobblemon plaque 的 redye 环（`cobblemon:*_plaque_redye_as_coloring`）。建议汇总者在 filters.json 维持现状并继续推进下一 batch 至 convergence ≥ 85%。
