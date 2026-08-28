# tfc-enhanced batch-002 审查报告
- pack: tfc-enhanced
- 批次: batch-002（written=143；分布 long 73 / giant 20 / short 19 / medium 12 / colossal 10 / incomplete 9；coverage 846/4249=19.9%）
- 审查链数: 143（incomplete 9 / complete 134；全量 + 代表抽样：short 19、medium 12、incomplete 9 全读；colossal 10 全看 headers + 0000/0106 深读；giant 20 全看 headers + 0090/0091/0092/0097 深读；long 73 全看 headers + 0010/0088/0116 深读，0011–0072 同 0010 模式。已超 100 条下限）
- 发现: critical 0 / major 3 / minor 0

## 发现

### F-tfc-enhanced-1 | chain=0010 | severity=major | category=tag-expansion-overreach
- 位置: 各链 RAW 区（实际 EXECUTE 只取单一变体，如 0010 A13/A17 仅用 bismuth_bronze chisel/hammer）
- 问题: TFC 大量"任意 X"配方（any-of tag：任意 twig / chisel / hammer / loose rock）被展开为**全部成员**，且每个成员都被当成"必需 raw"平行列出，导致原料计数虚高。
  - CHAIN 0010（一道 dacite 墙）：RAW 列出 8 种 chisel×10、8 种 hammer×6、34 种 twig×34，raw_kinds=44。
  - CHAIN 0091（1 张 music_disc_11）：RAW 列出全部 16 种 loose rock + 16 种 mossy_loose，raw_kinds=45。
  - CHAIN 0092（fire_charge）：RAW 列出全部 34 种 twig×38，raw_kinds=27。
  - CHAIN 0116（cherry_chest_boat）：raw_kinds=85（全 34 twig + 全 wood/chest 变体）。
  - CHAIN 0106（chiseled_sealed_bricks）：raw_kinds=134。
- 原因: 规划器对 tag any-of 不去重，按"并集"而非"任选其一"计入 raw 需求；与 TFC 的 any-* 配方叠加后尤为严重，成本/原料视图被严重夸大。
- 建议: 在 raw 归集阶段对 any-of tag 取单一代表成员（或去重为 1 个等价 raw），而非展开全部；可加 builtin 候选标签（如 `junk/anyof-expand`）收敛 TFC 任意 twig/chisel/hammer/loose_rock 的 tag 展开。

### F-tfc-enhanced-2 | chain=0114 | severity=major | category=worse-alternative
- 位置: A36–A42（dye_*_wool 重染链）
- 问题: 目标 white_carpet 需要 white_wool，规划器未用 string→white_wool 或直接染"未染色 wool"，而是用 `minecraft:dye_white_wool` 把 light_blue_wool 重染成白；light_blue_wool 又由 black_wool→purple(A34)→pink(A30/A37)→magenta(A26)→blue(A39)→light_blue(A40) 逐级重染得到（A36–A41 每步都吃 `black_wool|+14`）。最终 RAW 把全部 16 种 wool 颜色都列为原料，且 black_wool 因 RANK_TRUNCATED 仍未解析。5 条 carpet 链（0114/0126/0130/0131/0133）全部如此，均留 `minecraft:black_wool|+14` UNRESOLVED（candidates=5）。
- 原因: 配方图里未染色 `minecraft:wool` 不可产（RAW 只见 colored wool，无 undyed wool），迫使规划器以单一 colored wool 为基底逐级 re-dye；`dye_<color>_wool` 配方以"任意 colored wool"为输入被选中，形成重染级联。属明显更差路径 / 绕路。
  - 备注（判定纪律）: 若确认 undyed wool/string→white_wool 在本包配方图确实缺失，则本项本质为覆盖/分类缺口而非纯 worse-alternative，应改判 coverage gap；当前按 worse-alternative(major) 报，因其把"以已染色 wool 为基底的重染链"当成首选路径明显差于直接产 wool。
- 建议: 确认并暴露 `minecraft:wool`（未染色）及 string→white_wool 路径；对 `minecraft:dye_[a-z_]+_wool` 族在选路时优先"未染色 wool/string 为基底"，避免以已染色 wool 为基底的重染链；并将 black_wool 的硬缺口单独标记而非拖入全色 RAW。

