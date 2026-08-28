# technical-electrical batch-003 审查报告
- pack: technical-electrical
- 批次: batch-003（written=62；类别分布 incomplete 49 / colossal 10 / long 2 / giant 1；modCap 80、seed 20260829、loop=allowBoundedAmplification、targetBlacklist=decompress）
- 审查链数: 62（全量；49 incomplete / 10 colossal / 2 long / 1 giant）。完整精读约 11 条（0000/0001/0004/0006/0008/0016/0017/0033/0037/0050 及 colossal 0004 尾段），其余经 index.tsv + grep 定向信号筛查（未标签存储环、exchange 729 级展开、UNRESOLVED、jei:、fake-production 标记）。incomplete 平均 ~39KB/条（<100KB 阈值，未触发降采样）。
- 发现: critical 0 / major 3 / minor 3

## 发现
### F-te-001 | chain=0001 | severity=major | category=loop-or-cycle
- 位置: A1–A16（及下游 A134–A139）`pneumaticcraft:compressed_iron_block_from_ingot` / `compressed_iron_ingot_from_block` @ minecraft:crafting_table（无 rule-tag）
- 问题: 9 锭→方块→9 锭 互逆往返重复 8 次（A1 出 block、A2 解回 9 锭、…A16），无净产出，纯 no-op 环；它把 depth 撑到 103 级（A1–A16 即 16 层），并再次出现在 A134–A139。同模式跨 7 条链：0001/0016/0017/0020/0022/0038/0052。
- 原因: `pattern/storage_ladder` 内置规则仅命中 x2（本链 builtin 标注 `pattern/storage_ladder x2`），漏判 pneumaticcraft 压缩铁方块对，使其以无标签往返环形式被反复插入充当 cone 放大/延迟节点。属 round-3 聚焦的"未被任何标签覆盖的可疑配方族"。
- 建议: 将 `pneumaticcraft:compressed_iron_block_from_ingot|compressed_iron_ingot_from_block` 列入 storage_ladder，并对无净产出的 block↔ingot 往返做 collapse。

### F-te-002 | chain=0001 | severity=major | category=amount-insanity
- 位置: A19–A41+ `create:*_stonecutting`（stonecutter，带 `[rule-tag: pattern/exchange]` x52）；根输入 `create:cut_andesite_brick_stairs|+14 x729`
- 问题: 以 9³=729 规模的单一切割 stair 为根，石切横向展开 20+ 变体（bricks/wall/stairs/pillar…）各 729，最终 RAW 含 `create:cut_andesite x31347`、`cut_andesite_stairs x30618`、`cut_andesite_wall x29889` 等 30k+ 个未被目标（`unassembled_reinforced_pressure_chamber_valve`）消费的废产；本链 crafts=37874。同类爆炸：0006(34096)/0008(40144)/0030(39303)/0033(depth103,raw_kinds366)。
- 原因: `|+|N` id 后缀污染 + 存储阶梯把单个 stair 放大到 729，再喂给 exchange 横向展开，造成几何级 crafts 与废产。pattern/exchange 标签已存在但此处仍失控。
- 建议: 注明"已带标签仍失控 (pattern/exchange)"；收敛 exchange 横向展开副本数（同一 stair 根不应并行 20+ 次 729 级转换），或对 729 级输入加 budget cap。

### F-te-003 | chain=0050 | severity=major | category=loop-or-cycle
- 位置: A19–A24 `prefab:compressed_stone` / `compressed_stone_to_stone`（+ double/triple 级联）；另见 0006 A150–A153 `compressed_obsidian`↔`double_compressed_obsidian`↔`to_obsidian`、A160–A165 `grass_slab`/`grass_slab_reverse`/`grass_stairs`/`grass_stairs_reverse`/`grass_wall`/`grass_wall_reverse`
- 问题: 均为 stone→block(729→81)→stone(81→729) 或 grass_block↔slab/stairs/wall 互逆往返，无净产出，作延迟节点反复插入。grep 统计 `prefab:grass_*`reverse 触及 12 条链，`prefab:compressed_stone*` 出现 89 次、`compressed_stone_to_stone` 97 次——属同一条未覆盖压缩族。
- 原因: `pattern/storage_ladder` 规则未覆盖 prefab 压缩石/黑曜石/萤石及 grass slab-stairs-wall 反向对（这些在 0006/0050-0056 等多链以无标签往返环形式出现）。
- 建议: 将 `prefab:(compressed_(stone|obsidian|glowstone)|compressed_[a-z]+_to_[a-z]+|block_(double_)?compressed_(stone|obsidian)|grass_(slab|stairs|wall)(_reverse)?)` 列入 storage_ladder。

### F-te-004 | chain=0008 | severity=minor | category=loop-or-cycle
- 位置: A37–A38 `malum:block_of_blazing_quartz` / `blazing_quartz_from_block`（无 rule-tag）
- 问题: 9 石英→方块→9 石英 互逆往返，无净产出（下游 A39 才用石英做 fragment）。单链出现，crafts 占比小。
- 原因: `pattern/storage_ladder` 规则漏判 malum 烈焰石英方块对。
- 建议: 将 `malum:(block_of_blazing_quartz|blazing_quartz_from_block)` 列入 storage_ladder。

