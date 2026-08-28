# CreateCustomised1.1.2 batch-001 审查报告

- pack: CreateCustomised1.1.2
- 批次: batch-001（written=150；类别分布：incomplete 150 / complete 0；depthBucket 全部为 colossal）
- 审查链数: 150（incomplete 150 / complete 0；全量审查：脚本化提取每链 digests + 对 48 条大 crafts 链与 8 条 MAX_VALUE 链逐行核验 EXECUTE 的 in/out 与 rule-tag，未整读 9.5MB 原文）
- 发现: critical 8 / major 40 / minor 2

## 审查口径说明

- 本批 150 条链 **全部 incomplete**，且每条都含若干 `[UNRESOLVED]`，其中大量为 `CASCADE_EXHAUSTED` 的原版/模组原矿与农产（raw_zinc/raw_copper/raw_iron/raw_gold/diamond、wheat/egg/honey_bottle/melon_slice/rice_panicle 等）。这些属于"采矿/种植无配方根"的正常原材料叶子，按口径"已内置规则不要重复报告 / +raw 叶子不算问题"**不计入问题**，仅作为覆盖缺口在末节说明。
- 所有被点名的放大步骤均经 `grep` 核验为 **无 `[rule-tag: ...]`**（即未被 builtin exchange/recolor/recycling 覆盖），故不属于"已内置、勿重复报告"范畴。

## 核心问题（系统性）

规划器在 `loop=allowBoundedAmplification` 下，对"方块↔锭 / 钻石↔碎钻 / 米穗↔米捆"等**可逆 1:1（或 9:1 往返）配方**反复往返放大，单次乘数达到 **9⁷(4,782,969)、9⁸(43,046,721) 乃至 Integer.MAX_VALUE(2,147,483,647)**，而对应 in/out 数量相等（**零净产出**）。这些物品对单件目标（仪表、背罐、胸甲）只需个位数，却被放大到数百万～数百亿，使 `crafts` 字段失真、链整体不可用。

## 发现

### F-CC-1 | chain=0032,0085,0086,0119,0122,0123,0126,0136 | severity=critical | category=amount-insanity

- 位置: 各链 EXECUTE 乘数 ` x2147483647`（= 2³¹−1，32 位有符号整数上限）
- 问题: 8 条链出现把某物料单次放大到 Integer.MAX_VALUE 的步骤，整链 crafts 被推到 10¹⁰–10¹¹ 量级：
  - 0032 `create:netherite_backtank` crafts=166,478,718,983；`[A20] EXECUTE create_salvage:diamond_nuggets_from_diamond x2147483647 @ create:mechanical_crafter` in `minecraft:diamond x2147483647` out `create_salvage:diamond_nugget x2147483647`（第 23912–23915 行），并有对称反向步。
  - 0122/0123 `tungsten_chestplate/leggings` crafts=406,255,752,579；`farmersdelight:rice_bale x2147483647`。
  - 0085 `farmersdelight:melon_juice` crafts=410,066,130,196；0086 `minecraft:purple_concrete_powder` crafts=16,572,155,234；0119 `minecraft:white_concrete_powder` crafts=16,486,053,470；0126 `tungsten_pickaxe` crafts=22,809,327,807；0136 `tungsten_sword` crafts=19,807,864,311。
- 原因: 规划器有界放大在钻石↔碎钻、米穗↔米捆、混凝土粉末等往返配方上失控，乘数撞到 int 上限仍继续；属于 amount-insanity 的最严重形态（链不可读/不可用）。
- 建议: 对单步乘数 ≥ 2³¹−1 或整链 crafts ≥ 10⁹ 的链直接判 bad 并回退放大；在 planner 侧对 `allowBoundedAmplification` 加绝对乘数上限与"零净产出往返"熔断。

### F-CC-2 | chain=0006,0016,0024,0029,0049,0057,0061,0063,0078,0105,0143,0148,0149（及 0008/0017/0048/0081/0092/0093/0101/0104/0109/0118/0121/0124/0125/0127/0131/0132/0133/0134/0137/0138/0141/0145/0146） | severity=major | category=amount-insanity+loop-or-cycle

