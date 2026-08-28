# lost-world batch-005 审查报告（选路质量深审 · producers 索引交叉核对）

- pack: lost-world
- 批次: batch-005（written=9 链：incomplete 8 / long 1；本批 solved=150，结构性去重 141，仅 9 条写出）
- 审查链数: 9（全部 9 条均过；incomplete 大文件按链边界全读，long 全读）
- 方法: 对每条链抽取非平凡中间品 EXECUTE，用 `cirrus-audit-data/lost-world/producers.tsv`(生产者数) + `producers.jsonl`(替代者明细) 逐物交叉核对，仅报 worse-alternative / silly-detour / impure-recipe / 目标怪象。
- 发现: critical 0 / major 3 / minor 0

## 说明（已排除，不报）
- 各链大量 `silentgear:netherwood_*` 的 `pattern/exchange`(x21–x22) 与 `intent/recycling`(x5) 放大子树、0006 的 `pattern/storage_ladder`(lapis 块↔粉) —— 均带 builtin-rule-tag，放大行为本身不报。
- `jei:/synthetic/*`、`minecraft:*` 空输入(自由资源伪影)、`|+N` id、UNRESOLVED/CAASCADE 截断、机器映射、已带标签家族放大 —— 按红线排除。
- `light_blue_dye` 两处均带 `[rule-tag: pattern/recolor]`，排除。
- 唯一生产者的选择（cannon_boat_oak 仅 1 生产者、cannon_boat_bamboo 另一候选为空输入伪影）一律 ok。

## 发现

### F-lostworld-1 | chain=0000 | severity=major | category=worse-alternative
- 位置: A66 `minecraft:mossy_cobblestone_from_moss_block` → 喂 A67 目标 `mossy_cobblestone_wall`
- 问题: 所选配方输入为 `moss_block x1 + cobblestone x1`（2 个基础材料，cobblestone 虽为 raw 但仍需额外一种材料）；索引中存在更直接生产者 `minecraft:mossy_cobblestone_from_vine`（输入仅 `vine x1`）。`vine` 在 producers.tsv 中无生产者（=世界 raw，可达）。
- 原因: planner 偏好"两基础材料合成"配方，未选单原料 `from_vine`；选 `from_moss_block` 反而多消耗一种材料，且与本链巨大 recycle 子树（A56–A65，tagged）耦合。
- 建议: 对 `mossy_cobblestone` 类物品路由时优先单输入配方 `from_vine`（更少材料、直连）；`from_moss_block` 仅在 vine 不可达时退用。

### F-lostworld-2 | chain=0008 | severity=major | category=silly-detour
- 位置: A77 `minecraft:granite_wall_from_granite_stonecutting`(granite→granite_wall) + A78 `block_variants:stonecutting/polished_granite_wall_from_granite_wall`(granite_wall→polished_granite_wall)
- 问题: 链已持有 `granite`(A76)，却先石切出中间物 `granite_wall` 再石切到目标，共 2 步。索引中存在直连 `block_variants:stonecutting/polished_granite_wall_from_granite`（输入 `granite x1` → `polished_granite_wall`，1:1 直连，与 A76 输入相同）。`granite_wall` 是纯冗余中间步。
- 原因: planner 选择了"经墙中转"的石切变体而非直连变体；该包仅此 1 个物品同时具备 `_from_granite_wall` 与 `_from_granite` 两种石切配方，属单例而非系统族。
- 建议: 对 `block_variants:*` 石切，遇 `*_from_<base>_wall` 与 `*_from_<base>` 并存时优先直连 1:1；可直接收敛 `block_variants:stonecutting/polished_granite_wall_from_granite_wall`（证据 1 条）。

### F-lostworld-3 | chain=0006 | severity=major | category=worse-alternative
- 位置: A1 `lapis_lazuli_from_blasting_deepslate_lapis_ore` → A2 `lapis_block` → A3 `lapis_lazuli` → A4 `minecraft:blue_dye`（喂 blueprint_paper），位于 leggings 链非标签子树
- 问题: 为得 blue_dye 走了"深板岩青金石矿→熔炼→块→解块→染"4 步。索引中存在 `minecraft:blue_dye_from_cornflower`（输入仅 `cornflower x1` → blue_dye，1 步）。`cornflower` 在 producers.tsv 中无生产者（=世界 raw，可达）。A1–A3 的 lapis 块/解块即 storage_ladder 放大，换成 cornflower 可整体省去。
- 原因: planner 偏好"矿→块→粉"链路合成染料，未选单花 `from_cornflower`。同一偏差也出现在 0001(A267/A273)、0004(A274/A280) 的 blue_dye 步（其下游为 recolor 子树，故仅以 0006 为主证）。
- 建议: 染料生产路由优先单原料花配方（cornflower→blue_dye 等），避免为染料走矿→块→粉放大。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | questionable | F-1：mossy_cobblestone 选 from_moss_block 而非 from_vine（单原料） |
| 0001 | incomplete | questionable | F-3：blue_dye 走 lapis 放大（下游 recolor）；目标 cannon_boat_oak 唯一生产者 ok |
| 0002 | incomplete | ok | polished_granite_slab 选 1:1 stonecutting，最优 |
| 0003 | incomplete | ok | mossy_cobblestone_stairs 选 1:1 stonecutting，最优 |
| 0004 | incomplete | questionable | F-3：blue_dye 走 lapis 放大；目标 cannon_boat_bamboo 选真配方 ok |
| 0005 | incomplete | ok | checker_slab 选 stonecutting，最优 |
| 0006 | long | questionable | F-3：blue_dye 走 lapis 四步而非 cornflower 单步 |
| 0007 | incomplete | ok | mossy_cobblestone_slab 选 1:1 stonecutting，最优 |
| 0008 | incomplete | questionable | F-2：granite→granite_wall→polished_granite_wall 绕两步，有 from_granite 直连 |

## 规则建议（给汇总者）
- targetRegex: 无（本批目标均合法可合成物，无展示/重复变体怪象）。
- recipeRegex: 无系统性 mod 配方族误选。3 处均为孤立选路偏好偏差：偏好"两基础材料合成"(from_moss_block) 与"间接石切/矿→块→粉"而非单原料/直连 1:1。仅 block_variants:stonecutting/polished_granite_wall_from_granite_wall 可单点收敛（证据 1 条），但属单例不构成族。
- builtin 候选: 无跨包通用新模式；建议后续在路由成本层加"单输入/直连 1:1 优先"权重，而非正则收敛。
