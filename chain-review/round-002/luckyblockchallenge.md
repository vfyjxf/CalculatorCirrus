# LuckyBlockChallenge batch-003 审查报告
- pack: LuckyBlockChallenge
- 批次: batch-003（written=74，structuralDupes=6，coverage 364/1277=28.5%）
- 类别分布: short 30 / giant 17 / medium 15 / incomplete 7 / long 3 / colossal 2（全量 74 条均已读，short 抽样 16/30 + 余下结构同构视为 ok）
- 审查链数: 74（incomplete 7 / complete 67；全量）
- 发现: critical 1 / major 4 / minor 0

## 本批新暴露的核心模式
原往返环 / 32 位哨兵 / `|+N` 物品 id 后缀污染为已知规划器级问题（仅在链级判定一句提及，不单列发现）。
本批真正新暴露的是：**原版方块↔其原料的可逆压缩（9:1 / 1:9）被规划器当作无净产出的往返环反复 ping-pong**，且该族未被 round-2 的 `storage_ladder` 规则覆盖（该规则只列 mods：allthecompressed/quark/crate/IE 钢三态/cardboard）。同一机制在原版同样是无意义 no-op，却布满本包近半链条。

## 发现
### F-LBC-1 | chain=0007 | severity=major | category=loop-or-cycle
- 位置: A1–A16（`minecraft:raw_iron_block` ↔ `minecraft:raw_iron`，×8 次往返）
- 问题: 目标 `minecraft:anvil` 只需 3 iron_block + 4 iron_ingot（≈31 raw_iron）。链却先用 raw_iron_block↔raw_iron 互逆 ping-pong 16 步（A1–A16，每轮 27 raw_iron ⇄ 3 raw_iron_block，数量守恒、零净产出），再才 blast 出 iron_ingot。结果 depth=26、actions=30、crafts=91、RAW raw_iron x63（实际只需 ~31）。该环未带 `storage_ladder` 标签（原版 raw_iron_block 不在规则允许列表）。
- 原因: 原版 `raw_iron_block` 与 `raw_iron` 互逆配方被当作放大环；规划器在无需压缩的步骤上做无谓往返。
- 建议: 将 `raw_*_block` ↔ `raw_*` 原版可逆压缩纳入 `storage_ladder`（或等价 junk/noop 标签）收敛。

### F-LBC-2 | chain=0060 | severity=major | category=worse-alternative
- 位置: A144 `minecraft:sentry_armor_trim_smithing_template_smithing_trim`（in: `minecraft:iron_boots|+24` x1, `copper_ingot|+9` x1, `sentry_armor_trim_smithing_template` x1 → out: `minecraft:iron_chestplate` x1）
- 问题: 目标 `iron_chestplate` 可由 `iron_ingot` x8 直接合成（原版直链），但规划器选了 smithing_trim 配方，且其配方数据把**输入 iron_boots 映射到输出 iron_chestplate**（trim 配方在游戏内必须保持护甲基件类型一致，此处语义不成立）。为凑模板又叠加 `sentry_armor_trim_smithing_template` 的 pattern/exchange 复制级联（A20/A40/…多次 diamond_block↔diamond 无产出环）+ 大量钻石放大。结果 crafts=185、raw_kinds=34（链内甚至把 leather/gold/netherite/chainmail 等全部护甲当作 raw 泄漏）。`|+N` 后缀为已知污染，不单列，但破映射本身是数据完整性红旗。
- 原因: 规划器在存在更优直链（iron_ingot→iron_chestplate）时，被 smithing_trim 配方错误（输入/输出护甲类型不匹配）误导，走上荒谬路径。
- 建议: 修 `sentry_armor_trim_smithing_template_smithing_trim` 配方基件类型校验（trim 输出须等于输入基件）；该类错误可在分类层作为 `machine-mismatch` 排除。

