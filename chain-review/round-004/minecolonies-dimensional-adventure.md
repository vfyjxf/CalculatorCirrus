# minecolonies-dimensional-adventure batch-003 审查报告

- pack: minecolonies-dimensional-adventure
- 批次: batch-003（written=65；类别分布：incomplete 63 / colossal 1(0007) / medium 1(0041)）
- 审查链数: 65（全量 written chains；incomplete 63 / complete 2）。读法：0007、0041 全读；0000/0003/0005/0008/0009/0011/0016/0028/0029/0033/0044/0048/0050/0055/0059 抽 EXECUTE 行细读；全文件 grep `crafts=`、`reason=BUDGET_TRUNCATED`、`|\+N`、`quartz_flake`、`quartz_bone`、`jei:`、`occultism:ritual_dummy`。总审查量覆盖全部 65 链（含全局 grep 与 index 全读）。
- 发现: critical 0 / major 1 / minor 1

## 发现

### F-minecolonies-dimensional-adventure-1 | chain=0007(主证) / 0008 / 0011 / 0016 / 0048 / 0009 | severity=major | category=loop-or-cycle

- 位置:
  - CHAIN 0007 A12-A28、A42-A58、A72-A88：`datanessence:crafting/quartz_flake x6 → quartz_flake x54`，下一行 `datanessence:crafting/quartz_from_flakes x6 → quartz x6`（9:1 互逆，净零），三段重复。
  - CHAIN 0007 A18-A25、A49-A56、A79-A86：`abyssal_decor:quartz_bone_recipe`（in bone_meal x3 + quartz x6，out quartz_bone x1）↔ `abyssal_decor:quartz_bone_decraft`（in quartz_bone x1，out quartz x6），仅烧 bone_meal、quartz 守恒，三段重复。
  - CHAIN 0008 A22-A33+：同 `quartz_flake↔quartz_from_flakes` 环；`status: ... crafts=516278`（mega-wide，行 4593）。
  - CHAIN 0011：同 0008（portable_fluid_cell_256m，crafts=516278）。
  - CHAIN 0016 A20-A33（quartz 环 12×）+ A34-A49（quartz_bone 环 12×）。
  - CHAIN 0048 A20-A51（quartz 环 16× + quartz_bone 环 16×）。
  - CHAIN 0009 A45-A72：`minecraft:white_allium_petal_block x1 → white_allium x1 → petal_block x1 → allium x1 …`（互逆无净产出）重复 8×，pink 同法 5×，共 16×。
- 问题: 这些可逆配比对被 `loop=allowBoundedAmplification` 当作"有界放大"反复执行，但**净产出为零**（quartz↔flake 9:1 互逆；quartz_bone 环只消费 bone_meal 守恒 quartz；allium petal↔flower 互逆）。它们把仅 14 配方的目标 0007 撑成 93 个 action，把 0008/0011 撑到 crafts=516278（约 0.5M）。所有相关 EXECUTE 步**均未带任何 `[rule-tag]`**，因此逃过 storage_ladder / exchange 排除。
- 原因: 规划器对"可逆压缩/解压配比"的覆盖存在具体 mod 家族缺口：`datanessence:crafting/*_flake↔*_from_flakes`、`abyssal_decor:quartz_bone_*`、`minecraft:*_allium_petal_block↔*_allium` 这类 1:9 / 互逆对既不在 storage_ladder 排除清单，也不在 exchange 环集合，分类器也未打标签，遂被当成合法放大步反复插入。本质即"无净产出往返机制"在这些新配方族上的具体体现，但当前过滤器未收敛到它们。
- 建议: 新增 builtin 标签 `pattern/reversible_amplification`（或扩展 storage_ladder），regex 命中 `datanessence:crafting/(\w+)_flake` + `datanessence:crafting/\1_from_flakes`、`abyssal_decor:quartz_bone_recipe`、`abyssal_decor:quartz_bone_decraft`、`minecraft:\w*_allium_petal_block`、`minecraft:\w*_allium`（成对互逆），直接判 no-net loop 排除/折叠。

### F-minecolonies-dimensional-adventure-2 | chain=0000 / 0003 / 0005 | severity=minor | category=loop-or-cycle（suspected）

