# LuckyBlockChallenge batch-006 审查报告
- pack: LuckyBlockChallenge
- 批次: batch-006（written=41：giant 11 / long 6 / medium 17 / short 7；无 chains-incomplete 文件）
- 审查链数: 41（全量；无 incomplete 文件，故全部细读；无定向 grep 命中 BUDGET/RANK_TRUNCATED，无 crafts≥1e5）
- 发现: critical 0 / major 0 / minor 0

## 发现
**无新模式（审查层收敛）。**

本批 41 条链 100% 为原版 `minecraft:` 合成 / 熔炼 / 切石配方，系统仅出现
`minecraft:crafting_table`(403) / `blast_furnace`(32) / `stonecutter`(5) / `furnace`(2)。
扫描结果：

- 无 `jei:` 命名空间 / 合成 id（`jei:/synthetic`）系统；无 fake-production。
- 无 `reason=BUDGET_TRUNCATED|RANK_TRUNCATED`（grep 全文件 0 命中）。
- `index.tsv` 中 `crafts` 最大值 72，远低于 1e5，无 amount-insanity。
- 所有 EXECUTE step 要么带已固化 `[rule-tag: pattern/storage_ladder]`(278) /
  `pattern/exchange`(12)，要么为合法原版叶子/终产物配方
  （矿石 blasting：`*_from_blasting_*`；木材：`*_wood→*_planks→stick`；终装：*_leggings/*_door/*_axe 等）。
- 未出现任何非原版 mod 系统、任何新配方族或新目标类。

因此本批没有需要新增过滤规则的新可疑配方族 / 目标类。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 golden_chestplate | long | ok | raw_gold_block⇄raw_gold storage_ladder(8)+blast→chestplate；`|+N`后缀已知 |
| 0001 coast_armor_trim_smithing_template | giant | ok | diamond storage_ladder×4 + exchange 复制；"已带标签仍无界放大"(已知) |
| 0002 snout_armor_trim_smithing_template | giant | ok | 同 0001 复制簇 |
| 0003 rib_armor_trim_smithing_template | giant | ok | 同 0001 复制簇 |
| 0004 iron_leggings | giant | ok | raw_iron_block⇄raw_iron storage_ladder(16)+blast→leggings |
| 0005 ladder | medium | ok | any-of 木板全展开(raw13)；stick→ladder 正常 |
| 0006 composter | medium | ok | any-of 台阶全展开(raw13) |
| 0007 campfire | long | ok | coal storage_ladder(8)+blast + 木材；raw51 any-of 木全展开 |
| 0008 polished_andesite_slab | medium | ok | quartz→diorite→andesite→stonecut 材料层级 |
| 0009 polished_andesite_stairs | medium | ok | 同上 |
| 0010 daylight_detector | medium | ok | glass+quartz+slab；any-of 全展开 |
| 0011 writable_book | short | ok | leather+paper+book+ink 正常链 |
| 0012 beehive | short | ok | any-of 木板全展开(raw14) |
| 0013 soul_campfire | medium | ok | raw51 any-of 木/hyphae 全展开 |
| 0014 golden_leggings | long | ok | raw_gold storage_ladder(8)+blast |
| 0015 rail | giant | ok | raw_iron storage_ladder(16)+stick |
| 0016 cauldron | giant | ok | raw_iron storage_ladder(16)+blast |
| 0017 shield | giant | ok | raw_iron storage_ladder(16)+wood |
| 0018 andesite_stairs | medium | ok | quartz→diorite→andesite→stonecut |
| 0019 dark_oak_fence_gate | medium | ok | any-of 木板全展开(raw13) |
| 0020 cartography_table | short | ok | paper+planks 正常 |
| 0021 iron_door | giant | ok | raw_iron storage_ladder(16)+blast |
| 0022 oak_fence_gate | medium | ok | any-of 木板全展开 |
| 0023 tinted_glass | short | ok | glass+amethyst_shard 正常 |
| 0024 acacia_fence_gate | medium | ok | any-of 木板全展开 |
| 0025 jungle_fence_gate | medium | ok | any-of 木板全展开 |
| 0026 smithing_table | giant | ok | raw_iron storage_ladder(16)+wood |
| 0027 mangrove_fence_gate | medium | ok | any-of 木板全展开 |
| 0028 bamboo_fence_gate | medium | ok | bamboo_block + any-of 木板 |
| 0029 mushroom_stew | medium | ok | bowl+mushroom 正常 |
| 0030 cherry_fence_gate | medium | ok | any-of 木板全展开 |
| 0031 birch_fence_gate | medium | ok | any-of 木板全展开 |
| 0032 lead | long | ok | slime_block⇄slime_ball storage_ladder(8) |
| 0033 spruce_fence_gate | medium | ok | any-of 木板全展开 |
| 0034 clock | long | ok | redstone storage_ladder(14)+raw_gold blast |
| 0035 iron_bars | giant | ok | raw_iron storage_ladder(16)+blast |
| 0036 diorite_stairs | short | ok | quartz→diorite→stonecut |
| 0037 yellow_candle | short | ok | candle+yellow_dye 正常 |
| 0038 diorite_wall | short | ok | quartz→diorite→stonecut |
| 0039 iron_axe | giant | ok | raw_iron storage_ladder(16)+stick |
| 0040 golden_axe | long | ok | raw_gold storage_ladder(8)+stick |

## 规则建议（给汇总者）
- 无。本批未出现任何需要新增 targetRegex / recipeRegex / builtin 标签的新配方族或目标类；
  全部命中既有 4 个固化模式（storage_ladder / exchange / recolor / recycling）或纯原版常规合成。

## 规划器级已知问题（仅一句话提及，非本轮新问题）
- `raw_*_block`⇄`raw_*` storage_ladder 有界放大贯穿全批（raw_iron/raw_gold/diamond/coal/redstone/slime_block），
  属"已带标签仍有界放大"，有净产出，不计为 loop。
- smithing_template 链 0001–0003 经 `pattern/exchange` 复制 + diamond storage_ladder 放大，
  属"已带标签仍无界放大"（已知 planner 级问题）。
- `|+N` id 后缀污染普遍出现（如 `acacia_wood|+3`、`cherry_planks|+10`、`coal|+1`、`stripped_crimson_hyphae|+39`），属已知数据污染。
- any-of 木板/木材全展开导致 raw_kinds=13/14/51（fence_gate/campfire/soul_campfire 等），属"any-of 全展开"已知问题。
- quartz→diorite→andesite 切石材料层级为原版唯一合法路径，非 worse-alternative。
