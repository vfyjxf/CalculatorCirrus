# LuckyBlockChallenge batch-002 审查报告
- pack: LuckyBlockChallenge
- 批次: batch-002（written=1，类别分布：medium 1）
- 审查链数: 1（complete 1 / incomplete 0；整批仅 1 条 medium 链，全读）
- 发现: critical 0 / major 0 / minor 1

## 发现
### F-LuckyBlockChallenge-1 | chain=0000 | severity=minor | category=silly-detour
- 位置: [A6]→[A7]→[A8]（minecraft:acacia_wood / minecraft:acacia_planks / minecraft:bowl）
- 问题: 链尾 `rabbit_stew`（[A10]）本身正确——输入 `baked_potato`(A1)、`cooked_rabbit`(A3)、`carrot`、`bowl`(A8)、`brown_mushroom` 与 `minecraft:rabbit_stew_from_brown_mushroom` 原版配方一致。但 A6→A7 分支（`acacia_log x4 → acacia_wood x3 → acacia_planks x4`）产出的 `acacia_planks` 在链内**无任何消费方**；真正做碗的是 [A8]，其输入为 `cherry_planks`（RAW 叶子，见 [RAW] 行），而非 A7 的 `acacia_planks`。同时 [A8] 标注 `<= from A7`（依赖 A7），但其实际输入是 `cherry_planks`，依赖边与真实数据流向不符。
- 原因: 规划器对碗配方 `#minecraft:planks` 标签展开为多 plank 候选后，树结构把 A8 挂在 A7 之下，却最终挑选了另一种 raw plank（cherry_planks）做碗，导致自制 `acacia_planks` 分支成为死分支；属规划器 tag 收敛/依赖标注不一致，非配方本身错误。
- 建议: 在 planner 层排查 `#planks` 标签展开后未收敛到所制 plank 的问题（自制 plank 应被下游消费或不应生成）；本链无需 junk/duplicate 排除。

## 链级判定
| chain | category | verdict | 一句话 |
|-------|----------|---------|--------|
| 0000 | silly-detour | questionable | rabbit_stew 目标路径正确；但 A6→A7 自制的 acacia_planks 未被消费（碗改用 raw cherry_planks），且 A8 的 `<= from A7` 依赖标注与真实输入不符——死分支+标注不一致。 |

## 规则建议（给汇总者）
- targetRegex: 无
- recipeRegex: 无
- builtin 候选: 暂无跨包通用新模式。可观测到 1 条例证（CHAIN 0000 A6/A7/A8）：自制木板（log→wood→planks）分支产出未被下游消费、而下游戏改用另一种 raw plank，并伴随依赖边标注不一致。建议作为 planner 层 tag(#planks) 收敛缺陷上报，而非新增 junk/duplicate 规则。
- 无新模式时写"无"：除上条 planner 层观察外，本批次无新增筛选/标签建议。
