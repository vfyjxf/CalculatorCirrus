# lost-world batch-002 审查报告
- pack: lost-world
- 批次: batch-002（written=131；类别分布 incomplete 45 / long 27 / colossal 23 / giant 14 / medium 14 / short 8）
- 审查链数: 131（incomplete 45 / complete 86；其中 63 条全读，其余结合 index.tsv 与链头/状态/RAW 段审查；含 chains-incomplete 45 条全扫 header/status）
- 发现: critical 0 / major 0 / minor 3

## 概述
本批 131 条链被上一轮已固化的规则高度覆盖：incomplete/colossal 中 `pattern/exchange` 880 处、`intent/recycling` 738 处、`pattern/recolor` 34 处，压缩阶梯与 stonecutting/slab_to_block 等带标签步骤均已收敛。本批**无新暴露的假生产配方族**（全包无 `jei:category/`（真实机器）误用，无 ftbquests/村民/市场交易/奖励页泄漏；`jei:/synthetic/N` 均为合法合成 id）。主导噪声是上一轮已知的「无净产出往返环机制」：`raw_*_block ↔ raw_*`、`hay_block ↔ wheat`、`lapis_block ↔ lapis_lazuli`、`bone_block ↔ bone_meal`、`*_slab ↔ *_planks(_from_slabs)`、`*_stairs ↔ *_planks(_from_stairs)` 等 9:1↔1:9 互逆环，在 mud/copper/gold/iron/lapis/bone/quartz 等链中反复出现以做计数放大，零净产出。该机制按 round-2 口径属已知规划器级问题，仅在链级判定一句话提及，不重复展开。

下面三条是**本批新暴露的已知机制变体**（证据确凿，但站点/量级为新）。

## 发现
### F-lostworld-1 | chain=CHAIN 0044 (及 676 处) | severity=minor | category=other
- 位置: `[A8] EXECUTE minecraft:anvil.materials_repair. x2147483647 @ minecraft:anvil [rule-tag: pattern/exchange]`（CHAIN 0044 同链还有 A15/A22；全 incomplete 文件共 676 处带此步骤）
- 问题: `minecraft:anvil.materials_repair.` 是铁砧修复动作（用 leather 修 leather_helmet，再经 `jei:/synthetic/N` salvager 拆回 leather），构成 leather→helmet→anvil→leather 的 no-op 往返。round-1 口径明确「anvil/enchantment 泄漏配方已被排除（不应再出现）」，但本批它被重新归类为 `pattern/exchange` 标签而未被排除，仍作为可执行步骤进入链。配方 id 本身带尾点 `materials_repair.`（toString 串伪影），属数据污染。
- 原因: 分类器把「铁砧修复」套到了 exchange 标签，绕过了 round-1 的 anvil 排除集；标签掩盖了它本应被排除的泄漏本质。
- 建议: 在排除集里补 `minecraft:anvil(\..*)?` 严格排除（含尾点变体），不要让 anvil 修复被 exchange 标签放行。

### F-lostworld-2 | chain=CHAIN 0003 / 0006 / 0007 / 0044 / 0065 | severity=minor | category=data-pollution
- 位置:
  - CHAIN 0003 A2 `in : silentgear:netherwood_log|+3 x192`；CHAIN 0006 A32 `in : silentgear:dagger_template|+1 x18, minecraft:bamboo_planks|+82 x18`；CHAIN 0007 同理；
  - UNRESOLVED 叶子带 `+N`：`minecraft:cobbled_deepslate|+2`（CHAIN 0003/0005 等多处 `reason=CASCADE_EXHAUSTED`）、`biomesoplenty:pine_wood|+95`、`minecraft:bamboo_planks|+82`、`silentgear:crude_hammer|+2`、`silentgear:black_fluffy_block|+15`（CHAIN 0007 等）。
- 问题: `|+N` 物品 id 后缀污染（round-1 已知）本批已侵入 RAW 输入与 UNRESOLVED 叶子，且 N 取值无界（观测到 +1/+2/+3/+5/+15/+24/+82/+95）。这会让按干净 id 去重/合并的 raw_kinds 与 UNRESOLVED 解析漏掉这些带后缀的叶子（同一物被拆成 `oak_planks` 与 `acacia_planks|+24` 两个不同叶子），并污染统计。
- 原因: 规划器在 item id 上附加了计数/变体后缀 `|+N`，未在分类与去重前归一化。
- 建议: 分类/去重前对所有 item id 做 `strip \|\\+[0-9]+` 归一化；下游 raw/UNRESOLVED 解析与 any-of 展开都应基于干净 id。

### F-lostworld-3 | chain=CHAIN 0065 / 0066 / 0044 | severity=minor | category=data-pollution
- 位置:
  - CHAIN 0065 `[RAW] biomesoplenty:cypress_leaves x103783858142`（全部 19 种 *_leaves 同量级）+ `minecraft:enchanted_book x15384707055` + `minecraft:leather x82678120412`；
  - CHAIN 0066 `biomesoplenty:*_leaves x2852126720` + `minecraft:diamond x10737418235`（2^33）+ `minecraft:enchanted_book x1442840576`；
  - CHAIN 0044 UNRESOLVED `minecraft:leather x8589934588`（2^33）。
