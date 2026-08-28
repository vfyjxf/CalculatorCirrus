# create-oneblock batch-004 审查报告
- pack: create-oneblock
- 批次: batch-004（written=99，类别分布：incomplete 99 / 0 其他；全部为 incomplete）
- 审查链数: 99（incomplete 99；全量扫描 builtin-rule-tags 确认 99 条均带 pattern/exchange；重点精读 14 条 mega-wide/异常 + 抽样 createdeco 砖块变体族）
- 发现: critical 0 / major 0 / minor 0

## 结论速读
本批**无新模式**。99 条链全部落在已固化标签（`pattern/exchange`、`pattern/storage_ladder`、`intent/recycling`、`pattern/recolor`）+ round-4 已知规划器级问题之内。残留的 amount-insanity 与 UNRESOLVED 全部可溯源到 32 位溢出哨兵、`|+N` id 后缀污染、any-of 基底坍缩（#planks/#doors/#cabinets 全展开）、可再生物放大（rice_panicle），均属已知规划器问题清单，无新增可疑配方族/目标类。该包在本轮可视作已收敛。

## 发现
（本轮无 critical/major/minor 发现。以下为已确认"非新"的残留现象，引用链内证据，仅供汇总者判断是否需要规划器层修复。）

### 残留现象 R-1 | 32 位溢出哨兵驱动 crafts 数量级失控
- 位置: 多条 mega-wide/colossal 链 EXECUTE 行，如 CHAIN 0017 `[A92] minecraft:diamond_from_blasting_deepslate_diamond_ore x2147483647`
- 证据: 全文件 `2147483647` 出现 285 次；CHAIN 0017 crafts=5072800565、0030 crafts=255321462925、0022 crafts=22153843004 等均由该哨兵放大。
- 判定: 已知规划器级问题（round-4 清单"32 位溢出哨兵"），非新模式，不在审查层修复。

### 残留现象 R-2 | any-of 基底坍缩（#planks/#doors/#cabinets 全展开）
- 位置: CHAIN 0009 RAW 尾 `acacia_planks x40, bamboo_planks x50, ... spruce_planks x50`（11 种木头全展开）；CHAIN 0080/0096 UNRESOLVED `acacia_door|+9 … cherry_door|+9`、`acacia_cabinet … spruce_cabinet`。
- 判定: 已知规划器级问题（round-4 清单"any-of 基底坍缩"），非新模式。

### 残留现象 R-3 | 可再生物过度放大（rice_panicle）
- 位置: CHAIN 0080 UNRESOLVED `farmersdelight:rice_panicle x56623131`；CHAIN 0096 `x113246262`（reason=CASCADE_EXHAUSTED），均经 `farmersdelight:integration/create/milling/rice_panicle` 放大。
- 判定: 规划器对可再生农作物的需求放大（baseline 无净产出往返机制项），属已知规划器级问题，非新模式。

### 残留现象 R-4 | `|+N` id 后缀污染进入 UNRESOLVED
- 位置: 普遍，如 CHAIN 0000 `[UNRESOLVED] create:small_limestone_bricks|+14 x1`、CHAIN 0080 `minecraft:acacia_door|+9 x8192`。
- 判定: 已知规划器级问题（round-4 清单"`|+N` id 后缀污染"），非新模式。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | pattern/exchange | ok | createdeco 砖块变体，exchange x49 已覆盖；UNRESOLVED 为小石灰砖`|+14`/brick（已知后缀+截断） |
| 0009 | pattern/exchange+storage+recycling | questionable | 真实 display_link 合法；RAW 暴露 #planks 全展开（R-2） |
| 0011 | exchange+storage+recycling | questionable | everlasting_upgrade 合法；crafts 3M 由哨兵放大（R-1） |
| 0013 | exchange+storage+recycling | questionable | bacon_and_eggs 合法；哨兵/放大驱动 crafts 21M（R-1） |
| 0014 | exchange+storage+recycling | questionable | diamond_backpack 合法；crafts 146M 由放大驱动（R-1/R-2） |
| 0017 | exchange+storage+recycling | questionable | 关键证据 `diamond x2147483647` → crafts 5B（R-1）；非新 |
| 0022 | exchange+storage+recycling | questionable | large_water_wheel 合法；哨兵 x20 驱动 crafts 22B（R-1） |
| 0030 | exchange+storage+recycling | questionable | pumpkin_pie_slice 合法；哨兵 x168 驱动 crafts 255B（R-1） |
| 0037 | pattern/exchange | ok | `mystery_conversion`/`conversion_0` 已被标 pattern/exchange；lapis 哨兵驱动（R-1） |
| 0039 | exchange+storage+recycling | questionable | blue_andesite_lamp 合法；哨兵 x20 驱动 crafts 19B（R-1） |
| 0052 | exchange+recolor+storage | questionable | empty_andesite_chunk_loader 合法；哨兵驱动 crafts 20B（R-1） |
| 0063 | exchange+recolor+storage | questionable | package_frogport 合法；哨兵 x25 驱动 crafts 22B（R-1） |
| 0080 | exchange+storage+recycling | questionable | cooking_pot 合法；rice_panicle 放大 56M + 全木头门/柜展开（R-2/R-3） |
| 0096 | exchange+storage+recycling | questionable | rice_roll_medley_block 合法；rice_panicle 放大 113M（R-3/R-2） |
| 0001–0008, 0060–0098 等 createdeco 砖块族 | pattern/exchange | ok | 抽样确认均为砖块/楼梯/墙/台阶变体，exchange 已覆盖，无新 |

> 其余未单列链（0002–0008、0010、0012、0015–0036、0038–0049、0051–0062、0064–0079、0081–0095、0097–0098）全部经 builtin-rule-tags 扫描确认带 `pattern/exchange`（多数 x44–x110）+ 其余已覆盖标签，结构与 0000/0003 同族，判定 ok。

## 规则建议（给汇总者）
- targetRegex: 无新增。本批目标类（createdeco 砖块族、sophisticatedbackpacks 升级/背包、create 机械/物流、farmersdelight 食物、create_power_loader 块）均落在已覆盖族或已知规划器问题内。
- recipeRegex: 无新增收敛需求。`jei:category/create/mystery_conversion`(`conversion_0`)、`farmersdelight:integration/create/milling/rice_panicle` 等已由分类器标为 pattern/exchange / recycling，无需新增。
- builtin 候选: **无新模式**。残留异常（amount-insanity / UNRESOLVED）100% 溯源至 round-4 已知规划器级问题：32 位溢出哨兵 `x2147483647`（285 处）、`|+N` id 后缀、`#planks/#doors/#cabinets` any-of 全展开、可再生物 rice_panicle 放大。建议在**规划器层**修复（哨兵归零、tag 展开收敛、可再生物封顶），而非新增审查过滤器——新增过滤器只会重复已覆盖行为。
- 收敛评估: 本批相对 round-1..3 无新增可疑族，符合"已收敛"判定。建议停手或仅做规划器层修复后复测。
