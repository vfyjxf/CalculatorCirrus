# fom batch-001 审查报告
- pack: fom
- 批次: batch-001（written=150；incomplete 95 / colossal 38 / giant 13 / long 4；trivial/short/medium 无）
- 审查链数: 113（incomplete 95 全部经 awk 系统扫描取得逐链信号 + 精读 0053/0038；complete 端 long 4 + giant 13 + colossal 1 全读；抽样说明见下）
- 发现: critical 0 / major 3 / minor 1

## 审查方法说明
- 小文件全读：chains-long-001.txt（4 条）、chains-giant-001.txt（13 条）、chains-colossal-001.txt 中 0038（crafts=15780）、chains-incomplete-001.txt 中 0053（pattern/exchange x120）。
- 大文件 incomplete（3.6MB / 95 链 ≈ 38KB/链，未达“很大>100KB/链”降采样阈值，但绝对体量大）用 awk 逐链抽取关键信号：rb/rib/rcb（redstone_block / raw_iron_block / raw_copper_block 执行次数，即无净产出往返环）、exchange（pattern/exchange 执行次数）、UNRESOLVED 列表、crafts、pending。覆盖全部 95 条 incomplete，得到逐链证据。
- 定向 grep 确认无 fake-production（无 jei:、无 quest/reward/villager/market/loot/jer 命名空间）、无 free-resource（无空 in: 的 EXECUTE）、无 data-pollution（无 minecraft:air/null/sentinel/toString/unknown）、无 catalyst-misuse、无 machine-mismatch、无 impure/worse-alternative/silly-detour 实证。下列 major 为系统性路径质量问题。

## 发现
### F-fom-1 | chain=多链（0009/0077/0081/0103/0084/0035/0047/0058/0131/0135…） | severity=major | category=loop-or-cycle
- 位置: 各链内 reversible conversion 区段，例如
  - CHAIN 0009：A7 `redstone_from_blasting_deepslate_redstone_ore`(9 redstone) → A8 `redstone_block`(9→1) → A9 `redstone`(1→9) → … → A15 `redstone`(9)；9 红石经 A8–A15 共 8 步往返后仍回到 9 红石，净产出为零。
  - CHAIN 0077：A3 `gunpowder_pinch`(1→9) → A4 `gunpowder_from_pinches`(9→1) → … → A10，纯 gunpowder↔pinch 往返。
  - CHAIN 0081：A2–A10 `raw_copper`↔`raw_copper_block` 往返；A16–A21 `redstone`↔`redstone_block` 往返。
  - CHAIN 0103：A6–A21（16 步）`redstone`↔`redstone_block`；A26–A40（15 步）`raw_iron`↔`raw_iron_block`。
  - CHAIN 0084：A11–A27（17 步）`raw_iron`↔`raw_iron_block`；A38–A54（17 步）`redstone`↔`redstone_block`。
- 问题: 规划器在 `allowBoundedAmplification` 下大量插入可逆变压缩/解压（block↔ingot、redstone↔block、gunpowder↔pinch、cardboard↔block）往返环；每环入口已产出的物品数量在环结束后完全守恒，没有任何净产出，却占用大量 action/深度。属于口径文档明确列出的“无净产出的互逆往返 / A↔B no-op 重复多次”。
- 原因: `loop=allowBoundedAmplification` 缺少“no-op round-trip 排除”护栏；分类器/规划器把可逆压缩当放大手段，把本可一次到位的产出反复 bounce。该模式在 long/giant/colossal 全类别可见（awk 统计 incomplete 中 rb/rib/rcb 非零的链数十余条，complete 端几乎每链都有）。
- 建议: 在 builtin 增加 no-op round-trip 排除（A↔B 互逆且净产出=0 的放大不展开），或将 raw_iron_block/raw_copper_block/redstone_block/iron_block/gunpowder_pinch/cardboard_block 等可逆压缩标记为 `pattern-cyclic-amplification` 收敛。