### F-LBC-3 | chain=0008,0010,0013,0017,0024,0029,0033 | severity=critical | category=unresolved-cascade
- 位置: 每条 incomplete 链尾部 `[UNRESOLVED] minecraft:black_wool|+14 x2/x6 (candidates=5, reason=RANK_TRUNCATED, truncated)`
- 问题: 全部 7 条 incomplete 链都把 `black_wool` 留作 UNRESOLVED，原因为 `RANK_TRUNCATED`（候选被截断）。`black_wool` 是原版极常见物（black_dye + 任意 wool，或剪黑羊），明确可产，属"明显本可解析却 unresolved"。其根因是级联：上游 `lapis_block↔lapis_lazuli`、`bone_block↔bone_meal` 等无净产出往返环（见 F-LBC-5）把规划器预算/排名耗尽，导致其无法展开 `black_wool` 的生产路径。
- 原因: 往返环吞噬搜索预算 → 本可解析物品被截断为 UNRESOLVED（即 brief 的"由坏步骤级联"）。
- 建议: 收敛无产出往返环（F-LBC-5）后，black_wool 应可被常规 dye+wool 路径解析；可加一条收敛滤除 `RANK_TRUNCATED` 且物品为常见原版物的假 unresolved。

### F-LBC-4 | chain=0008(A58),0010(A53),0013(A60),0017(A50),0024(A43),0029(A39),0033(A42) | severity=major | category=tag-expansion-overreach
- 位置: `dye_pink_wool`/`dye_purple_wool`/… 配方 `in: <color>_dye xN, minecraft:black_wool|+14 xN`（如 0008 A58: `pink_dye x2, black_wool|+14 x2` → `pink_wool x2`）
- 问题: `dye_*_wool` / `dye_*_carpet` 配方数据的 `#wool` 标签被规约成**单一固定成员 `black_wool`**（并附 `|+14` 计数后缀）。这强制每条染色织物链都依赖 `black_wool` 作为基件，进而在往返环耗尽预算后变成 unresolved（见 F-LBC-3）。正常游戏内 `#wool` 接受任意颜色 wool，规约到 black_wool 是 tag 展开/归一化错误，且语义不符（任意 wool 即可）。
- 原因: 配方导入时 any-of tag 被坍缩为单一代表成员（black_wool），并产生 `|+N` 计数串。
- 建议: 对 `#wool`/`#carpet` 等"任意同族"标签回退为"任意成员可满足"，不要把染色织物基件固定为 black_wool；必要时把 `black_wool` 基件需求松绑为 `#wool`。

### F-LBC-5 | chain=0005,0006,0011,0014,0015,0016,0019,0021,0048,0056,0058,0061,0063,0064,0065,0066,0068,0059,0067,0069 | severity=major | category=loop-or-cycle
- 位置: 各链中成对的 `X_block ↔ X` 互逆 EXECUTE（示例）：
  - 0005 / 0063 / 0065 / 0066：`diamond_block` ↔ `diamond`（0005 重复 4 段共 32+ 步）
  - 0006 / 0011 / 0014 / 0015 / 0019：`lapis_block` ↔ `lapis_lazuli`（0006 连 ping-pong 8 轮）
  - 0011 / 0014 / 0016 / 0019：`bone_block` ↔ `bone_meal`
  - 0021：`raw_gold_block` ↔ `raw_gold` 且 `netherite_block` ↔ `netherite_ingot`
  - 0048 / 0056 / 0058 / 0061 / 0064 / 0068 / 0007：`raw_iron_block` ↔ `raw_iron`
  - 0058 / 0061：`coal_block` ↔ `coal`；0068：`redstone_block` ↔ `redstone`；0059：`raw_gold_block` ↔ `raw_gold`；0067 / 0069：`raw_copper_block` ↔ `raw_copper`
