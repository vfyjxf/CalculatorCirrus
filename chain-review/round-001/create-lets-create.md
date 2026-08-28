# create-lets-create batch-001 审查报告
- pack: create-lets-create
- 批次: batch-001（written=150；类别分布: incomplete 142 / colossal 8）
- 审查链数: 150（incomplete 142 / colossal 8；抽样说明见下）
- 发现: critical 1 / major 2 / minor 2

## 抽样与读法说明
- `chains-incomplete-001.txt` 9.78MB / 142 链 ≈ 68KB/链，**未超过** 100KB/条阈值，故抽样下限仍为 100。受单次上下文限制，巨型 incomplete 链采用「链头(目标/状态/机器) + 链尾(UNRESOLVED) + 定向 grep 信号」读法，而非逐行全读。
- colossal 8 条及代表性 incomplete（0000/0050/0053/0055/0064/0073/0091/0107/0115/0137 等）做了段落精读。
- 全批 142 条通过三类定向 grep 全量核查：① allthecompressed `compress/..._9x`↔`decompress/..._9x` 往返；② `INT_MAX 2147483647` / `UINT_MAX 4294967294` / `1870418615` 数量溢出；③ vanilla `minecraft:` UNRESOLVED。
- 已核查且无问题：fake-production（无 `jei:category` 机器，全部 `jei:/synthetic` 合法；`ftbquests:screen_7` 为真实 `minecraft:crafting`；`bhc:relic_apple` 为 apple+diamond+emerald 真实合成）；free-resource（无零输入 EXECUTE；cobblestone 来自解压玩家采集的压缩块，无 generator）；machine-mismatch（cataclysm 仪式配方输入真实、@ minecraft:crafter 合理）。

## 发现

### F-create-lets-create-1 | chain=0000 | severity=major | category=loop-or-cycle
- 位置: `[A1] EXECUTE allthecompressed:compress/blazing_crystal_block_9x x1 @ minecraft:crafter`（in 9× blazing_crystal_block_8x）→ 紧接 `[A2] EXECUTE allthecompressed:decompress/blazing_crystal_block_9x x1 @ minecraft:crafter`（out 9× blazing_crystal_block_8x, `<= from A1`）。同模式见 CHAIN 0055 `[A52] compress/acacia_log_8x`→`[A53] compress/acacia_log_9x`→`[A54] decompress/acacia_log_9x`（8x→9x→8x 净零）。
- 问题: 压缩到 9x 后立刻解压回同阶 8x，净产出为零（9×8x → 9×8x）。属于 brief 明确列举的「compress↔decompress 无净产出往返环」。规划器却把压缩阶梯当必需中间体。
- 原因: allthecompressed 压缩/解压阶梯被当作合法生产步骤；实际上 8x 块存在 `decompress/..._8x` 可直接解压，先压到 9x 再解压纯属冗余往返。`loop=allowBoundedAmplification` 在此被误用为纯放大而非有净产出放大。
- 建议: 将 allthecompressed compress↔decompress 往返识别为 loop 并剪枝；压缩阶梯折叠为「直接使用基础材料」。
- 范围: 142/142 条 incomplete + 8 条 colossal 全部含此模式（grep: `compress/..._9x` 2022 处，`decompress/..._9x` 1011 处）。

### F-create-lets-create-2 | chain=0000 | severity=major | category=amount-insanity
- 位置: CHAIN 0000 `status: crafts=2179240396`；CHAIN 0108 `toms_storage:adv_wireless_terminal crafts=80292581521`；CHAIN 0115 `rsinfinitybooster:dimension_card crafts=79146462888`；CHAIN 0099 `mcwfurnitures:spruce_glass_table crafts=145282721`。
- 问题: crafts 数量级达数亿至数百亿，全部来自 allthecompressed 9 倍压缩阶梯的纯放大（每升/降一级 ×9），对目标无任何净收益。典型: CHAIN 0099 目标仅需 6 个 sand，却压缩/解压了 `774,840,978` 个 red_sand（链尾 `[RAW] minecraft:red_sand x774840978`）。
- 原因: 同 F-1，压缩阶梯充当「数量放大器」被反复串联（`loop=allowBoundedAmplification` 误用）。
- 建议: 与 F-1 同一折叠规则；并设 crafts 上限告警（如 >1e8 标 red）。

