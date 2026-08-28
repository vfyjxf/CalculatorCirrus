# fom batch-004 审查报告
- pack: fom
- 批次: batch-004（written=48；类别分布：incomplete 38 / colossal 5 / giant 3 / long 2）
- 审查链数: 48（incomplete 38 / complete 10；全量：10 条 complete 全文精读，38 条 incomplete 经 header/RAW/UNRESOLVED 全扫 + 定向 grep：reason=RANK_TRUNCATED 39 处、crafts 5 位数检查无命中、机器类型全为合法 crafting_table/blast_furnace/furnace/stonecutter/smithing_table）
- 发现: critical 0 / major 0 / minor 0

## 发现

本批**无新可疑配方族 / 目标类**（无新模式）。所有未解析项与异常扇出均可归因为 round-4 口径已列明的「已知规划器级问题」。故本章无 finding；已知问题仅在链级一句话提及，见「链级判定」与下方汇总。

### 已知规划器级问题（仅链级一句话提及，按 round-4 口径不报为 finding）

1. **`|+N` id 后缀污染 × budget/rank 截断**：38 条 incomplete 链中 36 条带 `UNRESOLVED create:polished_cut_andesite_stairs|+14`（reason=RANK_TRUNCATED，candidates=5），另 3 条 harness 链（0002/0010/0013）带 `UNRESOLVED minecraft:black_wool|+14`（同 reason）。真身 `create:polished_cut_andesite_stairs` 真实可产（同批链内见 `create:polished_cut_andesite_stairs_from_stone_types_andesite_stonecutting` 等 stonecutting 配方），污染 id 使规划器另开一支并因 rank 截断放弃。该 `|+N` 后缀在生产者步骤亦普遍出现（如 `minecraft:crimson_planks|+13`、`minecraft:stripped_acacia_log|+3`），属导出层 id 污染，非配方问题。
2. **any-of 基底坍缩 → `pattern/exchange` 扇出**：CHAIN 0019（millstone）`pattern/exchange x100`，全部为 `create:*_from_stone_types_andesite_stonecutting` 变体环；所有 `createcasing:*_gearshift` 链（0004/0014/0016/0018/0022/0024/0025）`pattern/exchange x50`。均已带 `pattern/exchange` 标签、内容均为已知 `#stone`/`#planks`→多变体展开，非新族。
3. **截断级联到 mod 金属**：CHAIN 0005 `cast_iron_ingot`、0009 `bronze_ingot`、0011/0032 `steel_ingot` 以 reason=CASCADE_EXHAUSTED 未解析（candidates=2）。但同批 CHAIN 0003 已用 `createbigcannons:cast_iron_ingot_from_nuggets`（pattern/storage_ladder，9 nugget→1 ingot）正常产出该族，证明可产；本批未解析仅为上游 rank 截断级联，非配方族缺口。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14（\|+N 污染×RANK_TRUNCATED，已知） |
| 0001 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；swivel_bearing 为 raw 叶子正常 |
| 0002 | incomplete | questionable | UNRESOLVED black_wool\|+14；wool/dye/recolor 正常，无新族 |
| 0003 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；cast_iron_ingot 经 nugget 正常产出 |
| 0004 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50（any-of 坍缩，已标签） |
| 0005 | incomplete | questionable | + cast_iron_ingot CASCADE_EXHAUSTED（截断级联，0003 已证可产） |
| 0006 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；smart_propeller 叶正常 |
| 0007 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；basin_lid 正常 |
| 0008 | colossal | ok | netherite_leggings；storage_ladder x52 + exchange x4 已标签 |
| 0009 | incomplete | questionable | + bronze_ingot CASCADE_EXHAUSTED（截断级联） |
| 0010 | incomplete | questionable | UNRESOLVED black_wool\|+14；magenta harness 正常 |
| 0011 | incomplete | questionable | + steel_ingot CASCADE_EXHAUSTED（截断级联） |
| 0012 | long | ok | stack_upgrade t2→t4；diamond/netherite 块 9:1 环已标签，无 no-op |
| 0013 | incomplete | questionable | UNRESOLVED black_wool\|+14；light_blue harness 正常 |
| 0014 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0015 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；gantry_carriage 正常 |
| 0016 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0017 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；engine_exhaust_cone 正常 |
| 0018 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0019 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x100（any-of 坍缩峰值，已标签） |
| 0020 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；engine_turbine 正常 |
| 0021 | giant | ok | purple_candle；recolor/lapis 9:1 环已标签 |
| 0022 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0023 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；brake 正常 |
| 0024 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0025 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；exchange x50 已标签 |
| 0026 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；hammer 正常 |
| 0027 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；metal_bracket raw20 多为 mod 叶正常 |
| 0028 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；purple_handle 叶正常 |
| 0029 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；cannon_loader 正常 |
| 0030 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；large_cogwheel 正常 |
| 0031 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；cannon_mount_extension 正常 |
| 0032 | incomplete | questionable | + steel_ingot CASCADE_EXHAUSTED（截断级联）；余已收敛 |
| 0033 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；rope_winch 叶正常 |
| 0034 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；industrial_iron_press 正常 |
| 0035 | colossal | ok | netherite_diving_helmet；storage_ladder 已标签 |
| 0036 | giant | ok | stonecutter_upgrade；raw_iron/redstone 9:1 环已标签 |
| 0037 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；mechanical_arm 正常 |
| 0038 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；mechanical_plough 正常 |
| 0039 | colossal | ok | hopper_minecart；正常 |
| 0040 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；dashboard 正常 |
| 0041 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；clutch 正常 |
| 0042 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；clutch 正常 |
| 0043 | long | ok | chunk_loader；framed_glass 变体环 exchange 已标签 |
| 0044 | incomplete | questionable | UNRESOLVED polished_cut_andesite_stairs\|+14；wooden_bracket 正常 |
| 0045 | giant | ok | white_stained_glass_pane；bone/framed_glass 环已标签 |
| 0046 | colossal | ok | netherite_pickaxe；storage_ladder 已标签 |
| 0047 | colossal | ok | netherite_helmet；storage_ladder 已标签 |

## 规则建议（给汇总者）

- targetRegex: 无。本批无新目标类需排除；所有 create*/aero*/simulated*/interiors* 目标均为正常 mod 产物或 raw 叶子。
- recipeRegex: 无。本批全部配方族已被现有 4 filter 覆盖：pattern/storage_ladder（866 次）、pattern/exchange（1659 次）、pattern/recolor（20 次）、intent/recycling（2 次）；无未覆盖配方族。
- builtin 候选: 无新模式。建议把 `|+N` id 后缀污染在**导出/清洗层**归并回真身 id（跨包通用，属 data-cleanup 而非 review 过滤）。本批 filterHitPct 已达 79.2%，残余 20.8%「未命中」全部由该污染产生的伪 UNRESOLVED 链贡献；清洗 id 后即可越过 85% 收敛阈值，无需新增 review 规则。
- 结论: 该包本批已收敛。继续跑 batch 价值低，建议转入导出层 id 清洗修复后重评收敛。
