# create-ultimate batch-002 审查报告
- pack: create-ultimate
- 批次: batch-002（written=131；类别分布 incomplete 91 / long 15 / medium 13 / giant 6 / colossal 5 / short 1）
- 审查链数: 100+（完整类别 40 条全读：short1+medium13+long15+giant6+colossal5；incomplete 抽样 ≥60 条，含对 28 条 `|+N` 与 54 条 variant-cycle 的逐链 UNRESOLVED 核对；其余经 index.tsv 与代表性抽样覆盖）
- 发现: critical 0 / major 4 / minor 1

## 发现

### F-CREATE-ULTIMATE-01 | chain=多链(28) | severity=major | category=data-pollution
- 位置: 各链 `[UNRESOLVED] <id>|+N ... reason=RANK_TRUNCATED/CASCADE_EXHAUSTED`
- 问题: `|+N` 后缀变体（如 `dndecor:cut_stone|+14`、`create:polished_cut_andesite_stairs|+14`、`minecraft:black_wool|+14`、`garnished:mastic_paste_purple|+16`、`createnuclear:brown_cloth|+15`、`createnuclear:lead_block|+1`、`createframed:horizontal_black_stained_framed_glass|+4`、`create:item_vault|+1`）被当作与基础物品不同的独立未解析物。它们是同一物品的 tag/variant 判别式后缀，基础 id 在链内本已产出（例如 `dndecor:cut_stone` 在 0083 A58 已 stonecut 出 minecraft:stone）。全批 109 条 UNRESOLVED 中 31 条带 `|+N`，涉及 28 条不同链（0083/0084/0086/0087/0088/0099/0104/0107/0108/0110/0111/0113/0114/0115/0116/0121/0122/0124/0127/0128/0050 等）。
- 原因: 已知 `|+N` 物品 id 后缀污染（规划器级）本批新暴露为"伪 UNRESOLVED"——把同一物品的不同内部变体判成不可解析，导致 28 条链被标 incomplete，并污染 unresolved 列表。
- 建议: 在解析/UNRESOLVED 发射前将 `id|+N` 归一为 `id`，并对 `|+N` 变体去重；属 data-pollution 类，应在分类层修。

### F-CREATE-ULTIMATE-02 | chain=0000/0084/0086/0088/0099/0108/0110/0128/0116/0121(54 链) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0000 A1-A17+（`short_scarlet_bricks→scarlet_bricks→tiled→long→short→corner→long→...` 闭环，depth=35，raw_kinds=0）；CHAIN 0084 A18-A26（`dean_bricks→long→corner→short→dean_bricks`）；CHAIN 0086 asurine / 0088 ochrum / 0099 crimsite / 0108/0110/0128 andesite / 0116/0121 netherrack 同构家族；CHAIN 0050 A1-A14 vault/container/silo 见 F-03。
- 问题: createdeco/dndecor/garnished 的"砖/石 variant"家族构成**闭环石切**，无进入点：每个 variant 只能由另一 variant 石切而来，链内 54 条带 `createdeco|dndecor|garnished:*_bricks` 的 UNRESOLVED（candidates=4，CASCADE_EXHAUSTED）。规划器在环内放大，没能走到真正的基配方（如 0084 A18 `createdeco:dean_bricks` 实际由 `create:ochrum + minecraft:brick` 合成）。结果一个 variant 始终 UNRESOLVED，整链标 incomplete。
- 原因: 无净产出的 variant 石切环（no-op 往返），根配方排名低于环内互转，被 CASCADE 耗尽。
- 建议: 新增 `pattern/variant-cycle` 标签标记此类闭环石切；并/或把"基配方（ochrum+brick 合成 brick）"排名提到 variant 互转之上，使根可达。

### F-CREATE-ULTIMATE-03 | chain=0050 | severity=major | category=loop-or-cycle
- 位置: CHAIN 0050 A1-A14（`create:item_vault ↔ create_vibrant_vaults:vertical_item_vault ↔ ...shipping_container ↔ dndecor:container ↔ create_sa:vault_component ↔ create_connected:item_silo` 全部 1:1 互转），UNRESOLVED `create:item_vault x1 (candidates=5, CASCADE_EXHAUSTED)`。
- 问题: vault/container/silo 系列 1:1 可逆互转形成无净产出闭环，规划器在环内来回，最终 `create:item_vault` 不可解析。
- 原因: 与 F-02 同构的 1:1 可逆转换环；基配方（item_vault 的真实来源）未纳入或排名过低。
- 建议: 对 vault/container/silo 1:1 互转套 `pattern/exchange` 或 `pattern/variant-cycle`，并把 item_vault 的真实生产配方置顶。

