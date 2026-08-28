# lost-world batch-003 审查报告
- pack: lost-world
- 批次: batch-003（written=150；类别分布 incomplete 27 / colossal 4 / giant 2 / medium 9 / long 108）
- 审查链数: 150（incomplete 27 / colossal 4 / giant 2 / medium 9 全文精读；long 108 经 untagged-EXECUTE 信号 grep + 抽样详读，netherwood 震荡在 long 文件出现 105 次覆盖绝大多数 long 链；fence/sign/gate 类 long 均带 pattern/exchange 标签判 ok）
- 发现: critical 0 / major 3 / minor 0

## 已知规划器级问题（仅链级一句带过，不展开为发现）
本批 `incomplete`/`colossal`/`giant` 大量链被以下已确认问题主导，按 round-3 口径不单独报告：
- 32 位溢出哨兵：`x2147483647`（Integer.MAX_VALUE）驱动 leather 系无净产出回收环（CHAIN 0000/0005/0008/0119 等）。
- `|+N` 物品 id 后缀污染：`acacia_planks|+24`、`netherwood_log|+3`、`netherwood_stick|+1`、`cobbled_deepslate|+2`、`dark_oak_chest|+10`、`white_sand|+5`、`infested_stone|+14` 等，遍布各链输入/RAW。
- 背包升级物品被当机器：`sophisticatedbackpacks:crafting_upgrade` / `smithing_upgrade` 作为 EXECUTE 机器（所有 backpack upgrade 链）。
- 原版常见物预算截断：`[UNRESOLVED] ... reason=BUDGET_TRUNCATED`（stone_axe / iron_ingot / redstone / diamond / raw_iron / raw_gold 等，INCOMPLETE 链普遍）。
- 无净产出往返环机制本身（recycling / storage_ladder 标签已覆盖）。
- smithing_trim 破映射（netherite 链 0136-0140 经 `wild_armor_trim_smithing_template` 等）。

## 发现
### F-lost-world-1 | chain=0000,0005,0008,0119 | severity=major | category=fake-production
- 位置: target `ftbquests:screen_7`(0000) / `ftbquests:task_screen_configurator`(0005) / `ftbquests:screen_5`(0008) / `ftbquests:loot_crate_opener`(0119)；步骤 CHAIN 0005 [A317]→[A318]
- 问题: FTB Quests 的 screen / task_screen / loot_crate_opener 是任务放置/奖励/战利品实体，玩家不可正常合成（链内 `recipe: ftbquests:screen_7 | system: minecraft:crafting | tier: uncovered` 为规划器臆测）。CHAIN 0005 中规划器用 `sophisticatedbackpacks:crafting_upgrade` 合成 `ftbquests:screen_1`（in `minecraft:book x18` + `minecraft:stone x144`，out `ftbquests:screen_1 x18`）再合成 `ftbquests:screen_3`（[A317]-[A318]），即把任务展示块当可制作物品执行。为"够到"该不可生产目标，整链退化成 199/184 层深的溢出回收环，链整体不可用。
- 原因: 目标选择怪象——任务系统展示物被选为生产目标；分类器未识别 ftbquests 展示/奖励页语义，规划器以 `uncovered` 层级伪造合成步骤。
- 建议: 将 `ftbquests:` 的 screen/task_screen/loot_crate 类加入 targetBlacklist（见末尾 targetRegex），避免其作为生产目标。

### F-lost-world-2 | chain=0112,0004,0136,+systemic(long 文件 105 处) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0112 [A1]-[A6]/[A10]-[A16]/[A18]-[A24]；CHAIN 0004 [A274]-[A280]；CHAIN 0136 [A1]-[A6]
- 问题: `silentgear:netherwood_wood → netherwood_planks → netherwood_stairs → netherwood_planks_from_stairs → netherwood_planks → netherwood_slab → netherwood_planks_from_slabs → netherwood_planks` 是一段无净产出的往返环（回到 planks）。CHAIN 0112 中该环产出的 netherwood_planks 被遗弃——下游真正做 `minecraft:stick`（[A7]/[A8]）消耗的是 `minecraft:acacia_planks|+24`，而非 netherwood planks；netherwood_stick 实为 RAW 输入。即整段 netherwood 木块处理是孤儿输出、纯 no-op 震荡。该震荡未带任何 rule-tag（非 exchange/storage_ladder/recycling），且在 long 文件出现 105 次，影响绝大多数 fence/lever 等长链；连 netherite 链（0136）也前置了同一段孤儿震荡。
- 原因: 规划器在需要 stick 时错误地插入 silentgear netherwood 木块往返（wood↔planks↔stairs↔slab），实际 stick 由 acacia_planks 产出；属未被任何标签覆盖的 silentgear 振荡配方族。
- 建议: 将 silentgear netherwood 木块互转（planks_from_stairs / planks_from_slabs / slab / stairs / wood / planks）收敛为振荡排除族（见末尾 recipeRegex）。

