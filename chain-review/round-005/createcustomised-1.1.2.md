# CreateCustomised1.1.2 batch-005 审查报告
- pack: CreateCustomised1.1.2
- 批次: batch-005（written=31，全部为 incomplete 类别，无 trivial/short/medium/long/giant/colossal 子文件）
- 审查链数: 31（incomplete 31 / complete 0；本批仅 31 条链，按口径"不足则全量"全审，未抽样）
- 发现: critical 0 / major 0 / minor 0

## 发现
无新模式。本轮（round-5 最终验证）未发现任何超出前四轮已固化规则（storage_ladder / exchange / recolor / recycling / generation / junk）及已知规划器级问题之外的**新可疑配方族或目标类**。审查层收敛。

支撑证据（全批 31 链）：
- 所有 EXECUTE 机台均为合法 Create/原版机器：`minecraft:stonecutter`(1747)、`crafting_table`(783)、`create:mechanical_crafter`(634)、`create:basin`(297)、`create:encased_fan`(224)、`create:mechanical_mixer`(203)、`create:depot`(190)、`create:crushing_wheel`(109)、`create:millstone`(17)、`minecraft:smoker`(29)、`minecraft:blast_furnace`(4)、`minecraft:furnace`(1) 等。**无任务奖励页 / 村民交易 / 市场 / JER 展示页 / quest / trade 类伪生产系统**。
- 所有 `[rule-tag: ...]` 仅属 5 个已知族：`pattern/exchange`(1781)、`pattern/storage_ladder`(合计数百)、`intent/recycling`(262)、`pattern/recolor`(91)、`intent/generation`(3)。无第六族。
- 无 `in :` 为空的 free-resource 步骤；无 fake-production。
- 全部 `[UNRESOLVED]` 均为 `reason=BUDGET_TRUNCATED`（规划器预算耗尽，属 incomplete 预期），非"本应可解析却未解析"的新目标类。其中部分带 `|+17`/`|+9` id 后缀污染（已知规划器问题，不计入新发现）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 linked_controller | incomplete | ok | 全 exchange/storage_ladder/recycling 环，`create:cut_andesite\|+17` 为已知 id 后缀污染 |
| 0001 track_warped_wide | incomplete | ok | 已知三族覆盖 |
| 0002 large_water_wheel | incomplete | ok | 已知三族覆盖 |
| 0003 melon_juice_fluid | incomplete | ok | `minecraft:melon x2147483647` 为 32 位溢出哨兵（已知） |
| 0004 black_concrete | incomplete | ok | exchange/splashing 已知 |
| 0005 magenta_concrete | incomplete | ok | 同上 |
| 0006 green_concrete | incomplete | ok | 同上 |
| 0007 light_blue_concrete | incomplete | ok | 同上 |
| 0008 pink_concrete | incomplete | ok | 同上 |
| 0009 gray_concrete | incomplete | ok | 同上 |
| 0010 chef_hat_with_goggles | incomplete | ok | 已知三族覆盖 |
| 0011 light_gray_concrete | incomplete | ok | 同上 |
| 0012 cyan_concrete | incomplete | ok | 同上 |
| 0013 pumpkin_pie | incomplete | ok | 含 `vbincubationcompat:blue_egg_crate` 属 storage_ladder crate 族（已覆盖） |
| 0014 bacon_and_eggs | incomplete | ok | 含 `incubation:chicken_egg_crate` 属 storage_ladder crate 族（已覆盖） |
| 0015 nixie_board | incomplete | ok | 已知三族覆盖 |
| 0016 nether_brick_fence | incomplete | ok | 全 exchange |
| 0017 brass_lamp | incomplete | ok | 已知三族覆盖 |
| 0018 grindstone | incomplete | ok | 已知三族覆盖 |
| 0019 lime_concrete | incomplete | ok | `create:milling/cactus`@crushing_wheel 为机器标签小瑕疵（已知机器映射问题桶） |
| 0020 blue_concrete | incomplete | ok | exchange/splashing 已知 |
| 0021 large_nixie_tube | incomplete | ok | 已知三族覆盖 |
| 0022 orange_concrete | incomplete | ok | 同上 |
| 0023 chain_pulley | incomplete | ok | 已知三族覆盖 |
| 0024 nixie_tube | incomplete | ok | 全 exchange |
| 0025 rose_quartz_lamp | incomplete | ok | 已知三族覆盖 |
| 0026 integer_selector | incomplete | ok | 已知三族覆盖 |
| 0027 lodestone | incomplete | ok | 已知 exchange |
| 0028 end_crystal | incomplete | ok | `blaze_powder`@mechanical_crafter 为已知机器映射问题（不新） |
| 0029 smoker | incomplete | ok | 已知三族覆盖 |
| 0030 track_blackstone | incomplete | ok | 已知 exchange |

## 规则建议（给汇总者）
无新模式。以下仅为既有规则收敛度的补强建议（非新问题，不计入发现）：

- **exchange 覆盖率补强（minor）**：少量单步 stonecutting 切割未被打 `pattern/exchange` 标签，如 `create:small_ochrum_brick_slab_from_stone_types_ochrum_stonecutting`(CHAIN 0001)、`bits_n_bobs:*`/`create:*` 的个别 stonecutter 单切步骤（如 CHAIN 0000 A45 `bits_n_bobs:andesite_tile_slab`）。证据：全批未打标 EXECUTE 步骤中，除已知规划器问题外，仅剩 stonecutting 单切与 crate 两类，均属既有族。建议将"任意单步 stonecutter 同料 variant 切割"明确纳入 `pattern/exchange` 正则，避免后续批次漏标。
- **storage_ladder 覆盖率补强（minor）**：`incubation:chicken_egg_crate` / `vbincubationcompat:blue_egg_crate`（crate 打包↔`incubation:egg` 解包）属 crate/bag 族，但本批未打 `pattern/storage_ladder` 标签（CHAIN 0013 A559、0014 A291、0019 A30）。建议 `storage_ladder` 的 crate/bag 正则显式加入 `incubation:*` / `vbincubationcompat:*` 命名空间。证据条数：3 条链命中。
- **机器映射小瑕疵（已知桶，不新）**：`minecraft:blaze_powder`@mechanical_crafter(0028 A3)、`minecraft:ender_eye`@mechanical_mixer(0028 A4)、`create:milling/cactus`@crushing_wheel(0019 A28) 等将原版/Create 配方标到非常规机台，属已知"机器映射错配"规划器问题，已在前序轮固化，仅在此一行提及。

以上均不构成本轮新发现；审查层在本批已收敛。
