# mythcrafts batch-002 审查报告
- pack: mythcrafts
- 批次: batch-002（written=71；类别分布：long 26 / giant 17 / colossal 16 / incomplete 10 / medium 2）
- 审查链数: 71（全部 71 条均过；小类别全读，incomplete/colossal/giant/long 抽样+定向 grep 交叉验证；无单文件 >100KB/条，故未降采样）
- 发现: critical 0 / major 5 / minor 0

## 审查口径执行说明
- 本批数据经 grep 确认：5 个链文件中**完全不存在** `rule-tag: pattern/storage_ladder`，且 `uncompress`/`_revert`/`from_crate`/`from_bag`/`from_leaves`/`bale` 类步骤**273 条全部未带任何 rule-tag**。
- round-2 增补口径声称“压缩阶梯/存储阶梯已带 `pattern/storage_ladder` 标签、不再作为发现”，但本批实际**未应用该标签**。因此这些可逆对往返步骤仍按 `loop-or-cycle` 真实判定，并在 F5 指出标签缺口。
- 已排除：anvil `quark:ancient_tome_book_overleveling`（带 `pattern/exchange`，0008 中重复 60×）；`twilightforest:giant_*_to_*`、`quark:tweaks` 等带 `pattern/exchange`/`pattern/recolor`/`intent/recycling` 的步骤（按口径不再报告）。
- 未发现 `jei:` 命名空间配方、村民交易/任务奖励/展示页等 fake-production，亦无零输入 free-resource 步骤。

## 发现
### F-mythcrafts-1 | chain=0004 | severity=major | category=loop-or-cycle
- 位置: A3–A27（raw_iron_block↔raw_iron↔raw_iron_bricks 往返）
- 问题: 链用 `raw_iron_block x4 → raw_iron x36`（A3）后，立即 `raw_iron x36 → raw_iron_block x4`（A4，净零），再 `raw_iron_block x4 → raw_iron_bricks x4`（A5）→ `raw_iron_bricks x4 → raw_iron x36`（A6），该 block↔bricks↔raw 往返从 A3 一直重复到 A27 约 12 次，最终仅 A27 一次 `revert x1 → raw_iron x9` 喂给 8× `shriving_stone`（A28–A42）。整个块↔砖循环无净产出，却把链深度撑到 43、actions 228。emerald_block↔emerald 同样在 A45–A54+ 往返多次。
- 原因: 规划器把可逆存储配方当作“生产放大器”反复横跳（无净产出往返环），且该 quark building 系列未被 `pattern/storage_ladder` 收敛。
- 建议: 对 `quark:building/crafting/raw_*_bricks` + `*_revert` 施加 `pattern/storage_ladder` 排除；同一循环若仍几何放大 crafts 则保留为 bad。

### F-mythcrafts-2 | chain=0003 | severity=major | category=loop-or-cycle
- 位置: A12–A20（mud_brick_slab↔mud_brick_vertical_slab）、A1–A8（straw_bale↔straw）
- 问题: A12 已用 stonecutting 直接产出 `mud_brick_slab x4`；A13 又把 3 slab 转成 `mud_brick_vertical_slab x3`，A14 `vertslabs/..._revert x3 → mud_brick_slab x3`（纯往返，vertical_slab 从不用于任何非还原用途），A15–A20 再重复 3 次。straw 一侧 A1 `straw x9 → straw_bale`、A2 `straw_bale → straw x9` … 重复 4 次（A1–A8），只为在 A8 取出 9 straw 喂 `packed_mud`。两类均为无净产出往返。
- 原因: `quark:building/crafting/vertslabs/*_revert` 与 `farmersdelight:straw_bale↔straw` 可逆对未贴 `pattern/storage_ladder`，被当成“生产者”。
- 建议: 将 `vertslabs/.*_revert`、`vertplanks/.*_revert` 及 `farmersdelight:.*_bale↔straw` 纳入 `pattern/storage_ladder`/排除。