### F-te-005 | chain=0016 | severity=minor | category=loop-or-cycle
- 位置: A73–A74 `mekanism:processing/osmium/storage_blocks/from_ingots` / `ingot/from_block`（无 rule-tag）
- 问题: osmium 方块↔锭 9:1 互逆往返，无净产出（A73 出 block、A74 解回 36 锭）。注意 mekanism 其他金属块多已被 storage_ladder 覆盖，此处 osmium 路径遗漏。
- 原因: `pattern/storage_ladder` 规则对 `mekanism:processing/*/storage_blocks/from_ingots` 与 `ingot/from_block` 通配不足。
- 建议: 列入 storage_ladder（建议用通配 `mekanism:processing/[a-z]+/storage_blocks/from_ingots` 与 `mekanism:processing/[a-z]+/ingot/from_block`）。

### F-te-006 | chain=0000 | severity=minor | category=silly-detour
- 位置: A17–A18 `oritech:biosteel_blockblock` / `biosteel_blockblockinv`（无 rule-tag）；同形见 0037 A26–A27、0004(colossal) A70–A79
- 问题: steel 9 锭→`oritech:biosteel_block`→steel 9 锭 的跨模方块往返，净产出为零，纯绕路（下游 A19 仍需要 steel）。colossal 0004 中重复 10 次（A70–A79）。
- 原因: `pattern/storage_ladder` 规则漏判 oritech biosteel 方块对；此处表现为无意义 detour，本质同 F-te-001..005 属未覆盖压缩族。
- 建议: 将 `oritech:biosteel_blockblock(_inv)?` 列入 storage_ladder，并对 steel→block→steel 无净往返做 collapse。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0001 | incomplete | bad | compressed_iron 无标签 no-op 环(A1-A16,depth↑) + exchange 729 级展开 30k+ 废产(crafts 37874) |
| 0000 | incomplete | questionable | 干净 tier 进阶链，仅 A17-A18 oritech biosteel 无意义 detour(minor) |
| 0003 | incomplete | questionable | infinite_logistical_transporter，同族压缩/交换模式（未精读，索引判同型） |
| 0004 | colossal | questionable | 标签化 lapis 环良性(crafts362)，但 A70-A79 含 oritech biosteel 无标签 detour |
| 0006 | incomplete | bad | exchange 20k+ 废产 + prefab compressed_obsidian/grass reverse 无标签环 |
| 0008 | incomplete | bad | exchange 40144 crafts + malum 烈焰石英无标签环 |
| 0010 | colossal | ok | glow_display_case，未见异常 |
| 0011 | incomplete | questionable | infinite_radioactive_waste_barrel，同族压缩/交换模式 |
| 0012 | incomplete | bad | particleinjector crafts=40180，exchange 级废产 |
| 0013 | colossal | ok | diamond_backpack |
| 0014 | incomplete | bad | fusionreactorcore crafts=2281，exchange 级展开 |
| 0016 | incomplete | questionable | vacuum_trap，A73-A74 mekanism osmium 无标签环(minor) |
| 0017 | incomplete | questionable | assembly_laser，A30-A31 compressed_iron 无标签环(major 类) |
| 0018 | colossal | ok | 64k_crafting_storage |
| 0019 | colossal | ok | spatial_io_port |
| 0020 | incomplete | questionable | thermostat_module，含 compressed_iron 无标签环 |
| 0021 | incomplete | bad | chargerhv crafts=468，exchange 级展开 |
| 0022 | incomplete | questionable | spawner_extractor，含 compressed_iron 无标签环 |
| 0023 | incomplete | questionable | cosmic_logistical_transporter，同族压缩/交换模式 |
| 0024 | colossal | questionable | cosmic_pressurized_tube，标签化存储环良性 + 同族交换展开 |
| 0025 | incomplete | questionable | cosmic_thermodynamic_conductor，同族模式 |
| 0026 | incomplete | bad | nuclearboiler crafts=13120，exchange 级展开 |
| 0027 | colossal | ok | gold_aiot |
| 0028 | incomplete | questionable | cosmic_universal_cable，同族模式 |
| 0029 | incomplete | bad | minecartendothermic crafts=13131，exchange 级展开 |
| 0030 | incomplete | bad | stone_aiot crafts=39303，exchange 级废产 |
| 0031 | incomplete | bad | thermometermodule crafts=12775，exchange 级展开 |
| 0032 | incomplete | bad | chemthrower，exchange 级展开 |
| 0033 | incomplete | questionable | mechanical_mixer depth=103(raw_kinds366)，exchange 729 级 fan-out 撑 depth |
| 0034 | incomplete | questionable | cosmic_compressing_factory，同族模式 |
| 0035 | colossal | ok | wireless_crafting_terminal |
| 0036 | incomplete | bad | digital_miner crafts=1296，exchange 级展开 |
| 0037 | giant | questionable | sample_drill 干净，仅 A26-A27 oritech biosteel detour(minor) |
| 0038 | incomplete | questionable | air_cannon，含 compressed_iron 无标签环 |
| 0039 | colossal | ok | survey_tools |
| 0040 | incomplete | questionable | cosmic_smelting_factory，同族模式 |
| 0041 | incomplete | questionable | absolute_injecting_factory，同族模式 |
| 0042 | incomplete | questionable | cosmic_sawing_factory，同族模式 |
| 0043 | incomplete | questionable | netherite_upgrade，同族模式 |
| 0044 | incomplete | questionable | cosmic_combining_factory，同族模式 |
| 0045 | incomplete | questionable | supreme_purifying_factory，同族模式 |
| 0046 | incomplete | questionable | cosmic_enriching_factory，同族模式 |
| 0047 | colossal | ok | railgun |
| 0048 | incomplete | questionable | cosmic_crushing_factory，同族模式 |
| 0049 | incomplete | questionable | weighted_ejector depth=103，exchange 撑 depth |
| 0050 | incomplete | bad | mossy_stone_brick_wall，A19-A24 prefab compressed_stone 无标签环 |
| 0051 | incomplete | bad | reinforced_mossy_stone_brick_wall，同 0050 prefab 环 |
| 0052 | incomplete | questionable | vacuum_module，含 compressed_iron 无标签环 |
| 0053 | incomplete | bad | reinforced_mossy_stone_brick_stairs，prefab 环 |
| 0054 | incomplete | bad | mossy_stone_brick_slab，prefab 环 |
| 0055 | incomplete | bad | mossy_stone_brick_stairs，prefab 环 |
| 0056 | incomplete | bad | reinforced_mossy_stone_brick_slab，prefab 环 |
| 0057 | incomplete | questionable | item_farm_advanced，同族模式 |
| 0058 | long | ok | mossy_red_rock_brick_stairs |
| 0059 | incomplete | questionable | blastfurnace_preheater，同族模式 |
| 0060 | long | ok | mossy_red_rock_brick_wall |
| 0061 | incomplete | bad | cannon_boat_oak crafts=23324，A60-A75 compressed_iron 无标签环(16 级) |

