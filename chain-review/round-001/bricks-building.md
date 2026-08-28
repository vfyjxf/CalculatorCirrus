# Brick's Building 1.4.1 batch-001 审查报告

- pack: Brick's Building 1.4.1
- 批次: batch-001（written=150，类别分布：incomplete 150 / trivial·short·medium·long·giant·colossal 均无——本批仅有 chains-incomplete-001.txt）
- 审查链数: 150（incomplete 150 / complete 0；全量程序化扫描 150 条 + 重点精读 0000/0008/0023/0102；per-chain 约 43KB < 100KB 阈值，未降采样）
- 发现: critical 0 / major 3 / minor 2

## 审查口径说明（本批适用项）

- 红线确认：未创建/修改任何非报告文件，未运行 gradle/java 导出。
- 全批 150 条均为 `incomplete`，且 150/150 链含 `[UNRESOLVED]`（最大 pending 18），根因是 `search: budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`——摘要截断本身按 brief 豁免，不重复报。
- `jei:*` 与 `simulated:*` **均非 fake-production**：所有 `jei:category/<mod>/<机器>` 是真实机器配方系统；`simulated:*` 目标（linked_typewriter / steering_wheel / navigation_table 等 18 条）系统为 `minecraft:crafting` 或 `jei:category/create/mechanical_crafting`，玩家可执行。已核对，不报 fake-production，亦不建议加 fake 规则。
- `+raw N` 叶子（如 `quark:white_shingles_slab|+270`）为正常原材料，不报。

## 发现

### F-BB-1 | chain=0000,0023（代表，140/150 条命中）| severity=major | category=tag-expansion-overreach
- 位置: `quark:tweaks/crafting/slab_to_block` @ minecraft:crafting_table（如 CHAIN 0000 A10、CHAIN 0023 A6）
- 问题: 单个 slab 输入被展开为约 80 种不同 block 输出。例 CHAIN 0023 A6：输入 `quark:white_shingles_slab|+270 x36`，输出 `quark:shingles / create:cut_veridium / bits_n_bobs:calcite_tiles / …`（约 80 种）各 x18；下游仅消费其中 `create:cut_crimsite_wall`（A7 用 crushing_wheel 回收取铁）。slab→对应 block 语义应为 1:1，却生成整族方块。
- 原因: planner 将 any-slab→any-block 的 tag 配方输出 tag 全量展开，单步产生 80 个互不相干的 block，极大膨胀 raw_kinds（普遍 270–345）与配方多样性，并诱使后续把无关 block 当原料粉碎/回收。
- 建议: 将 `quark:tweaks/crafting/slab_to_block` 收敛为“输出按输入 slab 类型收窄为单一对应 block”，或按 (输入slab, 输出block) 配对裁剪输出 tag。

### F-BB-2 | chain=0000,0023,0102（代表；block↔ingot 99/113 条、apple_crate↔uncompress 127 条）| severity=major | category=loop-or-cycle
- 位置: CHAIN 0000 A13–A30（minecraft:iron_block ↔ minecraft:iron_ingot_from_iron_block 反复 9 次，净零）；CHAIN 0000 A1–A3 与 CHAIN 0102 A1–A4（`quark:building/crafting/compressed/apple_crate` ↔ `…apple_crateuncompress` 往返）；CHAIN 0023 `farmersdelight:potato_crate` / `potato_from_crate` 各 16 次。
- 问题: 无净产出的互逆往返环：block→ingot→block→… 与 crate→uncompress→crate→…，仅做数量往复，最终回到起点等价物，未推进目标。声明 `loop=allowBoundedAmplification` 被这类守恒 no-op 环滥用（1 iron_block=9 iron_ingot 往返守恒）。
- 原因: planner 在预算内用压缩/解压、块/锭互转“放大”计数，但互逆步骤净产出为零——属 brief 明确的 compress↔decompress / A↔B 互逆 no-op 问题。
- 建议: 将 “块↔锭”（iron/gold/copper）与 “X_crate ↔ X_crateuncompress / X_from_crate” 互逆对加入 loop 检测黑名单（无净产出即判环），或限制 allowBoundedAmplification 不对守恒互逆步骤计数。

