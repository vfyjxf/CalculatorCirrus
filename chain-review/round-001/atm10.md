# atm10 batch-001 审查报告
- pack: atm10
- 批次: batch-001（written=150，类别分布：incomplete=150 / mega-wide=134；无 trivial/short/medium/long/giant/colossal 小类别文件，全部落在 chains-incomplete-001.txt）
- 审查链数: 150（incomplete 150 / complete 0；大文件 82MB≈550KB/链，>100KB/条触发抽样下限 40，但本批以 grep 链边界 + awk 区间/字段抽取 + 定向 grep 信号替代整读，覆盖全部 150 条；优先 incomplete/mega-wide 全覆盖）
- 发现: critical 1 / major 4 / minor 0

## 发现

### F-atm10-1 | chain=0000,0002,0003,0011,0014,0018,0019,... | severity=major | category=amount-insanity
- 位置: status 行 `crafts=` 与 `[UNRESOLVED]` 行数量
- 问题: 单件终产物（如一把钻头）的 `crafts` 总数与未解析需求达到数量级失控：`CHAIN 0000` crafts=21534650121（215 亿）、`CHAIN 0002` crafts=177593068672（1779 亿）、`CHAIN 0003` crafts=1288680809298（1.29 万亿）、`CHAIN 0011` crafts=5109593734725（5.1 万亿）、`CHAIN 0014` crafts=7184550644578（7.2 万亿）。未解析需求触达整数溢出哨兵值：`CHAIN 0000` `minecraft:enchanted_book x106048573` 与 `minecraft:enchanted_book x2147483647`（=2^31-1, Integer.MAX_VALUE）、`mysticalagriculture:inferium_essence x8589934588`（≈2^33）；`CHAIN 0002/0003` `maxQ=2147483647000`（2^31-1×1000）。全批 89/150 链 crafts≥1e9，maxQ 普遍在 1.7e10–2.1e12。
- 原因: `loop=allowBoundedAmplification` 在 `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]` 的巨型锥（expanded=3027/7258、candidates=210730/142827）下反复叠加放大，放大计数 32 位溢出，导致需求与 crafts 爆炸式膨胀，远超单件真实成本。
- 建议: 规划器层修复 `allowBoundedAmplification` 计数溢出并对单件终产设 crafts 上界；审查口径上加 `crafts>=1e9` 自动降级为 bad（量纲失控）。

### F-atm10-2 | chain=0001（A1–A23）, 系统性 | severity=major | category=loop-or-cycle
- 位置: `CHAIN 0001` A1–A23，`allthecompressed:compress/raw_tin_block_*x` ↔ `allthecompressed:decompress/raw_tin_block_*x`
- 问题: 在 6x/7x/8x/9x 压缩层级间无净产出地往返：A8 decompress 9x→8x、A9 8x→7x、A10 又 compress 7x→8x、A11 8x→9x、A12 decompress 9x→8x、A13 8x→7x、A14 又 compress 7x→8x……持续横跳（A8–A21），最终仅经 A22–A23 解出 raw_tin。纯 compress↔decompress 互逆 no-op 重复，符合口径「compress↔decompress 互逆 no-op 重复多次」的循环判定。
- 原因: `allowBoundedAmplification` 声明策略未排除互逆压缩/解压环；规划器为凑目标数量在压缩级数间来回，无净产出。
- 建议: 加 `pattern-loop-amplification` 规则识别「raw_*_block_Nx 压缩↔解压互逆往返」并只保留单向抵达目标级数；全批 compress 12679 行 + decompress 14009 行，普遍存在于需原材料的链。

### F-atm10-3 | chain=0000,0001,0004,...（系统性） | severity=major | category=tag-expansion-overreach
- 位置: `[UNRESOLVED]` 与 `in :` 行中 `item|+N` 变体 id
- 问题: 大量携带 `|+N` 状态/调色板变体后缀的 id 被选作需求或输入，且多数无独立合成路径而落入 UNRESOLVED。证据：`CHAIN 0000` `create:cut_tuff|+14`（重复多行）、`chipped:tiny_brick_bordered_bricks|+65`、`framedblocks:framed_iron_gate|+203`、`rechiseled:diamond_block_chiseled|+72`；`CHAIN 0001` `modern_industrialization:raw_tin_block|+2 x531441`、`xycraft_world:kivi|+3`、`industrialforegoing:white_laser_lens|+15`；`CHAIN 0004` `productivetrees:beech_wood|+970`、`silentgear:netherwood_stick|+4`、`productivetrees:sawdust|+2`；甚至原版物品也被加了变体：`minecraft:red_sand|+2`、`minecraft:pufferfish|+2`。全批 `|+N` 作为 UNRESOLVED 达 3783 行、作为输入达 79089 行。
- 原因: tag/调色板展开把「任意方块状态变体」展开成具体 `|+N` 成员，其中装饰性/状态变体（chiseled/cut/framed/rechiseled/xycraft kivi、MI 的 `+N` 处理态）无独立配方；`|+|` 后缀使本可合成的基础物品（如 red_sand、pufferfish）也解析失败 → 既 tag-expansion-overreach 又 data-pollution。
- 建议: 收敛层剥离 `|\+[0-9]+` 后缀回退到基物品再求解；对确为独立物品者保留。规则建议见下 recipeRegex。

