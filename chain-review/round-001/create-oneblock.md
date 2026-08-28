# create-oneblock batch-001 审查报告
- pack: create-oneblock
- 批次: batch-001（written=150，类别分布：incomplete 150 / complete 0；colossal 与 mega-wide 占比高）
- 审查链数: 150（incomplete 150 / complete 0；全量审查：150 条链头部元数据全读，深度细读代表链 0000/0001/0008/0044/0088/0134/0148 及 0140/0142/0145/0146 头部；其余按头部信号归类）
- 发现: critical 0 / major 2（影响 24 条链）/ minor 1

## 背景与口径说明
本批 150 条链全部为 `incomplete`（create-oneblock 为天空岛式整合包，大量原料在包内无生产者，`+raw N` 叶子与 `[UNRESOLVED]` 属预期，不重复报告）。全部 `system` 仅 `minecraft:crafting / stonecutting / smithing / smelting` 与 `jei:category/<mod>/<机器>`（真实机器配方，合法，非 fake-production）。未检出 reward/villager/trade/market/JER 等假生产信号。

## 发现
### F-create-oneblock-1 | chain=0001 | severity=major | category=amount-insanity
- 位置: CHAIN 0001 `[A1] EXECUTE minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore x2147483647 @ create:encased_fan`（in `deepslate_lapis_ore x2147483647` → out `lapis_lazuli x2147483647`）；同链 `[A7]→[A8]` 将 `lapis_lazuli x2147483647` 与 `lapis_block x2147483647` 互转。另见 CHAIN 0088 `[A55]` `minecraft:diamond x2147483647`、CHAIN 0134 `[A63]` `createdeco:netherite_ingot x2147483647`（in `netherite_nugget x19327352823`）。
- 问题: 规划器把 `lapis/diamond/netherite` 等贵重资源放大到 `2147483647`（=Integer.MAX_VALUE，整数溢出哨兵值）量级，再经 block↔ingot/nugget 互转环膨胀，最终只为产出 1 个目标物品（如 brass_chunk_loader、feeding_upgrade）。crafts 统计达 20860395780（0001）、166346028640（0088）、5069809575（0134）等。该量级无任何净收益，纯属放大失控。
- 原因: `loop=allowBoundedAmplification` 在块/矿/粒互转环上未设上限，放大到整数天花板后仍以 `2147483647` 进入链，属规划器/数据层的量纲溢出。builtin 标签（exchange/recycling）只覆盖了转换"模式"，未约束"放大倍数"，故未被排除。
- 建议: 对含 `x2147483647` 的链直接标记为 `pattern-amplification-overflow`（junk）并收敛；建议给 bounded-amplification 加硬上限（如 ≤1e6），将 Integer.MAX_VALUE 视为 data-pollution 哨兵。
- 影响链（6）: 0001, 0002, 0088, 0096, 0118, 0134。

### F-create-oneblock-2 | chain=0008 | severity=major | category=amount-insanity
- 位置: CHAIN 0008 `crafts=118383651`；CHAIN 0032 `createdeco:netherite_nugget x1743392565`、CHAIN 0039 `minecraft:wheat x1743392241`、CHAIN 0146 `minecraft:deepslate_diamond_ore x2324524230`、CHAIN 0044 `[A181]` `minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore x43046721`（=3^16，几何放大）、CHAIN 0140/0142 `minecraft:saddle x161414428`、CHAIN 0145 `minecraft:kelp x774840978`。
- 问题: 与 F-1 同源——`allowBoundedAmplification` 在矿石→锭→块→粒互转环上几何放大，中间量达数千万至 23 亿（crafts 6.2M–2.8B），但目标仅 1 个物品，无净收益。本组 18 条链的 crafts 落在 6.2M–2.8B，且呈现明显的同簇数值（如 5380xxxx、71283xxxx、2086039xxxx），表明同一放大源按目标所需批量重复。
- 原因: 同 F-1，`allowBoundedAmplification` 未设上限；天空岛下目标原料本身稀缺，规划器为凑足输入反复套用块/粒互转，量级失控。
- 建议: 与 F-1 共用过滤——对所有 `crafts≥1e6` 或链内出现 `x≥1e7` 中间量的链标记 amount-insanity 并收敛；优先排查 block/ore/nugget 互转配方族。
- 影响链（18）: 0004, 0008, 0013, 0032, 0039, 0044, 0061, 0068, 0075, 0076, 0093, 0099, 0100, 0114, 0140, 0142, 0145, 0146。

