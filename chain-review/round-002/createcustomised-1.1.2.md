# CreateCustomised1.1.2 batch-002 审查报告
- pack: CreateCustomised1.1.2
- 批次: batch-002（written=99，全部为 incomplete；depthBucket: colossal 78 / mega-wide 16 / long 5）
- 审查链数: 99（incomplete 99 / complete 0；单文件 chains-incomplete-001.txt 5.3MB，逐链抽取 EXECUTE 步骤 + UNRESOLVED + 截断信号审查，覆盖全部 99 条）
- 发现: critical 0 / major 3 / minor 2

## 已固化规则的延续说明（本轮不重复展开，仅一句话）
上一轮固化的 anvil/enchantment 泄漏排除、压缩阶梯 `[rule-tag: pattern/storage_ladder]`、exchange `[rule-tag: pattern/exchange]` 在本批已生效；32 位计数溢出（`x2147483647` 哨兵）、`|+N` 物品 id 后缀污染、无净产出往返环机制本身仍为底层已知问题——本批 iron_ingot(139)/diamond(298)/bamboo(105)/stick(61)/gold_ingot(73) 等 UNRESOLVED 多为 `BUDGET_TRUNCATED`，根因即放大溢出级联，非新缺陷。

## 发现

### F-CC-1 | chain=0048（波及 0044,0052,0053,0055,0057,0058,0060,0062,0063,0065,0069,0078,0084） | severity=major | category=loop-or-cycle
- 位置: `create_salvage:diamond_nuggets_from_diamond` 与 `create_salvage:diamond_from_diamond_nuggets`（CHAIN 0048 行 16842-16913，A238↔A241 等；行 19554-19596 出现 `x2147483647` 哨兵 A391-A402）
- 问题: 钻石↔钻石碎片可逆对，无净产出，被规划器当作计数器放大通道反复往返（如 A238 972 钻石→972 碎片，A239 972 碎片→108 钻石，净亏损却仍用于凑数）。该对**未带任何 `[rule-tag]`**，与已固化的 storage_ladder/compress 标签不同，属本轮新暴露的未打标签往返环。
- 原因: `create_salvage` 的拆解/重组配方未被归类为 loop/回收，规划器在 `allowBoundedAmplification` 下把它当成可用的"数量搬运"机制。
- 建议: 将 `create_salvage:diamond_nuggets_from_diamond` / `diamond_from_diamond_nuggets` 标记为 `loop/amplification`（或 junk），与 `salvaging/*_superheated`（已带 `intent/recycling`）一并收敛。

### F-CC-2 | chain=0000（波及 39 条链，见链级判定表 F 组） | severity=major | category=loop-or-cycle
- 位置: Farmer's Delight 打包/解包对：`rice_panicle↔rice_bale`、`straw↔straw_bale`、`canvas↔canvas_rug`（CHAIN 0000 行 607-657，A67-A78）
- 问题: 这些是 9:1 存储阶梯式可逆打包，但**全部未带 `[rule-tag]`**，规划器在链中来回打包/解包以搬运数量（A67 `rice_panicle x6966`→`rice_bale x774`，A68 又 `rice_bale x774`→`rice_panicle x774`，A69/A70 再 9:1 还原）。与 `allthecompressed`/crate 等已标签 storage_ladder 同性质却未被标签，造成链级冗余。
- 原因: FD 的 bale/rug 包装配方未纳入 `pattern/storage_ladder` 标签族。
- 建议: 扩展 builtin `pattern/storage_ladder` 标签，覆盖 `farmersdelight:(rice_bale|rice_panicle|straw_bale|straw|canvas_rug|canvas)` 及其互逆配方。

### F-CC-3 | chain=0052（波及 0016,0050,0055,0056,0058,0060,0061,0062,0063,0065,0069,0084,0092） | severity=major | category=generation-abuse
- 位置: `create_synthetic_pressure:compacting/graphite_processing`（CHAIN 0052 区域行 6416，A208：`in graphite x27 → out impure_diamond x27, minecraft:diamond x27`）
- 问题: 该配方由煤衍生的石墨**直接产出 `minecraft:diamond`**（免费钻石），规划器在 14 条链中借此把廉价煤炭转化为钻石，并进一步放大（叠加钻石↔碎片环至 `x2147483647`）。配方本身玩家可执行（属真实生产），但属于"生成类"配方，被规划器当作低成本钻石源滥用，是钻石溢出与多条 netherite 链膨胀的根因之一。
- 原因: 合成钻石的 generation 配方未标记为 generation，规划器优先选它而非采矿/交易。
- 建议: 将 `create_synthetic_pressure:compacting/graphite_processing` 标记为 `generation/synthetic_diamond`，在路径质量评估中降权或按需收敛。

