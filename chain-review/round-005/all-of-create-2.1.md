# All Of Create 2.1 batch-005 审查报告

- pack: All Of Create 2.1
- 批次: batch-005（written=57；category 分布：incomplete 29 / colossal 14 / giant 2 / long 4 / medium 7 / short 1）
- 审查链数: 57（全量，100%；incomplete 29 / complete 28）。读法：giant/long/medium/short/colossal 全读；incomplete 全量以「target + builtin-rule-tags + [RAW]/[UNRESOLVED]」抽取核对，并对代表性链（0000/0001/0002/0027/0016/0018 等）逐行精读。定向 grep `reason=BUDGET_TRUNCATED|RANK_TRUNCATED` 命中 48 处，均为已知规划器级截断。
- 发现: critical 0 / major 0 / minor 0

## 发现

无新模式，审查层收敛。

本批 57 条链的全部 EXECUTE 步骤仅由 4 类已固化规则家族覆盖：`pattern/exchange`、`pattern/storage_ladder`、`pattern/recolor`、`intent/recycling`（见每条链头部 `builtin-rule-tags` 行）。未出现在已处理清单中的可疑配方族或目标类：

- **garnished:sweetened_*（9 条 colossal）**：内核为 `minecraft:sugar_from_honey_bottle`（recycling）+ `garnished:*_sack ↔ from_sack/*`（storage_ladder 装/拆袋）+ framed glass 交换环（exchange）。与 melon_juice(0001) 同构，非新族。
- **createdeco:*_shipping_container（7 条 incomplete）**：经 `minecraft:*_shipping_container_from_dyeing_vaults`（染色→recolor）及 `create_vibrant_vaults:stonecutting/*_from_base_*_vaults`（形状环→exchange）产出，属已覆盖的 createdeco/dyeing 变体环。
- **multimeter 家族（tfmg:*_multimeter ×6 + powergrid:multimeter + powergrid:redstone_relay）**：主体为 storage_ladder 放大 + 钢铁/andesite 材料，未出现新配方族。
- **sophisticatedstorage/backpacks 各 upgrade、dndesires、createcasing、gnkinetics、extendedwrenches、createframed、create_submarine 等**：均为 exchange + storage_ladder +（个别）recycling 的组合，目标类虽多但配方手法已全部覆盖。

唯一的非标签中间步骤 `createadditionallogistics:crafting/kinetics/lazy_cogwheel_from_cogwheel ↔ cogwheel_from_lazy`（链 0026 内 A57–A61，5 次互转）是一个 2 项变体互转对，形态等同 exchange 变体环，且仅为中间步骤、非目标类，不构成本批新增族。