### F-atm10-4 | chain=0001,0004,0000,...（系统性） | severity=critical | category=unresolved-expected-item
- 位置: `[UNRESOLVED]` 行中原版常见物（reason=BUDGET_TRUNCATED）
- 问题: 原版随手可得的物品被标 UNRESOLVED，明显本可解析：`CHAIN 0001` `minecraft:water_bucket x1`、`minecraft:leather_boots x1`、`minecraft:leather_chestplate x1`、`minecraft:paper x1`、`minecraft:potato x36`；`CHAIN 0000` `minecraft:stone x118098`、`minecraft:enchanted_book x106048573`、`minecraft:iron_ingot x162`、`minecraft:copper_ingot x2`；`CHAIN 0004` nV=64（大量原版未解析）。全批 48/150 链含 ≥10 个原版未解析（0079 nV=1214、0093 nV=1307、0062 nV=442 等）。
- 原因: 巨型锥（maxRootInputBudget 耗尽）把预算先耗在深层放大环上，到原版叶子时 per-leaf 预算已 BUDGET_TRUNCATED；属 F-atm10-1 的下游症状，但直接后果是叶子解析不可信（看似缺料实为预算耗尽）。
- 建议: 与原版叶子解析解耦（原版常见物应作为内置可解析源，不进入锥预算竞争）；至少对 `minecraft:*` 常见物设「可解析白名单」免 UNRESOLVED。

