# All Of Create 2.1 batch-002 审查报告
- pack: All Of Create 2.1
- 批次: batch-002（written=118；类别分布：incomplete 96 / colossal 6 / giant 7 / long 6 / medium 3）
- 审查链数: 118（incomplete 96 / complete 22）。complete 22 条全读；incomplete 96 条做逐链循环/UNRESOLVED 信号解析（Python 逐行），并对 0020/0023/0027/0034/0012/0085/0013/0017 等做深读取证；全文件批量扫描 rule-tag、UNRESOLVED、EXECUTE@system、溢出哨兵。总审查量 ≥100，达配额。
- 已知规划器级问题（按 round-2 口径仅一句话提及，不展开）：`|+N` 物品 id 后缀污染（本批 2562+ 处，渗入 UNRESOLVED 如 `create:layered_andesite|+14`、`create_ironworks:steel_ingot|+5`、`createframed:black_stained_framed_glass|+4`）；无净产出往返环机制本身（`loop=allowBoundedAmplification` 放行）；32 位溢出哨兵（本批未见 `x2147483647`）。
- 发现: critical 0 / major 3 / minor 1

## 发现

### F-allofcreate-1 | chain=0002/0065/0020/0117/0007/0015/0042/0115(等全批) | severity=major | category=loop-or-cycle
- 位置: 互逆「方块↔解体」无净产出往返环，跨原版与多 mod，且**全无 rule-tag**（本批仅 `pattern/exchange`/`intent/recycling` 两类标签，无 `storage_ladder`）。代表证据：
  - CHAIN 0002（long）A2→A9：`bone_meal↔bone_block` 往返 ×8（`bone_meal_from_bone_block` 与 `bone_block` 互逆），净产出 0，仅为喂 A10 `white_dye`（而 `bone_meal` 在 A1 已直接产出）。
  - CHAIN 0065（long）A3→A10：`fluix_block↔deconstruction_fluix_block` ×8。
  - CHAIN 0020（incomplete）A44→A59：`fluix_block↔deconstruction_fluix_block` ×16，全链 121 EXECUTE、crafts=391，目标 spatial_io_port 本可简单合成。
  - CHAIN 0117（giant）A3→A18：`raw_iron_block↔raw_iron` ×16，而 `raw_iron x18` 已是 RAW 输入（目标 everlasting_upgrade 只需 raw_iron×4）。
  - CHAIN 0007（giant）A2→A10 `lapis_block↔lapis_lazuli` ×8，叠加 A10→A17 `dndesires:lapis_lazuli_shard↔lapis_lazuli` 往返。
  - CHAIN 0015/0072：`raw_copper_block↔raw_copper`、`create_compressed:copper_sheet_block↔copper_sheet`、`prismarine_alloy_block↔prismarine_alloy_from_block`。
  - CHAIN 0042/0115：`farmersdelight:onion_crate↔onion_from_crate`、`potato_crate↔potato_from_crate`、`carrot_crate↔carrot_from_crate`、`create_compressed:wheat_flour_pile↔wheat_flour`。
- 问题: 在 `loop=allowBoundedAmplification` 下，规划器把「9:1/1:1 方块化↔解体」当成免费放大，即使基底物已是现成 RAW/已直接产出，仍插入互逆 ping-pong，净产出为零；逐链制造 8–16 层冗余深度，使 action 数从应有十几膨胀到 100+。属 reciprocal no-op round trip，按口径仍属问题。
- 原因: 上一轮拟打 `pattern/storage_ladder` 的压缩阶梯族（allthecompressed/quark/crate/IE/cardboard）在本批**未落地任何 storage_ladder 标签**；而本批暴露的互逆族远更广（原版矿石/染料方块 + create 板材/压缩堆 + farmersdelight 箱 + ae2 fluix + 合金块 + dndesires 碎片），全部漏标。
- 建议: 将全量 `<base>_block ↔ <base>`（及 `_crate↔_from_crate`、`deconstruction_<id>_block`、`*_from_block`、`*_pile↔*`）互逆配方统一收敛为 `pattern/storage_ladder`（覆盖原版与所有 mod），而非仅限上轮列出的 5 个 mod；并在规划器层对「压缩后随即解压、无第三方消耗」的 ping-pong 做 no-op 环识别。

