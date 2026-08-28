# technical-electrical batch-001 审查报告
- pack: technical-electrical
- 批次: batch-001（written=150；incomplete 101 / colossal 44 / mega-wide 5）
- 审查链数: 150（incomplete 101 / complete 49；全量程序化扫描 150 条 + 重点深读代表链 0000/0008-0014/0010/0055/0070 等）
- 发现: critical 0 / major 3 / minor 2

> 读取与判定口径严格遵循 BATCH-REVIEW-BRIEF。
> 已内置规则（exchange/recolor/recycling，如 `supplementaries:flax_block_uncrafting [rule-tag: intent/recycling]`）不重复报告。
> 本批次未发现 `jei:` 配方（0 条）、未发现 trade/quest/reward 展示页，故 fake-production / free-resource 不适用。
> `+raw N` 叶子视为正常原材料，不单独报告。

## 发现

### F-TE-1 | chain=0010（系统性，覆盖 148/150 链） | severity=major | category=loop-or-cycle
- 位置: CHAIN 0010 A2→A18（`minecraft:redstone` ↔ `minecraft:redstone_block` 互逆往返）；CHAIN 0055 A7→A11（`iron_ingot`→`iron_block`→`iron_ingot`→`iron_nugget`→`iron_ingot`）、A57→A61（gold 同类）、A63→A65（redstone↔block）；CHAIN 0000 A5→A10 同类。
- 问题: 以 CHAIN 0010 为例，A2 产 `redstone x9` 后，A3→A18 连续 16 次 `redstone_block ↔ redstone` 互逆转换，净产出恒为 `redstone x9`（零净产出），纯 no-op 往返环。CHAIN 0055 的 iron/gold/redstone 形态互转同样净量不变。全批次 **148/150 链含此类可逆转换**（incomplete 99/101、colossal 44/44、mega-wide 5/5），合计 **2966 次执行**，其中 `minecraft:redstone`（redstone_block 拆块）在 colossal 单文件出现 218 次。
- 原因: `loop=allowBoundedAmplification` 将"有净产出的放大"与"零净产出的互逆往返"一视同仁放行；cone 搜索在约束求解时把物品在 block/ingot/nugget 形态间反复装箱，形成无净产出往返环（符合 brief：compress↔decompress、A↔B 互逆 no-op 重复多次仍是问题）。
- 建议: 在 planner 收敛/剪枝层将"可逆 block↔item、ingot↔nugget、redstone↔redstone_block"识别为 no-op 往返并折叠；该族配方未带任何 builtin rule-tag，应作为新 loop 规则而非交给 recycling 处理。

### F-TE-2 | chain=0003（同类 0000/0001/0002/0004/0005/0007/0018） | severity=major | category=amount-insanity
- 位置: status `crafts=` —— 0003=1039179、0002=635903、0001=377800、0000=377076、0005=366333、0004=159001、0007=98326、0018=53249（均 ≥50000）。
- 问题: mega-wide 感应单元/provider 链 crafts 达 10^5–10^6，且每链含 60–101 次可逆转换；cone 被 no-op 往返 + bounded amplification 撑爆，深度 53–73、actions 412–808、raw_kinds 432–463。数量级失控且无净收益。
- 原因: 同 F-TE-1 的 no-op 往返叠加放大循环，使 cone 内 craft 计数指数式膨胀。
- 建议: 先消掉 F-1 往返环，再对 `mekanism_extras:*` / `mekanism:*` 感应类目标施加 cone/modCap 上限或收敛 filter（见规则建议 targetRegex）。

