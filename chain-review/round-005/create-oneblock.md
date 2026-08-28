# create-oneblock batch-005 审查报告

- pack: create-oneblock
- 批次: batch-005（written=64，全部 incomplete；类别分布：incomplete 64/64，无 complete/trivial/short/medium/long/giant/colossal 等小类别文件——本批仅 `chains-incomplete-001.txt` 一个全量文件，4.0MB / 40777 行 / 64 链）
- 审查链数: 64（incomplete 64 / complete 0；因不足 100 条按"不足则全量"全读：64 链头部 status/builtin-rule-tags 全读，并按 depthBucket 抽读 0000/0001/0005/0010/0016/0029/0042/0055/0063/0007 等正文确认驱动来源；定向 grep `reason=BUDGET_TRUNCATED|RANK_TRUNCATED`=616 处、`UNRESOLVED`=694 处、`crafts>=1e5` 经 index 核验均为 rice_panicle/crimsite 无界放大而非新族）
- 发现: critical 0 / major 0 / minor 0

## 发现

### 无新模式

本批 64 条链的全部结构均由前四轮已固化规则覆盖，未出现任何新的可疑配方族 / 目标类。逐类核验如下（每条均能在链内证据中定位）：

1. **storage_ladder（已固化）**：贯穿全部链。lapis/diamond/iron 的 block↔ingot、farmersdelight 的 rice_bale↔rice_panicle、straw_bale↔straw、canvas_rug↔canvas、crimsite 等压缩/解压阶梯，均带 `[rule-tag: pattern/storage_ladder]`。证据：CHAIN 0000 A2-A30、CHAIN 0001 A2-A38、CHAIN 0016 A2-A6、CHAIN 0055 全程。
2. **exchange（已固化）**：全部石切变体环（calcite/crimsite/andesite/dripstone/red_sandstone… 的 stairs/wall/slab/pillar 互切）、concrete_powder 的 fan_washing（splashing）、stonecutting 反向环，均带 `[rule-tag: pattern/exchange]`。证据：CHAIN 0000 A8-A22、CHAIN 0010 A1-A24（crimsite 环）、CHAIN 0042 A1-A6、CHAIN 0055 A1-A25（andesite 环）。
3. **recolor（已固化）**：dye 混色（light_blue from blue+white 等）带 `[rule-tag: pattern/recolor]`。证据：CHAIN 0000 A32、CHAIN 0042/0058 内 recolor 标记。
4. **recycling（已固化）**：nugget_from_smelting / clay 等带 `[rule-tag: intent/recycling]`（本批 manifest 计 164 处，链内 `intent/recycling xN` 普遍 30–54）。
5. **jei: 命名空间**：全部 `jei:category/create/*`、`jei:category/farmersdelight/*` 均为真实机器配方系统（sequenced_assembly / mechanical_crafting / cooking_pot / fan_washing / automatic_shapeless / cutting / mystery_conversion），可执行；未发现 quest/trade/JER/展示页语义的 fake-production。本批 machines 列仅含 `minecraft:stonecutter`、`create:*`、`sophisticatedbackpacks:*`、`farmersdelight:*`、`minecraft:blast_furnace/furnace/soul_campfire` 等真实机器，无伪机器。
6. **free-resource / catalyst**：全批 `catalysts=0`（64 条 status 行均 catalysts=0）；无零输入 EXECUTE（grep `in : *$` 为空）；无 `jei:/synthetic` 伪 id。

**唯一反复出现的"新症状"是数量级失控，但根因属于已登记的规划器级问题，不是新配方族**：
- `farmersdelight:rice_panicle` 在 mega-wide 食物链（0001/0002/0003/0005/0011/0025/0029/0041/0063）中被放大到千万~十亿级（`UNRESOLVED farmersdelight:rice_panicle x14155803 reason=CASCADE_EXHAUSTED`），由 `rice_bale↔rice_panicle` storage_ladder 叠加 mill 出 straw+rice 的农场生成闭环驱动。这正是 round-5 已登记的 **"已带标签仍无界放大"** + **"intent/generation（农场作物生成）"** 规划器问题，非新族。
- `create:crimsite|+14`、`minecraft:diamond xN`、`minecraft:acacia_door|+9` 等超大计数属 **"原版常见物预算截断"（BUDGET_TRUNCATED/RANK_TRUNCATED）** 与 **"|+N id 后缀污染"**，均已在 round-5 清单。
- `minecraft:*_from_blasting_* @ create:encased_fan`（如 CHAIN 0016 A1 `diamond_from_blasting_deepslate_diamond_ore @ create:encased_fan`）属已登记的 **"blasting@encased_fan 机器映射错配"**，非新族。
- 16B 级链（0055/0058/0062 的 concrete_powder，`raw_kinds=0`）由 andesite 石切 exchange + storage_ladder 纯放大闭环驱动，属已知无界放大，非新族。