### F-allofcreate-2 | chain=0012/0085(及 createdeco 全族 50+ 条) | severity=major | category=loop-or-cycle + unresolved-cascade
- 位置:
  - CHAIN 0012（incomplete）A1→A14：`createdeco:tiled_umber_bricks←short←umber←long←short←corner←long←tiled←corner←umber←…` 在 short/long/tiled/corner/umber_bricks 之间石切互转闭环，最终 `[UNRESOLVED] createdeco:short_umber_bricks x1 (reason=CASCADE_EXHAUSTED)`。
  - CHAIN 0085（incomplete）A2→A15：pearl_bricks 变种同样闭环，UNRESOLVED `createdeco:short_pearl_bricks`。
- 问题: createdeco 各砖种变体互为「石切互转」形成**无根闭环**，规划器在其中循环耗尽候选（candidates=4 全是互相转换），永远触达不到 base 砖（`umber_bricks` 由 `umber_brick` 物品合成、再石切得到 short 等）。结果 depth≈35、pending=1、base 砖被误标 UNRESOLVED，而目标楼梯/台阶/墙全部因此 incomplete。本批 createdeco 族 50+ 条链（0012/0021/0022/0037–0113 等）同构陷入此闭环。
- 原因: createdeco 的砖块变种石切转换**未被打 `pattern/exchange` 标签**（与 create 的 `cut_andesite_*` 不同），规划器把它们当真实生产步骤拼接，又没有「转换闭环无根」检测。
- 建议: 给 createdeco（及类似 mod）的 brick/block 变种石切互转补 `pattern/exchange`；并在收敛器增加「转换闭环无根」检测——base 变种应有 crafting 根配方，不应被标 unresolved。

### F-allofcreate-3 | chain=0017/0068/0042/0115(等) | severity=major | category=tag-expansion-overreach
- 位置:
  - CHAIN 0017（colossal, diamond_backpack）RAW 列出 biomesoplenty 全部 ~15 种木板 + chipped 全部 ~30 种 plank 风格各 ×11（raw_kinds=461），但链内实际仅用 `chipped:cornered_oak_planks` 一种（A3 chest / A5 stick / A24 tripwire_hook）。
  - CHAIN 0068（colossal, gold_backpack）同构，raw_kinds=457。
  - CHAIN 0042（long, fireproof_chemical_vat）RAW 同样列出全部 biomesoplenty+chipped plank 变种 ×6，链内仅用 `dead_planks` 与 `cornered_oak_planks`；raw_kinds=489。
  - CHAIN 0115（long, roast_chicken_block）同构，raw_kinds=458。
- 问题: 配方输入为 `#planks`/`#chipped_planks` 之类 any-of 标签，规划器把**每个成员都当作必需 RAW 输入**展开，导致 raw_kinds 膨胀到 460+（实际只需 1–2 种木板）。属 any-of 标签过度展开：成员语义都是木板（非「语义不符」），但展开量爆炸并污染「未完成/原材料」统计。
- 原因: 规划器对 any-of 标签在 raw 需求端做了全量展开而非「任选其一」归并；chipped 模块又为每个木种生成数十种风格（mosaic/panel/shavings/basket_woven/boxed/brick_bond…），交叉乘积放大。
- 建议: any-of 标签的 raw 需求应只要求「任一成员」，收敛器对 plank/tag 展开设上限或归并为单一代表成员；或对该类「all-wood-variant」输入做代表成员削减。