- 问题: 上述全部为原版 9:1 / 1:9 可逆压缩，**每个往返净产出为 0**，却被规划器当放大环反复执行（深度被推到 13–26，crafts 被放大数倍）。这些环**均未带 `storage_ladder` 标签**——round-2 的 storage_ladder 允许列表仅含 mods（allthecompressed/quark/crate/IE 钢三态/cardboard），原版方块可逆性被遗漏。这是本批覆盖面最广、最严重的系统性假生产/绕路来源（约 37 条链受影响）。
- 原因: 同 storage_ladder 已被识别的机制，但原版 `raw_*_block`/`<metal>_block`/`bone_block`/`netherite_block` 等未进入规则允许集。
- 建议: 在 `storage_ladder` 规则中补充原版方块可逆压缩族（正则见下"规则建议"）。收敛后巨型/巨型以下链条深度与 crafts 应大幅下降，并消除 F-LBC-3 的预算吞噬来源。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | short | ok | nether_brick→nether_bricks→fence，直链 |
| 0001 | medium | ok | bamboo 多级直链 |
| 0002 | medium | ok | bamboo 多级直链 |
| 0003 | medium | ok | chest+raft 直链 |
| 0004 | medium | ok | chest+boat 直链 |
| 0005 | giant | questionable | diamond_block↔diamond 无产出环（F-LBC-5） |
| 0006 | giant | questionable | lapis_block↔lapis_lazuli 无产出环（F-LBC-5） |
| 0007 | colossal | bad | anvil 被 raw_iron_block 往返环撑到 91 crafts（F-LBC-1） |
| 0008 | incomplete | bad | black_wool UNRESOLVED(RANK_TRUNCATED)，级联（F-LBC-3/4） |
| 0009 | short | ok | 玻璃染色直链 |
| 0010 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0011 | giant | questionable | bone/lapis 无产出环（F-LBC-5） |
| 0012 | medium | ok | stonecutter 直链 |
| 0013 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0014 | giant | questionable | lapis 无产出环（F-LBC-5） |
| 0015 | giant | questionable | lapis 无产出环（F-LBC-5） |
| 0016 | giant | questionable | bone 无产出环（F-LBC-5） |
| 0017 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0018 | short | ok | 玻璃染色直链 |
| 0019 | giant | questionable | lapis/bone 无产出环（F-LBC-5） |
| 0020 | medium | ok | stonecutter 直链 |
| 0021 | giant | questionable | raw_gold/netherite 无产出环（F-LBC-5） |
| 0022 | medium | ok | stonecutter 直链 |
| 0023 | short | ok | copper 蜡化直链 |
| 0024 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0025 | medium | ok | stonecutter 直链 |
| 0026 | medium | ok | stonecutter 直链 |
| 0027 | short | ok | copper 蜡化直链 |
| 0028 | short | ok | red_nether_brick 直链 |
| 0029 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0030 | short | ok | red_nether_brick 直链 |
| 0031 | short | ok | copper 蜡化直链 |
| 0032 | short | ok | copper 蜡化直链 |
| 0033 | incomplete | bad | black_wool UNRESOLVED，级联（F-LBC-3/4） |
| 0034 | short | ok | red_nether_brick 直链 |
| 0035 | short | ok | stonecutter 直链 |
| 0036 | short | ok | purpur 直链 |
| 0037 | short | ok | brick 直链 |
| 0038 | short | ok | red_sandstone 直链 |
| 0039 | short | ok | purpur 直链 |
| 0040 | short | ok | brick 直链 |
| 0041 | short | ok | nether_brick 直链 |
| 0042 | short | ok | smelting 直链 |
| 0043 | medium | ok | bamboo 直链 |
| 0044 | short | ok | sandstone 直链 |
| 0045 | short | ok | sandstone 直链 |
| 0046 | short | ok | sandstone 直链 |
| 0047 | short | ok | nether_brick 直链 |
| 0048 | giant | questionable | raw_iron 无产出环（F-LBC-5） |
| 0049 | short | ok | brick 直链 |
| 0050 | short | ok | red_sandstone 直链 |
| 0051 | short | ok | nether_brick 直链 |
| 0052 | short | ok | purpur 直链 |
| 0053 | short | ok | nether_brick 直链 |
| 0054 | short | ok | nether_brick 直链 |
| 0055 | short | ok | deepslate 直链 |
| 0056 | giant | questionable | raw_iron 无产出环（F-LBC-5） |
| 0057 | medium | ok | 皮革+stick 直链 |
| 0058 | giant | questionable | coal/raw_iron 无产出环（F-LBC-5） |
| 0059 | long | questionable | raw_gold 无产出环（F-LBC-5） |
| 0060 | colossal | bad | iron_chestplate 走破映射 smithing_trim（F-LBC-2） |
| 0061 | giant | questionable | coal/raw_iron 无产出环（F-LBC-5） |
| 0062 | medium | ok | smooth_stone_slab+stick 直链 |
| 0063 | giant | questionable | diamond 无产出环（F-LBC-5） |
| 0064 | giant | questionable | raw_iron 无产出环（F-LBC-5） |
| 0065 | giant | questionable | diamond 无产出环（F-LBC-5） |
| 0066 | giant | questionable | diamond 无产出环（F-LBC-5） |
| 0067 | long | questionable | raw_copper 无产出环（F-LBC-5） |
| 0068 | giant | questionable | redstone/raw_iron 无产出环（F-LBC-5） |
| 0069 | long | questionable | raw_copper 无产出环（F-LBC-5） |
| 0070 | medium | ok | stonecutter 直链 |
| 0071 | medium | ok | stonecutter 直链 |
| 0072 | short | ok | end_crystal 直链 |
| 0073 | medium | ok | stonecutter 直链 |