### F-TE-3 | chain=0055（同类 33 条 incomplete 链） | severity=major | category=silly-detour
- 位置: CHAIN 0055 A16→A18→A19→A20（及 0000 A14→A17→A18→A19 同类）。
- 问题: A16 已由 `dust_osmium` 经 `from_dust_blasting` **直接产出 `ingot_osmium x9`**；随后 A18 把 9 个 `ingot_osmium` 作成 `osmium_shovel x9`（`<= from A16`），A19 熔炼回收 `nugget_osmium x9`（配方输入标注 `osmium_helmet`，但上游边来自 A18 的 shovel，属输入 id 与边来源不一致），A20 由 9 nugget → **仅 `ingot_osmium x1`**。即 9 个锇锭 → 工具 → 熔炼 → 仅回收 1 个锇锭（损 8/9）。同链 A7→A11 亦把 `iron_ingot` 在 block/nugget 间空转（净 18 不变）。全 incomplete 中 `nugget_from_smelting` 执行 29 次，`osmium_shovel` 出现 29 次但**从未作为任何 `in :` 输入**——证实该"工具→熔炼回收"为损材绕路而非必要步骤。
- 原因: planner 以"装备→熔炼回收 nugget"作为基础金属来源，但回收比远低于装备本身含金属量；同时存在直接 `dust→ingot` 路径却仍走工具熔炼，构成明显更差路径。
- 建议: 将 `mekanismtools:*/nugget_from_smelting` 等"装备→nugget"回收标为损材回收，仅当无直接 ingot 来源时允许；对 osmium/ingot 目标优先 `processing/.../from_dust_blasting`。另建议导出层校验配方输入 id 与边来源一致性（A19 的 `osmium_helmet` vs A18 的 `osmium_shovel`）。

### F-TE-4 | chain=0010 | severity=minor | category=worse-alternative
- 位置: CHAIN 0010 A20→A24 与 A24→A27。
- 问题: A20→A22→A23 `deconstruction_chiseled_certus_quartz` 产 `certus_quartz_crystal x4`；A24→A25→A26→A27 `deconstruction_certus_quartz_pillar` **再次产 `certus_quartz_crystal x4`**。两条并行路径产出同一中间物，后一条为冗余路径（与 F-1 同源，由 quartz 形态互转构成）。
- 原因: cone 搜索对等价中间物生成了并行的"装饰方块变形→拆解"路径，未做去重。
- 建议: 对相同输出的并行拆解/变形路径做一次合并收敛；与 F-1 的 no-op 折叠一并处理。