### F-atm10-5 | chain=0000,0008,0009,0110,...（系统性） | severity=major | category=unresolved-cascade
- 位置: `[UNRESOLVED]` 行 `reason=CASCADE_EXHAUSTED`
- 问题: 全批 3232 处 `reason=CASCADE_EXHAUSTED`，即上游预算/截断失败级联到依赖物：`CHAIN 0000` `modern_industrialization:turbo_machine_hull x4 (CASCADE_EXHAUSTED)`、`CHAIN 0002/0003/0004` cascade=3/2/2；13/150 链被判级含 cascade（如 0008/0009 等离子/iridium 管、0110/0118/0120/0122/0123/0125/0128/0130/0131/0134/0136/0143 等 expandedae 加速器）。
- 原因: 上游放大/截断使依赖的 MI 机器部件无法求解，错误沿锥向下游扩散，成片 UNRESOLVED；是 F-atm10-1 的级联表现。
- 建议: 先修 F-atm10-1 的量纲失控，cascade 会随之收敛；审查口径将 `CASCADE_EXHAUSTED` 与 `BUDGET_TRUNCATED` 合并计入「预算耗尽」类，避免重复计为独立缺陷。

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |
| 0000 | extended_industrialization:ultimate_laser_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=8589934588 |
| 0001 | modern_industrialization:quantum_tank x1  | unresolved-expected-item | questionable | 原版未解析 nV=35 |
| 0002 | modern_industrialization:highly_advanced_upgrade x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0003 | industrialization_overdrive:vajra x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0004 | allthemodium:vibranium_ore x1  | unresolved-expected-item | questionable | 原版未解析 nV=64 |
| 0005 | kubejs:allthemodium_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0006 | kubejs:vibranium_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0007 | kubejs:unobtainium_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869167 |
| 0008 | modern_industrialization:plasma_turbine x1  | unresolved-cascade | questionable | cascade=1 |
| 0009 | modern_industrialization:iridium_machine_casing_pipe x1  | unresolved-cascade | questionable | cascade=1 |
| 0010 | modern_industrialization:large_advanced_pump x1  | unresolved-expected-item | questionable | UNRESOLVED=108 |
| 0011 | modern_industrialization:titanium_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=19327352823 |
| 0012 | modern_industrialization:quantum_circuit x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0013 | advanced_ae:data_entangler x1  | amount-insanity | bad | crafts/量纲失控 maxQ=774840992 |
| 0014 | allthetweaks:nexium_emitter x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0015 | modern_industrialization:uu_matter_bucket x1  | amount-insanity | bad | crafts/量纲失控 maxQ=3486784401 |
| 0016 | modern_industrialization:uu_matter x1  | amount-insanity | bad | crafts/量纲失控 maxQ=3486784401 |
| 0017 | modern_industrialization:singularity x1  | amount-insanity | bad | crafts/量纲失控 maxQ=3486784401 |
| 0018 | modern_industrialization:nuke x1  | amount-insanity | bad | crafts/量纲失控 maxQ=19327352823 |
| 0019 | advanced_ae:quantum_multi_threader x1  | amount-insanity | bad | crafts/量纲失控 maxQ=268435456000 |
| 0020 | extended_industrialization:electric_mining_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0021 | modern_industrialization:large_advanced_motor x1  | amount-insanity | bad | crafts/量纲失控 maxQ=1339300743 |
| 0022 | modern_industrialization:turbo_upgrade x1  | unresolved-expected-item | questionable | 原版未解析 nV=70 |
| 0023 | modern_industrialization:processing_unit x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0024 | extended_industrialization:electric_chainsaw x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0025 | advanced_ae:quantum_core x1  | amount-insanity | bad | crafts/量纲失控 maxQ=6198727824 |
| 0026 | modern_industrialization:stainless_steel_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0027 | modern_industrialization:advanced_upgrade x1  | amount-insanity | bad | crafts/量纲失控 maxQ=89478486000 |
| 0028 | modern_industrialization:diesel_mining_drill x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0029 | modern_industrialization:centrifuge x1  | amount-insanity | questionable | crafts=269001033 |
| 0030 | modern_industrialization:hv_diesel_generator x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0031 | modern_industrialization:hv_steam_turbine x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0032 | megacells:source_storage_cell_256m x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0033 | megacells:256m_crafting_storage x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0034 | merrymaking:cookie_tray x1  | unresolved-expected-item | questionable | UNRESOLVED=37 |
| 0035 | powah:reactor_nitro x1  | unresolved-expected-item | questionable | UNRESOLVED=30 |
| 0036 | extended_industrialization:tesla_tower x1  | unresolved-expected-item | questionable | 原版未解析 nV=30 |
| 0037 | extended_industrialization:hv_solar_panel x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0038 | megacells:source_storage_cell_64m x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0039 | extended_industrialization:machine_chainer x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0040 | modern_industrialization:chemical_reactor x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0041 | megacells:64m_crafting_storage x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0042 | modern_industrialization:mv_diesel_generator x1  | amount-insanity | questionable | crafts=268993077 |
| 0043 | modern_industrialization:distillery x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0044 | modern_industrialization:electrolyzer x1  | amount-insanity | bad | crafts/量纲失控 maxQ=268435456000 |
| 0045 | advanced_ae:quantum_storage_256 x1  | amount-insanity | bad | crafts/量纲失控 maxQ=774840992 |
| 0046 | enderdrives:ender_storage_component_256k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0047 | megacells:source_storage_cell_16m x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0048 | modern_industrialization:digital_circuit x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0049 | powah:reactor_spirited x1  | unresolved-expected-item | questionable | UNRESOLVED=14 |
| 0050 | megacells:16m_crafting_storage x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0051 | megacells:chemical_storage_cell_256m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0052 | megacells:fluid_storage_cell_256m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0053 | megacells:item_storage_cell_256m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0054 | megacells:cell_component_256m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0055 | megacells:radioactive_cell_component x1  | amount-insanity | bad | crafts/量纲失控 maxQ=19327352823 |
| 0056 | enderdrives:ender_storage_component_64k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0057 | extended_industrialization:nano_quantum_saber x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0058 | advanced_ae:quantum_crafter x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0059 | extended_industrialization:nano_saber x1  | amount-insanity | bad | crafts/量纲失控 maxQ=19327352823 |
| 0060 | modern_industrialization:nuclear_reactor x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0061 | megacells:source_storage_cell_4m x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0062 | allthetweaks:dimensional_seed x1  | amount-insanity | bad | crafts/量纲失控 maxQ=268435456000 |
| 0063 | megacells:4m_crafting_storage x1  | unresolved-expected-item | questionable | 原版未解析 nV=11 |
| 0064 | appflux:fe_256m_cell x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0065 | megacells:fluid_storage_cell_64m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0066 | megacells:item_storage_cell_64m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0067 | megacells:chemical_storage_cell_64m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0068 | modern_industrialization:auto_forge x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0069 | appflux:core_256m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0070 | megacells:cell_component_64m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0071 | ars_nouveau:ritual_awakening x1  | unresolved-expected-item | questionable | UNRESOLVED=45 |
| 0072 | enderdrives:ender_storage_component_16k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0073 | powah:reactor_niotic x1  | unresolved-expected-item | questionable | UNRESOLVED=20 |
| 0074 | megacells:source_storage_cell_1m x1  | unresolved-expected-item | questionable | 原版未解析 nV=12 |
| 0075 | advanced_ae:quantum_storage_128 x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869175 |
| 0076 | appflux:fe_64m_cell x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0077 | appflux:core_64m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0078 | modern_industrialization:large_pump x1  | amount-insanity | bad | crafts/量纲失控 maxQ=3486784401 |
| 0079 | advanced_ae:evasion_card x1  | amount-insanity | bad | crafts/量纲失控 maxQ=43046721000 |
| 0080 | megacells:1m_crafting_storage x1  | unresolved-expected-item | questionable | 原版未解析 nV=11 |
| 0081 | megacells:item_storage_cell_16m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0082 | megacells:chemical_storage_cell_16m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0083 | megacells:fluid_storage_cell_16m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0084 | megacells:cell_component_16m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0085 | appmek:portable_chemical_cell_256k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0086 | modern_industrialization:mv_steam_turbine x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0087 | enderdrives:ender_storage_component_4k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0088 | megacells:bulk_cell_component x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0089 | appflux:fe_16m_cell x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0090 | ae2:fluid_storage_cell_64k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0091 | extended_industrialization:mv_solar_panel x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647 |
| 0092 | appflux:core_16m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0093 | advanced_ae:hp_buffer_card x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0094 | ae2:item_storage_cell_64k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0095 | powah:energy_cable_spirited x1  | unresolved-expected-item | questionable | UNRESOLVED=15 |
| 0096 | modern_industrialization:nuclear_alloy_machine_casing_pipe x1  | unresolved-expected-item | questionable | 原版未解析 nV=33 |
| 0097 | megacells:chemical_storage_cell_4m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0098 | megacells:fluid_storage_cell_4m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0099 | megacells:item_storage_cell_4m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0100 | allthetweaks:withers_compass x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0101 | megacells:cell_component_4m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0102 | ars_elemental:air_turret x1  | unresolved-expected-item | questionable | 原版未解析 nV=31 |
| 0103 | appflux:fe_4m_cell x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0104 | appmek:chemical_storage_cell_256k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0105 | powah:reactor_blazing x1  | unresolved-expected-item | questionable | UNRESOLVED=10 |
| 0106 | ars_elemental:earth_turret x1  | unresolved-expected-item | questionable | 原版未解析 nV=31 |
| 0107 | ars_elemental:water_turret x1  | unresolved-expected-item | questionable | 原版未解析 nV=23 |
| 0108 | ars_elemental:fire_turret x1  | unresolved-expected-item | questionable | 原版未解析 nV=31 |
| 0109 | appflux:core_4m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0110 | expandedae:exp_crafting_accelerator_1m x1  | unresolved-cascade | questionable | cascade=1 |
| 0111 | industrialization_overdrive:multi_processing_array x1  | amount-insanity | bad | crafts/量纲失控 maxQ=19327352823 |
| 0112 | quarryplus:quarry x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0113 | enderdrives:ender_storage_component_1k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0114 | expandedae:exp_crafting_accelerator_512k x1  | unresolved-expected-item | questionable | 原版未解析 nV=27 |
| 0115 | expandedae:exp_crafting_accelerator_256k x1  | unresolved-expected-item | questionable | 原版未解析 nV=37 |
| 0116 | modern_industrialization:large_diesel_generator x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0117 | modern_industrialization:pressurizer x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0118 | expandedae:exp_crafting_accelerator_128k x1  | unresolved-cascade | questionable | cascade=1 |
| 0119 | modularbees:modular_beehive_core x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0120 | expandedae:exp_crafting_accelerator_64k x1  | unresolved-cascade | questionable | cascade=1 |
| 0121 | modularbees:modular_beehive_overclocker x1  | amount-insanity | bad | crafts/量纲失控 maxQ=12884901882 |
| 0122 | expandedae:exp_crafting_accelerator_32k x1  | unresolved-cascade | questionable | cascade=1 |
| 0123 | expandedae:exp_crafting_accelerator_16k x1  | unresolved-cascade | questionable | cascade=1 |
| 0124 | mysticalagriculture:awakened_supremium_upgrade x1  | unresolved-expected-item | questionable | 原版未解析 nV=24 |
| 0125 | expandedae:exp_crafting_accelerator_8k x1  | unresolved-cascade | questionable | cascade=1 |
| 0126 | expandedae:exp_crafting_accelerator_4k x1  | amount-insanity | questionable | crafts=629573099 |
| 0127 | expandedae:exp_crafting_accelerator_2k x1  | unresolved-expected-item | questionable | 原版未解析 nV=35 |
| 0128 | expandedae:exp_crafting_accelerator_1k x1  | unresolved-expected-item | questionable | 原版未解析 nV=30 |
| 0129 | productivelib:upgrade_productivity_4 x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0130 | expandedae:exp_crafting_accelerator_512 x1  | unresolved-cascade | questionable | cascade=1 |
| 0131 | expandedae:exp_crafting_accelerator_256 x1  | unresolved-cascade | questionable | cascade=1 |
| 0132 | expandedae:exp_crafting_accelerator_128 x1  | unresolved-expected-item | questionable | 原版未解析 nV=32 |
| 0133 | create:creative_blaze_cake x1  | amount-insanity | bad | crafts/量纲失控 maxQ=17179869176 |
| 0134 | expandedae:exp_crafting_accelerator_64 x1  | unresolved-cascade | questionable | cascade=1 |
| 0135 | productivetrees:star_fruit_leaves x1  | unresolved-expected-item | questionable | UNRESOLVED=8 |
| 0136 | expandedae:exp_crafting_accelerator_32 x1  | unresolved-cascade | questionable | cascade=1 |
| 0137 | productivetrees:star_fruit_sapling x1  | amount-insanity | questionable | crafts=277114019 |
| 0138 | expandedae:exp_crafting_accelerator_16 x1  | unresolved-expected-item | questionable | 原版未解析 nV=29 |
| 0139 | megacells:chemical_storage_cell_1m x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |
| 0140 | megacells:fluid_storage_cell_1m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0141 | megacells:item_storage_cell_1m x1  | unresolved-expected-item | questionable | 原版未解析 nV=10 |
| 0142 | modern_industrialization:quantum_circuit_board x1  | unresolved-expected-item | questionable | 原版未解析 nV=30 |
| 0143 | expandedae:exp_crafting_accelerator_8 x1  | unresolved-cascade | questionable | cascade=1 |
| 0144 | appmek:chemical_storage_cell_64k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0145 | arseng:source_storage_cell_256k x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0146 | expandedae:exp_crafting_accelerator_4 x1  | unresolved-expected-item | questionable | 原版未解析 nV=31 |
| 0147 | occultism:ritual_dummy/misc_chalk_rainbow x1  | unresolved-expected-item | questionable | 原版未解析 nV=50 |
| 0148 | advanced_ae:quantum_storage_component x1  | amount-insanity | bad | crafts/量纲失控 maxQ=15032385529 |
| 0149 | appflux:fe_1m_cell x1  | amount-insanity | bad | crafts/量纲失控 maxQ=2147483647000 |

