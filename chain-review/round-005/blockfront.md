# BlockFront - Official Mod Pack 0.9.0.24b batch-005 审查报告

- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-005（written=41；long 6 / giant 11 / medium 17 / short 7；无 incomplete 文件）
- 审查链数: 41（incomplete 0 / complete 41；全量读取 chains-giant/long/medium/short 四个文件，覆盖 index.tsv 全部 41 行；超出 ≥80 配额下限因本批仅 41 条）
- 发现: critical 0 / major 0 / minor 0

## 发现

**无新模式** —— 审查层收敛。

本批 41 条链全部为纯 vanilla 配方（`system: minecraft:*` 或 stonecutting/furnace/blast_furnace），机器仅 `crafting_table / blast_furnace / stonecutter / furnace`，无任何 `jei:`、mod 机器或生成类配方。所有 EXECUTE 步骤已被既有规则覆盖：

- `pattern/storage_ladder`：出现 278 次（diamond/raw_gold/raw_iron/coal/redstone/slime 等 block↔ingot 放大，用于凑 9 的倍数以喂给金/铁/钻甲与工具）。
- `pattern/exchange`：出现 12 次，全部落在 `coast_/snout_/rib_armor_trim_smithing_template` 的"复制"配方（1 模板+7 钻石+1 圆石→2 模板，vanilla 模板复制）。
- 分类器层面：storage_ladder 42 / exchange 37 / recolor 12 / recycling 5，均属已固化模式。

无 `reason=BUDGET_TRUNCATED|RANK_TRUNCATED`，无 `crafts≥100000`，无 `UNRESOLVED`，无 fake-production，无 free-resource / generation-abuse / tag-expansion / machine-mismatch 等新现象。

例外（均属 round-5 已列规划器级噪声，仅在此链级一句话提及，不计入新发现）：
- `|+N` id 后缀污染（如 `stripped_acacia_log|+3`、`crimson_planks|+10`、`dark_oak_log|+39`）出现在多数 wood→planks→stick/fence_gate 链，属 any-of 基底坍缩的副产物。
- any-of 全展开：木/木板/台阶/原木在多链 RAW 区被全量枚举（如 CHAIN 0007 raw_kinds=51、0013 raw_kinds=51），为 planner 任意选定一种木材后展开所有同义项。
- `smithing_trim 破映射`：armor_trim 链（0001/0002/0003）依赖复制配方，需 1 个种子模板作为 RAW 输入（链内 `coast_armor_trim_smithing_template x1` 无任何 `<= from`），即"先有模板才能复制"，规划器将种子当作原材料——属已知 smithing_trim 映射问题，非新配方族。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | long | ok | 金胸甲，raw_gold↔block 放大+blasting，已覆盖 |
| 0001 | giant | ok | coast 模板复制，需种子模板为 RAW（smithing_trim 已知问题）；diamond 阶梯放大 |
| 0002 | giant | ok | snout 模板复制，同上 |
| 0003 | giant | ok | rib 模板复制，同上 |
| 0004 | giant | ok | 铁护腿，raw_iron↔block 放大+blasting |
| 0005 | medium | ok | 梯子，wood→planks→stick 含 any-of/|+N |
| 0006 | medium | ok | 堆肥桶，slab 任意木种 any-of 坍缩 |
| 0007 | long | ok | 营火，coal 阶梯放大；RAW 51 为 any-of 全展开 |
| 0008 | medium | ok | 磨制安山岩台阶，stonecutting 直线链 |
| 0009 | medium | ok | 磨制安山岩楼梯，stonecutting 直线链 |
| 0010 | medium | ok |  daylight 探测器，glass+quartz+slab 正常 |
| 0011 | short | ok | 可写书，leather/paper/book 正常链 |
| 0012 | short | ok | 蜂巢，honeycomb+crimson_planks any-of |
| 0013 | medium | ok | 灵魂营火，RAW 51 为 any-of 全展开 |
| 0014 | long | ok | 金护腿，raw_gold 阶梯放大 |
| 0015 | giant | ok | 铁轨，raw_iron 阶梯放大（正常巨链） |
| 0016 | giant | ok | 炼药锅，raw_iron 阶梯放大 |
| 0017 | giant | ok | 盾牌，raw_iron 阶梯放大 |
| 0018 | medium | ok | 安山岩楼梯，stonecutting 直线链 |
| 0019 | medium | ok | 黑橡栅栏门，any-of/|+N |
| 0020 | short | ok | 制图台，paper+planks any-of |
| 0021 | giant | ok | 铁门，raw_iron 阶梯放大 |
| 0022 | medium | ok | 橡木栅栏门，any-of/|+N |
| 0023 | short | ok | 染色玻璃，glass+amethyst 正常 |
| 0024 | medium | ok | 金合欢栅栏门，any-of/|+N |
| 0025 | medium | ok | 丛林栅栏门，any-of/|+N |
| 0026 | giant | ok | 锻造台，raw_iron 阶梯放大 |
| 0027 | medium | ok | 红树栅栏门，any-of/|+N |
| 0028 | medium | ok | 竹栅栏门，bamboo_block→planks 正常 |
| 0029 | medium | ok | 蘑菇煲，bowl+蘑菇 正常 |
| 0030 | medium | ok | 樱桃栅栏门，any-of/|+N |
| 0031 | medium | ok | 白桦栅栏门，any-of/|+N |
| 0032 | long | ok | 拴绳，slime_block↔ball 放大+string |
| 0033 | medium | ok | 云杉栅栏门，any-of/|+N |
| 0034 | long | ok | 时钟，redstone+raw_gold 阶梯放大 |
| 0035 | giant | ok | 铁栅栏，raw_iron 阶梯放大（16 段） |
| 0036 | short | ok | 闪长岩楼梯，stonecutting 直线链 |
| 0037 | short | ok | 黄蜡烛，candle+yellow_dye 正常 |
| 0038 | short | ok | 闪长岩墙，stonecutting 直线链 |
| 0039 | giant | ok | 铁斧，raw_iron 阶梯放大+any-of stick |
| 0040 | long | ok | 金斧，raw_gold 阶梯放大+any-of stick |

## 规则建议（给汇总者）

- 无。本批未出现任何超出前四轮已固化模式的配方族/目标类；所有 vanilla 目标均已被 `pattern/storage_ladder` / `pattern/exchange` 收敛。无需新增 `targetRegex` / `recipeRegex` / builtin 规则。建议维持现有 5 个激活 filter 继续下一轮直至收敛 ≥85% 或覆盖目标达成。
