# atm10 batch-005 审查报告（round-6 选路质量深审）

- pack: atm10
- 批次: batch-005（written=14 条，全部 `incomplete` + `mega-wide`；本批仅有 14 条链，已全量审查，无法凑满 ≥60 条链配额）
- 审查链数: 14（incomplete 14 / complete 0；抽样说明：全 14 条链，提取非 rule-tagged 的 EXECUTE 步共 30,577 条，去重得 530 个非平凡中间品，逐一用 producers 索引交叉核对）
- 发现: critical 0 / major 0 / minor 0

## 方法论与关键结论

本批 14 条链均为 **mega-wide 探索锥**（`search: ... budgetStop=maxRootInputBudget [BUDGET_EXHAUSTED]`，链尾大量 `[UNRESOLVED] ... reason=BUDGET_TRUNCATED`）。
这意味着 planner 并非"选一条生产者路径"，而是**枚举了每个中间品几乎所有候选生产者**——
因此"选路质量"的判定应落在：**锥中是否包含合理（更优/更纯/更直连）的生产者**，而非"只选了坏的那条"。

交叉核对做法：从链中提取非 `rule-tag`（exchange/recolor/recycling/storage_ladder/generation 已排除）的 EXECUTE 步，
对其 530 个去重中间品调用 `cirrus-audit-data/atm10/producers.tsv` + `producers.jsonl` 比对生产者数、输入/输出对比。

**核对结果（证据）**：
- 514/530 个中间品在索引中有条目；16 个缺失项均为 MI 流体/原料（如 `modern_industrialization:lubricant`、`minecraft:water`），属正常原材料，不计。
- 对"链用了多杂产配方、索引里是否有更纯直接配方"的候选扫描：绝大多数"更优"条目经核实是**索引伪生产者**——
  `jei:category/ftbquests/quest`（任务奖励页，~3,902 条）、`minecraft:enchantment.`（铁砧附魔展示页，~39,857 条）、
  `jei:category/minecolonies/*`、`jei:category/securitycraft/*` 转换页。链**正确规避**了这些，选用了真实配方。
- 抽样验证链内确实包含纯配方：`minecraft:raw_iron`（纯 uncraft）被使用 132 次、`minecraft:raw_copper` 4 次、
  `allthecompressed:decompress/sand_1x` 71 次 —— 即"更纯/更直连"的候选在锥中**已存在**，并非被遗漏。
- 111 个中间品经 `jei:/synthetic/N`（planner 自有合成复合配方）到达（共 351 个 EXECUTE 步）；这些是 planner 的子装配，内部已用纯子配方，不属"选了多杂产配方"。

结论：本批**未检出**可判 major 的 `worse-alternative` / `silly-detour` / `impure-recipe`。
所有"看起来更差"的候选，要么锥内另含更优生产者（权衡可辩护，判 ok），要么所谓"更优"来自索引伪生产者（链已正确规避）。

## 发现

无 critical / major / minor 发现。

### 候选项复核（均判 ok，记录备查）
- `alltheores:nickel_ingot` 锥内用 `alltheores:arcfurnace/nickel/from_raw`（副产 slag）；纯候选 `minecraft:nickel_ingot_from_smelting_nickel_raw` 存在。→ 机器产线 vs 熔炉，副产系科技 mod 常态，可辩护 ok。
- `minecraft:flint` 用 `enderio:sag_milling/gravel`（3 输出）；纯候选 `supplementaries:flint_block_uncrafting` 存在。→ 碎矿副产属正常，ok。
- `minecraft:string` 用 `ars_elemental:wool_to_string`；纯候选 `supplementaries:strings` 存在。→ 不同来源等价，ok。
- `modern_industrialization:beryllium_dust` / `quartz_dust` / `sodium_dust` / `yttrium_tiny_dust` 用 MI 电解/离心（副产）分支；纯候选（macerator / dust_from_block / tiny_dust_from_dust）在索引存在。→ MI 标准工艺分支，ok。
- `create:experience_nugget` 用 `create:crushing/nether_gold_ore`（3 输出）；纯候选 `create:crafting/materials/experience_nugget_from_block` 存在。→ 碎矿批量路径，ok。
- `occultism:miner_marid_master` 用 `occultism:ritual/craft_miner_marid_master`（真实仪式配方，9 输入）；索引"最优"为 `minecraft:enchantment.` 附魔展示页（伪生产者）。→ 链选真实配方，ok。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | tesla_tower 探索锥，含全部候选生产者，无更优遗漏 |
| 0001 | incomplete | ok | ender_disk_16k 锥，目标合法，选路合理 |
| 0002 | incomplete | ok | uranium_fuel_rod_depleted 锥，MI 标准工艺分支 |
| 0003 | incomplete | ok | quantum_crafter 锥，含纯配方 |
| 0004 | incomplete | ok | machine_chainer 锥，选路合理 |
| 0005 | incomplete | ok | uranium_fuel_rod_double 锥 |
| 0006 | incomplete | ok | hv_solar_panel 锥 |
| 0007 | incomplete | ok | electrolyzer 锥，MI 电解分支正常 |
| 0008 | incomplete | ok | portable_chemical_cell_256k 锥 |
| 0009 | incomplete | ok | distillery 锥 |
| 0010 | incomplete | ok | mv_diesel_generator 锥（raw436 为复杂机器常态，非目标怪象） |
| 0011 | incomplete | ok | hp_buffer_card 锥 |
| 0012 | incomplete | ok | ender_storage_component_16k 锥 |
| 0013 | incomplete | ok | nano_saber 锥 |

## 规则建议（给汇总者）
- targetRegex: 无（本批 14 个目标均为合法机器/元件，无展示物/重复变体/无意义目标）。
- recipeRegex: 无（未发现任何 mod 配方族被系统性误选；锥内生产者枚举完整且合理）。
- 跨包通用建议（**数据层，非 planner recipeRegex**）：`cirrus-audit-data/atm10/producers.tsv/.jsonl`
  含大量伪生产者——`minecraft:enchantment.`（铁砧附魔展示，~39,857 条）、`jei:category/ftbquests/quest`
  （任务奖励页，~3,902 条）、`jei:category/minecolonies/*`、`jei:category/securitycraft/*` 转换页。
  这些会让"输入更少=更优"的自动化扫描**误报**为 worse-alternative。建议在构建 producers 索引时于数据层过滤，
  而非作为 planner 的 recipeRegex 收敛项。
- 无新模式。
