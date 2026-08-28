# 链审查 Round 3 汇总（2026-08-29）

- 范围：13 个未收敛包的 batch-003（LBC batch-004），约 1,160 条链；规则自本批起确认加载
- 汇总发现：**critical 1 / major 25 / minor 8**（较 round-2 的 3/42/9 显著下降——已知模式被标签吸收）
- Round 2 后已收敛 10 包；本轮后预期更多包过 85% 收敛线

## 1. 逐包统计与新发现

| 包 | 批次 | 链数 | c/m/mi | 本批新问题 |
|---|---|---|---|---|
| All Of Create 2.1 | 003 | 79 | 0/2/2 | sturdy_sheet/refractory_mortar/diamond_shard 压缩族漏标；`simulated:` 占位目标 |
| atm10 | 003 | 148 | 0/2/1 | hay↔wheat 阶梯（1:1 与 1:9 I/O 不自洽）；superconductor MI↔MD 互逆；productivebees comb 环 |
| BlockFront | 003 | 70 | 1/1/0 | `#wool` any-of 坍缩到 black_wool 成环形依赖（20 链）；storage_ladder ×16 仍放大 |
| cobblemon-x-create | 003 | 126 | 0/3/1 | create raw_zinc 阶梯 3^16；createdeco netherite 正向漏标；tumblestone 2^16 |
| create-oneblock | 003 | 141 | 0/2/0 | 原版 blasting 配方 @encased_fan 机器错配（95 链）；装饰目标高权重 |
| CreateCustomised1.1.2 | 003 | 63 | 0/1/0 | create_generators cobblegen 被误标 exchange 绕过 generation 排除（14 链） |
| farmers-colonies | 003 | 104 | 0/1/0 | 酿造延时药水 no-op 链（potion 守恒吞红石，13 步） |
| fom | 003 | 65 | 0/0/1 | raw_zinc/brass/nethersteel/resin 阶梯漏标（长尾） |
| lost-world | 003 | 150 | 0/3/0 | ftbquests 屏幕物被选为目标（fake-production 复发）；silentgear 孤儿振荡族 |
| LuckyBlockChallenge | 004 | 70 | 0/0/0 | **零发现**——纯原版，已标签全覆盖；20 条 incomplete 全因 `\|+N` 污染 |
| mythcrafts | 003 | 35 | 0/2/0 | `#planks` 标签全量展开 519 成员；infernum/flint 9:1 漏标 |
| technical-electrical | 003 | 62 | 0/3/3 | compressed_iron/blazing_quartz/osmium/biosteel 等多 mod 阶梯长尾 |
| tfc-enhanced | 003 | 137 | 0/2/0 | stick_bundle 死分支梯；mossy cobble→wall→loose 零净环 |

## 2. 本轮规则动作

### builtin_rules.json（31 条）
- `storage_compression_ladders` 继续扩：`minecraft:wheat`、`create:crafting/materials/*_block`、
  `compressed_*` 前缀族、`storage_blocks/from_`、tumblestone、createdeco netherite、FD crate
- 新增 `cirrus:cobble_generators`（`create_generators:generator_*` → intent/generation，
  堵住 lava+water 免费石料绕过 generation 排除的漏洞）

### 过滤器（19 条，13 包）
目标排除：`simulated:` 占位（AOC）、ftbquests 屏幕物（lost-world）、`items_displayed:` 装饰
（create-oneblock）、limited_barrel_1-4 重复目标（cobblemon）、`_conversion$` 元物品（mythcrafts）。
收敛标记：各包 mod 专属阶梯长尾 + 染色基底坍缩（BlockFront）+ 药水 no-op（farmers-colonies）。

## 3. 规划器层新增/强化 backlog

1. **storage_ladder 已带标签仍放大**（多包复现 ×16/3^16）：需要折叠护栏而非仅打标签
2. **any-of 基底坍缩**：`#wool`→black_wool、`#planks`→全量 519 成员——应选未染/代表基底
3. **机器映射**：原版 blasting @create:encased_fan（create-oneblock 95 链）
4. **hay↔wheat I/O 不自洽**（atm10：同配方族 1:1 与 1:9 并存）——导出数据校验
5. `|+N` 归一化再次确认为 incomplete 最大单一来源（LBC 20/20、fom 全部 pending）

## 4. 下一轮

`chain-review.sh next` → batch-004；对仍未收敛包做 Round 4（预计剩 6-8 包）。