### F-TE-5 | chain=0000（系统性，mega-wide 感应链） | severity=minor | category=unresolved-expected-item
- 位置: CHAIN 0000 `[UNRESOLVED] minecraft:stick x18 / iron_ingot x2 / redstone x4 / diamond x2 / gold_ingot x2 / emerald x2 / raw_iron_block x2 …`（共 55 个原版物 unresolved）；0001 同型 56 个、0002 53 个等。
- 问题: 这些 UNRESOLVED 全部为 `reason=BUDGET_TRUNCATED`（cone `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`），且均为原版常见物（stick/iron_ingot/redstone/diamond/gold_ingot/emerald/raw_iron 等），明显本可解析。因 cone 预算在到达 trivial 原版叶子前已耗尽，导致链尾堆积大量可解析未解析物。
- 原因: 感应类 mega-wide 目标的 cone 预算（maxRootInputBudget）不足以覆盖其极宽叶集，预算截断先于 trivial 叶子解析。
- 建议: 对 mega-wide 感应目标放宽根输入预算或预置原版 staple 的解析短路；该未解析系预算截断所致（非坏步骤级联），故标 minor，不按 critical 处理。

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |
|---|---|---|---|
| 0000 | incomplete | bad | mekanism_extras:infinite_induction_cell — amount-insanity: crafts=377076, 98可逆转换 |
| 0001 | incomplete | bad | mekanism_extras:infinite_induction_provider — amount-insanity: crafts=377800, 101可逆转换 |
| 0002 | incomplete | bad | mekanism_extras:cosmic_induction_cell — amount-insanity: crafts=635903, 99可逆转换 |
| 0003 | incomplete | bad | mekanism_extras:cosmic_induction_provider — amount-insanity: crafts=1039179, 92可逆转换 |
| 0004 | incomplete | bad | mekanism_extras:supreme_induction_cell — amount-insanity: crafts=159001, 77可逆转换 |
| 0005 | incomplete | bad | mekanism_extras:supreme_induction_provider — amount-insanity: crafts=366333, 78可逆转换 |
| 0006 | incomplete | questionable | mekanism_extras:absolute_induction_cell — tool-smelt 损毁回收 x2 + 可逆转换 x58 |
| 0007 | incomplete | bad | mekanism_extras:absolute_induction_provider — amount-insanity: crafts=98326, 69可逆转换 |
| 0008 | mega-wide | questionable | rftoolsutility:speedplus_module — 系统性 no-op 往返 (redstone/block) x48 |
| 0009 | incomplete | questionable | immersive_aircraft:warship — BUDGET_TRUNCATED 未解析原版物 x7 (预期) |
| 0010 | colossal | questionable | appmek:portable_chemical_cell_256k — 系统性 no-op 往返 (redstone/block) x36 |
| 0011 | mega-wide | questionable | rftoolsutility:featherfallingplus_module — 系统性 no-op 往返 (redstone/block) x47 |
| 0012 | mega-wide | questionable | rftoolsutility:hasteplus_module — 系统性 no-op 往返 (redstone/block) x48 |
| 0013 | mega-wide | questionable | rftoolsutility:regenerationplus_module — 系统性 no-op 往返 (redstone/block) x49 |
| 0014 | mega-wide | questionable | rftoolsutility:saturationplus_module — 系统性 no-op 往返 (redstone/block) x47 |
| 0015 | incomplete | questionable | pneumaticcraft:jumping_upgrade_4 — 含可逆转换 x3 (no-op 往返) |
| 0016 | incomplete | questionable | mekanism:ultimate_induction_cell — tool-smelt 损毁回收 x2 + 可逆转换 x41 |
| 0017 | colossal | questionable | ae2:portable_item_cell_256k — 系统性 no-op 往返 (redstone/block) x22 |
| 0018 | incomplete | bad | mekanism:module_gravitational_modulating_unit — amount-insanity: crafts=53249, 66可逆转换 |
| 0019 | incomplete | questionable | pneumaticcraft:jet_boots_upgrade_5 — 含可逆转换 x1 (no-op 往返) |
| 0020 | incomplete | questionable | mekanism:ultimate_induction_provider — tool-smelt 损毁回收 x2 + 可逆转换 x49 |
| 0021 | incomplete | questionable | actuallyadditions:quintuple_battery — 含可逆转换 x5 (no-op 往返) |
| 0022 | colossal | questionable | appmek:portable_chemical_cell_64k — 系统性 no-op 往返 (redstone/block) x30 |
| 0023 | colossal | questionable | rftoolsutility:peaceful_module — 系统性 no-op 往返 (redstone/block) x41 |
| 0024 | incomplete | questionable | pneumaticcraft:jumping_upgrade_3 — 含可逆转换 x2 (no-op 往返) |
| 0025 | colossal | questionable | ae2:portable_fluid_cell_256k — 系统性 no-op 往返 (redstone/block) x30 |
| 0026 | incomplete | questionable | pneumaticcraft:jet_boots_upgrade_4 — 含可逆转换 x1 (no-op 往返) |
| 0027 | colossal | questionable | rftoolsutility:waterbreathing_module — 系统性 no-op 往返 (redstone/block) x41 |
| 0028 | colossal | questionable | rftoolsutility:flight_module — 系统性 no-op 往返 (redstone/block) x41 |
| 0029 | incomplete | questionable | actuallyadditions:handheld_filler — 含可逆转换 x4 (no-op 往返) |
| 0030 | incomplete | questionable | ballistix:minecartdarkmatter — 含可逆转换 x11 (no-op 往返) |
| 0031 | incomplete | questionable | immersive_aircraft:bamboo_hopper — BUDGET_TRUNCATED 未解析原版物 x6 (预期) |
| 0032 | incomplete | questionable | ballistix:minecartlargeantimatter — 含可逆转换 x5 (no-op 往返) |
| 0033 | incomplete | questionable | ballistix:darkmatter — 含可逆转换 x11 (no-op 往返) |
| 0034 | incomplete | questionable | actuallyadditions:netherite_aiot — 含可逆转换 x1 (no-op 往返) |
| 0035 | incomplete | questionable | actuallyadditions:quadruple_battery — 含可逆转换 x4 (no-op 往返) |
| 0036 | incomplete | questionable | ballistix:minecartantimatter — 含可逆转换 x11 (no-op 往返) |
| 0037 | incomplete | questionable | ballistix:largeantimatter — 含可逆转换 x11 (no-op 往返) |
| 0038 | incomplete | questionable | ballistix:minecartnuclear — 含可逆转换 x5 (no-op 往返) |
| 0039 | colossal | questionable | ae2:portable_item_cell_64k — 系统性 no-op 往返 (redstone/block) x20 |
| 0040 | colossal | questionable | appmek:chemical_storage_cell_256k — 含可逆转换 x14 (no-op 往返) |
| 0041 | incomplete | questionable | ballistix:antimatter — 含可逆转换 x12 (no-op 往返) |
| 0042 | colossal | questionable | rftoolspower:cell3 — 系统性 no-op 往返 (redstone/block) x22 |
| 0043 | incomplete | questionable | mekanism:elite_induction_cell — tool-smelt 损毁回收 x1 + 可逆转换 x26 |
| 0044 | colossal | questionable | rftoolsutility:blindness_module — 系统性 no-op 往返 (redstone/block) x37 |
| 0045 | incomplete | questionable | pneumaticcraft:jet_boots_upgrade_3 — 含可逆转换 x1 (no-op 往返) |
| 0046 | colossal | questionable | rftoolsutility:noteleport_module — 系统性 no-op 往返 (redstone/block) x37 |
| 0047 | incomplete | questionable | ballistix:minecartthermobaric — 含可逆转换 x8 (no-op 往返) |
| 0048 | incomplete | questionable | ballistix:nuclear — 含可逆转换 x12 (no-op 往返) |
| 0049 | incomplete | questionable | immersive_aircraft:cargo_airship — 含可逆转换 x1 (no-op 往返) |
| 0050 | colossal | questionable | appmek:portable_chemical_cell_16k — 系统性 no-op 往返 (redstone/block) x35 |
| 0051 | colossal | questionable | ae2:portable_item_cell_16k — 系统性 no-op 往返 (redstone/block) x30 |
| 0052 | incomplete | questionable | ballistix:thermobaric — 含可逆转换 x8 (no-op 往返) |
| 0053 | incomplete | questionable | pneumaticcraft:jumping_upgrade_2 — 含可逆转换 x2 (no-op 往返) |
| 0054 | incomplete | questionable | mekanism:elite_induction_provider — tool-smelt 损毁回收 x1 + 可逆转换 x31 |
| 0055 | incomplete | questionable | mekanism_extras:infinite_energy_cube — tool-smelt 损毁回收 x1 + 可逆转换 x7 |
| 0056 | colossal | questionable | ae2:portable_fluid_cell_64k — 含可逆转换 x15 (no-op 往返) |
| 0057 | incomplete | questionable | rftoolsstorage:storage_module3 — 系统性 no-op 往返 (redstone/block) x30 |
| 0058 | colossal | questionable | rftoolsutility:moduleplus_template — 系统性 no-op 往返 (redstone/block) x37 |
| 0059 | incomplete | questionable | actuallyadditions:triple_battery — 含可逆转换 x3 (no-op 往返) |
| 0060 | incomplete | questionable | actuallyadditions:battery_box — 含可逆转换 x3 (no-op 往返) |
| 0061 | colossal | questionable | ae2:item_storage_cell_256k — 含可逆转换 x8 (no-op 往返) |
| 0062 | incomplete | questionable | rftoolsbuilder:shield_block4 — tool-smelt 损毁回收 x1 + 可逆转换 x14 |
| 0063 | incomplete | questionable | pneumaticcraft:jet_boots_upgrade_2 — 含可逆转换 x2 (no-op 往返) |
| 0064 | incomplete | questionable | immersiveengineering:turret_chem — tool-smelt 损毁回收 x1 + 可逆转换 x15 |
| 0065 | incomplete | questionable | prefab:item_house_advanced — 含可逆转换 x11 (no-op 往返) |
| 0066 | incomplete | questionable | immersive_aircraft:airship — 含可逆转换 x1 (no-op 往返) |
| 0067 | incomplete | questionable | immersiveengineering:turret_gun — BUDGET_TRUNCATED 未解析原版物 x6 (预期) |
| 0068 | incomplete | questionable | actuallyadditions:empowerer — 含可逆转换 x3 (no-op 往返) |
| 0069 | colossal | questionable | ae2:portable_fluid_cell_16k — 系统性 no-op 往返 (redstone/block) x25 |
| 0070 | incomplete | questionable | mekanism_extras:cosmic_energy_cube — tool-smelt 损毁回收 x2 + 可逆转换 x7 |
| 0071 | incomplete | questionable | sophisticatedbackpacks:xp_pump_upgrade — BUDGET_TRUNCATED 未解析原版物 x5 (预期) |
| 0072 | incomplete | questionable | pneumaticcraft:universal_sensor — 含可逆转换 x9 (no-op 往返) |
| 0073 | incomplete | questionable | nuclearscience:electromagneticgateway — BUDGET_TRUNCATED 未解析原版物 x10 (预期) |
| 0074 | incomplete | questionable | prefab:item_warehouse_improved — 含可逆转换 (系统性) |
| 0075 | incomplete | questionable | electrodynamics:circuitbreaker — BUDGET_TRUNCATED 未解析原版物 x6 (预期) |
| 0076 | incomplete | questionable | electrodynamics:advancedcompressor — BUDGET_TRUNCATED 未解析原版物 x12 (预期) |
| 0077 | colossal | questionable | rftoolsutility:speed_module — 系统性 no-op 往返 (redstone/block) x30 |
| 0078 | incomplete | questionable | ballistix:firecontrolradar — BUDGET_TRUNCATED 未解析原版物 x8 (预期) |
| 0079 | incomplete | questionable | immersive_aircraft:nether_engine — 含可逆转换 x2 (no-op 往返) |
| 0080 | colossal | questionable | rftoolsutility:slowness_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0081 | colossal | questionable | ae2:portable_fluid_cell_4k — 系统性 no-op 往返 (redstone/block) x27 |
| 0082 | colossal | questionable | appmek:portable_chemical_cell_4k — 系统性 no-op 往返 (redstone/block) x29 |
| 0083 | colossal | questionable | ae2:portable_item_cell_4k — 系统性 no-op 往返 (redstone/block) x27 |
| 0084 | incomplete | questionable | immersive_aircraft:gyroscope_hud — 含可逆转换 x6 (no-op 往返) |
| 0085 | colossal | questionable | ae2:fluid_storage_cell_64k — 系统性 no-op 往返 (redstone/block) x24 |
| 0086 | incomplete | questionable | mekanism_extras:supreme_energy_cube — tool-smelt 损毁回收 x2 + 可逆转换 x7 |
| 0087 | incomplete | questionable | electrodynamics:advancedthermoelectricmanipulator — BUDGET_TRUNCATED 未解析原版物 x11 (预期) |
| 0088 | colossal | questionable | rftoolspower:cell2 — 含可逆转换 x16 (no-op 往返) |
| 0089 | colossal | questionable | appmek:chemical_storage_cell_16k — 系统性 no-op 往返 (redstone/block) x23 |
| 0090 | colossal | questionable | rftoolsutility:featherfalling_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0091 | colossal | questionable | rftoolsutility:haste_module — 系统性 no-op 往返 (redstone/block) x30 |
| 0092 | incomplete | questionable | industrialforegoing:simulated_hydroponic_bed — 含可逆转换 x10 (no-op 往返) |
| 0093 | incomplete | questionable | nuclearscience:electromagneticdiode — 含可逆转换 x5 (no-op 往返) |
| 0094 | colossal | questionable | rftoolsutility:regeneration_module — 系统性 no-op 往返 (redstone/block) x30 |
| 0095 | incomplete | questionable | mekanism_extras:infinite_injecting_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0096 | incomplete | questionable | ballistix:landmine — 含可逆转换 x8 (no-op 往返) |
| 0097 | incomplete | questionable | electrodynamics:electrolosischamber — BUDGET_TRUNCATED 未解析原版物 x9 (预期) |
| 0098 | incomplete | questionable | actuallyadditions:heat_collector — 含可逆转换 x3 (no-op 往返) |
| 0099 | colossal | questionable | rftoolsutility:luck_module — 系统性 no-op 往返 (redstone/block) x31 |
| 0100 | colossal | questionable | rftoolsutility:nightvision_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0101 | colossal | questionable | rftoolsutility:glowing_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0102 | incomplete | questionable | immersive_aircraft:eco_engine — 含可逆转换 x2 (no-op 往返) |
| 0103 | incomplete | questionable | rftoolsbuilder:shield_block3 — tool-smelt 损毁回收 x1 + 可逆转换 x14 |
| 0104 | incomplete | questionable | nuclearscience:electromagneticswitch — BUDGET_TRUNCATED 未解析原版物 x7 (预期) |
| 0105 | incomplete | questionable | create:netherite_backtank — 含可逆转换 x2 (no-op 往返) |
| 0106 | incomplete | questionable | mekanism:advanced_induction_cell — tool-smelt 损毁回收 x1 + 可逆转换 x14 |
| 0107 | incomplete | questionable | actuallyadditions:double_battery — 含可逆转换 x2 (no-op 往返) |
| 0108 | incomplete | questionable | ballistix:minecartfragmentation — 含可逆转换 x8 (no-op 往返) |
| 0109 | incomplete | questionable | actuallyadditions:teleport_staff — 含可逆转换 x1 (no-op 往返) |
| 0110 | incomplete | questionable | actuallyadditions:laser_relay_extreme — 含可逆转换 x5 (no-op 往返) |
| 0111 | colossal | questionable | rftoolsutility:weakness_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0112 | colossal | questionable | ae2:fluid_storage_cell_256k — 含可逆转换 x13 (no-op 往返) |
| 0113 | colossal | questionable | rftoolsutility:saturation_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0114 | colossal | questionable | rftoolsutility:poison_module — 系统性 no-op 往返 (redstone/block) x29 |
| 0115 | incomplete | questionable | immersive_aircraft:biplane — 含可逆转换 x2 (no-op 往返) |
| 0116 | incomplete | questionable | mekanism_extras:infinite_purifying_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0117 | incomplete | questionable | mekanism_extras:cosmic_injecting_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0118 | incomplete | questionable | immersive_aircraft:gyroscope_dials — 含可逆转换 x10 (no-op 往返) |
| 0119 | colossal | questionable | ae2wtlib:wireless_universal_terminal — 系统性 no-op 往返 (redstone/block) x25 |
| 0120 | colossal | questionable | ae2:fluid_storage_cell_16k — 系统性 no-op 往返 (redstone/block) x24 |
| 0121 | colossal | questionable | appmek:chemical_storage_cell_64k — 含可逆转换 x9 (no-op 往返) |
| 0122 | colossal | questionable | ae2:item_storage_cell_64k — 含可逆转换 x7 (no-op 往返) |
| 0123 | incomplete | questionable | primalmagick:salted_rabbit_stew — tool-smelt 损毁回收 x1 + 可逆转换 x0 |
| 0124 | colossal | questionable | ae2:256k_crafting_storage — 含可逆转换 x13 (no-op 往返) |
| 0125 | incomplete | questionable | mekanism_extras:absolute_energy_cube — tool-smelt 损毁回收 x2 + 可逆转换 x6 |
| 0126 | incomplete | questionable | mekanism_extras:infinite_compressing_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0127 | incomplete | questionable | rftoolsutility:destination_analyzer — 含可逆转换 x13 (no-op 往返) |
| 0128 | colossal | questionable | geneticsresequenced:blood_purifier — 含可逆转换 x1 (no-op 往返) |
| 0129 | incomplete | questionable | industrialforegoing:hydroponic_simulation_processor — 系统性 no-op 往返 (redstone/block) x28 |
| 0130 | incomplete | questionable | pneumaticcraft:jumping_upgrade_1 — 含可逆转换 x11 (no-op 往返) |
| 0131 | incomplete | questionable | industrialforegoing:mob_detector — 系统性 no-op 往返 (redstone/block) x30 |
| 0132 | colossal | questionable | rftoolsutility:crafter3 — 含可逆转换 x9 (no-op 往返) |
| 0133 | incomplete | questionable | rftoolsbuilder:shape_card_pump_clear — 含可逆转换 x8 (no-op 往返) |
| 0134 | colossal | questionable | ae2wtlib:wireless_pattern_encoding_terminal — 系统性 no-op 往返 (redstone/block) x27 |
| 0135 | incomplete | questionable | nuclearscience:fusioninterface — 系统性 no-op 往返 (redstone/block) x21 |
| 0136 | incomplete | questionable | securitycraft:secure_redstone_interface — 系统性 no-op 往返 (redstone/block) x24 |
| 0137 | incomplete | questionable | electrodynamics:thermoelectricmanipulator — BUDGET_TRUNCATED 未解析原版物 x13 (预期) |
| 0138 | incomplete | questionable | xnet:controller — 系统性 no-op 往返 (redstone/block) x22 |
| 0139 | incomplete | questionable | immersive_aircraft:quadrocopter — 含可逆转换 x1 (no-op 往返) |
| 0140 | incomplete | questionable | mekanism_extras:infinite_combining_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0141 | incomplete | questionable | ballistix:fragmentation — 含可逆转换 x8 (no-op 往返) |
| 0142 | incomplete | questionable | mekanism_extras:infinite_smelting_factory — tool-smelt 损毁回收 x9 + 可逆转换 x10 |
| 0143 | incomplete | questionable | mekanism:mekasuit_bodyarmor — 含可逆转换 x12 (no-op 往返) |
| 0144 | incomplete | questionable | mekanism_extras:cosmic_purifying_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0145 | incomplete | questionable | mekanism_extras:supreme_injecting_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0146 | incomplete | questionable | mekanism_extras:infinite_sawing_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0147 | incomplete | questionable | mekanism_extras:infinite_crushing_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0148 | incomplete | questionable | mekanism_extras:infinite_enriching_factory — tool-smelt 损毁回收 x1 + 可逆转换 x10 |
| 0149 | incomplete | questionable | mekanism:mekasuit_pants — tool-smelt 损毁回收 x1 + 可逆转换 x11 |