### F-CREATE-ULTIMATE-04 | chain=0109/0046/0123/0043/0052/0083/0045/0049/0119/0126/0112(多链) | severity=major | category=amount-insanity
- 位置: 大量未打标签的 block↔item / nugget↔block / tier 往返环：
  - `raw_iron_block↔raw_iron`（0103 A5-A30、0109 A5-A20、0123 A1-A16、0083 A38-A39）
  - `raw_copper_block↔raw_copper`（0109 A24-A40）、`raw_gold_block↔raw_gold`（0083 A19-A34、0092 A11-A18）
  - `raw_zinc_block↔raw_zinc`（0052 A12-A19、0058 A1-A8、0062 A1-A8、0069 A1-A8、0109 A24-、0110）
  - `hay_block↔wheat`（0043 A7-A15、0045 A13-A20、0103）、`slime_block↔slime_ball`（0045 A23-A30）
  - `bone_block↔bone_meal`（0119 A4-A12、0126 A4-A12）、`redstone_block↔redstone`（0083 A1-A17）
  - `experience_block↔experience_nugget`（0052、0058、0062、0064、0069）、`cast_iron_ingot↔nugget↔block`（0017、0019）、`brass_ingot↔nugget↔block`（0020）、`steel 三态`（0046、0105、0125）
  - `straw_bale↔straw`（0049、0053、0112）、`potato/onion/carrot/beetroot_crate↔item`（0043、0044、0047、0051）、`ender_jelly_block↔ender_jelly`/`ethereal_compound_block↔ethereal_compound`（0049）、`numbing_parchment_block↔numbing_parchment`（0117）
- 问题: 上述 block↔item / nugget↔block 往返环本属 storage_ladder 家族，但 round-2 已固化的 `pattern/storage_ladder` 标签仅覆盖 allthecompressed/quark compressed/crate/IE 钢三态/cardboard——本批这些**原版 + 多 mod 的 tier 环未被标签**，于是全部以 `loop=allowBoundedAmplification` 放大执行，造成链体大幅膨胀（如 0103 有 20 次 raw_iron 往返、0045 有 9 次 hay 往返 + 9 次 slime 往返；多链 depth 35~80、actions 60~130）。
- 原因: storage_ladder 规则作用域过窄，未覆盖原版 `raw_*_block`、nugget↔block、hay/slime/bone/redstone block↔item、crate/bale↔item、mod block↔item（jelly/parchment/compound）等语义等价的往返环。
- 建议: 扩展 `pattern/storage_ladder`（或新增 `pattern/tier-loop`）覆盖上述族；见"规则建议"。