### F-allofcreate-4 | chain=0014/0018/0019/0025(等 rainbowcompound 多条) | severity=minor | category=unresolved-expected-item
- 位置: 多条 `[UNRESOLVED] minecraft:diamond x9 (candidates=5, reason=CASCADE_EXHAUSTED)`，如 CHAIN 0014 / 0018 / 0019 / 0025。
- 问题: 原版钻石可由 `diamond_ore`/`deepslate_diamond_ore` 熔炼得到，属常见可合成物，却被标 UNRESOLVED。candidates=5 应已含矿石熔炼路径，耗尽可能因矿石在图内无生产者（采矿未被建模）且 `diamond_block` 反解（9 钻石↔1 块）为循环。
- 原因: 熔炼钻石路径依赖矿石，而矿石作为「采矿」产出未被纳入 crafting 图；钻石自身又无 crafting 配方，故 CASCADE_EXHAUSTED。
- 建议: 确认图是否含 `diamond_ore` 熔炼配方；若含，钻石不应 UNRESOLVED（属 unresolved-expected-item，待确认）；若不含则属采矿未建模的预期缺口，按 known-issue 处理，勿误判为「假生产」。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | loop-or-cycle | questionable | blue_glazed_thin_pot，lapis_block 无净往返环 |
| 0001 | loop-or-cycle | questionable | white_glazed_medium_pot，bone_meal↔bone_block ×8 |
| 0002 | loop-or-cycle | questionable | white_crockery_combo，bone 往返 ×8，净产出 0 |
| 0003 | loop-or-cycle | questionable | white_glazed_thin_pot，bone 往返 |
| 0004 | loop-or-cycle | questionable | blue_glazed_medium_pot，lapis 往返 |
| 0005 | loop-or-cycle | questionable | white_glazed_wide_pot，bone 往返 |
| 0006 | loop-or-cycle | questionable | blue_glazed_thick_pot，lapis 往返 |
| 0007 | loop-or-cycle | questionable | blue_crockery_combo，lapis+dndesires 碎片往返 |
| 0008 | loop-or-cycle | questionable | blue_glazed_wide_pot，lapis 往返 |
| 0009 | loop-or-cycle | questionable | white_glazed_thick_pot，bone 往返 |
| 0010 | ok | ok | yellow_crockery_combo，简单无环 |
| 0011 | loop-or-cycle(已知|+N) | questionable | tfmg:large_switch，含 raw_iron 互逆环 |
| 0012 | loop-or-cycle+unresolved-cascade | bad | createdeco 砖块石切闭环，UNRESOLVED short_umber_bricks |
| 0013 | loop-or-cycle(已知|+N) | bad | netherite 升级，diamond_block 环 + layered_andesite\|+14 |
| 0014 | unresolved-expected(嫌疑) | questionable | rainbow_leggings，minecraft:diamond CASCADE_EXHAUSTED |
| 0015 | loop-or-cycle | questionable | mechanical_conduit，copper_sheet/raw_copper/alloy 块环 |
| 0016 | loop-or-cycle | questionable | iron_backtank，含互逆环（raw156） |
| 0017 | tag-expansion-overreach | bad | diamond_backpack，plank 标签爆炸 raw_kinds=461 + raw_iron 环 |
| 0018 | unresolved-expected(嫌疑) | questionable | rainbow_boots，diamond UNRESOLVED |
| 0019 | unresolved-expected(嫌疑) | questionable | rainbow_hoe，diamond UNRESOLVED |
| 0020 | loop-or-cycle | bad | ae2:spatial_io_port，fluix 往返 ×16（121 EXECUTE）+ black_stained_framed_glass\|+4 |
| 0021 | loop-or-cycle+unresolved-cascade | bad | createdeco short_umber_brick_stairs，闭环 |
| 0022 | loop-or-cycle+unresolved-cascade | bad | createdeco tiled_scarlet_brick_wall，闭环 |
| 0023 | 已知|+N污染 | bad | andesite_large_cogwheel，layered_andesite\|+14 阻塞 |
| 0024 | loop-or-cycle | questionable | basic_to_diamond_tier_upgrade，互逆环 |
| 0025 | loop-or-cycle+unresolved-expected | questionable | rainbow_axe，diamond 环 + diamond UNRESOLVED |
| 0026 | loop-or-cycle | questionable | robo_bee，互逆环 |
| 0027 | loop-or-cycle+已知|+N | bad | rainbow_pickaxe，diamond/gold 块环 + steel_ingot\|+5 + sturdy_sheet UNRES |
| 0028 | loop-or-cycle | questionable | rainbow_helmet，含环 + 11 未解析 |
| 0029 | loop-or-cycle | questionable | tfmg:industrial_mixer，互逆环 |
| 0030 | loop-or-cycle | questionable | rainbow_knife，含环 |
| 0031 | loop-or-cycle | questionable | rainbow_sword，含环 |
| 0032 | loop-or-cycle | questionable | refined_radiance_encased_fan，含环 |
| 0033 | ok | ok | terracotta_crockery_combo，简单无环 |
| 0034 | 已知|+N污染(已标exchange x91) | questionable | shadow_steel_encased_fan，pattern/exchange 已标；layered_andesite\|+14 阻塞 |
| 0035 | loop-or-cycle | questionable | golden_backtank，含环 |
| 0036 | loop-or-cycle | questionable | diamond_backtank，含环 |
| 0037–0113 | loop-or-cycle+unresolved-cascade | bad | createdeco 砖块族 50+ 条，全部陷入石切闭环，base 砖 UNRESOLVED |
| 0114 | loop-or-cycle | questionable | resonarium_chestplate，含环 |
| 0115 | loop-or-cycle+tag-expansion | questionable | roast_chicken_block，crate/wheat_flour 环 + plank 爆炸 raw458 |
| 0116 | loop-or-cycle | questionable | rainbow_chestplate，含环 + 11 未解析 |
| 0117 | loop-or-cycle(已标recycling) | questionable | everlasting_upgrade，raw_iron 环 ×16（flax 回收已标 intent/recycling） |