### F-tfc-enhanced-3 | chain=0097 | severity=major | category=unresolved-expected-item
- 位置: UNRESOLVED 行（lines 592–609）
- 问题: CHAIN 0097（dispenser）把 `firmalife:beehive_frame`（×11，candidates=1, BUDGET_TRUNCATED）、`tfc:stick_bundle`（×1，candidates=1, BUDGET_TRUNCATED）、`tfc:spindle`、`tfc:rock/cobble/andesite x12`（candidates=1）列为 UNRESOLVED；但 `firmalife:beehive_frame` 与 `tfc:stick_bundle` 在本批**完整链 0090** 中由 `firmalife:crafting/beehive_frame`（A15）、`tfc:crafting/stick_bundle`（A5）实际产出。即同一批内别处能产的物品被本链预算截断判为"缺失原料"，使 raw_kinds 虚高到 124（pending=18）。
- 原因: 0097 命中 `maxRootInputBudget [BUDGET_EXHAUSTED]`（expanded=533, candidates=2153），预算耗尽后把候选数=1、明明可产的配方截断为 UNRESOLVED；分类/收敛未跨链复用已验证生产者，导致"假缺失原料"污染成本会计。
- 建议: 对 BUDGET_TRUNCATED 且 candidates=1 的 UNRESOLVED，回退到本批已知生产者（如 0090 的 beehive_frame/stick_bundle）或标记 raw；将 TFC 基础 rock cobble（`tfc:rock/cobble/*`，如 andesite）纳入 raw 叶子，避免把可采掘物当待产。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000–0009 | colossal/giant | ok | netherite 装备/链；block↔ingot、template-copy 已带 `[rule-tag: pattern/exchange]`，几何放大有净产出，正常 |
| 0010–0072 | long | questionable | tfc cracked_bricks 墙/阶/板；TFC 回收环（wall→loose→cobble→stairs）正常，但受 F-1 any-of 展开拖累 raw_kinds=44 |
| 0073 | incomplete | questionable | firmalife:beehive；straw↔thatch 回收环 + BUDGET_EXHAUSTED（`|+N` 污染贯穿），见已知项 |
| 0074 | short | ok | nether_brick_fence，正常（含 `red_sand|+1` 已知污染） |
| 0075–0076 | long | ok | waxed copper，stonecutting 正常 |
| 0077–0087 | short/medium | ok | tfc 板/墙/阶回收环，正常 |
| 0088 | long | questionable | firework_rocket；正常，但受 F-1 twig 展开（raw_kinds=39） |
| 0089 | incomplete | questionable | firmalife:centrifuge；BUDGET_EXHAUSTED + `|+N`（firmalife 工具 any-of 展开） |
| 0090 | giant | questionable | honeyed_beehive_frame；A20 提取蜜→A23 重组形成蜜环（已知往返环机制，链级注明），beehive_frame 由 A15 正常产出 |
| 0091 | giant | bad | music_disc_11；glue↔slime_block 无净产出往返环（A2–A18）+ F-1（全 32 种 rock 展开，raw_kinds=45） |
| 0092 | giant | questionable | fire_charge；F-1（全 34 twig×38），其余正常 |
| 0093 | short | ok | end_crystal，正常（`red_sand|+1` 已知污染） |
| 0094–0113 | giant | questionable | firmalife wood/jarbnet/food_shelf；正常但受 F-1 any-of 展开（raw10） |
| 0103 | giant | ok | minecraft:target，正常 |
| 0106 | colossal | bad | chiseled_sealed_bricks；stick_bundle↔stick_from_bundle 往返环（A3–A20）+ F-1 极致（raw_kinds=134） |
| 0114/0126/0130/0131/0133 | incomplete | bad | 各色 carpet；F-2 重染级联 + black_wool UNRESOLVED（RANK_TRUNCATED） |
| 0116/0121/0122/0124/0127/0135/0141 | long | questionable | 各 chest_boat；正常但 F-1（raw_kinds=85） |
| 0118–0142 | short/medium/long | ok/questionable | 各类 slab/stairs/wall/copper；stonecutting/recolor 正常，零星 `|+N` 污染 |

## 规则建议（给汇总者）
- targetRegex: 本批无需新增目标排除（decompress 已在 blacklist）。carpet 目标是合法目标，问题在路径而非目标，不建议排除。
- recipeRegex:
  - `^minecraft:dye_[a-z_]+_wool$` —— 收敛为首选用"未染色 wool/string"基底（F-2 证据 5 链）。
  - `^tfc:crafting/stick_(bundle|bunch|from_bundle|from_bunch)$` 与 `^tfc:crafting/glue$`/`^minecraft:slime_block$` 互逆 —— 已知往返环，建议收敛（不重复展开）。
  - any-of 展开：`^tfc:wood/twig/.+`、`^tfc:metal/(chisel|hammer)/.+`、`^tfc:rock/loose/.+` —— 建议 builtin 候选 `junk/anyof-expand`，在 raw 归集去重为单一代表成员（F-1 证据 0010/0091/0092/0106/0116）。
- builtin 候选: 跨包通用模式 `any-of tag 全展开致 raw 虚高`（junk/anyof-expand），证据 5+ 链；TFC 基础 rock cobble（`tfc:rock/cobble/*`）应标 raw 叶子，避免 UNRESOLVED（F-3 中 andesite）。
- 无新模式时写"无"。

## 已知（round-2 已固化，本批仅链级注明，不重复为发现）
- `|+N` 物品 id 后缀污染贯穿全批（`tfc:wood/twig/kapok|+20`、`tfc:gem/diamond|+1`、`minecraft:black_wool|+14`、`minecraft:red_sand|+1`、`tfc:rock/loose/chalk|+41` 等）。
- 32 位计数溢出哨兵 / 无净产出往返环机制本身（glue↔slime_block A2–A18、stick_bundle↔stick_from_bunch A3–A20、honeyed_beehive_frame 蜜环 A20–A23）属规划器级问题，本批已在对应链注明。