## 规则建议（给汇总者）
- targetRegex: `^mekanism_extras:(infinite|cosmic|supreme|absolute)_induction_(cell|provider)$|^mekanism:(ultimate|elite|advanced)_induction_(cell|provider)$` —— 收敛这些 mega-wide 感应目标（amount-insanity，证据 8 链 crafts≥50000：0000/0001/0002/0003/0004/0005/0007/0018）。
- recipeRegex: `(_from_(iron|gold|copper|diamond|emerald|netherite|redstone|lapis|quartz)_block|from_nuggets|_nugget$|^minecraft:redstone$|raw_storage_blocks/from_raw|from_raw_block|_slab$|_pillar$|deconstruction_|cut_quartz)` —— 标记/折叠可逆 no-op 往返配方族（证据 2966 次执行 / 148 链）。注意：此族未带任何 builtin rule-tag，勿与已内置 recycling/exchange 规则重复。
- builtin 候选:
  1. `pattern-noop-roundtrip`（loop 检测）：把"可逆 block↔item / ingot↔nugget / redstone↔redstone_block 零净产出往返"加入循环检测并折叠；跨包通用（证据 148/150 链）。
  2. `pattern-lossy-recycle`（junk/duplicate 倾向）：`mekanismtools:*/nugget_from_smelting` 及同型"装备→nugget"回收标为损材回收，仅在无直接 ingot 来源时允许（证据 33 incomplete 链、29 次执行、osmium_shovel 29 次出现且 0 次被消费）。
  3. 数据质量校验：配方输入 id 与上游边来源不一致（CHAIN 0055 A19 `in: osmium_helmet` 但 `<= from A18` 的 `osmium_shovel`），建议导出层加一致性断言。
- 无新模式时写"无"：本批次除上述外无明显 fake-production / free-resource / tag-expansion / catalyst-misuse / machine-mismatch / generation-abuse / data-pollution。
