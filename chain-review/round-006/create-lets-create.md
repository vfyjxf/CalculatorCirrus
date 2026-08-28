# create-lets-create batch-003 审查报告（round-6 选路质量深审）

- pack: create-lets-create
- 批次: batch-003（written=27：incomplete 22 + colossal 5）
- 审查链数: 27（全量；批次仅 27 条，不足 60 故全审；覆盖 incomplete / colossal 两类）
- 选路质量深审方法：对每条链的非 storage_ladder、非 exchange 的 EXECUTE 步，用 `producers.tsv` 查生产者数、`producers.jsonl` 查替代者明细，比对输入/输出成本。
- 发现: critical 0 / major 0 / minor 0

## 审查范围说明

本批次 27 条链中，`pattern/storage_ladder`（allthecompressed 压缩/解压阶梯，x36）与
`pattern/exchange`（create 石系 `create:*_from_stone_types_*_stonecutting` 阶梯，x30）已带
rule-tag，按口径不报。剥离这两类后，剩余非平凡中间品 EXECUTE 共 87 个唯一输出物，全部存在于
producers 索引（无缺失生产者）。对其逐一做了 producer 数 + 替代者明细核对。

## 发现

无。本批次轮次只审四类（worse-alternative / silly-detour / impure-recipe / 目标选择怪象），
经全量交叉核对，未发现任一生产者选择明显劣于索引中可替代者，也未发现目标本身无意义/展示物/重复变体。

逐类核查结论：

1. **worse-alternative —— 未发现。**
   - MCW 家族目标（mcwfences/mcwdoors/mcwbridges/mcwfurnitures/mcwroofs 的 pillar_wall /
     western_door / rail_bridge / table / chair / attic_roof）：producers.tsv 中计数均为 **1**，
     无替代可比较 → 一律 ok。
   - `minecraft:polished_andesite`（chain 0000, A74）：索引存在 1:1 石切替代
     `minecraft:polished_andesite_from_andesite_stonecutting`；但 planner 选的
     `minecraft:polished_andesite`（@ minecraft:crafter）实际执行 `in andesite x4 → out x4`，
     即 1:1，与石切替代成本相等 → 非更差，ok。
   - `minecraft:polished_diorite` / `polished_granite`（chain 0018 / 0021, A32）：planner 已选
     1:1 石切 `..._from_*_stonecutting`（最佳），未选 4:1 合成 → ok。
   - `minecraft:stone_slab`（chain 0000）：合成 `minecraft:stone_slab` 实际 387420489 stone →
     774840978 slab（≈2/stone），不劣于 1:1 石切替代 → ok。
   - `minecraft:stick` / `glass` / `lever` / `packed_mud` / `*_planks`：均选标准配方；替代者
     （branches→stick、framed/tiled glass、straw→mud 等）需链内不可得的输入或等价成本 → 可辩护 ok。
   - `sophisticatedstorage:limited_barrel_1..4`（各 12 生产者）：planner 选具体木种配方
     （spruce_limited_barrel_N），与 `generic_limited_barrel_N`（tag 配方）输入数相同（8 件），
     等价；具体木种配方无 tag 歧义，更"纯" → ok。

2. **silly-detour —— 未发现。** 所有中间品均由其直接生产者产出，无"有直连却绕多步间接"的情形。
   （create 石系阶梯虽长，但属带标签的 exchange 模式，已排除。）

3. **impure-recipe —— 未发现。** 所有被选中配方输出均为单一目标物，无"选多杂产配方而存在更纯直接配方"
   的情况。

4. **目标选择怪象 —— 未发现。** 目标均为真实可放置方块（MCW 家具/栅栏/门/桥/屋顶、sophisticatedstorage
   有限桶、MCW 石柱墙），无展示物/奖励页/重复无意义变体。pillar_wall 的 andesite/diorite/granite/
   mud_brick 四变体为不同方块；chair 的 modern/striped 为不同方块，均合理。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok* | andesite_pillar_wall；选路合理。唯一结构异常为 cobblestone 往返环（cobble→stone→slab→cobble via terralith），属 loop-or-cycle，超出本轮四类范围，且 cobblestone 无真生产者（两生产者皆转换型） |
