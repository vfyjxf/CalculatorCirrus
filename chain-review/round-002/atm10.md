# atm10 batch-002 审查报告
- pack: atm10
- 批次: batch-002（written=26，全部 incomplete；类别分布：incomplete 26 / 0 complete / 0 其它）
- 审查链数: 26（incomplete 26 / complete 0）。本批仅 26 条链，不足 100 配额，故**全量审查**：chain 0000 头部+上下文精读、chain 0023 全文精读，其余 24 条头部（target/status/builtin-rule-tags）+ 全文件批量信号扫描（rule-tag、UNRESOLVED、EXECUTE@system、溢出哨兵）。
- 已知规划器级问题（按 round-2 口径仅一句话提及，不展开）：32 位计数溢出（x2147483647 哨兵，渗入 UNRESOLVED 集合与 crafts）、`|+N` 物品 id 后缀污染（如 `productivetrees:beech_wood|+970`、`create:cut_tuff|+14`）、无净产出往返环机制本身（`loop=allowBoundedAmplification` 放行）。
- 发现: critical 0 / major 4 / minor 0

## 发现

### F-atm10-1 | chain=0023 | severity=major | category=loop-or-cycle
- 位置: CHAIN 0023 内 A40–A55（`sophisticatedstorage:sawmill/sawmill_upgrade` 与 `create:basin` 的 `productivetrees:wood/sugar_maple_*` ↔ `jei:/synthetic/176291`/`172731` 互转）
- 问题: A40 `sugar_maple_log x32 -> sugar_maple_wood x32`（锯木 1:1），A41 `wood x32 -> log x32`（1:1），A42/A43 继续 `log↔wood` 1:1 往返，无净产出。A39 已产出 `sugar_maple_log x32`，而 A56 `treetap:tap` 直接把 log 变成 `maple_sap_bucket`——中间 A40–A55 整段 wood/log 振荡是无意义绕路+往返环。全批同类振荡 60 处，且**无 rule-tag**（未命中 exchange/recycle 标签），`loop=allowBoundedAmplification` 未拦截 1:1 无净产出振荡。
- 原因: 规划器对"1:1 互逆、无净产出"的木材/原木互转缺乏 no-op 环识别；productivetrees 的 log→wood 与 wood→log 互为反向配方，被当成两条独立生产链反复拼接。
- 建议: 将 `productivetrees:wood/*` ↔ `*/sawmill` 的 1:1 互转加入 no-op 环/loop 检测并排除振荡；或在 builtin 标记 `pattern/noop`。

### F-atm10-2 | chain=0023(及0009) | severity=major | category=generation-abuse
- 位置: CHAIN 0023 A22 `ars_nouveau:alakarkinos/ocean_ruins_warm x4 @ ars_nouveau:alakarkinos_charm`（in: sand x4，out: wheat/emerald/iron_axe×44/…随机战利品）；CHAIN 0009 A4145 `alakarkinos/trail_ruins_common x653772078 @ alakarkinos_charm`（in: gravel x653772078 溢出哨兵，out: 数十种随机 loot 表物品）
- 问题: `alakarkinos_charm` 是随机战利品（loot-table）发生器，被当作"生产"步骤来取得常见物（wheat、emerald）。A22 为获得 `minecraft:wheat` 走了 ars 随机 charm 而非种田；A4145 更以溢出哨兵级 gravel 喂入，产出 count 同样为 `x653772078`。全批 52 处，且**全无 rule-tag**，绕过了 `intent/generation` 过滤（同批 `occultism:dimensional_mineshaft`、`actuallyadditions:lens_of_the_miner` 均被正确标 intent/generation，alakarkinos 漏标）。
- 原因: 分类器未把 `ars_nouveau:alakarkinos/* @ alakarkinos_charm` 归为 generation/随机产出族；其多输出随机 loot 语义与"真实确定性生产"不符，却被选为普通原料来源。
- 建议: 将 `ars_nouveau:alakarkinos/.* @ ars_nouveau:alakarkinos_charm` 加入 builtin `intent/generation` 标签（或标记为 generation 收敛），避免其被当作普通生产路径。