### F-fom-2 | chain=0053 及 90+ 条 incomplete（0002/0003/0004/0005/0006/0012/0025/0026/0028/0046/0053/0062-0068/0070/0075/0076/0078/0086/0090-0097/0100/0106/0107/0110/0112/0138/0141…） | severity=major | category=unresolved-cascade
- 位置: 每条 incomplete 链均含 `[UNRESOLVED] create:polished_cut_andesite_stairs|+14`（reason=RANK_TRUNCATED, candidates=5）；CHAIN 0053 中 120 个 `pattern/exchange` 石切步骤的输入全部是 `create:polished_cut_andesite_stairs|+14 x2`（如 A1、A135–A143），且 `[RAW]` 叶子出现的是未限定的 `create:polished_cut_andesite_stairs x80`（同链已产出该未限定形式）。
- 问题: `create:polished_cut_andesite_stairs|+14` 这一个 tag 变体（|+14 鉴别符）无法解析，却作为根被 50–120 个 `pattern/exchange` 步骤反复派生整套 andesite 族方块；整条巨型交换树架在一个 UNRESOLVED 源上，导致近全部 incomplete 链 pending。这是典型的级联：源未解析 → 派生树全部悬空。且同链已能产出未限定 `polished_cut_andesite_stairs`，说明 `|+14` 这个 tag 鉴别符破坏了回匹配。
- 原因: （1）石切 `pattern/exchange` 无深度/规模上限，在单一源上爆炸式展开（x50–x120）；（2）`|+N` tag 变体 id（|+14/|+13/|+4）作为独立物品参与规划，其派生/回匹配在分类器里失效。本批 incomplete 几乎 100% 命中同一失败点，是系统性而非偶发。
- 建议: 优先修复 `polished_cut_andesite_stairs`（`|+14` 变体）解析，或在 builtin 将 `pattern/exchange` 递归上限压低（如 ≤10）并排除“根本身是 UNRESOLVED tag 变体”的派生；同时把 `|+N` 鉴别符的统一回匹配纳入数据层修复。
- 备注: 口径文档规定 `pattern/exchange` 已内置、不重复逐步骤报告，本发现仅就“该模式应升级为排除/限深”与级联根因提建议，符合要求。

### F-fom-3 | chain=0038 | severity=major | category=amount-insanity
- 位置: CHAIN 0038 `sophisticatedbackpacks:stack_upgrade_omega_tier`，status 行 `crafts=15780 raw_kinds=2`；A4–A19（16 步）`raw_iron`↔`raw_iron_block`（36↔4），A20–A36（17 步）`iron_block`↔`iron_ingot`（36↔4），A39–A53+（十余步）`copper_ingot`↔`copper_block`（72↔648）。
- 问题: crafts=15780 数量级失控，且绝大部分由上述零净产出的 block↔ingot 往返环构成（36 raw_iron 经 A4→A19 往返后仍为 36；iron 环、copper 环同理）。仅有的真实产出是 A20 单次熔炼与 A37 `upgrade_base`。指数级放大无真实收益。
- 原因: 同 F-fom-1 的放大护栏缺失，在 omega 级高 crafts 目标上被极端放大；是 loop-or-cycle 在数量级上的极端实例。
- 建议: 与 F-fom-1 同治理（no-op round-trip 排除）；对 `crafts` 超阈值链增加放大步数告警。

