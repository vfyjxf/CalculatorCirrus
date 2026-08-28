# cobblemon-x-create batch-004 审查报告
- pack: cobblemon-x-create
- 批次: batch-004（written=43，全部 incomplete；类别仅 incomplete 一类，depthBucket 多为 colossal/giant）
- 审查链数: 43（全部为本批 chains-incomplete-001.txt，已全量过；定向 grep 覆盖 100% 链，因总链数 <100 故全读/抽样结合）
- 发现: critical 0 / major 2 / minor 3

> 已知规划器级问题（仅一句话，不单列发现）：`|+N` id 后缀污染（green_plaque|+15 出现 1155 次、crimsite_pillar|+14 / stripped_*_wood|+12 / apricorn_planks|+15 等）导致 these 物不可解、级联为 raw；3^n 放大原始成本（raw_iron x387420489=3^18、raw_copper x43046721=3^16、coal x1.503e10/x1.718e10 等）属无净产出往返机制/溢出哨兵；原版常见物预算截断（redstone 196 条、andesite 23 条、coal 溢出量）属"原版常见物预算截断"。以下只报**新**可疑配方族/目标类。

## 发现

### F-cobblemon-x-create-1 | chain=0001,0003,0021,0022,0023,0028,0029,0030,0038 | severity=major | category=worse-alternative
- 位置: `cobblemon:iron_nugget_from_blasting_plaque` @ create:encased_fan（如 0001 行 451 A50，line 10248/10566/10774/11519/13881/15451/15686/16025/21141 分属上述链）
- 问题: 规划器把 Cobblemon 的" plaque 烧炼回收"配方当**主铁源**用，喂入 `cobblemon:green_plaque|+15`（装饰方块）产出 `minecraft:iron_nugget`，再合成 andesite_alloy / iron_block 供给 Create 机械（copper_press、mechanical_plough、copper_depot、clutch、deployer、mechanical_drill 等）。同一链内**已存在直达铁路**（0001 行 118-130：raw_iron_block→raw_iron→iron_ingot→iron_block）。该 plaque→iron 支路更绕，且仅有的输入 `green_plaque|+15` 因 id 污染在本批不可解（[UNRESOLVED] cobblemon:green_plaque|+15，candidates=5, BUDGET_TRUNCATED），整条支路实际塌缩为 raw。
- 原因: `intent/recycling` 类配方被当成 primary production 调用；分类器把该 recipe 误标 `[rule-tag: pattern/storage_ladder]`（A230 行），未识别为回收-only。装饰性 plaque 不应作为铁锭主来源被纳入机械链。
- 建议: 将 `cobblemon:iron_nugget_from_blasting_plaque` 标为 recycling-only / 排除作主生产；或至少把 `cobblemon:*_plaque` 列排除目标，避免把装饰方块拉进 Create 机械依赖图。

### F-cobblemon-x-create-2 | chain=0001,0002,0006,...(全批多链) | severity=major | category=silly-detour
- 位置: `cobblemon:*_plaque_redye_as_coloring` @ dndesires:industrial_fan（已带 `[rule-tag: pattern/recolor,pattern/exchange]`；0001 行 256-... 连续 49 条，全批 each-color 出现达 172 次，如 gray 172 / brown 172 / blue 172 / cyan 172 / gray 136）
- 问题: 已带标签仍失控。redye 支路以 `green_plaque|+15` 为唯一输入，逐色产出 gray/cyan/brown/blue 等彩色 plaque，但**全批无任何步骤消费这些彩色 plaque**（grep `in : .*_plaque` 除 green_plaque|+15 与 redye 外命中 0）。彩色 plaque 全部落入 [RAW] 残差（如 0001 RAW 段 black_plaque x27、blue_plaque x20…），属死支路。真正的 iron 路径（F-1）直接吃 `green_plaque|+15`，根本不经过 redye。
- 原因: recolor/exchange 模式被过度应用，对"装饰 plaque 重染"生成海量无用中间物；目标（copper_press 等机械）本不需要任何 plaque。
- 建议: 将 `cobblemon:.*_plaque_redye_as_coloring` 升级为排除（装饰重染不计入生产链）；该模式当前仅标 recolor 不足以收敛。