- 问题: 整数溢出不再只是 round-1 口径提到的 `x2147483647`（2^31）哨兵，本批溢出档位更多、量级更大（~8.59e9、~1.5e10、~1.04e11），且**直接落在 UNRESOLVED/RAW 叶子计数上**。其中 `minecraft:enchanted_book`（附魔台+书可产）与 `biomesoplenty:*_leaves`（可获取）属本应可解析/可获取的物，却带着 1e10~1e11 的溢出计数作为未解析叶子出现（例如 glow_item_frame 链 CHAIN 0065 根本不需要 leaves，纯属往返环污染溢出）。这会使 raw_kinds/leaf 统计与下游收敛判断失真。
- 原因: 规划器 32 位（及以上）计数溢出在往返环放大下累积，且未对 UNRESOLVED 计数做钳制；溢出同时污染了叶子与可解析物。
- 建议: 整数溢出钳制需覆盖多档（不只 2^31，至少 2^33≈8.59e9 与 ~1.04e11 档）；并把 `enchanted_book`、各类 `leaves` 等可获取/可解析物按 resolvable 处理，避免以溢出计数当作未解析叶子。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000,0001,0002,0039,0046,0053,0056 | long | questionable | wheat↔hay_block 互逆往返环（0 净产出）放大 packed_mud/mud_bricks，已知机制 |
| 0003,0005,0006,0010,0022,0029,0067 | incomplete | questionable | netherwood planks↔stairs↔slabs 回收环 + raw_*_block 往返环 + `+N` 污染，已知机制 |
| 0004,0098,0102,0106~0132 | long | questionable | netherwood 回收环 + stick 用 `acacia_planks\|+24`（+N 污染），已知机制 |
| 0037 | giant | questionable | raw_copper_block↔raw_copper 往返环放大，已知机制 |
| 0043,0049,0051,0052 | giant | questionable | quartz_slab↔chiseled_quartz_block 互逆环 + `+2` 污染，已知机制 |
| 0069,0074 | giant | questionable | raw_gold_block↔raw_gold 16 段往返环放大，已知机制 |
| 0075,0077,0079,0080,0081,0082,0127 | giant | questionable | lapis/bone 往返环 + `pattern/recolor` 染 dye，已知机制 |
| 0007~0009,0014,0015,0073,0083~0097,0101,0103,0104,0105,0122,0110,0114 | incomplete | questionable | lapis/bone 往返环产蓝/白 dye 做 candle_holder/flag/banner，已知机制 |
| 0011~0036 (trapped_chest/awning/cake/sconce 等 colossal) | colossal | questionable | raw_*_block 往返环 + netherwood 回收环（含 `\|+3` 污染），完整但含已知机制 |
| 0044,0061,0063,0064,0065,0066,0068 | incomplete(mega-wide) | bad | 32 位(及以上)溢出 crafts 3.3e11 / leaves 1.04e11；anvil 修复泄漏(CHAIN 0044 等)，已知机制+新暴露(F-1/3) |
| 0062,0089 | incomplete | questionable | leather/book/chest 回收环，已知机制；0089 crafts=36558 为溢出级 |
| 0071,0072,0087,0092,0093 | incomplete | questionable | raw17 巨量 planks 回收环 + netherwood 环，crafts=6156/6157 已知机制 |
| 0038,0040,0042,0045,0047,0054,0057,0058 | short | ok | stonecutting/smelting 直产，正常 |
| 0041,0048,0050,0055,0059,0060,0076,0078,0099,0100,0109,0113,0120,0126 | medium | ok | 直产/少量回收，正常（+N 污染随 +raw 叶子出现，见 F-2） |

其余未逐行列出的链均属上述同类（reciprocating 环 / recycling / recolor 标签覆盖），判定同组。

## 规则建议（给汇总者）
- targetRegex: 暂不需要新增目标排除。
- recipeRegex: `^minecraft:anvil(\..*)?$` —— 排除 anvil 修复泄漏（含 `minecraft:anvil.materials_repair.` 尾点变体）；证据 676 处，CHAIN 0044 A8/A15/A22 等。
- builtin 候选:
  - item-id 归一化：分类/去重/any-of 展开前 strip `\|\\+[0-9]+` 后缀（cross-pack 通用）；证据 CHAIN 0003/0006/0007/0044/0065 多处（N 取值 +1~+95）。
  - 整数溢出多档钳制：除 2^31=2147483647 外，需覆盖 2^33≈8589934592 与 ~1.04e11 档；并对 `enchanted_book`/`*_leaves` 等可获取物按 resolvable 处理；证据 CHAIN 0065/0066/0044。
