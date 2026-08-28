# farmers-colonies batch-005 审查报告
- pack: farmers-colonies
- 批次: batch-005（written=36；类别分布：short 14 / giant 6 / colossal 6 / medium 6 / trivial 3 / long 1；无 incomplete 类别）
- 审查链数: 36（incomplete 0 / complete 36；小 batch 无大文件，全量逐条阅读 6 个 chains-*.txt 全文，覆盖全部 36 条）
- 发现: critical 0 / major 0 / minor 0

## 发现

无新模式。本批 36 条链全部可归入已固化规则（pattern/storage_ladder、pattern/recolor、intent/recycling），残留怪象均为已知规划器级问题（`|+N` id 后缀污染、any-of 基底坍缩导致 raw_kinds 膨胀、有界放大存储阶梯），无新可疑配方族 / 目标类，审查层收敛。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | short | ok | orange_terracotta：orange_tulip→orange_dye + clay→terracotta，干净 |
| 0001 | short | ok | brown_candle：candle + cocoa_beans→brown_dye，干净 |
| 0002 | short | ok | fletching_table：oak_planks\|+10 为已知 id 后缀污染，链本身正确 |
| 0003 | giant | ok | pink_terracotta：recolor(pink)+bone storage_ladder+clay，已覆盖 |
| 0004 | short | ok | cyan_candle：pitcher_plant→cyan_dye + candle，干净 |
| 0005 | short | ok | ender_chest：ender_eye + obsidian，干净 |
| 0006 | short | ok | black_candle：ink_sac→black_dye + candle，干净 |
| 0007 | short | ok | polished_diorite_slab：quartz+stonecutting，干净 |
| 0008 | giant | ok | light_blue_terracotta：recolor+lapis storage_ladder+clay，已覆盖 |
| 0009 | colossal | ok | anvil：raw_iron↔raw_iron_block storage_ladder x25（有界放大，已覆盖） |
| 0010 | medium | ok | beetroot_soup：raw_kinds=14 为 any-of 基底坍缩已知问题，链正确 |
| 0011 | colossal | ok | warped_hanging_sign：raw_iron storage_ladder + intent/recycling(iron_pickaxe\|+13→nugget→chain) 已覆盖；\|+13 为已知 id 后缀污染 |
| 0012 | colossal | ok | jungle_hanging_sign：与 0011 同构，storage_ladder+recycling，已覆盖 |
| 0013 | giant | ok | magenta_terracotta：recolor x3 + storage_ladder，已覆盖 |
| 0014 | giant | ok | light_gray_terracotta：recolor x2 + bone storage_ladder，已覆盖 |
| 0015 | short | ok | green_candle：cactus→green_dye + candle，干净 |
| 0016 | medium | ok | mossy_stone_brick_wall：stone→stone_bricks→mossy→stonecutting，干净 |
| 0017 | long | ok | blue_terracotta：lapis storage_ladder x8 + clay，已覆盖 |
| 0018 | short | ok | diorite_slab：quartz+stonecutting，干净 |
| 0019 | short | ok | red_candle：beetroot→red_dye + candle，干净 |
| 0020 | colossal | ok | mangrove_hanging_sign：与 0011 同构，已覆盖 |
| 0021 | medium | ok | mossy_stone_brick_slab：stonecutting，干净 |
| 0022 | colossal | ok | bamboo_hanging_sign：与 0011 同构，已覆盖 |
| 0023 | colossal | ok | cherry_hanging_sign：与 0011 同构，已覆盖 |
| 0024 | short | ok | lime_candle：sea_pickle→lime_dye + candle，干净 |
| 0025 | medium | ok | warped_fence：warped_planks+stick；acacia_log\|+3 已知 id 污染 |
| 0026 | medium | ok | mangrove_fence：与 0025 同构，链正确 |
| 0027 | giant | ok | light_blue_stained_glass：recolor+lapis storage_ladder+glass(sand\|+1 污染)，已覆盖 |
| 0028 | short | ok | loom：oak_planks\|+10 已知 id 污染，链正确 |
| 0029 | short | ok | beacon：sand\|+1 已知 id 污染；nether_star+obsidian+glass，干净 |
| 0030 | trivial | ok | calibrated_sculk_sensor：sculk_sensor+amethyst，干净 |
| 0031 | medium | ok | jungle_fence：jungle_planks+stick，干净 |
| 0032 | giant | ok | pink_stained_glass：recolor+glass(sand\|+1 污染)，已覆盖 |
| 0033 | short | ok | domum_ornamentum:paper_extra：paper→paper_extra 两步，干净 |
| 0034 | trivial | ok | minecolonies:baked_salmon：salmon+garlic，干净 |
| 0035 | trivial | ok | minecolonies:composted_dirt：compost+dirt，干净 |

## 规则建议（给汇总者）
- 无新模式，审查层收敛。无新增 targetRegex / recipeRegex / builtin 候选。
- 已知规划器级问题（仅链级提及，非新规则）：`|+N` id 后缀污染出现于 acacia_log\|+3、oak_planks\|+10、sand\|+1、iron_pickaxe\|+13、warped_stem\|+3 等（影响 0002/0010/0011/0025/0027/0028/0029/0032 等多条），属 round-5 已记录哨兵/乱码问题；any-of 基底坍缩致 raw_kinds 膨胀出现于 0010/0002/0025/0026/0031 等（原版常见物全展开入 RAW），属已记录规划器问题。两者均不应作为新配方族上报。
