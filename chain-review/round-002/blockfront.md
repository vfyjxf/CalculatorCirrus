# BlockFront - Official Mod Pack 0.9.0.24b batch-002 审查报告
- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-002（written=75；category 分布：short 30 / giant 17 / medium 16 / incomplete 7 / long 3 / colossal 2）
- 审查链数: 75（incomplete 7 / complete 68；全量审查，无抽样——本批仅 75 条）
- 发现: critical 0 / major 1 / minor 0

## 读法说明
- 全量阅读：short(30) / medium(16) / long(3) / colossal(2) / giant(17) 全部逐条读取；incomplete(7) 中 0009、0011 全读，0014/0018/0025/0030/0034 经 `grep` 确认与 0009/0011 同构（均以 `pattern/recolor` 标签 + 末尾 `[UNRESOLVED] minecraft:black_wool|+14` 收尾）。
- 本批与上轮固化的标签/规则完全一致，未触发任何新的 `pattern/storage_ladder`、`pattern/exchange`、`pattern/recolor` 失控案例。
- 已知规划器级问题（仅在此一句话提及，未展开为发现）：无净产出往返环（raw_iron/raw_gold/raw_copper/diamond/lapis/bone_meal/coal/redstone/netherite 的 block↔item 环）贯穿多数 giant/colossal/long 链；`|+N` 物品 id 后缀污染（red_sand|+1、charcoal|+1、crimson_planks|+10、stripped_acacia_log|+3、black_wool|+14、iron_leggings|+24、amethyst_shard|+9 等）遍布全批；anvil/enchantment 泄漏配方已排除（0008 anvil 仅余已知 raw_iron 环，无泄漏）。

