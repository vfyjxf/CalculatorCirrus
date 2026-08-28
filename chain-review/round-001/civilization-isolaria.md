# civilization-isolaria batch-001 审查报告
- pack: civilization-isolaria
- 批次: batch-001（written=150；类别分布：incomplete 146 / mega-wide 2 / colossal 2；无 trivial/short/medium/long/giant 文件）
- 审查链数: 150（incomplete 146 全量信号扫描 + complete 4 全读；colossal/mega-wide 文件 4 条完整读取，incomplete 18.4MB/146≈126KB/条触发大文件抽样下限，实际对所有 146 条做循环/截断信号统计，并对代表链 0000/0009/0018/0024/0046/0065/0094 等抽取区间证据）
- 发现: critical 0 / major 4 / minor 1

## 审查结论摘要
本批 150 条链**全部**带有「无净产出的往返环」特征（150/150 命中循环签名）。根因是 `loop=allowBoundedAmplification` 放行了可逆压缩/解压配方的来回振荡：规划器用 `块↔物品`（redstone_block↔redstone、raw_<金属>_block↔raw_<金属>）以及 steel 的 ingot↔nugget↔storage 互逆转换反复「放大」解空间，但转换是 1:1 无损可逆，净产出为零，只堆积废料并把 crafts 量级推到数十万（最高 556126）。`jei:/synthetic/<n>` 均为规划器合成 id（合法），且都带 `pattern/exchange` 内置标签，不属 fake-production，未重复报告。未发现空输入 free-resource，未发现 reward/quest/villager/trade/loot 类假生产配方。

## 发现
### F-iso-1 | chain=0092 | severity=major | category=loop-or-cycle
- 位置: [A59]–[A74]（redstone_block↔redstone，16 对）、[A2]–[A4]（steel ingot↔nugget↔ingot）、[A77]–[A94]（raw_iron_block↔raw_iron，18 对）、[A98]–[A107]（raw_gold_block↔raw_gold）
- 问题: 纯振荡往返。[A59] `minecraft:redstone_block x1 @ minecraft:crafting_table` → redstone x9；[A60] redstone x9→redstone_block x1；… 直至 [A74] redstone x9。1 个红石块→9 红石本可一步完成（[A59]），中间 [A60]–[A73] 共 14 个互逆 no-op 步骤净产出为零。[A2] alltheores:steel_dust 烧成 steel_ingot x9 → [A3] ingot 拆成 nugget x81 → [A4] nugget 还原 ingot x9，同样无净产出。链尾 [RAW] 堆积 `immersiveengineering:nugget_steel x1377`、`mekanism:nugget_steel x1377` 等振荡副产物。
- 原因: `loop=allowBoundedAmplification` 把无损可逆的 compress↔decompress 当作放大手段；分类器未识别「来回转换净产出为零」的环。
- 建议: 在 `loop=allowBoundedAmplification` 下增加「互逆配方成对出现且数量回到起点」的环检测，命中即收敛/剔除；redstone_block 与 raw_*_block 这类原版压缩块应标记 no-op。

### F-iso-2 | chain=0036 | severity=major | category=loop-or-cycle
- 位置: [A271]–[A273]（redstone 振荡，23 对）、[A257]–[A261]（steel ingot↔nugget↔storage）
- 问题: [A271] `kubejs:kjs/redstone x32` 产 redstone x512 → [A272] redstone x504→redstone_block x56 → [A273] redstone_block x56→redstone x504，红石总量未增（512→504 还净损 8），纯往返。steel 段 [A257] dust 烧 ingot x252 → [A258] 拆 nugget x2268 → [A259] 还原 ingot x252 → [A260] 压 storage x28 → [A261] 还原 ingot x252，无净产出。链尾 [RAW] 堆积 `immersiveengineering:nugget_steel x3645`、`mekanism:nugget_steel x3645`。
- 原因: 同 F-iso-1，mega-wide 规模下振荡被放大更多。
- 建议: 同 F-iso-1；mega-wide 目标应优先用直接 ingot，而非先拆 nugget 再还原。

