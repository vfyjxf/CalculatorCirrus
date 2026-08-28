# farmers-colonies batch-004 审查报告

- pack: farmers-colonies
- 批次: batch-004（written=70；类别分布 giant 19 / medium 18 / short 12 / long 10 / incomplete 7 / trivial 4）
- 审查链数: 70（全量，不足 100 故全读；小类别全读，giant 19 全扫描无异常、incomplete 7 全读，定向 grep 已确认无遗漏）
- 发现: critical 0 / major 1 / minor 0

## 发现

### F-farmers-colonies-1 | chain=0060 | severity=major | category=amount-insanity
- 位置: chains-incomplete-001.txt:1032-1067（A2/A4/A6/A8/A10/A12/A14/A16/A18/A20）`jei:/synthetic/2549…1996` @ `minecraft:anvil`，均带 `[rule-tag: pattern/exchange]`
- 问题: 链目标 `minecolonies:blockhutlumberjack` 的最终配方 `blockhutlumberjackstone` 仅需 `stone_axe x1 + structurize:sceptergold x1 + oak_planks x7`（A28，已成功产出 x2）。但规划器在前面塞入一条铁砧合成链：每个 `jei:/synthetic/NNNN` 输入 `stone_axe x1 + enchanted_book x1`，却输出 `stone_axe x5 / x3 / x2 / x1`（出量随 id 变动）。这是把"铁砧附魔应用"（物品+附魔书→1 个附魔物品）误读成"1→N 倍增"的生产循环。链尾 `[UNRESOLVED] minecraft:stone_axe x1 (candidates=5, reason=CASCADE_EXHAUSTED)`（:1095）——而 `stone_axe` 是 3 圆石+2 木棍即可 trivial 合成的原版物，且链内 RAW 已含 `stone_axe x1`、A25 还做过木棍。
- 原因: 口径文档明列 `quality/junk`（已排除）包含 **anvil/enchantment 泄漏**，但本批这些 `jei:/synthetic/*` @ `minecraft:anvil` 的附魔应用配方未被 junk 排除，反被标成 `pattern/exchange`，形成"假倍增"环路并把规划器候选预算耗尽（CASCADE_EXHAUSTED），使本可平凡合成的 `stone_axe` 残留未解析。
- 建议: 将 `quality/junk` 的 anvil/enchantment 排除扩展到 `jei:/synthetic/<id> @ minecraft:anvil`（附魔应用，输入含 `enchanted_book`）这一族，避免其被当作基础物品的 1→N 复制；证据见 chain 0060（10 处）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | giant | ok | armor trim 模板自复制 = pattern/exchange+storage_ladder，已带标签 |
| 0003 | giant | ok | 同上（wayfinder trim） |
| 0004 | giant | ok | 同上（host trim） |
| 0012-0022 | giant | ok | 11 条 armor trim 模板复制，exchange+storage_ladder 已带标签 |
| 0017 | giant | ok | netherite_upgrade 模板复制，同 exchange+storage_ladder 模式 |
| 0029 | giant | ok | rail = storage_ladder(raw_iron 取量)+常规合成，已带标签 |
| 0030 | giant | ok | cauldron = storage_ladder(raw_iron) 已带标签 |
| 0036 | giant | ok | iron_door = storage_ladder 已带标签 |
| 0043 | giant | ok | smithing_table = storage_ladder 已带标签 |
| 0053 | giant | ok | iron_bars = storage_ladder 已带标签 |
| 0001 | long | ok | note_block = storage_ladder(redstone) 已带标签 |
| 0005 | long | ok | golden_carrot = storage_ladder(raw_gold) 已带标签 |
| 0006 | long | ok | glistering_melon = storage_ladder 已带标签 |
| 0007 | long | ok | powered_rail = storage_ladder 已带标签 |
| 0008 | long | ok | golden_apple = storage_ladder 已带标签 |
| 0010 | long | ok | jukebox = storage_ladder(diamond) 已带标签 |
| 0025 | long | ok | campfire = storage_ladder(coal)+常规，已带标签 |
| 0046 | long | ok | white_candle = storage_ladder(bone) 已带标签 |
| 0050 | long | ok | lead = storage_ladder(slime) 已带标签 |
| 0052 | long | ok | clock = storage_ladder 已带标签 |
| 0002 | medium | ok | shulker_box 常规合成 |
| 0011 | medium | ok | chiseled_bookshelf 常规 |
| 0023 | medium | ok | ladder 常规 |
| 0024 | medium | ok | composter 常规 |
| 0026 | medium | ok | daylight_detector 常规 |
| 0027 | medium | ok | soul_campfire 常规（RAW 多样为 #log 全展开+`|+N` 已知污染） |
| 0028 | medium | ok | grindstone 常规 |
| 0031 | medium | ok | andesite_stairs stonecutting 常规 |
| 0032/0037/0039/0040/0044/0048/0049/0051 | medium | ok | 各 fence_gate 常规合成 |
| 0045 | medium | ok | bamboo_fence_gate 常规 |
| 0047 | medium | ok | mushroom_stew 常规 |
| 0034 | short | ok | cartography_table 常规（`acacia_log|+3`/`oak_planks|+10` 已知污染） |
| 0038 | short | ok | tinted_glass 常规 |
| 0054/0056 | short | ok | diorite_stairs/wall stonecutting 常规 |
| 0055 | short | ok | yellow_candle 常规 |
| 0058/0059 | short | ok | domum brick_extra 常规 |
| 0062-0064/0067/0069 | short | ok | domum 砖/沙砖 常规 |
| 0061/0065/0066/0068 | trivial | ok | minecolonies 料理 常规 |
| 0009 | incomplete | questionable | 唯一 UNRESOLVED=`black_wool|+14`：#wool→black_wool 基底坍缩+`|+N` 后缀污染（已知规划器问题），目标 painting 其余已解析 |
| 0033 | incomplete | questionable | 唯一 UNRESOLVED=`black_wool|+14`，同上根因（white_banner） |
| 0035 | incomplete | questionable | 唯一 UNRESOLVED=`black_wool|+14`，同上根因（purple_wool；其余染料/染毛网已解析，recolor 已带标签） |
| 0041 | incomplete | questionable | 唯一 UNRESOLVED=`black_bed|+14`，同上根因（purple_bed） |
| 0042 | incomplete | questionable | 唯一 UNRESOLVED=`black_wool|+14`，同上根因（purple_carpet） |
| 0057 | incomplete | questionable | 唯一 UNRESOLVED=`black_wool|+14`，同上根因（blockconstructiontape） |
| 0060 | incomplete | bad | 见 F-farmers-colonies-1：anvil `jei:/synthetic/*` 附魔应用被标 exchange 形成假倍增，stone_axe CASCADE_EXHAUSTED |