## 规则建议（给汇总者）
- targetRegex: 无 —— 本批 150 个目标均为合法终产物（MI 机器/钻头、megacells、appflux、expandedae 等），无需排除；问题在求解过程而非目标。
- recipeRegex: `\|\+[0-9]+` —— 标记所有携带 `|+N` 状态/调色板变体的物品 id（UNRESOLVED 3783 行 + 作为输入 79089 行），建议收敛层剥离 `|\+[0-9]+` 后缀回退到基物品再求解，确无基物品配方者才判 UNRESOLVED；证据：3783 + 79089。
- builtin 候选:
  1. `allthecompressed:compress/raw_*_block_{N}x` ↔ `allthecompressed:decompress/raw_*_block_{N}x` 互逆压缩/解压环 → 标签 `pattern-loop-amplification`（证据：compress 12679 + decompress 14009 行；链 0001 A1–A23 在 6x–9x 间横跳）。跨包通用（任何含 allthecompressed 的包）。
  2. `minecraft:enchantment.`（anvil 附魔）放大链 → 链 0004 A10–A16 多次重复附魔产出递增（x3/x8/x13）；若确认有净产出保留为 `pattern/exchange`，否则加 `loop` 标记。
  3. 需求计数器在 2^31-1（2147483647）与 ≈2^33（8589934588）处饱和/溢出（链 0000 enchanted_book x2147483647、inferium_essence x8589934588）→ 非规则层问题，建议规划器层修复 `allowBoundedAmplification` 的 32 位计数溢出；审查口径加 `crafts>=1e9` 自动判 bad。
  4. `reason=CASCADE_EXHAUSTED` 与 `reason=BUDGET_TRUNCATED` 合并计入「预算耗尽」类（全批 20077+3232 处），避免重复计为独立缺陷；修 F-atm10-1 量纲失控后级联会收敛。
