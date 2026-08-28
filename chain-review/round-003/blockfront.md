# BlockFront - Official Mod Pack 0.9.0.24b batch-003 审查报告
- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-003（written=70；类别分布 incomplete 20 / short 13 / colossal 11 / giant 10 / medium 9 / long 7）
- 审查链数: 70（全部 70 条均过审：short 13 + medium 9 + long 7 + colossal 11 + giant 10 + incomplete 20；无抽样，整批全读）
- 发现: critical 1 / major 1 / minor 0

## 发现
### F-BF-001 | chain=<全部 20 条 incomplete 链> | severity=critical | category=tag-expansion-overreach
- 位置: 各 `dye_*_wool` / `dye_*_bed` / `dye_*_carpet` 配方的 wool/bed 任意-of 原料；典型步骤 0005 A50 `dye_purple_wool` in:`purple_dye x1, black_wool|+14 x1`；0008 A50 `dye_purple_bed` in:`purple_dye x1, black_bed|+14 x1`；0010 A50 `dye_green_wool` in:`green_dye x6, black_wool|+14 x6`。
- 问题: 所有 `dye_<color>_<item>` 配方把"任意 wool/bed/carpet"(`#minecraft:wool` 等 any-of) 展开成了 `black_wool` / `black_bed`——一个**染色后产物**——而非未染基底 `white_wool` / `white_bed`。black_wool 本身又需"某 wool + 黑染料"才能产出，规划器于是用另一个染色 wool 去满足它，形成无限递归；候选排序在 `candidates=5` 处 `reason=RANK_TRUNCATED` 截断，留下唯一 `UNRESOLVED black_wool|+14` / `black_bed|+14`，导致整链 incomplete。受影响链：0005,0008,0010,0016,0017,0033,0034,0036,0040,0042,0043,0045,0049,0053,0054,0057,0058,0059,0067,0068（共 20 条，全部 pending=1 且 UNRESOLVED 物均为 black_wool/black_bed）。
- 原因: any-of 展开对"染色配方"选错了成员语义——应优先未染基底（white_wool/white_bed，作为可解析叶或廉价源），却选了黑色染色变体；叠加本包未建模"剪羊毛得未染 wool"这一基底来源，使每种染色 wool 都只能靠"给另一染色 wool 重新染色"派生，必然退化成环形依赖并被 RANK_TRUNCATED 截断。属 `tag-expansion-overreach`（成员语义不符）并级联为 `unresolved-cascade` / `unresolved-expected-item`（black_wool 是可通过 black_dye+white_wool 产出的原版常见物）。
- 建议: 对 `dye_*` 类配方，将 any-of wool/bed/carpet 的展开收敛为未染基底（prefer `white_wool`/`white_bed`，视为可解析叶）；并在数据层补"未染 wool/bed 基底来源"。可加 builtin 规则 `pattern/dye-base-pick`，证据 20/20 incomplete 链。

