# minecolonies-dimensional-adventure batch-001 审查报告

- pack: minecolonies-dimensional-adventure
- 批次: batch-001（written=150，全部 `incomplete`；depthBucket 多为 colossal，少数 mega-wide）
- 审查链数: 150（全文件信号扫描 `chains-incomplete-001.txt` 13.4MB / 132676 行全覆盖 + 代表性链精读约 32 条：0000/0001/0002/0003/0004/0040/0042/0058/0060/0068/0073/0085/0094/0119/0123/0127/0137/0146 等；incomplete 150 / complete 0）
- 发现: critical 0 / major 5 / minor 0

> 已知、规则层无法解决、本轮不再展开的规划器级问题（按 round-2 口径一句话带过）：32 位计数溢出哨兵、物品 id `|+N` 后缀污染（`minecraft:iron_pickaxe|+17` 出现 1845 行、`minecraft:glass|+48` 409 行等）、`intent/recycling` 回收环（已带 `[rule-tag: intent/recycling]`）、`jei:/synthetic` 铁砧合成阶梯（已带 `pattern/exchange`）。本批未发现 `jei:category/`（真实机器系统）与任何任务奖励/村民交易/展示页类假生产。

## 发现

### F-minecolonies-dimensional-adventure-001 | chain=0000 | severity=major | category=loop-or-cycle
- 位置: powah:crafting/blazing_crystal_block ⇄ powah:crafting/blazing_crystal（A1–A16 共 16 步），以及 powah:crafting/dielectric_rod_h ⇄ dielectric_rod_2 ⇄ dielectric_rod_h_2（A59–A77 约 19 步）
- 问题: 链首 A1 用 9×crystal_blazing 合成 blazing_crystal_block，A2 立即把该块 1:9 解回 crystal_blazing，A3 再合块……A1–A16 在「块⇄晶体」间纯往返，输出喂给下一步、净产出为 0（最终晶体交由 A83 造发电机）。dielectric_rod 同理：horizontal⇄rod 反复振荡，仅 A78(dielectric_casing) 真实需求 rod_horizontal×2+rod×2，却振荡 ~19 步。两处均无 `[rule-tag]` 标注。
- 原因: `loop=allowBoundedAmplification` + `budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]` 下，规划器对可逆存储/形态转换配方做无界往返搜索；powah 晶体块与介电杆两形态均被当作独立节点反复互转。
- 建议: 对该类「块⇄锭/晶体」「杆⇄横杆」对称配方加 `pattern/reversible_conversion` 标签并禁止无净产出的往返放大。

### F-minecolonies-dimensional-adventure-002 | chain=0119 | severity=major | category=loop-or-cycle
- 位置: cosmos_infinia:void_steel_block_craft ⇄ cosmos_infinia:void_steel_block_decraft（A1–A16 至少 16 步）
- 问题: A1 由 raw_voidsteel 炼出 9×void_steel_ingot（真实生产），A2 9 锭合 1 块、A3 1 块解回 9 锭、A4 再合块……A2–A16 纯「块⇄锭」往返，除最终一块被火箭配方消费外其余均为无净产出振荡。无 `[rule-tag]` 标注。
- 原因: 同上，可逆压缩/解压配方被规划器当作可重复放大的步骤；宇宙维度（cosmos_infinia）tier_4 火箭目标触发该振荡。
- 建议: 同 F-001，将 `_block_craft`/`_block_decraft` 对称配方纳入可逆转换标签收敛。

### F-minecolonies-dimensional-adventure-003 | chain=0040 | severity=major | category=loop-or-cycle
- 位置: draconicevolution:compress/draconium_block ⇄ decompress/draconium_ingot ⇄ decompress/draconium_nugget（A2–A5）
- 问题: A2 压缩 draconium_block→ingot，A3 解压 ingot→nugget，A4 再压缩 nugget→ingot，A5 解压→... 形成块⇄锭⇄粒多档互逆环，与 F-001/F-002 同一机理，无标签。
- 原因: draconicevolution 的 compress/decompress 对称配方被规划器纳入往返放大。
- 建议: 同 F-001，compress/decompress 对称配方加 `pattern/reversible_conversion`。