结论：**无新模式，审查层收敛**。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | exchange/storage_ladder/recolor | questionable | calcite 石切环 + lapis 压缩阶梯无界放大（已知） |
| 0001 | storage_ladder/recycling | questionable | rice_panicle CASCADE_EXHAUSTED 无界放大（已知规划器问题） |
| 0002 | storage_ladder/recycling | questionable | 同 0001，nether_salad 农场作物无界放大 |
| 0003 | storage_ladder/recycling | questionable | 同 0001，roasted_mutton_chops |
| 0004 | storage_ladder | questionable | stack_upgrade_tier_2 压缩阶梯放大（crafts 3M，已知） |
| 0005 | storage_ladder/recycling | questionable | gold_backpack 由 rice_panicle 无界放大驱动 |
| 0006 | exchange/storage_ladder | questionable | item_drain 石切+压缩阶梯（已知） |
| 0007 | exchange/storage_ladder | questionable | host trim：dripstone 石切环 + smithing_trim 已知映射问题 |
| 0008 | exchange/storage_ladder | questionable | raiser trim，同 0007 |
| 0009 | exchange/storage_ladder | questionable | shaper trim，同 0007 |
| 0010 | exchange/storage_ladder | questionable | track_switch_andesite 19.8B：crimsite 环+压缩放大（已知） |
| 0011 | storage_ladder/recycling | questionable | steak_and_potatoes 274B，rice_panicle 无界放大 |
| 0012 | storage_ladder | questionable | sticky_mechanical_piston 压缩阶梯放大 |
| 0013 | exchange/storage_ladder | questionable | wayfinder trim，同 0007 |
| 0014 | storage_ladder/recycling | questionable | advanced_feeding_upgrade 53M 放大 |
| 0015 | storage_ladder | questionable | mechanical_piston 压缩阶梯放大 |
| 0016 | storage_ladder | questionable | netherite_leggings：diamond 阶梯 + blasting@encased_fan（已知机器错配） |
| 0017 | exchange/storage_ladder | questionable | big_buffer 石切+压缩（已知） |
| 0018 | exchange/storage_ladder | questionable | track_acacia_wide 石切+压缩阶梯（已知） |
| 0019 | exchange/storage_ladder | questionable | track_jungle_wide，同 0018 |
| 0020 | storage_ladder | questionable | stack_downgrade_tier_1 3M 放大 |
| 0021 | exchange/storage_ladder | questionable | portable_fluid_interface（已知） |
| 0022 | exchange/storage_ladder | questionable | track_dark_oak_wide，同 0018 |
| 0023 | storage_ladder | questionable | mechanical_pump 107M 放大 |
| 0024 | exchange/storage_ladder | questionable | track_mangrove_wide，同 0018 |
| 0025 | storage_ladder/recycling | questionable | roast_chicken_block 274B，rice_panicle 无界放大 |
| 0026 | exchange/storage_ladder | questionable | track_spruce_wide，同 0018 |
| 0027 | storage_ladder | questionable | copper_backtank 96M 放大 |
| 0028 | exchange/storage_ladder | questionable | track_oak_wide，同 0018 |
| 0029 | storage_ladder/recycling | questionable | track_stripped_bamboo_wide 68M，rice_panicle 无界放大 |
| 0030 | exchange/storage_ladder | questionable | black_concrete：fan_washing(splashing)+石切（已知） |
| 0031 | exchange | questionable | smooth_red_sandstone_stairs 石切（已知） |
| 0032 | exchange/storage_ladder | questionable | magenta_concrete，同 0030 |
| 0033 | exchange/storage_ladder | questionable | green_concrete，同 0030 |
| 0034 | exchange/storage_ladder | questionable | light_blue_concrete，同 0030 |
| 0035 | storage_ladder | questionable | daylight_detector 压缩阶梯放大 |
| 0036 | exchange/storage_ladder | questionable | pink_concrete，同 0030 |
| 0037 | storage_ladder | questionable | stack_downgrade_tier_2 3M 放大 |
| 0038 | exchange/storage_ladder | questionable | gray_concrete，同 0030 |
| 0039 | exchange/storage_ladder | questionable | light_gray_concrete，同 0030 |
| 0040 | exchange/storage_ladder | questionable | cyan_concrete，同 0030 |
| 0041 | storage_ladder | questionable | netherite_diving_helmet 1.4B 压缩阶梯放大 |
| 0042 | exchange/storage_ladder/recolor | questionable | stonecutter_upgrade：crimsite 石切环（已知） |
| 0043 | exchange/storage_ladder | questionable | purple_concrete，同 0030 |
| 0044 | storage_ladder/recycling | questionable | skillet 农场+压缩（已知） |
| 0045 | exchange/storage_ladder | questionable | lime_concrete，同 0030 |
| 0046 | exchange | questionable | smooth_red_sandstone_slab 石切 |
| 0047 | exchange/storage_ladder | questionable | white_concrete，同 0030 |
| 0048 | storage_ladder | questionable | netherite_pickaxe 85M 压缩阶梯放大 |
| 0049 | exchange/storage_ladder | questionable | blue_concrete，同 0030 |
| 0050 | storage_ladder | questionable | netherite_helmet 压缩阶梯放大 |
| 0051 | exchange/storage_ladder | questionable | orange_concrete，同 0030 |
| 0052 | storage_ladder | questionable | spectral_arrow 484M 放大 |
| 0053 | exchange/storage_ladder | questionable | gray_terracotta 石切+压缩 |
| 0054 | storage_ladder | questionable | netherite_boots 压缩阶梯放大 |
| 0055 | exchange/storage_ladder | questionable | pink_concrete_powder 16B：andesite 石切环+纯放大（raw_kinds=0，已知无界放大） |
| 0056 | exchange/storage_ladder | questionable | orange_terracotta 石切+压缩 |
| 0057 | exchange/storage_ladder | questionable | green_terracotta 石切+压缩 |
| 0058 | exchange/storage_ladder | questionable | magenta_concrete_powder 16B，同 0055 |
| 0059 | storage_ladder | questionable | item_hatch 压缩阶梯放大 |
| 0060 | exchange/storage_ladder | questionable | cyan_terracotta 石切+压缩 |
| 0061 | exchange/storage_ladder | questionable | purple_terracotta 石切+压缩 |
| 0062 | exchange/storage_ladder | questionable | black_concrete_powder 16B，同 0055 |
| 0063 | storage_ladder/recycling | questionable | stack_downgrade_tier_3 111M 放大 |

