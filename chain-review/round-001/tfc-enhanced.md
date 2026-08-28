# tfc-enhanced batch-001 审查报告
- pack: tfc-enhanced
- 批次: batch-001（written=150；类别分布 giant 93 / long 21 / medium 15 / colossal 14 / incomplete 6 / short 1）
- 审查链数: 150（索引级全量 150；完整读 20 条代表链覆盖全部 6 类别：short 1、incomplete 6〈0028 全读 + 其余 5 条 grep 同构确认〉、medium 15、colossal 1〈0000〉、long 1〈0019〉、giant 1〈0007〉；其余经 grep 全量 survey + 同构比对）。无 `jei:`/`synthetic` 命名空间、无 `crafts>=100000` 失控。
- 发现: critical 1 / major 4 / minor 0

## 发现

### F-tfc-enhanced-1 | chain=0028（同构覆盖 0032/0034/0041/0044/0047；giant 约 60/93、long 普遍亦含）| severity=major | category=loop-or-cycle
- 位置: A1–A8（`minecraft:slime_block` ⇄ `tfc:crafting/glue`）
- 问题: `minecraft:slime_block` 输入 `tfc:glue|+1 x9` 产出 1 slime_block；`tfc:crafting/glue` 输入 1 slime_block 产出 `tfc:glue x9`（A2/A4/A6/A8）。9 glue → 1 slime → 9 glue 为**无净产出互逆环**，重复 4 次把 depth 推到 22、actions 51。
- 原因: `minecraft:slime_block` 配方输入竟为 `tfc:glue`（原版应为 9 slimeball），与 `tfc:crafting/glue` 构成 9:1 互逆 no-op；`loop=allowBoundedAmplification` 下被误当放大。根 RAW 已有 `tfc:glue x18`，环不产出任何净 glue。
- 建议: 收敛/排除 `slime_block⇄glue` 9:1 互逆环；glue 直接取 raw，不要经 slime 往返。

### F-tfc-enhanced-2 | chain=0028（water_wheel）/ 0000（lectern）/ 0007（water_wheel）| severity=major | category=loop-or-cycle
- 位置: 0028 A33–A49、0000 A19–A36、0007 A34–A39（`tfc:crafting/wood/planks/<wood>` ⇄ `tfc:crafting/wood/lumber/<wood>_from_planks`）
- 问题: `planks → lumber(×4) → planks` 互逆无净产出往返（如 0000 A23 planks→4 lumber，A24 lumber→1 planks），且每轮额外消耗 `tfc:metal/saw/steel|+8`。0028 中该环重复 5+ 次（A35/A38/A41/A44/A48）。
- 原因: `lumber_from_planks` 与 `planks_from_lumber` 互逆 no-op 被当放大使用；lumber 本应只由 `_from_logs` 产出。
- 建议: 排除 `planks⇄lumber` 互逆环；lumber 仅保留 `from_logs` 路径。

### F-tfc-enhanced-3 | chain=0028 / 0007 / 0000 | severity=major | category=catalyst-misuse
- 位置: 0028 A35/A38/A41/A44/A48、0000 A15/A19/A23/A27/A31/A35、0007 A26/A30/A34/A37（均为 `tfc:metal/saw/steel|+8` 作输入）
- 问题: 锯是 TFC 可复用工具，却被按每次 lumber 步骤的 `|+8`（耐久）当**消耗品**计入需求，每条 water_wheel/lectern 链重复计 5–6 把钢锯。
- 原因: saw 的耐久标签被当作消耗；规划器未将其标记为 catalyst，导致需求被重复累加。
- 建议: 将 `^tfc:metal/saw/.*`、`^tfc:metal/hammer/.*` 标记为 catalyst（只计一次生产，不随步骤重复消耗）。

### F-tfc-enhanced-4 | chain=0000 / 0019 / 0007 / 0137（all-of 标签全成员展开，约 110+ 链）| severity=major | category=tag-expansion-overreach
- 位置: 各链 `[RAW]` 行
- 问题: 接受任意-of 标签的槽位被展开为**全部成员并各自作为 raw 需求**，尽管每个动作只消耗其中一个成员：
  - 0000：`tfc:wood/twig/<16 wood> ×45` 全列，但仅 `twig/kapok` 被 A3/A5/A7/A9/A14/A18/A22/A26/A30/A34 消耗；8 种 `tfc:metal/saw/<metal> ×6` 全列，仅 `saw/bismuth_bronze`（造）+ `saw/steel`（消耗）被用。raw_kinds=32。
  - 0019（gem/sapphire）：8 种 `tfc:metal/hammer/<m> ×2`（仅 bismuth_bronze 被造 A10/A17）、10 种 `tfc:powder/<gem> ×2`（仅 sapphire 被用 A27）、8 种 `tfc:sand/* ×2`（仅 red_sand A27）、4 种 `tfc:stone/hammer/<class> ×2`（仅 metamorphic A11/A24）、全部 loose/mossy rock 与 groundcover 均列入。raw_kinds=72。
  - 0007：`tfc:wood/twig/<16 wood> ×14` 与 `tfc:wood/planks/<16 wood> ×4` 全列，仅 kapok twig 与 hickory/douglas_fir/ash/acacia planks 被消耗。raw_kinds=67。
  - 0137（polished_granite）：全部 21 种 `tfc:rock/cobble/<rock> ×2` 全列，仅 `gabbro`(A8) 与 `andesite`(loop) 被消耗。raw_kinds=22。
