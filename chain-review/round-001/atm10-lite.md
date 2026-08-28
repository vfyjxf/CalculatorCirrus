# atm10-lite batch-001 审查报告

- pack: atm10-lite
- 批次: batch-001（written=145，全部类别为 incomplete / mega-wide；无 trivial/short/medium/long/giant/colossal 文件）
- 审查链数: 145（incomplete 145 / complete 0；单文件 chains-incomplete-001.txt 78MB，平均 544KB/链 ≫ 100KB 阈值，按大文件口径抽样下限 40，实际全量扫描 145 条并做全文件定向 grep + per-chain 标记）
- 发现: critical 0 / major 4 / minor 1

## 审查范围与读法

单批次只导出了 `chains-incomplete-001.txt`（78MB，145 条链，全部 `incomplete+mega-wide`），
另含 `index.tsv` / `report.txt` / `manifest.txt` / `targets.tsv`。
因文件过大未整读，采用：全文件 `grep`/`awk` 定向信号统计 + per-chain 布尔标记遍历（145/145）+ 抽样精读（0000、0004、0086、0144 等代表性链全文）。

全批系统性强相关，几乎每条链都命中同一组根因：
`loop=allowBoundedAmplification`（声明策略）+ `allthecompressed` 多级压缩 + 大量 chisel/crush 互逆转换，
导致 cone 数量级爆炸（crafts 中位数 119B、最大 412B），并产生乱码 id 与生成类溢出。

## 发现

### F-atm10-lite-1 | chain=0000,0144,...(全批) | severity=major | category=amount-insanity
- 位置: 链头 `crafts=` 字段；链内 EXECUTE 数量（如 0000 A4 `x1549681968`）
- 问题: 全批 crafts 数量级失控。144/145 条 `crafts ≥ 1e9`，中位数 **119B**，最大 **412.7B**（0051/0129），单条链内单步产出达 `x1549681968`（0000 A4 `mekanism:crushing/quartz/to_smooth_quartz`）。数量均为 9 的整数次幂（如 `387420489 = 9^9`、`43046721 = 9^8`），即 `allthecompressed` 多级压缩（quartz_block_1x→9x）的纯数学放大，对“造 1 个目标”毫无意义。
- 原因: `loop=allowBoundedAmplification` 在 mega-wide cone 下与多级压缩/互逆 chisel-crush 叠加，放大被反复计入 crafts 总量；budget 在 `maxRootInputBudget [BUDGET_EXHAUSTED]` 处耗尽，数量已失真。
- 建议: 对 mega-wide 目标收敛时按“目标需求数量”封顶，不要累加 cone 内每一级放大的 crafts；或把 `allthecompressed` 压缩族视为等价的 1 个需求层级。

### F-atm10-lite-2 | chain=0086,0000,...(全批) | severity=major | category=loop-or-cycle
- 位置: 0086 A2–A16（compress raw_iron_block_4x→9x 再 decompress 9x→1x→raw_iron）；0000 A4–A10（quartz_block→smooth_quartz→[chisel]→quartz_block→quartz_pillar→crush→quartz_block→crystallize→quartz→quartz_block/quartz_bricks）
- 问题: 无净产出的互逆往返环。0086 先把 raw_iron_block 压缩到 9x（A2–A6），紧接着又把同一物解压回 1x 再拆成 raw_iron（A7–A16）——纯 `compress↔decompress` no-op 环，仅放大基数。0000 的石英环在 quartz_block 家族内反复 crush/chisel/crystallize 回到 quartz_block（`x1549681968`），同样无净产出、只放大数量。本口径明确把 compress↔decompress / A↔B 互逆 no-op 列为环路问题。
- 原因: 规划器在 cone 扩展时把“压缩”与“解压”都当合法边，未识别其互逆等价；`loop=allowBoundedAmplification` 只豁免“有净产出的放大”，而这批属于无净产出的往返。
- 建议: 识别 `allthecompressed:compress/<x>` 与 `decompress/<x>` 的互逆对，作为 no-op 环路剪枝（见规则建议）。

### F-atm10-lite-3 | chain=0086,0000,...(全批) | severity=major | category=data-pollution
- 位置: `minecraft:enchantment.`（末尾带点的乱码 id），经 `crafting_on_a_stick:anvil` 产出（rule-tag pattern/exchange），如 0086 A25/A27/.../A43+；0000 内出现 **256 次**
- 问题: 145/145 条链都含乱码 id `minecraft:enchantment.`（尾部多一个 `.`），被当作可制作物品反复产出/消耗。`minecraft:enchantment.` 不是合法 ResourceLocation（无路径段、疑似 Enchantment 对象 toString 残片，注册名空）。属口径中“toString 串进入链”的污染。
- 原因: `crafting_on_a_stick` 暴露附魔书时把 Enchantment 实例的 toString（末尾带点）当成物品 id 输出；规划器未过滤即纳入图。
- 建议: 将 `minecraft:enchantment.` 列为 junk 直接剔除（145 链证据），并回源修 `crafting_on_a_stick` 的 id 导出。