（verdict 全部为 questionable：链本身受已知无界放大 / exchange / storage_ladder 影响而"不完整"，但均属于已固化规则或已登记规划器问题，无新增可报问题。）

## 规则建议（给汇总者）

- targetRegex: 无新增。本批目标类（create 机械 / sophisticatedbackpacks 升级 / farmersdelight 食物 / railways 轨道 / 混凝土·陶瓦 / 下界合金护甲 / 盔甲纹饰模板）均已在前序 round 的 exchange/storage_ladder/recycling/recolor 与 junk 过滤覆盖。
- recipeRegex: 无新增收敛需求；`pattern/storage_ladder`、`pattern/exchange`、`pattern/recolor`、`intent/recycling` 四个标签已覆盖本批 100% 的非 vanilla 步骤（manifest 分类计数：exchange 4690 / storage_ladder 288 / recycling 164 / recolor 24 / junk 873 / duplicate 248）。
- builtin 候选: 无。跨包通用模式（storage_ladder / exchange / recolor / recycling / generation / junk）本批未出现超出既有定义的新成员；`farmersdelight:rice_panicle` 无界放大是 round-5 已登记的"已带标签仍无界放大 + 农场生成"规划器级问题，建议在 planner 层（而非审查层）处理，不属于新配方族。
- **结论：无新模式，审查层收敛。**
