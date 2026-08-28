# fom batch-002 审查报告
- pack: fom
- 批次: batch-002（written=85；incomplete 57 / colossal 2 / giant 11 / long 8 / medium 6 / short 1；trivial/compact 无）
- 审查链数: 85（全量，因总量不足 100；complete 端 28 条全读，incomplete 57 条经定向 grep 信号全覆盖 + 抽取 0001/0010/0029/0059 精读）
- 发现: critical 0 / major 0 / minor 0
- 结论: 本批未暴露任何超出 round-2 已豁免规划器级问题的新模式；所有链的问题均为「无净产出往返环机制本身」「pattern/exchange 标签内爆炸」「|+N 物品 id 后缀污染」三类，按 round-2 口径不再作为发现报告。

## 审查方法
- 小类别文件全读：chains-short-001（1 条）、chains-medium-001（6 条）、chains-long-001（8 条）、chains-giant-001（11 条）、chains-colossal-001（2 条），共 28 条 complete 全读。
- incomplete 大文件（1.39MB / 57 链 ≈ 24KB/链，未达「>100KB/链降采样」阈值，但体量仍大）用定向 grep 全覆盖：
  - `jei:` 0、`quest|villager|market|/loot|jer_|reward` 0 → 无 fake-production / 任务奖励 / 村民交易 / 展示页。
  - 空 `in :` 0 → 无 free-resource（无零输入产出）。
  - `2147483647` 0、`air|null|unknown|sentinel` 0 → 无 32 位哨兵 / 数据污染。
  - `generation` 0 → 无 generation-abuse。
  - `simulated:` 9（链 0060/0084）：`simulated:contraption_diagram`/`physics_assembler`/`velocity_sensor` 均为 `@ minecraft:crafting_table` 真实合成配方，非假生产。
  - `UNRESOLVED` 62，全部 `reason=RANK_TRUNCATED`；聚合后仅 `create:polished_cut_andesite_stairs|+14`（59 次）、`minecraft:black_wool|+14`（5 次）、`create:granite_pillar|+14`（2 次）——纯 |+N 标签污染，按 round-2 豁免。
  - `rule-tag` 2213 次，全部为 `pattern/exchange`(2175) / `pattern/recolor`(36) / `intent/recycling`(2)；`storage_ladder` 0 次（见规则建议）。
  - 抽取 0001（raw_iron 块往返 + |+14 exchange x39）、0010（raw_zinc/raw_copper/diamond 块往返 + exchange x59 + 盔甲模板复制）、0029、0059（emerald 块往返）精读确认结构。
- 机器-配方错配核查：石切/熔炼/高炉配方均落在对应机器；`minecraft:stone @ furnace` 为 cobblestone→stone 熔炼，无误。无 machine-mismatch。
- `crafts` 上限仅 932（链 0019），无 5+ 位数数量失控链，故无 amount-insanity。

## 发现
本批无新发现（critical 0 / major 0 / minor 0）。以下为已知、按 round-2 口径豁免、仅在链级判定一行提及的规划器级问题：
1. **无净产出往返环机制本身**（round-2 明确豁免，不重报）：emerald/diamond/lapis/redstone/raw_iron/raw_copper/raw_gold/raw_zinc 块↔物品、hay↔wheat、bone_meal↔bone_block、sandstone_slab↔chiseled_sandstone、create↔create_connected `_from_conversion` 等互逆往返，贯穿几乎全部链。
2. **pattern/exchange 标签内爆炸**（已带 `[rule-tag: pattern/exchange]`，不重报）：多数 incomplete 链 15–100 个石切交换步骤全架于 UNRESOLVED 的 `create:polished_cut_andesite_stairs|+14` 根上。
3. **|+N 物品 id 后缀污染 / 32 位计数溢出**（round-2 明确豁免，不重报）：UNRESOLVED 全是 `|+14` 变体；本批未见 2147483647 哨兵。