### F-atm10-lite-4 | chain=0000,0144,...(全批) | severity=major | category=generation-abuse
- 位置: `industrialforegoing:laser_drill`（intent/generation），如 0000 A1829 `x2147483647 @ industrialforegoing:laser_drill [rule-tag: intent/generation]`
- 问题: 115/145 条链使用 laser_drill 生成配方；其中 **136/145** 条出现数量 `x2147483647`（= `Integer.MAX_VALUE`，无界哨兵）。这是零输入的资源生成（教科书 free-resource），且以“无限量”灌入 cone，直接放大 crafts 总量。
- 原因: 生成类配方被 `intent/generation` 标为 builtin 已处理，但其数量被写成整型溢出哨兵 `2147483647`，规划器把它当真实巨量需求计入，加剧 amount-insanity。
- 建议: 对 laser_drill 等生成配方封顶/排除（按口径，生成步骤唯一有效建议是“是否升级为排除”）；并将 `x2147483647` 视为数据错误单独标记。

### F-atm10-lite-5 | chain=0000,...(全批) | severity=minor | category=unresolved-expected-item
- 位置: `[UNRESOLVED]` 行，如 0000 行6244 `[UNRESOLVED] minecraft:redstone_block|+27 x1 (candidates=5, reason=RANK_TRUNCATED, truncated)`、`minecraft:leather x1 (candidates=5, ...)`
- 问题: 原版常见物（redstone_block、leather、iron_ingot、gold_ingot、diamond 等）仅因 `RANK_TRUNCATED` 预算耗尽而标记为 UNRESOLVED，全批此类原版物未解析行共 585 处。这些物品 trivially 可制作，并非缺配方。
- 原因: mega-wide cone 在 rank 预算处截断，而非分类器漏配方；属 amount-insanity（F-1）的连带症状，不是独立分类错误。
- 建议: 不作为单独缺陷处理，但后续批预算/rank 上限需放宽或按“目标真实需求”收敛，避免原版物被假 unresolved 污染统计。

## 链级判定

> 全部 145 条链均命中 data-pollution + loop-or-cycle + amount-insanity 的系统性强相关，故统一判 `bad`；下表按家族给出主导症状。`laser_drill生成`=含生成溢出，`含ritual_dummy步骤`=链内使用 occultism ritual_dummy 作为机器/步骤（非目标）。