### F-BB-3 | chain=0008,0023,0085,0088,0096,0098 | severity=major | category=amount-insanity
- 位置: status 行 crafts 值：0008=253830238、0023=37358210147、0085=29847552、0088=37358209025、0096=37358209204、0098=225580543。
- 问题: 单目标（如 farmersdelight:roast_chicken_block x1）累计 crafts 达 2500 万–370 亿量级。例 CHAIN 0023 A21–A29：`minecraft:enchantment.` 在 anvil 上经 5 次 pattern/exchange 14→68→336→1678→8389（≈×5/步）指数放大；叠加 potato_crate 往返 16 次。
- 原因: F-BB-2 的往返环 + F-BB-4 的乱码 id 指数放大共同驱动，planner 把同一物品的“计数”当产出累加，crafts 失控。链虽 status=success 但作为真实配方计划不可用。
- 建议: 修复 F-BB-2 / F-BB-4 后 amounts 自然回落；可临时把 crafts≥1e6 的链标 blacklist/skip 以防污染下游 coverage 统计。

### F-BB-4 | chain=0023,0085,0088,0096,0098 | severity=minor | category=data-pollution
- 位置: `[A21][A23][A25]…` EXECUTE `minecraft:enchantment.` xN @ minecraft:anvil（如 CHAIN 0023 行 6893/6897/6901/6905/6909）
- 问题: 物品 id `minecraft:enchantment.` 以句点结尾，是截断/乱码 id（非合法物品，应为 enchantment_table 或某附魔物品）。它作为 pattern/exchange 步骤在 anvil 上参与，并随次数指数增长（见 F-BB-3）。
- 原因: 数据层 enchantment 命名解析失败，留下带尾点占位 id 进入图；虽带 `[rule-tag: pattern/exchange]`（builtin 模式，不重报该模式），但 id 本身属 data-pollution。
- 建议: 解析/分类阶段过滤 `minecraft:enchantment.` 这类尾点乱码 id（正则 `~^minecraft:enchantment\.$`），按 junk 丢弃或回退到真实附魔产出配方。

### F-BB-5 | chain=0000,0003,0018（代表；stick/iron_ingot/diamond 等）| severity=minor | category=unresolved-expected-item（suspected）
- 位置: `[UNRESOLVED]` 高频：`minecraft:stick`（41 次）、`minecraft:iron_ingot`（15）、`minecraft:diamond`（8）、`minecraft:redstone`（10）、`minecraft:cobblestone`（3）。
- 问题: 这些原版常见物在链内实际可产（如 CHAIN 0023 A4 用 planks→stick @ crafting_table），却仍标 `[UNRESOLVED]`。全批 150/150 链均 `[UNRESOLVED]`，符合 `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`。
- 原因: 绝大多数 unresolved 是预算截断所致（brief 豁免“摘要截断本身”）；但 stick/iron_ingot 这类有确定性原版配方者，疑似基础配方被排除或不在搜索半径内——属 suspected，需确认。
- 建议: 核查 stick/iron_ingot 等基础配方是否被分类器误排除；若确为预算截断，则不必单独修复，随 coverage 提升自然消解。

## 链级判定