### F-iso-3 | chain=0018 | severity=major | category=loop-or-cycle
- 位置: [A38]–[A45]（redstone_block↔redstone 纯振荡，4 对 8 步）
- 问题: [A38] `minecraft:redstone_block x1`、[A39] `minecraft:redstone x1`、[A40] redstone_block x1、[A41] redstone x1… 连续 8 个互逆步骤，红石数量在块/物品间来回，净产出为零。本链 redstone_block=12、redstone=12（扫描计数完全相等），整条 incomplete 链被这种环充斥。
- 原因: 同 F-iso-1。
- 建议: 同 F-iso-1。

### F-iso-4 | chain=0046 | severity=major | category=loop-or-cycle
- 位置: [A108]–[A111] 与 [A181]–[A184]（immersiveengineering steel 重塑环）
- 问题: [A108] `immersiveengineering:crafting/ingot_steel_to_nugget_steel x9` → [A109] `nugget_steel_to_ingot_steel x9` → [A110] `ingot_steel_to_storage_steel x1` → [A111] `storage_steel_to_ingot_steel x1`：钢锭→粒→锭→块→锭，净产出为零（后续 [A181]–[A184] 以 x36/x36/x4/x4 重复同样环）。这是 F-iso-1/2 中 steel 段的具体化。
- 原因: 同 F-iso-1；immersiveengineering 钢的三态（ingot/nugget/storage）互逆配方被当作放大。
- 建议: 同 F-iso-1；steel 三态互逆转换应整体识别为 no-op 环。

### F-iso-5 | chain=0000 | severity=minor | category=unresolved-expected-item
- 位置: [UNRESOLVED] `minecraft:golden_sword x2`（candidates=5, CASCADE_EXHAUSTED）、`minecraft:diamond_sword x2`（candidates=5, CASCADE_EXHAUSTED）、`minecraft:enchanted_book x2`（candidates=2）
- 问题: golden_sword/diamond_sword 是原版可合成物（金锭+木棍 / 钻石+木棍），且 candidates=5 表明图内存在候选配方，却以 CASCADE_EXHAUSTED 收尾 unresolved。疑为规划器在 truncated 预算内未把原版武器配方纳入解，属「明显本可解析」的未解析物——但本链本身是 incomplete/截断链，也可能是预算耗尽而非数据缺失（discipline #3 截断豁免需谨慎）。
- 原因: 截断链内原版常见物品未被解析，疑似 classification/可达性未覆盖基础合成；与循环放大导致的预算耗尽可能级联相关。
- 建议: 标记为 suspected，下一轮确认 vanilla 武器/enchanted_book 配方是否在图内可达；若可达则属 unresolved-cascade（由循环浪费预算致级联放弃）。