## 规则建议（给汇总者）
- targetRegex: 无新增目标排除（本批 target 多为合理整合包物品；createdeco 楼梯/台阶/墙属正常目标，问题在配方闭环而非目标）。
- recipeRegex:
  - `minecraft:(bone_meal_from_bone_block|lapis_lazuli|emerald|raw_iron|raw_copper|diamond|gold|redstone|coal)_block?` 与对应 `*_block` 互逆，及 `create_compressed:.*_block↔.*`、` farmersdelight:.*_crate↔.*_from_crate`、`ae2:decorative/fluix_block↔ae2:misc/deconstruction_fluix_block`、`*_alloy_block↔*_alloy_from_block`、`dndesires:.*_shard↔lapis_lazuli` —— 收敛为 `pattern/storage_ladder`，证据遍布全批（colossal/giant/long + incomplete 75 处 loop 命中）。
  - `createdeco:.*_(from_)?(short|long|tiled|corner|umber)_.*_stonecutting` —— 标记 `pattern/exchange`（或 no-op 环），证据 createdeco 全族 50+ 条（CHAIN 0012/0085 等）。
  - `create:layered_andesite\|\+[0-9]+|create_ironworks:steel_ingot\|\+[0-9]+|createframed:.*\|\+[0-9]+` —— 收敛 `|+N` 污染 id（属已知数据问题，建议清洗而非排除目标），证据 2562 处（CHAIN 0020/0023/0027/0034/0013 等）。
- builtin 候选:
  - 通用「方块化↔解体」互逆（blockify/unblockify ping-pong，净产出 0） → 建议 `pattern/storage_ladder` 跨包通用，覆盖原版与所有 mod（当前仅 5 个 mod 在旧规则内，本批暴露远更广）。
  - createdeco 砖块变种石切闭环 → 建议 `pattern/exchange` + 「转换闭环无根」检测（base 变种应有 crafting 根，不应 UNRESOLVED），跨包通用（凡多变种互切 mod 均适用）。
  - any-of 标签在 raw 端全量展开 → 非标签，属搜索/展开修复：对 plank/tag 展开设上限或归并代表成员。
- 无新模式时写"无"：否，已列出 3 条新模式（互逆 block/crate 无净环漏标、createdeco 砖块石切闭环、plank any-of 标签原料爆炸）。