| chain | category | verdict(ok/questionable/bad) | 一句话 |
|---|---|---|---|
| 0000 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0001 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0002 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0003 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0004 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0005 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0006 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0007 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0008 | amount-insanity | bad | crafts=253830238 数量级失控(指数放大/往返环驱动) |
| 0009 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0010 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0011 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0012 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0013 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0014 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0015 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0016 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0017 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0018 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0019 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0020 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0021 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0022 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0023 | amount-insanity | bad | crafts=37358210147 数量级失控(指数放大/往返环驱动) |
| 0024 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0025 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0026 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0027 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0028 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0029 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0030 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0031 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0032 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0033 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0034 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0035 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0036 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0037 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0038 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0039 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0040 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0041 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0042 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0043 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0044 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0045 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0046 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0047 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0048 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0049 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0050 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0051 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0052 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0053 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0054 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0055 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0056 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0057 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0058 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0059 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0060 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0061 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0062 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0063 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0064 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0065 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0066 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0067 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0068 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0069 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0070 | ok | ok | 无上述系统性问题 |
| 0071 | ok | ok | 无上述系统性问题 |
| 0072 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0073 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0074 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0075 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0076 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0077 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0078 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0079 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0080 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0081 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0082 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0083 | ok | ok | 无上述系统性问题 |
| 0084 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0085 | amount-insanity | bad | crafts=29847552 数量级失控(指数放大/往返环驱动) |
| 0086 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0087 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0088 | amount-insanity | bad | crafts=37358209025 数量级失控(指数放大/往返环驱动) |
| 0089 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0090 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0091 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0092 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0093 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0094 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0095 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0096 | amount-insanity | bad | crafts=37358209204 数量级失控(指数放大/往返环驱动) |
| 0097 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0098 | amount-insanity | bad | crafts=225580543 数量级失控(指数放大/往返环驱动) |
| 0099 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0100 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0101 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0102 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0103 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0104 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0105 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0106 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0107 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0108 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0109 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0110 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0111 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0112 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0113 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0114 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0115 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0116 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0117 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0118 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0119 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0120 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0121 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0122 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0123 | loop-or-cycle | questionable | block/crate 往返环无净产出 |
| 0124 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0125 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0126 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0127 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0128 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0129 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0130 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0131 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0132 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0133 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0134 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0135 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0136 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0137 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0138 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0139 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0140 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0141 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0142 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0143 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0144 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0145 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0146 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0147 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0148 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |
| 0149 | tag-expansion-overreach | bad | slab_to_block 单slab展开~80方块,过度生成 |

## 规则建议（给汇总者）

- targetRegex: 本批无需排除目标（`simulated:*` 与 farmersdelight 食物块均为真实可产目标，非 fake）。唯一建议：对脏数据 `minecraft:enchantment.` 作 junk 过滤（见 recipeRegex）。
- recipeRegex:
  - `quark:tweaks/crafting/slab_to_block` —— 收敛为单 block 输出（tag-expansion-overreach，证据 140/150 链）。
  - `quark:building/crafting/compressed/.*crate(uncompress)?` 与 `farmersdelight:.*_from_crate` —— 标记 crate 往返（loop，证据 apple_crate 127/150、potato_crate 等 16× 重复）。
  - `minecraft:(iron|gold|copper)_block` ↔ `minecraft:(iron|gold|copper)_ingot_from_.*_block` —— 标记块↔锭守恒往返（loop，证据 iron 99/113、gold 30、copper 52/56 链）。
  - `minecraft:enchantment\.` —— 按 junk 丢弃（data-pollution，证据 5 链：0023/0085/0088/0096/0098）。
- builtin 候选:
  - pattern-crate-bounce：`X_crate ↔ X_crateuncompress / X_from_crate` 互逆压缩往返（Quark/FD），建议标签 pattern-xxx，证据 127+ 链。
  - pattern-block-ingot-bounce：`metal_block ↔ metal_ingot` 守恒互逆（iron/gold/copper），建议标签 pattern-xxx，证据 iron 99/113、gold 30、copper 52/56 链。
  - junk：`minecraft:enchantment.` 尾点乱码 id，建议标签 junk，证据 5 链。
  - 注意：`jei:*` / `simulated:*` 均为真实机器配方系统，勿加 fake-production 规则（已核对 jei:category/* 与 simulated: 目标均经 minecraft:crafting / jei: mechanical_crafting 生产）。
- 无新模式时写“无”：除上述 tag-expansion / 两类互逆环 / 乱码 id 外，未发现 fake-production、free-resource、worse-alternative、silly-detour、machine-mismatch、generation-abuse、catalyst-misuse 等其他类别。

## 复核备注
- 本批 `chains-incomplete-001.txt` 6.5MB / 150 链，per-chain ~43KB，未触发 100KB/条降采样阈值，故按全量审查（≥100 条达标）。
- 未发现 fake-production（jei:/simulated: 全部为真实机器配方，符合 brief 的 jei: 命名空间澄清）；未发现 free-resource（0 条零输入 EXECUTE）。
- 最严重根因为 F-BB-1（slab_to_block 输出过展开），它放大 raw_kinds 并诱发后续回收/粉碎乱走；F-BB-2（往返环）与 F-BB-4（乱码 id 指数放大）共同导致 F-BB-3（数量级失控）。修复 F-BB-1/2/4 后，多数链的不可读性应显著下降。
