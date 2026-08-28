# BlockFront - Official Mod Pack 0.9.0.24b batch-005 审查报告

- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-005（written=41；类别分布：medium 17 / giant 11 / short 7 / long 6）
- 审查链数: 41（全量覆盖，无 incomplete 文件；short+medium+long+giant 全读）
- 发现: critical 0 / major 0 / minor 0

## 审查方法说明

本轮为「选路质量深审」：对每条链的非平凡中间品 EXECUTE（跳过 `|+N` 生成器伪影与 `storage_ladder`/`exchange` 已带 rule-tag 的放大步骤），用 `cirrus-audit-data/BlockFront - Official Mod Pack 0.9.0.24b/producers.tsv` + `producers.jsonl` 核对可选生产者，判断 planner 所选是否明显更差。

排除项（红线）：`|+N` 伪影 id（如 `crimson_planks|+10`、`stripped_acacia_log|+3`、`red_sand|+1`、`dark_oak_log|+39`、`charcoal|+1`、`bamboo_block|+1`）、`storage_ladder`（raw_iron/raw_gold/diamond/coal/slime/redstone 块↔锭放大，x16~x32）、`exchange`（armor trim smithing template）。这些族放大行为本身不报。

## 生产者交叉核对结果（关键中间品）

| 中间品 | 生产者数 | planner 选择 | 索引替代者 | 判定 |
|---|---|---|---|---|
| polished_andesite_slab | 3 | `…_from_andesite_stonecutting`（1 andesite→1 slab，石刀，最直连） | crafting(3×polished_andesite)、`…_from_polished_andesite_stonecutting`(需先产 polished_andesite) | **ok**（已选最省步） |
| polished_andesite_stairs | 3 | `…_from_andesite_stonecutting`（1 andesite→1 stairs） | 同上两类（均需额外产 polished_andesite） | **ok** |
| yellow_dye | 2 | `yellow_dye_from_dandelion`（dandelion→1） | `yellow_dye_from_sunflower`（sunflower→1） | **ok**（1:1 等价，链内有 dandelion） |
| stick | 2 | 链内为 `crimson_planks|+10`（伪影，排除） | `stick`（planks 标签）、`stick_from_bamboo_item` | **ok**（输入等价） |
| quartz | 2 | `quartz_from_blasting`（blast_furnace，更快） | `quartz`（furnace 熔炼，同输入同输出） | **ok**（ blasting 严格更优） |
| iron_ingot | 8 | `iron_ingot_from_blasting_raw_iron`（输入 raw_iron，blast） | 仅另 `…_from_smelting_raw_iron` 接受 raw_iron（furnace 较慢）；其余接受 ore/block/nugget（输入不符） | **ok**（输入约束最优，且属 storage_ladder 族） |
| gold_ingot | 10 | `gold_ingot_from_blasting_raw_gold`（输入 raw_gold） | 同上逻辑 | **ok**（storage_ladder 族） |
| coal | 5 | `coal_from_blasting_coal_ore`（输入 coal_ore，blast） | `coal_from_smelting_coal_ore`（furnace 较慢） | **ok**（输入约束最优） |
| diamond | 5 | `diamond_from_blasting_deepslate_diamond_ore`（输入 deepslate_diamond_ore） | 其余接受不同矿石输入 | **ok**（exchange 族） |
| leather / paper / book / writable_book / glass / tinted_glass / candle / yellow_candle / ladder / acacia_slab / composter / campfire / soul_campfire / daylight_detector / mushroom_stew / 9×fence_gate / bamboo_block / bowl / acacia_wood / diorite / andesite | 各 1 | — | 无替代者 | **ok**（唯一生产者，依纪律判 ok） |

## 发现

本轮在四类（worse-alternative / silly-detour / impure-recipe / 目标选择怪象）中**无任何发现**。