### F-lost-world-3 | chain=0055(+colossal),0000,0049 | severity=major | category=loop-or-cycle
- 位置: CHAIN 0055(及 colossal 0143/0146/0147) `silentgear:fluffy_fabric ⇄ fluffy_puff_from_fabric` [A139]-[A152]+；CHAIN 0000 `silentgear:leather_scrap ⇄ leather_from_scraps`（line ~719-743）；CHAIN 0049 `silentgear:diamond_shard ⇄ diamond_from_shards` [A96]-[A97]
- 问题: 与上述同族的 silentgear 纯 no-op 环。`fluffy_fabric x32 → fluffy_puff x128 → fluffy_fabric x32 → …` 重复数十次且无任何消耗/净产出（colossal 链中该段落完全自循环）；`leather_scrap x18 → x162 → x18`、`diamond_shard x2 → x18 → x2` 亦为互逆往返、数量放大无净收益。三者均不带 rule-tag。
- 原因: silentgear 的 convert/salvage 互转配方被规划器当作可循环放大步骤，实为无净产出环（与已排除的 intent/recycling 不同源，属独立的 silentgear 振荡族）。
- 建议: 与 F-2 一并收敛到 recipeRegex；建议新增 builtin 标签 `pattern/oscillation` 覆盖 silentgear 系（netherwood / fluffy / leather_scrap / diamond_shard）互转环。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | bad | ftbquests screen_7 为目标 + 溢出回收环，链不可用（F-1） |
| 0005 | incomplete | bad | ftbquests task_screen_configurator，伪造合成 screen_1/3（F-1） |
| 0008 | incomplete | bad | ftbquests screen_5 为目标，溢出回收环（F-1） |
| 0119 | incomplete | bad | ftbquests loot_crate_opener 为目标，溢出回收环（F-1） |
| 0001-0008,0023,0047-0054,0138 | incomplete | questionable | sophisticatedbackpacks 升级链，背包升级当机器 + 溢出/截断已知问题主导 |
| 0049 | incomplete | questionable | 含 diamond_shard 纯 no-op 环 + lolmcv `|+10` 污染转换（F-3；`|+N` 已知） |
| 0089 | incomplete | questionable | smooth_stone_stairs，截断/震荡已知问题 |
| 0100 | incomplete | questionable | writable_book，溢出回收环已知问题 |
| 0112 | incomplete | bad | lever 链插孤儿 netherwood 震荡（F-2） |
| 0136-0140 | incomplete | questionable | netherite 系 + smithing_trim；前置 netherwood 孤儿震荡（F-2）+ 已知截断/trim 问题 |
| 0112 同族 netherwood 震荡 | long | bad | netherwood 木块互转 no-op 震荡，long 文件 105 处（F-2） |
| 0055,0143,0146,0147 | colossal | questionable | 含 fluffy 纯 no-op 环（F-3）+ storage_ladder 标签已覆盖的放大 |
| 0142,0145 | giant | questionable | 染色玻璃链，lapis 存储梯震荡标签已覆盖；含 `white_sand|+5` 污染（已知） |
| 0009,0026,0039,0040,0044,0046,0071,0076,0148 | medium | ok | 短栅栏/玻璃，直接配方 |
| 其余 long（fence/sign/gate/boat） | long | ok | block_variants / biomesoplenty 变体，pattern/exchange 标签已覆盖 |

## 规则建议（给汇总者）
- targetRegex: `^ftbquests:(screen_[0-9]+|task_screen_configurator|loot_crate_opener)$` —— 排除 FTB Quests 展示/奖励/战利品实体作为生产目标；证据 CHAIN 0000/0005/0008/0119 共 4 条。
- recipeRegex: `^silentgear:(netherwood_(planks_from_stairs|planks_from_slabs|slab|stairs|wood|planks)|(leather_scrap|leather_from_scraps|diamond_shard|diamond_from_shards)|fluffy_(fabric|fluffy_puff_from_fabric|puff|fabric_from_block|block_base|wool|string))$` —— 收敛 silentgear 无净产出振荡族；证据 CHAIN 0112/0004/0136(long 105 处)/0055/0000/0049。
- builtin 候选: 新增 `pattern/oscillation` 标签，覆盖 silentgear 系（netherwood 木块互转、fluffy_fabric⇄puff、leather_scrap⇄leather、diamond_shard⇄diamond）互逆 no-op 环——它们既非 exchange 亦非 storage_ladder(非 1:9) 亦非 recycling(salvager)；证据同上，跨 ~100 条链。
- 无新模式时写"无"：本批新发现均已归入上述两条规则建议，无额外独立模式。
