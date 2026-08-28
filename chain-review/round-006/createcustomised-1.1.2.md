# CreateCustomised1.1.2 batch-005 审查报告
- pack: CreateCustomised1.1.2
- 批次: batch-005（written=31，全部 incomplete；其余 119 目标为 structuralDupes 已丢弃，故全量审查 31 条）
- 审查链数: 31（incomplete 31 / complete 0；覆盖全部 31 条，含各 target 类别与 mega-wide/colossal）
- 方法: producers 索引交叉核对（producers.tsv / producers.jsonl）。对每条链的非 rule-tag EXECUTE 步，定位其在索引中的生产者集合，比对所选配方与替代者的输入/输出代价。
- 发现: critical 0 / major 0 / minor 0

## 选路质量深审结论

对 31 条链共 1056 个非 rule-tag EXECUTE 步做生产者索引核对，凡出现多生产者的中间品/目标均逐一比对。结果：

- **worse-alternative（明显更差生产者）: 0 例。** 所有多生产者选择均属以下两类可辩护情形：
  1. `create:mechanical_crafter`（automatic_shaped/shapeless，输入为具体物品如 `gold_ingot`/`diamond`）对比 `minecraft:crafting_table`（输入为 tag 如 `c:ingots/gold`/`c:gems/diamond`）。两者输入语义等价、产出相同；在 Create 整合包中机械装配是推荐的自动化路径，非更差选择（证据：铁/金/钻石斧在链 0000/0001/0002/0003/0010/0013/0014/0018/0023 等大量出现，均为 mechanical_crafter 版）。
  2. `minecraft:crafting_table` 石切 slab/wall（3~6 个具体 tiles/bricks）对比 `minecraft:stonecutting`（1 个 `create:stone_types/*` tag）。后者输入更"少"但仅为 tag 解析差异，产出完全一致，属 **exchange 石切族**的配方模式选择，非"选择本身错误"。
- **silly-detour（绕远路）: 0 例。** 每个被核查的中间品在索引中均有直接生产者，且 planner 所选即为其直接生产者之一（crafting_table / stonecutter / mechanical_crafter），未见"本有直连却多步间接"的情况。
- **impure-recipe（杂产替代纯配方）: 0 例。** 全量扫描 `chosen.outs.length>1 且存在纯产出替代` 命中 0（impure 候选 0）。
- **目标选择怪象: 0 例。** 31 个目标均为可建造的合法物品（`linked_controller`/`track_*`/`large_water_wheel`/`melon_juice_fluid`/各色 `concrete`/`bacon_and_eggs`/`nixie_board`/`brass_lamp`/`grindstone`/`lodestone`/`end_crystal`/`smoker`/`integer_selector`/`chain_pulley` 等），无展示物/重复变体/无意义目标；彩色 concrete 各为独立可产方块，非 junk。

### 关键抽样核对（带索引证据）
- `create:nixie_tube`（链 0024，目标）：索引有 2 生产者——`crafting/kinetics/nixie_tube`（2× `electron_tube`）与 `automatic_shapeless`（1× `electron_tube` @ mechanical_mixer/basin）。链选 **`[A57] ...@ create:mechanical_mixer in: electron_tube x1`**，即更优的 1-tube 配方，选路正确。
- `minecraft:grindstone`/`lodestone`/`end_crystal`/`smoker`/`nether_brick_fence`（链 0018/0027/0028/0029/0016，目标）：各 2 生产者均为 `crafting_table` 与 `mechanical_crafter` 且输入完全相同，选 mechanical_crafter 属自动化等价，无更差。
- `bits_n_bobs:brass_lamp`/`nixie_board`/`large_nixie_tube`（链 0017/0015/0021，目标）：2 生产者（crafting_table vs mechanical_crafter）输入完全一致，选路等价，无更差。
- `extra_gauges:integer_selector`（链 0026，目标）：2 生产者输入相同（analog_lever+quartz），选路等价。
- `farmersdelight:bacon_and_eggs`（链 0014，目标）：2 生产者（crafting_table tag 版 vs mechanical_mixer 具体版），输入等价，选 mixer 版合理。
- `minecraft:black_concrete`/`lime_concrete`（链 0004…0013 等）：索引中**唯一**生产者 `fan_washing`（powder→concrete），无替代可比较，按口径判 ok。

