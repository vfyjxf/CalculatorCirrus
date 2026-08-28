# technical-electrical batch-002 审查报告
- pack: technical-electrical
- 批次: batch-002（written=95；类别分布 incomplete 51 / colossal 32 / giant 8 / long 4）
- 审查链数: 95（incomplete 51 / complete 44＝colossal32+giant8+long4；全量过，因总量<100 配额下限）
- 发现: critical 1 / major 1 / minor 0

## 审查口径说明
- 本批已生效上轮固化规则：anvil/enchantment 泄漏配方已排除；压缩阶梯（allthecompressed/quark compressed/crate/IE 钢三态/cardboard）带 `[rule-tag: pattern/storage_ladder]`；`quark:tweaks/crafting/slab_to_block`、`stonecutting/(chipped|chisel)_` 带 `[rule-tag: pattern/exchange]`；`intent/recycling` 同。带这些标签的步骤一律不报。
- 本批 `builtin-rule-tags` 仅 `pattern/exchange`、`intent/recycling`，**无** `storage_ladder` 标签行——即可逆 ingot↔block 9x9 转换在本批未被任何 rule-tag 标记。
- 已知规划器级问题（round-1）只在链级一句话提及：`|+N` 物品 id 后缀污染；32 位计数溢出哨兵 `x2147483647` 本批**未出现**（grep 全文件 0 命中）。

## 发现

### F-technical-electrical-1 | chain=0004,0003,0040,0081,0073,0088,0011,0026,0056,0062,0083 | severity=major | category=loop-or-cycle
- 位置: 各链 `*_(ingot_9x9|block_9x9)` 与 `minecraft:*_ingot_from_*_block` 等 EXECUTE 步骤（如 0004 A2–A9、A11–A19、A21–A28；0073 A10–A12）
- 问题: 可逆 ingot↔block 9x9 转换被规划器反复折叠成**零净产出往返环**——块形态被造出后立即 un-craft 回锭，从未以"块"真正被使用。
  - CHAIN 0004（giant, justdirethings:eclipsegate_wand）：A1 blast 出 9 个 eclipsealloy_ingot；A2 `eclipsealloy_ingot_9x9`→block；A3 `eclipsealloy_block_9x9`→9 ingot；如此 A2↔A3 重复 8 次后才交付 1 ingot 给法杖。blazegold(A11–A19, 8 次)、celestigem(A21–A28, 7 次) 同理。38 个 EXECUTE 中 32 个是 9x9 环步骤。
  - CHAIN 0003（colossal, polymorphic_wand_v2）：ferricore/eclipsealloy/celestigem/blazegold 四个 9x9 塔，50 个 9x9 环步骤，depth=37。
  - CHAIN 0073（incomplete, oritech:arcane_augment_station）：A10 blast raw_iron→iron_ingot×18；A11 `iron_block`×2（吃掉 18 ingot）；A12 `iron_ingot_from_iron_block`→18 ingot；随后 A14 才用这 18 ingot。A11↔A12 是纯 no-op 往返。该链 16 个 9x9 环步骤，crafts=13007。
  - CHAIN 0040（ae2:spatial_anchor）34 环、0081（sophisticatedbackpacks:advanced_alchemy_upgrade）32 环、0011/0026（mekanism_extras 罐）各 16 环、0088（incomplete）7 环。
- 原因: `loop=allowBoundedAmplification` 把可逆对当成"有净产出放大"，但 ingot→block→ingot 每整轮净产出为 0；规划器为凑出最终需要的少量锭，把单向转换反复往返。该族**不在**上轮固化的 `storage_ladder` 标签清单内（清单仅 allthecompressed/quark compressed/crate/IE 钢三态/cardboard），故未被收敛。
- 建议: 将可逆 ingot↔block 9x9 转换族纳入 `storage_ladder` 标签（见规则建议），对"造块即 un-craft 回锭"的零净往返做剪枝；否则这些链深度/动作数（0003 depth=37、crafts 膨胀到万级）严重失真，且无法靠现有 exchange/recycling 标签过滤。
- 波及配方族（文件级出现次数，均为 EXECUTE 行）: `minecraft:iron_ingot_from_iron_block` 83×、`justdirethings:ferricore_ingot_9x9`/`ferricore_block_9x9` 各 58×、`minecraft:gold_ingot_from_gold_block` 29×、`justdirethings:celestigem` 对 24×、`blazegold` 对 21×、`eclipsealloy` 对 20×、`minecraft:netherite_ingot_from_netherite_block` 4×、`electrodynamics:bronze_ingot_from_storage_block` 4×。即**原版（iron/gold/netherite）+ mod（justdirethings 四系、electrodynamics bronze）全族**皆未标签。

