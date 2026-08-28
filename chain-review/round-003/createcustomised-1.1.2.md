# CreateCustomised1.1.2 batch-003 审查报告
- pack: CreateCustomised1.1.2
- 批次: batch-003（written=63，全为 incomplete；categories: incomplete 63）
- 审查链数: 63（incomplete 63 / complete 0；本批仅 63 条，低于 100 配额故全量审查：读头部 63 条、抽尾 0006/0007/0028/0029/0036/0042，并全读 0000/0007/0029/0042 验证生成族与环结构）
- 发现: critical 0 / major 1 / minor 0

## 审查范围与口径
- 全批 63 条均为 incomplete，单文件 `chains-incomplete-001.txt`（3.1MB，平均 ~48KB/条，低于 100KB 抽样降阈线），故按全量审查处理。
- 已内置标签（pattern/exchange 3362、pattern/storage_ladder 1994、pattern/recolor 744、intent/recycling 214）覆盖的环/阶梯视为已处理，不再作为发现报告。
- 规划器级已知问题（仅链级一句话提及，不展开为发现）：32 位溢出哨兵（`minecraft:stick x2147483647`）、`|+N` 物品 id 后缀污染、`|+N` 互逆石切/染色环几何放大、smithing_trim 破映射、原版常见物预算截断、背包升级等。

