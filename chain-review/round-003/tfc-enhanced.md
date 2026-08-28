# tfc-enhanced batch-003 审查报告
- pack: tfc-enhanced
- 批次: batch-003（written=137；类别分布 giant 95 / short 17 / long 11 / incomplete 10 / medium 3 / colossal 1）
- 审查链数: 137（incomplete 10 / complete 127；全读 medium 3 + short 17 + long 11 + incomplete 10 + colossal 1 = 42 条；giant 抽 12 条完整读 + 其余按家族归类；全部 137 条经 index 级过筛）
- 发现: critical 0 / major 2 / minor 0

> 注：round-1/2 规则（storage_ladder / exchange / recolor / recycling）本批已生效，带 `[rule-tag: ...]` 的步骤（redstone/lapis/bone/raw_iron/raw_copper/slime/glue/straw-hay 等块↔锭↔锭块环、recolor 染料、quark 式石切环）均视为已处理，不再作为发现。已知规划器级问题（`|+N` 物品 id 后缀污染、原版常见物预算截断、32 位溢出哨兵、无净产出往返环机制本身）仅在链级判定一句话提及，不展开。

## 发现

### F-tfc-enhanced-001 | chain=<CHAIN 0004,0016,0048,0094,0098,0104,0105,0111,0135,0078,0095 …> | severity=major | category=amount-insanity
- 位置: 各链前缀的 `[A*] EXECUTE tfc:crafting/stick_bundle` / `tfc:crafting/stick_from_bundle` / `tfc:crafting/stick_bunch` / `tfc:crafting/stick_from_bunch`（及其产生的 `minecraft:stick` 输出）
- 问题: 规划器在绝大多数木制/家具/招牌/压桶/红石/物品展示框链的开头插入一段 `tfc:wood/twig/kapok|+20 → stick_bunch → stick → stick_bundle → stick` 梯，但其终端产物 `minecraft:stick` 在本批次**任何配方**的 `in:` 中都不出现（已全批次 grep 验证：`in : ...stick...` 仅命中 `stick_bunch`/`stick_bundle`/`sticky_*`，从不命中 `minecraft:stick`）。证据：CHAIN 0016 的 A3–A12 产出 stick x18+9×5 后，下游 A13 凿子只吃 `twig`、A14/A17/A18 吃 `chisel`/`cut_sandstone`，stick 无人消费；CHAIN 0004 的 A10–A13 与 A17/A21 stick 同样无下游消费者（锯/凿用 `twig` 当柄，架子用 `lumber`+`planks`）；CHAIN 0104 A5–A8、0105 A5–A8、0135 A6–A9、0094 A11–A18、0098 A7–A9 同理。
- 原因: 规划器搜索 cone 把 `twig→stick` 子树作为“默认前置”挂到需要木柄/木板的工具与家具配方上，形成幽灵依赖（`<= from` 指向无 stick 输入的 action）。该死分支把每条链的 crafts 与 raw_kinds 显著放大——典型 raw_kinds=30~84，且 RAW 列表因 `stick_bunch` 接受任意树枝 tag 而枚举全部 ~30 种 `tfc:wood/twig/*` 变体（每条 x10~x48）。与已知的 `|+N` 后缀污染同源（梯的输入正是 `tfc:wood/twig/kapok|+20` 等污染 id，可能加剧规划器误判）。
- 建议: 新增 `pattern/stick_ladder`（junk/duplicate）排除 `tfc:crafting/stick_bundle|stick_from_bundle|stick_bunch|stick_from_bunch` 四配方，并在收敛时剔除“终端为 `minecraft:stick` 且无下游消费”的死分支；同时归一化 `|+N` 后缀（见规则建议 3）。

### F-tfc-enhanced-002 | chain=<CHAIN 0000,0001,0002,0003> | severity=major | category=loop-or-cycle
- 位置: `[A1] EXECUTE tfc:crafting/rock/mossy_cobble/<rock>_wall` 及其下游 `tfc:crafting/rock/mossy_loose/<rock>_from_walls` → `tfc:crafting/rock/mossy_cobble/<rock>`
- 问题: mossy_cobble 家族（basalt/schist 的 stairs/slab/wall）走了 `cobble → wall → loose → cobble → 目标` 的环。证据 CHAIN 0000：RAW 已是 `tfc:rock/mossy_cobble/basalt x12`，目标配方 A4 `mossy_cobble/basalt_stairs` 直接吃 `mossy_cobble/basalt x6`；但 A1 先把 raw cobble x6 做成 wall，A2 墙→loose x24，A3 loose→cobble x6，再 A4 做楼梯。即 `cobble(6)→wall(6)→loose(24)→cobble(6)` 是**零净产出的往返环**，raw 本可直接喂 A4。crafts 从可直接的 1 放大到 14。CHAIN 0003（schist_wall）更绕：先 A1 做 stairs，再 A2 stairs→loose、A3 loose→cobble、A4 cobble→wall。
- 原因: 规划器把 `mossy_cobble→wall`（正向合成）与 `wall→loose→cobble`（分解回收）当成可组合路径，在输入已是 cobble 时仍插入整段回收环，形成无净产出的互逆往返（违反 `loop=allowBoundedAmplification` 中“无净产出往返环仍是问题”的纪律）。该模式未带任何 `rule-tag`，属未覆盖配方族。
- 建议: 新增 `pattern/rock_reclaim`（或并入 exchange）标记 `tfc:crafting/rock/mossy_cobble/*_wall` + `tfc:crafting/rock/mossy_loose/*_from_walls` + `tfc:crafting/rock/mossy_cobble/*` 组合，收敛“cobble→wall→loose→cobble”零净环。