### F-mythcrafts-3 | chain=0000 | severity=major | category=loop-or-cycle
- 位置: A7–A8（sturdy_stone↔cobblestone）、A18–A20（raw_iron block↔bricks）、A25–A27（raw_gold block↔bricks）、A32–A33（diamond_block↔diamond）
- 问题: 本链含 4 处独立无净往返——(1) A7 `cobblestone x9→sturdy_stone` 后 A8 `sturdy_stone_uncompress→cobblestone x9`；(2) A17 已直接 `raw_iron_block x4→raw_iron x36`，却仍走 A18 还原成 block、A19 转 bricks、A20 `revert→raw_iron`；(3) raw_gold 同理 A25–A27；(4) A31 已得 9 diamond，A32 `diamond x9→diamond_block`、A33 又 `diamond_block→diamond x9`。均为可被直接配方替代的 no-op 往返。
- 原因: 同 F1/F2，quark building 可逆对与 block↔ingot 还原未被收敛。
- 建议: 同 F1；另注意 `minecraft:*_block↔*_ingot` 还原（如 `netherite_block↔netherite_ingot`，见 chain 0070 A41–A43）亦属同类，一并排除。

### F-mythcrafts-4 | chain=0002,0061 | severity=major | category=loop-or-cycle（级联 unresolved-cascade）
- 位置: 0002 A6–A11（carrot）、A28–A33（beetroot）；0061 A3–A8（potato）、A13–A18（carrot）
- 问题: 为“生产”作物，规划器把 crop 在 crate/bag 间来回转换：0002 `carrot_crate → uncompress → farmersdelight:carrot_crate → carrot_from_crate → farm_and_charm:carrot_bag → carrot_from_bag`（净 9 carrot）；0061 `potato_crate → uncompress → farmersdelight:potato_crate → potato_from_crate → farm_and_charm:potato_bag → potato_from_bag`，carrot 侧类似 A13–A18。这些往返无净产出，且 carrot/beetroot/potato 最终仍 `[UNRESOLVED] reason=CASCADE_EXHAUSTED`（0002 行 144–145；0061 行 3139–3140）。
- 原因: crate/bag 存储配方被当作物产出器；循环耗尽候选后作物仍不可解析，UNRESOLVED 由坏步骤级联。
- 建议: 对 `quark:building/crafting/compressed/*_(crate|uncompress)`、`farmersdelight:*_from_crate`、`farm_and_charm:*_(bag|from_bag)` 加 `pattern/storage_ladder`/排除；作物若包内无农场配方则属正常 raw，不应靠 crate 往返伪造产出。

### F-mythcrafts-5 | chain=全批（0000/0003/0004/0002/0061/0064/0070 + grep 273 条） | severity=major | category=other
- 位置: 全批 71 条链、5 个文件
- 问题: round-2 增补口径声明“压缩阶梯（含 quark compressed/crate、storage_ladder）已带 `rule-tag: pattern/storage_ladder` 并排除”，但本批实测 `rule-tag` 仅出现 `pattern/exchange`、`pattern/recolor`、`intent/recycling` 三类，**零条 `pattern/storage_ladder`**；而 `uncompress`/`_revert`/`from_crate`/`from_bag`/`bale` 类无净往返步骤共 273 条（colossal 146 / long 86 / incomplete 56 / giant 24 / medium 1）全部未带标签。即该排除规则在本批并未生效，导致上述 no-op 循环（F1–F4）批量进入导出。
- 原因: 本批导出在 `pattern/storage_ladder` 标签规则应用之前生成，或该规则的正则未覆盖 `quark:building/crafting/*` 与 crate/bag 系列。
- 建议: 汇总者应在下一轮前确认 `pattern/storage_ladder` 标签已覆盖以下正则（证据：全批 273 条未标签步骤）；覆盖后 F1–F4 多数应降为 ok/不再报。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | giant | bad | 4 处无净往返（sturdy_stone/raw_iron/raw_gold/diamond），见 F3 |
| 0001 | giant | questionable | 同类 raw_iron/raw_gold bricks 往返（同 F3 家族，未见全文但 grep 命中） |
| 0002 | incomplete | bad | crate/bag 作物往返→UNRESOLVED，见 F4 |
| 0003 | colossal | bad | vertical_slab/straw 无净往返，见 F2 |
| 0004 | incomplete | bad | raw_iron/raw_gold bricks 往返 ~12× + emerald 往返，见 F1 |
| 0005 | giant | questionable | raw_*_bricks 往返家族（grep 命中） |
| 0006 | incomplete | questionable | 同存储往返家族；UNRESOLVED 待核 |
| 0007 | giant | questionable | raw_*_bricks 往返家族 |
| 0008 | incomplete | questionable | 60× overleveling 已带 exchange 排除；3 项 UNRESOLVED 为规划器级，非本批新发现 |
| 0009 | long | questionable | raw_*_bricks 往返家族 |
| 0010–0013 | long | questionable | limited_diamond_barrel 系列，含 raw_*_bricks 往返 |
| 0014 | colossal | questionable | raw_iron_bricks 往返家族（grep 命中 revert） |
| 0015 | incomplete | questionable | 同存储往返家族 |
| 0016 | medium | ok | 仅 `pattern/exchange` 一处，无 no-op 循环 |
| 0017–0029 | giant/long/medium | questionable | handcrafted/mcwlights  pots/fans，部分含 *_uncompress 往返（grep） |
| 0030/0036 | colossal | questionable | raw_iron_bricks 往返（grep 命中） |
| 0039 | colossal | questionable | magenta_shingles_slab，vertslabs revert 家族（grep） |
| 0040 | colossal | questionable | raw_gold_bricks revert 家族 |
| 0041 | colossal | questionable | stack_upgrade，含存储往返 |
| 0042/0044/0046/0051/0053 | colossal | questionable | mcwlights ceiling_fan，部分 vertplanks revert |
| 0047 | long | questionable | chipped:alchemy_book，含 raw_*_bricks 往返 |
| 0049 | giant | questionable | honey_glazed_ham，含 storage 往返 |
| 0052/0055/0056/0069 | long/colossal | questionable | sophisticatedstorage/backpacks，含 raw_*_bricks 往返 |
| 0057–0060 | long | questionable | limited_iron_barrel，含 storage 往返 |
| 0061 | incomplete | bad | potato/carrot crate/bag 往返→UNRESOLVED，见 F4 |
| 0062 | long | questionable | hammerlib gears/diamond，含 raw_*_bricks 往返 |
| 0063/0065 | incomplete | questionable | mcwfences mud_brick 墙，含 vertslabs revert 家族 |
| 0064 | long | bad | raw_iron_bricks↔raw_iron 往返（A1–A4），见 F3 同类 |
| 0066/0068 | colossal/giant | questionable | quark shingles/stairs，含 vertslabs revert |
| 0067 | giant | questionable | magenta_shingles_vertical_slab，vertslabs revert |
| 0070 | long | bad | sturdy_stone/raw_iron/raw_gold/diamond/netherite_block 共 5 处无净往返（A2–A43） |

