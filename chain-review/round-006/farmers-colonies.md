# farmers-colonies batch-005 审查报告（选路质量深审）

- pack: farmers-colonies
- 批次: batch-005（written=36；short 14 / giant 6 / colossal 6 / medium 6 / trivial 3 / long 1）
- 审查链数: 36（incomplete 0 / complete 36；本批共 36 条，未达 100 全量过审）
- 发现: critical 0 / major 2 / minor 0
- 方法: 全批 36 条链逐条抽非平凡中间品 EXECUTE，用 `cirrus-audit-data/farmers-colonies/producers.tsv` 查生产者数、`producers.jsonl` 取替代者明细交叉核对。
- 排除项: 生成器/导出伪影、已带 rule-tag 家族（storage_ladder xN / recycling / recolor）的放大行为本身、junk 外不出现的配方、唯一生产者选择，均不报。

## 发现

### F-farmers-colonies-1 | chain=0017,0008,0027,0013 | severity=major | category=worse-alternative
- 位置: `[A10] EXECUTE minecraft:blue_dye`（0017）、`[A21] EXECUTE minecraft:blue_dye`（0008/0027）、`[A12] EXECUTE minecraft:blue_dye`（0013）
- 问题: 为生产 blue_dye，planner 选定配方 `minecraft:blue_dye`（输入 `minecraft:lapis_lazuli x1`），并为此在链中铺了一条 `pattern/storage_ladder x13` 的 lapis_block↔lapis_lazuli 放大链（深穴青金石矿 blasting + 13 次往返），最终只为拿到 1 个 lapis_lazuli 去染 1 份蓝染料。
- 原因（索引证据）: `producers.tsv` 中 `minecraft:blue_dye  2`，`producers.jsonl` 列两个生产者：
  - `minecraft:blue_dye`（选中的，`in: lapis_lazuli x1`）—— 青金石来料，需挖深穴青金石矿 + 熔炉/高炉 + 储运放大；
  - `minecraft:blue_dye_from_cornflower`（`in: cornflower x1`）—— 1 株矢车菊直接合成，无矿、无放大、无机器链路。
  两配方产量同为 1 blue_dye，但后者输入是采集即得的花，明显更优；planner 系统性舍近求远。属典型 worse-alternative / silly-detour（绕开直连花源）。
- 建议: 对 blue_dye 收敛，优先 `minecraft:blue_dye_from_cornflower`；规则层给 `*_from_cornflower` 系染料配方更高权重。

### F-farmers-colonies-2 | chain=0008,0027,0003,0013,0014,0032 | severity=major | category=worse-alternative
- 位置: `[A10] EXECUTE minecraft:white_dye`（0008/0027）、`[A12] EXECUTE minecraft:white_dye`（0003/0032）、`[A26] EXECUTE minecraft:white_dye`（0013）、`[A13] EXECUTE minecraft:white_dye`（0014）
- 问题: 为生产 white_dye，planner 选定配方 `minecraft:white_dye`（输入 `minecraft:bone_meal x1`），并在链中铺 `pattern/storage_ladder x5`（bone→bone_meal→bone_block→bone_meal…）放大链只为凑够 1 份骨粉，再染 1 份白染料。
- 原因（索引证据）: `producers.tsv` 中 `minecraft:white_dye  2`，`producers.jsonl` 列两个生产者：
  - `minecraft:white_dye`（选中的，`in: bone_meal x1`）—— 需刷骨、储运放大；
  - `minecraft:white_dye_from_lily_of_the_valley`（`in: lily_of_the_valley x1`）—— 1 株铃兰直接合成，无放大、无机器链路。
  两配方产量同为 1 white_dye，后者输入为采集花，明显更优；planner 同样系统性舍花取骨。
