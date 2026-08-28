# creative-drawers-producer-2 batch-001 审查报告
- pack: creative-drawers-producer-2
- 批次: batch-001（written=150；类别分布：incomplete 149、colossal 1）
- 审查链数: 150（incomplete 149 / colossal 1；incomplete 文件 131598 行 / 10.3MB，未整读，按口径做结构 grep 全量扫描 + 3 条链全文精读：0000、0117、0120，其余抽样头部）
- 发现: critical 0 / major 5 / minor 0

## 审查方法说明
- 全文精读：colossal 链 0120；incomplete 链 0000、0117 头部及各链 `awk` 区间抽取。
- 全量结构扫描（覆盖全部 149 条 incomplete）：`reason=` 分布、compress↔decompress 邻接、`sourceberry_sack`/`cardboard_from_block`/`ender_pearl_fragment` 循环信号、`jei:/synthetic` 与 `ftbquests` 目标。
- 已按口径忽略：`jei:/synthetic/<id>` 在 `avaritia:extreme_anvil` 上均带 `[rule-tag: pattern/exchange]`（builtin，不重复报告）；`+raw N` 叶子为正常原材料；BUDGET_TRUNCATED 的 `[UNRESOLVED]` 属预算耗尽预期，不单列。

## 发现
### F-creative-drawers-producer-2-1 | chain=0000,0120(+148/149) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0000 A2-A3（line 8-11，`allthecompressed:compress/bone_block_9x`→`decompress/bone_block_9x`）；CHAIN 0120 A23-A41（line 85-159，`obsidian_1x→…→9x` 再 `9x→…→obsidian`）
- 问题: 无净产出的 compress↔decompress 往返。A2 把 `bone_block_8x` 压到 `9x`，A3 立即解压回 `8x`（9^8 物品量守恒，纯等价变换），随后 A4-A11 一路解压到 `1x`。CHAIN 0120 更极端：A23 直接产出 `minecraft:obsidian x387420496`，A24-A32 全压成 `9x`，A33-A41 又全解压回 `obsidian x9` 去喂 ender_chest——obsidian 本已在手，压缩再解压纯属浪费。该模式在 148/149 条链出现，644 处 immediate compress/9x→decompress/9x 邻接对。
- 原因: 规划器把 allthecompressed 压缩层级当成"数量洗涤"手段，在不需要高阶压缩形态时仍做 `9x` 往复；`allowBoundedAmplification` 被误用于无净产出环（口径明确：compress↔decompress 互逆 no-op 仍属问题）。
- 建议: 增加结构规则，禁止"compress/X_9x 后紧跟 decompress/X_9x 同物"的环；或对 allthecompressed 各层级配方做去环惩罚。

### F-creative-drawers-producer-2-2 | chain=0117(+ widespread) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0117 A4-A21（line 112876-112943，`create:crafting/materials/cardboard_from_block` 与 `create:crafting/materials/cardboard_block` 互逆）
- 问题: `cardboard_block → cardboard x4 → cardboard_block → cardboard x4 …` 的纯振荡。A5 用 4 cardboard 合成 `cardboard_block`，A6 又把 `cardboard_block` 拆回 4 cardboard，A7 再合成……块与 4 纸板恒等，无净产出却被重复 16 次以上。文件内 `cardboard_from_block` 出现 238 次，属跨链系统性环。
- 原因: 下游（A24 book）只需 `cardboard`，但规划器用 block↔cardboard 互逆配方做无谓往返，疑似为"凑数量"触发放大策略。
- 建议: 将 `create:crafting/materials/cardboard_block` 与 `cardboard_from_block` 标记为 pattern/noop-oscillation 候选（去环）。

### F-creative-drawers-producer-2-3 | chain=0025,0051(+多链) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0025 A1-A15（line 41679-41715，`ars_nouveau:sourceberry_sack` ↔ `ars_nouveau:sourceberry_sack_to_bush`）；CHAIN 0051 同构起始
- 问题: `sourceberry_bush x9 → sourceberry_sack x1 → sourceberry_bush x9 → …` 的 A↔B 互逆 no-op，重复十余次无净产出（9 bush→1 sack→9 bush 守恒）。在 CHAIN 0025（million_furnace）与 CHAIN 0051（rainbow_core）均出现，文件内 `sourceberry_sack_to_bush` 共 20 处。
- 原因: 与 F-2 同类——互逆配方被当成放大手段，实际为零净产出环。
- 建议: 标记 `ars_nouveau:sourceberry_sack_to_bush` 与 `sourceberry_sack` 为 pattern/noop-oscillation 候选（去环）。

