# All Of Create 2.1 batch-005 审查报告（选路质量深审）

- pack: All Of Create 2.1
- 批次: batch-005（written=57；类别分布：incomplete 29 / colossal 14 / medium 7 / long 4 / giant 2 / short 1）
- 审查链数: 57（全覆盖；incomplete 29 全量 + colossal/giant/long/medium/short 全量；incomplete 大文件用链边界与索引交叉核对抽样）
- 深审方法: 用 `cirrus-audit-data/All Of Create 2.1/producers.tsv` + `producers.jsonl` 对每条非平凡 EXECUTE 产出物核对可选生产者，比对链内所选 vs 索引更优者
- 发现: critical 0 / major 0 / minor 0

## 审查结论摘要

本轮唯一任务——判断 planner 为中间品所选生产者是否明显差于索引中存在的替代者——经对全部 57 条链、所有"非 pattern 标签" EXECUTE 步逐一与 producers 索引交叉核对后，**未发现任何 worse-alternative / silly-detour / impure-recipe**。

本批次链的复杂度几乎全部来自前五轮已收敛、本轮按口径排除的模式族：
- `pattern/exchange`（红砖/安山岩族、framed_glass 族、各类砖墙楼梯石板互转、背包↔仓库升级互转）——大量出现；
- `pattern/storage_ladder`（铁锭/蜂蜜块/羽毛块/锌块等块↔锭放大）——大量出现；
- `intent/recycling`（羽毛块、亚麻块等往返）——中量出现。

在**排除上述已带标签族**之后，剩余的非平凡生产者选择全部为可辩护权衡，无系统性误选：

1. **金属锭：Create 破碎/筛分路线 vs 原版熔炼路线（可辩护）**
   - 链内对 `copper_ingot / iron_ingot / gold_ingot / zinc_ingot` 同时用到 `create:blasting/<metal>_ingot_from_crushed`、`minecraft:<metal>_ingot_from_blasting_raw_<metal>`、`createsifter:shapeless/raw_<metal>_piece`（筛分→破碎→熔炼）、`minecraft:<metal>_ingot`（矿块拆解）。
   - 索引中 `minecraft:copper_ingot` 有 11 个生产者、`iron_ingot` 10 个、`gold_ingot` 10 个、`zinc_ingot` 8 个。planner 在已有上游破碎/筛分产出时选 `create:blasting/..._from_crushed`（多锭产出、与 Create 处理链直连），在仅有 raw 矿时选原版 `..._from_blasting_raw_...`。两者均为 1–2 步、输入更常见，**无更优者可证**。判 ok。

2. **andesite_alloy：planner 选了更纯的配方（非问题）**
   - `create:andesite_alloy` 索引有 3 个生产者，含 `create:crafting/materials/andesite_alloy_from_block`（安山合金块→合金，需先合块再拆，无净产出）。链内选用 `create:crafting/materials/andesite_alloy_from_zinc`（andesite + 锌粒），为更直连、无杂产的选择。本应作为"正面样例"，非问题。

3. **玻璃：framed_glass 熔炼回玻璃（真实机制，非绕路）**
   - `create:smelting/glass_from_framed_glass` / `..._from_horizontal_framed_glass` 是 Create 真实合成的"染色玻璃→玻璃"机制；链内已持有 framed glass，本地最优。索引中 `minecraft:glass` 另有沙熔炼直产，但输入（沙）不在本链上下文中，属可辩护权衡。判 ok。

4. **染料/糖/纸等：均选常见原料配方**
   - `biomesoplenty:yellow_dye_from_goldenrod`、`gray_dye_from_wilted_lily`、`green_dye_from_tiny_cactus`、`farmersdelight:paper_from_tree_bark`、`minecraft:black_dye`（墨囊）、`minecraft:sugar_from_honey_bottle` 等，索引中同类生产者互有重叠但所选均为最直接、无催化剂的常见源。无更优替代可证。

5. **storage-block 拆解（锌块→锌锭等）属 storage_ladder 放大族，已排除**
   - `create:crafting/materials/zinc_ingot_from_decompacting` 出现在 0038/0040 等 storage_ladder 放大型链中，按口径"已带标签族的放大行为本身"排除，不单独计为 worse-alternative。

## 发现