### F-create-oneblock-3 | chain=0000 | severity=minor | category=unresolved-expected-item
- 位置: 全批 `[UNRESOLVED]` 高频原版常见物：`minecraft:stone_axe`(44×)、`minecraft:diamond_hoe`(14×)、`minecraft:acacia_door`(17×)、`minecraft:cherry_door`(7×)、`minecraft:bamboo_door`(11×)；CHAIN 0000 另见 `[UNRESOLVED] minecraft:lava x1000`。
- 问题: 这些为原版可直接合成的物品（斧/锄需木板+木棍，门需对应木板），却被标 `[UNRESOLVED]`（reason 多为 BUDGET/RANK_TRUNCATED）。疑为规划器预算耗尽放弃，而非包内真无配方。
- 原因（推测，证据不足）: 这些物品的上游（木板/木棍/圆石）在天空岛里本身是 `+raw` 未解析原料，故链式级联成 unresolved；属 unresolved-cascade 而非独立可解。鉴于本包原料普遍 raw，不能断定其"本应可解析"，故降为 minor + suspected。
- 建议: 若为级联，随 F-1/F-2 收敛原料后即可消除；若确为真实可解却被预算截断，建议放宽该类目标的搜索预算或单独补配方。不单独建规则。

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |
| 0000 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0001 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=20860395780 |
| 0002 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=20860398718 |
| 0003 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0004 | amount-insanity | questionable | 放大失控 crafts=21523824 |
| 0005 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0006 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0007 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0008 | amount-insanity | questionable | 放大失控 crafts=118383651 |
| 0009 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0010 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0011 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0012 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0013 | amount-insanity | questionable | 放大失控 crafts=21523581 |
| 0014 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0015 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0016 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0017 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0018 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0019 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0020 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0021 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0022 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0023 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0024 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0025 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0026 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0027 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0028 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0029 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0030 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0031 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0032 | amount-insanity | questionable | 放大失控 crafts=1155087246 |
| 0033 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0034 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0035 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0036 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0037 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0038 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0039 | amount-insanity | questionable | 放大失控 crafts=2823237524 |
| 0040 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0041 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0042 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0043 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0044 | amount-insanity | questionable | 放大失控 crafts=53855455 |
| 0045 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0046 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0047 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0048 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0049 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0050 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0051 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0052 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0053 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0054 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0055 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0056 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0057 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0058 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0059 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0060 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0061 | amount-insanity | questionable | 放大失控 crafts=53809259 |
| 0062 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0063 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0064 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0065 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0066 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0067 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0068 | amount-insanity | questionable | 放大失控 crafts=118383424 |
| 0069 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0070 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0071 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0072 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0073 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0074 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0075 | amount-insanity | questionable | 放大失控 crafts=53809036 |
| 0076 | amount-insanity | questionable | 放大失控 crafts=53808915 |
| 0077 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0078 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0079 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0080 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0081 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0082 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0083 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0084 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0085 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0086 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0087 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0088 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=166346028640 |
| 0089 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0090 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0091 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0092 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0093 | amount-insanity | questionable | 放大失控 crafts=53808744 |
| 0094 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0095 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0096 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=20860390132 |
| 0097 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0098 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0099 | amount-insanity | questionable | 放大失控 crafts=53808924 |
| 0100 | amount-insanity | questionable | 放大失控 crafts=53808924 |
| 0101 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0102 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0103 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0104 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0105 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0106 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0107 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0108 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0109 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0110 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0111 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0112 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0113 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0114 | amount-insanity | questionable | 放大失控 crafts=6281552 |
| 0115 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0116 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0117 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0118 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=20860390170 |
| 0119 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0120 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0121 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0122 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0123 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0124 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0125 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0126 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0127 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0128 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0129 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0130 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0131 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0132 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0133 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0134 | amount-insanity | bad | Integer.MAX_VALUE(2147483647) 放大环，crafts=5069809575 |
| 0135 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0136 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0137 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0138 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0139 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0140 | amount-insanity | questionable | 放大失控 crafts=712830380 |
| 0141 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0142 | amount-insanity | questionable | 放大失控 crafts=712830014 |
| 0143 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0144 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0145 | amount-insanity | questionable | 放大失控 crafts=677986188 |
| 0146 | amount-insanity | questionable | 放大失控 crafts=1452831908 |
| 0147 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0148 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |
| 0149 | - | ok | incomplete 预期(skyblock 原料/UNRESOLVED 正常) |

## 规则建议（给汇总者）
- targetRegex: 无（目标物品本身均合法：chunk_loader / mechanical_arm / feeding_upgrade / 铜锈蚀变体 / 背包升级等；问题在放大机制而非目标，故不按目标排除）。
- recipeRegex: 标记/收敛块·矿·粒互转配方族——`(mechanical_crafter|mechanical_press|stonecutter|blast_furnace).*(block|nugget|from_crate|_from_[a-z]+_ore|ore_from)`；证据：24 条链（crafts≥1e6）全部经由此类互转环几何放大，中间量最高 23 亿、含 Integer.MAX_VALUE 哨兵。建议对链内 `x≥1e7` 的中间量直接收敛/截断。
- builtin 候选: `pattern-amplification-overflow`（junk）——`loop=allowBoundedAmplification` 在块/矿/粒互转上溢出至 `2147483647`(6 链) 或几何放大(3^16=43046721 等) 至数千万~23 亿(18 链)，无净收益。建议给 bounded-amplification 加硬上限（≤1e6），将 `2147483647` 视为 data-pollution 哨兵。跨包通用（任何启用 allowBoundedAmplification 的 skyblock/tech 包均会复现）。
- 无新模式（fake-production / free-resource / loop-or-cycle 非放大类 / tag-expansion / machine-mismatch）在本批未检出。