| chain | target | verdict | 一句话 |
|---|---|---|---|
| 0000 | advanced_ae:data_entangler | bad | enchantment.污染+压缩↔解压环+crafts=95.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0001 | allthetweaks:improbable_probability_device | bad | enchantment.污染+压缩↔解压环+crafts=23.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0002 | advanced_ae:quantum_multi_threader | bad | enchantment.污染+压缩↔解压环+crafts=109.9B; laser_drill生成; 含ritual_dummy步骤 |
| 0003 | allthetweaks:nexium_emitter | bad | enchantment.污染+压缩↔解压环+crafts=123.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0004 | powah:thermo_generator_nitro | bad | enchantment.污染+压缩↔解压环+crafts=14.6B; 含ritual_dummy步骤 |
| 0005 | advanced_ae:quantum_core | bad | enchantment.污染+压缩↔解压环+crafts=180.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0006 | powah:thermo_generator_spirited | bad | enchantment.污染+压缩↔解压环+crafts=14.6B; 含ritual_dummy步骤 |
| 0007 | megacells:256m_crafting_storage | bad | enchantment.污染+压缩↔解压环+crafts=158.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0008 | megacells:portable_chemical_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=247.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0009 | megacells:portable_item_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=195.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0010 | megacells:portable_fluid_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=131.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0011 | powah:battery_nitro | bad | enchantment.污染+压缩↔解压环+crafts=1.4B; 含ritual_dummy步骤 |
| 0012 | powah:thermo_generator_niotic | bad | enchantment.污染+压缩↔解压环+crafts=14.6B; 含ritual_dummy步骤 |
| 0013 | appflux:fe_256m_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=217.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0014 | megacells:64m_crafting_storage | bad | enchantment.污染+压缩↔解压环+crafts=158.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0015 | powah:energy_hopper_nitro | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0016 | megacells:portable_chemical_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=138.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0017 | megacells:portable_item_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=175.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0018 | megacells:portable_fluid_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=155.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0019 | advanced_ae:evasion_card | bad | enchantment.污染+压缩↔解压环+crafts=124.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0020 | powah:solar_panel_nitro | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0021 | kubejs:soul_sand_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0022 | appflux:fe_64m_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=214.4B; laser_drill生成; 含ritual_dummy步骤 |
| 0023 | kubejs:netherrack_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0024 | kubejs:calcite_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0025 | kubejs:moss_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0026 | kubejs:clay_cell | bad | enchantment.污染+压缩↔解压环+crafts=107.4B; laser_drill生成; 含ritual_dummy步骤 |
| 0027 | kubejs:cobbled_deepslate_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0028 | kubejs:dirt_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0029 | powah:furnator_nitro | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0030 | kubejs:basalt_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0031 | powah:magmator_nitro | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0032 | kubejs:blackstone_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0033 | kubejs:lava_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0034 | kubejs:sky_stone_cell | bad | enchantment.污染+压缩↔解压环+crafts=141.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0035 | kubejs:end_stone_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0036 | megacells:16m_crafting_storage | bad | enchantment.污染+压缩↔解压环+crafts=158.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0037 | kubejs:ancient_stone_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0038 | kubejs:diorite_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0039 | kubejs:granite_cell | bad | enchantment.污染+压缩↔解压环+crafts=121.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0040 | kubejs:tuff_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0041 | kubejs:andesite_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0042 | kubejs:brown_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0043 | kubejs:cyan_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0044 | kubejs:lime_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0045 | kubejs:black_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0046 | kubejs:white_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0047 | kubejs:purple_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0048 | kubejs:gray_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0049 | kubejs:red_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0050 | megacells:fluid_storage_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0051 | megacells:chemical_storage_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=412.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0052 | megacells:item_storage_cell_256m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0053 | kubejs:light_blue_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0054 | kubejs:magenta_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0055 | kubejs:orange_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0056 | kubejs:blue_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0057 | kubejs:yellow_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0058 | kubejs:pink_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0059 | kubejs:light_gray_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0060 | kubejs:green_cell | bad | enchantment.污染+压缩↔解压环+crafts=119.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0061 | megacells:portable_item_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=175.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0062 | megacells:portable_fluid_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=155.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0063 | megacells:portable_chemical_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=138.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0064 | megacells:radioactive_chemical_cell | bad | enchantment.污染+压缩↔解压环+crafts=120.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0065 | powah:battery_spirited | bad | enchantment.污染+压缩↔解压环+crafts=19.1B; 含ritual_dummy步骤 |
| 0066 | advanced_ae:quantum_storage_256 | bad | enchantment.污染+压缩↔解压环+crafts=88.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0067 | megacells:cell_component_256m | bad | enchantment.污染+压缩↔解压环+crafts=145.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0068 | megacells:radioactive_cell_component | bad | enchantment.污染+压缩↔解压环+crafts=61.3B; 含ritual_dummy步骤 |
| 0069 | appflux:fe_16m_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=217.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0070 | powah:energy_hopper_spirited | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0071 | ae2:portable_fluid_cell_256k | bad | enchantment.污染+压缩↔解压环+crafts=298.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0072 | powah:energy_cell_creative | bad | enchantment.污染+压缩↔解压环+crafts=90.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0073 | powah:energy_cell_nitro | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0074 | powah:solar_panel_spirited | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0075 | appflux:fe_256m_cell | bad | enchantment.污染+压缩↔解压环+crafts=5.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0076 | megacells:4m_crafting_storage | bad | enchantment.污染+压缩↔解压环+crafts=152.9B; laser_drill生成; 含ritual_dummy步骤 |
| 0077 | appflux:core_256m | bad | enchantment.污染+压缩↔解压环+crafts=1.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0078 | megacells:fluid_storage_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0079 | megacells:item_storage_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0080 | megacells:chemical_storage_cell_64m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0081 | appmek:chemical_storage_cell_256k | bad | enchantment.污染+压缩↔解压环+crafts=127.4B; laser_drill生成; 含ritual_dummy步骤 |
| 0082 | megacells:portable_item_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=195.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0083 | megacells:portable_chemical_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=247.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0084 | megacells:portable_fluid_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=131.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0085 | powah:thermo_generator_blazing | bad | enchantment.污染+压缩↔解压环+crafts=34.6B; 含ritual_dummy步骤 |
| 0086 | occultism:ritual_dummy/misc_chalk_rainbow | bad | enchantment.污染+压缩↔解压环+crafts=48M; 含ritual_dummy步骤; 目标即 ritual_dummy |
| 0087 | powah:furnator_spirited | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0088 | powah:magmator_spirited | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0089 | megacells:cell_component_64m | bad | enchantment.污染+压缩↔解压环+crafts=145.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0090 | appflux:fe_4m_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=246.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0091 | powah:player_transmitter_nitro | bad | enchantment.污染+压缩↔解压环+crafts=55.6B; 含ritual_dummy步骤 |
| 0092 | powah:energizing_rod_nitro | bad | enchantment.污染+压缩↔解压环+crafts=55.6B; 含ritual_dummy步骤 |
| 0093 | advanced_ae:quantum_crafter | bad | enchantment.污染+压缩↔解压环+crafts=76.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0094 | appflux:fe_64m_cell | bad | enchantment.污染+压缩↔解压环+crafts=5.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0095 | appmek:portable_chemical_cell_256k | bad | enchantment.污染+压缩↔解压环+crafts=131.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0096 | advanced_ae:hp_buffer_card | bad | enchantment.污染+压缩↔解压环+crafts=142.4B; laser_drill生成; 含ritual_dummy步骤 |
| 0097 | powah:reactor_nitro | bad | enchantment.污染+压缩↔解压环+crafts=111.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0098 | appflux:core_64m | bad | enchantment.污染+压缩↔解压环+crafts=1.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0099 | powah:battery_niotic | bad | enchantment.污染+压缩↔解压环+crafts=14.6B; 含ritual_dummy步骤 |
| 0100 | occultism:ritual_dummy/misc_eldritch_chalice | bad | enchantment.污染+压缩↔解压环+crafts=98.5B; laser_drill生成; 含ritual_dummy步骤; 目标即 ritual_dummy |
| 0101 | occultism:ritual_dummy/misc_celestial_chalice | bad | enchantment.污染+压缩↔解压环+crafts=110.7B; laser_drill生成; 含ritual_dummy步骤; 目标即 ritual_dummy |
| 0102 | megacells:1m_crafting_storage | bad | enchantment.污染+压缩↔解压环+crafts=155.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0103 | megacells:item_storage_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0104 | megacells:fluid_storage_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0105 | megacells:chemical_storage_cell_16m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0106 | appflux:fe_1m_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=247.9B; laser_drill生成; 含ritual_dummy步骤 |
| 0107 | powah:energy_hopper_niotic | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0108 | megacells:portable_item_cell_1m | bad | enchantment.污染+压缩↔解压环+crafts=195.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0109 | megacells:portable_chemical_cell_1m | bad | enchantment.污染+压缩↔解压环+crafts=247.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0110 | megacells:portable_fluid_cell_1m | bad | enchantment.污染+压缩↔解压环+crafts=131.8B; laser_drill生成; 含ritual_dummy步骤 |
| 0111 | powah:energy_cell_spirited | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0112 | advanced_ae:quantum_storage_128 | bad | enchantment.污染+压缩↔解压环+crafts=66.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0113 | megacells:cell_component_16m | bad | enchantment.污染+压缩↔解压环+crafts=161.9B; laser_drill生成; 含ritual_dummy步骤 |
| 0114 | powah:solar_panel_niotic | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0115 | occultism:storage_stabilizer_tier5_dark | bad | enchantment.污染+压缩↔解压环+crafts=41.4B; 含ritual_dummy步骤 |
| 0116 | occultism:ritual_dummy/misc_stabilizer_tier5 | bad | enchantment.污染+压缩↔解压环+crafts=41.4B; laser_drill生成; 含ritual_dummy步骤; 目标即 ritual_dummy |
| 0117 | ae2:portable_item_cell_256k | bad | enchantment.污染+压缩↔解压环+crafts=127.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0118 | appmek:portable_chemical_cell_64k | bad | enchantment.污染+压缩↔解压环+crafts=131.6B; laser_drill生成; 含ritual_dummy步骤 |
| 0119 | megacells:bulk_item_cell | bad | enchantment.污染+压缩↔解压环+crafts=51.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0120 | appflux:fe_16m_cell | bad | enchantment.污染+压缩↔解压环+crafts=5.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0121 | powah:player_transmitter_spirited | bad | enchantment.污染+压缩↔解压环+crafts=55.6B; 含ritual_dummy步骤 |
| 0122 | appflux:core_16m | bad | enchantment.污染+压缩↔解压环+crafts=1.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0123 | powah:energizing_rod_spirited | bad | enchantment.污染+压缩↔解压环+crafts=55.6B; 含ritual_dummy步骤 |
| 0124 | megacells:bulk_cell_component | bad | enchantment.污染+压缩↔解压环+crafts=94.4B; laser_drill生成; 含ritual_dummy步骤 |
| 0125 | powah:furnator_niotic | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0126 | powah:magmator_niotic | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0127 | powah:reactor_spirited | bad | enchantment.污染+压缩↔解压环+crafts=111.5B; laser_drill生成; 含ritual_dummy步骤 |
| 0128 | ae2:portable_fluid_cell_64k | bad | enchantment.污染+压缩↔解压环+crafts=127.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0129 | megacells:chemical_storage_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=412.7B; laser_drill生成; 含ritual_dummy步骤 |
| 0130 | megacells:item_storage_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0131 | megacells:fluid_storage_cell_4m | bad | enchantment.污染+压缩↔解压环+crafts=167.1B; laser_drill生成; 含ritual_dummy步骤 |
| 0132 | allthetweaks:dimensional_seed | bad | enchantment.污染+压缩↔解压环+crafts=111.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0133 | occultism:ritual_dummy/misc_trinity_gem | bad | enchantment.污染+压缩↔解压环+crafts=107.7B; laser_drill生成; 含ritual_dummy步骤; 目标即 ritual_dummy |
| 0134 | appflux:fe_4m_cell | bad | enchantment.污染+压缩↔解压环+crafts=110.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0135 | ae2wtlib:wireless_universal_terminal | bad | enchantment.污染+压缩↔解压环+crafts=55.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0136 | megacells:cell_component_4m | bad | enchantment.污染+压缩↔解压环+crafts=161.9B; laser_drill生成; 含ritual_dummy步骤 |
| 0137 | appflux:core_4m | bad | enchantment.污染+压缩↔解压环+crafts=1.0B; laser_drill生成; 含ritual_dummy步骤 |
| 0138 | powah:thermo_generator_hardened | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0139 | appflux:fe_256k_portable_cell | bad | enchantment.污染+压缩↔解压环+crafts=261.3B; laser_drill生成; 含ritual_dummy步骤 |
| 0140 | powah:battery_blazing | bad | enchantment.污染+压缩↔解压环+crafts=34.5B; 含ritual_dummy步骤 |
| 0141 | powah:energy_cell_niotic | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0142 | powah:energy_hopper_blazing | bad | enchantment.污染+压缩↔解压环+crafts=51.3B; 含ritual_dummy步骤 |
| 0143 | ae2:portable_fluid_cell_16k | bad | enchantment.污染+压缩↔解压环+crafts=127.2B; laser_drill生成; 含ritual_dummy步骤 |
| 0144 | allthetweaks:pulsating_black_hole | bad | enchantment.污染+压缩↔解压环+crafts=272.2B; laser_drill生成; 含ritual_dummy步骤 |