### F-technical-electrical-2 | chain=0000,0001,0002,0005,0007,0008,0009,0013,0014,0015,0016,0017,0018,0020,0021,0022,0023,0025,0028,0029,0031,0032,0033,0042,0043,0045,0048,0052,0054,0059,0061,0063,0069,0070,0073,0074,0075,0076,0086,0088,0092,0094 | severity=critical | category=unresolved-expected-item
- 位置: 各 incomplete 链 `[UNRESOLVED] minecraft:* ... (candidates=N, reason=BUDGET_TRUNCATED, truncated)`
- 问题: 42 条 incomplete 链把**原版常见物**标为 UNRESOLVED，而其图谱里明明存在生产者（`candidates>=1`），仅因 `budgetStop=maxRootInputBudget`/`maxGeneratedCandidates` 截断未展开。
  - CHAIN 0000：`[UNRESOLVED] minecraft:raw_iron x18 (candidates=1)`、`minecraft:stick|+1 x18 (candidates=4)`、`minecraft:furnace x2 (candidates=1)`、`minecraft:redstone x2 (candidates=5)`、`minecraft:iron_ingot x4 (candidates=5)`，全部 `reason=BUDGET_TRUNCATED`。
  - 跨批累计被截断的原版物（及候选数）：`stick`(c4)、`iron_ingot`(c5)、`redstone`(c5)、`gold_ingot`(c5)、`furnace`(c1)、`cobblestone`(c1/c3)、`raw_iron`(c1)、`diamond`(c5)、`emerald`(c5)、`obsidian`(c3)、`netherite_ingot`(c5)、`carrot`(c1)、`paper`(c5)、`leather`(c4)、`iron_block`(c1)、`redstone_block`(c1)、`sticky_piston`(c2)、`lapis_block`(c1)、`stone`(c2)、`oak_log`(c1)、`gray_dye`(c5)、`allium`(c1)。这些全部是 base-game 可获取物。