- 位置: CHAIN 0000 A1-A9；CHAIN 0005 A1-A20；CHAIN 0003 前段。配方 `minecraft:cb/brown_shard_to_block`、`minecraft:sc/brown_block_to_centered_sc`、`brown_pillar_to_side_slab_sc`、`brown_other_slab_back_to_slab_sc`、`brown_slab_to_block`、`brown_back_to_block_sc` 等在 bibliocraft fancy_crafter 与 stonecutter 之间反复转换 glazedresymmetry brown 系列（shard↔block↔centered↔slab↔pillar）。
- 问题: 该 brown-glazed 变体环**未带 `pattern/exchange` 标签**（对比同文件 blockbox amethyst 段已带 `[rule-tag: pattern/exchange]`），疑似又一个未被覆盖的 variant ring。无法从抽样确认净产出，故列 suspected minor。
- 原因: 与 F-1 同类——glazedresymmetry / 自定义 `minecraft:cb|sc` 变体互换未被 exchange 规则覆盖。
- 建议: 把 `minecraft:cb/*`↔`minecraft:sc/*` 的 brown-glazed 等变体互换归入 `pattern/exchange`（或新增 `pattern/glazed_variant`）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | loop-or-cycle | questionable | brown-glazed `cb/sc` 变体环未带标签（F-2）+ quartz_flake 尾段 |
| 0001 | amount-insanity | questionable | powah 电池，occultism 通用熔炼机映射（已知）+ 重复 nugget 回收 |
| 0002 | ok | ok | immersive_aircraft gyroscope，常规路径 |
| 0003 | loop-or-cycle | questionable | brown 环（F-2）+ blockbox amethyst 已带 exchange 标签；`cobblestone\|+13` id 污染（已知） |
| 0004 | amount-insanity | questionable | powah reactor_nitro，重复熔炼放大 |
| 0005 | loop-or-cycle | questionable | brown 环（F-2）占 A1-A20，crafts=27104 |
| 0006 | ok | ok | advanced_ae recharging_card，常规 |
| 0007 | loop-or-cycle | bad | **F-1 主证**：quartz_flake + quartz_bone 无净环，93 action |
| 0008 | loop-or-cycle/amount-insanity | bad | **F-1**：quartz_flake 环主导，crafts=516278 |
| 0009 | loop-or-cycle | bad | **F-1**：white/pink allium petal↔flower 互逆 16× |
| 0010 | loop-or-cycle | questionable | warfare_wings yak3_scheme3，与 0009 同族（花/铁 nugget 放大） |
| 0011 | loop-or-cycle/amount-insanity | bad | 同 0008（portable_fluid_cell_256m），crafts=516278 |
| 0012-0013 | loop-or-cycle | questionable | warfare_wings f6f/p51d scheme，同族放大 |
| 0014 | amount-insanity | questionable | powah energizing_rod，重复熔炼 |
| 0015 | ok | ok | advanced_ae quantum_core |
| 0016 | loop-or-cycle | bad | **F-1**：quartz + quartz_bone 环各 12× |
| 0017-0022 | ok | ok | sophisticatedstorage 箱/桶系列，常规 + storage_ladder 标签到位 |
| 0023 | ok | ok | blaze_and_glaze furnace |
| 0024-0027 | ok | ok | rustic_engineer / tofucraft，常规 |
| 0028 | ok | ok | tofucraft tttburger，常规 ladder（storage_ladder 标签到位） |
| 0029 | ok | ok | heralds_luna flag，feather_pile↔uncraft 已带 intent/recycling |
| 0030-0032 | ok | ok | heralds_luna 系列，同族 |
| 0033 | loop-or-cycle | questionable | forbidden_arcanus deorum_ingot↔nugget 环（正向带 storage_ladder、反向未带）+ coal_lump 环（F-1 同类） |
| 0034-0047 | loop-or-cycle | questionable | warfare_wings 各 scheme，与 0009 同族（花/铁 nugget 放大） |
| 0048 | loop-or-cycle | bad | **F-1**：quartz + quartz_bone 环各 16× |
| 0049 | ok | ok | immersive_aircraft bamboo_hopper |
| 0050-0052 | amount-insanity | questionable | tofucraft mega-wide，glass 生产重复（blockbox 段已带 exchange）；crafts≈976-989 |
| 0053 | ok | ok | man_of_many_planes economy_plane |
| 0054 | loop-or-cycle | questionable | warfare_wings，同族 |
| 0055-0056 | ok | ok | heralds_luna wall_flag，feather_pile 已带标签 |
| 0057 | ok | ok | sophisticatedstorage diamond_chest |
| 0058 | ok | ok | immersive_aircraft cargo_airship |
| 0059 | ok | ok | powah player_transmitter，raw_iron 压缩已带 storage_ladder |
| 0060-0063 | ok | ok | sophisticatedstorage limited_diamond_barrel 系列 |
| 0064 | ok | ok | heralds_luna flag_custom_35 |

已知规划器级问题（链级一句话提及，不单列发现）：
- `|\+N` id 后缀污染普遍（全文件 3552 处，如 `brown_centered_glazed_terracotta\|+1`、`cobblestone\|+13`、`oak_planks\|+199`、`iron_pickaxe\|+17`）。
- `occultism:ritual_dummy/summon_afrit_smelter` 被当作通用熔炼机处理所有 smelting（iron/copper/gold/redstone/glass/diamond/charcoal/quartz），真实可产但机器映射错配。
- vanilla `[UNRESOLVED] ... reason=BUDGET_TRUNCATED`（minecraft:glass/redstone/iron_ingot/oak_log 等原版常见物）属原版常见物预算截断。
- `bibliocraft:biomeswevegone_aspen_fancy_crafter` 路由所有 `minecraft:crafting` 配方（功能即合成台，机器映射错配）。
- 无 `jei:category` 伪生产、`jei:/synthetic/<n>` 仅 0004 一处且已带 `pattern/exchange`，均合法。

## 规则建议（给汇总者）

- targetRegex: 无（本批目标均为合法物品，无新增需排除目标类）。
- recipeRegex: `datanessence:crafting/(\w+)_flake|datanessence:crafting/\1_from_flakes|abyssal_decor:quartz_bone_recipe|abyssal_decor:quartz_bone_decraft|minecraft:\w*_allium_petal_block|minecraft:\w*_allium` —— 标记收敛为 no-net 环；证据链：0007/0008/0011/0016/0048/0009 等 ≥6 链，单链重复 12-16×，0008 crafts=516278。
- builtin 候选: `pattern/reversible_amplification`（跨包通用：datanessence 压缩族、abyssal_decor quartz_bone、花↔petal 互逆对），建议标签 loop/duplicate；与现有 storage_ladder 互补（当前 storage_ladder 清单未含这些 mod 家族）。
- builtin 候选（次要）: `pattern/glazed_variant` 或扩展 exchange —— regex `minecraft:cb/.*|minecraft:sc/.*`（brown-glazed 变体互换），证据：0000/0003/0005，未带 exchange 标签。
