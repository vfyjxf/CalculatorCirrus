# BlockFront - Official Mod Pack 0.9.0.24b batch-004 审查报告

- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-004（written=59；类别分布 short 32 / giant 11 / long 11 / medium 3 / incomplete 2）
- 审查链数: 59（全量，因总量不足 100；incomplete 2 / complete 57）
- 发现: critical 0 / major 0 / minor 1

## 发现

### F-blockfront-001 | chain=0036 | severity=minor | category=loop-or-cycle
- 位置: CHAIN 0036，A1–A8（minecraft:dried_kelp ⇄ minecraft:dried_kelp_block @ crafting_table，9:1 压缩↔解压，循环 4 轮）
- 问题: 目标 `minecraft:dried_kelp_block` 只需 `dried_kelp x9 → dried_kelp_block`（1 步），规划器却走了 9 步的 `block→9 kelp→block→…` 往返放大环，depth=8 / actions=9 / crafts=8，且链头 **无** `builtin-rule-tags` 行——即该 9:1 压缩族未被 `pattern/storage_ladder` 正则捕获。
- 原因: 现有 `pattern/storage_ladder` 覆盖清单（含 FD rice/straw/canvas/crate、wheat 等）漏掉了原版 `dried_kelp` / `dried_kelp_block` 这一对 9:1 食品/植物块压缩族，导致它既未打标签、也未进入收敛过滤器，在行为上等同于其它已处理的压缩环。本批其它所有压缩环（gold/raw_gold、diamond、emerald、redstone、raw_copper、copper、lapis、bone）均已正确带 `storage_ladder` 标签。
- 建议: 将 `dried_kelp` / `dried_kelp_block` 并入 `pattern/storage_ladder` 正则（或新增 `storage_blocks/from_dried_kelp` 规则），使其与其它 9:1 压缩族一致被排除/收敛。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0001 | incomplete | questionable | `black_bed\|+14` 因 RANK_TRUNCATED 未解析；`\|+14` 为已知 id 后缀污染（any-of 基底坍缩），非新家族 |
| 0048 | incomplete | questionable | `black_wool\|+14` 同因 RANK_TRUNCATED 未解析；`\|+14`/`\|+15`/`\|+10`/`\|+3` 后缀污染贯穿本批，已知规划器级问题 |
| 0036 | loop-or-cycle | questionable | 见 F-blockfront-001：未带标签的 dried_kelp 9:1 压缩环（唯一新遗漏） |
| 0038,0039,0042,0043,0051–0055,0058 | exchange+storage_ladder | ok | 盔甲纹样模板复制（diamond/terracotta 压缩环放大），已带 exchange + storage_ladder 标签，净产出，属已处理模式 |
| 0003 | storage_ladder | ok | raw_copper→cut_copper_stairs 压缩环放大，已带标签，raw_kinds=0 自洽 |
| 0041,0050,0057 | craft | ok | shulker_box / chiseled_bookshelf / barrel，普通合成；RAW 含 `\|+N` 后缀叶（stripped_acacia_log\|+3、crimson_planks\|+10 等），已知污染，非新家族 |
| 0034,0035,0037,0040,0044–0049,0056 | storage_ladder | ok | 金/绿宝石/红石/观察者/笔记块/金胡萝卜/金苹果/唱片机/附魔台等，压缩环已带标签，正常 |
| 其余 short（0000,0002–0033） | craft/stonecut | ok | 全部为原版石切/合成装饰方块（slab/stairs/wall/button 等），最短路径，无异常 |

## 规则建议（给汇总者）

- targetRegex: 无新增目标类需排除（本批目标均为原版 minecraft:* 物品，无 mod 专有物、无任务/交易/展示页）。
- recipeRegex: `minecraft:dried_kelp(_block)?` —— 将未带标签的 9:1 压缩解压族补入 `pattern/storage_ladder` 收敛（证据 CHAIN 0036，1 条）。
- builtin 候选: `pattern/storage_ladder` 扩展项 `dried_kelp`/`dried_kelp_block`（建议标签 storage_ladder）。证据：CHAIN 0036（链头无 rule-tag，contrast 同批 0034/0035/0037 等均已带标签）。
- `|+N` id 后缀污染：本批在 0001(`black_bed|+14`)、0048(`black_wool|+14`)、0041/0050/0057(`stripped_acacia_log|+3`、`crimson_planks|+10`、`green_wool|+15`) 等多处出现，是已知规划器级问题（any-of 基底坍缩 + 后缀污染），导致 2 条 incomplete 链 RANK_TRUNCATED；非新家族，按既有口径一句话提及即可，建议上游清理 id 规范化而非在链层排除。
- 无其它新模式：本包（看似纯原版工艺 + 盔甲纹样复制）已高度收敛，新增发现仅上述 1 例 storage_ladder 子集遗漏。