- 原因: 规划器预算/排名截断在展开基础游戏常见物之前就停止；`candidates>=1` 证明图谱中存在合法生产者，并非不可生产。属"明显本可解析却 unresolved"。
- 建议: 在截断前对已知原版常见物做预解析或加入"已知 vanilla raw"白名单，使其不再作为 UNRESOLVED 叶子；或在预算分配里给 base-game 物品更高优先级。否则 42 条 incomplete 链的真实未解析度被高估，且与上轮 `|+N`/溢出等规划器级问题叠加，难判真实缺口。
- 注: 其中 `minecraft:stick|+1`、`cobblestone|+4`、`gray_dye|+15` 等是 `|+N` 污染 id 的变体（见下"已知规划器级问题"），污染 id 被当作独立未解析物，进一步放大了假 UNRESOLVED。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0004 | loop-or-cycle | questionable | 9x9 ingot↔block 三塔共 32 环步骤，零净产出（F1） |
| 0003 | loop-or-cycle | questionable | polymorphic_wand_v2 含 50 个 9x9 环步骤，depth=37（F1） |
| 0040 | loop-or-cycle | questionable | ae2:spatial_anchor 34 个 9x9 环步骤（F1） |
| 0081 | loop-or-cycle | questionable | advanced_alchemy_upgrade 32 个 9x9 环步骤（F1） |
| 0011/0026 | loop-or-cycle | questionable | mekanism_extras 罐各 16 个 9x9 环步骤（F1） |
| 0073 | loop-or-cycle+unresolved | questionable | iron_block 零净往返 + crafts=13007 + 含原版 UNRESOLVED（F1/F2） |
| 0088 | loop-or-cycle+unresolved | questionable | 7 个 9x9 环 + 含原版 UNRESOLVED（F1/F2） |
| 0056/0062/0083 | loop-or-cycle | questionable | justdirethings/handcrafted 长链被 lapis/blazegold 9x9 环放大（F1） |
| 0000 | unresolved-expected-item | bad | 原版 raw_iron/stick/furnace/redstone/iron_ingot 被 BUDGET_TRUNCATED（F2） |
| 0001,0002,0005,0007,0008,0009,0013,0014,0015,0016,0017,0018,0020,0021,0022,0023,0025,0028,0029,0031,0032,0033,0042,0043,0045,0048,0052,0054,0059,0061,0063,0069,0070,0074,0075,0076,0086,0092,0094 | unresolved-expected-item | bad | 各含 ≥1 原版 UNRESOLVED（candidates>=1, BUDGET_TRUNCATED），同 F2 |
| 0034 | mega-wide | questionable | oritech:unstable_container 189 深度/mega-wide，含 F2 类原版 UNRESOLVED |
| 其余 colossal/giant/long 无 9x9 环且无原版 UNRESOLVED 者（如 0035–0039 handcrafted 盆、0046/0047/0049/0050/0051/0053/0057/0058/0060/0068/0071/0072/0077/0079/0080/0082/0084/0085/0089/0091/0093 等） | — | ok | 带 tagged recycling/exchange 的按口径不报；路径质量未见新问题 |

## 规则建议（给汇总者）
- targetRegex: 无（本批无 fake-production / free-resource 目标需排除；问题在配方层循环与截断，不在目标）。
- recipeRegex: 收敛可逆 ingot↔block 9x9 转换族，建议匹配
  `(justdirethings:(ferricore|eclipsealloy|celestigem|blazegold)_(ingot_9x9|block_9x9))|(minecraft:(iron|gold|netherite|copper|diamond|emerald|redstone|lapis)_ingot_from_[a-z_]+_block)|(electrodynamics:[a-z]+_ingot_from_storage_block)`
  → 标 `pattern/storage_ladder`（证据：iron_ingot_from_iron_block 83×、ferricore 对 58×、gold 29×、celestigem 24×、blazegold 21×、eclipsealloy 20×、netherite 4×、electrodynamics 4×，合计 300+ 环步骤，分布于 16+ 条链）。
- builtin 候选: 上轮 `storage_ladder` 标签清单（allthecompressed/quark compressed/crate/IE 钢三态/cardboard）**应扩展**到任意可逆 `<metal>_ingot_9x9`↔`<metal>_block_9x9` 以及原版 `<metal>_block`↔`<metal>_ingot` 对（junk/duplicate 风格：判为零净产出可逆对并剪枝往返）。建议标签 `junk/loop-noop` 或并入现有 `storage_ladder`。
- 计划器层（非 rule，转交规划器）: 原版常见物（stick/iron_ingot/redstone/gold_ingot/furnace/cobblestone/raw_iron/diamond/emerald/obsidian/netherite_ingot/carrot/paper/leather/iron_block/redstone_block/sticky_piston/lapis_block/stone/oak_log/allium/gray_dye 等）在预算截断前预解析或加白名单，消除 F2 的假 UNRESOLVED（42 链、各物 candidates 1–5）。
- 已知规划器级问题一句话: `|+N` 物品 id 后缀污染本批依旧普遍（73× `stripped_cherry_wood|+280`、55× `stick|+1`、50× `diorite|+7`、45× `stripped_oak_wood|+3`、40× `cobblestone|+4`、36× `gravel|+1`、16× `gray_dye|+15`…），且不仅作叶子，还作为**实际被消耗输入**出现（CHAIN 0073 A14 用 `minecraft:stick|+1 x18`、A17 用 `immersiveengineering:dust_copper|+4 x9`）；32 位哨兵 `x2147483647` 本批未出现。此二项按 round-2 口径不展开，仅提示其在本批的真实影响。