- 建议: 对 white_dye 收敛，优先 `minecraft:white_dye_from_lily_of_the_valley`；与 F-1 同为染料源路由偏好问题。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | short | ok | orange_terracotta：orange_tulip→dye→terracotta，唯一生产者路径 |
| 0001 | short | ok | brown_candle：cocoa→brown_dye，唯一源 |
| 0002 | short | ok | fletching_table：木板/燧石唯一配方 |
| 0003 | giant | bad | pink_terracotta：white_dye 取骨粉（见 F-2） |
| 0004 | short | ok | cyan_candle：pitcher_plant→cyan_dye 唯一源 |
| 0005 | short | ok | ender_chest：blaze_powder/ender_eye/obsidian 唯一 |
| 0006 | short | ok | black_cye 取 ink_sac（wither_rose 等价，非更优） |
| 0007 | short | ok | polished_diorite_slab：quartz 取 blasting（高炉对矿更优，可辩护），diorite 唯一 |
| 0008 | giant | bad | light_blue_terracotta：blue(lapis, F-1)+white(bone, F-2) 双错选 |
| 0009 | colossal | ok | anvil：iron 储运放大属已处理 storage_ladder，无未处理错选 |
| 0010 | medium | ok | beetroot_soup 唯一 |
| 0011 | colossal | ok | warped_hanging_sign：chain 靠 recycling/storage_ladder，已处理家族 |
| 0012 | colossal | ok | jungle_hanging_sign：同上，已处理家族 |
| 0013 | giant | bad | magenta_terracotta：blue(lapis, F-1)+white(bone, F-2) 双错选 |
| 0014 | giant | bad | light_gray_terracotta：white(bone, F-2)，black(ink) 正常 |
| 0015 | short | ok | green_candle：cactus→green_dye 唯一源 |
| 0016 | medium | ok | mossy_stone_brick_wall：stonecutter 唯一 |
| 0017 | long | bad | blue_terracotta：blue_dye 取 lapis 放大（见 F-1） |
| 0018 | short | ok | diorite_slab：同 0007，quartz blasting 可辩护 |
| 0019 | short | ok | red_dye 取 beetroot（农包内可再生作物，可辩护） |
| 0020 | colossal | ok | mangrove_hanging_sign：recycling/storage_ladder 已处理 |
| 0021 | medium | ok | mossy_stone_brick_slab：stonecutter 唯一 |
| 0022 | colossal | ok | bamboo_hanging_sign：recycling/storage_ladder 已处理 |
| 0023 | colossal | ok | cherry_hanging_sign：recycling/storage_ladder 已处理 |
| 0024 | short | ok | lime_candle：sea_pickle→lime_dye 唯一源 |
| 0025 | medium | ok | warped_fence 唯一 |
| 0026 | medium | ok | mangrove_fence 唯一 |
| 0027 | giant | bad | light_blue_stained_glass：blue(lapis, F-1)+white(bone, F-2) 双错选 |
| 0028 | short | ok | loom 唯一 |
| 0029 | short | ok | beacon：glass/obsidian/nether_star 唯一 |
| 0030 | trivial | ok | calibrated_sculk_sensor 唯一 |
| 0031 | medium | ok | jungle_fence 唯一 |
| 0032 | giant | bad | pink_stained_glass：white(bone, F-2)，red(beetroot) 可辩护 |
| 0033 | short | ok | paper_extra 唯一 |
| 0034 | trivial | ok | baked_salmon 唯一 |
| 0035 | trivial | ok | composted_dirt 唯一 |

## 规则建议（给汇总者）
- targetRegex: 无（本批目标均为合法方块/物品，无展示物/重复变体/无意义目标）
- recipeRegex: `^minecraft:(blue_dye|white_dye)$` —— 收敛压制裸 id 的矿物/骨粉源，改用 `*_from_cornflower` / `*_from_lily_of_the_valley` 花源；证据 7 条链（0017/0008/0027/0013/0003/0014/0032）。另对 `quartz_from_blasting` vs `quartz`（furnace）之类同效配方，建议允许高炉优先，非必须收敛。
- builtin 候选: 无（染料源偏好属路由权重问题，非 junk/duplicate/pattern 标签族）。
- 说明: hanging_sign / anvil 等 colossal 链的 chain、iron_nugget 走 recycling+storage_ladder，但该两配方族本身已带 rule-tag 且放大行为已排除，无未处理更优生产者（chain 在索引中仅 1 个生产者 `minecraft:chain`，iron_nugget 的直合成 `minecraft:iron_nugget` 亦属 storage_ladder 家族），故不另立发现。