## 发现
### F-blockfront-1 | chain=0061 | severity=major | category=impure-recipe
- 位置: CHAIN 0061，目标 `minecraft:iron_chestplate`，最终配方 `minecraft:sentry_armor_trim_smithing_template_smithing_trim`（system: `minecraft:smithing`，@ `minecraft:smithing_table`），见 A144：`in: sentry_armor_trim_smithing_template x1, amethyst_shard|+9 x1, iron_leggings|+24 x1` → `out: iron_chestplate x1`（<= A142,A141,A143）。
- 问题: 该链用一条**盔甲纹饰（smithing trim）配方**来"生产" `iron_chestplate`。trim 配方语义是**保留基底盔甲类型**仅附加花纹——基底是 `iron_leggings` 却产出 `iron_chestplate`，类型被改变，这不可能是合法生产。结合 `iron_leggings|+24`、`amethyst_shard|+9` 的 `|+N` 污染后缀，根因是**该 smithing_trim 配方在导出数据中输出项被错标为 iron_chestplate**（正确应为被纹饰的 iron_leggings），并被规划器当作 iron_chestplate 的合法生产者。链体因此被 8 段 `diamond_block↔diamond` 无净产出环（A3–A142）放大到 145 actions / 185 crafts。
- 原因: 配方输出数据错标（trim 输出≠基底类型）+ `|+N` id 污染共同导致规划器锚定到错误且极贵的路径；本应有更直接的 `minecraft:iron_chestplate` 合成配方（8 iron_ingot → chestplate，链 RAW 中已存在干净的 `iron_ingot x1`）。
- 建议: 修复/排除输出项与基底类型不符的 `*_smithing_trim` 配方（输出应=基底盔甲），让 iron_chestplate 回落到直接合成；并归一化 `|+N` 后缀以免基底/材料 id 错配。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | medium | ok | rabbit_stew，正常；planks 输入带 `|+N`（已知污染，良性） |
| 0001 | short | questionable(minor) | nether_brick_fence；A1/A3 分别产出 nether_brick x2/x16 双重来源略冗余，crafts=23 偏多但可解释 |
| 0002 | medium | ok | bamboo_mosaic_stairs，正常 |
| 0003 | medium | ok | bamboo_mosaic_slab，正常 |
| 0004 | medium | ok | bamboo_chest_raft；`|+N` 污染输入（已知） |
| 0005 | medium | ok | spruce_chest_boat；`|+N` 污染输入（已知） |
| 0006 | giant | ok | spire_armor_trim；diamond 环 + `pattern/exchange` 标签（已知） |
| 0007 | giant | ok | purple_concrete_powder；lapis 环 + `pattern/recolor`（已知） |
| 0008 | colossal | questionable | anvil；raw_iron↔raw_iron_block 无净产出环 16× 主导（已知规划器环） |
| 0009 | incomplete | questionable | purple_carpet；末尾 `[UNRESOLVED] black_wool|+14`（已知 `|+N` 污染致不可解析，见规则建议） |
| 0010 | short | ok | orange_stained_glass_pane；glass 由 `red_sand|+1` 产出（已知污染） |
| 0011 | incomplete | questionable | orange_banner；同 0009 的 `black_wool|+14` 不可解析 |
| 0012 | giant | ok | light_gray_stained_glass_pane；bone 环 + `pattern/recolor`（已知） |
| 0013 | medium | ok | mossy_stone_brick_stairs，正常 |
| 0014 | incomplete | questionable | light_gray_banner；`black_wool|+14` 不可解析（已知污染） |
| 0015 | giant | ok | light_blue_stained_glass_pane；bone/lapis 环 + `pattern/recolor`（已知） |
| 0016 | giant | ok | blue_stained_glass_pane；lapis 环（已知） |
| 0017 | giant | ok | pink_stained_glass_pane；bone 环 + `pattern/recolor`（已知） |
| 0018 | incomplete | questionable | red_banner；`black_wool|+14` 不可解析（已知污染） |
| 0019 | short | ok | red_stained_glass_pane；`red_sand|+1` 污染（已知） |
| 0020 | giant | ok | magenta_stained_glass_pane；bone/lapis 环 + `pattern/recolor`（已知） |
| 0021 | medium | ok | mossy_stone_brick_wall，正常 |
| 0022 | giant | ok | lodestone；raw_gold 环 + netherite 环（已知） |
| 0023 | medium | ok | mossy_stone_brick_slab，正常 |
| 0024 | short | ok | waxed_oxidized_cut_copper_stairs，stonecutting 正常 |
| 0025 | incomplete | questionable | light_gray_carpet；`black_wool|+14` 不可解析（已知污染，与 0009 同构） |
| 0026 | medium | ok | smooth_quartz_stairs，正常 |
| 0027 | medium | ok | smooth_quartz_slab，正常 |
| 0028 | short | ok | waxed_exposed_cut_copper_slab，正常 |
| 0029 | short | ok | red_nether_brick_stairs，正常 |
| 0030 | incomplete | questionable | red_carpet；`black_wool|+14` 不可解析（已知污染，与 0009 同构） |
| 0031 | short | ok | red_nether_brick_slab，正常 |
| 0032 | short | ok | waxed_oxidized_chiseled_copper，正常 |
| 0033 | short | ok | waxed_weathered_cut_copper_stairs，正常 |
| 0034 | incomplete | questionable | orange_carpet；`black_wool|+14` 不可解析（已知污染，与 0009 同构） |
| 0035 | short | ok | red_nether_brick_wall，正常 |
| 0036 | short | ok | deepslate_tile_stairs，正常 |
| 0037 | short | ok | purpur_stairs，正常 |
| 0038 | short | ok | brick_slab，正常 |
| 0039 | short | ok | smooth_red_sandstone_stairs，正常 |
| 0040 | short | ok | purpur_slab，正常 |
| 0041 | short | ok | brick_wall，正常 |
| 0042 | short | ok | nether_brick_wall，正常 |
| 0043 | short | ok | cracked_deepslate_tiles，正常 |
| 0044 | medium | ok | bamboo_mosaic，正常 |
| 0045 | short | ok | smooth_sandstone_slab，正常 |
| 0046 | short | ok | cut_sandstone_slab，正常 |
| 0047 | short | ok | smooth_sandstone_stairs，正常 |
| 0048 | short | ok | nether_brick_slab，正常 |
| 0049 | giant | ok | trapped_chest；raw_iron 环（已知） |
| 0050 | short | ok | brick_stairs，正常 |
| 0051 | short | ok | smooth_red_sandstone_slab，正常 |
| 0052 | short | ok | nether_brick_stairs，正常 |
| 0053 | short | ok | purpur_pillar，正常 |
| 0054 | short | ok | cracked_nether_bricks，正常 |
| 0055 | short | ok | chiseled_nether_bricks，正常 |
| 0056 | short | ok | deepslate_tile_wall，正常 |
| 0057 | giant | ok | tnt_minecart；raw_iron 环（已知） |
| 0058 | medium | ok | glow_item_frame；`|+N` 污染输入（已知） |
| 0059 | giant | ok | soul_lantern；coal 环 + `charcoal|+1` 污染（已知） |
| 0060 | long | questionable | gold_nugget；raw_gold_block↔raw_gold 无净产出环（已知规划器环） |
| 0061 | colossal | bad | iron_chestplate；经 smithing_trim 配方错标路径生产（见 F-blockfront-1，major） |
| 0062 | giant | ok | lantern；raw_iron 环 + `charcoal|+1` 污染（已知） |
| 0063 | medium | ok | armor_stand；`|+N` 污染输入（已知） |
| 0064 | giant | ok | wild_armor_trim；diamond 环 + `pattern/exchange`（已知） |
| 0065 | giant | ok | crossbow；raw_iron 环（已知） |
| 0066 | giant | ok | dune_armor_trim；diamond 环 + `pattern/exchange`（已知） |
| 0067 | giant | ok | tide_armor_trim；diamond 环 + `pattern/exchange`（已知） |
| 0068 | long | questionable | waxed_copper_door；raw_copper_block↔raw_copper 无净产出环（已知规划器环） |
| 0069 | giant | ok | recovery_compass；redstone + raw_iron 环（已知） |
| 0070 | long | questionable | waxed_copper_trapdoor；raw_copper 无净产出环（已知规划器环） |
| 0071 | medium | ok | andesite_wall，正常 |
| 0072 | medium | ok | polished_andesite，正常 |
| 0073 | short | ok | end_crystal；`red_sand|+1` 污染（已知） |
| 0074 | medium | ok | andesite_slab，正常 |

