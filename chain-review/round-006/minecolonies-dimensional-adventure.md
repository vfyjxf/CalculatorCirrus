# minecolonies-dimensional-adventure batch-004 审查报告
- pack: minecolonies-dimensional-adventure
- 批次: batch-004（written=34；类别分布 incomplete 33 / colossal(complete) 1）
- 审查链数: 34（全量；批次仅 34 条，未达 100 故全量过审）。非平凡、无规则标记 EXECUTE 步共 2629 条，对应 343 个不同配方；每条多生产者中间品均经 `cirrus-audit-data/minecolonies-dimensional-adventure/producers.{tsv,jsonl}` 交叉核对。
- 发现: critical 0 / major 0 / minor 0

## 发现
无 route-quality 违规。planner 的生产者选择全部合理：要么是规范（canonical）配方，要么在链的可用输入锥内是唯一可达生产者。以下是针对所有"可疑多生产者项"的逐项交叉核对结论（均判 ok / 可辩护，无证据支撑 worse-alternative / silly-detour / impure-recipe）。

### 交叉核对记录（候选 → 结论，全部 ok）
- `minecraft:glass`（4 生产者）：链内用 `chronodawn:temporal_sand_to_glass`（temporal_sand→glass）与 `minecraft:glass_from_smelting_rough_glass`（rough_glass→glass）。后者属 glass→rough_glass→glass 放大环（`loop=allowBoundedAmplification`，净产出放大，非问题范畴）；链内无 `minecraft:sand` 可达，故直接 sand→glass 不可达。判 ok。
- `minecraft:magma_block`（2 生产者）：链 0020 选 `ars_nouveau:fire_essence_to_magma_block`（8×c:stones + fire_essence）。替代 `minecraft:magma_block`（4×magma_cream）在该链 RAW 中不可达（无 magma_cream），属被迫选择。判 ok。
- `minecraft:quartz`（6 生产者）：链 0020 选 `lithereal:quartz_from_blasting_phantom_quartz_ore` 与 `abyssal_decor:quartz_bone_decraft`。替代 `minecraft:quartz`（smelt nether_quartz_ore）/ `minecraft:quartz_from_blasting`（blasting nether_quartz_ore）在链内不可达（无 nether_quartz_ore）；mod 矿/骨为唯一可达路径。判 ok。
- `minecraft:raw_iron` / `raw_gold` / `amethyst_shard`（各 2 生产者）：链内用 `tide:iron_from_tetra` / `tide:gold_from_minnow` / `tide:amethyst_from_shrimp`（鱼→raw）。替代者为 storage_block 解压（raw_iron_block→raw_iron 等），但链正为生产 raw 物、并无原料块；鱼配方是唯一前向路径，非更差。判 ok。
- `blockbox:polished_amethyst`（4 生产者）：链内 `blockbox:polished_amethyst`（crafting，4×amethyst_block→4×polished，实为 1:1）与 `blockbox:polished_amethyst_from_amethyst_block_stonecutting`（1:1）并存；二者效率等价，并非 4:1 浪费。判 ok。
- `minecraft:coal`（16 生产者）：链内用 `netherdepthsupgrade:coal` / `datanessence:crafting/coal_from_lumps` / `actuallyadditions:tiny_to_coal` 等 mod 配方；vanilla `minecraft:coal_from_blasting_coal_ore` 所需 coal_ore 在链内不可达，属被迫。判 ok。
- `minecraft:ender_eye`、`minecraft:iron_ingot_from_blasting_raw_iron`、`minecraft:stick`、`minecraft:charcoal`、`sophisticatedstorage:generic_barrel`（通用 planks 标签，最灵活）、`minecraft:bone_meal`（配 `bone_meal_from_bone_block`）、`minecraft:white_wool`（BWG 花瓣配方与 vanilla dye 配方并存）等：均为规范配方或同效并存，无可证更优替代。判 ok。
- impure-recipe 扫描：343 个被选（非标记）配方中 **0 个为多输出配方** → 不存在"更纯直接配方"可指。判 ok。
- 目标怪象扫描：150 个目标全部为真实可合成物品（powah / appflux / ironfurnaces / draconicevolution / heralds_luna 旗帜 / warfare_wings 涂装 等），无 `jei:` 展示页、无 synthetic/合成 id、无重复无意义变体。判 ok。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | 非标记步为规范矿石/锭/amethyst 石切，选路合理 |
| 0001 | incomplete | ok | glass 经 temporal_sand 直产，rough_glass 为放大环，无更差选路 |
| 0002 | incomplete | ok | 非标记步规范，多生产者项均判 ok |
| 0003 | incomplete | ok | herald 旗帜为主，材料步选路合理 |
| 0004 | incomplete | ok | powah reactor：uraninite/电容/晶体系规范配方 |
| 0005 | incomplete | ok | appflux 电芯：非标记步规范 |
| 0006 | incomplete | ok | 非标记步规范，无可证更优替代 |
| 0007 | incomplete | ok | 非标记步规范 |
| 0008 | incomplete | ok | herald 旗帜，材料步合理 |
| 0009 | incomplete | ok | 非标记步规范 |
| 0010 | incomplete | ok | 非标记步规范 |
| 0011 | incomplete | ok | 非标记步规范 |
| 0012 | incomplete | ok | 非标记步规范 |
| 0013 | incomplete | ok | 非标记步规范 |
| 0014 | incomplete | ok | powah battery：电容/电芯规范配方 |
| 0015 | incomplete | ok | 非标记步规范 |
| 0016 | incomplete | ok | 非标记步规范 |
| 0017 | incomplete | ok | sophisticatedstorage barrel：generic_barrel 最灵活，合理 |
| 0018 | incomplete | ok | ironfurnaces：矿石/锭/玻璃/钻石各路均规范或被迫 mod 配方 |
| 0019 | incomplete | ok | warfare_wings 涂装，材料步合理 |
| 0020 | incomplete | ok | magma/quartz 用 mod 矿（vanilla 矿不可达），判 ok |
| 0021 | incomplete | ok | herald 旗帜，材料步合理 |
| 0022 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0023 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0024 | incomplete | ok | warfare_wings 涂装，材料步合理 |
| 0025 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0026 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0027 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0028 | incomplete | ok | warfare_wings 涂装，材料步合理 |
| 0029 | colossal | ok | ars_nouveau ritual：recolor 已标记；source_gem 压缩/解压为仪式所需 |
| 0030 | incomplete | ok | skd 室外箱，非标记步规范 |
| 0031 | incomplete | ok | warfare_wings 涂装，材料步合理 |
| 0032 | incomplete | ok | warfare_wings 涂装，材料步合理 |
| 0033 | incomplete | ok | oritech reactor_quad_rod：rod/锭/塑料规范配方 |

## 规则建议（给汇总者）
- targetRegex: 无
- recipeRegex: 无
- builtin 候选: 无
- 无新模式。本轮选路质量深审未发现需收敛的配方族；已收敛的 exchange/storage_ladder/recolor/recycling 模式族在本批次继续成立。