### F-create-lets-create-3 | chain=0050 | severity=critical | category=amount-insanity
- 位置: 需求数量溢出整数表示边界：`minecraft:redstone x2147483647`（INT_MAX，22 链）；`minecraft:netherite_scrap x4294967294`（UINT_MAX，11 链）；另有 `minecraft:gold_ingot x1870418615`。
- 问题: 需求数量撞上 INT_MAX / UINT_MAX 哨兵值，证明 9 倍放大在宽幅链(mega-wide)中累积溢出，生成的「需求数量」已非物理可行值 → 这些链的计划实质不可用（brief: critical = 链整体不可用）。
- 原因: F-1/F-2 放大在 mega-wide 链中失控溢出。
- 建议: 把 `2147483647` / `4294967294` / `1870418615` 视为溢出哨兵（data-pollution），出现即判 bad；根因修复见 F-1。
- 溢出链: redstone INT_MAX → 0050,0053,0065,0073,0075,0076,0088,0095,0106,0108,0110,0111,0112,0113,0115,0117,0132,0133,0137,0138,0148,0149；netherite_scrap UINT_MAX → 0064,0101,0102,0103,0104,0108,0109,0115,0125,0134,0147。

### F-create-lets-create-4 | chain=0000 | severity=minor | category=unresolved-expected-item
- 位置: CHAIN 0000 `[UNRESOLVED] minecraft:iron_ingot x4 (reason=BUDGET_TRUNCATED)`；`[UNRESOLVED] minecraft:redstone_block x387420489 (reason=BUDGET_TRUNCATED)`；全批 `[UNRESOLVED] minecraft:enchanted_book x1` 出现于 4 条链；另有 `minecraft:cobblestone/stone/red_sand` 等原版物 UNRESOLVED。
- 问题: 这些属原版常见、可由原版合成/熔炼/附魔台产出的物品，却以 UNRESOLVED 收尾。多因上游放大耗尽 budget/cascade（reason=BUDGET_TRUNCATED/CASCADE_EXHAUSTED），并非真不可解。
- 原因: 链尾部被放大耗尽预算，把本可解析的原版物级联标记为 unresolved。属 F-1/F-2 的级联症状。
- 建议: 修复放大后此类将大幅减少；可另加「原版常见物 unresolved 自动续解」规则。严格属预期截断范畴，仅作 minor 提示。

### F-create-lets-create-5 | chain=0055 | severity=minor | category=loop-or-cycle
- 位置: CHAIN 0055 `[A65]`–`[A76]` 交替出现 `powah:crafting/dielectric_rod_2 x2` 与 `powah:crafting/dielectric_rod_h_2 x2`（A68–A76 反复互现，数量恒为 x2）。
- 问题: 两种介电杆配方交替产出、数量均为 x2，疑似 A↔B 互逆 no-op 重复（类似 F-1 的放大往返）。证据不足以确认有净产出，标 suspected。
- 原因: 疑似 powah 介电杆多级制造的规划器往返。
- 建议: 复核 `dielectric_rod_2 ↔ dielectric_rod_h_2` 是否为有净产出放大；若是 no-op 则剪枝。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0099,0124,0130,0140,0141,0142,0143,0144 (colossal×8) | amount-insanity / loop-or-cycle | bad | 完成链但仍含 allthecompressed 放大，crafts 9.6M–145M |
| 0050,0053,0065,0073,0075,0076,0088,0095,0106,0108,0110,0111,0112,0113,0115,0117,0132,0133,0137,0138,0148,0149 | amount-insanity (INT_MAX) | bad | redstone 需求溢出 2147483647，计划不可用 |
| 0064,0101,0102,0103,0104,0109,0125,0134,0147 | amount-insanity (UINT_MAX) | bad | netherite_scrap 需求溢出 4294967294，计划不可用 |
| 0000,0055 | loop-or-cycle + amount-insanity | bad | 往返环 + 数亿 crafts（详见 F-1/F-2/F-5） |
| 0001–0049,0051,0052,0054,0056–0063,0066–0072,0074,0077–0087,0089,0090,0092–0094,0096–0100,0105,0107,0114,0116,0118–0131,0135,0136,0139–0146,0149(其余 incomplete) | loop-or-cycle / amount-insanity | questionable | 含 allthecompressed 往返环 + crafts 百万~数十亿（系统性，F-1/F-2） |

## 规则建议（给汇总者）
- recipeRegex: `allthecompressed:(compress|decompress)/[a-z0-9_]+` —— 折叠 compress↔decompress 往返阶梯为直接使用基础材料（证据: 142/142 链，compress/9x 2022 处 + decompress/9x 1011 处）。
- builtin 候选: `pattern-allthecompressed-ladder`（标签 pattern-xxx）—— 把 9x 压缩阶梯识别为合成放大伪迹并剪枝（同上证据）。
- 哨兵/recipeRegex: `2147483647|4294967294|1870418615` —— 标记整数溢出哨兵数量，出现即判 bad（data-pollution；证据: 22 链 redstone INT_MAX + 11 链 netherite_scrap UINT_MAX）。
- builtin 候选: `junk-overflow-quantity` —— 需求数量 ≥ INT_MAX 视为溢出垃圾（同上）。
- 无 targetRegex 新模式：问题在配方而非目标；目标均为真实物品（powah / immersive_aircraft / cataclysm / sophisticated_* / ftbquests 等），无需排除目标。