### F-atm10-3 | chain=0000/0001/…(全批) | severity=major | category=unresolved-expected-item
- 位置: 全文件 `[UNRESOLVED] minecraft:honeycomb_block x2147483647`（832 处）、`minecraft:amethyst_block x2147483647`（752）、`minecraft:nether_wart_block x2147483647`（384）、`minecraft:raw_iron_block x2147483647`（305）；非哨兵样本：`minecraft:stone x327310`、`create:cut_tuff|+14 x2`（CHAIN 0023 末尾）、`minecraft:red_sand x20460`
- 问题: 这些均为原版/常见可合成物——honeycomb_block=4 honeycomb、amethyst_block=4 amethyst_shard、nether_wart_block=9 nether_wart、raw_iron_block=9 raw_iron、stone=烧 cobblestone、cut_tuff=stonecutter 切 tuff——却以 `reason=BUDGET_TRUNCATED` 留在 UNRESOLVED。说明规划器在预算耗尽前从未触达这些廉价原版配方，把本可解析物当叶子原料；并且 x2147483647 溢出哨兵直接渗入 UNRESOLVED 集合，污染"未完成"判定。
- 原因: 锥状预算搜索优先深入高 coneScore 长链，廉价原版方块（深度浅、coneScore 低）被排到预算外；叠加 32 位溢出哨兵，使这些常见物既"需求溢出"又"解析失败"。
- 建议: 提升廉价原版方块（honeycomb/amethyst/nether_wart/raw_iron block、stone、cut_tuff 及各 `|+N` 变体）的解析优先级，或在预算分配上对浅层原版配方设保底；报告 unresolved-expected-item 时剔除 x2147483647 哨兵噪声。