### 系统性模式说明（非发现，供汇总者参考）
本批次唯一成体系的"多生产者分歧"是 **exchange 石切族**：planner 在石切阶梯中倾向用 `crafting_table` 由具体 tiles/bricks 合成 slab/wall（3~6 输入），而非 `stonecutting` 由 1 个 stone-type tag 直接切出。该分歧：
- 产出完全相同，选择本身非错误；
- 属已带 `pattern/exchange` 标签族的行为放大范畴（BATCH-REVIEW-BRIEF 已明确"已带 rule-tag 的家族的放大行为本身"不报）；
- 故不计为 finding。

## 发现
（本轮四类问题均无。以下为占位说明，无 F 条目。）

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | linked_controller：选路合理，多生产者均等价/自动化 |
| 0001 | incomplete | ok | track_warped_wide：石切阶梯为 exchange 族，选路等价 |
| 0002 | incomplete | ok | large_water_wheel：机械装配版配方，选路合理 |
| 0003 | incomplete | ok | melon_juice_fluid：无更差生产者证据 |
| 0004 | incomplete | ok | black_concrete：唯一 fan_washing 生产者 |
| 0005 | incomplete | ok | magenta_concrete：唯一 fan_washing 生产者 |
| 0006 | incomplete | ok | green_concrete：唯一 fan_washing 生产者 |
| 0007 | incomplete | ok | light_blue_concrete：唯一 fan_washing 生产者 |
| 0008 | incomplete | ok | pink_concrete：唯一 fan_washing 生产者 |
| 0009 | incomplete | ok | gray_concrete：唯一 fan_washing 生产者 |
| 0010 | incomplete | ok | chef_hat_with_goggles：多生产者选路等价 |
| 0011 | incomplete | ok | light_gray_concrete：唯一 fan_washing 生产者 |
| 0012 | incomplete | ok | cyan_concrete：唯一 fan_washing 生产者 |
| 0013 | incomplete | ok | pumpkin_pie：石切 slab 为 exchange 族，选路等价 |
| 0014 | incomplete | ok | bacon_and_eggs：mixer 具体版 vs crafting tag 版等价 |
| 0015 | incomplete | ok | nixie_board：2 生产者输入一致，选路等价 |
| 0016 | incomplete | ok | nether_brick_fence：2 生产者等价 |
| 0017 | incomplete | ok | brass_lamp：2 生产者输入一致，选路等价 |
| 0018 | incomplete | ok | grindstone：2 生产者输入一致，选路等价 |
| 0019 | incomplete | ok | lime_concrete：唯一 fan_washing 生产者 |
| 0020 | incomplete | ok | blue_concrete：唯一 fan_washing 生产者 |
| 0021 | incomplete | ok | large_nixie_tube：2 生产者输入一致，选路等价 |
| 0022 | incomplete | ok | orange_concrete：唯一 fan_washing 生产者 |
| 0023 | incomplete | ok | chain_pulley：多生产者选路等价 |
| 0024 | incomplete | ok | nixie_tube：选中更优 1-tube 配方，选路正确 |
| 0025 | incomplete | ok | rose_quartz_lamp：多生产者选路等价 |
| 0026 | incomplete | ok | integer_selector：2 生产者输入一致 |
| 0027 | incomplete | ok | lodestone：2 生产者输入一致 |
| 0028 | incomplete | ok | end_crystal：2 生产者输入一致 |
| 0029 | incomplete | ok | smoker：2 生产者输入一致 |
| 0030 | incomplete | ok | track_blackstone：石切族，选路等价 |

## 规则建议（给汇总者）
- targetRegex: 无（本批次目标均合法，无可排除目标）。
- recipeRegex: 无（未发现某 mod 配方族被系统性"误选"；唯一成体系分歧为 exchange 石切族用 crafting_table 而非 stonecutter，属已覆盖的 exchange 家族行为放大，不应新增收敛规则）。
- builtin 候选: 无新模式。
- 注: 若后续希望把"石切 slab/wall 优先 stonecutter（1 tag 输入）而非 crafting_table（3~6 具体 tiles）"作为选路偏好固化，可在 planner 层加 recipeRegex 权重，但这会改动 exchange 族既有行为，建议维持现状（非错误）。
