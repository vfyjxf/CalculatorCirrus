# tfc-enhanced batch-005 审查报告
- pack: tfc-enhanced
- 批次: batch-005（written=43；giant 19 / short 7 / colossal 6 / incomplete 6 / medium 4 / long 1）
- 审查链数: 43（不足 100 全量审阅；incomplete 6 / complete 37；抽样说明：6 个 colossal + 19 个 giant + 6 个 incomplete + 4 medium + 7 short + 1 long 全部读取，含 8 处 UNRESOLVED 与 8 处 reason=BUDGET/RANK_TRUNCATED 逐条核验）
- 发现: critical 0 / major 0 / minor 0

## 发现
**无新模式。** 本轮未发现超出已固化规则（pattern/storage_ladder、pattern/exchange、pattern/recolor、intent/recycling、intent/generation、quality/junk）与 round-5 已知规划器级问题之外的新可疑配方族或目标类。所有 43 条链均落入既有覆盖或已知规划器级问题，审查层收敛。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | colossal | ok | magenta_candle：recolor/storage_ladder/exchange 已标签；stick 阶梯深但净产出；`metal/knife/steel\|+12` 为已知 `\|+N` 污染 |
| 0001 | giant | ok | clay_oven_hopper：hopper + 木/棍阶梯，正常 |
| 0002 | medium | ok | brick_slab：clay_oven_bottom_to_clay(intent/recycling) + stonecutting，已标签 |
| 0003 | giant | ok | scribing_table/maple：wood→lumber→planks→slab 阶梯，正常 TFC 木作 |
| 0004 | colossal | ok | light_gray_candle：recolor/bone storage_ladder 已标签 |
| 0005 | medium | ok | brick_stairs：同 0002 模板，已标签 |
| 0006 | colossal | ok | blue_candle：lapis storage_ladder + blue_dye，已标签 |
| 0007 | incomplete | ok | recovery_compass：主目标经 compass 可达；2 UNRESOLVED(stick_bunch、metal/saw/steel\|+8) 均 BUDGET_TRUNCATED，真实物被预算截断（已知规划器级问题） |
| 0008 | short | ok | stainless_steel_greenhouse_port：玻璃+钢管，正常 |
| 0009 | short | ok | beekeeper_helmet：皮革帽+布，正常 |
| 0010 | incomplete | ok | barrel_rack：主目标可达；2 UNRESOLVED(twig/kapok\|+20、metal/saw/steel\|+8) BUDGET_TRUNCATED，已知污染+截断 |
| 0011 | colossal | ok | brown_candle：brown_dye 直染，已标签 |
| 0012 | giant | ok | scribing_table/hickory：同 0003 模板 |
| 0013 | medium | ok | brick_wall：同 0002 模板 |
| 0014 | giant | ok | scribing_table/sequoia：同 0003 模板 |
| 0015 | giant | ok | mixing_bowl：slime_block↔glue storage_ladder 已标签 + stick 阶梯 |
| 0016 | giant | ok | chest/hickory：wood/lumber 阶梯，正常 |
| 0017 | giant | ok | jarring_station：stick 阶梯 + beeswax，正常 |
| 0018 | giant | ok | alabaster/bricks：chisel→brick→bricks，正常 |
| 0019 | giant | ok | chest/sequoia：同 0016 模板 |
| 0020 | giant | ok | lodestone：netherite_block↔ingot、raw_gold_block↔raw_gold storage_ladder 已标签 |
| 0021 | giant | ok | spiced_flour：stick 阶梯 + spice，正常 |
| 0022 | incomplete | ok | red_carpet：主目标可达；UNRESOLVED(black_wool\|+14 x2) RANK_TRUNCATED，已知 `\|+N` 污染+截断 |
| 0023 | incomplete | ok | yellow_carpet：同 0022 RANK_TRUNCATED 模式 |
| 0024 | colossal | ok | ashtray：19 层 stick_bundle↔stick_bunch 可转化阶梯有净产出（非 no-op 环），正常但深度膨胀 |
| 0025 | incomplete | ok | lime_carpet：bone_block 多次 storage_ladder + dye；UNRESOLVED(black_wool\|+14) RANK_TRUNCATED |
| 0026 | short | ok | reinforced_fire_bricks：fire_bricks + 铁板，正常 |
| 0027 | giant | ok | treated_wood_greenhouse_port：chisel 清洁 + stick 阶梯，正常 |
| 0028 | long | ok | redstone_lamp：redstone_block↔redstone storage_ladder 已标签 |
| 0029 | short | ok | red_kaolin_clay：pink→red kaolin recolor 已标签 |
| 0030 | colossal | ok | sealed_brick_trapdoor：同 0024 stick 阶梯深但净产出 |
| 0031 | giant | ok | drying_bricks/andisol：hay_block↔straw↔thatch 阶梯（hay/wheat I/O 不自洽，已知规划器级问题）+ storage_ladder 已标签 |
| 0032 | giant | ok | rock/smooth/diorite_wall：chisel + stick 阶梯，正常 |
| 0033 | giant | ok | rock/smooth/shale_wall：同 0032 模板 |
| 0034 | giant | ok | wine_shelf/aspen：stick 阶梯 + treated_lumber，正常 |
| 0035 | short | ok | waxed_exposed_cut_copper_stairs：honeycomb 蜡化 + stonecutting，正常 |
| 0036 | short | ok | decorated_pot：clay_oven_bottom_to_clay recycling 已标签 |
| 0037 | short | ok | waxed_exposed_chiseled_copper：同 0035 模板 |
| 0038 | giant | ok | wine_shelf/acacia：同 0034 模板 |
| 0039 | incomplete | ok | pink_carpet：dye_*_wool→dye_*_carpet 重染级联（真实原版染配方，非假生产）；UNRESOLVED(black_wool\|+14) RANK_TRUNCATED |
| 0040 | giant | ok | wine_shelf/sequoia：同 0034 模板 |
| 0041 | medium | ok | mossy_cobblestone_wall：rock exchange 已标签；RAW 含 22 种 cobble（any-of 岩石 tag 全展开，已知规划器级问题）；`gabbro\|+21` 为 `\|+N` 污染 |
| 0042 | giant | ok | wine_shelf/kapok：同 0034 模板 |

## 规则建议（给汇总者）
- 无新模式。本轮所有结构均已被既有规则（pattern/storage_ladder、pattern/exchange、pattern/recolor、intent/recycling）或 round-5 已知规划器级问题覆盖，无需新增 targetRegex / recipeRegex / builtin 标签。
- 仅作观察（非新规则需求）：TFC 的 `stick_bundle↔stick_from_bundle↔stick_bunch↔stick_from_bunch` 可转化阶梯在本批大量出现（0000/0001/0003/0012/0014/0016/0019/0021/0024/0027/0030/0032/0033/0034/0038/0040/0042 等），有净产出、非 no-op 环，按口径不算问题，但持续造成 giant/colossal 深度膨胀；若后续收敛率不足，可评估将「TFC 棍/束可转化阶梯」并入 storage_ladder 概念作排除级收敛，但当前不构成新可疑族。
- `|+N` id 后缀污染（steel\|+12、aspen\|+41、kapok\|+20、gabbro\|+21、black_wool\|+14 等）与 BUDGET/RANK_TRUNCATED 截断（8 处 UNRESOLVED 均为真实物）为 round-5 已列规划器级问题，不在本轮新报。