## 规则建议（给汇总者）

- targetRegex: 无新增目标排除。
- recipeRegex: 无新增收敛配方族。
- builtin 候选:
  - **（新增）** `jei:/synthetic/<数字> @ minecraft:anvil` 且其输入含 `minecraft:enchanted_book` 的附魔应用配方 → 建议归为 `quality/junk`（anvil/enchantment 泄漏）而非 `pattern/exchange`。当前未命中既有 anvil/enchantment 排除，造成 chain 0060 的假 1→N 复制与 CASCADE_EXHAUSTED；证据 10 处。
  - `black_wool|+14` / `black_bed|+14` 等带 `|+N` 后缀的 id：属已知 `#wool`/`#bed` any-of 基底坍缩 + id 后缀污染（非新配方族），导致 0009/0033/0035/0041/0042/0057 共 6 条 incomplete。建议（规划器侧）在 id 归一化阶段剥离 `|+N` 并正确映射 #wool→可合成 base，使这些 trivial 物不再 UNRESOLVED。本批不视为新发现，链级一句话已记录。
- 无新模式：本批除 chain 0060 的 anvil 合成配方过滤缺口外，其余全部落入已固化标签（storage_ladder / exchange / recolor / 已知 #wool 基底坍缩 + `|+N` 污染）。该包路径质量已高度收敛。