| 0001 | colossal | ok | spruce_table；标准木作链 |
| 0002 | colossal | ok | jungle_table；标准木作链 |
| 0003 | incomplete | ok | birch_table（由 birch_coffee_table+栏杆，唯一生产者） |
| 0004 | incomplete | ok | stripped_birch_modern_chair（唯一生产者） |
| 0005 | incomplete | ok | stripped_spruce_modern_chair（唯一生产者） |
| 0006 | incomplete | ok | stripped_birch_striped_chair（唯一生产者） |
| 0007 | incomplete | ok | stripped_jungle_chair（唯一生产者） |
| 0008 | incomplete | ok | stripped_spruce_striped_chair（唯一生产者） |
| 0009 | incomplete | ok | stripped_spruce_chair（唯一生产者） |
| 0010 | incomplete | ok | stripped_jungle_striped_chair（唯一生产者） |
| 0011 | incomplete | ok | spruce_rail_bridge（slab+fence，唯一生产者） |
| 0012 | incomplete | ok | birch_rail_bridge（唯一生产者） |
| 0013 | incomplete | ok | limited_barrel_2（具体木种配方，等价于 generic tag 配方） |
| 0014 | incomplete | ok | limited_barrel_1（同上） |
| 0015 | incomplete | ok | limited_barrel_4（同上） |
| 0016 | incomplete | ok | limited_barrel_3（同上） |
| 0017 | incomplete | ok | jungle_rail_bridge（唯一生产者） |
| 0018 | incomplete | ok | diorite_pillar_wall；polished_diorite 已选 1:1 石切（最佳） |
| 0019 | incomplete | ok | mud_brick_pillar_wall（packed_mud 选 mud+wheat 标准配方） |
| 0020 | incomplete | ok | jungle_western_door（slab+planks，唯一生产者） |
| 0021 | incomplete | ok | granite_pillar_wall；polished_granite 已选 1:1 石切（最佳） |
| 0022 | incomplete | ok | spruce_western_door（唯一生产者） |
| 0023 | incomplete | ok | birch_western_door（唯一 producers） |
| 0024 | colossal | ok | jungle_planks_attic_roof（glass+planks，唯一生产者） |
| 0025 | colossal | ok | birch_planks_attic_roof（唯一生产者） |
| 0026 | colossal | ok | spruce_planks_attic_roof（唯一生产者） |

\* chain 0000 的 cobblestone 往返环说明：索引中 `minecraft:cobblestone` 仅 2 个生产者
（`terralith:cobblestone_from_stone_slab`、`allthecompressed:decompress/cobblestone_1x`），
均非真源；`minecraft:stone`（2 生产者：熔炼 cobble、解压）与 `minecraft:stone_slab`（2 生产者：
合成、石切）亦然。该环为结构/数据层面"缺 cobblestone 真源"所致，建议归入后续 loop-or-cycle /
unresolved-expected 审查，不在本轮选路质量四类内。

## 规则建议（给汇总者）

- targetRegex: 无（本批目标均为合理方块，无可排除目标）。
- recipeRegex: 无（未发现任何 mod 配方族被系统性误选；planner 在 create-lets-create 的选路合理）。
- builtin 候选: 无新模式（MCW 家具/栅栏/门/桥/屋顶、sophisticatedstorage 有限桶、create 石系均已被
  既有 exchange / storage_ladder 模式覆盖或仅为唯一生产者）。
- 唯一跨轮提示（非本轮四类，供参考）：cobblestone/stone/stone_slab 在 create-lets-create 中缺少真源
  生产者、导致 round-trip 环，建议在 loop-or-cycle / unresolved-expected 轮次关注 chain 0000。