## 规则建议（给汇总者）
- targetRegex: 无（本批无需要排除的坏目标）。
- recipeRegex: `.*_smithing_trim$` —— 收敛/校验所有盔甲纹饰配方，断言其 output 物品类型 == 基底（base）物品类型；凡 output 类型与 base 不符者（如 `sentry_armor_trim_smithing_template_smithing_trim` 的 base=iron_leggings、output=iron_chestplate）应修复或排除。证据：CHAIN 0061，1 条（+ 同族 trim 配方高度同源，建议全量校验）。
- builtin 候选（跨包通用）：
  1. `junk/normalize-id`（建议标签）—— 全局归一化物品 id 尾部的 `|\+N` 后缀（如 `red_sand|+1`→`red_sand`、`black_wool|+14`→`black_wool`、`iron_leggings|+24`→`iron_leggings`）。证据：全批数十处出现（red_sand|+1、charcoal|+1、crimson_planks|+10、stripped_acacia_log|+3、black_wool|+14、iron_leggings|+24、amethyst_shard|+9 等），且是 7 条 incomplete 链 `[UNRESOLVED] black_wool|+14` 的根因（black_wool 本可解析为原版常见物）。
  2. 已知无净产出 block↔item 环（raw_iron/raw_gold/raw_copper/diamond/lapis/bone_meal/coal/redstone/netherite）仍在本批放大链体（0008 raw_iron 16×、0061 8×、0060/0068/0070 同族），crafts 最高达 185。属已知规划器环，建议对 `loop=allowBoundedAmplification` 加去重/上限约束；本轮未作为新发现上报。
- 无其它新模式。