### F-minecolonies-dimensional-adventure-004 | chain=0002 | severity=major | category=loop-or-cycle
- 位置: glazedresymmetry shard/block/slab/pillar 多步互转（A27–A36：shard→block→centered→slab→block→shard→pillar→shard→centered→block）；以及 glass⇄rough_glass 环（A12–A14 / A38–A40 / A60–A62 / A71–A73：temporal_sand→glass→rough_glass(石切)→glass(冶炼)）
- 问题: glazedresymmetry 的 shard/block/slab/centered/pillar 配方两两可逆，规划器在其中长链互转；glass 环每个单元 glass→rough_glass→glass 净产出为 0，却随需求层级几何放大：x8→x24→x84（A12/A60/A71）。均无 `[rule-tag]` 标注。
- 原因: glazedresymmetry 装饰方块与 blockbox rough_glass、chronodawn temporal_sand 等可逆/对称配方在预算耗尽时被规划器当作可放大步骤反复执行。
- 建议: 将 glazedresymmetry shard⇄block⇄slab、glass⇄rough_glass 对称转换纳入可逆转换标签；glass 环可直接用 temporal_sand→glass 直产，去掉 rough_glass 折返。

### F-minecolonies-dimensional-adventure-005 | chain=0000,0040 | severity=major | category=loop-or-cycle
- 位置: blockbox 石切多步环——amethyst 3 步（cut_amethyst_from_polished → amethyst_block_from_cut → polished_amethyst_from_amethyst_block，A142–A144/0000）；mosaic 5 步（amethyst_mosaic → polished → mosaic → block → cut → mosaic…，A23–A33/0040）
- 问题: blockbox 的 `..._from_..._stonecutting` 配方构成闭合环，规划器沿石切树来回转换（polished→cut→block→polished / mosaic→polished→mosaic→block→cut→mosaic），无净产出，无标签。
- 原因: blockbox 石切配方两两可逆，被规划器在预算耗尽时反复展开成环（blockbox 为全批第 2 大配方系统，5604 条 EXECUTE）。
- 建议: 将 blockbox `..._from_..._stonecutting` 族纳入可逆转换标签，阻止无净产出的石切往返。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | loop-or-cycle | bad | blazing_crystal 块⇄晶体 + dielectric_rod 双形态振荡(A1-A16/A59-A77) |
| 0002 | loop-or-cycle | bad | glazedresymmetry 多步环 + glass⇄rough_glass 几何放大(8→24→84) |
| 0040 | loop-or-cycle | bad | draconium 块⇄锭⇄粒环 + blockbox mosaic 5 步环 |
| 0119 | loop-or-cycle | bad | void_steel 块⇄锭振荡(A1-A16) |
| 0042/0068/0069/0081/0083/0084/0086/0116/0117/0121/0122/0123 | amount/loop | questionable | AE2/Megacells/Appflux 256m 单元 crafts 51万–55万，共享同一组件锥被各链独立重算（跨链冗余，非链内异常，记入规则建议） |
| 其余 powah/appflux/ae2/draconic/cosmos 系链 | loop-or-cycle | questionable | 普遍含上述可逆转换振荡的某一变体 |
| warfare_wings*/tofucraft*/tinted_dyes* 等低 raw(≤14) 链 | other | questionable | 体量小、结构相对干净，但仍受 `|+N` 后缀污染与 recycling 环影响（已知问题） |

## 规则建议（给汇总者）
- targetRegex: 无新增（batch-001 目标均为合法物品：powah/appflux/ae2/megacells/draconic/cosmos/warfare_wings 等）。
- recipeRegex（收敛可逆转换族，证据：≥4 模组族、链 0000/0040/0119/0002 + 全批 118 条 compress/decompress 类 EXECUTE）：
  - `:(compress|decompress)/[a-z_]+_(block|ingot|nugget)$`
  - `_(block_craft|_block_decraft)$`（cosmos void_steel）
  - `blazing_crystal_block$|blazing_crystal$`、`dielectric_rod_h_?2?$|dielectric_rod_2$`
  - `blockbox:.*_from_.*_stonecutting`、`glazedresymmetry:.*(shard|slab|pillar|centered)`
  - `rough_glass_from_glass_stonecutting|glass_from_smelting_rough_glass`
- builtin 候选: 新增跨包通用标签 `pattern/reversible_conversion`（junk/duplicate 类收敛），覆盖所有「对称可逆」压缩/解压/石切/形态转换配方，使规划器在无净产出时不再往返放大。证据：本批涌现的 powah/cosmos/draconic/glazedresymmetry/blockbox/glass 六族同构振荡。
- 跨链冗余（minor）: AE2/Megacells/Appflux 256m 单元（crafts 51万–55万）各自独立重算同一 AE2 处理器/元件锥（portable_item_cell_256m / portable_fluid_cell_256m / portable_source_cell_256m 三者 crafts 均为 516277），建议规划器对共享组件锥做跨链缓存，降低重复 cone 体积。
- 已知问题（本轮不展开，仅提示）：`|+N` id 后缀污染（iron_pickaxe|+17 1845 行、glass|+48 409 行）、`intent/recycling` 回收环、anvil `jei:/synthetic` 交换阶梯——均已在标签/已知层处理。
