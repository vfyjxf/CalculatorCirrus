# Brick's Building 1.4.1 batch-005 选路质量深审报告

- pack: Brick's Building 1.4.1
- 批次: batch-005（written=20，类别分布：incomplete 20；全部 colossal/giant）
- 审查链数: 20（incomplete 20 / complete 0；本批仅 20 条，全量过审，无抽样）
- 方法: 抽每条链**非平凡且未带 rule-tag**的 EXECUTE 步（storage_ladder/exchange/recycling/recolor 已排除），对中间品用 `producers.tsv`(计数)+`producers.jsonl`(sys/id/machines/in/outs) 交叉核对替代生产者。
- 发现: critical 0 / major 0 / minor 0

## 选路核对摘要（本批被审的四类：worse-alternative / silly-detour / impure-recipe / 目标怪象）

本批 20 条链高度同构：目标形为 `<mod>:<color>_dye_bucket` / `<mod>:<color>_cement_bucket` / `railways:track_*_wide` / `create:sequenced_gearshift` / `create_enchantment_industry:experience`。链体绝大多数是**已声明 OK 的有界放大**（iron/copper/zinc/coal 的 block↔ingot↔nugget、crate↔item、slab↔block 交换、crushing 回收）加若干**原版/mod 标准加工**（ore 熔炼/鼓风、Create milling/crushing、basin mixing、spout 灌桶）。对这些非平凡生产者的逐一索引核对结论如下，全部为"选路合理/唯一生产者"：

| 被审物品 / 配方 | 索引生产者数 | 链内所选 | 核对结论 |
|---|---|---|---|
| `minecraft:chorus_fruit`（经 `create_dragons_plus:ending/chorus_fruit_from_apple`） | 3 | encased_fan（apple→chorus_fruit） | 另两条为 `quark:chorus_fruit_blockuncompress`，需 `quark:chorus_fruit_block`（本身由 chorus_fruit 压实而来，循环）。apple 路线是包内**唯一非循环生产者** → ok |
| `minecraft:purpur_block` / `minecraft:purpur_slab` | 4 / 6 | crafting_table（4 popped_chorus_fruit→block；block→slab） | 选定即原版标准配方；其余为 quark slab_to_block（杂产）、stonecutter/saw（等价）。无更优 → ok |
| `minecraft:diamond` / `coal` / `lapis_lazuli`（from ore 鼓风） | 11 / 10 / 10 | `create:blasting/*_from_*_ore` @ encased_fan/blast_furnace | 鼓风为原版 1:1 最快路径，与熔炼等价；另含 crushed→ingot 的 blasSMELT 同类。选路合理 → ok |
| `quark:iron_plate`（chain 0010 轨道链） | 3 | `quark:building/crafting/iron_plate`（iron_ingot→plate） | 索引中另有 `quark:tweaks/crafting/slab_to_block`（**杂产**，输入多种 slab、输出数十种 block）与 vertslab revert。链**选了纯配方** → 选路正确（非 impure） |
| `minecraft:mangrove_slab`（chain 0010） | 5 | crafting_table（mangrove_planks→slab） | 等价配方（stonecutter / mechanical_saw）输入同为 planks → ok |
| `create:raw_zinc` / `zinc_ingot` / `brass_ingot` / `rose_quartz` / `electron_tube` / `sequenced_gearshift`（chain 0009/0012/0013） | 2~8 | Create 标准机械（mixing/blasting/crafting/部署） | 均为 mod 标准路径；brass 另含 block/nugget 压实解压（storage_ladder，已排除）。选路合理 → ok |
| `create_dragons_plus:<color>_dye_bucket`（11 条链的目标） | **1**（`create:fill_*`，需 dye 流体） | `create_dragons_plus:mixing/<color>_dye_from_item`（vanilla 染料 item + water → 染料流体）→ spout 灌桶 | 目标**唯一生产者**；染料流体须由该 mod mixing 产生。底层 `minecraft:<color>_dye` 虽有 9~25 个生产者，但产出的是**物品**而非灌桶所需的**流体**，不能直接替代。选路即 mod 预期路径 → ok（唯一生产者，一律 ok） |
| `createdieselgenerators:<color>_cement_bucket` / `create_enchantment_industry:experience`（桶） | 灌桶配方**唯一**；cement/experience 流体不在生产者索引 | spout 灌桶 | 唯一路径 → ok |
| `railways:track_oak_wide` / `track_mangrove_wide` | **1** | `railways:sequenced_assembly/*` | 唯一生产者 → ok |
| `dramaticdoors:short_waxed_copper_door`（chain 0009） | 4 | copper_door→(honeycomb 上蜡)→stonecutter 截短 | 目标名即"上蜡短门"，上蜡步为必需；选 stonecutter 截短是 mod 机制 → ok |
| `minecraft:white_dye`(bone_meal) / `blue_dye`(lapis) / `light_gray_dye`(white+gray recolor) / `stick` / `bucket` / `iron_nugget` | 18 / 12 / 25 / 13 / 52 / 20 | 原版 crafting / mechanical_crafter | 均为上下文内最廉价输入（calcite/coal/lapis 由放大器富余供给），无更优替代 → ok |
| `farmersdelight:tomato` / `cutting/wild_tomatoes` / `rotten_tomato` / `seed_oil` / `biomass(_pellet)` | 3 / — / — | cutting board + haunting + press |  FarmersDelight/CreateAddition 预期机制；crate↔tomato 为 storage_ladder（已排除） → ok |