未发现 fake-production（`jei:category/quest|trade|market`、JER、villager_trade、information_page 全批零命中）；未发现 amount-insanity（index 中无 `crafts>=100000`）；未发现无净产出的往返 no-op 环（所有放大均带 `[rule-tag: pattern/storage_ladder|exchange]` 且声明 `loop=allowBoundedAmplification`）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | storage_ladder(+recolor)；UNRESOLVED `create_ironworks:steel_ingot\|+5`=RANK_TRUNCATED（\|+N 后缀污染，已知规划器问题） |
| 0001 | colossal | ok | exchange+storage_ladder 全覆盖，complete |
| 0002 | incomplete | ok | storage_ladder；UNRESOLVED steel_ingot\|+5=RANK_TRUNCATED（\|+N 污染，已知） |
| 0003 | incomplete | ok | exchange×100+storage_ladder；UNRESOLVED `create:layered_andesite\|+14`=RANK_TRUNCATED（\|+N 污染，已知） |
| 0004 | colossal | ok | storage_ladder×45，complete |
| 0005 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0006 | incomplete | ok | storage_ladder+recolor；UNRESOLVED steel_ingot\|+5=RANK_TRUNCATED（已知） |
| 0007 | incomplete | ok | storage_ladder；UNRESOLVED steel_ingot\|+5=RANK_TRUNCATED（已知） |
| 0008 | long | ok | recycling+storage_ladder，complete |
| 0009 | incomplete | ok | storage_ladder+recolor；UNRESOLVED steel_ingot\|+5=RANK_TRUNCATED（已知） |
| 0010 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0011 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0012 | incomplete | ok | recycling+exchange+storage_ladder；UNRESOLVED `chipped:cornered_oak_planks\|+451`=BUDGET_TRUNCATED（\|+N 污染，已知） |
| 0013 | colossal | ok | storage_ladder，complete |
| 0014 | incomplete | ok | exchange×6；UNRESOLVED `createdeco:corner_red_bricks`=CASCADE_EXHAUSTED（red_brick 变体环级联，已知） |
| 0015 | incomplete | ok | exchange×13；UNRESOLVED short_red_bricks=CASCADE_EXHAUSTED（已知） |
| 0016 | giant | ok | recycling+storage_ladder×24，complete |
| 0017 | colossal | ok | exchange×32，complete |
| 0018 | giant | ok | storage_ladder×32，complete |
| 0019 | incomplete | ok | exchange×12；UNRESOLVED long_red_bricks=CASCADE_EXHAUSTED（已知） |
| 0020 | incomplete | ok | recycling+exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0021 | long | ok | storage_ladder，complete |
| 0022 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0023 | short | ok | 原版合成，complete |
| 0024 | long | ok | storage_ladder，complete |
| 0025 | long | ok | storage_ladder，complete |
| 0026 | incomplete | ok | exchange+storage_ladder；UNRESOLVED `create_vibrant_vaults:basic_shipping_container`=CASCADE_EXHAUSTED（vault 染色/形状环级联，已知） |
| 0027 | colossal | ok | exchange+storage_ladder，complete |
| 0028 | colossal | ok | exchange+storage_ladder，complete |
| 0029 | medium | ok | stonecutting exchange，complete |
| 0030 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0031 | colossal | ok | exchange+storage_ladder，complete |
| 0032 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0033 | colossal | ok | exchange+storage_ladder，complete |
| 0034 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0035 | colossal | ok | storage_ladder×34，complete |
| 0036 | incomplete | ok | recolor+storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0037 | medium | ok | stonecutting，complete |
| 0038 | incomplete | ok | exchange+storage_ladder；UNRESOLVED layered_andesite\|+14=RANK_TRUNCATED（已知） |
| 0039 | medium | ok | stonecutting，complete |
| 0040 | incomplete | ok | exchange+storage_ladder；UNRESOLVED `create:brass_ingot`=CASCADE_EXHAUSTED（原版常见物级联，已知） |
| 0041 | incomplete | ok | storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0042 | incomplete | ok | storage_ladder；UNRESOLVED `minecraft:raw_iron`=BUDGET_TRUNCATED（原版常见物预算截断，已知） |
| 0043 | medium | ok | stonecutting，complete |
| 0044 | incomplete | ok | storage_ladder；UNRESOLVED `minecraft:chiseled_stone_bricks`=BUDGET_TRUNCATED（原版常见物预算截断，已知） |
| 0045 | medium | ok | stonecutting，complete |
| 0046 | colossal | ok | exchange+storage_ladder，complete |
| 0047 | incomplete | ok | recolor+storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0048 | incomplete | ok | storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0049 | incomplete | ok | recolor+storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0050 | medium | ok | stonecutting，complete |
| 0051 | incomplete | ok | recolor+storage_ladder；UNRESOLVED basic_shipping_container=CASCADE_EXHAUSTED（已知） |
| 0052 | colossal | ok | exchange+storage_ladder，complete |
| 0053 | medium | ok | stonecutting，complete |
| 0054 | colossal | ok | exchange+storage_ladder，complete |
| 0055 | colossal | ok | exchange+storage_ladder，complete |
| 0056 | colossal | ok | exchange+storage_ladder，complete |

## 规则建议（给汇总者）

- 无新模式，审查层收敛。本批 57 条链 100% 命中既有 4 类规则家族（exchange / storage_ladder / recolor / recycling），无需新增 targetRegex 或 recipeRegex。
- 既有 8 个过滤器（report.txt：convergence 47.4%）已覆盖本批全部 written chain；本轮未产生可提升收敛率的新候选过滤。
- 残留 UNRESOLVED（共约 48 处截断信号）全部归于已知规划器级问题，建议在规划器层修复，而非新增审查过滤：
  - `create_ironworks:steel_ingot|+5`、`create:layered_andesite|+14`、`chipped:cornered_oak_planks|+451` 等 `|+N` 后缀污染导致 RANK/BUDGET_TRUNCATED；
  - `createdeco:*_red_bricks`、`create_vibrant_vaults:basic_shipping_container`（shipping container 染色/形状环）CASCADE_EXHAUSTED；
  - `create:brass_ingot`、`minecraft:raw_iron`、`minecraft:chiseled_stone_bricks` 等原版常见物预算截断。
- 候选 builtin（非新增，仅提示归类）：`createadditionallogistics:crafting/kinetics/lazy_cogwheel_from_cogwheel ↔ cogwheel_from_lazy` 互转对已可归入 `pattern/exchange`，无需单独建标签。
