# atm10-lite batch-004 审查报告（round-6 选路质量深审）

- pack: atm10-lite
- 批次: batch-004（written=13，全部 incomplete / mega-wide；本批 solved=150、structuralDupes=137 仅落盘 13 条结构去重后的链）
- 审查链数: 13（全部 incomplete，无更小类别文件；按"不足则全量"全审）
- 方法: 解析 13 条链的 5113 个未带 `rule-tag` 的 EXECUTE 步 → 抽取唯一输出物 321 个（在 `producers.tsv` 中生产者数 ≥2）→ 对每个用 `producers.jsonl` 交叉核对替代生产者，判定 worse-alternative / silly-detour / impure-recipe。
- 发现: critical 0 / major 0 / minor 1

## 选路质量结论（核心）
对 321 个"有多生产者"的非平凡中间品逐一交叉核对后，**未发现有证据支撑的 major 级选路错误**。所有初筛出的 `worse-alternative` 候选经索引复核均为**伪阳性**（替代配方是"环形依赖"——其输入本身就是该产物）：
- `megacells:sky_steel_ingot`：索引中 `megacells:crafting/sky_steel_ingot_from_sky_steel_block` 需要 `megacells:sky_steel_block`，而 `sky_steel_block` 由 `sky_steel_ingot` 合成（`ae2:decorative/fluix_block` 同理，`fluix_block` 由 4×`ae2:fluix_crystal` 合成）。planner 选的 `jei:/synthetic/10919`、`jei:/synthetic/10927` 才是真实路线，非误选。
- `ae2:fluix_crystal`、`ae2:damaged_budding_quartz`、`minecraft:netherite_pickaxe`：索引中的"更优"替代（`ae2:misc/deconstruction_fluix_block`、`anvil.self_repair.`）不是更便宜的直路，而是反解/修复路线，不优于所选。
- 大量 `occultism:ritual/*`、`occultism:miner/*`、`occultism:crystallize/*`、`mekanism:nucleosynthesizing/*`（全 13 链共 179+120+93+110 次未带标签 EXECUTE）是**成批产出的生成类配方**，用于一次性产出一整组下界/资源块（如 `occultism:ritual/possess_blaze` 单步产出 netherrack/blackstone/glowstone/gravel/soul_sand/soul_soil/basalt/blaze_rod 等 30+ 项）。作为"批量直连生产者"是合理且可辩护的，不构成 worse-alternative 或 silly-detour。

## 发现

### F-atm10-lite-1 | chain=0000,0007 | severity=minor | category=impure-recipe
- 位置: `[A52] mekanism:sawing/torch`（0000，out=`minecraft:stick`+`minecraft:coal`）、`[A?] mekanism:sawing/item_frame`（0007，out=`minecraft:leather`+`minecraft:stick`）、`[A?] mekanism:sawing/pumpkin`（0007，out=`minecraft:pumpkin_seeds`+`minecraft:carved_pumpkin`）
- 问题: planner 用 mekanism **锯切**的多产物配方去取单一中间品，而索引中存在更纯的直连配方：
  - `minecraft:stick`：所选 `mekanism:sawing/torch`（out 含 stick+coal）；索引纯配方 `minecraft:stick`（`minecraft:planks`→stick，sys=minecraft:crafting）。
  - `minecraft:leather`：所选 `mekanism:sawing/item_frame`（out 含 leather+stick）；索引纯配方 `minecraft:leather`（rotten_flesh→leather / `irons_spellbooks:hogskin_to_leather`）。
  - `minecraft:pumpkin_seeds`：所选 `mekanism:sawing/pumpkin`（out 含 seeds+carved_pumpkin）；索引纯配方 `minecraft:pumpkin_seeds`（`minecraft:pumpkin`→seeds）。
- 原因: 这些锯切步骤是"顺手"复用一个已有的锯切流水线、同时回收副产物（coal/stick/carved_pumpkin），与同链中大量 mekanism 锯切上下文一致。副产物在链内通常被复用。
- 建议: 权衡可辩护（副产物被链复用 / 与锯切流水线对齐），**不升级为 major**。仅作为 impure-recipe 的 minor 记录，供汇总者知晓已被交叉核对。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | mega-wide；storage_ladder 放大（已处理）+ occultism/nucleosynthesizing 批量产出，选路合理 |
| 0001 | incomplete | ok | 同上；fluix/damaged_budding 走 synthetic 真实路线，环形替代已排除 |
| 0002 | incomplete | ok | occultism 批量产出为主，无更优直路误选 |
| 0003 | incomplete | ok | 与 0000 同构，选路合理 |
| 0004 | incomplete | ok | sky_steel 走 synthetic（环形反解替代已排除），选路合理 |
| 0005 | incomplete | ok | 少量 occultism/sawing，选路合理 |
| 0006 | incomplete | ok | damaged_budding_quartz 走 synthetic，环形替代已排除 |
| 0007 | incomplete | ok | 锯切多产物（leather/pumpkin）为 minor impure，副产物复用，可辩护 |
| 0008 | incomplete | ok | occultism 批量产出最重（ritual×32），成批直连合理 |
| 0009 | incomplete | ok | sky_steel 走 synthetic，选路合理 |
| 0010 | incomplete | ok | occultism/nucleosynthesizing 批量，选路合理 |
| 0011 | incomplete | ok | 同 0009 结构，选路合理 |
| 0012 | incomplete | ok | 最小链（raw4），occultism 少量，选路合理 |

## 规则建议（给汇总者）
- targetRegex: 无（本批目标均为 atm10-lite 真实制品，无展示物/重复变体怪象）。
- recipeRegex: 无（未发现有 mod 配方族被**系统性误选**；唯一 impure 为 mekanism 锯切副产物复用，属可辩护行为，不建议收敛）。
- builtin 候选（跨包通用、建议补标 `pattern/generation` 或 `intent/generation`）：
  - `occultism:ritual/*`、`occultism:miner/*`、`occultism:crystallize/*` 与 `mekanism:nucleosynthesizing/*` 在本批**未带 rule-tag**，却大量（合计 502 次 EXECUTE）作为"批量/生成类"生产者出现，绕过了 builtin 的 generation 过滤（manifest 中 `intent/generation` 仅命中 30 条）。建议在分类层补标为生成/批量模式，使其放大行为按既有口径处理，避免在深审中被误判为普通选路。证据：13 链中 untagged EXECUTE 统计 ritual=179 / miner=120 / crystallize=93 / nucleosynthesizing=110。
  - 注：此建议属**分类层缺口**，非选路质量缺陷；本轮选路深审结论仍为"选路合理"。