### F-BF-002 | chain=0011,0013,0014,0020,0022,0026,0029,0031,0038,0039,0044,0012,0019 | severity=major | category=amount-insanity
- 位置: 上述 13 条链中 `raw_iron_block ↔ raw_iron` 存储阶梯被重复 16 次（如 0011 oak_hanging_sign：`builtin-rule-tags ... pattern/storage_ladder x16`，depth=20，actions=23，crafts=23，raw_kinds=1；0012 chest_minecart、0019 furnace_minecart 同为 x16）。同一 `pattern/storage_ladder` 在长链中也以 x8 出现（0004/0006/0007/0030/0060/0063/0064，depth=11）。
- 问题: 这些链虽已带 `[rule-tag: pattern/storage_ladder]`（属 round-1/2 已处理模式），但标签下仍发生**几何放大**：悬挂告示牌/矿车只需极少量铁（链最终用 iron 做 chain），规划器却把 `raw_iron → 方块 → raw_iron` 往返做了 16 次，将链深推到 20、crafts 堆到 23，全程零净产出（仅 1 种 raw_kinds）。符合 round-3"已带标签仍失控：带 storage_ladder 仍几何放大 crafts"的例外上报条件。
- 原因: `pattern/storage_ladder` 排除规则只抑制了"是否报告"，未对**连续往返次数**设上限；规划器把上/下存储阶梯当作"合法有净产出放大"反复插入，造成 depth 失控。属已带标签仍失控的 amount-insanity。
- 建议: 在 `pattern/storage_ladder` 收敛逻辑中加"连续 compress↔decompress 往返次数上限"（如 ≤2 次），避免为单件低量需求构造 depth≥20 链。证据：13 条链 x16、7 条长链 x8。其余含 x5/x13 放大的蜡烛/混凝土粉链（0015,0024,0025,0041,0048,0050,0052,0065）程度较轻，已在链级判定标 ok（已带标签未失控）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 grindstone | ok | ok | 正常木石链，raw_kinds 略膨胀（已知规划器级，不计） |
| 0001 carrot_on_a_stick | ok | ok | 正常钓鱼竿链 |
| 0002 warped_fungus_on_a_stick | ok | ok | 正常钓鱼竿链 |
| 0003 crimson_fence_gate | ok | ok | 正常栅栏门链 |
| 0004 waxed_weathered_copper_bulb | amount-insanity | ok | storage_ladder x8 放大，已带标签，未失控 |
| 0005 purple_wool | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0006 waxed_exposed_copper_bulb | amount-insanity | ok | storage_ladder x8 放大，已带标签，未失控 |
| 0007 waxed_oxidized_copper_bulb | amount-insanity | ok | storage_ladder x8 放大，已带标签，未失控 |
| 0008 purple_bed | tag-expansion-overreach | bad | F-BF-001：black_bed 环形未解析 |
| 0009 warped_fence_gate | ok | ok | 正常栅栏门链 |
| 0010 green_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析（经 dye_green_wool） |
| 0011 oak_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 已带标签仍失控，depth20 |
| 0012 chest_minecart | amount-insanity | questionable | F-BF-002：raw_iron 阶梯 x16 失控 |
| 0013 crimson_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0014 birch_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0015 pink_candle | amount-insanity | ok | storage_ladder x5 放大，已带标签，未失控 |
| 0016 magenta_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0017 lime_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0018 lime_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0019 furnace_minecart | amount-insanity | questionable | F-BF-002：raw_iron 阶梯 x16 失控 |
| 0020 dark_oak_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0021 black_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0022 acacia_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0023 brown_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0024 light_blue_candle | amount-insanity | ok | storage_ladder x13 放大，已带标签，未失控 |
| 0025 light_gray_candle | amount-insanity | ok | storage_ladder x5 放大，已带标签，未失控 |
| 0026 spruce_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0027 orange_candle | ok | ok | 正常蜡烛链 |
| 0028 green_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0029 warped_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0030 blue_candle | amount-insanity | ok | storage_ladder x8（lapis）放大，已带标签，未失控 |
| 0031 jungle_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0032 cyan_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0033 light_blue_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0034 brown_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0035 red_candle | ok | ok | 正常蜡烛链 |
| 0036 yellow_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0037 yellow_stained_glass_pane | ok | ok | 正常染色玻璃链 |
| 0038 mangrove_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0039 bamboo_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0040 pink_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0041 magenta_candle | amount-insanity | ok | storage_ladder x13 放大，已带标签，未失控 |
| 0042 black_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0043 cyan_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0044 cherry_hanging_sign | amount-insanity | questionable | F-BF-002：storage_ladder x16 失控 |
| 0045 blue_banner | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0046 spectral_arrow | ok | ok | 正常箭链 |
| 0047 warped_fence | ok | ok | 正常栅栏链 |
| 0048 pink_concrete_powder | amount-insanity | ok | storage_ladder x5 放大，已带标签，未失控 |
| 0049 magenta_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0050 light_gray_concrete_powder | amount-insanity | ok | storage_ladder x5 放大，已带标签，未失控 |
| 0051 dark_prismarine_stairs | ok | ok | 正常石砌阶梯 |
| 0052 magenta_concrete_powder | amount-insanity | ok | storage_ladder x13 放大，已带标签，未失控 |
| 0053 lime_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0054 light_blue_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0055 dark_prismarine_slab | ok | ok | 正常石砌台阶 |
| 0056 red_concrete_powder | ok | ok | 正常混凝土粉链 |
| 0057 brown_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0058 pink_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0059 cyan_carpet | tag-expansion-overreach | bad | F-BF-001：black_wool 环形未解析 |
| 0060 blue_concrete_powder | amount-insanity | ok | storage_ladder x8（lapis）放大，已带标签，未失控 |
| 0061 orange_concrete_powder | ok | ok | 正常混凝土粉链 |
| 0062 crimson_fence | ok | ok | 正常栅栏链 |
| 0063 purple_dye | amount-insanity | ok | storage_ladder x8 放大，已带标签，未失控 |
| 0064 redstone_lamp | amount-insanity | ok | storage_ladder x8 放大，已带标签，未失控 |
| 0065 light_blue_concrete_powder | amount-insanity | ok | storage_ladder x13 放大，已带标签，未失控 |
| 0066 crimson_sign | ok | ok | 正常告示牌链 |
| 0067 light_gray_bed | tag-expansion-overreach | bad | F-BF-001：black_bed 环形未解析 |
| 0068 orange_bed | tag-expansion-overreach | bad | F-BF-001：black_bed 环形未解析 |
| 0069 respawn_anchor | ok | ok | 正常重生锚链 |

## 规则建议（给汇总者）
- targetRegex: 无新增（染色 wool/bed/banner/carpet 目标本身合法，不应排除）。
- recipeRegex: `^minecraft:dye_.*_(wool|bed|carpet)$` —— 收敛这些配方族以触发 any-of 基底修正，证据 20 条 incomplete 链。
- builtin 候选:
  - `pattern/dye-base-pick`：对 `dye_*` 配方展开 any-of `#minecraft:wool`/`#minecraft:bed`/`#minecraft:carpet` 时，优先未染基底 `white_wool`/`white_bed`（视为可解析叶）；停止选 `black_wool`/`black_bed` 等染色变体。证据：20/20 incomplete 链的唯一 UNRESOLVED 均为 black_wool/black_bed（reason=RANK_TRUNCATED）。
  - `pattern/storage_ladder` 收敛逻辑补"连续往返次数上限"（建议 ≤2），消除 x16（depth20）几何放大。证据：13 条链 x16 + 7 条长链 x8。
- 已知规划器级问题（仅链级一句提及，不展开）: `+N` 物品 id 后缀污染（本批 70 链几乎全覆盖，如 `red_sand|+1`、`stripped_acacia_log|+3`、`black_wool|+14`）；raw_kinds 膨胀（如 grindstone 列 14 种 raw 仅用 ~4 种）；无净产出往返环机制本身；32 位溢出哨兵。其中 `+N` 后缀与 raw_kinds 膨胀未计入上述发现。