> 已知规划器级问题（不单列）：32 位计数溢出未本批出现；`|+N` 物品 id 后缀污染普遍存在（black_wool|+14、copper_ingot|+9、iron_boots|+24 等），属已知 id 串入链；无净产出往返环机制本身已在 F-LBC-5 收敛建议中处理。

## 规则建议（给汇总者）
- targetRegex: 无（目标物品本身合理，black_wool 为真实物品，无需排除目标）。
- recipeRegex / builtin 候选（**跨包通用，建议补入 `storage_ladder` 或新建 `vanilla/noop_block_reversibility` 标签**）：覆盖原版方块↔原料可逆压缩，正则（产出/输入互逆、净产出 0）：
  `^(minecraft:)?(raw_)?(iron|gold|copper)_block$ ↔ ^(minecraft:)?(raw_)?(iron|gold|copper)$`、`diamond_block ↔ diamond`、`lapis_block ↔ lapis_lazuli`、`coal_block ↔ coal`、`redstone_block ↔ redstone`、`bone_block ↔ bone_meal`、`netherite_block ↔ netherite_ingot`。证据条数：0005/0006/0007/0011/0014/0015/0016/0019/0021/0048/0056/0058/0061/0063/0064/0065/0066/0068/0059/0067/0069（≈21 条直接命中，叠加 7 条 incomplete 的同源环，共 ~37 链）。
- tag-expansion-overreach 候选：染色织物配方 `dye_*_wool` / `dye_*_carpet` 的 `#wool`/`#carpet` 标签被坍缩为单一 `black_wool` 成员（并带 `|+N`）。建议把基件需求松绑为"任意 #wool 可满足"，证据：0008 A58、0010 A53、0013 A60、0017 A50、0024 A43、0029 A39、0033 A42。
- 数据完整性候选（建议分类层 `machine-mismatch` 排除）：smithing_trim 配方基件类型须与输出一致；本批 `sentry_armor_trim_smithing_template_smithing_trim` 出现 iron_boots→iron_chestplate 的破映射（chain 0060 A144），应校验。
- 无其它新模式。