### F-creative-drawers-producer-2-4 | chain=0120 | severity=major | category=loop-or-cycle
- 位置: CHAIN 0120 A2-A17（line 10-73，`ender_pearl`↔`forbidden_arcanus:ender_pearl_fragment`）；A19-A20（line 74-79，`blaze_powder↔blaze_rod`）
- 问题: 两层叠加空转环。(a) A1 已用 `enderman_essence` 产出 `ender_pearl x4`，但 A2-A17 把 `ender_pearl→fragment x4→ender_pearl x1` 往复 16 次（1→4→1 守恒，纯 no-op）后才喂 ender_eye。(b) A19 `blaze_powder x4 → blaze_rod x1`、A20 `blaze_rod x1 → blaze_powder x2` 构成净损耗互逆环。
- 原因: 规划器在已有正向产出（A1 essence→pearl）后，又用互逆配方反复"洗涤"同一物品；`allowBoundedAmplification` 未排除零/负净产出环。
- 建议: 与 F-1 同治理——对 ender_pearl_fragment 互逆、blaze 互逆做去环；确保仅保留 A1 单条 essence→pearl 正向路径。

### F-creative-drawers-producer-2-5 | chain=0000,0003,0004… | severity=major | category=amount-insanity
- 位置: CHAIN 0000 `crafts=11946198150`；CHAIN 0003 `crafts=24749789798`；CHAIN 0004 `crafts=2010148949`；colossal CHAIN 0120 `crafts=96855155`
- 问题: crafts 数量级失控（数十亿级），远超制造单件目标所需。根因正是上述循环洗涤：每多绕一次压缩/互逆环，crafts 计数被成倍放大，而净产出为零。
- 原因: 循环环把"动作计数"指数级放大，叠加 mega-wide 锥形预算，使 crafts 数值失真；并非真实需要数十亿次合成。
- 建议: 治理 F-1~F-4 的去环规则后，crafts 量级应回落到合理区间；建议把 `crafts≥1e8` 且存在上述环的链标记为 amount-insanity 复核。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | loop-or-cycle / amount-insanity | bad | bone_block compress↔decompress 往返 + crafts 11.9B |
| 0001 | loop-or-cycle | bad | 同模式（mega-wide，含 allthecompressed 环） |
| 0002 | loop-or-cycle | bad | raw149 mega-wide，含压缩往返环 |
| 0003 | loop-or-cycle / amount-insanity | bad | crafts 24.7B，循环洗涤放大 |
| 0004 | loop-or-cycle | bad | cdp2:absolute_essence，含压缩往返 |
| 0011 | loop-or-cycle | questionable | createmoremachines:creative_casing，疑似含环 |
| 0025 | loop-or-cycle | bad | sourceberry_sack↔bush 互逆环（A1-A15） |
| 0051 | loop-or-cycle | bad | rainbow_core，sourceberry 互逆环 |
| 0117 | loop-or-cycle | bad | cardboard_block↔cardboard 振荡 16+ 次 |
| 0120 | loop-or-cycle / amount-insanity | bad | 三层叠加环（ender_pearl/blaze/obsidian）+ crafts 96.8M |
| 其余 139 条 incomplete | loop-or-cycle | bad/questionable | 148/149 含 allthecompressed compress↔decompress 往返环，量级随 raw 数放大 |
| colossal 0120 | — | (同上) | 已单列 |

## 规则建议（给汇总者）
- targetRegex: 暂无需要排除的目标（ftbquests:screen_7 等目标虽为任务展示方块，但确有 minecraft:crafting 合成配方，非 fake-production，不排）。
- recipeRegex: `^allthecompressed:(compress|decompress)/[a-z_0-9]+_[0-9]x$` —— 标记需做"紧邻同物 compress↔decompress 去环"收敛（证据：148/149 链、644 处 immediate 邻接对）。
- builtin 候选:
  - `pattern/noop-oscillation`（junk 类去环）：覆盖 `create:crafting/materials/cardboard_block`↔`cardboard_from_block`（238 次）、`ars_nouveau:sourceberry_sack`↔`sourceberry_sack_to_bush`（20 处）、`ender_pearl`↔`forbidden_arcanus:ender_pearl_fragment`、`blaze_powder`↔`blaze_rod`、`compress/9x`↔`decompress/9x`。共同特征：配方成对互逆、净产出为零、被 `allowBoundedAmplification` 误放大。
  - `pattern/compress-launder`（duplicate/收敛）：allthecompressed 全层级在"输入已是低阶块、下游仅要 raw 形态"时被无谓压到 9x 再解压回，建议收敛为直达路径。
- 备注：`jei:/synthetic/<id> @ avaritia:extreme_anvil` 全部带 `pattern/exchange` 已内置，不重复处理；若后续发现其合成展开仍放大 crafts，可考虑升级为排除。