- 原因: 规划器对 any-of 输入的每个候选成员，把其生产代价并入 root RAW，而非只选一个具体代表成员满足该槽位。语义上成员都合法，但"需要全部"严重夸大 raw 需求。
- 建议: any-of 输入只选一个代表成员计 raw；或加收敛规则排除"全成员 raw 展开"（见规则建议 builtin 候选）。

### F-tfc-enhanced-5 | chain=0028（同构 0032/0034/0041/0044/0047）| severity=critical | category=unresolved-cascade
- 位置: 各链 `[UNRESOLVED] tfc:metal/saw/steel|+8 x1`（×4/链）、`tfc:wood/planks/<wood> x3`（reason=BUDGET_TRUNCATED）
- 问题: 6 条 water_wheel 链 `status` 标 `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`，使本可生产的物被假 unresolved：
  - `tfc:metal/saw/steel`：TFC 标准可铸造工具（saw blade 铸模 + twig），链内其他金属锯（bismuth_bronze 等）已成功制造，钢锯因预算被截断而 unresolved。
  - `tfc:wood/planks/<wood>`：目标木种自身的 planks，链内 planks⇄lumber 环已产出，却仍标 unresolved x3。
  - 两物均为"明显本可解析"却被级联 unresolved，6 条链整体不可用。
- 原因: F-1 的 slime⇄glue 无净环 + F-4 的全标签展开耗尽 root input budget，级联出假 unresolved。
- 建议: 修复 F-1/F-4 后重跑；钢锯与目标木 planks 应可解析，incomplete 应消除。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0028/0032/0034/0041/0044/0047 | incomplete | bad | slime⇄glue 环 + 全标签展开耗预算，钢锯/planks 假 unresolved，链不可用（F-1/F-4/F-5） |
| 0000 | colossal | questionable | 全 wood twig/saw 展开 + planks⇄lumber 环 + 钢锯消耗（F-2/F-3/F-4） |
| 0007 | giant | questionable | 全 wood twig/plank 展开 + slime⇄glue 环 + 钢锯消耗（F-1/F-3/F-4） |
| 0019 | long | questionable | 全 hammer/powder/sand/rock/groundcover 展开，raw_kinds=72（F-4） |
| 0137/0142/0143/0144/0145 | medium | questionable | 全 21 种 rock 展开为 raw，仅 gabbro+andesite 被用（F-4） |
| 0107–0120（glazed_terracotta 等） | medium | ok | 直接路径，无环/无过度展开，raw_kinds 2–5 |
| 0012 | medium | ok | rustic_finish 直接路径，正常 |
| 其余 giant/colossal/long（complete） | — | questionable | 普遍携带 slime⇄glue 环 / 全标签展开 / saw 催化剂误计（F-1~F-4），结构可解但 raw 需求被严重夸大 |

## 规则建议（给汇总者）
- targetRegex: 无（目标均合法，问题在配方/展开层，不应按 target 排除；decompress 已在内置黑名单）。
- recipeRegex:
  - `^minecraft:slime_block$`（当 in 含 `tfc:glue`）—— 收敛 `slime_block⇄glue` 9:1 互逆环；证据：incomplete 6/6、giant ≈60/93、long 普遍（42 处）。
  - `tfc:crafting/wood/lumber/[^/]+_from_planks` ↔ `tfc:crafting/wood/planks/[^/]+` 互逆——收敛 `planks⇄lumber` 环；证据：0007/0000/0028。
  - catalyst 规则：`^tfc:metal/saw/.*`、`^tfc:metal/hammer/.*` 标记催化剂不随步骤消耗；证据：0000/0007/0019。
- builtin 候选: `pattern-tag-expand-junk` —— any-of 标签被展开为全部成员并各自计 raw（应只取一个代表成员）。跨包通用（TFC/Firmalife 的 wood/metal/gem/rock/sand/groundcover 任意标签）。证据：0000/0007/0019/0137 等 100+ 链，raw_kinds 被推到 22/32/67/72。建议作为内置收敛规则，预计可让本批 coverage 与 convergence 大幅提升（当前 convergence 0%）。
- 无新模式时写"无"：不适用（已提出 1 条强内置候选）。