### F-fom-4 | chain=0017/0080/0098/0134/0143（black_wool|+14）、0004(raw_platinum)、0070/0075/0078/0110/0112(zinc_nugget)、0061/0146(brass_ingot) | severity=minor | category=unresolved-expected-item
- 位置: `[UNRESOLVED] minecraft:black_wool|+14`(reason=RANK_TRUNCATED)；`createpropulsion:raw_platinum`(candidates=1, reason=BUDGET_TRUNCATED)；`zinc_nugget`/`brass_ingot` 作为 pending 叶。
- 问题: 这些多为可生产物品（black_wool=染料+白羊毛；zinc_nugget=zinc_ingot 拆分；brass_ingot=铜锌合金；raw_platinum 为矿料需生成），但被标记 UNRESOLVED/待解。证据不足以判定为 hard 死路（多为 RANK/BUDGET_TRUNCATED 截断或 tag 变体回匹配失败），故列 minor+suspected，随 F-fom-2 的 tag 修复一并观察。
- 原因: 与 F-fom-2 同源——tag 变体 `|+N` 回匹配失效 + 截断预算；非独立数据污染。
- 建议: 修复 `|+N` 回匹配后再复测这些目标；若仍 unresolved 再升级为 critical。

## 链级判定（代表性抽样，余链同模式）
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0009 | long | bad | redstone↔block 无净产出往返（A7–A15） |
| 0077 | long | bad | gunpowder↔pinch 无净产出往返（A3–A10） |
| 0081 | long | bad | raw_copper↔block + redstone↔block 双往返 |
| 0103 | giant | bad | redstone/raw_iron 双 block 往返（A6–A21, A26–A40） |
| 0084 | giant | bad | raw_iron/redstone 双 block 往返（A11–A27, A38–A54） |
| 0035 | giant | questionable | 含 redstone↔block 往返 |
| 0047/0058/0131/0135 | giant | bad | 含 block↔ingot/redstone 往返 |
| 0087 | giant | questionable | framed_glass exchange 已内置；iron↔block 往返仍存 |
| 0038 | colossal | bad | crafts=15780，block↔ingot 三套无净产出环 |
| 0053 | incomplete | bad | exchange x120 全架于 UNRESOLVED polished_cut_andesite_stairs|+14 |
| 0012 | incomplete | bad | exchange x120 + stairs|+14 未解析 |
| 0002/0003/0004/0005/0006 | incomplete | bad | stairs|+14 未解析 + exchange 50–91 |
| 0062-0068/0090-0097/0106/0107/0138 | incomplete | bad | exchange x100 + stairs|+14 未解析（近同构） |
| 0070/0075/0078/0110/0112 | incomplete | bad | stairs|+14 + zinc_nugget 未解析 |
| 0017/0080/0098/0134/0143 | incomplete | questionable | black_wool|+14 未解析（minor） |
| 其余 incomplete（约 70 条） | incomplete | bad | 同 0053 模式：stair|+14 根 + exchange 爆炸 |

## 规则建议（给汇总者）
- targetRegex: 本批无需要拉黑的 fake/垃圾 target（已 grep 确认无 jei:/quest/villager/market 等）。暂不需要。
- recipeRegex: 收敛可逆压缩放大族（标记 `pattern-cyclic-amplification`）：
  `minecraft:raw_iron_block|minecraft:raw_copper_block|minecraft:redstone_block|minecraft:iron_block|createbigcannons:gunpowder_pinch|createbigcannons:gunpowder_from_pinches|create:cardboard_block|create:cardboard_from_block`（及其反向）。证据：long/giant/colossal 多链出现，0038 单链 ≥50 步。
- builtin 候选:
  1. `loop-guard/noop-roundtrip`（建议标签 junk-ish/pattern）：`allowBoundedAmplification` 应排除“A↔B 互逆且净产出=0”的往返放大。证据：本批 complete 端近乎每链、F-fom-1。
  2. `pattern/exchange` 递归上限（建议标签 duplicate/pattern-exchange-blowup）：当根为 UNRESOLVED tag 变体（如 `polished_cut_andesite_stairs|+14`）或展开步数 ≥50 时停止/排除。证据：0053/0012 等 x120，90+ incomplete 链命中。
  3. 数据层：`|+N` tag 变体回匹配修复（polished_cut_andesite_stairs 等），阻断级联根因。证据：F-fom-2。
- 无新 fake-production / free-resource / data-pollution 模式。