- 位置:
  - 0006 `[A57] EXECUTE create:crafting/materials/raw_zinc x4782969 @ create:mechanical_crafter` in `create:raw_zinc_block x4782969` out `create:raw_zinc x4782969`（第 4197–4200 行），其前一步 `[A56]` 为对称反向；乘数序列 9⁷,9⁶,…,9 呈等比阶梯，crafts=10,761,934。
  - 0024 `minecraft:raw_iron_block x43046721`(=9⁸) 与 `x4782969` 成对往返，crafts=107,617,453。
  - 0016 `farmersdelight:rice_bale x1572867`、`minecraft:golden_axe x32768`(=2¹⁵)、`minecraft:stick x16384`(=2¹⁴) 等比放大，crafts=14,715,705。
  - 同族 10.76M 常数集群：0006/0029/0049/0057/0061/0063/0078/0105/0143/0148/0149（crafts 均 ≈ 10,761,xxx）。
- 问题: 上述为"材料 ↔ 方块 / 钻石 ↔ 碎钻 / 米穗 ↔ 米捆"的**无净产出往返放大**（in 与 out 数量相等），把目标只需个位数的原料放大到 4.78M–43M 乃至 10⁸ 级，整链 crafts 失真。约 40 条链属此形态（crafts 10⁷–10¹⁰）。
- 原因: `allowBoundedAmplification` 在可逆配方上反复叠乘却不收敛到真实需求；分类器未给这些步骤打 exchange/recycling 标签，故未被内置规则拦截。
- 建议: 标记"等比往返放大（乘数=9ᵏ/2ᵏ/3ᵏ 且 in_qty==out_qty）"为 loop-or-cycle；在收敛过滤中排除这些配方族（见规则建议）。

### F-CC-3 | chain=全部 150（结构现象） | severity=minor | category=other

- 位置: `[UNRESOLVED] ... (CASCADE_EXHAUSTED)` 普遍出现在每条链（raw_zinc/raw_copper/raw_iron/raw_gold/diamond、wheat/egg/honey_bottle/melon_slice/beetroot/rice_panicle 等）。
- 问题: 150 条链 100% incomplete 的根因高度一致——核心原矿与农产在本包配方图中没有"采矿/种植"根节点，全部 `CASCADE_EXHAUSTED`。按口径这些是正常原材料叶子，不单链计问题；但导致覆盖率仅 7.7%（目标 50%）。
- 原因: 数据/建模层缺采矿与种植生成根；非链路径 bug。
- 建议: 若需达到覆盖率目标，应在图构建期注入采矿/种植 generation 根；审查层面不计入单链问题。

### F-CC-4 | chain=各巨大 crafts 链 | severity=minor | category=amount-insanity

