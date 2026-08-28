# lost-world batch-004 审查报告
- pack: lost-world
- 批次: batch-004（written=100；类别分布 incomplete 42 / short 31 / giant 9 / colossal 8 / long 6 / medium 4）
- 审查链数: 100（incomplete 42 / complete 58；medium/long/short/giant 全读，colossal 全读结构+标签，incomplete 全读链头与 18 条全链抽样，含全部 6 条 mega-wide）
- 发现: critical 0 / major 0 / minor 0（无未被覆盖的新可疑配方族/目标类）

## 残留已知问题（非新问题，规划器级，已带标签仍失控）
本批所有"异常"均为 round-4 已固化标签 / 已知规划器级问题的下游症状，无新家族。证据：

- `intent/recycling` 无净产出往返环被当无界放大器，放出 32 位溢出哨兵量级：
  - CHAIN 0000 `minecraft:netherite_sword`：A37 `minecraft:diamond_leggings x2147483647`（= Integer.MAX_VALUE），整链 `intent/recycling x82 + exchange x5 + storage_ladder x34`，crafts=189291704433（溢出哨兵）。
  - CHAIN 0087 `minecraft:bookshelf`：A1 `leather_leggings x2147483647` ← `leather x15032385529`，皮甲↔皮革 salvage 往返无净产出，却为 1 个书架需求 1.5e10 皮革；[UNRESOLVED] leather x15032385529 (CASCADE_EXHAUSTED)。
  - CHAIN 0094 `minecraft:lodestone`：A1 `jei:/synthetic/4586 x589824`（salvage diamond_boots）`intent/recycling x66`；CHAIN 0089 `observer` `intent/recycling x21 + storage_ladder x198`；CHAIN 0030 `waystones:pink_portstone` `intent/recycling x15`。
  - 注：overflow 哨兵量级（2147483647、~1.5e10、~1.28e10、~8.58e9）贯穿 53 条 BUDGET_TRUNCATED + 258 条 CASCADE_EXHAUSTED 未解析项，全部为 diamond/leather/netherite_ingot 等本可生产物，是被放大后级联失败的假缺料。
- `|+N` id 后缀污染（data-pollution，已知）：`silentgear:netherwood_log|+3`、`minecraft:acacia_planks|+24`、`minecraft:bamboo_planks|+82`、`biomesoplenty:pine_wood|+95`（CHAIN 0092/0098 fine_wood、0083、0077、0030 等）。
- any-of 基底坍缩（已知）：`#planks`/`#logs` 全展开致 raw_kinds 失控——CHAIN 0093 `blueprint_book` raw_kinds=210、0083 raw_kinds=181、0092/0098 raw_kinds=96。
- 全部 1480 处 `pattern/storage_ladder`、796 处 `intent/recycling`、267 处 `pattern/exchange`、68 处 `pattern/recolor` 均已带标签；唯一 `jei:category/` 机器系统为 `silentgear:tool_action`（合法 Silent Gear 工具/部件动作，非 fake-production）。

