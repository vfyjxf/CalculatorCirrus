# enigmatica10 batch-001 审查报告
- pack: enigmatica10
- 批次: batch-001（written=150，全部为 incomplete 类别；mega-wide 占多数，colossal 少量）
- 审查链数: 150（聚合扫描 150 条 + 详读 18 条代表性链头/全文：0000/0001/0008/0023/0030/0040/0045/0052/0056/0063/0069/0076/0095/0100/0114/0123/0137/0149；大文件 28MB/180KB·条，抽样下限 40，已超额）
- 发现: critical 2 / major 6 / minor 1

## 摘要
本批次 150 条链全部以 `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]` 结束，根因高度一致：**`chisel:`/`chipped:` 等装饰方块 stonecutting 变体被当作独立生产路径（已标 `pattern/exchange`，属内置规则），其组合爆炸平均占每条链 162–502 个 exchange 步骤（全批 32877 步）**，消耗了几乎全部 root-input budget，级联导致 **3567 个 `[UNRESOLVED]` 中有 886 个是原版可直制的常见物**（iron_ingot / copper_ingot / redstone / diamond / gold_ingot / 各类 planks / stick / glass / raw_iron_block …），全部 `reason=BUDGET_TRUNCATED`。这是本批唯一系统性缺陷。

> 已内置规则未重复报告：flax_block↔flax_block_uncrafting 等 `intent/recycling` 无净产出环（全批 919 处，均带 rule-tag）、`pattern/exchange` 装饰变体步、及 `jei:/synthetic/*`（@ industrialforegoing:enchantment_applicator，合法合成 id）均按口径不再逐条报，仅作为规则排除建议。

## 发现
### F-enigmatica10-1 | chain=0114 | severity=critical | category=unresolved-expected-item
- 位置: [UNRESOLVED] minecraft:acacia_planks|+14 x2 (L1090)、minecraft:diamond x3 (L1091)、minecraft:netherite_ingot x1 (L1093)、minecraft:netherite_upgrade_smithing_template x1 (L1092)
- 问题: 原版可直制常见物被标 unresolved（reason=BUDGET_TRUNCATED）。链头 A1–A9 全为 `enigmatica:minecraft/stonecutting/chisel_*_acacia_planks_from_chisel_acacia_planks`，262 个 `pattern/exchange` 步都在展开 chisel 橡木板的装饰变体，预算烧完后连作为基材的 `minecraft:acacia_planks` 自身都解析不出。
- 原因: chisel/chipped 类 stonecutting 变体被当作独立生产路径（pattern/exchange 已内置标记），但其组合爆炸耗尽 root-input budget，级联使真正需要的原版基础物无法解析。
- 建议: 将 chisel/chipped 装饰变体生产从生产基材中排除（rule-exclusion），或提高原版基础物解析优先级、不受 budget 截断影响。

### F-enigmatica10-2 | chain=0123 | severity=critical | category=unresolved-expected-item
- 位置: [UNRESOLVED] minecraft:oak_planks|+622 x4 (L1417)、minecraft:diamond x3 (L1411)、minecraft:emerald x8 (L1422)、minecraft:gold_ingot x12/16 (L1418/L1421)、minecraft:gold_block (L1430)、cobblegengalore:block_gen_iron x8 (L1420)
- 问题: 同上，oak_planks/diamond/emerald/gold_ingot 等原版常见物 BUDGET_TRUNCATED 未解析；且 `block_gen_iron x8`、`block_gen_emerald` 等 cobblegengalore 发电机亦未解析（由交换爆炸级联）。
- 原因: 同上 exchange 爆炸 → budget 耗尽 → vanilla 基础物级联 unresolved。
- 建议: 同 F-1。

### F-enigmatica10-3 | chain=0000 | severity=major | category=unresolved-expected-item
- 位置: [UNRESOLVED] minecraft:raw_iron_block|+66 x1 (L)、minecraft:stick|+3 x4/8 (L)、minecraft:glass|+94 x3/6 (L)、minecraft:acacia_log|+11 x1 (L)
- 问题: 这些原版基础物 unresolved；同时链内存在 `supplementaries:flax_block`↔`flax_block_uncrafting` 无净产出环（A8–A24，已标 intent/recycling），外加 351 个 exchange 步骤，预算被大量消耗后 vanilla 基础物丢失。
- 原因: 交换模式爆炸 + recycling 环消耗预算，级联 vanilla 基础物 unresolved。
- 建议: exchange/recycling 模式升级为排除基材；强制预解析原版基础物。

### F-enigmatica10-4 | chain=0023 | severity=major | category=unresolved-expected-item
- 位置: 链内 vanilla-common unresolved = 12（electric_farmer）；builtin-rule-tags: intent/recycling x16, pattern/exchange x340
- 问题: 340 个 exchange 步（chisel/chipped 变体）占满预算，导致 12 个原版基础物 BUDGET_TRUNCATED 未解析；depth=242/actions=631 显示路径极宽。
- 原因: 同 F-1 级联。
- 建议: 同 F-1。

### F-enigmatica10-5 | chain=0030 | severity=major | category=unresolved-expected-item
- 位置: 链内 vanilla-common unresolved = 11（electric_quarry）；pattern/exchange x290
- 问题: 290 个 exchange 步爆炸，11 个原版基础物 unresolved。
- 原因: 同 F-1 级联。
- 建议: 同 F-1。