- 位置: 多个独立链 crafts 落在 ~10.76M 这一**固定常数**（0006/0029/0049/0057/0061/0063/0078/0105/0143/0148/0149）。
- 问题: 不同目标（仪表/机械臂/部署器/滚压器/显示板等）却得到几乎相同的 10,761,xxx crafts，提示存在某个固定的放大种子/上限而非真实需求驱动，值得在 planner 侧核查放大起点。
- 原因: 疑似有界放大以固定基数起步后未对具体目标收敛。
- 建议: 排查 `allowBoundedAmplification` 的初始放大基数来源；此类链作为 amount-insanity 疑似（minor suspected）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0006 | amount-insanity | bad | raw_zinc↔block 9⁷=4.78M 往返，零净产出 |
| 0008 | amount-insanity | bad | crafts=3.2e8，放大失控 |
| 0016 | amount-insanity+loop | bad | rice/golden_axe/stick 等比放大至 2¹⁵ |
| 0017 | amount-insanity | bad | crafts=2.26e8 |
| 0024 | amount-insanity+loop | bad | raw_iron↔block 9⁸=43M 往返 |
| 0029 | amount-insanity | bad | 10.76M 常数放大 |
| 0032 | amount-insanity | bad(critical) | diamond↔nugget 撞 Integer.MAX_VALUE，crafts=1.66e11 |
| 0048 | amount-insanity | bad | crafts=9.68e7 |
| 0049 | amount-insanity | bad | 10.76M 常数放大 |
| 0056 | amount-insanity | bad | crafts=1.46e7 |
| 0057 | amount-insanity | bad | 10.76M 常数放大 |
| 0061 | amount-insanity | bad | 10.76M 常数放大（mechanical_roller） |
| 0063 | amount-insanity | bad | 10.76M 常数放大（deployer） |
| 0072 | amount-insanity | bad | crafts=2.15e7 |
| 0078 | amount-insanity | bad | 10.76M 常数放大 |
| 0081 | amount-insanity | bad | crafts=9.68e7 |
| 0085 | amount-insanity | bad(critical) | melon_juice crafts=4.1e11，MAX_VALUE |
| 0086 | amount-insanity | bad(critical) | purple_concrete_powder crafts=1.65e10，MAX_VALUE |
| 0092 | amount-insanity | bad | crafts=2.4e8 |
| 0093 | amount-insanity | bad | crafts=1.93e8 |
| 0101 | amount-insanity | bad | crafts=8.53e7 |
| 0104 | amount-insanity | bad | crafts=1.46e7 |
| 0105 | amount-insanity | bad | 10.76M 常数放大（contraption_controls） |
| 0109 | amount-insanity | bad | crafts=1.26e7 |
| 0118 | amount-insanity | bad | crafts=8.52e7 |
| 0119 | amount-insanity | bad(critical) | white_concrete_powder crafts=1.64e10，MAX_VALUE |
| 0121 | amount-insanity | bad | crafts=1.14e7 |
| 0122 | amount-insanity | bad(critical) | tungsten_chestplate crafts=4.06e11，MAX_VALUE |
| 0123 | amount-insanity | bad(critical) | tungsten_leggings crafts=4.06e11，MAX_VALUE |
| 0124 | amount-insanity | bad | crafts=4.11e7 |
| 0125 | amount-insanity | bad | crafts=8.70e8 |
| 0126 | amount-insanity | bad(critical) | tungsten_pickaxe crafts=2.28e10，MAX_VALUE |
| 0127 | amount-insanity | bad | crafts=3.21e8 |
| 0131 | amount-insanity | bad | crafts=3.21e8 |
| 0132 | amount-insanity | bad | crafts=8.70e8 |
| 0133 | amount-insanity | bad | crafts=6.77e8 |
| 0134 | amount-insanity | bad | crafts=7.74e8 |
| 0136 | amount-insanity | bad(critical) | tungsten_sword crafts=1.98e10，MAX_VALUE |
| 0137 | amount-insanity | bad | crafts=1.30e8 |
| 0138 | amount-insanity | bad | crafts=1.16e9 |
| 0141 | amount-insanity | bad | crafts=2.47e8 |
| 0143 | amount-insanity | bad | 10.76M 常数放大（blaze_cake_base） |
| 0145 | amount-insanity | bad | crafts=6.45e7 |
| 0146 | amount-insanity | bad | crafts=2.40e8 |
| 0148 | amount-insanity | bad | 10.76M 常数放大（altitude_sensor） |
| 0149 | amount-insanity | bad | 10.76M 常数放大（display_board） |
| 其余 102 条（0000–0005,0007,0009–0023,0025–0028,0030–0031,0033–0047,0050–0055,0058–0060,0062,0064–0071,0073–0077,0079–0080,0082–0084,0087–0091,0094–0100,0102–0103,0106–0117,0112–0117,0120,0128–0130,0135,0139–0140,0142,0144,0147） | — | ok | incomplete 但 crafts 正常（≤数万），仅 CASCADE/BUDGET 未解析 raw 叶子，路径合理 |

> 注：上表中 0006/0016/0024 同时为 loop-or-cycle（in==out 零净产出）；其余 bad 链为同形态 amount-insanity。

## 规则建议（给汇总者）

- targetRegex: （暂不建议排除目标）这些目标均为合法制品，问题在放大不在目标。
- recipeRegex（标记收敛这些配方族，供下一轮过滤）:
  - 方块↔锭/原矿往返：`create:crafting/materials/(raw_)?(zinc|iron|copper|gold)_block` 与对应 `(raw_)?(zinc|iron|copper|gold)` 同时出现且 in_qty==out_qty。
  - 钻石↔碎钻：`create_salvage:diamond_nuggets_from_diamond|create_salvage:diamond_from_diamond_nuggets`。
  - 米往返：`farmersdelight:rice_bale|farmersdelight:rice_panicle|farmersdelight:integration/create/milling/rice_panicle`。
  - 通用熔断：任何单步乘数 `>= 9^6 (531441)` 或 `== 2147483647` 即标记 amount-insanity。
  - 证据条数：MAX_VALUE 步 8 链；9⁷ 级 40+ 链。
- builtin 候选（跨包通用模式）:
  - 建议新增内置规则 `cirrus:pattern/amplification-noop`（标签：junk/loop）：识别"可逆配方且 in_qty==out_qty 的等比往返放大"，无论是否已打 exchange 标签都应在 planner 侧熔断。
  - 建议新增 `cirrus:intent/overflow-guard`：单步乘数达到 2³¹−1 即视为数值溢出，链判 bad。
  - 这两类在 Create 系多包（Create/CreateCraft&Additions/CreateEnchantmentIndustry/ExtraGauges 等）中高度通用。
- 无新模式时写"无"：不适用——本批存在明确的跨包可复用放大失控模式。