## 链级判定
| chain | category | verdict | 一句话 |
| --- | --- | --- | --- |
| 0092 | colossal | bad | redstone/raw/steel 多重无净往返环，RAW 堆积 nugget_steel x1377 |
| 0036 | mega-wide | bad | redstone/steel 振荡，RAW 堆积 nugget_steel x3645 |
| 0111 | colossal | bad | 同 0092 家族（redstone_block 13/redstone 13，raw_block 26/raw_item 52） |
| 0050 | mega-wide | bad | 同 0036 家族（redstone_block 19/redstone 19，raw_block 31/raw_item 62） |
| 0000 | incomplete | questionable | 循环 + golden_sword/diamond_sword 疑似可解析未解析（F-iso-5） |
| 0003 | incomplete | bad | raw_block↔raw_item 振荡；redstone_block 9/9 |
| 0006 | incomplete | bad | 多金属 raw 块振荡 + redstone 振荡 |
| 0009 | incomplete | bad | redstone_block 13/13 振荡 |
| 0012 | incomplete | bad | redstone_block 9/9 振荡 |
| 0015 | incomplete | bad | redstone_block 10/10 振荡 |
| 0018 | incomplete | bad | redstone 纯振荡 A38–A45（F-iso-3） |
| 0021 | incomplete | bad | raw 块振荡（raw_block 22/raw_item 44） |
| 0024 | incomplete | bad | raw 块振荡（raw_block 11） |
| 0027 | incomplete | bad | raw 块振荡 |
| 0030 | incomplete | bad | redstone+raw 振荡 |
| 0033 | incomplete | bad | raw 块振荡 |
| 0037 | incomplete | bad | redstone+raw 振荡 |
| 0040 | incomplete | bad | raw 块振荡 |
| 0043 | incomplete | bad | redstone_block 9/9 振荡 |
| 0046 | incomplete | bad | steel 三态重塑环 A108–A111（F-iso-4） |
| 0049 | incomplete | bad | raw 块振荡 |
| 0053 | incomplete | bad | redstone+raw 振荡 |
| 0056 | incomplete | bad | raw 块振荡 |
| 0059 | incomplete | bad | redstone+raw 振荡 |
| 0062 | incomplete | bad | redstone_block 10/10 振荡 |
| 0065 | incomplete | bad | raw 块振荡（crafts=555793 受环放大） |
| 0068 | incomplete | bad | raw 块振荡（raw_block 11） |
| 0071 | incomplete | bad | redstone_block 8/8 振荡 |
| 0074 | incomplete | bad | redstone+raw 振荡 |
| 0077 | incomplete | bad | redstone+raw 振荡 |
| 0080 | incomplete | bad | raw 块振荡 |
| 0083 | incomplete | bad | redstone+raw 振荡（trunc=125） |
| 0086 | incomplete | bad | raw 块振荡 |
| 0089 | incomplete | bad | raw 块振荡（raw_block 19） |
| 0093 | incomplete | bad | redstone+raw 振荡 |
| 0096 | incomplete | bad | redstone 振荡 |
| 0099 | incomplete | bad | redstone+raw 振荡 |
| 0102 | incomplete | bad | redstone+raw 振荡 |
| 0105 | incomplete | bad | redstone+raw 振荡 |
| 0108 | incomplete | bad | redstone+raw 振荡 |
| 0112 | incomplete | bad | raw 块振荡 |
| 0115 | incomplete | bad | redstone 振荡 |
| 0118 | incomplete | bad | redstone+raw 振荡 |
| 0121 | incomplete | bad | raw 块振荡 |
| 0124 | incomplete | bad | redstone+raw 振荡 |
| 0127 | incomplete | bad | redstone_block 8/8 振荡 |
| 0130 | incomplete | bad | raw 块振荡（raw_block 27/raw_item 54） |
| 0133 | incomplete | bad | redstone+raw 振荡 |
| 0136 | incomplete | bad | redstone 振荡 |
| 0139 | incomplete | bad | redstone_block 8/8 振荡 |
| 0142 | incomplete | bad | redstone_block 26/26 重度振荡 |
| 0145 | incomplete | bad | raw 块振荡 |
| 0148 | incomplete | bad | raw 块振荡 |

> 注：以上为逐条证据抽取 + 信号扫描覆盖的 53 条（4 complete + 49 抽样 incomplete）。剩余 97 条 incomplete 链经同一信号扫描（redstone_block↔redstone 相等计数 / raw_block↔raw_item 1:2 振荡 / steel 互逆）**全部命中循环签名（150/150）**，verdict 均为 bad，未逐行列出以节省篇幅。

## 规则建议（给汇总者）
- targetRegex: 无需排除目标；本批目标（flux/singularity generator、各类 storage/induction cell、vat、module）本身合理。
- recipeRegex: 收敛以下「互逆 no-op 环」配方族（在 `loop=allowBoundedAmplification` 下命中即标记/剔除，证据：150/150 链）：
  - `minecraft:redstone_block`(compress) ↔ `minecraft:redstone`(decompress) —— 原版红石块往返
  - `minecraft:raw_(iron|gold|copper|osmium)_block` ↔ `minecraft:raw_(iron|gold|copper|osmium)` —— alltheores/mekanism 原矿块往返
  - `immersiveengineering:crafting/(ingot|nugget|storage)_steel_to_(nugget|ingot|storage)_steel` —— steel 三态互逆转
- builtin 候选: 跨包通用模式 `pattern/roundtrip-noop` —— 当 `loop=allowBoundedAmplification` 下某配方与其精确逆配方成对出现、且物品计数回到起点（净产出≈0），判为 no-op 环并收敛（本批 150 链证据，建议标签 junk/pattern-roundtrip-noop）。
- 额外观察: crafts 量级失控（最高 556126，见 0023/0065/0094/0072 等）是上述循环的直接后果，建议作为循环收敛后的副指标，而非单独建规则。
- 无新模式（除 roundtrip-noop 外）：`jei:/synthetic/<n>` 为合法合成 id 且带 pattern/exchange，勿误报为 fake-production。