### F-enigmatica10-6 | chain=0045 | severity=major | category=amount-insanity
- 位置: status: crafts=240092（target=appflux:fe_256m_portable_cell）；builtin-rule-tags: pattern/exchange x336
- 问题: 单条链 crafts 高达 240092；链头 A1–A12 全为 `enigmatica:minecraft/stonecutting/chipped_*_cyan_stained_glass_from_chipped_cyan_stained_glass`（336 个 chipped 变体步），明显推高数量级。256m 电池虽有 256× 层阶放大的真实净产出，但 crafts 量级被 chipped 交换爆炸放大到失控。
- 原因: 装饰变体 exchange 爆炸叠加 cell 层阶放大；net output 存在但数量级失控。
- 建议: 排除 chipped 变体基材后重测 crafts；如仍高，则归为合理的层阶放大。

### F-enigmatica10-7 | chain=0052 | severity=major | category=amount-insanity
- 位置: status: crafts=180236（target=appflux:fe_256m_cell）；pattern/exchange x299
- 问题: crafts=180236，链头同样以 chipped cyan stained glass 变体 299 步起手。
- 原因: 同 F-6。
- 建议: 同 F-6。

### F-enigmatica10-8 | chain=0069 | severity=major | category=amount-insanity
- 位置: status: crafts=179954（target=appflux:core_256m）；pattern/exchange x96
- 问题: crafts=179954（appflux 核心也走 256m 层阶放大 + chipped 变体生产）。
- 原因: 同 F-6。
- 建议: 同 F-6。

### F-enigmatica10-9 | chain=0137 | severity=minor | category=amount-insanity
- 位置: status: crafts=17896（target=advanced_ae:quantum_storage_128）；pattern/exchange x271
- 问题: crafts=17896，数额偏高但存在净产出（128 存储升级），主要由 271 个 exchange 步放大，证据不足以判 major，暂记 minor suspected。
- 原因: 同 F-6（弱放大）。
- 建议: 排除变体基材后复核。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0114 | unresolved-expected-item | bad | 262 exchange 步烧预算，acacia_planks/diamond/netherite_ingot 等原版物未解析 |
| 0123 | unresolved-expected-item | bad | chisel 爆炸级联，oak_planks/diamond/emerald/gold_ingot 未解析 |
| 0000 | unresolved-expected-item | questionable | flax recycling 环+351 exchange，raw_iron_block/stick/glass 未解析 |
| 0023 | unresolved-expected-item | questionable | 340 exchange，12 原版物 unresolved |
| 0030 | unresolved-expected-item | questionable | 290 exchange，11 原版物 unresolved |
| 0045 | amount-insanity | bad | crafts=240092 被 chipped 变体放大 |
| 0052 | amount-insanity | bad | crafts=180236 被 chipped 变体放大 |
| 0069 | amount-insanity | bad | crafts=179954 被 chipped 变体放大 |
| 0137 | amount-insanity | questionable | crafts=17896，弱放大，minor suspected |
| 0008 | unresolved-expected-item | questionable | 169 exchange，4 原版物 unresolved |
| 0040 | unresolved-expected-item | questionable | 302 exchange，8 原版物 unresolved |
| 0056 | unresolved-expected-item | questionable | 226 exchange，5 原版物 unresolved |
| 0076 | unresolved-expected-item | questionable | 219 exchange，8 原版物 unresolved |
| 0095 | unresolved-expected-item | questionable | 502 exchange（最高），7 原版物 unresolved |
| 0100 | unresolved-expected-item | questionable | 230 exchange，6 原版物 unresolved |
| 0149 | unresolved-expected-item | questionable | 162 exchange，5 原版物 unresolved |

其余 134 条链经 index.tsv + `[UNRESOLVED]` 聚合扫描，全部含 2–12 个 unresolved 原版常见物（reason=BUDGET_TRUNCATED），且每条带 162–502 个 pattern/exchange 步，判定 **questionable（unresolved-cascade）**，根因与 F-1 一致。本批无 ok 链。

## 规则建议（给汇总者）
- targetRegex: 无（本批 target 均为合理机器/物品，问题不在 target 而在生产基材展开）。
- recipeRegex: `enigmatica:minecraft/stonecutting/(chipped|chisel)_.*` —— 将 chiseled/chipped 装饰变体的 stonecutting 配方族标记为排除（不作为生产基材收敛），证据：全批 32877 个 pattern/exchange 步，抽样链头 A1–A12 几乎全为 chipped/chisel 变体；期望可消除 80%+ 的 exchange 步与大部分 vanilla-common 级联 unresolved。
- recipeRegex(补): `.*:(chipped|chisel):.*` 与 `.*/stonecutting/.*(chipped|chisel)_.*` 一并纳入，覆盖配方 id 与产出 item 两种命名。
- builtin 候选: `pattern/exchange` 已内置，但当前为"允许展开"语义；建议新增 **exclude-substrate 模式**（标签建议 `pattern-exchange-exclude-variant`），对装饰方块变体 stonecutting 仅保留"直接目标变体"路径、禁止以变体作通用基材。证据：每条链 162–502 exchange 步且与 target 无真实依赖（如 fe_256m_cell 链以 chipped cyan stained glass 起手）。
- 新增 **vanilla-basics 预解析** 规则：强制在 budget 截断前优先解析 `minecraft:` 常见物（planks/log/ingot/redstone/diamond/emerald/glass/stick 等），避免其因级联被标 unresolved。证据：886 个 unresolved 为原版常见物。
- 无新模式（除上述 exchange 变体排除与 vanilla 预解析外）。