## 链级判定（代表性抽样，余链同模式）
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0003 | short | ok | warp_plate，干净生产链 |
| 0013/0016/0017/0021/0024 | medium | ok | 釉砖族（dye→clay→terracotta→glazed），已全读确认无往返 |
| 0057 | medium | ok | rabbit_stew，仅输入含 |+3/|+13 木板后缀（豁免污染） |
| 0020/0023 | giant | ok | 釉砖族，无块往返 |
| 0000 | long | bad | emerald↔block 无净产出往返（A1–A9） |
| 0025/0026/0027 | long | bad | hay↔wheat 无净产出往返（A1–A8） |
| 0035 | long | bad | raw_iron↔block 无净产出往返（A7–A8） |
| 0077/0081 | long | bad | emerald / raw_copper 块往返（A1–A9） |
| 0083 | long | bad | sandstone_slab↔chiseled_sandstone 振荡（A16–A23，exchange 机制） |
| 0004 | colossal | bad | raw_iron/diamond/redstone 块三套往返（A4–A61） |
| 0080 | colossal | bad | raw_copper 块往返 + redstone_link↔linked_transmitter `_from_conversion` 互逆（A21–A28） |
| 0006/0011/0018/0022/0082 | giant | questionable | lapis/emerald/diamond 块 4 步往返（已豁免） |
| 0007 | giant | bad | raw_gold 块往返（A3–A10）+ 真实转换 |
| 0030 | giant | questionable | diamond 块往返（A30–A38）+ exchange x4 |
| 0059 | giant | questionable | emerald 块往返（A1–A9），末端 mossy_waystone 生产正常 |
| 0001/0002/0004–0084 等 57 条 incomplete | incomplete | bad | exchange x15–x100 全架于 UNRESOLVED `polished_cut_andesite_stairs|+14` + 块往返 + |+N 未解析（同 round-001 F-fom-2 模式） |

## 规则建议（给汇总者）
- targetRegex: 本批无需要拉黑的 fake/垃圾 target（已 grep 确认无 jei:/quest/villager/market/loot/jer 命名空间）。暂不需要。
- recipeRegex: 本批未现需要新收敛的杂产/绕路配方族。
- builtin 候选（观察项，机制本身已按 round-2 豁免，仅建议打标以收口）：
  1. **fom 原版块↔物品往返未被 `storage_ladder` 覆盖**：本批 `storage_ladder` 标签出现 **0 次**，说明 round-2 称「已固化」的压缩阶梯标签并不适用于 fom（fom 未用 allthecompressed/quark/crate/IE 钢三态/cardboard）。fom 的 raw_iron/raw_copper/raw_gold/diamond/emerald/lapis/redstone/iron/gold/copper/hay/bone 块往返**全部未打标**。建议新增 fom 作用域的 `pattern-cyclic-amplification` 标签，覆盖 `minecraft:*_block`↔`minecraft:*` 及 `create:crafting/materials/raw_*_block`↔`create:crafting/materials/raw_*` 的互逆往返（证据：long/giant/colossal 多链，链 0010 中 `raw_zinc_block↔raw_zinc` 出现 251 次）。
  2. **`create_connected:crafting/kinetics/*_from_conversion` 互逆转换族**（redstone_link↔linked_transmitter，链 0080 A21–A28）：本批新增、未打标的往返族，建议同标 `pattern-cyclic-amplification` / junk（证据：链 0080 振荡 4 次净产出为零）。
  3. `minecraft:*_armor_trim_smithing_template` 复制（链 0010）：真实游戏机制（模板增殖），已正确标 `pattern/exchange`，无需动作。
- 32 位溢出 / |+N 污染 / 无净产出往返环：与 round-001 同源、round-2 已豁免，本批无新证据；随 round-001 数据层修复一并观察。
- 无新 fake-production / free-resource / data-pollution / impure / worse-alternative / silly-detour / tag-expansion / generation / catalyst / machine-mismatch 模式。