### F-CREATE-ULTIMATE-05 | chain=0017/0019/0020/0110/0107/0115 | severity=minor | category=unresolved-cascade
- 位置: UNRESOLVED `createbigcannons:cast_iron_ingot x9 (candidates=2, CASCADE_EXHAUSTED)`（0017、0019）、`create:brass_ingot x9`（0020）、`tfmg:aluminum_ingot x9`（0110）、`createnuclear:lead_block|+1`（0107、0115）。
- 问题: 这些金属/合金物在图内只有三态（ingot↔nugget↔block）互转，缺真正生产配方（Create 混合/鼓风冶炼 brass、cast_iron 鼓风、aluminum 冶炼等），故 candidates=2 皆为三态环，CASCADE 耗尽后判 UNRESOLVED。
- 原因: 疑似导出配方集缺失真实生产配方（或根配方排名低于三态环）。需核对导出集是否含 brass/cast_iron/aluminum 的冶炼/混合配方。
- 建议: 核实 `brass_ingot`/`cast_iron_ingot`/`aluminum_ingot` 的生产配方是否在 10647 条导出配方内；若在，应排名于三态环之上；若缺，属导出缺口（suspected，待验证）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | bad | scarlet 砖 variant 石切闭环无根→UNRESOLVED(F-02) |
| 0001-0076(+raw) | incomplete | bad | createdeco 砖/墙/台阶族，variant-cycle 闭环(F-02) |
| 0017 | incomplete | bad | cast_iron 三态环+cast_iron_ingot UNRESOLVED(F-04/F-05) |
| 0019 | incomplete | bad | 同 0017 |
| 0020 | incomplete | bad | brass 三态环+brass_ingot UNRESOLVED(F-04/F-05) |
| 0043 | long | questionable | hay/onion/potato crate 往返环未标签(F-04) |
| 0044 | long | questionable | potato/carrot crate 往返环(F-04) |
| 0045 | colossal | questionable | hay↔wheat+slime↔ball 9×往返环(F-04) |
| 0046 | giant | questionable | steel 跨 mod 三态放大网(F-04) |
| 0047 | long | questionable | potato/onion crate 往返环(F-04) |
| 0048 | incomplete | bad | 40×pattern/exchange + raw_copper 往返环(F-04) |
| 0049 | long | questionable | ender_jelly/ethereal 双 block↔item 环(F-04) |
| 0050 | incomplete | bad | vault/container/silo 1:1 互转闭环(F-03) |
| 0051 | long | questionable | beetroot crate + 多 exchange 环(F-04) |
| 0052 | long | questionable | experience/raw_zinc 三态环(F-04) |
| 0053 | medium | questionable | straw_bale↔straw 环(F-04) |
| 0054-0068(cabinet) | medium | ok | 木系台阶/活板门直链，仅 `|+3` 后缀污染(F-01 轻微) |
| 0112 | medium | questionable | straw_bale↔straw 环(F-04) |
| 0058/0062/0064/0069 | long | questionable | experience/raw_zinc 三态环(F-04) |
| 0083 | incomplete | bad | redstone/gold/iron 三态环 + `cut_stone|+14` 伪UNRESOLVED(F-04/F-01) |
| 0084/0086/0088/0099 | incomplete | bad | asurine/ochrum/crimsite/dean 砖石 variant 闭环(F-02) |
| 0092 | long | questionable | raw_gold 三态环(F-04) |
| 0103 | colossal | questionable | 20×raw_iron 环 + framed_glass exchange 环（已标） |
| 0104 | incomplete | bad | cut_stone|+14 伪UNRESOLVED(F-01) |
| 0107/0115 | incomplete | bad | lead_block|+1 + cloth|+15 伪UNRESOLVED(F-01/F-05) |
| 0108/0110/0128 | incomplete | bad | andesite variant 闭环 + polished_cut_andesite_stairs|+14 伪UNRESOLVED(F-02/F-01) |
| 0110 | incomplete | bad | aluminum_ingot UNRESOLVED + |+14(F-05/F-01) |
| 0111/0113/0124 | incomplete | questionable | BUDGET_TRUNCATED: bone_block/blue_dye/cardboard_block/sail_frame/raw_iron 本可解析(F-05类, 预算截断) |
| 0114/0127 | incomplete | bad | black_wool|+14 伪UNRESOLVED(F-01) |
| 0116/0121 | incomplete | bad | netherrack variant 闭环 + cut_netherrack_bricks|+14 伪UNRESOLVED(F-02/F-01) |
| 0117 | long | questionable | numbing_parchment block↔item 环(F-04) |
| 0118 | colossal | questionable | straw_bale/raw_zinc 双三态环(F-04) |
| 0119 | giant | ok* | cardboard 重染链，已标 pattern/recolor；bone↔bone_meal 环未标签(F-04) |
| 0122 | incomplete | bad | mastic_paste_purple|+16 伪UNRESOLVED(F-01) |
| 0123 | colossal | questionable | raw_iron 16×往返环(F-04) |
| 0125 | giant | questionable | steel 跨 mod 三态放大网(F-04) |
| 0126/0129 | giant | ok* | glazed_terracotta/candle 重染链，已标 pattern/recolor；bone↔bone_meal 环未标签(F-04) |
| 0130 | long | questionable | end_crystal 直链，正常 |

## 规则建议（给汇总者）
- targetRegex: 无新增目标排除；建议把 `createdeco:*_brick_*` 与 `dndecor:cut_*|+14` 等已判 incomplete 的族纳入收敛过滤（非排除）。
- recipeRegex:
  - `.*(_block|_ingot|_nugget|_bale|_crate|_wool|_jelly|_parchment|_compound).*(from_)?(block|decompacting|compacting|nugget|from_crate|from_block)$` → 标记 `pattern/storage_ladder`（扩展版）。
  - `createdeco:[a-z]+_(bricks|slab|stairs|wall)_from_[a-z_]+_(bricks|slab|stairs|wall)_stonecutting` 及 `dndecor:cut_.*_stonecutting`（双向存在时）→ `pattern/variant-cycle`。
  - `.*(item_vault|shipping_container|container|vault_component|item_silo).*` 1:1 互转 → `pattern/variant-cycle`/`pattern/exchange`。
- builtin 候选:
  - 扩展 `pattern/storage_ladder`（或新 `pattern/tier-loop`）：覆盖原版 `raw_*_block↔raw_*`、experience/cast_iron/brass/steel 三态、`hay_block↔wheat`、`slime_block↔slime_ball`、`bone_block↔bone_meal`、`redstone_block↔redstone`、`*_bale↔*`、crate↔item、mod block↔item（jelly/parchment/compound）。证据 F-04 各链。
  - 新增 `pattern/variant-cycle`：createdeco/dndecor/garnished 砖石 variant 石切闭环（54 链）+ vault/container/silo 1:1 互转（0050）。证据 F-02、F-03。
  - `data-pollution`：归一 `id|+N` 后缀，消除 28 条伪 UNRESOLVED。证据 F-01。
- 规划器级（已知，仅链级提及不展开）：32 位计数溢出哨兵、无净产出往返环机制本身；`|+N` 后缀污染见 F-01 已作为本批新暴露点上报。