### F-cobblemon-x-create-3 | chain=0002,0004,...(含 potion 支路链) | severity=minor | category=data-pollution
- 位置: `minecraft:item.minecraft.potion.effect_id.<...>.to.item.minecraft.potion.effect_id.<...>`（全批 231 次，如 `minecraft:item.minecraft.potion.effect_id.deeperdarker.sculk_affinity.to.item.minecraft.potion.effect_id.deeperdarker.long_sculk_affinity`，0002 行 1393 区）
- 问题: 配方/物品 id 直接用 potion NBT 的 toString（effect_id + 长修饰）作为链内 id，出现 231 次。这是合法的酿造配方，但 id 形态为序列化串，污染链可读性并可能被分类器误判为独立 id。
- 原因: 数据层把 potion 物品按其 NBT 序列化串当作 key，而非规范 recipe key（如 `minecraft:brewing/long_fire_resistance`）。
- 建议: 在导出层对 potion id 做规范化（canonical recipe key），消除 toString 串。minor / suspected。

### F-cobblemon-x-create-4 | chain=0002 | severity=minor | category=worse-alternative
- 位置: A186 `dndesires:hydraulic_compacting/cobblestone_gen` → A187 `create:mixing/lava_from_cobble` → A188 `jei:/synthetic/10986` @ aether:ice_ring（0002 行 ~13679-1396）
- 问题: 为造 enchanting_table 的 obsidian，规划器走 生成 cobblestone → 混出 lava → `jei:/synthetic/10986` @ aether:ice_ring 产 obsidian 的绕路。obsidian 的直达配方为 lava+water（无机器、零成本），此路明显更差且依赖 generation 配方，属 generation-abuse / 机器映射可疑（ice_ring 产 obsidian 语义牵强）。jei:/synthetic 合法，但 @aether:ice_ring 机器映射值得存疑。
- 原因: generation 配方（cobblestone_gen、lava_from_cobble）在图里被当可用生产步骤放大使用。
- 建议: 对 `dndesires:hydraulic_compacting/cobblestone_gen`、`create:mixing/lava_from_cobble` 标记为 generation/排除，obsidian 走 lava+water 直达。minor / suspected。

