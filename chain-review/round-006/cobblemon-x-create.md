# cobblemon-x-create batch-005 审查报告（选路质量深审）

- pack: cobblemon-x-create
- 批次: batch-005（written=34，全部为 incomplete 类别；colossal 31 / giant 1 / long 1 / mega-wide 2）
- 审查链数: 34（全量，因批次仅 34 条；incomplete 34 / complete 0）
- 方法: 解析 `chains-incomplete-001.txt` 全部 4228 条 EXECUTE；对每条**非 rule-tag（exchange/recolor/storage_ladder/recycling/generation）排除族**的中间品 EXECUTE，用 `cirrus-audit-data/cobblemon-x-create/producers.tsv` + `producers.jsonl` 交叉核对生产者选择。
- 发现: critical 0 / major 0 / minor 0

## 深审范围与口径

本轮只审四类：`worse-alternative` / `silly-detour` / `impure-recipe` / 目标选择怪象。
明确排除（按 BATCH-REVIEW-BRIEF）：生成器/导出伪影（哨兵 x2147483647、`|+N` id、BUDGET/RANK/CASCADE 截断症状）、已带 rule-tag 家族的放大行为、被 junk 排除的配方、唯一生产者选择。

## 自动化交叉核对结果

1. **严格"输入子集更差"扫描（worse-alternative 核心判据）**：遍历全部 34 链、所有多生产者（索引 producers>1）中间品 EXECUTE，检查是否存在"输入集合为所选配方输入的严格子集"的替代者。结果：**0 条**。即没有任何一步是"用了比替代者更多的原料"。
2. **"同输入纯配方"扫描（impure-recipe 判据）**：找出所选配方真实 outc>1、且存在同输入、outc=1 的纯替代者。命中共 2 条，经索引与链上下文逐一复核后均**不成立**（见下）。
3. **目标选择**：34 个 target 均为真实可建造/可合成物品（机器部件、升级件、方块变体、cobblemon 道具），无展示物/重复变体/无意义目标。

## 已查证并排除的可疑候选（证据）

### 候选 A — chain 0002 `minecraft:iron_ingot` via `dndesires:seething/crushed_raw_iron`
- 所选：`dndesires:seething/crushed_raw_iron`（fan_seething，in: `create:crushed_raw_iron` → out: `iron_ingot x2`）。
- 索引同输入纯替代：`create:smelting/iron_ingot_from_crushed`、`create:blasting/iron_ingot_from_crushed`（outc=1，1 crushed→1 ingot）。
- 结论：**所选更优**。索引 `outs:["minecraft:iron_ingot","minecraft:iron_ingot"]` 为**产量翻倍**配方（1  crushed_raw_iron → 2 iron_ingot），纯替代者反而只有 1:1。非 impure，判 ok。

### 候选 B — chain 0012 `minecraft:flint` via `create:crushing/gravel`
- 所选：`create:crushing/gravel`（in: `gravel` → out: `sand`+`flint`+`clay_ball`，outc=3）。
- 索引纯替代：`create:milling/gravel`（outc=1，仅 `flint`）。
- 结论：**上下文需要 clay_ball**。链中 A175 粉碎 gravel 正是为了拿到 `clay_ball`（A176 继续 splashing sand→clay_ball），flint 只是副产。所选多产配方利用了副产，非问题。判 ok。

### 候选 C — chain 0019 `minecraft:ender_eye` 经 dndesires 三段链
- `slime_ball →(seething/magma_cream_from_slime_ball) magma_cream →(dragon_breathing/ender_pearl_from_magma_cream) ender_pearl →(seething/ender_eye_from_ender_pearl) ender_eye`。
- 索引核对：`ender_pearl` 在本包**唯一**生产者即 `dragon_breathing/ender_pearl_from_magma_cream`；`ender_eye` 的所选 `seething/ender_eye_from_ender_pearl` 仅需 `ender_pearl`（对比 vanilla `ender_eye` 需 `ender_pearl+blaze_powder`，所选反而更省）。整条链是本包可达的唯一路径，非绕路。判 ok。

### 候选 D — chain 0011 `cobblemon:relic_*` 硬币↔钱袋转换
- `relic_pouch_from_coin` / `relic_coin_from_pouch` / `relic_coin_sack`（9 pouch→sack）均为合法的包装/拆包（storage）转换，索引中亦为 vanilla crafting。非生产者选择错误。判 ok。

## 其他观察（非本轮发现，仅记录）