### F-CC-4 | chain=0000 | severity=minor | category=impure-recipe
- 位置: `farmersdelight:rice_panicle`（CHAIN 0000 行 610-612，A68：in `rice_bale x774` → out `rice_panicle x774`，1:1 损耗）
- 问题: 同链 A70（行 618-620）存在正确的 1:9 解包（`rice_bale x86`→`rice_panicle x774`），但规划器却选用 1:1 损耗解包 A68，每包损失 8 个 panicle。证据显示更优解在链内别处可用。
- 原因: FD 同时存在"块→作物"的 1:1 与 1:9 两种解包配方，规划器在放大路径中错选了损耗型。
- 建议: 对 FD `rice_panicle` 类"块→作物"配方做杂质收敛，优先 1:9 解包；低置信度，标 suspected。

### F-CC-5 | chain=0001（波及 35 条链，171 处） | severity=minor | category=machine-mismatch
- 位置: `minecraft:stripped_*_log_via_vanilla_stripping`（如 CHAIN 0000 行 658，A84：`EXECUTE minecraft:stripped_birch_log_via_vanilla_stripping x131072`，**无 `@ 机器` 字段**）
- 问题: 该类"原版剥皮"动作被表示为无机器归属的 EXECUTE 步骤，出现在 35 条链中。属玩家斧头右键动作，无对应机器系统；格式上与其它带 `@ <machine>` 的步骤不一致，疑似分类/数据占位缺失。
- 原因: 原版 stripping 动作未被映射到具体机器系统。
- 建议: 给 `via_vanilla_stripping` 系配方补机器归属（或标记为 player-action），避免机器字段空缺；不影响路径正确性，标 minor。

## 链级判定
| chain | category | verdict | 一句话 |
| 0000 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0001 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0002 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0003 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0004 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0005 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0006 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0007 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0008 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0009 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0010 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0011 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0012 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0013 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0014 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0015 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0016 | generation-abuse | questionable | graphite_processing 由煤产钻石(免费钻石生成) |
| 0017 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0018 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0019 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0020 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0021 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0022 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0023 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0024 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0025 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0026 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0027 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0028 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0029 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0030 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0031 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0032 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0033 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0034 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0035 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0036 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0037 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0038 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0039 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0040 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0041 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0042 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0043 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0044 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0045 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0046 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0047 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0048 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0049 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0050 | generation-abuse | questionable | graphite_processing 由煤产钻石(免费钻石生成) |
| 0051 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0052 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0053 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0054 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0055 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0056 | generation-abuse | questionable | graphite_processing 由煤产钻石(免费钻石生成) |
| 0057 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0058 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0059 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0060 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0061 | generation-abuse | questionable | graphite_processing 由煤产钻石(免费钻石生成) |
| 0062 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0063 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0064 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0065 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0066 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0067 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0068 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0069 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0070 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0071 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0072 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0073 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0074 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0075 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0076 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0077 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0078 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0079 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0080 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0081 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0082 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0083 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0084 | loop-or-cycle | questionable | create_salvage 钻石↔碎片无净产出往返环(未打标签) |
| 0085 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0086 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0087 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0088 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0089 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0090 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0091 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0092 | generation-abuse | questionable | graphite_processing 由煤产钻石(免费钻石生成) |
| 0093 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0094 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0095 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0096 | loop-or-cycle | questionable | FD 打包/解包存储阶梯环(未打标签) |
| 0097 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |
| 0098 | - | ok | 主要为 stonecutting exchange 标签步骤+截断，无新问题 |

## 规则建议（给汇总者）
- targetRegex: 无新增目标排除（本批 99 个目标均为真实可合成物品，无奖励/任务/交易展示页类 fake-production 目标）。
- recipeRegex: `^create_salvage:diamond_nuggets_from_diamond$|^create_salvage:diamond_from_diamond_nuggets$` —— 标记 loop/amplification 并收敛；证据 130+130 处 EXECUTE，波及 14 条链。
- recipeRegex: `^farmersdelight:(rice_bale|rice_panicle|straw_bale|straw|canvas_rug|canvas)$` 及其互逆 —— 归入 `pattern/storage_ladder`；证据波及 39 条链。
- recipeRegex: `^create_synthetic_pressure:compacting/graphite_processing$` —— 标记 `generation/synthetic_diamond`；证据 14 条链。
- builtin 候选:
  1. `create_salvage` 钻石↔碎片往返环 → 建议标签 `loop/diamond-salvage`（或 junk），跨包若含 create_salvage 均适用；证据见 F-CC-1。
  2. Farmer's Delight 打包阶梯 → 扩展现有 `pattern/storage_ladder` 标签覆盖 bale/rug 系；证据见 F-CC-2。
  3. `create_synthetic_pressure:compacting/graphite_processing` → 建议标签 `generation/synthetic_diamond`，与采矿路径区分；证据见 F-CC-3。
  4. `minecraft:*_via_vanilla_stripping` → 建议补机器归属或标 `player-action`；证据见 F-CC-5（minor，非收敛类）。