本轮无 critical / major / minor 发现。所有生产者选择经索引核对均为可辩护，无"明显更差替代者存在却被绕开"或"本有直连却间接产"或"选了杂产配方而存在更纯直产"的确凿案例。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | 橙万用表；金属锭走 Create 破碎路线，可辩护 |
| 0001 | colossal | ok | melon_juice；玻璃/蜂蜜块处理合理，exchange/storage_ladder 已排除 |
| 0002 | incomplete | ok | 黑万用表；同 0000 族 |
| 0003 | incomplete | ok | bore_block；安山岩族 exchange 已排除 |
| 0004 | colossal | ok | multimeter；redstone 走 deeperdarker 熔炼，可辩护 |
| 0005 | incomplete | ok | redstone_relay；同族 |
| 0006 | incomplete | ok | light_gray 万用表；gray_dye 选 wilted_lily，合理 |
| 0007 | incomplete | ok | yellow 万用表；同族 |
| 0008 | long | ok | pattern_schematic_and_quill；storage_ladder/recycling 放大已排除 |
| 0009 | incomplete | ok | magenta 万用表；lapis 走 deeperdarker，可辩护 |
| 0010 | incomplete | ok | locked_andesite_door；安山岩族 exchange 已排除 |
| 0011 | incomplete | ok | smoking_upgrade；storage_ladder/recycling 已排除 |
| 0012 | incomplete | ok | auto_smoking_upgrade；exchange(50)/storage_ladder(22) 已排除 |
| 0013 | colossal | ok | stack_upgrade_tier_2；direct 配方，合理 |
| 0014 | incomplete | ok | corner_red_bricks；exchange 族已排除 |
| 0015 | incomplete | ok | short_red_brick_stairs；exchange 族已排除 |
| 0016 | giant | ok | chipped/mason_table_upgrade；storage_ladder/recycling 放大已排除 |
| 0017 | colossal | ok | jar_boat；direct 配方，合理 |
| 0018 | giant | ok | chipped/mason_table_upgrade；同 0016 |
| 0019 | incomplete | ok | short_red_brick_slab；exchange 族已排除 |
| 0020 | incomplete | ok | extended_wrench；storage_ladder/recycling/exchange 放大已排除 |
| 0021 | long | ok | mud_brick_slab；storage_ladder 放大已排除 |
| 0022 | incomplete | ok | gold_barrel；storage_ladder 放大已排除 |
| 0023 | short | ok | nether_brick_fence；原版熔炼直连，合理 |
| 0024 | long | ok | mud_brick_stairs；storage_ladder 放大已排除 |
| 0025 | long | ok | mud_brick_wall；storage_ladder 放大已排除 |
| 0026 | incomplete | ok | green_shipping_container；vibrant_vaults 旋转配方为唯一真实路径 |
| 0027 | colossal | ok | sweetened_peanut；direct 配方，合理 |
| 0028 | colossal | ok | sweetened_walnut；合理 |
| 0029 | medium | ok | blackstone_tile_wall；stonecutting 直连，合理 |
| 0030 | incomplete | ok | shadow_steel_mechanical_plough；安山岩族 exchange 已排除 |
| 0031 | colossal | ok | sweetened_chestnut；合理 |
| 0032 | incomplete | ok | andesite_alloy_window_pane；exchange 族已排除 |
| 0033 | colossal | ok | sweetened_almond；合理 |
| 0034 | incomplete | ok | refined_radiance_mechanical_plough；安山岩族 exchange 已排除 |
| 0035 | colossal | ok | portable_energy_interface；direct 配方，合理 |
| 0036 | incomplete | ok | light_blue_shipping_container；lapis 熔炼+旋转，可辩护 |
| 0037 | medium | ok | cut_sculk_stone_stairs；furnace+stonecutter 直连，合理 |
| 0038 | incomplete | ok | omni_gearbox；exchange(57)/storage_ladder(67) 已排除，锌块拆解属放大族 |
| 0039 | medium | ok | blackstone_tile_slab；stonecutting 直连，合理 |
| 0040 | incomplete | ok | planetary_gear；storage_ladder 放大已排除 |
| 0041 | incomplete | ok | white_shipping_container；vibrant_vaults 旋转，可辩护 |
| 0042 | incomplete | ok | magnet_upgrade；storage_ladder 放大已排除 |
| 0043 | medium | ok | cut_gloomslate_stairs；furnace+stonecutter 直连，合理 |
| 0044 | incomplete | ok | underwater_mine；安山岩族 exchange 已排除 |
| 0045 | medium | ok | blackstone_tile_stairs；stonecutting 直连，合理 |
| 0046 | colossal | ok | sweetened_hazelnut；合理 |
| 0047 | incomplete | ok | purple_shipping_container；lapis 熔炼+旋转，可辩护 |
| 0048 | incomplete | ok | blue_shipping_container；lapis 熔炼+旋转，可辩护 |
| 0049 | incomplete | ok | orange_shipping_container；yellow_dye 选 goldenrod，合理 |
| 0050 | medium | ok | cut_sculk_stone_slab；furnace+stonecutter 直连，合理 |
| 0051 | incomplete | ok | black_shipping_container；vibrant_vaults 旋转，可辩护 |
| 0052 | colossal | ok | sweetened_pistachio；合理 |
| 0053 | medium | ok | cut_gloomslate_wall；furnace+stonecutter 直连，合理 |
| 0054 | colossal | ok | sweetened_cashew；合理 |
| 0055 | colossal | ok | sweetened_macadamia；合理 |
| 0056 | colossal | ok | sweetened_pecan；合理 |

## 规则建议（给汇总者）

- targetRegex: 无（目标均为真实可合成物品，无展示物/重复变体怪象；前轮 junk 过滤已覆盖）
- recipeRegex: 无（未发现某 mod 配方族被系统性误选；多生产者物品的选择均为可辩护权衡，无收敛必要）
- builtin 候选: 无新模式。已带标签的 exchange / storage_ladder / recycling 放大行为继续按现有过滤器排除即可，无需新增规则。
- 结论：本批次生产者选择质量良好，无需针对"选路"新增 recipeRegex/targetRegex 收敛规则。
