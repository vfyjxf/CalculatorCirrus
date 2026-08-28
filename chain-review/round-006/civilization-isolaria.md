# civilization-isolaria batch-005 审查报告（选路质量深审）

- pack: civilization-isolaria
- 批次: batch-005（written=8；类别分布：colossal 6 / incomplete 2）
- 审查链数: 8（全部 8 条，因批次远小于 60 条配额故全量过；incomplete 2：0002 mega-wide、0007 colossal；其余 colossal 完整链）
- 审查方法：用 `cirrus-audit-data/civilization-isolaria/producers.tsv` + `producers.jsonl` 对每个非平凡中间品 EXECUTE 做生产者索引交叉核对，比对链内所选配方与索引中替代配方之输入/输出。
- 发现: critical 0 / major 0 / minor 0

## 发现

本批次未发现 `worse-alternative` / `silly-detour` / `impure-recipe` / 目标选择怪象。
所有非平凡中间品的生产者选择均合理：或为唯一生产者（无替代可比，按纪律判 ok），或在多生产者项中已选更优/等价配方；其余活动落在已排除的放大家族（storage_ladder / exchange / recycling）内。

### 生产者索引交叉核对证据（多生产者项逐一核过）

| 中间品 | 生产者数 | 链内所选 | 索引替代者 | 结论 |
|---|---|---|---|---|
| `ae2:quartz_block` | 2 | `ae2cs:craft/shaped/quartz_block_from_purified_certus_quartz_crystal`（A35，4 purified→1）与 `ae2:decorative/quartz_block`（A39，4 certus→1） | 二者互为唯一两条 | ok：`ae2cs:purified_certus_quartz_crystal` 为原始输入（RAW），A35 合理消耗之；A39 用于放大，非更差 |
| `ae2:quartz_pillar` | 2 | `ae2:decorative/certus_quartz_pillar_from_stonecutting`（1 cut→1，1:1） | `ae2:decorative/certus_quartz_pillar`（2 cut→1，2:1） | ok：已选更优（1:1）配方 |
| `mekanism:nugget_refined_obsidian` | 3 | `mekanism:nuggets/refined_obsidian`（1 ingot→9 nugget，直连） | `…/nugget_from_smelting`、`…/nugget_from_blasting`（熔整件工具/盔甲→1 nugget） | ok：直连配方已选；另两条带 `intent/recycling` 标签，属排除家族 |
| `mekanism:ingot_refined_obsidian` | 2 | `…/ingot/from_block` | `…/ingot/from_nuggets` | ok：等价（块↔锭），无更差 |
| `mekmm:ultimate_max_chemical_tank` | 2 | `mekmm:max_chemical_tank/ultimate`（标准升级配方） | `mekmm:max_chemical_tank/use_tank/ultimate`（需 dynamic_tank×5） | ok：标准 tier 升级路径，输入更少依赖外围机器 |
| `mekmm:elite_max_chemical_tank` / `mekmm:ultimate_mid_chemical_tank` | 各 2 | `max_chemical_tank/elite`、`mid_chemical_tank/ultimate`（标准升级） | `…use_tank/…` 变体（需 dynamic_tank） | ok：同上 |
| `minecraft:redstone` | 7 | `kubejs:kjs/redstone`（3 molten_core→16） | `redstone_ore`/`deepslate_redstone_ore` blasting、`redstone_block→redstone` 等 | ok：`reliquary:molten_core` 为本链可达原始资源，链内未产出 redstone_ore，故 molten_core 路径为可达来源，非臆造更差替代 |
| `alltheores:osmium_ingot` | 10 | `alltheores:smelting/osmium/raw_blasting`（raw→ingot） | 同族 ore/dust/block/nugget 变体 | ok：标准矿→锭路径 |
| `alltheores:steel_ingot` | 4 | `alltheores:smelting/steel/dust_blasting`（dust→ingot） | block/nugget 变体 | ok：标准 |
| `minecraft:iron_block`/`gold_block`/`diamond`/`quartz`/`lapis_lazuli`/`bone_meal` | 3–10 | 均选 ore/dust/raw 直产或 block 分解 | 同族等价变体 | ok：标准路径，无更差 |
| `minecraft:warped_planks` | 2 | `civilizationcore:easy_warped_planks`（warped_stems→4） | `utilitarian:utility/warped_slab_to_block`（slab→planks，逆向放大） | ok：正向配方已选；slab→planks 为放大家族 |
| `mekanism:advanced_control_circuit`/`elite`/`ultimate`、`mekanism:steel_casing` | 各 1 | 唯一生产者 | 无 | ok（唯一生产者） |

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | colossal | ok | advanced_io_bus_part；AE2 部件链，石英由 purified certus 原始料直产，控制核心等均为唯一生产者 |
| 0001 | colossal | ok | ultimate_replicating_factory；osmium/steel→控制回路→factory 阶梯，全部标准配方 |
| 0002 | incomplete | ok | ultimate_smelting_factory；refined_obsidian 走 recycling/storage 放大（已排除），其余合理；UNRESOLVED 为 BUDGET/CASCADE 截断预期 |
| 0003 | colossal | ok | ultimate_max_chemical_tank；chemical_tank basic→advanced→elite 与 mekmm max 标准升级，无更差替代 |
| 0004 | colossal | ok | ultimate_rolling_mill_factory；同 factory 模板，选路一致合理 |
| 0005 | colossal | ok | ultimate_stamping_factory；同 factory 模板，选路一致合理 |
| 0006 | colossal | ok | ultimate_sawing_factory；precision_sawmill 为唯一生产者，配方直连 |
| 0007 | incomplete | ok | ultimate_crystallizing_factory；chemical_crystallizer / cnc 为唯一生产者，UNRESOLVED 为截断预期 |

## 规则建议（给汇总者）

- targetRegex: 无
- recipeRegex: 无（本批次未观察到任何被系统性误选、可用正则收敛的配方族）
- builtin 候选: 无
- 说明：本批次选路质量良好，未触发任何 recipeRegex 收敛需求。放大行为集中在 storage_ladder / exchange / recycling 家族，已由既有规则覆盖，无需新增。