> 其余未逐条精读的 incomplete 链（0002/0005/0007/0009/0011/0014/0015/0021/0026/0029/0031/0032/0036/0043 等）经 index 与 grep 判定同属"标签化 exchange 729 级展开 + 偶发未覆盖压缩环"型，无独立 critical 发现。

## 规则建议（给汇总者）
- targetRegex: 本批无需新增目标排除（targetBlacklist=decompress 已生效；infinite/cosmic/absolute/supreme 系真实可产 tier，不应排除）。
- recipeRegex（标记收敛"未覆盖压缩族"，证据见上）:
  - `pneumaticcraft:compressed_iron_(block_from_ingot|ingot_from_block)` —— 7 链(0001/0016/0017/0020/0022/0038/0052)
  - `prefab:(compressed_(stone|obsidian|glowstone)|compressed_[a-z]+_to_[a-z]+|block_(double_)?compressed_(stone|obsidian)|grass_(slab|stairs|wall)(_reverse)?)` —— grass reverse 触 12 链
  - `malum:(block_of_blazing_quartz|blazing_quartz_from_block)` —— 0008
  - `mekanism:processing/[a-z]+/storage_blocks/from_ingots` 与 `mekanism:processing/[a-z]+/ingot/from_block` —— 0016
  - `oritech:biosteel_blockblock(_inv)?` —— 0000/0037/0004
- builtin 候选: **扩展 `pattern/storage_ladder` 为对"任意 mod 方块↔锭/块、及 slab-stairs-wall 反向对"的通配识别**，而非仅原版+少数已知 mod。当前对 pneumaticcraft 压缩铁 / prefab 压缩石·黑曜石·草方块反向 / malum 烈焰石英 / mekanism-osmium / oritech biosteel 全部漏判，导致它们以无标签 no-op 往返环插入、放大 depth，并间接制造出"已带标签仍失控"的 exchange 729 级废产级联。
- 无新模式时写"无"：**有**——exchange 横向展开失控属"已带标签仍失控"，建议在收敛层对 exchange 的并行副本数与 9³ 输入做 cap（见 F-te-002）。
- 已知规划器级问题（一句话，不展开为发现）: `|+|N` id 后缀污染（create:cut_andesite_brick_stairs|+14、minecraft:magenta_stained_glass|+16、minecraft:emerald_ore|+2 等贯穿多链）；UNRESOLVED 多为 `reason=BUDGET_TRUNCATED|RANK_TRUNCATED` 的原版常见物（raw_iron/stick/iron_ingot 等）属"原版常见物预算截断"；无净产出往返环机制本身、32 位溢出哨兵、背包升级物品被当机器等本轮未见直接证据。
