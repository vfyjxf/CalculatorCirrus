# create-ultimate batch-005 审查报告
- pack: create-ultimate
- 批次: batch-005（written=88；giant 44 / incomplete 28 / short 10 / long 3 / medium 2 / colossal 1）
- 审查链数: 88（全量；short/medium/long/colossal 全读，giant 全 44 条头部 + 代表体 0001/0002/0003/0004/0008/0012/0016/0020/0079，incomplete 全 28 条头部 + 全部 34 个 [UNRESOLVED] 项逐条核对）
- 发现: critical 0 / major 0 / minor 0

## 发现

无新模式。本批所有链均可归入已固化规则或已登记的规划器级问题，未出现任何超出前四轮覆盖范围的新可疑配方族 / 目标类。审查层收敛。

逐类归因（供汇总者对照，非新发现）：
- `pattern/storage_ladder`：原料放大几乎贯穿全部 giant/incomplete 链（raw_copper↔block、raw_iron、redstone、bone、beetroot_crate、lapis_shard、gunpowder_pinch 等 1:9↔9:1 互逆放大用于有净产出的 bounded amplification，声明允许）。CHAIN 0067 的 `candy_cane↔candy_cane_block`、CHAIN 0079 的 `gunpowder_pinch↔gunpowder` 未带标签，但属同类存储阶梯式放大，行为良性，不报。
- `pattern/recolor`：染料混合（beetroot→red_dye、bone→white_dye、pink/orange/gray/light_gray 等）驱动 tfmg 彩色混凝土族。
- `pattern/exchange`：海量 stonecutter 变体环（create/garnished/bellsandwhistles 的 tuff/andesite/diorite 等切割砖族、copper 装饰、waxed 铜瓦等），前四轮已覆盖。
- 规划器级（非链缺陷，仅链级提及）：`|+|N` id 后缀污染（如 `create:cut_tuff_bricks|+14`、`minecraft:calcite|+14`、`createnuclear:lead_block|+1`）导致 base 无法解析；`RANK_TRUNCATED`/`BUDGET_TRUNCATED`/`CASCADE_EXHAUSTED` 命中本可生产的常见物（brass_ingot、cast_iron_ingot、aluminum_ingot、beetroot_crate、blue/pink_dye、packed_mud、各类 cut_* 砖）。这些在 [UNRESOLVED] 行中已显式标注 reason，属预期截断，非 fake-production。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | long | ok | garnished 玻璃/木材交换环，已带 pattern/exchange×4 |
| 0010 | long | ok | waxed copper 存储阶梯+石切，已带 storage_ladder×3 |
| 0067 | long | ok | candy_cane↔block 良性放大，未带标签但属 storage_ladder 类 |
| 0019/0053 | medium | ok | quartz 标准石切链，正常 |
| 0032 | colossal | ok | mastic_resin 染料+交换，已带 exchange/recolor/storage_ladder |
| 0001-0006,0012,0030-0031,0034-0036,0042-0043,0054,0056 | giant | ok | tfmg 彩色混凝土 stair/slab/wall/rebar，recolor+storage_ladder 已覆盖 |
| 0002/0003/0005/0013/0025/0052 | giant | ok | createdeco 铜装饰，storage_ladder 放大铜后石切 |
| 0014/0020/0023/0024/0026/0037-0041/0046-0051/0059-0062 | giant | ok | create_things_and_misc 各 sing 变体，同 copper→石切 模板 |
| 0015/0028 | giant | ok | waxed copper tile，storage_ladder 类 |
| 0008 | giant | ok | mysterious_cuckoo_clock，多子件组装，storage_ladder 已覆盖 |
| 0016 | giant | ok | tfmg:converter 机器，子件 transformer/copper_wire 展开，良性 |
| 0079 | giant | ok | createbigcannons mortar end，gunpowder_pinch 良性放大 |
| 0011/0022/0027/0044/0045/0055/0065/0075/0076/0085 | short | ok | 原版/standard 石切或熔炼链，正常 |
| 0007/0009/0017/0021/0033/0057 | incomplete | ok | bellsandwhistles polished_*_pilot，exchange 环建于 `cut_*|+14` 后缀 base，base 因 RANK_TRUNCATED 未解析（规划器级，非新缺陷） |
| 0018 | incomplete | ok | basic_motor_extension，exchange 环建于 `polished_cut_andesite_stairs|+14`，同上 |
| 0029 | incomplete | ok | distillation_controller，机器链，pending 为截断 |
| 0058 | incomplete | ok | bag_of_grapeshot，pending=black_wool|+14 截断 |
| 0063 | incomplete | ok | cannon_carriage，pending=polished_cut_andesite_stairs|+14 截断 |
| 0064/0068/0070/0074/0080-0087 | incomplete | ok | 原版 tuff/brick/mud 砖石 stair/slab/wall，pending 为 cut_*|+14 或 packed_mud|+14 截断 |
| 0066 | incomplete | ok | redstone_link_wildcard，pending=brass_ingot（CASCADE_EXHAUSTED，可生产） |
| 0069 | incomplete | ok | tfmg:exhaust，pending=cast_iron_ingot|+1（CASCADE_EXHAUSTED，可生产） |
| 0071 | incomplete | ok | copper_leg_thrusters，pending 截断 |
| 0072 | incomplete | ok | headstock_link_and_pin，pending=polished_cut_andesite_stairs|+14 截断 |
| 0073 | incomplete | ok | tfmg:screwdriver，pending=aluminum_ingot（CASCADE_EXHAUSTED，可生产） |
| 0077 | incomplete | ok | createnuclear chestplate，pending=beetroot_crate/blue_dye/pink_dye/brown_cloth|+15/lead_block|+1（均 BUDGET_TRUNCATED，可生产） |
| 0078 | incomplete | ok | tiled_verdant_brick_slab，pending=short_verdant_bricks（CASCADE_EXHAUSTED，可生产） |

## 规则建议（给汇总者）
- targetRegex: 无新增。建议维持既有 blacklist（decompress）与 6 active filters；`bellsandwhistles:polished_*_pilot`、`*_mortar_end`、`*_chestplate` 等目标均为真实合成，无需排除。
- recipeRegex: 无新增。`candy_cane_block`↔`candy_cane`、`gunpowder_pinch`↔`gunpowder` 两处未带 storage_ladder 标签的 1:9↔9:1 互逆放大，建议归入 `pattern/storage_ladder` 收敛（良性，仅作标签补全，不升为排除）。
- builtin 候选: 无跨包新模式。
- 无新模式时写"无"。