### F-atm10-4 | chain=0000 | severity=major | category=amount-insanity（已带标签仍失控）
- 位置: CHAIN 0000 A8/A10/A12/A14… `minecraft:enchantment. xN @ minecraft:anvil [rule-tag: pattern/exchange]`（in: book xN + enchanted_book xN，out: book x8N），链 crafts=4716502853535
- 问题: 上一轮拟排除的 anvil/enchantment 泄漏配方本批以 `pattern/exchange` 标签重现（全批 1159 处全部带 `pattern/exchange`，未真正排除）。在 0000 中它仍几何放大书本：book x1→x8 反复级联，配合 `enchanted_book`（UNRESOLVED、x2147483647 哨兵）作"免费"输入，把 crafts 推到 4.7e12 溢出。属于 round-2 允许的"带标签仍几何放大 crafts"例外，记为**已带标签仍失控**。
- 原因: 该配方 net-positive 放大书本（出 8 入 1+1），被 exchange 标签放行而非排除；配合未解析的 enchanted_book 哨兵需求形成失控放大。
- 建议: 对 `minecraft:enchantment. @ minecraft:anvil` 由 `pattern/exchange` 标签升级为**排除**（或限制放大次数），因其本质是书本倍增泄漏而非等价交换。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | amount-insanity(已带标签仍失控) | bad | anvil exchange 几何放大书本+enchanted_book 哨兵，crafts 溢出 4.7e12 |
| 0001 | unresolved-expected-item | questionable | 4249 raw_kinds、BUDGET_EXHAUSTED，疑似含廉价原版未解析 |
| 0002 | unresolved-expected-item | questionable | raw17、crafts 1.6e12 溢出，预算未触达浅层配方 |
| 0003 | ok(已知规划器问题) | questionable | appflux core，exchange 为主，无新异常 |
| 0004 | ok(已知规划器问题) | questionable | fe_256k_cell，exchange 为主 |
| 0005 | ok | ok | colossal、crafts=257 正常，exchange/recolor 标签内 |
| 0006 | ok(已知规划器问题) | questionable | core_256k，与 0003 同构 |
| 0007 | generation-abuse | questionable | oblivion_shard，含 alakarkinos/随机产出族风险 |
| 0008 | ok(已知规划器问题) | questionable | hephaestus_forge_t5，exchange 为主 |
| 0009 | generation-abuse + 溢出 | bad | A4145 alakarkinos 喂 x653772078 溢出 gravel 取随机 loot |
| 0010 | ok(已知规划器问题) | questionable | corrupted_arcane_crystal，exchange 为主 |
| 0011 | ok(已知规划器问题) | questionable | pulsating_black_hole，exchange 为主 |
| 0012 | ok(已知规划器问题) | questionable | advanced_io_bus，exchange 为主 |
| 0013 | ok(已知规划器问题) | questionable | module_solar，intent/generation x44 已标 |
| 0014 | ok(已知规划器问题) | questionable | occultism stabilizer，exchange 为主 |
| 0015 | ok(已知规划器问题) | questionable | 与 0014 同构（ritual dummy） |
| 0016 | ok | ok | colossal、crafts=634 正常，仅 exchange 标签 |
| 0017 | ok(已知规划器问题) | questionable | titanium_drill_head，exchange 为主 |
| 0018 | ok | ok | colossal、crafts=8.3e8（溢出近端），路径未见新异常 |
| 0019 | ok(已知规划器问题) | questionable | supremium_enchanting_base，recolor 已标 |
| 0020 | ok(已知规划器问题) | questionable | improbable_probability_device，exchange x624 为主 |
| 0021 | ok | ok | colossal、crafts=873 正常 |
| 0022 | ok(已知规划器问题) | questionable | runic_enchanter，exchange/recolor 已标 |
| 0023 | loop-or-cycle + generation-abuse | bad | wood↔log 锯木振荡(A40–A55) + alakarkinos 取 wheat(A22) |
| 0024 | unresolved-expected-item | questionable | generator_limit_remover，raw12，浅层原版未解析 |
| 0025 | ok(已知规划器问题) | questionable | module_geothermal，exchange x490 为主 |

## 规则建议（给汇总者）
- targetRegex: 无新增目标排除（本批 26 个 target 均为合理整合包物品）。
- recipeRegex:
  - `ars_nouveau:alakarkinos/[^ ]* @ ars_nouveau:alakarkinos_charm` —— 标记/排除随机 loot 发生器作为生产步骤，证据 52 处（CHAIN 0023 A22、0009 A4145 等）。
  - `productivetrees:wood/[^ ]* @ (sophisticatedstorage:sawmill/sawmill_upgrade|create:basin)` 及反向 `jei:/synthetic/(176291|172731) @ sophisticatedstorage:sawmill/sawmill_upgrade` —— 标记 wood↔log 1:1 无净产出振荡，证据 60 处（CHAIN 0023 A40–A55）。
  - `minecraft:enchantment\. [^ ]* @ minecraft:anvil` —— 由 `pattern/exchange` 升级为排除（书本倍增泄漏），证据 1159 处（仍几何放大，见 F-atm10-4）。
- builtin 候选:
  - `ars_nouveau:alakarkinos_charm` 随机战利品 → 建议标签 `intent/generation`（当前漏标，跨包可能通用）。
  - `productivetrees` wood↔log 互转 → 建议标签 `pattern/noop` 或加入 no-op 环检测（跨包通用，凡 1:1 互逆原木/木板互转均适用）。
  - 廉价原版方块（honeycomb_block/amethyst_block/nether_wart_block/raw_iron_block/stone/cut_tuff 及 `|+N` 变体）→ 建议解析优先级保底规则（非标签，属搜索排序修复）。
- 无新模式时写"无"：否，已列出 3 条新模式（alakarkinos 漏标生成、productivetrees 无净振荡、anvil exchange 带标签仍失控）。