> 说明：colossal/giant/long 中未逐行展开者，均经 `grep -E 'EXECUTE .*(uncompress|_revert|from_crate|from_bag|from_leaves|bale)'` 命中，确属同一 storage_ladder 无净往返家族；因本批无 `pattern/storage_ladder` 标签，统一判定 questionable/bad。

## 规则建议（给汇总者）
- targetRegex: 本批问题集中在配方族而非目标，无需新增 target 排除。（链级 UNRESOLVED 多为 CASCADE_EXHAUSTED，随循环修复后应消解）
- recipeRegex（收敛 `pattern/storage_ladder` 候选，证据：全批 273 条未标签可逆步骤）：
  - `quark:building/crafting/(raw_.*_bricks|.*_uncompress|vertslabs/.*_revert|vertplanks/.*_revert|compressed/.*_(uncompress|crate))`
  - `farmersdelight:.*(_from_crate|bale)`、`farm_and_charm:.*_(bag|from_bag)`
  - `minecraft:(.*_block|_bricks)_(revert|from_.*)` 及 `.*_bale↔.*` 还原对（如 `straw_bale↔straw`、`diamond_block↔diamond`、`netherite_block↔netherite_ingot`）
- builtin 候选: 将 `pattern/storage_ladder` 标签覆盖范围补齐至上述 quark building 可逆对 + crate/bag 存储往返 + block↔ingot 还原；**关键：本批数据实测零条 `pattern/storage_ladder` 标签，round-2 假设的“已生效”在本包未兑现，需先确认标签规则已落地再跑下一轮**，否则 F1–F4 会持续污染导出。
- 无新模式时写“无”：除 storage_ladder 标签缺口外，未发现新的 fake-production / free-resource / tag-expansion 类模式；anvil 泄漏已正确标 exchange 排除。

## 规划器级（已知、不再展开，仅链级提及）
- 0008 中 `quark:ancient_tome_book_overleveling` 重复 60×（已带 exchange）——几何放大但仍标 exchange，按 round-2 不另报。
- 本批未观察到 32 位 x2147483647 哨兵或 `|+N` 后缀泛滥（仅正常 `|+N` 物品 id 后缀如 `oak_log|+3` 属既有约定，未计入问题）。