- `minecraft:glass_bottle` 在 8 条链（0001/0002/0004/0012/0019/0023/0025/0026）经 `create:potions`（item_drain 倒空 potion→bottle）取得。索引确有更直接的 `minecraft:glass_bottle`（crafting，in: `glass`）。但全 34 链中**裸 `minecraft:glass` 均不可达**（仅 `glass_bottle`/`glass_pane` 字面匹配），故直接合成不在 cone 内——属 BUDGET/CONE 截断症状，按口径明确排除，不计入发现。
- `create:splashing/crushed_raw_iron → iron_nugget`（多链）对比 `minecraft:iron_nugget`（crafting，in: `iron_ingot`）。所选为 Create 矿石处理的标准加倍路径，输入 `crushed_raw_iron` 在链内由矿石粉碎可得；直接 crafting 反需消耗整块 ingot。权衡可辩护，判 ok。
- `cobblemon:*_plaque_redye_as_coloring` 在 13 条链（0008/0016-0018/0020-0022/0024/0026-0027/0029-0030/0033）密集出现，属 recolor 家族（前 5 轮已收敛），不在本轮四类范围内。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | 仅 createdeco:dusk_bricks 非 exchange 步，无更优替代 |
| 0001 | incomplete | ok | 非 tag 步（coal/coal_piece/iron_nugget/potion 倒瓶）输入均在 cone 内 |
| 0002 | incomplete | ok | seething 翻倍产 iron_ingot 优于纯替代 |
| 0003 | incomplete | ok | 仅 createdeco:blue_bricks 非 exchange 步 |
| 0004 | incomplete | ok | 多步构建 iron_barrel，选择合理 |
| 0005 | incomplete | ok | CEI 超经验块链，唯一/合理路径 |
| 0006 | incomplete | ok | 工具交换升级，原材料步合理 |
| 0007 | incomplete | ok | 仅 createdeco:blue_bricks 非 exchange 步 |
| 0008 | incomplete | ok | plaque redye 族（recolor，已收敛） |
| 0009 | incomplete | ok | pokefinder 链，machinery 步合理 |
| 0010 | incomplete | ok | 仅 createdeco:pearl_bricks 非 exchange 步 |
| 0011 | incomplete | ok | relic 包装转换，合法 |
| 0012 | incomplete | ok | crushing/gravel 为拿 clay_ball 副产，合理 |
| 0013 | incomplete | ok | rose_quartz/electron_tube/zinc 链，合理 |
| 0014 | incomplete | ok | 仅 createdeco:pearl_bricks 非 exchange 步 |
| 0015 | incomplete | ok | weathered iron gearshift 链，合理 |
| 0016 | incomplete | ok | plaque redye 族 |
| 0017 | incomplete | ok | refined_radiance_mixer 链 + plaque redye |
| 0018 | incomplete | ok | shadow_steel_mixer 链 + plaque redye |
| 0019 | incomplete | ok | ender_eye 链为本包唯一可达路径 |
| 0020 | incomplete | ok | olivias_z_ring 链 + plaque redye |
| 0021 | incomplete | ok | advanced_filter_upgrade 链 + plaque redye |
| 0022 | incomplete | ok | fan_component 链 + plaque redye |
| 0023 | incomplete | ok | content_observer 链，brass 合成合理 |
| 0024 | incomplete | ok | large_cog_crank + plaque redye |
| 0025 | incomplete | ok | trainer_spawner 链，biomass 链合理 |
| 0026 | incomplete | ok | film 链 + plaque redye |
| 0027 | incomplete | ok | sticky_mechanical_piston + plaque redye |
| 0028 | incomplete | ok | encased_fan 链，andesite alloy 合理 |
| 0029 | incomplete | ok | plaque redye 族 |
| 0030 | incomplete | ok | refined_radiance_encased_fan + plaque redye |
| 0031 | incomplete | ok | timer_rod 链，apricorn→dye 合理 |
| 0032 | incomplete | ok | printer 链，copper/zinc 合理 |
| 0033 | incomplete | ok | brass_chain_conveyor + plaque redye |

## 规则建议（给汇总者）

- targetRegex: 无。
- recipeRegex: 无（未发现任何被系统性误选的 mod 配方族；所见多生产者选择经索引核对均为合理/更优/cone 截断所致）。
- builtin 候选: 无新模式。
- 备注: 本批次生产者选择质量良好，全部 34 条链的 EXECUTE 选择经 producers 索引交叉核对均无 `worse-alternative` / `silly-detour` / `impure-recipe` 实据。
