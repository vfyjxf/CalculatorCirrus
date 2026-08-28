# create-ultimate batch-005 审查报告（round-6 选路质量深审）

- pack: create-ultimate
- 批次: batch-005（written 88 链；类别分布 giant 44 / incomplete 28 / short 10 / long 3 / medium 2 / colossal 1）
- 审查链数: 88（全覆盖；其中完整抽样细读 10 short + colossal 0032 + giant 0008/0012/0079 + incomplete 0058/0069/0073/0077/0078，其余 44 giant + 17 incomplete 经 producers 索引逐 EXECUTE 交叉核对）
- 方法: 对每条链的每个非平凡 EXECUTE 产出物，用 `grep -F '<item>\t' producers.tsv` 取生产者数，用 `grep '"for":"<item>"' producers.jsonl` 取替代者明细；自动以「所选配方 distinct 输入数 − 最优替代者 distinct 输入数 ≥ 2」与「所选为杂产而存在纯配方」双重阈值初筛，再逐条人工判定。
- 发现: critical 0 / major 2 / minor 0

## 审查结论概要

本批 88 链绝大多数为良性：装饰方块阶梯（stonecutting ladder）、铜/金/铁/钢等 ingot 的双配方（planner 均选了合法配方，空输入的 `create:crafting/materials/*` 垃圾配方被正确规避）、`storage_ladder`/`exchange`/`recolor` 已带 rule-tag 的放大（按口径不报）、以及跨 mod 命名空间的 ingot↔block/nugget 压缩-解压转换（无可替代直连，属必要转换）。全量交叉核对后，唯一满足「存在更优生产者」阈值的产出物是 `minecraft:gunpowder`，集中在两条链。未发现 `impure-recipe`（所有所选配方均为纯产）或目标选择怪象（目标均为合法成品）。

## 发现

### F-create-ultimate-1 | chain=0008 | severity=major | category=worse-alternative
- 位置: A27→A28→A29 段（产出物 `minecraft:gunpowder` 在 A29 经 `createbigcannons:gunpowder_from_pinches` 生产）
- 问题: 链中 A27 已用直连配方 `minecraft:gunpowder`（输入 cgs:charcoal_dust+sulfur+niter）产出 gunpowder x12。随后 A28 把其中 x4 转成 `createbigcannons:gunpowder_pinch`，A29 再用 `createbigcannons:gunpowder_from_pinches`（输入 9×gunpowder_pinch）转回 gunpowder，最终 A30 用的正是 A29 的 gunpowder。这是一个无净收益的 gunpowder→pinch→gunpowder 往返：A27 余量（12−4=8）已足够喂 A30（需 4）。
- 原因: planner 为 `minecraft:gunpowder` 选了 `createbigcannons:gunpowder_from_pinches` 作为生产者，而该配方输入 `gunpowder_pinch` 只能由 gunpowder 制得（链内 A28 即 `gunpowder → gunpowder_pinch`），形成循环依赖；索引中存在更优的直连生产者 `minecraft:gunpowder`（producers.tsv 计数=2，两条均可证）。所选者明显差于已使用过的直连配方。
- 建议: 对 `minecraft:gunpowder` 优先选用直连配方 `minecraft:gunpowder`，并将 `createbigcannons:gunpowder_from_pinches` 降权/排除为 gunpowder 的生产路径（其输入依赖 gunpowder 自身，易诱发往返放大）。

### F-create-ultimate-2 | chain=0079 | severity=major | category=worse-alternative
- 位置: A21 起贯穿至 A37 的 gunpowder↔gunpowder_pinch 往返放大（A21 直产 `minecraft:gunpowder` 后，A22→A37 反复 `gunpowder→gunpowder_pinch→gunpowder`）
- 问题: 目标为 `createbigcannons:wrought_iron_drop_mortar_end`。A21 已用直连配方 `minecraft:gunpowder` 产出 gunpowder x3，但 planner 随后用 16+ 层 `gunpowder_pinch`/`gunpowder_from_pinches` 来回转换放大 gunpowder（A22 pinch→A23 gunpowder→A24 pinch→…→A37 gunpowder），最终喂给 A42 总成。该放大是无净收益的循环（pinch 只能由 gunpowder 制得）。
- 原因: 同 F-1，planner 把 `createbigcannons:gunpowder_from_pinches`（循环依赖配方）当作 gunpowder 的主要生产者并据此放大，而索引中存在更优直连生产者 `minecraft:gunpowder`（计数=2）。这是 worse-alternative 叠加 loop 放大；按 round-6 选路质量口径归为 worse-alternative。
- 建议: 同 F-1，治理 `createbigcannons:gunpowder_from_pinches` 作为 gunpowder 生产路径的选用；gunpowder 需求应直接由 `minecraft:gunpowder` 满足。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0008 | giant | bad | gunpowder 经 pinch 往返放大，选了循环的 `gunpowder_from_pinches` 而非已用的直连 `minecraft:gunpowder` |
| 0079 | giant | bad | 17+ 层 gunpowder↔pinch 无净收益放大，所选生产者循环依赖 |
| 其余 86 链 | 各类 | ok | 良性阶梯/双配方（合法者被选）/已带 tag 的放大/跨 mod 压缩解压转换，无更优替代可证 |

## 规则建议（给汇总者）

- targetRegex: 无（本批目标均为合法成品，无展示物/重复变体/无意义目标）。
- recipeRegex: `createbigcannons:gunpowder_from_pinches` —— 收敛为 gunpowder 的非优选/排除路径（证据：chain 0008、0079，均因该配方输入 `gunpowder_pinch` 只能由 gunpowder 制得而形成循环放大，直连配方 `minecraft:gunpowder` 在索引中存在且同链已用）。可选广义化：`_from_pinches$` 类配方在产出物与其 pinch 输入同根时需审查。
- builtin 候选: 无跨包通用新模式（gunpowder↔pinch 仅 createbigcannons 单 mod 现象）。