## 链级判定
| chain / family | category | verdict | 一句话 |
|---|---|---|---|
| 0000,0001,0002,0003 mossy_cobble stairs/slab/wall | loop-or-cycle | bad | F-002：cobble→wall→loose→cobble 零净环 |
| 0004,0007,0008,0009 … wood shelf/sluice/chest_minecart/hanging_sign/log_fence/fence_gate/gear_box/sign/loom/door/barrel/pressure_plate/trapdoor/tool_rack（~40 条） | amount-insanity | bad | F-001：前缀 stick 梯为死分支，stick 无人消费 |
| 0016–0061 cut_sandstone 各色 stairs/slab/wall（21 条） | amount-insanity + loop-or-cycle | bad | F-001 死 stick 梯；目标配方本不需 stick |
| 0095 sticky_piston（colossal） | amount-insanity | questionable | 红石 `block↔ingot` 17 层净零环（已带 storage_ladder 标签，crafts=82 尚可，见规则建议 4） |
| 0094 activator_rail / 0098 glow_item_frame / 0104 cartography_table / 0105 bottle_label / 0135 sugared_beehive_frame / 0078 birch_chest_boat | amount-insanity | bad | F-001：前缀死 stick 梯 |
| 0111–0130 firmalife barrel_press/*（20 条） | amount-insanity | bad | F-001：前缀死 stick 梯（另含 slime/glue storage_ladder 净零环，已标签） |
| 0089,0100,0102,0103,0107,0108,0101,0106,0109,0110 concrete_powder 各色（11 条） | (tagged) | ok | storage_ladder(lapis/bone)+recolor 已标签；RAW 列出全部 32 种 sand/gravel 变体为 tag 展开（合法，非 overreach） |
| 0096 iron_nugget / 0097 gold_nugget / 0124 waxed_copper_door / 0127 waxed_copper_trapdoor / 0083 cut_copper_stairs / 0087 chiseled_copper | (tagged) | ok | raw_*/copper block↔ingot storage_ladder 已标签；无新异常 |
| 0112 blast_furnace / 0125 comparator / 0133 repeater / 0094(红石部分) | (tagged) | ok | raw_iron/redstone block↔ingot storage_ladder 已标签（净零但已处理） |
| 0065–0075 drying_bricks/*（11 条） | (partially tagged) | questionable | straw↔hay↔thatch 纤维梯仅部分带 storage_ladder；A10–A15 thatch↔straw 未标签，但 feeding 目标，见规则建议 4 |
| 0056 smooth_mud_bricks / 0079–0093 原版石切 stairs/slab/pillar 等（short 类） | — | ok | 直链，无异常 |
| 0099,0120,0122,0123,0128,0129,0131,0132,0134,0136 beds/name_tag（incomplete 10 条） | unresolved-expected(budget) | ok* | 全部 `[UNRESOLVED]` 均为 `reason=BUDGET_TRUNCATED`（stick_bunch/saw_steel/twig_kapok/beehive_frame/knife_steel/spindle 均为包内可产真物品），属预算截断豁免，非配方质量问题 |

## 规则建议（给汇总者）
- targetRegex: 无（未发现异常目标选择）。
- recipeRegex:
  - `tfc:crafting/stick_bundle|tfc:crafting/stick_from_bundle|tfc:crafting/stick_bunch|tfc:crafting/stick_from_bunch` —— 收敛为 `pattern/stick_ladder`（junk/duplicate），证据 ~20 链且 `minecraft:stick` 全批次无下游消费（F-001）。
  - `tfc:crafting/rock/mossy_cobble/([a-z_]+)_wall|tfc:crafting/rock/mossy_loose/\1_from_walls|tfc:crafting/rock/mossy_cobble/\1` —— 收敛为 `pattern/rock_reclaim` 环，证据 chains 0000–0003（F-002）。
- builtin 候选:
  - `|+N` 物品 id 后缀归一化（data-pollution）：全批次出现 `tfc:wood/twig/kapok|+20`、`tfc:metal/saw/steel|+8`、`tfc:wood/planks/hickory|+30`、`tfc:wood/log/acacia|+3`、`tfc:glue|+1`、`tfc:rock/gravel/chalk|+21`、`minecraft:red_sand|+8`、`tfc:metal/ingot/copper|+1`、`tfc:stone/hammer/metamorphic|+12`、`tfc:wood/lumber/willow|+19` 等；建议导出层 strip 尾部 `|\+[0-9]+`。该污染与 F-001 同源（stick 梯输入正是污染 twig id），归一后可能直接消解 F-001 的幽灵依赖。
  - 扩展 `pattern/storage_ladder` 覆盖 TFC 纤维梯 `tfc:crafting/straw_from_hay`、`tfc:crafting/thatch`、`minecraft:hay_block↔tfc:straw`，并**额外折叠“净零 block↔ingot 重复”**（如 0095 红石 17 层、0112 raw_iron 16 层）：这些已带标签但仍为纯 no-op 往返，应在 storage_ladder 规则内判定“净零即坍缩”。
- 无新模式时写“无”：不适用——已提出 stick_ladder / rock_reclaim 两条新规则。
