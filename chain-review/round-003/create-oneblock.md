# create-oneblock batch-003 审查报告
- pack: create-oneblock
- 批次: batch-003（written=141，全部 incomplete；类别分布：incomplete 141，无 trivial/short/medium/long/giant/colossal 分卷，全部落在 chains-incomplete-001.txt 9.7MB）
- 审查链数: 141（incomplete 141 / complete 0）
  - 抽样说明：单文件 9.7MB、95803 行、141 链，平均 ~680 行/链、~69KB/链（未超 100KB/条阈值，但体量仍大）。
  - 采取「全链指标化 + 高信号链全文通读」：对全部 141 链提取 builtin-rule-tags、未打标 EXECUTE 步数、32 位溢出哨兵、blasting@fan 错配、巨型 crafts；对高信号链 0129/0140/0077/0136/0100/0072/0139/0123/0002/0003 全文通读。
- 发现: critical 0 / major 2 / minor 0

## 发现
### F-create-oneblock-1 | chain=0140 | severity=major | category=amount-insanity
- 位置: CHAIN 0140 `createdeco:locked_andesite_door`；步 A155 `minecraft:stick_from_bamboo_item x1048576 @ sophisticatedbackpacks:crafting_upgrade`、A157 `create:cut_ochrum_stairs x36864 @ ...`、A200 `minecraft:diamond_from_blasting_deepslate_diamond_ore x884763 @ minecraft:blast_furnace`、A221 `x221211`、A243 `x55242`（三处合 ~1.16M 钻石）、A387 等散点；status crafts=19814398281。
- 问题: 一扇 createdeco 门锁门，规划器推导出约 **1.16M 钻石**（深板岩钻石矿 blasting）+ **1.05M 木棍**（竹）+ 36864 赭石楼梯的原材料需求。门配方本身只需极少钻石/木棍；这是 `pattern/storage_ladder x146` 几何放大（4^10=1048576、3·2^15 等幂次）在叶子原材料上失控，整链 19.8B crafts 对单物品完全不可用。
- 原因: `pattern/storage_ladder` 已带标签（属 round-1/2 已处理模式），但本题属于「已带标签仍失控」——压缩阶梯把极小输入指数放大成荒谬原始需求，链整体不可用。非 free-resource / 非 fake-production（blasting 是真生产步）。
- 建议: 在 storage_ladder 收敛器加 crafts 上限/放大深度护栏；对 locked_andesite_door 类目标单独校验其钻石/木棍原始需求是否合理（疑似门配方误选或需求反推错误）。同型失控另见 0101（stack_upgrade_tier_4 5.07B）、0123（raw_zinc_block x43046721）、0002/0003（20.8B，见下条哨兵）。

