# technical-electrical batch-005 审查报告

- pack: technical-electrical
- 批次: batch-005（written=12，类别分布：incomplete 7 / colossal 5；manifest 标注 classification 命中 pattern/exchange 1682、pattern/storage_ladder 208、pattern/recolor 327、intent/recycling 113）
- 审查链数: 12（incomplete 7 / complete 5；全量审查——批次仅 12 条，未抽样）
- 发现: critical 0 / major 2 / minor 0

## 发现问题

### F-technical-electrical-1 | chain=0009,0010 | severity=major | category=loop-or-cycle
- 位置: `justdirethings:ferricore_ingot_9x9` ↔ `justdirethings:ferricore_block_9x9`（CHAIN 0009 A2–A17；CHAIN 0010 A2–A17），@ minecraft:crafting_table，全程**无 rule-tag**
- 问题: A1 产 `ferricore_ingot x9`；A2 将 9 锭→1 块（ingot_9x9），A3 将 1 块→9 锭（block_9x9），此互逆对重复 **8 次**（A2↔A17 共 16 步），末步 A18 仅消耗 `ferricore_ingot x4`。每一轮 `9 锭→1 块→9 锭` **净产出为零**，是纯 no-op 往返环重复多次；A1 的 9 锭本可直接满足 A18 的 4 锭需求。
- 原因: 这是 `block↔ingot` 压缩/解压互逆环，但 `storage_ladder` 规则未对其打标（本链 builtin 标注为 pattern/exchange x75、pattern/storage_ladder x17，17 个 tag 落在玻璃/蜂蜜等别处，9x9 环未被收敛）。在 `loop=allowBoundedAmplification` 下规划器把该环当可放大步骤反复下钻，形成无界 no-op 放大（属"已带标签仍无界放大"的**未打标**变体）。
- 建议: 将 `justdirethings:*_(ingot|block)_9x9` 互逆对纳入 storage_ladder / 新增 `pattern/block_loop` 收敛；证据 CHAIN 0009、0010。

### F-technical-electrical-2 | chain=0003 | severity=major | category=loop-or-cycle
- 位置: `ae2:decorative/certus_quartz_pillar_from_stonecutting` → `ae2:misc/deconstruction_certus_quartz_pillar` → `ae2:decorative/quartz_block` → `ae2:decorative/cut_quartz_block_from_stonecutting`（CHAIN 0003 A80–A97），全程**无 rule-tag**
- 问题: 闭环 `cut_quartz_block(9) → quartz_pillar(9) → certus_quartz_crystal(36) → quartz_block(9) → cut_quartz_block(9)`，晶体在环内产生即被下步消耗，**净产出为零**，此环重复 **5 次**（A80–A97）。仅最末 A97 的 `certus_quartz_crystal x36` 真正喂给 A98（cell_component_1k）；前 4 轮整段为冗余 no-op 往返。
- 原因: `ae2:misc/deconstruction_*` 解构台配方 + stonecutting 变体构成闭合环，`storage_ladder`/`pattern/exchange` 均未覆盖（本链 storage_ladder x38、exchange x32 落在红石/青金石/玻璃变体上，该解构环漏标）。属未打标的无净产出往返环。
- 建议: 将 `ae2:misc/deconstruction_*` 与对应 `*_from_stonecutting` + `decorative/quartz_block` 环纳入收敛规则；证据 CHAIN 0003。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | questionable | storage_ladder/exchange 已打标；UNRESOLVED `enori_crystal` 为 AA 晶体原矿(CASCADE_EXHAUSTED)，`\|+N` 污染已知规划器级问题 |
| 0001 | incomplete | questionable | storage_ladder x47 放大（obsidian 多级压缩）；UNRESOLVED `block_double_compressed_stone` CASCADE_EXHAUSTED |
| 0002 | incomplete | questionable | storage_ladder x27 + exchange x15 已打标；UNRESOLVED `create:cut_andesite_brick_stairs\|+14` RANK_TRUNCATED（已知 `\|+N`/截断） |
| 0003 | colossal | **bad** | 见 F-2：ae2 解构台闭合 no-op 环未打标；其余 exchange/storage_ladder 已收敛 |
| 0004 | incomplete | questionable | storage_ladder x80 放大；UNRESOLVED 多项 RANK/BUDGET_TRUNCATED + `\|+N` + any-of 木材全展开（已知规划器级） |
| 0005 | incomplete | questionable | storage_ladder x22 已打标；`oritech:*_blockblock`↔`*_blockblockinv` 单对未打标（轻微）；UNRESOLVED `\|+N`/BUDGET_TRUNCATED |
| 0006 | incomplete | questionable | storage_ladder x72 放大；primalmagick quartz nugget 环已标 recycling；UNRESOLVED `cut_andesite_brick_stairs\|+14` RANK_TRUNCATED |
| 0007 | colossal | ok | exchange x64 + storage_ladder x19 已收敛；moderndynamics:machine_extender 正常 |
| 0008 | colossal | ok | exchange x32 + storage_ladder x52 已收敛；pneumaticcraft:pneumatic_leggings 正常 |
| 0009 | colossal | **bad** | 见 F-1：justdirethings 9x9 块↔锭 no-op 环未打标重复 8 次 |
| 0010 | colossal | **bad** | 见 F-1：与 0009 同构（目标为 blazegold_leggings，仍走 ferricore 9x9 环） |
| 0011 | incomplete | questionable | recycling/exchange/recolor/storage_ladder 已打标；UNRESOLVED `blazing_quartz` CASCADE_EXHAUSTED + `\|+N`；oritech blockblock 单对未打标 |

## 规则建议（给汇总者）

- targetRegex: 无新排除目标（本批 12 目标均属合理生产目标，无 fake-production / free-resource）。
- recipeRegex: `^justdirethings:.*_(ingot|block)_9x9$` `^ae2:misc/deconstruction_.*$` `^oritech:.*_blockblock(inv)?$` —— 收敛 reciprocal 块↔锭 / 解构台闭合环（storage_ladder 未覆盖），证据条数：0009、0010、0003、0005、0011。
- builtin 候选: `pattern/block_loop`（或扩展 storage_ladder）覆盖跨模 `block↔ingot` 互逆对与 ae2 解构台环，建议标签 duplicate/junk；证据 CHAIN 0009/0010（9x9 环重复 8 次）、0003（解构环重复 5 次）、0005/0011（oritech blockblock 单对）。
- 备注: `\|+N` id 后缀污染、BUDGET/RANK_TRUNCATED、any-of 木材/砂石全展开、已打标仍无界放大等均为已知规划器级问题，按口径仅在链级一句话提及，不计入本轮新发现。
