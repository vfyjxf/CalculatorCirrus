# farmers-colonies batch-003 审查报告
- pack: farmers-colonies
- 批次: batch-003（written=104；类别分布：short 47 / incomplete 17 / medium 15 / giant 12 / long 8 / trivial 3 / colossal 2）
- 审查链数: 104（incomplete 17 / complete 87；全量审查，无抽样：文件体量均小，trivial~colossal 与 incomplete 均逐条或定向读完；short 47 条经 grep 确认无 rule-tag 且全为干净原版路径，抽读样本核对）
- 发现: critical 0 / major 1 / minor 0

## 发现
### F-farmers-colonies-1 | chain=0032 | severity=major | category=loop-or-cycle
- 位置: CHAIN 0032（target=minecraft:splash_potion，system=minecraft:brewing）；actions A2→A4→…→A26（13 步 `item.minecraft.potion.effect_id.<X>.to.item.minecraft.potion.effect_id.<X>.long_<X>` @ brewing_stand），以及最终 A28（splash_potion = potion + gunpowder）。
- 问题: 13 个"延时长效药水"配方每步均为 `in: potion x1, redstone x1 → out: potion x1`（potion 守恒的 identity 变换），被 `pattern/exchange` 标签命中并串成 13 步 no-op 链，每步额外吞掉 1 个红石；最终 `[UNRESOLVED] minecraft:potion x1 (BUDGET_TRUNCATED)` 与 `[UNRESOLVED] minecraft:redstone x1 ×13 (BUDGET_TRUNCATED)`，导致目标 splash_potion 实际无法产出，链整体不可用。
- 原因: `pattern/exchange` 标签误命中酿造"加红石延时"配方族——该族对逼近 splash_potion 目标毫无推进（既不变更效果也不产出新物，仅消耗红石），却因 brewing 子系统无法桥接到 blasting 取红石而把红石留成未解析；同时基础 `potion` 需要酿造台+地狱疣+烈焰粉链，本批次图未建模，故 BUDGET_TRUNCATED。属"已带标签仍失控"：标签触发但链级完全不可用。
- 建议: 将酿造"延时/延长药水"（effect_.*_to_.*_long_.*）排除出 pattern/exchange 命中（视为 no-op identity，不展开、不计入红石需求），并在 brewing 子系统补 `potion` 的酿造配方以消 BUDGET_TRUNCATED。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | banner_rally_guards | bad | black_wool|+14 RANK_TRUNCATED；bow 经 anvil `jei:/synthetic` 放大后 CASCADE_EXHAUSTED（已知 anvil/enchant 泄漏+后缀污染） |
| 0003 | red_banner | bad | black_wool|+14 x6 RANK_TRUNCATED（未染色基底缺失，已知 `|+N` 后缀污染） |
| 0007 | light_blue_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0008 | brown_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0010 | pink_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0012 | black_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0013 | cyan_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0014 | blue_banner | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0016 | black_carpet | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0020 | light_gray_carpet | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0023 | brown_carpet | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0024 | pink_carpet | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0025 | red_carpet | bad | black_wool|+14 RANK_TRUNCATED（同上已知） |
| 0032 | splash_potion | bad | 见 F-farmers-colonies-1（major）：酿造延时药水误标 exchange，13 步 no-op 吞红石，链不可用 |
| 0033 | light_gray_bed | bad | black_bed|+14 x1 RANK_TRUNCATED（已知 `|+N` 后缀污染） |
| 0035 | orange_bed | bad | black_bed|+14 x1 RANK_TRUNCATED（已知 `|+N` 后缀污染） |
| 0086 | colony_banner | bad | black_wool|+14 x2 RANK_TRUNCATED（已知 `|+N` 后缀污染） |
| 0028 | lodestone (colossal) | ok | storage_ladder 放大已带标签，最终 lodestone 可产出 |
| 0093 | supplycampdeployer (colossal) | ok | storage_ladder 放大已带标签；RAW 中多种 planks x45 为锥体候选残留，已 complete |
| 0002/0005/0026/0029/0069/0070/0085 | long 族 | ok | storage_ladder 放大已带标签 |
| 0092 | structurize:caliper (long) | ok | 经 bolt_armor_trim_smithing_template + smithing_trim 产未用 iron_chestplate 再熔 iron_pickaxe 取 iron_nugget，已带 exchange/recycling 标签 |
| 0001/0011/0017/0018/0021/0030/0071/0072/0073/0078/0079/0100 | giant 族 | ok | storage_ladder / exchange / recycling 放大已带标签（含 raiser_armor_trim 模板复制、blocksubstitution 等） |
| 0015/0037/0038/0074~0084/0087/0088/0091 | medium 族（15） | ok | 食物/小屋/木桶，干净直接路径 |
| 0004/0006/0009/0019/0022/0027/0031/0034/0036/0039~0068/0094/0095/0097~0103 | short 族（47） | ok | 原版方块/染色玻璃/石切，无 rule-tag，直接路径 |
| 0089/0090/0096 | trivial 族（3） | ok | 直接烹饪配方 |

## 规则建议（给汇总者）
- targetRegex: 无新增目标级排除（`decompress` 已在 blacklist）。
- recipeRegex: `item\.minecraft\.potion\.effect_id\.[^.]+\.to\.item\.minecraft\.potion\.effect_id\.[^.]+\.long_[^.]+` —— 酿造"加红石延时"配方族，potion 守恒的 identity no-op，应排除 pattern/exchange 命中（证据 CHAIN 0032：13× 串成 no-op 且每步吞红石，链不可用）。
- builtin 候选:
  1. `id\|+\d+` 后缀归一化（如 `black_wool|+14`、`black_bed|+14`、`oak_planks|+10`、`acacia_log|+3`）：解析时剥离 `|\+N` 使可合成原版物能匹配配方。证据：15/17 条 incomplete 链因 `black_wool|+14` / `black_bed|+14` RANK_TRUNCATED 断链（未染色基底缺失致重染级联 + `|+N` 后缀污染，已知规划器级问题，本批仍系统性触发）。
  2. brewing 子系统补全 `potion` 基础酿造配方（水瓶+地狱疣+烈焰粉链），消除 CHAIN 0032 的 `potion`/`redstone` BUDGET_TRUNCATED。
- 无其它新模式（storage_ladder / recolor / exchange / recycling 放大环占本批 90%+，均已在 round-1/2 标签内，未再报）。