### F-cobblemon-x-create-5 | chain=0002 | severity=minor | category=generation-abuse
- 位置: 同上 A186-A188 支路（cobblestone_gen + lava_from_cobble 共全批 4 次）
- 问题: 与 F-4 同源——用 generation 类配方制造本可廉价获取的资源（cobblestone、lava）以支持机械链。intent/generation 已知为已处理模式，但本批 aether:ice_ring 合成 obsidian 是该模式的"越界"用例（编造非原版 obsidian 来源）。
- 原因: generation 配方未被排除，且被合成器接出到非原版产物。
- 建议: 同 F-4，排除 generation 配方上的合成放大。minor。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 zamega:raichunitey | other | ok | 仅已知截断/污染；coal→dndesires 钻石为真实但冗余 |
| 0001 createcasing:copper_press | worse-alternative | questionable | F-1 plaque→iron 支路 + F-2 死 redye；直达铁路共存 |
| 0002 create_enchantment_industry:blaze_enchanter | worse-alternative | questionable | F-4/F-5 generation 绕路造 obsidian；potion toString(F-3) |
| 0003 createcasing:copper_mechanical_plough | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0004 minecraft:written_book | data-pollution | questionable | F-3 potion toString；余为截断类 |
| 0005 createcasing:copper_mechanical_harvester | other | ok | 仅已知截断/污染 |
| 0006 create:millstone | other | questionable | F-2 装饰 plaque 污染；余为截断类 |
| 0007 create:gearshift | other | ok | 仅已知截断/污染 |
| 0008 create:gantry_carriage | other | ok | 仅已知截断/污染 |
| 0009 createcasing:copper_clutch | other | ok | 仅已知截断/污染 |
| 0010 zamega:chesnaughtite | other | ok | 仅已知截断/污染 |
| 0011 createcasing:copper_mechanical_drill | other | ok | 仅已知截断/污染 |
| 0012 dndesires:inverse_gearshift | other | ok | 仅已知截断/污染 |
| 0013 create:mechanical_plough | other | ok | 仅已知截断/污染 |
| 0014 minecraft:smooth_red_sandstone_stairs | other | ok | 原版截断类 |
| 0015 createcasing:industrial_iron_mechanical_roller | other | ok | 仅已知截断/污染 |
| 0016 createcasing:copper_adjustable_chain_gearshift | other | ok | 仅已知截断/污染（crafts 3.7e10 溢出） |
| 0017 create:mechanical_bearing | other | ok | 仅已知截断/污染 |
| 0018 create:mechanical_harvester | other | ok | 仅已知截断/污染 |
| 0019 minecraft:smooth_red_sandstone_slab | other | ok | 原版截断类 |
| 0020 sophisticatedstorage:iron_shulker_box | other | ok | 仅已知截断/污染 |
| 0021 createcasing:copper_depot | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0022 create:clutch | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0023 dndesires:roll_table | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0024 bellsandwhistles:polished_granite_pilot | other | ok | 仅已知截断/污染 |
| 0025 createdeco:long_dusk_brick_slab | other | ok | 仅已知截断/污染 |
| 0026 createcasing:refined_radiance_mechanical_roller | other | ok | 仅已知截断/污染 |
| 0027 minecraft:nether_brick_fence | other | ok | 仅已知截断/污染 |
| 0028 createcasing:shadow_steel_mechanical_roller | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0029 createcasing:industrial_iron_deployer | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0030 create:mechanical_drill | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0031 create:waxed_exposed_copper_shingle_slab | other | ok | 仅已知截断/污染 |
| 0032 create:waxed_exposed_copper_shingle_stairs | other | ok | 仅已知截断/污染 |
| 0033 sophisticatedstorage:copper_to_netherite_tier_upgrade | other | ok | 仅已知截断/污染 |
| 0034 create:mechanical_crafter | other | ok | 仅已知截断/污染 |
| 0035 create:mysterious_cuckoo_clock | other | ok | 仅已知截断/污染 |
| 0036 createcasing:shadow_steel_mechanical_roller | other | ok | 仅已知截断/污染 |
| 0037 create:rare_vector_package | other | ok | 仅已知截断/污染 |
| 0038 createcasing:refined_radiance_deployer | worse-alternative | questionable | F-1 plaque→iron 支路 |
| 0039 create_enchantment_industry:enchanting_template | other | ok | 短链，仅截断类 |
| 0040 createdeco:tiled_dusk_brick_slab | other | ok | 仅已知截断/污染 |
| 0041 createcasing:brass_adjustable_chain_gearshift | other | ok | 仅已知截断/污染 |
| 0042 createcasing:shadow_steel_deployer | other | ok | 仅已知截断/污染 |

## 规则建议（给汇总者）
- targetRegex: `^cobblemon:.*_plaque$` —— 排除装饰 plaque 作生产目标；证据：F-2 每色 redye 达 172 次且全死支路、F-1 把 green_plaque 当铁源 raw，涉及链 ≥10（0001/0002/0003/0006/0021/0022/0023/0028/0029/0030/0038）。
- recipeRegex: `cobblemon:.*_plaque_redye_as_coloring` —— 标 recolor 并排除（装饰重染）；证据：全批 1162 步，彩色产物 0 消费。
- recipeRegex: `cobblemon:iron_nugget_from_blasting_plaque` —— 标 recycling-only / 排除作主生产；证据：31 步，输入 green_plaque|+15 污染不可解，且同链有直达铁路；链 0001/0003/0021/0022/0023/0028/0029/0030/0038。
- recipeRegex: `dndesires:hydraulic_compacting/cobblestone_gen|create:mixing/lava_from_cobble` —— 标 generation/排除放大；证据：链 0002，绕路造 obsidian。
- builtin 候选: `minecraft:item.minecraft.potion.effect_id.*` —— data-pollution，导出层规范化 potion id（canonical recipe key）；证据 231 次。建议标签 data-pollution。
- 无新模式时写"无"——本条不适用：本批发现 Cobblemon 装饰 plaque 被系统性当作铁源/死重染，属前三轮未覆盖的新目标类。