## 发现
### F-createcustomised-1.1.2-1 | chain=0005,0007,0008,0009,0010,0011,0012,0014,0015,0017,0019,0020,0021,0022 | severity=major | category=generation-abuse
- 位置: `create_generators:generator_stone_8x` / `generator_cobblestone_8x` / `generator_stone` / `generator_cobblestone` `@ create:basin` 与 `@ create:mechanical_mixer`（14 条链中均带 `[rule-tag: pattern/exchange]`）
- 问题: 该配方族把 `minecraft:water x1000` 与 `minecraft:lava x1000` 同时列为输入与输出（数量守恒），净产出 `minecraft:stone`/`minecraft:cobblestone` —— 即一个 lava+water 守恒的 cobblegen/生成器。证据见 CHAIN 0007 A91–A94（water/lava 各 x1000 进 x1000 出，出 stone/cobblestone），CHAIN 0005 A112–A115 同构。分类器将其标为 `pattern/exchange`，而 generation 排除只覆盖 `laser_drill/alakarkinos/graphite_processing`（`intent/generation`），于是这个零净输入的生成配方未被排除，被织入 14 条链（主要是 create_dragons_plus 染料/染料桶族）作为未裁剪的"免费"石料来源（CHAIN 0007 经 cobblestone→glass→glass_bottle→potion 的锥噪声支）。
- 原因: 生成类（cobblegen）配方族被分类器误标为 `pattern/exchange`，导致它绕过 generation 排除规则、作为 free-resource 进入生产链锥。
- 建议: 将 `create_generators:generator_*` 重新标为 `intent/generation`（或加入 free-resource 排除正则 `^create_generators:generator_`）；属跨包通用生成族，建议作为 builtin 候选 `intent/generation` 标签。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | create:small_crimsite_* 石切环 + 铁回收塔 | questionable | 已知 +N 后缀驱动石切环（pattern/exchange 已覆盖），目标 cocoa_liquor_bucket 实际经 A28–A29 正常产出 |
| 0001 | create:experience_block | ok | 短链，路径正常 |
| 0002 | red_nether_brick wall | questionable | 石切环 +N 后缀污染（已知），非新发现 |
| 0003 | red_nether_brick stairs | questionable | 同上石切环 +N（已知） |
| 0004 | red_nether_brick slab | questionable | 同上石切环 +N（已知） |
| 0005 | magenta_dye_bucket | questionable | 含 create_generators 误标生成族（见 F-1） |
| 0006 | create:tea | questionable | 全首段为花岗岩石切环噪声（pattern/exchange 已覆盖），目标经 draining 正常产出 |
| 0007 | orange_dye | questionable | 含 create_generators 误标生成族（F-1）+ 甜菜 crate 环 |
| 0008 | red_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0009 | brown_dye | questionable | 含 create_generators 误标生成族（F-1） |
| 0010 | blue_dye | questionable | 含 create_generators 误标生成族（F-1） |
| 0011 | white_dye | questionable | 含 create_generators 误标生成族（F-1） |
| 0012 | yellow_dye | questionable | 含 create_generators 误标生成族（F-1） |
| 0013 | create_enchantment_industry:experience | questionable | 经验块/粒互逆环（pattern 已覆盖），无新发现 |
| 0014 | black_dye | questionable | 含 create_generators 误标生成族（F-1） |
| 0015 | light_gray_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0016 | ratatouille:dragon_breath_bucket | questionable | 桶填充锥噪声，无新发现 |
| 0017 | cyan_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0018 | ratatouille:bio_gas_bucket | questionable | 桶填充锥噪声，无新发现 |
| 0019 | green_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0020 | lime_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0021 | light_blue_dye_bucket | questionable | 含 create_generators 误标生成族（F-1） |
| 0022 | create:chocolate_bucket | questionable | 桶填充/锥噪声，无新发现 |
| 0023 | ratatouille:irrigation_tower | questionable | mega-wide，raw5，锥预算截断（已知） |
| 0024 | spire_armor_trim_smithing_template | bad | smithing_trim 破映射 + 32 位哨兵（stick x2147483647, crafts 2.5e11），已知规划器级问题 |
| 0025 | farmersdelight:nether_salad | questionable | crafts 8.5e7，锥放大溢出（已知），目标经 crafting 正常产出 |
| 0026 | fermented_spider_eye | questionable | 石切环 +N（已知） |
| 0027 | spectral_arrow | questionable | crafts 4.8e8，锥放大溢出（已知） |
| 0028 | railways:track_acacia_wide | questionable | 序列装配轨 + 木/稻锥放大（rice_panicle 2.8e7 未解析，已知） |
| 0029 | simulated:redstone_magnet | questionable | 真实链（copper_sheet+industrial_iron_block+redstone），redstone 经 create_more_automation 正常产 |
| 0030 | railways:track_dark_oak_wide | questionable | 同 0028 轨族锥放大（已知） |
| 0031 | railways:track_phantom_wide | questionable | 轨族锥噪声（已知） |
| 0032 | railways:track_oak_wide | questionable | 轨族锥放大（已知） |
| 0033 | railways:track_birch_wide | questionable | 轨族锥放大（已知） |
| 0034 | minecraft:packed_mud | questionable | 石切环 +N（已知） |
| 0035 | railways:track_cherry_wide | questionable | 轨族锥放大（已知） |
| 0036 | daylight_detector | questionable | mega-wide，crimsite 石切环 + bamboo 回收支噪声 + rice_panicle 3.9e8 未解析（已知） |
| 0037 | railways:track_blackstone_narrow | questionable | 轨族锥噪声（已知） |
| 0038 | muddy_mangrove_roots | questionable | 石切环 +N（已知） |
| 0039 | create:netherite_diving_helmet | questionable | mega-wide，crafts 4.2e10，含 netherite_upgrade_smithing_template 不可合成（已知 smithing 问题） |
| 0040 | create:netherite_diving_boots | questionable | 同 0039 系列（已知 smithing 问题） |
| 0041 | farmersdelight:honey_cookie | questionable | 短链，锥噪声，无新发现 |
| 0042 | minecraft:black_harness | bad | +N 后缀染色环几何放大致 crafts=107617172（19 色 harness 同值），已知 +N 污染 |
| 0043 | minecraft:pink_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0044 | minecraft:gray_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0045 | minecraft:orange_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0046 | minecraft:light_gray_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0047 | minecraft:blue_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0048 | minecraft:lime_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0049 | minecraft:green_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0050 | minecraft:brown_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0051 | minecraft:purple_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0052 | minecraft:red_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0053 | farmersdelight:organic_compost | questionable | 堆肥锥噪声，无新发现 |
| 0054 | create:mysterious_cuckoo_clock | questionable | crafts 9.7e7 锥放大（已知） |
| 0055 | minecraft:cyan_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0056 | minecraft:white_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0057 | minecraft:yellow_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0058 | minecraft:magenta_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0059 | create_enchantment_industry:blaze_forger | questionable | raw32 mega-wide，真实链，无新发现 |
| 0060 | minecraft:light_blue_harness | bad | 同 0042 染色环溢出（已知 +N） |
| 0061 | create_train_parts:train_step_copper | questionable | crafts 15876 锥放大（已知） |
| 0062 | create_dragons_plus:fragile_fluid_tank | questionable | crafts 2.4e8 锥放大（已知） |

## 规则建议（给汇总者）
- targetRegex: 无新增目标黑名单（harness / smithing_template 目标已在 blacklist decompress 之外由上游处理）。
- recipeRegex: `^create_generators:generator_` —— 标记/排除为 generation（intent/generation）。证据：14 条链（0005,0007–0012,0014,0015,0017,0019–0022）均出现，A91–A94 显示 lava+water 守恒的 cobblegen。
- builtin 候选: `create_generators:generator_*` 属跨包通用生成族，建议加 `intent/generation` 标签并排除（与 laser_drill/alakarkinos/graphite_processing 同组）。另 `+N` 后缀（如 `small_crimsite_brick_wall|+17`、`black_wool|+14`、`cobblestone|+3`）仍普遍驱动石切/染色互逆环与 10⁸ 级 crafts，建议作为 data-pollution 内置规则在图构建期剥离 `\|\+N` 后缀、归并到基础 id（已知规划器级问题，重申以推动修复）。
- 无新模式时写"无"：除上述生成族误标与 +N 后缀外，未发现其他未被标签覆盖的可疑配方族、新型假生产、可解析 UNRESOLVED 物或目标选择怪象。