## 发现
无（本批未出现未被覆盖的新可疑配方族/目标类；该包在 chain-review 层已收敛，残余全部为 round-4 已列规划器级问题）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | bad | 已知 recycling 放大到 Integer.MAX_VALUE 哨兵量级（规划器级） |
| 0001 | colossal | ok | banner=exchange+recolor+storage_ladder 已标签，合法但冗长 |
| 0002 | giant | ok | glass pane=recolor+storage_ladder 已标签 |
| 0003 | giant | ok | stained glass pane，storage_ladder 已标签 |
| 0004 | medium | ok | brown stained glass pane，正常 |
| 0005 | giant | ok | pink stained glass pane，storage_ladder 已标签 |
| 0006 | colossal | ok | flag=exchange+recolor+storage_ladder 已标签 |
| 0007 | medium | ok | red stained glass pane，正常 |
| 0008 | colossal | ok | banner 已标签 |
| 0009 | colossal | ok | flag 已标签 |
| 0010 | colossal | ok | banner 已标签 |
| 0011 | colossal | ok | flag 已标签 |
| 0012 | incomplete | bad | lingering_potion，已知 recycling/overflow 放大 |
| 0013 | short | ok | waxed copper stairs，正常 |
| 0014 | long | ok | copper_grate，storage_ladder x9 已标签 |
| 0015 | short | ok | waxed copper stairs，正常 |
| 0016 | medium | ok | bamboo_mosaic_stairs，正常 |
| 0017 | incomplete | bad | carpet，已知 recycling/overflow 放大 |
| 0018 | short | ok | waxed copper slab，正常 |
| 0019 | medium | ok | bamboo_mosaic_slab，正常 |
| 0020 | incomplete | bad | brush，exchange+storage_ladder 已标签但 incomplete |
| 0021 | short | ok | waxed copper，正常 |
| 0022 | short | ok | waxed copper，正常 |
| 0023 | giant | ok | silentgear chestplate，正常 |
| 0024 | incomplete | bad | tool_swapper_upgrade mega-wide，recycling/overflow 溢出 |
| 0025 | giant | ok | ftbfiltersystem smart_filter，正常 |
| 0026 | long | ok | cut_copper_stairs，storage_ladder x9 已标签 |
| 0027 | incomplete | bad | yellow_carpet，已知 recycling/overflow 放大 |
| 0028 | incomplete | bad | orange_carpet，已知 recycling/overflow 放大 |
| 0029 | long | ok | cut_copper_slab，storage_ladder x9 已标签 |
| 0030 | incomplete | bad | waystones portstone，recycling x15 + 哨兵量级 |
| 0031 | short | ok | brick_slab，正常 |
| 0032 | short | ok | smooth_red_sandstone_stairs，正常 |
| 0033 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0034 | short | ok | cracked polished blackstone brick stairs，正常 |
| 0035 | giant | ok | supplementaries cage，正常 |
| 0036 | short | ok | cut_red_sandstone_wall，正常 |
| 0037 | short | ok | blackstone_tile_slab，正常 |
| 0038 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0039 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0040 | short | ok | cut_orange_sandstone_slab，正常 |
| 0041 | short | ok | ash_bricks_slab，正常 |
| 0042 | incomplete | bad | alchemy_upgrade，已知 recycling/overflow |
| 0043 | short | ok | brick_stairs，正常 |
| 0044 | short | ok | blackstone_tile_wall，正常 |
| 0045 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0046 | short | ok | ash_bricks_stairs，正常 |
| 0047 | short | ok | deepslate_tile_wall，正常 |
| 0048 | short | ok | smooth_white_sandstone_stairs，正常 |
| 0049 | incomplete | bad | stonecutter_upgrade，已知 recycling/overflow |
| 0050 | short | ok | deepslate_tile_stairs，正常 |
| 0051 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0052 | short | ok | cut_black_sandstone_slab，正常 |
| 0053 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0054 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0055 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0056 | short | ok | smooth_white_sandstone_slab，正常 |
| 0057 | incomplete | bad | advanced_magnet_upgrade mega-wide，recycling/overflow 溢出 |
| 0058 | short | ok | smooth_black_sandstone_stairs，正常 |
| 0059 | short | ok | brick_wall，正常 |
| 0060 | short | ok | cracked polished blackstone brick slab，正常 |
| 0061 | short | ok | smooth_orange_sandstone_slab，正常 |
| 0062 | long | ok | waxed_chiseled_copper，storage_ladder x3 已标签 |
| 0063 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0064 | short | ok | smooth_orange_sandstone_stairs，正常 |
| 0065 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0066 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0067 | short | ok | ash_bricks_wall，正常 |
| 0068 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0069 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0070 | incomplete | bad | azure_silver_dust，已知 recycling/overflow |
| 0071 | short | ok | smooth_red_sandstone_slab，正常 |
| 0072 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0073 | short | ok | blackstone_tile_stairs，正常 |
| 0074 | short | ok | smooth_black_sandstone_slab，正常 |
| 0075 | incomplete | bad | blasting_upgrade，已知 recycling/overflow |
| 0076 | incomplete | bad | waystones portstone，已知 recycling/overflow |
| 0077 | giant | ok | tnt_minecart，storage_ladder x16 已标签 |
| 0078 | incomplete | bad | crimson_steel_nugget，已知 recycling/overflow |
| 0079 | incomplete | bad | stack_upgrade_tier_2，已知 recycling/overflow |
| 0080 | incomplete | bad | gold_backpack，已知 recycling/overflow |
| 0081 | colossal | ok | wind_vane，recycling x16+storage_ladder x198 已标签 |
| 0082 | giant | ok | azure_silver_nugget，raw_azure_silver 往返已标签 |
| 0083 | giant | ok | azure_electrum_nugget，storage x4；any-of planks +N 已知 |
| 0084 | long | ok | silentgear setting，bort 往返已标签 |
| 0085 | colossal | ok | soul_lantern，storage_ladder x32 已标签 |
| 0086 | incomplete | bad | supplementaries bomb，已知 recycling/overflow |
| 0087 | incomplete | bad | bookshelf，recycling 放出 1.5e10 leather 哨兵量级 |
| 0088 | incomplete | bad | mossy_stone_brick_slab，已知 recycling/overflow |
| 0089 | incomplete | bad | observer mega-wide，recycling x21+storage x198 哨兵量级 |
| 0090 | incomplete | bad | supplementaries pedestal，已知 recycling/overflow |
| 0091 | incomplete | bad | stone_tile_slab，已知 recycling/overflow |
| 0092 | short | ok | fine_wood_stairs，any-of planks 全展开 +N 已知（叶子噪声） |
| 0093 | incomplete | bad | blueprint_book mega-wide，raw_kinds=210 any-of 坍缩+overflow |
| 0094 | incomplete | bad | lodestone mega-wide，recycling x66 放大到 589824 哨兵量级 |
| 0095 | incomplete | bad | stone_tile_wall，已知 recycling/overflow |
| 0096 | incomplete | bad | stone_tile_stairs，已知 recycling/overflow |
| 0097 | long | ok | fire_pit，storage_ladder x8 已标签 |
| 0098 | short | ok | fine_wood_slab，any-of planks 全展开 +N 已知（叶子噪声） |
| 0099 | incomplete | bad | polished_granite_stairs，已知 recycling/overflow |

## 规则建议（给汇总者）
- 无新模式（targetRegex / recipeRegex / builtin 候选均无新增）。
- 收敛结论：本批 chain-review 层已收敛，58 条 complete 链路径合法（仅 any-of/+N 叶子噪声，属规划器级），42 条 incomplete 全部由已知 `intent/recycling` 无净产出往返放大 + 32 位溢出哨兵 + `|+N` 后缀污染 + any-of 坍缩导致，均为 round-4 已列规划器级问题，无未被覆盖的新配方族/目标类。
- 唯一可升级建议（规划器层，非新 chain 规则）：`intent/recycling` 标签已带但未能约束放大——salvage 往返环被当作无界放大器放出 Integer.MAX_VALUE 量级计数，建议规划器侧对 loop=allowBoundedAmplification 增加硬性上界并丢弃溢出哨兵计数，而非新增过滤规则。