### F-create-oneblock-2 | chain=0003 | severity=major | category=machine-mismatch
- 位置: CHAIN 0003 `create_power_loader:empty_brass_chunk_loader`（及 0002、0011、0074、0088、0103、0105、0109、0111、0124 等同型）；步 A1 `minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore x2147483647 @ create:encased_fan`（行 2793）、A5 同一配方 `x2147483647 @ minecraft:blast_furnace`（行 2799）、A10 `@ create:encased_fan`（2810）、A14 `@ minecraft:blast_furnace`（2816）。全批 204 处 `minecraft:*_from_blasting_* @ create:encased_fan`，覆盖 **95/141 链**。
- 问题: 配方 id 为原版 blasting 配方（系统 `minecraft:blasting`），机器却标 `create:encased_fan`。同一配方在 10 条链（0003/0004/0011/0074/0088/0103/0105/0109/0111/0124）中**同时**出现在 `@ create:encased_fan` 与 `@ minecraft:blast_furnace`，证明机器归属不一致——fan 不能执行原版矿石冶炼，属 EXECUTE @ 机器与配方系统明显不符。
- 原因: 规划器在给 blasting/smelting 类配方分配自动化机器时，把 `create:encased_fan` 当作通用处理机（疑似 Create 风扇“烟熏/处理”被误归），与正确的 `minecraft:blast_furnace` 并存。需确认是否为本包 datapack 真的把矿石冶炼注册到风扇（若是则降级为 ok）。
- 建议: 加 recipeRegex 收敛 `minecraft:[a-z_]+_from_blasting[a-z_]* @ create:encased_fan`（本批 204 处、95 链），并建议 builtin 候选 `cirrus:machine/mismatch/vanilla_blasting_on_fan`；下游自动化据此路由会错认机器。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | pattern/exchange+recycling+storage_ladder | ok | 纯石切/回收环，handled 标签覆盖；含 `|+N` 后缀噪声（已知规划器级） |
| 0002 | amount-insanity+machine-mismatch | bad | brass_chunk_loader 20.8B crafts 由 32 位溢出哨兵 2147483647 驱动（lapis x2147483647 @ encased_fan/blast_furnace）；背包物品当机器目标（已知） |
| 0003 | amount-insanity+machine-mismatch | bad | 同上，empty_brass_chunk_loader；同配方 @ encased_fan 与 @ blast_furnace 共存（错配实证） |
| 0005 | storage_ladder | ok | netherite_backtank，handled |
| 0006 | storage_ladder | ok | extendo_grip，handled |
| 0010 | storage_ladder+mega-wide | questionable | mechanical_arm 118M crafts，storage_ladder x130 放大，目标合理但体量过大 |
| 0027 | storage_ladder+mega-wide | questionable | oxidized_copper 85M crafts，storage_ladder x58 |
| 0048 | storage_ladder+mega-wide | questionable | auto_blasting_upgrade 3M crafts，背包升级目标（已知规划器级） |
| 0062 | storage_ladder | questionable | advanced_tool_swapper_upgrade 85M，背包升级目标 |
| 0073 | storage_ladder+mega-wide | questionable | sequenced_gearshift 118M，storage_ladder x126 |
| 0077 | storage_ladder+amount | bad | netherite_backpack 146M；diamond_from_blasting x2654208 @ encased_fan（错配）+ 链内同配方 @ blast_furnace；背包升级目标（已知） |
| 0079 | storage_ladder | questionable | auto_smelting_upgrade 4220，背包升级目标 |
| 0087 | storage_ladder | questionable | tool_swapper_upgrade 85M，背包升级目标 |
| 0093 | storage_ladder | questionable | advanced_compacting_upgrade 56M，背包升级目标 |
| 0096 | storage_ladder | questionable | stack_upgrade_omega_tier 6.2M，背包升级目标 |
| 0101 | amount-insanity | bad | stack_upgrade_tier_4 5.07B crafts，storage_ladder 失控（已带标签仍失控） |
| 0102/0104/0106/0107/0108/0110 | storage_ladder+recolor | questionable | items_displayed:*_jewelry_pillow 装饰目标，53–107M crafts，目标选择怪象（装饰品高权重） |
| 0123 | storage_ladder+amount | bad | kinetic_bridge 290M；raw_zinc_block x43046721（=9^8，storage_ladder 放大失控） |
| 0129 | storage_ladder+loop | bad | netherite_diving_boots 1.45B；crushing/nether_gold_ore 同配方重复 16 次（A54…A82）+ 20.8B 级石切环，链不可用 |
| 0136 | storage_ladder | questionable | mechanical_crafter，diamond_from_blasting @ encased_fan 错配 + 正常 @ blast_furnace |
| 0139 | storage_ladder | ok | fan_splashing_catalyst，handled（lapis @ blast_furnace 正常） |
| 0140 | amount-insanity | bad | locked_andesite_door 19.8B，~1.16M 钻石+1.05M 木棍原始需求（F-1） |
| 0119–0135 | pattern/exchange+storage_ladder | ok | 各色 glazed_terracotta / 铜氧化变体，handled；`|+N` 噪声（已知） |
| 其余 0114–0138、0007–0061 等 | exchange/recycling/storage_ladder/recolor | ok | 全由 round-1/2 已处理标签覆盖；存在 `|+N` id 污染、BUDGET_TRUNCATED 未解析（均属已知规划器级，不单列） |

## 规则建议（给汇总者）
- targetRegex:
  - `items_displayed:.*_jewelry_pillow` —— 排除装饰枕头目标（证据 0102/0104/0106/0107/0108/0110，53–107M crafts，非进程物品）。
  - `minecraft:.*_glazed_terracotta` —— 降权（证据 0119–0135，纯 exchange 噪声）。
  - `create_power_loader:.*|sophisticatedbackpacks:.*_upgrade|sophisticatedbackpacks:.*_backpack` —— 背包/加载器类目标按“背包升级物品被当机器”已知规划器级处理（证据 0002/0003/0048/0062/0077/0079/0087/0093/0096/0101）。
- recipeRegex:
  - `minecraft:[a-z_]+_from_blasting[a-z_]* @ create:encased_fan` —— 收敛 vanilla 冶炼配方错标风扇（证据 204 处 / 95 链；F-2）。
  - `minecraft:[a-z_]+_from_[a-z_]*_smelting[a-z_]* @ create:encased_fan` —— 同类前置（本批 0 命中，留作护栏）。
- builtin 候选:
  - `cirrus:machine/mismatch/vanilla_blasting_on_fan` —— 标记原版 blasting/smelting 配方被归属到 `create:encased_fan`；跨包可能通用（mechanical/atm 系亦常见）。
  - storage_ladder 已生效，但本批出现「已带标签仍失控」放大（0140/0101/0123/0002/0003），建议给 storage_ladder 加 crafts 上限或最大压缩层数护栏。
- 已知规划器级（链级一句话带过，不展开）：32 位溢出哨兵 `2147483647`（0002/0003/0101/0140，共 71 处）；`|+N` 物品 id 后缀污染（全批石切/铜氧化链普遍）；无净产出往返环机制本身；背包升级物品被当机器；smithing_trim 破映射（本批未明显出现）；未染色基底缺失致重染级联（铜氧化族含 waxed/weathered/oxidized 大量 recolor，但均带 tag）；原版常见物预算截断（`[UNRESOLVED] ... reason=BUDGET_TRUNCATED`，如 minecraft:stick/raw_copper，共 1395 BUDGET_TRUNCATED）。
- 无新模式时写“无”：本批无新 fake-production（全部 `jei:` 均为真实机器系统 mechanical_crafting/spout_filling/sequenced_assembly/mixing，无 `jei:/synthetic`、无奖励/村民/展示页）；无 zero-input free-resource；无 tag-expansion-overreach 实证。