## 观察（不构成四类发现，仅标注）

- **染料桶链的 biomass_pellet 旁路未使用**：chain 0001/0002/0004/0005/0006/0014/0015/0016/0017/0019 中，planner 同时展开了 `tomato→seed_oil→biomass→biomass_pellet` 分支，但最终 `create_dragons_plus:mixing/<color>_dye_from_item` 只消费 `minecraft:<color>_dye`(item)+water，biomass_pellet 未被任何下游 EXECUTE 引用（悬空探索支）。这属 planner 树探索冗余，**非 worse-alternative**（最终选的染料流体配方是唯一正确路径，索引中亦无"用 biomass 产该流体"的更优替代），故不列发现。

## 发现

无（本批四类选路问题：worse-alternative / silly-detour / impure-recipe / 目标怪象 均为 0）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | orange_cement_bucket；chorus/purpur 为唯一非循环路径，iron 放大已声明 OK |
| 0001 | incomplete | ok | orange_dye_bucket；染料流体走 mod mixing，biomass 支悬空非选路错 |
| 0002 | incomplete | ok | purple_dye_bucket；purple_dye_from_fluid + item_drain 为预期路径 |
| 0003 | incomplete | ok | experience；super_experience 块/粒 grinding→灌桶，唯一路径 |
| 0004 | incomplete | ok | black_dye_bucket；coal 鼓风+haunting 合理 |
| 0005 | incomplete | ok | light_gray_dye_bucket；white(bone_meal)+gray 重染合理 |
| 0006 | incomplete | ok | light_gray_cement_bucket；calcite/white_dye 合理 |
| 0007 | incomplete | ok | blue_dye_bucket；lapis→blue_dye 合理 |
| 0008 | incomplete | ok | white_cement_bucket；calcite→white_dye→concrete 合理 |
| 0009 | incomplete | ok | short_waxed_copper_door；上蜡+stonecutter 截短为 mod 机制 |
| 0010 | incomplete | ok | track_mangrove_wide；**选了 quark:iron_plate 纯配方**（未选杂产 slab_to_block），iron 放大量级大但为声明 OK |
| 0011 | incomplete | ok | biodiesel；seed_oil/biomass 合法用于燃料目标 |
| 0012 | incomplete | ok | track_oak_wide；raw_zinc→zinc_ingot 标准，唯一生产者 |
| 0013 | incomplete | ok | sequenced_gearshift；andesite_alloy→shaft→cogwheel→brass_casing 标准 Create 链 |
| 0014 | incomplete | ok | lime_dye_bucket；lime_dye_from_fluid + item_drain 预期路径 |
| 0015 | incomplete | ok | green_cement_bucket；cactus→green_concrete 合理 |
| 0016 | incomplete | ok | light_blue_dye_bucket；lapis→blue_dye 合理 |
| 0017 | incomplete | ok | gray_cement_bucket；coal→gray_dye→concrete 合理 |
| 0018 | incomplete | ok | ethanol_bucket；basin_fermenting 合法 |
| 0019 | incomplete | ok | cyan_cement_bucket；lapis→blue_dye→cyan_concrete 合理 |

## 规则建议（给汇总者）

- 无。本批未出现被系统性误选的配方族：所有非平凡生产者要么是**唯一生产者**（染料/水泥/经验桶灌装、铁路轨道），要么选用了**标准/最优/纯配方**（如 chain 0010 主动避开 quark 杂产 slab_to_block 而选 `quark:building/crafting/iron_plate`）。既有 4 个 filter（storage_ladder/exchange/recolor/recycling）已覆盖本批全部放大行为，无需新增 recipeRegex 收敛。
- 附注（非规则，供参考）：染料桶链中 planner 展开的 `biomass_pellet` 悬空支属树探索冗余，可日后用"未引用产出裁剪"类规则收敛，但不属本次选路质量四类的范畴。
