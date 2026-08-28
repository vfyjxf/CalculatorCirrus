# 链审查 Round 2 汇总（2026-08-29）

- 范围：15 个未收敛包的最新批次（14 包 batch-002 + minecolonies batch-001 + LBC batch-003），约 1,500 条链
- 汇总发现：**critical 3 / major 42 / minor 9**
- 本轮结束后已收敛（≥85%，转观察）：atm10-lite、create-lets-create、creative-drawers-producer-2、enigmatica10、Brick's Building、create-train、civilization-isolaria（7 包，Round 1 后达成）

## 0. 元发现：round-1 内置规则未生效（已修复）

多个 agent（mythcrafts、fom、farmers-colonies、create-ultimate、LBC）报告
`pattern/storage_ladder` 零命中、anvil 泄漏配方仍出现。核实：
`chainExportClasspath` 任务只解析 classpath 不触发资源拷贝，batch-002 用的是
8 月 27 日的旧 `builtin_rules.json`。已修复：任务加 `dependsOn 'testClasses'`，
并验证 `anvil_leak` 已进入 build resources。**round-2 规则扩展自 batch-003 起真正生效。**

## 1. 逐包统计

| 包 | 批次 | 审查链数 | critical | major | minor | 本批新问题 |
|---|---|---|---|---|---|---|
| All Of Create 2.1 | 002 | 118 | 0 | 3 | 1 | createdeco 砖变体石切闭环 50+；#planks 标签乘积爆炸 |
| atm10 | 002 | 26 | 0 | 4 | 0 | productivetrees wood↔log 振荡；alakarkinos 随机 loot 当生产 |
| BlockFront | 002 | 75 | 0 | 1 | 0 | smithing_trim 破映射（base 类型≠输出） |
| cobblemon-x-create | 002 | 96 | 0 | 2 | 1 | exp_candy ×3 阶梯 + recc 反馈环 |
| create-oneblock | 002 | 57 | 1 | 1 | 0 | anvil 泄漏带 exchange 标签重现；背包升级物品被当机器（308 步） |
| create-ultimate | 002 | 100 | 0 | 4 | 1 | createdeco/dndecor/garnished 变体闭环 54 链；vault/container 1:1 互转 |
| CreateCustomised1.1.2 | 002 | 99 | 0 | 3 | 2 | FD straw/canvas 打包环 39 链；graphite 煤→免费钻石 |
| farmers-colonies | 002 | 126 | 0 | 2 | 1 | 原版 1↔9 互转 452 步未标签（规则未生效） |
| fom | 002 | 85 | 0 | 0 | 0 | 无新模式（主导为已知往返环 + `\|+N`） |
| lost-world | 002 | 131 | 0 | 0 | 3 | anvil 修复泄漏以 exchange 标签重现（同元发现） |
| LuckyBlockChallenge | 003 | 74 | 1 | 4 | 0 | 原版可逆压缩 37 链未标签；#wool 坍缩为 black_wool |
| minecolonies-dimensional-adventure | 001 | 150 | 0 | 5 | 0 | powah/cosmos/draconium 三态环；blockbox/glazedresymmetry 石切闭环 |
| mythcrafts | 002 | 71 | 0 | 5 | 0 | quark 原砖/竖台阶还原环；crate/bag 伪造产出 |
| technical-electrical | 002 | 95 | 1 | 1 | 0 | justdirethings 合金 9x9 环 300+ 步；42 链原版物假 UNRESOLVED |
| tfc-enhanced | 002 | 143 | 0 | 3 | 0 | any-of 全成员进 RAW（raw_kinds 134）；逐级重染级联 |

## 2. 跨包模式与规则动作

### 通用可逆转换族（round-2 最大共识，8+ 包）
round-1 的 `storage_compression_ladders` 只覆盖 5 个 mod 家族；原版
`*_block↔*` 1↔9/1↔4 对、`_from_*_block` 解包、nugget 阶梯、quark 原砖/竖台
阶还原、FD 稻草/帆布、powah/dielectric、`_from_conversion`、
productivetrees 锯木全部漏标。**已扩入同一条规则**（30 条规则总计）。

### 新增规则

| 规则 | 匹配 | 标签 | 证据 |
|---|---|---|---|
| `cirrus:variant_stonecutting_cycles` | createdeco/dndecor/garnished/blockbox/glazedresymmetry 的 stonecutting/shard/slab/pillar/centered 路径 | pattern/exchange | create-ultimate 54 链、AOC 50+、minecolonies |
| `cirrus:smithing_trim_swaps` | `_smithing_trim$` | pattern/exchange | BlockFront 0061、LBC 0060（破映射待数据层修复） |
| `cirrus:resource_generators`（扩展） | + `alakarkinos`、`graphite_processing` | intent/generation | atm10 52 处、CreateCustomised 14 链 |

### 新增过滤器（20 条，13 包）
要点：createdeco 变体（AOC/create-ultimate）、productivetrees/alakarkinos
（atm10）、exp_candy 阶梯（cobblemon）、anvil 兜底（create-oneblock/lost-world）、
quark 变体还原 + crate/bag（mythcrafts）、原版对（LBC/farmers-colonies）、
可逆转换（minecolonies/technical-electrical/fom/CreateCustomised）。

## 3. 规划器/数据层新增 backlog

1. **机器映射**：sophisticatedbackpacks 升级物品被当 EXECUTE 机器（create-oneblock 308 步/24 链）
2. **smithing_trim 数据校验**：输出盔甲类型必须等于 base（BlockFront/LBC 破映射）
3. **未染色基底优先**：tfc 重染级联（5 链拖入 16 色羊毛）；`#wool` 坍缩为 black_wool
4. **锥缓存跨链共享**：minecolonies 三款 256m 单元各自重算同一 51 万 crafts 锥
5. 原版常见物预算优先保护（technical-electrical 42 链假 UNRESOLVED，多包复现）

## 4. 下一轮

`chain-review.sh next`（规则已确认加载）→ 对仍未收敛的包做 Round 3。