- **worse-alternative**：所有多生产者中间品，planner 均选了输入最少/最直连/更快机器（石刀 from andesite、blast furnace）或等价输入（dandelion vs sunflower）的方案；其余均为唯一生产者。
- **silly-detour**：andesite 必经 diorite（→quartz+cobblestone），再石刀直出 polished_andesite_slab/stairs，无绕路；fence_gate/工具/盔甲链均为标准直链。
- **impure-recipe**：未发现「选多杂产而存在更纯直接配方」的情况；所有选用配方均为单输出或最纯路径。
- **目标选择怪象**：41 个目标均为可正常合成的合法原版物品（盔甲、trim 模板、各类栅栏门、各类石制品、工具、钟、引线、染料蜡烛等），无展示物/无意义/重复变体目标。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | long | ok | raw_gold→gold_ingot→chestplate，storage_ladder 放大，选路合理 |
| 0001 | giant | ok | coast trim（exchange+storage_ladder），选路合理 |
| 0002 | giant | ok | snout trim（exchange+storage_ladder），选路合理 |
| 0003 | giant | ok | rib trim（exchange+storage_ladder），选路合理 |
| 0004 | giant | ok | raw_iron→iron_ingot→leggings，选路合理 |
| 0005 | medium | ok | stick→ladder，标准直链 |
| 0006 | medium | ok | acacia_slab/composter，composter 单一生产者，输入等价 |
| 0007 | long | ok | coal→campfire，storage_ladder+伪影输入，选路合理 |
| 0008 | medium | ok | quartz→diorite→andesite→石刀 slab，最直连 |
| 0009 | medium | ok | quartz→diorite→andesite→石刀 stairs，最直连 |
| 0010 | medium | ok | glass+quartz+slab→daylight_detector，标准 |
| 0011 | short | ok | rabbit_hide→leather→book→writable_book，标准 |
| 0012 | short | ok | acacia→planks→beehive，标准 |
| 0013 | medium | ok | stick+log→soul_campfire，唯一生产者 |
| 0014 | long | ok | raw_gold→gold_ingot→leggings，选路合理 |
| 0015 | giant | ok | stick+iron_ingot→rail，选路合理 |
| 0016 | giant | ok | raw_iron→iron_ingot→cauldron，选路合理 |
| 0017 | giant | ok | raw_iron→iron_ingot+planks→shield，选路合理 |
| 0018 | medium | ok | 同 0008 族，andesite_stairs 最直连 |
| 0019 | medium | ok | dark_oak 木链→fence_gate，标准 |
| 0020 | short | ok | paper+planks→cartography_table，标准 |
| 0021 | giant | ok | raw_iron→iron_ingot→iron_door，选路合理 |
| 0022 | medium | ok | oak 木链→fence_gate，标准 |
| 0023 | short | ok | glass+amethyst→tinted_glass，唯一生产者 |
| 0024 | medium | ok | acacia 木链→fence_gate，标准 |
| 0025 | medium | ok | jungle 木链→fence_gate，标准 |
| 0026 | giant | ok | raw_iron→iron_ingot+planks→smithing_table，选路合理 |
| 0027 | medium | ok | mangrove 木链→fence_gate，标准 |
| 0028 | medium | ok | bamboo 木链→fence_gate，标准 |
| 0029 | medium | ok | bowl+mushroom→mushroom_stew，标准 |
| 0030 | medium | ok | cherry 木链→fence_gate，标准 |
| 0031 | medium | ok | birch 木链→fence_gate，标准 |
| 0032 | long | ok | slime→lead，storage_ladder 放大，选路合理 |
| 0033 | medium | ok | spruce 木链→fence_gate，标准 |
| 0034 | long | ok | redstone+gold→clock，storage_ladder 放大，选路合理 |
| 0035 | giant | ok | raw_iron→iron_ingot→iron_bars，选路合理 |
| 0036 | short | ok | quartz→diorite→石刀 diorite_stairs，最直连 |
| 0037 | short | ok | candle+yellow_dye→yellow_candle，标准 |
| 0038 | short | ok | quartz→diorite→石刀 diorite_wall，最直连 |
| 0039 | giant | ok | raw_iron→iron_ingot+stick→iron_axe，选路合理 |
| 0040 | long | ok | acacia→stick+raw_gold→golden_axe，选路合理 |

## 规则建议（给汇总者）

- targetRegex：无（本批 41 目标均为合法可合成原版物品，无展示物/无意义/重复变体）。
- recipeRegex：无（未发现任何被系统性误选的配方族；多生产者中间品均已选最优/等价方案）。
- builtin 候选：无新模式。
- 结论：**无**（本批选路质量良好，无需新增收敛规则）。