## 规则建议（给汇总者）

- targetRegex: `^occultism:ritual_dummy/.*$` —— 排除 5 条以 ritual_dummy 为目标的链（0086、0100、0101、0116、0133）；这些为 Occultism 仪式 dummy/展示占位项，需先确认是否为真实可获取物品再决定是否保留。证据 5 链。
- recipeRegex: `^allthecompressed:(compress|decompress)/` —— 将多级压缩/解压收敛为 no-op 环剪枝（compress↔decompress 互逆等价）。证据 145/145 链同时含两侧。
- recipeRegex: `^crafting_on_a_stick:anvil$`（产出 `minecraft:enchantment\.`）—— 标 junk/数据污染，剔除乱码 id。证据 145/145 链。
- recipeRegex: `^industrialforegoing:laser_drill$` 且数量 `x2147483647` —— 标 data-error / 生成溢出并封顶；`x2147483647` = Integer.MAX_VALUE 无界哨兵。证据 136/145 链。
- builtin 候选:
  - `minecraft:enchantment.` → **junk**（toString 残片，145 链）；
  - `allthecompressed` 压缩/解压互逆对 → **pattern/noop**（无净产出，145 链）；
  - `industrialforegoing:laser_drill` → **generation/duplicate** 候选，按口径评估是否升级为排除（115 链；含整型溢出哨兵）。
- 无新模式时写“无”：本次除 above 外，未发现 tag 错误展开、machine-mismatch、明显 impure/worse-alternative（mega-wide 的主要矛盾是数量级与环路，而非等价路径选择）。
